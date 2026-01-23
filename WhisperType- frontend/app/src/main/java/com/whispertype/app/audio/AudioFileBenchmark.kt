package com.whispertype.app.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.whispertype.app.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * AudioFileBenchmark - Simulates recording from a file at 1x speed
 *
 * This class provides accurate benchmarking by:
 * 1. Reading audio file at real-time speed (40ms chunks with delays)
 * 2. Running parallel RMS analysis during "recording" (for REALTIME_RMS flow)
 * 3. Providing both trimmed and raw audio for post-recording comparison
 *
 * Usage:
 * - Call simulateRecording() with an audio file
 * - It will "play" the file at 1x speed while analyzing in parallel
 * - Returns both pre-trimmed audio (parallel processed) and raw audio
 */
class AudioFileBenchmark(private val context: Context) {

    companion object {
        private const val TAG = "AudioFileBenchmark"
        
        // Audio configuration (matches RealtimeRmsRecorder)
        private const val TARGET_SAMPLE_RATE = 16000 // 16kHz
        private const val CHUNK_DURATION_MS = 40 // 40ms chunks
        private const val CHUNK_SAMPLES = (TARGET_SAMPLE_RATE * CHUNK_DURATION_MS / 1000) // 640 samples
        
        // RMS threshold (from Constants)
        private const val SPEECH_THRESHOLD_DB = Constants.SILENCE_THRESHOLD_DB // -40dB
        private const val MIN_SILENCE_DURATION_MS = Constants.MIN_SILENCE_DURATION_MS // 100ms
        private const val BUFFER_BEFORE_MS = Constants.AUDIO_BUFFER_BEFORE_MS // 100ms
        private const val BUFFER_AFTER_MS = Constants.AUDIO_BUFFER_AFTER_MS // 100ms
    }

    /**
     * Result of simulated recording
     */
    data class BenchmarkResult(
        val trimmedAudioBytes: ByteArray,  // Pre-trimmed (parallel processed)
        val rawAudioBytes: ByteArray,      // Original untrimmed
        val speechDurationMs: Long,
        val originalDurationMs: Long,
        val speechSegmentCount: Int
    )

    /**
     * Speech segment detected during simulation
     */
    private data class SpeechSegment(
        val startUs: Long,
        val endUs: Long,
        val startIndex: Long,
        val endIndex: Long
    )

    // RMS analysis state
    private val speechSegments = ConcurrentLinkedQueue<SpeechSegment>()
    private var currentSpeechStart: Long? = null
    private var lastSpeechEnd: Long = 0L
    private val isAnalyzing = AtomicBoolean(false)
    private val analysisQueue = ConcurrentLinkedQueue<ChunkData>()

    private data class ChunkData(
        val samples: ShortArray,
        val timeUs: Long,
        val globalIndex: Long
    )

    /**
     * Simulate recording from an audio file at 1x speed.
     * 
     * This function:
     * 1. Decodes the audio file to PCM samples
     * 2. Processes chunks at real-time speed (40ms + delay)
     * 3. Runs RMS analysis in parallel during "recording"
     * 4. Returns both trimmed and raw audio when "recording" stops
     *
     * @param audioFile The audio file to simulate recording from
     * @return BenchmarkResult with trimmed audio, raw audio, and metadata
     */
    suspend fun simulateRecording(audioFile: File): BenchmarkResult = withContext(Dispatchers.Default) {
        Log.d(TAG, "Starting simulated recording from: ${audioFile.name}")
        
        // Reset state
        speechSegments.clear()
        analysisQueue.clear()
        currentSpeechStart = null
        lastSpeechEnd = 0L

        // Step 1: Decode audio file to PCM samples
        val samples = decodeAudioToSamples(audioFile)
        if (samples == null || samples.isEmpty()) {
            throw Exception("Failed to decode audio file")
        }
        
        val totalDurationUs = (samples.size.toLong() * 1_000_000L) / TARGET_SAMPLE_RATE
        Log.d(TAG, "Decoded ${samples.size} samples, duration: ${totalDurationUs / 1000}ms")

        // Step 2: Start RMS analysis thread
        isAnalyzing.set(true)
        val analysisThread = Thread({ rmsAnalysisLoop() }, "BenchmarkRmsAnalysis").apply {
            priority = Thread.NORM_PRIORITY - 1
            start()
        }

        // Step 3: Process audio in chunks at real-time speed
        var chunkIndex = 0L
        var sampleOffset = 0
        
        while (sampleOffset < samples.size) {
            val chunkEnd = min(sampleOffset + CHUNK_SAMPLES, samples.size)
            val chunkSize = chunkEnd - sampleOffset
            
            // Extract chunk
            val chunk = ShortArray(chunkSize)
            System.arraycopy(samples, sampleOffset, chunk, 0, chunkSize)
            
            // Queue for RMS analysis
            val timeUs = (sampleOffset * 1_000_000L) / TARGET_SAMPLE_RATE
            analysisQueue.offer(ChunkData(chunk, timeUs, sampleOffset.toLong()))
            
            // Simulate real-time recording delay
            // This is the key to accurate benchmarking!
            delay(CHUNK_DURATION_MS.toLong())
            
            sampleOffset = chunkEnd
            chunkIndex++
        }

        Log.d(TAG, "Simulated recording complete, processed $chunkIndex chunks")

        // Step 4: Stop RMS analysis and wait for completion
        isAnalyzing.set(false)
        analysisThread.join(1000)

        // Step 5: Finalize segments (close any open speech segment)
        finalizeSegments(totalDurationUs)

        // Step 6: Extract trimmed audio
        val trimmedResult = extractTrimmedAudio(samples, totalDurationUs)

        // Step 7: Create raw audio WAV
        val rawWavBytes = samplesToWav(samples)

        Log.d(TAG, "Benchmark result: trimmed=${trimmedResult.trimmedBytes.size} bytes, " +
                "raw=${rawWavBytes.size} bytes, segments=${trimmedResult.segmentCount}")

        BenchmarkResult(
            trimmedAudioBytes = trimmedResult.trimmedBytes,
            rawAudioBytes = rawWavBytes,
            speechDurationMs = trimmedResult.speechDurationMs,
            originalDurationMs = totalDurationUs / 1000,
            speechSegmentCount = trimmedResult.segmentCount
        )
    }

    /**
     * Load audio file from assets folder
     */
    fun loadFromAssets(assetName: String): File {
        val tempFile = File(context.cacheDir, "benchmark_$assetName")
        context.assets.open(assetName).use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    /**
     * RMS analysis loop - runs in parallel during simulated recording
     */
    private fun rmsAnalysisLoop() {
        Log.d(TAG, "RMS analysis thread started")

        while (isAnalyzing.get() || analysisQueue.isNotEmpty()) {
            val chunk = analysisQueue.poll()
            if (chunk == null) {
                Thread.sleep(5)
                continue
            }

            // Calculate RMS
            val rmsDb = calculateRmsDb(chunk.samples)
            val isSpeech = rmsDb > SPEECH_THRESHOLD_DB

            // Update speech segments
            if (isSpeech) {
                if (currentSpeechStart == null) {
                    // Check if we should merge with previous segment
                    val lastSegment = speechSegments.toList().lastOrNull()
                    if (lastSegment != null &&
                        chunk.timeUs - lastSpeechEnd < MIN_SILENCE_DURATION_MS * 1000) {
                        // Merge
                        speechSegments.remove(lastSegment)
                        currentSpeechStart = lastSegment.startUs
                    } else {
                        currentSpeechStart = chunk.timeUs
                    }
                }
            } else {
                // Silence
                if (currentSpeechStart != null) {
                    val endTimeUs = chunk.timeUs
                    val startIndex = (currentSpeechStart!! * TARGET_SAMPLE_RATE) / 1_000_000
                    val endIndex = (endTimeUs * TARGET_SAMPLE_RATE) / 1_000_000

                    speechSegments.offer(SpeechSegment(
                        startUs = currentSpeechStart!!,
                        endUs = endTimeUs,
                        startIndex = startIndex,
                        endIndex = endIndex
                    ))

                    lastSpeechEnd = endTimeUs
                    currentSpeechStart = null
                }
            }
        }

        Log.d(TAG, "RMS analysis thread stopped")
    }

    /**
     * Finalize segments after simulated recording stops
     */
    private fun finalizeSegments(totalDurationUs: Long) {
        // Close open speech segment
        if (currentSpeechStart != null) {
            val startIndex = (currentSpeechStart!! * TARGET_SAMPLE_RATE) / 1_000_000
            val endIndex = (totalDurationUs * TARGET_SAMPLE_RATE) / 1_000_000

            speechSegments.offer(SpeechSegment(
                startUs = currentSpeechStart!!,
                endUs = totalDurationUs,
                startIndex = startIndex,
                endIndex = endIndex
            ))
            currentSpeechStart = null
        }

        // Add padding and merge overlapping segments
        if (speechSegments.isNotEmpty()) {
            val paddedSegments = mutableListOf<SpeechSegment>()

            for (segment in speechSegments) {
                val paddedStart = max(0, segment.startUs - BUFFER_BEFORE_MS * 1000)
                val paddedEnd = min(totalDurationUs, segment.endUs + BUFFER_AFTER_MS * 1000)
                val startIndex = (paddedStart * TARGET_SAMPLE_RATE) / 1_000_000
                val endIndex = (paddedEnd * TARGET_SAMPLE_RATE) / 1_000_000

                paddedSegments.add(SpeechSegment(
                    startUs = paddedStart,
                    endUs = paddedEnd,
                    startIndex = startIndex,
                    endIndex = endIndex
                ))
            }

            val merged = mergeOverlappingSegments(paddedSegments)
            speechSegments.clear()
            speechSegments.addAll(merged)

            Log.d(TAG, "Finalized ${merged.size} speech segments")
        }
    }

    /**
     * Merge overlapping segments
     */
    private fun mergeOverlappingSegments(segments: List<SpeechSegment>): List<SpeechSegment> {
        if (segments.isEmpty()) return emptyList()

        val sorted = segments.sortedBy { it.startUs }
        val merged = mutableListOf<SpeechSegment>()
        var current = sorted.first()

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.startUs <= current.endUs) {
                current = SpeechSegment(
                    startUs = current.startUs,
                    endUs = max(current.endUs, next.endUs),
                    startIndex = current.startIndex,
                    endIndex = max(current.endIndex, next.endIndex)
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)

        return merged
    }

    /**
     * Extract trimmed audio from samples based on speech segments
     */
    private data class TrimResult(
        val trimmedBytes: ByteArray,
        val speechDurationMs: Long,
        val segmentCount: Int
    )

    private fun extractTrimmedAudio(samples: ShortArray, totalDurationUs: Long): TrimResult {
        if (speechSegments.isEmpty()) {
            Log.w(TAG, "No speech detected")
            return TrimResult(
                trimmedBytes = samplesToWav(ShortArray(TARGET_SAMPLE_RATE)), // 1 second silence
                speechDurationMs = 0,
                segmentCount = 0
            )
        }

        val speechDurationUs = speechSegments.sumOf { it.endUs - it.startUs }
        val speechSamples = mutableListOf<Short>()

        for (segment in speechSegments) {
            val startSample = segment.startIndex.toInt().coerceIn(0, samples.size)
            val endSample = segment.endIndex.toInt().coerceIn(startSample, samples.size)

            for (i in startSample until endSample) {
                speechSamples.add(samples[i])
            }
        }

        Log.d(TAG, "Extracted ${speechSamples.size} speech samples")

        return TrimResult(
            trimmedBytes = samplesToWav(speechSamples.toShortArray()),
            speechDurationMs = speechDurationUs / 1000,
            segmentCount = speechSegments.size
        )
    }

    /**
     * Convert samples to WAV bytes
     */
    private fun samplesToWav(samples: ShortArray): ByteArray {
        val tempFile = File(context.cacheDir, "benchmark_temp.wav")
        try {
            tempFile.delete()

            val channelCount = 1
            val bitsPerSample = 16
            val byteRate = TARGET_SAMPLE_RATE * channelCount * bitsPerSample / 8
            val blockAlign = channelCount * bitsPerSample / 8
            val dataSize = samples.size * 2
            val fileSize = 36 + dataSize

            RandomAccessFile(tempFile, "rw").use { raf ->
                raf.writeBytes("RIFF")
                raf.writeIntLE(fileSize)
                raf.writeBytes("WAVE")

                raf.writeBytes("fmt ")
                raf.writeIntLE(16)
                raf.writeShortLE(1)
                raf.writeShortLE(channelCount)
                raf.writeIntLE(TARGET_SAMPLE_RATE)
                raf.writeIntLE(byteRate)
                raf.writeShortLE(blockAlign)
                raf.writeShortLE(bitsPerSample)

                raf.writeBytes("data")
                raf.writeIntLE(dataSize)

                val buffer = ByteBuffer.allocate(samples.size * 2)
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                buffer.asShortBuffer().put(samples)
                raf.write(buffer.array())
            }

            return tempFile.readBytes()
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Calculate RMS in decibels
     */
    private fun calculateRmsDb(samples: ShortArray): Float {
        if (samples.isEmpty()) return -100f

        var sum = 0.0
        for (sample in samples) {
            val sampleDouble = sample.toDouble()
            sum += sampleDouble * sampleDouble
        }

        val rms = sqrt(sum / samples.size)
        return if (rms > 0) {
            (20 * log10(rms / Short.MAX_VALUE)).toFloat()
        } else {
            -100f
        }
    }

    /**
     * Decode audio file to PCM samples
     */
    private fun decodeAudioToSamples(file: File): ShortArray? {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null

        return try {
            extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                Log.e(TAG, "No audio track found")
                return null
            }

            extractor.selectTrack(audioTrackIndex)

            val sampleRate = try {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } catch (e: Exception) { 16000 }

            val channelCount = try {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } catch (e: Exception) { 1 }

            Log.d(TAG, "Audio format: ${audioFormat.getString(MediaFormat.KEY_MIME)}, " +
                    "rate: $sampleRate, channels: $channelCount")

            // Create decoder
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()

            // Decode
            val samples = mutableListOf<Short>()
            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false

            while (!isOutputEOS) {
                // Feed input
                if (!isInputEOS) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)

                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isInputEOS = true
                        } else {
                            decoder.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                // Get output
                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        while (shortBuffer.hasRemaining()) {
                            samples.add(shortBuffer.get())
                        }
                    }

                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        isOutputEOS = true
                    }
                }
            }

            // Resample to 16kHz if needed
            val finalSamples = if (sampleRate != TARGET_SAMPLE_RATE || channelCount != 1) {
                resample(samples.toShortArray(), sampleRate, channelCount)
            } else {
                samples.toShortArray()
            }

            finalSamples

        } catch (e: Exception) {
            Log.e(TAG, "Error decoding audio", e)
            null
        } finally {
            decoder?.let {
                try { it.stop() } catch (e: Exception) {}
                try { it.release() } catch (e: Exception) {}
            }
            extractor?.release()
        }
    }

    /**
     * Resample audio to 16kHz mono
     */
    private fun resample(samples: ShortArray, originalRate: Int, channels: Int): ShortArray {
        // Convert to mono first if stereo
        val monoSamples = if (channels > 1) {
            val mono = ShortArray(samples.size / channels)
            for (i in mono.indices) {
                var sum = 0
                for (c in 0 until channels) {
                    sum += samples[i * channels + c]
                }
                mono[i] = (sum / channels).toShort()
            }
            mono
        } else {
            samples
        }

        // Resample to 16kHz
        if (originalRate == TARGET_SAMPLE_RATE) {
            return monoSamples
        }

        val ratio = originalRate.toDouble() / TARGET_SAMPLE_RATE
        val newLength = (monoSamples.size / ratio).toInt()
        val resampled = ShortArray(newLength)

        for (i in 0 until newLength) {
            val srcIndex = (i * ratio).toInt().coerceIn(0, monoSamples.size - 1)
            resampled[i] = monoSamples[srcIndex]
        }

        return resampled
    }

    // Little-endian write extensions
    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }
}
