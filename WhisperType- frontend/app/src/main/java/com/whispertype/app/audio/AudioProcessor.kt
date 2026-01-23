package com.whispertype.app.audio

import android.content.Context
import android.media.*
import android.util.Log
import com.whispertype.app.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * AudioProcessor - Processes audio to remove silent portions
 *
 * This class:
 * 1. Decodes M4A/AAC or OGG/Opus audio to raw PCM samples
 * 2. Detects speech segments using RMS amplitude analysis
 * 3. Outputs only the speech portions as a WAV file (simpler and more reliable than re-encoding)
 *
 * Purpose: Reduce audio duration to lower OpenAI Whisper API costs
 * (OpenAI charges $0.003 per minute of audio)
 *
 * Supported formats: M4A (AAC), OGG (Opus) → processes to → WAV (16-bit PCM, mono, 16kHz)
 *
 * MEMORY SAFETY:
 * - Properly releases MediaCodec and MediaExtractor resources
 * - Uses try-finally blocks for cleanup
 */
class AudioProcessor(private val context: Context) {

    companion object {
        private const val TAG = "AudioProcessor"
    }

    /**
     * Data class representing a time range in microseconds
     */
    private data class TimeRange(val startUs: Long, val endUs: Long)

    /**
     * Data class for decoded audio
     */
    private data class DecodedAudio(
        val samples: ShortArray,
        val sampleRate: Int,
        val channelCount: Int,
        val durationUs: Long
    )

    /**
     * Data class for processed audio result
     * Contains both the file and its format for correct API submission
     */
    data class ProcessedAudio(
        val file: File,
        val format: String,              // "wav" for processed, "m4a" for original
        val durationMs: Long,            // Duration of processed audio
        val originalDurationMs: Long     // Original recording duration (for billing)
    )

    /**
     * Process audio file to remove silence
     *
     * @param inputFile The original audio file (M4A or OGG)
     * @param inputFormat The format of input file ("m4a" or "ogg")
     * @return ProcessedAudio containing the file and its format
     */
    suspend fun trimSilence(inputFile: File, inputFormat: String = "m4a"): ProcessedAudio = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Starting silence trimming for: ${inputFile.absolutePath}")
            Log.d(TAG, "Original file size: ${inputFile.length()} bytes")
            
            // Step 1: Decode audio to PCM samples
            val decodedAudio = decodeAudioToSamples(inputFile)
            if (decodedAudio == null || decodedAudio.samples.isEmpty()) {
                Log.w(TAG, "Failed to decode audio, returning original")
                // Estimate duration from file size based on format
                val bitrate = if (inputFormat == "ogg") 24 else 64  // kbps
                val estimatedDurationMs = (inputFile.length() * 8 / bitrate).coerceAtLeast(1000)
                return@withContext ProcessedAudio(inputFile, inputFormat, estimatedDurationMs, estimatedDurationMs)
            }
            Log.d(TAG, "Decoded ${decodedAudio.samples.size} samples, " +
                    "rate: ${decodedAudio.sampleRate}Hz, " +
                    "duration: ${decodedAudio.durationUs / 1000}ms")
            
            // Step 2: Detect speech segments
            val speechSegments = detectSpeechSegments(
                decodedAudio.samples, 
                decodedAudio.sampleRate,
                decodedAudio.durationUs
            )
            if (speechSegments.isEmpty()) {
                Log.w(TAG, "No speech detected, returning original")
                val originalMs = decodedAudio.durationUs / 1000
                return@withContext ProcessedAudio(inputFile, inputFormat, originalMs, originalMs)
            }
            Log.d(TAG, "Found ${speechSegments.size} speech segments")
            
            // Step 3: Calculate actual trimmed duration
            val trimmedDurationUs = speechSegments.sumOf { it.endUs - it.startUs }
            val savingsPercent = if (decodedAudio.durationUs > 0) {
                ((decodedAudio.durationUs - trimmedDurationUs) * 100 / decodedAudio.durationUs).toInt()
            } else 0
            Log.d(TAG, "Trimmed duration: ${trimmedDurationUs / 1000}ms ($savingsPercent% savings)")
            
            // If savings are minimal (< MIN_SAVINGS_PERCENT%), skip processing to save CPU
            if (savingsPercent < Constants.MIN_SAVINGS_PERCENT) {
                Log.d(TAG, "Minimal savings ($savingsPercent%), skipping processing")
                val originalMs = decodedAudio.durationUs / 1000
                return@withContext ProcessedAudio(inputFile, inputFormat, originalMs, originalMs)
            }
            
            // Step 4: Extract speech samples and write to WAV
            val speechSamples = extractSpeechSamples(
                decodedAudio.samples,
                decodedAudio.sampleRate,
                speechSegments
            )
            
            if (speechSamples.isEmpty()) {
                Log.w(TAG, "No speech samples extracted, returning original")
                val originalMs = decodedAudio.durationUs / 1000
                return@withContext ProcessedAudio(inputFile, inputFormat, originalMs, originalMs)
            }
            
            Log.d(TAG, "Extracted ${speechSamples.size} speech samples")
            
            // Step 5: Write to WAV file
            val outputFile = File(context.cacheDir, Constants.PROCESSED_AUDIO_FILE_NAME)
            val success = writeWavFile(outputFile, speechSamples, decodedAudio.sampleRate)
            
            if (success && outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "Processed WAV file size: ${outputFile.length()} bytes")
                // Use trimmed duration for processed, original for billing
                val trimmedDurationMs = trimmedDurationUs / 1000
                val originalDurationMs = decodedAudio.durationUs / 1000
                return@withContext ProcessedAudio(outputFile, "wav", trimmedDurationMs, originalDurationMs)
            } else {
                Log.w(TAG, "WAV write failed, returning original")
                val originalMs = decodedAudio.durationUs / 1000
                return@withContext ProcessedAudio(inputFile, inputFormat, originalMs, originalMs)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio", e)
            // Estimate duration from file size based on format
            val bitrate = if (inputFormat == "ogg") 24 else 64  // kbps
            val estimatedDurationMs = (inputFile.length() * 8 / bitrate).coerceAtLeast(1000)
            return@withContext ProcessedAudio(inputFile, inputFormat, estimatedDurationMs, estimatedDurationMs)
        }
    }

    /**
     * Decode audio file to raw PCM samples
     * Supports M4A/AAC and OGG/Opus formats via MediaExtractor
     * Properly manages MediaCodec and MediaExtractor lifecycle
     *
     * PERFORMANCE OPTIMIZED: Uses pre-allocated arrays and bulk copy
     */
    private fun decodeAudioToSamples(file: File): DecodedAudio? {
        var extractorRef: MediaExtractor? = null
        var decoderRef: MediaCodec? = null

        return try {
            val extractor = MediaExtractor()
            extractorRef = extractor
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
                null
            } else {

            extractor.selectTrack(audioTrackIndex)

            // Get audio properties - sample rate and channel count
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
            val decoder = MediaCodec.createDecoderByType(mime)
            decoderRef = decoder
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()

            // PERFORMANCE FIX 1: Pre-allocate array based on estimated duration
            // Estimate: 60 seconds max at 16kHz mono = 960,000 samples
            // We'll grow if needed, but this covers most cases without reallocation
            val estimatedSamples = sampleRate * 60  // 60 seconds worth
            var samples = ShortArray(estimatedSamples)
            var sampleCount = 0

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
                when {
                    outputBufferIndex >= 0 -> {
                        if (bufferInfo.size > 0) {
                            val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            // Convert bytes to shorts (16-bit PCM)
                            val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val newSamplesCount = shortBuffer.remaining()

                            // PERFORMANCE FIX 1: Grow array if needed, then bulk copy
                            if (sampleCount + newSamplesCount > samples.size) {
                                // Double the size to minimize reallocations
                                val newSize = max(samples.size * 2, sampleCount + newSamplesCount)
                                samples = samples.copyOf(newSize)
                            }

                            // Bulk copy directly into pre-allocated array
                            shortBuffer.get(samples, sampleCount, newSamplesCount)
                            sampleCount += newSamplesCount
                        }

                        decoder.releaseOutputBuffer(outputBufferIndex, false)

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            isOutputEOS = true
                        }
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = decoder.outputFormat
                        Log.d(TAG, "Decoder output format changed: $newFormat")
                    }
                }
            }

            // Trim array to actual size
            val finalSamples = if (sampleCount < samples.size) {
                samples.copyOf(sampleCount)
            } else {
                samples
            }

            // IMPORTANT: Calculate duration from actual decoded samples, not MediaFormat.KEY_DURATION
            // MediaFormat.KEY_DURATION can return incorrect hardcoded values (e.g., 60 seconds)
            // Sample-based calculation: duration = samples / sampleRate / channelCount
            val actualDurationUs = (finalSamples.size.toLong() * 1_000_000L / sampleRate / channelCount)

            Log.d(TAG, "Calculated duration from samples: ${actualDurationUs / 1000}ms (${finalSamples.size} samples)")

            DecodedAudio(
                samples = finalSamples,
                sampleRate = sampleRate,
                channelCount = channelCount,
                durationUs = actualDurationUs
            )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding audio", e)
            null
        } finally {
            // Clean up resources in reverse order of creation
            decoderRef?.let {
                try {
                    if (it.name != null) { // Check if started
                        it.stop()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Decoder stop: ${e.message}")
                }
                try {
                    it.release()
                } catch (e: Exception) {
                    Log.d(TAG, "Decoder release: ${e.message}")
                }
            }
            extractorRef?.let {
                try {
                    it.release()
                } catch (e: Exception) {
                    Log.d(TAG, "Extractor release: ${e.message}")
                }
            }
        }
    }

    /**
     * Detect speech segments in the audio samples using RMS analysis
     *
     * PERFORMANCE OPTIMIZED: Uses index-based RMS calculation to avoid array allocations
     */
    private fun detectSpeechSegments(
        samples: ShortArray,
        sampleRate: Int,
        durationUs: Long
    ): List<TimeRange> {
        if (samples.isEmpty()) return emptyList()

        val windowSamples = (sampleRate * Constants.ANALYSIS_WINDOW_MS / 1000).toInt()
        val segments = mutableListOf<TimeRange>()

        var speechStart: Long? = null
        var lastSpeechEnd: Long = 0

        // Analyze in windows
        var windowIndex = 0
        while (windowIndex * windowSamples < samples.size) {
            val start = windowIndex * windowSamples
            val end = min(start + windowSamples, samples.size)

            // PERFORMANCE FIX 2: Use index-based RMS calculation (no array allocation)
            val rmsDb = calculateRmsDbRange(samples, start, end)
            val isSpeech = rmsDb > Constants.SILENCE_THRESHOLD_DB

            val currentTimeUs = (windowIndex * Constants.ANALYSIS_WINDOW_MS * 1000)

            if (isSpeech) {
                if (speechStart == null) {
                    // Check if we should merge with previous segment
                    if (segments.isNotEmpty() &&
                        currentTimeUs - lastSpeechEnd < Constants.MIN_SILENCE_DURATION_MS * 1000) {
                        // Merge: extend previous segment instead of starting new
                        speechStart = segments.removeAt(segments.lastIndex).startUs
                    } else {
                        // Start new segment
                        speechStart = currentTimeUs
                    }
                }
            } else {
                // Silence
                if (speechStart != null) {
                    // End of speech segment
                    lastSpeechEnd = currentTimeUs
                    segments.add(TimeRange(speechStart, currentTimeUs))
                    speechStart = null
                }
            }

            windowIndex++
        }

        // Handle case where audio ends during speech
        if (speechStart != null) {
            segments.add(TimeRange(speechStart, durationUs))
        }

        // Add buffers to each segment
        return segments.map { segment ->
            TimeRange(
                startUs = max(0, segment.startUs - Constants.AUDIO_BUFFER_BEFORE_MS * 1000),
                endUs = min(durationUs, segment.endUs + Constants.AUDIO_BUFFER_AFTER_MS * 1000)
            )
        }.mergeOverlapping()
    }

    /**
     * Merge overlapping or adjacent time ranges
     */
    private fun List<TimeRange>.mergeOverlapping(): List<TimeRange> {
        if (isEmpty()) return this
        
        val sorted = sortedBy { it.startUs }
        val merged = mutableListOf<TimeRange>()
        var current = sorted.first()
        
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.startUs <= current.endUs) {
                current = TimeRange(current.startUs, max(current.endUs, next.endUs))
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        
        return merged
    }

    /**
     * Calculate RMS amplitude in decibels for a range of samples
     *
     * PERFORMANCE FIX 2: Takes start/end indices to avoid creating new arrays
     *
     * @param samples The full samples array
     * @param start Start index (inclusive)
     * @param end End index (exclusive)
     */
    private fun calculateRmsDbRange(samples: ShortArray, start: Int, end: Int): Float {
        if (start >= end || start < 0 || end > samples.size) return -100f

        var sum = 0.0
        for (i in start until end) {
            val sample = samples[i].toDouble()
            sum += sample * sample
        }

        val count = end - start
        val rms = kotlin.math.sqrt(sum / count)

        return if (rms > 0) {
            (20 * log10(rms / Short.MAX_VALUE)).toFloat()
        } else {
            -100f
        }
    }

    /**
     * Extract speech samples from the decoded audio based on segments
     *
     * PERFORMANCE OPTIMIZED: Uses pre-allocated array and bulk copy
     */
    private fun extractSpeechSamples(
        samples: ShortArray,
        sampleRate: Int,
        segments: List<TimeRange>
    ): ShortArray {
        // Calculate total size needed
        var totalSize = 0
        for (segment in segments) {
            val startSample = (segment.startUs * sampleRate / 1_000_000).toInt()
            val endSample = min((segment.endUs * sampleRate / 1_000_000).toInt(), samples.size)
            if (startSample < endSample && startSample < samples.size) {
                totalSize += endSample - startSample
            }
        }

        // Pre-allocate array with exact size needed
        val speechSamples = ShortArray(totalSize)
        var offset = 0

        for (segment in segments) {
            // Convert time to sample indices
            val startSample = (segment.startUs * sampleRate / 1_000_000).toInt()
            val endSample = min((segment.endUs * sampleRate / 1_000_000).toInt(), samples.size)

            if (startSample < endSample && startSample < samples.size) {
                val length = endSample - startSample
                // Bulk copy using System.arraycopy
                System.arraycopy(samples, startSample, speechSamples, offset, length)
                offset += length
            }
        }

        return speechSamples
    }

    /**
     * Write PCM samples to a WAV file
     *
     * WAV format is simpler and more reliable than re-encoding to AAC.
     * OpenAI Whisper API supports WAV files.
     *
     * PERFORMANCE OPTIMIZED: Uses bulk write for sample data
     */
    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int): Boolean {
        try {
            file.delete()

            val channelCount = 1  // Mono
            val bitsPerSample = 16
            val byteRate = sampleRate * channelCount * bitsPerSample / 8
            val blockAlign = channelCount * bitsPerSample / 8
            val dataSize = samples.size * 2  // 2 bytes per sample (16-bit)
            val fileSize = 36 + dataSize  // Header (44 bytes) - 8 bytes for RIFF header = 36

            RandomAccessFile(file, "rw").use { raf ->
                // RIFF header
                raf.writeBytes("RIFF")
                raf.writeIntLE(fileSize)
                raf.writeBytes("WAVE")

                // fmt subchunk
                raf.writeBytes("fmt ")
                raf.writeIntLE(16)  // Subchunk1Size for PCM
                raf.writeShortLE(1)  // AudioFormat (1 = PCM)
                raf.writeShortLE(channelCount)
                raf.writeIntLE(sampleRate)
                raf.writeIntLE(byteRate)
                raf.writeShortLE(blockAlign)
                raf.writeShortLE(bitsPerSample)

                // data subchunk
                raf.writeBytes("data")
                raf.writeIntLE(dataSize)

                // PERFORMANCE FIX 3: Bulk write samples using ShortBuffer
                val buffer = ByteBuffer.allocate(samples.size * 2)
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                buffer.asShortBuffer().put(samples)  // Bulk put - much faster than loop
                raf.write(buffer.array())
            }

            Log.d(TAG, "WAV file written: ${file.length()} bytes, " +
                    "${samples.size} samples, ${sampleRate}Hz")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error writing WAV file", e)
            return false
        }
    }
    
    /**
     * Result of WAV to OGG encoding
     */
    data class EncodingResult(
        val bytes: ByteArray,
        val format: String,  // "ogg" or "wav"
        val wasCompressed: Boolean
    )

    /**
     * Encode WAV audio bytes to OGG/Opus format for smaller file size
     * Can be used for audio compression when upload bandwidth is limited
     *
     * @param wavBytes WAV file bytes (16kHz, mono, 16-bit PCM)
     * @return EncodingResult with the encoded bytes and the actual format
     */
    suspend fun encodeWavToOgg(wavBytes: ByteArray): EncodingResult = withContext(Dispatchers.Default) {
        Log.d(TAG, "Encoding WAV to OGG, input size: ${wavBytes.size} bytes")

        // Parse WAV header to get PCM samples
        val samples = parseWavToSamples(wavBytes)
        if (samples.isEmpty()) {
            Log.w(TAG, "Failed to parse WAV, returning original as WAV")
            return@withContext EncodingResult(wavBytes, "wav", false)
        }

        Log.d(TAG, "Parsed ${samples.size} samples from WAV")

        val tempOggFile = File(context.cacheDir, "temp_encode_output.ogg")

        try {
            // Encode samples directly to OGG using MediaCodec (no MediaExtractor)
            val success = encodeSamplesToOpus(samples, 16000, tempOggFile)

            if (success && tempOggFile.exists() && tempOggFile.length() > 0) {
                val oggBytes = tempOggFile.readBytes()
                Log.d(TAG, "OGG encoding complete: ${wavBytes.size} -> ${oggBytes.size} bytes " +
                        "(${100 - oggBytes.size * 100 / wavBytes.size}% reduction)")
                return@withContext EncodingResult(oggBytes, "ogg", true)
            } else {
                Log.w(TAG, "OGG encoding failed, returning original as WAV")
                return@withContext EncodingResult(wavBytes, "wav", false)
            }
        } finally {
            tempOggFile.delete()
        }
    }

    /**
     * Parse WAV bytes to PCM samples
     */
    private fun parseWavToSamples(wavBytes: ByteArray): ShortArray {
        try {
            if (wavBytes.size < 44) return shortArrayOf()

            // Check RIFF header
            val riff = String(wavBytes, 0, 4)
            val wave = String(wavBytes, 8, 4)
            if (riff != "RIFF" || wave != "WAVE") {
                Log.e(TAG, "Invalid WAV header")
                return shortArrayOf()
            }

            // Find data chunk
            var dataStart = 12
            while (dataStart < wavBytes.size - 8) {
                val chunkId = String(wavBytes, dataStart, 4)
                val chunkSize = ByteBuffer.wrap(wavBytes, dataStart + 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).int

                if (chunkId == "data") {
                    // Found data chunk
                    val dataOffset = dataStart + 8
                    val numSamples = chunkSize / 2  // 16-bit samples

                    val samples = ShortArray(numSamples)
                    val buffer = ByteBuffer.wrap(wavBytes, dataOffset, chunkSize)
                        .order(ByteOrder.LITTLE_ENDIAN)

                    for (i in 0 until numSamples) {
                        samples[i] = buffer.short
                    }

                    return samples
                }

                dataStart += 8 + chunkSize
            }

            Log.e(TAG, "Data chunk not found in WAV")
            return shortArrayOf()

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WAV", e)
            return shortArrayOf()
        }
    }

    /**
     * Encode PCM samples directly to OGG/Opus using MediaCodec
     * This bypasses MediaExtractor which doesn't reliably work with WAV files
     * Android 10+ supports Opus encoding
     *
     * @param samples PCM samples (16-bit, mono)
     * @param sampleRate Sample rate (typically 16000)
     * @param outputOgg Output OGG file
     */
    private fun encodeSamplesToOpus(samples: ShortArray, sampleRate: Int, outputOgg: File): Boolean {
        var encoderRef: MediaCodec? = null
        var muxerRef: MediaMuxer? = null

        return try {
            Log.d(TAG, "Encoding ${samples.size} samples to Opus at ${sampleRate}Hz")

            // Set up Opus encoder
            val outputFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                sampleRate,
                1  // mono
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 24000)  // 24kbps for good quality/size balance
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            encoderRef = encoder
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // Create OGG muxer
            val muxer = MediaMuxer(outputOgg.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
            muxerRef = muxer
            var trackIndex = -1
            var muxerStarted = false

            // Convert samples to bytes (little-endian 16-bit PCM)
            val sampleBytes = ByteBuffer.allocate(samples.size * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
            sampleBytes.asShortBuffer().put(samples)
            sampleBytes.rewind()

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false
            var inputSampleOffset = 0  // Track sample position for presentation time

            val inputChunkSize = 3200  // 100ms at 16kHz = 1600 samples = 3200 bytes

            while (!isOutputEOS) {
                // Feed input to encoder
                if (!isInputEOS) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val encoderInputBuffer = encoder.getInputBuffer(inputBufferIndex)!!
                        encoderInputBuffer.clear()

                        val remaining = sampleBytes.remaining()
                        if (remaining <= 0) {
                            encoder.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isInputEOS = true
                            Log.d(TAG, "Input EOS reached")
                        } else {
                            val bytesToWrite = min(remaining, min(encoderInputBuffer.capacity(), inputChunkSize))
                            val oldLimit = sampleBytes.limit()
                            sampleBytes.limit(sampleBytes.position() + bytesToWrite)
                            encoderInputBuffer.put(sampleBytes)
                            sampleBytes.limit(oldLimit)

                            // Calculate presentation time in microseconds
                            val presentationTimeUs = (inputSampleOffset.toLong() * 1_000_000L / sampleRate)
                            inputSampleOffset += bytesToWrite / 2  // 2 bytes per sample

                            encoder.queueInputBuffer(
                                inputBufferIndex, 0, bytesToWrite,
                                presentationTimeUs, 0
                            )
                        }
                    }
                }

                // Get output from encoder
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    outputBufferIndex >= 0 -> {
                        val encoderOutputBuffer = encoder.getOutputBuffer(outputBufferIndex)!!

                        if (bufferInfo.size > 0 && muxerStarted) {
                            encoderOutputBuffer.position(bufferInfo.offset)
                            encoderOutputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encoderOutputBuffer, bufferInfo)
                        }

                        encoder.releaseOutputBuffer(outputBufferIndex, false)

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            isOutputEOS = true
                            Log.d(TAG, "Output EOS reached")
                        }
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                            Log.d(TAG, "Muxer started, track index: $trackIndex")
                        }
                    }
                }
            }

            // Clean up muxer
            if (muxerStarted) {
                muxer.stop()
                Log.d(TAG, "Muxer stopped successfully")
            }
            muxer.release()
            muxerRef = null

            true

        } catch (e: Exception) {
            Log.e(TAG, "Error encoding to Opus: ${e.message}", e)
            false
        } finally {
            encoderRef?.let {
                try { it.stop() } catch (e: Exception) { Log.d(TAG, "Encoder stop: ${e.message}") }
                try { it.release() } catch (e: Exception) { Log.d(TAG, "Encoder release: ${e.message}") }
            }
            muxerRef?.let {
                try { it.release() } catch (e: Exception) { Log.d(TAG, "Muxer release: ${e.message}") }
            }
        }
    }

    // Extension functions for little-endian writing
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
