import AVFoundation
import Combine

// MARK: - Data Structures

struct AudioChunk {
    let samples: [Int16]
    let timestampUs: Int64      // Microseconds since recording start
    let globalIndex: Int64      // Cumulative sample index
}

struct SpeechSegment {
    let startUs: Int64
    let endUs: Int64
    let startIndex: Int64
    let endIndex: Int64
}

struct AudioMetadata {
    let speechDurationMs: Int
    let originalDurationMs: Int
    let speechSegmentCount: Int
    let silenceTrimmingApplied: Bool
}

struct AudioResult {
    let data: Data
    let format: String  // "m4a" or "wav"
    let metadata: AudioMetadata
}

// MARK: - AudioRecorder

final class AudioRecorder: ObservableObject {

    @Published var isRecording = false
    @Published var currentAmplitude: Float = 0
    @Published var recordingDuration: TimeInterval = 0

    private var audioEngine: AVAudioEngine?
    private var recordingStartTime: Date?
    private var amplitudeTimer: Timer?

    // Chunked buffer (replaces flat pcmBuffer)
    private var chunks: [AudioChunk] = []
    private let chunksLock = NSLock()
    private var globalSampleIndex: Int64 = 0

    // Silence detection (runs on dedicated serial queue)
    private let silenceQueue = DispatchQueue(label: "com.wozcribe.silenceDetection", qos: .userInitiated)
    private var speechSegments: [SpeechSegment] = []
    private var currentSpeechStartUs: Int64? = nil
    private var currentSpeechStartIndex: Int64 = 0
    private var lastSpeechEndUs: Int64 = 0

    // MARK: - Public

    func startRecording() throws {
        // Reset state
        chunksLock.lock()
        chunks = []
        chunksLock.unlock()
        globalSampleIndex = 0
        speechSegments = []
        currentSpeechStartUs = nil
        currentSpeechStartIndex = 0
        lastSpeechEndUs = 0
        recordingStartTime = Date()

        let engine = AVAudioEngine()
        let inputNode = engine.inputNode

        let hardwareFormat = inputNode.outputFormat(forBus: 0)
        guard hardwareFormat.sampleRate > 0 else {
            throw VoxTypeError.noAudioData
        }

        guard let desiredFormat = AVAudioFormat(
            commonFormat: .pcmFormatInt16,
            sampleRate: Constants.audioSampleRate,
            channels: AVAudioChannelCount(Constants.audioChannels),
            interleaved: true
        ) else {
            throw VoxTypeError.noAudioData
        }

        let converter = AVAudioConverter(from: hardwareFormat, to: desiredFormat)

        inputNode.installTap(onBus: 0, bufferSize: 1024, format: hardwareFormat) { [weak self] buffer, _ in
            guard let self else { return }

            if let converter {
                let frameCapacity = AVAudioFrameCount(
                    Double(buffer.frameLength) * Constants.audioSampleRate / hardwareFormat.sampleRate
                )
                guard frameCapacity > 0 else { return }

                guard let convertedBuffer = AVAudioPCMBuffer(
                    pcmFormat: desiredFormat,
                    frameCapacity: frameCapacity
                ) else { return }

                var error: NSError?
                var hasData = false
                converter.convert(to: convertedBuffer, error: &error) { _, outStatus in
                    if hasData {
                        outStatus.pointee = .noDataNow
                        return nil
                    }
                    hasData = true
                    outStatus.pointee = .haveData
                    return buffer
                }

                if error == nil, convertedBuffer.frameLength > 0 {
                    self.appendBuffer(convertedBuffer)
                }
            } else {
                self.appendBuffer(buffer)
            }
        }

        try engine.start()
        self.audioEngine = engine

        DispatchQueue.main.async {
            self.isRecording = true
            self.amplitudeTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
                guard let self, let start = self.recordingStartTime else { return }
                self.recordingDuration = Date().timeIntervalSince(start)
            }
        }
    }

    func stopRecording() -> AudioResult? {
        amplitudeTimer?.invalidate()
        amplitudeTimer = nil

        audioEngine?.stop()
        audioEngine?.inputNode.removeTap(onBus: 0)
        audioEngine = nil

        DispatchQueue.main.async {
            self.isRecording = false
            self.currentAmplitude = 0
            self.recordingDuration = 0
        }

        // Drain silence detection queue — wait for all pending analysis to complete
        silenceQueue.sync {}

        let rawPCM = extractAllPCM()
        let originalDurationMs = durationMs(fromByteCount: rawPCM.count)

        guard rawPCM.count >= Constants.minAudioSizeBytes else {
            return nil
        }

        // Finalize speech segments (close open segment, add padding, merge overlaps)
        let totalDurationUs = Int64(originalDurationMs) * 1000
        finalizeSegments(totalDurationUs: totalDurationUs)

        // Determine whether to trim
        let trimmedPCM: Data
        let silenceTrimmingApplied: Bool

        if !speechSegments.isEmpty {
            let extracted = extractTrimmedPCM()
            let savingsPercent = rawPCM.count > 0
                ? ((rawPCM.count - extracted.count) * 100) / rawPCM.count
                : 0

            if savingsPercent >= Constants.minSavingsPercent && extracted.count >= Constants.minAudioSizeBytes {
                trimmedPCM = extracted
                silenceTrimmingApplied = true
                print("[AudioRecorder] Silence trimming: \(rawPCM.count) -> \(extracted.count) bytes (\(savingsPercent)% saved)")
            } else {
                trimmedPCM = rawPCM
                silenceTrimmingApplied = false
                print("[AudioRecorder] Trimming skipped: only \(savingsPercent)% savings")
            }
        } else {
            trimmedPCM = rawPCM
            silenceTrimmingApplied = false
            print("[AudioRecorder] No speech segments detected, using full audio")
        }

        let speechDurMs = silenceTrimmingApplied
            ? durationMs(fromByteCount: trimmedPCM.count)
            : originalDurationMs

        let metadata = AudioMetadata(
            speechDurationMs: speechDurMs,
            originalDurationMs: originalDurationMs,
            speechSegmentCount: speechSegments.count,
            silenceTrimmingApplied: silenceTrimmingApplied
        )

        print("[AudioRecorder] Segments: \(speechSegments.count), Speech: \(speechDurMs)ms of \(originalDurationMs)ms")

        // Try AAC compression
        if let m4aData = compressToAAC(from: trimmedPCM) {
            let ratio = String(format: "%.1f", Float(trimmedPCM.count) / Float(m4aData.count))
            print("[AudioRecorder] Compressed: \(trimmedPCM.count) bytes PCM -> \(m4aData.count) bytes M4A (\(ratio)x reduction)")
            return AudioResult(data: m4aData, format: "m4a", metadata: metadata)
        }

        // Fallback to uncompressed WAV
        print("[AudioRecorder] AAC unavailable, using WAV fallback: \(trimmedPCM.count) bytes")
        return AudioResult(data: createWAVData(from: trimmedPCM), format: "wav", metadata: metadata)
    }

    var audioDurationMs: Int {
        chunksLock.lock()
        let totalSamples = chunks.reduce(0) { $0 + $1.samples.count }
        chunksLock.unlock()
        return Int((Double(totalSamples) / Constants.audioSampleRate) * 1000)
    }

    // MARK: - Buffer Append + Silence Detection Dispatch

    private func appendBuffer(_ buffer: AVAudioPCMBuffer) {
        guard let int16Data = buffer.int16ChannelData else { return }
        let frameCount = Int(buffer.frameLength)
        let channelData = int16Data[0]

        // Copy samples into an array
        let samples = Array(UnsafeBufferPointer(start: channelData, count: frameCount))

        // Create timestamped chunk
        let timestampUs = (globalSampleIndex * 1_000_000) / Int64(Constants.audioSampleRate)
        let chunk = AudioChunk(
            samples: samples,
            timestampUs: timestampUs,
            globalIndex: globalSampleIndex
        )
        globalSampleIndex += Int64(frameCount)

        // Store chunk (thread-safe)
        chunksLock.lock()
        chunks.append(chunk)
        chunksLock.unlock()

        // Dispatch to silence detection queue
        silenceQueue.async { [weak self] in
            self?.analyzeChunk(chunk)
        }

        // Calculate RMS for amplitude visualization (stays inline — lightweight)
        var sum: Float = 0
        for i in 0..<frameCount {
            let sample = Float(samples[i])
            sum += sample * sample
        }
        let rms = sqrt(sum / Float(frameCount))
        let normalized = min(rms / Constants.maxAmplitude, 1.0)

        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.currentAmplitude = self.currentAmplitude * (1 - Constants.amplitudeSmoothingFactor)
                + normalized * Constants.amplitudeSmoothingFactor
        }
    }

    // MARK: - Silence Detection (runs on silenceQueue)

    private func analyzeChunk(_ chunk: AudioChunk) {
        let rmsDb = calculateRmsDb(chunk.samples)
        let isSpeech = rmsDb > Constants.silenceThresholdDB

        if isSpeech {
            if currentSpeechStartUs == nil {
                // Check if we should merge with the previous segment (gap < minSilenceDurationMs)
                if let lastSegment = speechSegments.last,
                   chunk.timestampUs - lastSpeechEndUs < Int64(Constants.minSilenceDurationMs) * 1000 {
                    speechSegments.removeLast()
                    currentSpeechStartUs = lastSegment.startUs
                    currentSpeechStartIndex = lastSegment.startIndex
                } else {
                    currentSpeechStartUs = chunk.timestampUs
                    currentSpeechStartIndex = chunk.globalIndex
                }
            }
        } else {
            if let speechStart = currentSpeechStartUs {
                speechSegments.append(SpeechSegment(
                    startUs: speechStart,
                    endUs: chunk.timestampUs,
                    startIndex: currentSpeechStartIndex,
                    endIndex: chunk.globalIndex
                ))
                lastSpeechEndUs = chunk.timestampUs
                currentSpeechStartUs = nil
            }
        }
    }

    private func calculateRmsDb(_ samples: [Int16]) -> Float {
        guard !samples.isEmpty else { return -100 }
        var sum: Double = 0
        for sample in samples {
            let d = Double(sample)
            sum += d * d
        }
        let rms = sqrt(sum / Double(samples.count))
        return rms > 0 ? Float(20.0 * log10(rms / Double(Int16.max))) : -100
    }

    // MARK: - Segment Finalization

    private func finalizeSegments(totalDurationUs: Int64) {
        // Close any open speech segment
        if let speechStart = currentSpeechStartUs {
            let totalSamples = globalSampleIndex
            speechSegments.append(SpeechSegment(
                startUs: speechStart,
                endUs: totalDurationUs,
                startIndex: currentSpeechStartIndex,
                endIndex: totalSamples
            ))
            currentSpeechStartUs = nil
        }

        guard !speechSegments.isEmpty else { return }

        // Add padding and merge overlaps
        let totalSamples = globalSampleIndex
        let padded = speechSegments.map { seg -> SpeechSegment in
            let paddedStartUs = max(0, seg.startUs - Int64(Constants.audioBufferBeforeMs) * 1000)
            let paddedEndUs = min(totalDurationUs, seg.endUs + Int64(Constants.audioBufferAfterMs) * 1000)
            let paddedStartIndex = max(0, (paddedStartUs * Int64(Constants.audioSampleRate)) / 1_000_000)
            let paddedEndIndex = min(totalSamples, (paddedEndUs * Int64(Constants.audioSampleRate)) / 1_000_000)
            return SpeechSegment(
                startUs: paddedStartUs,
                endUs: paddedEndUs,
                startIndex: paddedStartIndex,
                endIndex: paddedEndIndex
            )
        }

        speechSegments = mergeOverlapping(padded)
    }

    private func mergeOverlapping(_ segments: [SpeechSegment]) -> [SpeechSegment] {
        guard !segments.isEmpty else { return [] }
        let sorted = segments.sorted { $0.startUs < $1.startUs }
        var merged: [SpeechSegment] = []
        var current = sorted[0]

        for i in 1..<sorted.count {
            let next = sorted[i]
            if next.startUs <= current.endUs {
                current = SpeechSegment(
                    startUs: current.startUs,
                    endUs: max(current.endUs, next.endUs),
                    startIndex: current.startIndex,
                    endIndex: max(current.endIndex, next.endIndex)
                )
            } else {
                merged.append(current)
                current = next
            }
        }
        merged.append(current)
        return merged
    }

    // MARK: - PCM Extraction

    private func extractTrimmedPCM() -> Data {
        chunksLock.lock()
        let allChunks = chunks
        chunksLock.unlock()

        var trimmedData = Data()

        for segment in speechSegments {
            for chunk in allChunks {
                let chunkEndIndex = chunk.globalIndex + Int64(chunk.samples.count)

                // Check if chunk overlaps with this speech segment
                guard chunkEndIndex > segment.startIndex && chunk.globalIndex < segment.endIndex else {
                    continue
                }

                let startOffset = max(0, Int(segment.startIndex - chunk.globalIndex))
                let endOffset = min(chunk.samples.count, Int(segment.endIndex - chunk.globalIndex))

                if startOffset < endOffset {
                    let slice = chunk.samples[startOffset..<endOffset]
                    slice.withContiguousStorageIfAvailable { ptr in
                        trimmedData.append(
                            UnsafeBufferPointer(
                                start: UnsafeRawPointer(ptr.baseAddress!).assumingMemoryBound(to: UInt8.self),
                                count: ptr.count * MemoryLayout<Int16>.size
                            )
                        )
                    }
                }
            }
        }

        return trimmedData
    }

    private func extractAllPCM() -> Data {
        chunksLock.lock()
        let allChunks = chunks
        chunksLock.unlock()

        var rawData = Data()
        for chunk in allChunks {
            chunk.samples.withContiguousStorageIfAvailable { ptr in
                rawData.append(
                    UnsafeBufferPointer(
                        start: UnsafeRawPointer(ptr.baseAddress!).assumingMemoryBound(to: UInt8.self),
                        count: ptr.count * MemoryLayout<Int16>.size
                    )
                )
            }
        }
        return rawData
    }

    // MARK: - Helpers

    private func durationMs(fromByteCount count: Int) -> Int {
        let bytesPerSample = Constants.audioBitDepth / 8
        let totalSamples = count / bytesPerSample
        return Int((Double(totalSamples) / Constants.audioSampleRate) * 1000)
    }

    // MARK: - AAC Compression

    private func compressToAAC(from pcmData: Data) -> Data? {
        let tempDir = FileManager.default.temporaryDirectory
        let m4aURL = tempDir.appendingPathComponent("wozcribe_\(UUID().uuidString).m4a")

        defer {
            try? FileManager.default.removeItem(at: m4aURL)
        }

        let sampleCount = pcmData.count / MemoryLayout<Int16>.size
        guard sampleCount > 0 else { return nil }

        let writeSucceeded: Bool = autoreleasepool {
            do {
                let outputSettings: [String: Any] = [
                    AVFormatIDKey: kAudioFormatMPEG4AAC,
                    AVSampleRateKey: Constants.audioSampleRate,
                    AVNumberOfChannelsKey: Int(Constants.audioChannels),
                    AVEncoderBitRateKey: 32000,
                ]

                guard let processingFormat = AVAudioFormat(
                    commonFormat: .pcmFormatFloat32,
                    sampleRate: Constants.audioSampleRate,
                    channels: AVAudioChannelCount(Constants.audioChannels),
                    interleaved: false
                ) else { return false }

                let outputFile = try AVAudioFile(
                    forWriting: m4aURL,
                    settings: outputSettings,
                    commonFormat: .pcmFormatFloat32,
                    interleaved: false
                )

                let frameCount = AVAudioFrameCount(sampleCount)
                guard let buffer = AVAudioPCMBuffer(
                    pcmFormat: processingFormat,
                    frameCapacity: frameCount
                ) else { return false }

                buffer.frameLength = frameCount

                pcmData.withUnsafeBytes { rawPtr in
                    guard let src = rawPtr.baseAddress?.assumingMemoryBound(to: Int16.self),
                          let dst = buffer.floatChannelData?[0] else { return }
                    for i in 0..<sampleCount {
                        dst[i] = Float(src[i]) / 32768.0
                    }
                }

                try outputFile.write(from: buffer)
                return true
            } catch {
                print("[AudioRecorder] AAC compression failed: \(error)")
                return false
            }
        }

        guard writeSucceeded else { return nil }
        return try? Data(contentsOf: m4aURL)
    }

    // MARK: - WAV Fallback

    private func createWAVData(from pcmData: Data) -> Data {
        var header = Data()

        let sampleRate = UInt32(Constants.audioSampleRate)
        let channels = UInt16(Constants.audioChannels)
        let bitsPerSample = UInt16(Constants.audioBitDepth)
        let byteRate = sampleRate * UInt32(channels) * UInt32(bitsPerSample / 8)
        let blockAlign = channels * (bitsPerSample / 8)
        let dataSize = UInt32(pcmData.count)
        let fileSize = 36 + dataSize

        // RIFF header
        header.append(contentsOf: "RIFF".utf8)
        header.append(withUnsafeBytes(of: fileSize.littleEndian) { Data($0) })
        header.append(contentsOf: "WAVE".utf8)

        // fmt sub-chunk
        header.append(contentsOf: "fmt ".utf8)
        header.append(withUnsafeBytes(of: UInt32(16).littleEndian) { Data($0) })
        header.append(withUnsafeBytes(of: UInt16(1).littleEndian) { Data($0) })
        header.append(withUnsafeBytes(of: channels.littleEndian) { Data($0) })
        header.append(withUnsafeBytes(of: sampleRate.littleEndian) { Data($0) })
        header.append(withUnsafeBytes(of: byteRate.littleEndian) { Data($0) })
        header.append(withUnsafeBytes(of: blockAlign.littleEndian) { Data($0) })
        header.append(withUnsafeBytes(of: bitsPerSample.littleEndian) { Data($0) })

        // data sub-chunk
        header.append(contentsOf: "data".utf8)
        header.append(withUnsafeBytes(of: dataSize.littleEndian) { Data($0) })

        return header + pcmData
    }
}
