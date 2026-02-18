import AVFoundation
import Combine

struct AudioResult {
    let data: Data
    let format: String  // "m4a" or "wav"
}

final class AudioRecorder: ObservableObject {

    @Published var isRecording = false
    @Published var currentAmplitude: Float = 0
    @Published var recordingDuration: TimeInterval = 0

    private var audioEngine: AVAudioEngine?
    private var pcmBuffer = Data()
    private var recordingStartTime: Date?
    private var amplitudeTimer: Timer?

    // MARK: - Public

    func startRecording() throws {
        pcmBuffer = Data()
        recordingStartTime = Date()

        let engine = AVAudioEngine()
        let inputNode = engine.inputNode

        // Get the hardware format and create our desired format
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

        // Install a tap — we may need a converter if hardware sample rate differs
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

        // Track duration and amplitude on main thread
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

        guard pcmBuffer.count >= Constants.minAudioSizeBytes else {
            return nil
        }

        // Try AAC compression first (10-20x smaller payload = much faster upload)
        if let m4aData = compressToAAC(from: pcmBuffer) {
            let ratio = String(format: "%.1f", Float(pcmBuffer.count) / Float(m4aData.count))
            print("[AudioRecorder] Compressed: \(pcmBuffer.count) bytes PCM → \(m4aData.count) bytes M4A (\(ratio)x reduction)")
            return AudioResult(data: m4aData, format: "m4a")
        }

        // Fallback to uncompressed WAV
        print("[AudioRecorder] AAC unavailable, using WAV fallback: \(pcmBuffer.count) bytes")
        return AudioResult(data: createWAVData(from: pcmBuffer), format: "wav")
    }

    var audioDurationMs: Int {
        let bytesPerSample = Constants.audioBitDepth / 8
        let totalSamples = pcmBuffer.count / bytesPerSample
        return Int((Double(totalSamples) / Constants.audioSampleRate) * 1000)
    }

    // MARK: - Private

    private func appendBuffer(_ buffer: AVAudioPCMBuffer) {
        guard let int16Data = buffer.int16ChannelData else { return }
        let frameCount = Int(buffer.frameLength)
        let channelData = int16Data[0]

        // Calculate RMS for amplitude visualization
        var sum: Float = 0
        for i in 0..<frameCount {
            let sample = Float(channelData[i])
            sum += sample * sample
        }
        let rms = sqrt(sum / Float(frameCount))
        let normalized = min(rms / Constants.maxAmplitude, 1.0)

        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.currentAmplitude = self.currentAmplitude * (1 - Constants.amplitudeSmoothingFactor)
                + normalized * Constants.amplitudeSmoothingFactor

            // Debug: Log amplitude values occasionally
            #if DEBUG
            if Int.random(in: 0..<50) == 0 {  // Log ~2% of the time to avoid spam
                print("🎤 Amplitude - RMS: \(Int(rms)), Normalized: \(String(format: "%.3f", normalized)), Current: \(String(format: "%.3f", self.currentAmplitude))")
            }
            #endif
        }

        // Append raw PCM bytes
        let data = Data(bytes: channelData, count: frameCount * MemoryLayout<Int16>.size)
        pcmBuffer.append(data)
    }

    private func compressToAAC(from pcmData: Data) -> Data? {
        let tempDir = FileManager.default.temporaryDirectory
        let m4aURL = tempDir.appendingPathComponent("voxtype_\(UUID().uuidString).m4a")

        defer {
            try? FileManager.default.removeItem(at: m4aURL)
        }

        let sampleCount = pcmData.count / MemoryLayout<Int16>.size
        guard sampleCount > 0 else { return nil }

        // Write AAC inside autoreleasepool to ensure AVAudioFile is
        // finalized (and the m4a container closed) before we read the file
        let writeSucceeded: Bool = autoreleasepool {
            do {
                let outputSettings: [String: Any] = [
                    AVFormatIDKey: kAudioFormatMPEG4AAC,
                    AVSampleRateKey: Constants.audioSampleRate,
                    AVNumberOfChannelsKey: Int(Constants.audioChannels),
                    AVEncoderBitRateKey: 32000,  // 32 kbps – plenty for speech
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

                // Convert Int16 PCM samples to Float32
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
        header.append(withUnsafeBytes(of: UInt32(16).littleEndian) { Data($0) })    // sub-chunk size
        header.append(withUnsafeBytes(of: UInt16(1).littleEndian) { Data($0) })     // PCM format
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
