package com.whispertype.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * DualFlowRecorder - Records audio once and processes through both WAV and OGG pipelines simultaneously
 *
 * This enables accurate comparison between:
 * - WAV flow: RMS analysis → Trimmed WAV output
 * - OGG flow: RMS analysis + Parallel Opus encoding → Trimmed OGG output
 *
 * Both pipelines process the same audio independently during recording, so when recording
 * stops, both outputs are ready immediately (encoding is already done).
 */
class DualFlowRecorder(private val context: Context) {

    companion object {
        private const val TAG = "DualFlowRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SAMPLES = 320 // 20ms at 16kHz
        private const val BUFFER_SIZE_SAMPLES = 960_000 // 60 seconds max
        private const val OPUS_BIT_RATE = 24000

        // RMS thresholds - Using dB-based detection (same as ParallelOpusRecorder)
        private const val SPEECH_THRESHOLD_DB = -40f // Decibel threshold for speech detection
        private const val SILENCE_DURATION_MS = 1500L
        private const val MIN_SPEECH_DURATION_MS = 300L
        private const val BUFFER_BEFORE_MS = 200L
        private const val BUFFER_AFTER_MS = 300L

        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var recordingThread: Thread? = null
    private var wavRmsThread: Thread? = null
    private var oggRmsThread: Thread? = null
    private var oggEncoderThread: Thread? = null

    private var amplitudeCallback: AudioRecorder.AmplitudeCallback? = null
    private var currentCallback: DualRecordingCallback? = null

    // WAV Pipeline
    private val wavAudioBuffer = ShortArray(BUFFER_SIZE_SAMPLES)
    private var wavWriteIndex = 0
    private val wavRmsQueue = ConcurrentLinkedQueue<ChunkData>()
    private val wavSpeechSegments = ConcurrentLinkedQueue<SpeechSegment>()

    // OGG Pipeline
    private val oggAudioBuffer = ShortArray(BUFFER_SIZE_SAMPLES)
    private var oggWriteIndex = 0
    private val oggRmsQueue = ConcurrentLinkedQueue<ChunkData>()
    private val oggEncoderQueue = ConcurrentLinkedQueue<ChunkData>()
    private val oggSpeechSegments = ConcurrentLinkedQueue<SpeechSegment>()
    private val oggEncodedFrames = ConcurrentLinkedQueue<EncodedFrame>()

    private var encoder: MediaCodec? = null

    data class ChunkData(
        val samples: ShortArray,
        val startIndex: Int,
        val timestamp: Long
    )

    data class SpeechSegment(
        val startIndex: Int,
        val endIndex: Int,
        val startUs: Long,
        val endUs: Long
    )

    data class EncodedFrame(
        val data: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int,
        val size: Int
    )

    data class DualResult(
        val wavBytes: ByteArray,
        val oggBytes: ByteArray,
        val wavMetadata: RmsMetadata,
        val oggMetadata: RmsMetadata
    )

    data class RmsMetadata(
        val speechSegments: List<SpeechSegment>,
        val totalSamples: Int,
        val silenceTrimmingApplied: Boolean  // True if trimming happened, false if fallback
    )

    interface DualRecordingCallback {
        fun onRecordingStarted()
        fun onRecordingStopped(result: DualResult)
        fun onRecordingError(error: String)
    }

    fun setAmplitudeCallback(callback: AudioRecorder.AmplitudeCallback?) {
        this.amplitudeCallback = callback
    }

    fun startRecording(callback: DualRecordingCallback) {
        if (isRecording.get()) {
            Log.w(TAG, "Already recording")
            return
        }

        if (!isSupported()) {
            callback.onRecordingError("Requires Android 10+ for Opus encoding")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            callback.onRecordingError("Microphone permission not granted")
            return
        }

        currentCallback = callback

        // Reset state
        wavWriteIndex = 0
        oggWriteIndex = 0
        wavRmsQueue.clear()
        oggRmsQueue.clear()
        oggEncoderQueue.clear()
        wavSpeechSegments.clear()
        oggSpeechSegments.clear()
        oggEncodedFrames.clear()
        wavAudioBuffer.fill(0)
        oggAudioBuffer.fill(0)

        try {
            // Initialize AudioRecord
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = maxOf(minBufferSize, CHUNK_SAMPLES * 4)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                callback.onRecordingError("Failed to initialize AudioRecord")
                return
            }

            // Initialize Opus encoder for OGG pipeline
            initializeEncoder()

            isRecording.set(true)

            // Start all threads
            startWavRmsThread()
            startOggRmsThread()
            startOggEncoderThread()
            startRecordingThread()

            audioRecord?.startRecording()
            callback.onRecordingStarted()
            Log.d(TAG, "Dual recording started")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            cleanup()
            callback.onRecordingError(e.message ?: "Unknown error")
        }
    }

    private fun initializeEncoder() {
        try {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                SAMPLE_RATE,
                1 // Mono
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, OPUS_BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK_SAMPLES * 2)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setInteger(MediaFormat.KEY_COMPLEXITY, 5)
                }
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder?.start()

            Log.d(TAG, "Opus encoder initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize encoder", e)
            throw e
        }
    }

    private fun startRecordingThread() {
        recordingThread = thread(name = "DualRecording") {
            val audioChunk = ShortArray(CHUNK_SAMPLES)
            var totalSamplesWritten = 0L  // Track total samples for timestamp calculation

            while (isRecording.get()) {
                val samplesRead = audioRecord?.read(audioChunk, 0, CHUNK_SAMPLES) ?: 0

                if (samplesRead > 0) {
                    // Use sample-count-based timestamp (same as ParallelOpusRecorder)
                    // This ensures speech segments and encoder frames use the same time base
                    val timestampUs = (totalSamplesWritten * 1_000_000L) / SAMPLE_RATE

                    // Clone chunk for each pipeline to avoid race conditions
                    val wavChunk = audioChunk.copyOf(samplesRead)
                    val oggChunk = audioChunk.copyOf(samplesRead)

                    // Write to WAV circular buffer
                    synchronized(wavAudioBuffer) {
                        for (i in 0 until samplesRead) {
                            wavAudioBuffer[wavWriteIndex] = wavChunk[i]
                            wavWriteIndex = (wavWriteIndex + 1) % BUFFER_SIZE_SAMPLES
                        }
                    }

                    // Write to OGG circular buffer
                    synchronized(oggAudioBuffer) {
                        for (i in 0 until samplesRead) {
                            oggAudioBuffer[oggWriteIndex] = oggChunk[i]
                            oggWriteIndex = (oggWriteIndex + 1) % BUFFER_SIZE_SAMPLES
                        }
                    }

                    // Feed to both RMS pipelines
                    val wavStartIndex = (wavWriteIndex - samplesRead + BUFFER_SIZE_SAMPLES) % BUFFER_SIZE_SAMPLES
                    val oggStartIndex = (oggWriteIndex - samplesRead + BUFFER_SIZE_SAMPLES) % BUFFER_SIZE_SAMPLES

                    wavRmsQueue.offer(ChunkData(wavChunk, wavStartIndex, timestampUs))
                    oggRmsQueue.offer(ChunkData(oggChunk, oggStartIndex, timestampUs))

                    // Feed to OGG encoder pipeline
                    oggEncoderQueue.offer(ChunkData(oggChunk, oggStartIndex, timestampUs))

                    totalSamplesWritten += samplesRead
                }
            }

            Log.d(TAG, "Recording thread finished, total samples: $totalSamplesWritten")
        }
    }

    private fun startWavRmsThread() {
        wavRmsThread = thread(name = "WAV-RMS") {
            var isSpeaking = false
            var silenceStartTime = 0L
            var currentSpeechStart = -1
            var currentSpeechStartUs = 0L

            while (isRecording.get() || wavRmsQueue.isNotEmpty()) {
                val chunk = wavRmsQueue.poll()
                if (chunk == null) {
                    Thread.sleep(10)
                    continue
                }

                val rmsDb = calculateRmsDb(chunk.samples)
                // Convert dB to amplitude for visualization (-40dB to 0dB -> 0 to 32767)
                val amplitude = ((rmsDb + 40f) / 40f * 32767).toInt().coerceIn(0, 32767)
                amplitudeCallback?.onAmplitude(amplitude)

                val isSpeech = rmsDb > SPEECH_THRESHOLD_DB

                if (isSpeech) {
                    if (!isSpeaking) {
                        isSpeaking = true
                        currentSpeechStart = chunk.startIndex
                        currentSpeechStartUs = chunk.timestamp
                    }
                    silenceStartTime = 0
                } else {
                    if (isSpeaking) {
                        if (silenceStartTime == 0L) {
                            silenceStartTime = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - silenceStartTime > SILENCE_DURATION_MS) {
                            val duration = (chunk.timestamp - currentSpeechStartUs) / 1000
                            if (duration >= MIN_SPEECH_DURATION_MS) {
                                wavSpeechSegments.offer(
                                    SpeechSegment(
                                        currentSpeechStart,
                                        chunk.startIndex,
                                        currentSpeechStartUs,
                                        chunk.timestamp
                                    )
                                )
                            }
                            isSpeaking = false
                            silenceStartTime = 0
                        }
                    }
                }
            }

            Log.d(TAG, "WAV RMS thread finished, detected ${wavSpeechSegments.size} segments")
        }
    }

    private fun startOggRmsThread() {
        oggRmsThread = thread(name = "OGG-RMS") {
            var isSpeaking = false
            var silenceStartTime = 0L
            var currentSpeechStart = -1
            var currentSpeechStartUs = 0L

            while (isRecording.get() || oggRmsQueue.isNotEmpty()) {
                val chunk = oggRmsQueue.poll()
                if (chunk == null) {
                    Thread.sleep(10)
                    continue
                }

                val rmsDb = calculateRmsDb(chunk.samples)
                val isSpeech = rmsDb > SPEECH_THRESHOLD_DB

                if (isSpeech) {
                    if (!isSpeaking) {
                        isSpeaking = true
                        currentSpeechStart = chunk.startIndex
                        currentSpeechStartUs = chunk.timestamp
                    }
                    silenceStartTime = 0
                } else {
                    if (isSpeaking) {
                        if (silenceStartTime == 0L) {
                            silenceStartTime = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - silenceStartTime > SILENCE_DURATION_MS) {
                            val duration = (chunk.timestamp - currentSpeechStartUs) / 1000
                            if (duration >= MIN_SPEECH_DURATION_MS) {
                                oggSpeechSegments.offer(
                                    SpeechSegment(
                                        currentSpeechStart,
                                        chunk.startIndex,
                                        currentSpeechStartUs,
                                        chunk.timestamp
                                    )
                                )
                            }
                            isSpeaking = false
                            silenceStartTime = 0
                        }
                    }
                }
            }

            Log.d(TAG, "OGG RMS thread finished, detected ${oggSpeechSegments.size} segments")
        }
    }

    private fun startOggEncoderThread() {
        oggEncoderThread = thread(name = "OGG-Encoder") {
            val bufferInfo = MediaCodec.BufferInfo()

            while (isRecording.get() || oggEncoderQueue.isNotEmpty()) {
                val chunk = oggEncoderQueue.poll()
                if (chunk == null) {
                    // Drain encoder output even when no input
                    drainEncoderOutput(bufferInfo)
                    Thread.sleep(10)
                    continue
                }

                try {
                    // Feed input to encoder
                    val inputIndex = encoder?.dequeueInputBuffer(10_000) ?: -1
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder?.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                            for (sample in chunk.samples) {
                                inputBuffer.putShort(sample)
                            }

                            // Use chunk's timestamp (sample-count based, same as speech segments)
                            encoder?.queueInputBuffer(
                                inputIndex,
                                0,
                                chunk.samples.size * 2,
                                chunk.timestamp,
                                0
                            )
                        }
                    }

                    // Drain output
                    drainEncoderOutput(bufferInfo)

                } catch (e: Exception) {
                    Log.e(TAG, "Error in encoder thread", e)
                }
            }

            // Signal EOS and drain
            try {
                val eosIndex = encoder?.dequeueInputBuffer(10_000) ?: -1
                if (eosIndex >= 0) {
                    encoder?.queueInputBuffer(
                        eosIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }

                // Drain remaining output
                var sawEos = false
                while (!sawEos) {
                    val outputIndex = encoder?.dequeueOutputBuffer(bufferInfo, 10_000) ?: -1
                    if (outputIndex >= 0) {
                        val outputBuffer = encoder?.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val frameData = ByteArray(bufferInfo.size)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.get(frameData, 0, bufferInfo.size)

                            oggEncodedFrames.offer(
                                EncodedFrame(
                                    frameData,
                                    bufferInfo.presentationTimeUs,
                                    bufferInfo.flags,
                                    bufferInfo.size
                                )
                            )
                        }
                        encoder?.releaseOutputBuffer(outputIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawEos = true
                        }
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error draining encoder", e)
            }

            Log.d(TAG, "OGG encoder thread finished, encoded ${oggEncodedFrames.size} frames")
        }
    }

    private fun drainEncoderOutput(bufferInfo: MediaCodec.BufferInfo) {
        var outputIndex = encoder?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
        while (outputIndex >= 0) {
            val outputBuffer = encoder?.getOutputBuffer(outputIndex)
            if (outputBuffer != null && bufferInfo.size > 0) {
                val frameData = ByteArray(bufferInfo.size)
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.get(frameData, 0, bufferInfo.size)

                oggEncodedFrames.offer(
                    EncodedFrame(
                        frameData,
                        bufferInfo.presentationTimeUs,
                        bufferInfo.flags,
                        bufferInfo.size
                    )
                )
            }
            encoder?.releaseOutputBuffer(outputIndex, false)
            outputIndex = encoder?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
        }
    }

    fun stopRecording() {
        if (!isRecording.get()) {
            Log.w(TAG, "Not recording")
            return
        }

        Log.d(TAG, "Stopping dual recording...")
        isRecording.set(false)

        try {
            // Stop audio recording
            audioRecord?.stop()

            // Wait for all threads to finish processing
            recordingThread?.join(3000)
            wavRmsThread?.join(3000)
            oggRmsThread?.join(3000)
            oggEncoderThread?.join(5000) // Encoder might take longer

            // Calculate total duration for segment finalization
            val totalSamples = wavWriteIndex.coerceAtLeast(oggWriteIndex)
            val totalDurationUs = (totalSamples.toLong() * 1_000_000L) / SAMPLE_RATE
            
            // Finalize any open speech segments before creating outputs
            finalizeSpeechSegments(totalDurationUs)

            // Process both outputs
            val wavSegmentsList = wavSpeechSegments.toList()
            val oggSegmentsList = oggSpeechSegments.toList()
            
            val wavBytes = createWavOutput()
            val oggBytes = createOggOutput()

            // Determine if trimming was applied
            val wavTrimmingApplied = wavSegmentsList.isNotEmpty() && wavWriteIndex > SAMPLE_RATE
            val oggTrimmingApplied = oggSegmentsList.isNotEmpty() && oggWriteIndex > SAMPLE_RATE

            val result = DualResult(
                wavBytes = wavBytes,
                oggBytes = oggBytes,
                wavMetadata = RmsMetadata(wavSegmentsList, wavWriteIndex, wavTrimmingApplied),
                oggMetadata = RmsMetadata(oggSegmentsList, oggWriteIndex, oggTrimmingApplied)
            )

            currentCallback?.onRecordingStopped(result)
            Log.d(TAG, "Dual recording stopped: WAV=${wavBytes.size} bytes, OGG=${oggBytes.size} bytes")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            currentCallback?.onRecordingError(e.message ?: "Unknown error")
        } finally {
            cleanup()
        }
    }

    private fun createWavOutput(): ByteArray {
        val trimmedSamples = trimAudioBySpeech(wavAudioBuffer, wavWriteIndex, wavSpeechSegments.toList())
        return samplesToWav(trimmedSamples)
    }

    private fun createOggOutput(): ByteArray {
        if (oggEncodedFrames.isEmpty()) {
            Log.w(TAG, "No encoded frames available")
            return ByteArray(0)
        }

        val frames = oggEncodedFrames.toList()
        val segments = oggSpeechSegments.toList()

        Log.d(TAG, "OGG output: ${frames.size} encoded frames, ${segments.size} speech segments")

        // If no speech segments detected, use all frames
        if (segments.isEmpty()) {
            Log.w(TAG, "No speech segments detected, using all frames")
            return muxToOgg(frames)
        }

        // Debug: log timestamp ranges to verify alignment
        if (frames.isNotEmpty() && segments.isNotEmpty()) {
            Log.d(TAG, "Frame timestamps: ${frames.first().presentationTimeUs} - ${frames.last().presentationTimeUs} us")
            Log.d(TAG, "Segment timestamps: ${segments.first().startUs} - ${segments.last().endUs} us")
        }

        // Filter frames that fall within speech segments (with padding)
        val bufferBeforeUs = BUFFER_BEFORE_MS * 1000
        val bufferAfterUs = BUFFER_AFTER_MS * 1000

        val filteredFrames = frames.filter { frame ->
            segments.any { segment ->
                frame.presentationTimeUs >= (segment.startUs - bufferBeforeUs) &&
                frame.presentationTimeUs <= (segment.endUs + bufferAfterUs)
            }
        }

        Log.d(TAG, "Filtered to ${filteredFrames.size} frames (from ${frames.size})")

        // If filtering removed all frames (shouldn't happen), use all frames as fallback
        if (filteredFrames.isEmpty()) {
            Log.w(TAG, "Filtering removed all frames, using all frames as fallback")
            return muxToOgg(frames)
        }

        // Mux filtered frames to OGG
        return muxToOgg(filteredFrames)
    }

    private fun muxToOgg(frames: List<EncodedFrame>): ByteArray {
        val outputFile = File(context.cacheDir, "dual_test_${System.currentTimeMillis()}.ogg")
        var muxer: MediaMuxer? = null

        try {
            val outputFormat = encoder?.outputFormat ?: run {
                Log.e(TAG, "No encoder output format available")
                return ByteArray(0)
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
            val trackIndex = muxer.addTrack(outputFormat)
            muxer.start()

            val baseTimeUs = frames.firstOrNull()?.presentationTimeUs ?: 0L
            for (frame in frames) {
                val bufferInfo = MediaCodec.BufferInfo().apply {
                    set(0, frame.size, frame.presentationTimeUs - baseTimeUs, frame.flags)
                }
                muxer.writeSampleData(trackIndex, ByteBuffer.wrap(frame.data), bufferInfo)
            }

            muxer.stop()
            muxer.release()
            muxer = null

            return outputFile.readBytes()

        } catch (e: Exception) {
            Log.e(TAG, "Error muxing to OGG", e)
            return ByteArray(0)
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            outputFile.delete()
        }
    }

    /**
     * Finalize any open speech segments when recording stops.
     * This is critical - if you stop recording while speaking (before 1.5s silence),
     * the last speech segment would otherwise be lost.
     */
    private fun finalizeSpeechSegments(totalDurationUs: Long) {
        // We need to track the state of both RMS threads
        // Since they've finished, we need to check if there was an open segment
        
        // For WAV: If we have samples but no/few segments, the audio was probably speech
        // that didn't get closed. Use all recorded audio as a fallback.
        val wavSegmentCount = wavSpeechSegments.size
        val oggSegmentCount = oggSpeechSegments.size
        
        Log.d(TAG, "Finalizing segments: WAV=$wavSegmentCount, OGG=$oggSegmentCount, totalDurationUs=$totalDurationUs")
        
        // If very few segments were detected, add a segment covering all audio
        // This handles the case where speech continues until recording stops
        if (wavSegmentCount == 0 && wavWriteIndex > SAMPLE_RATE) {
            // No segments but we have more than 1 second of audio - use it all
            Log.d(TAG, "WAV: No segments detected, creating full-audio segment")
            wavSpeechSegments.offer(
                SpeechSegment(
                    startIndex = 0,
                    endIndex = wavWriteIndex,
                    startUs = 0L,
                    endUs = totalDurationUs
                )
            )
        }
        
        if (oggSegmentCount == 0 && oggWriteIndex > SAMPLE_RATE) {
            Log.d(TAG, "OGG: No segments detected, creating full-audio segment")
            oggSpeechSegments.offer(
                SpeechSegment(
                    startIndex = 0,
                    endIndex = oggWriteIndex,
                    startUs = 0L,
                    endUs = totalDurationUs
                )
            )
        }
        
        Log.d(TAG, "After finalization: WAV=${wavSpeechSegments.size} segments, OGG=${oggSpeechSegments.size} segments")
    }

    private fun trimAudioBySpeech(
        buffer: ShortArray,
        writeIndex: Int,
        segments: List<SpeechSegment>
    ): ShortArray {
        if (segments.isEmpty()) {
            // Return ALL recorded audio when no segments detected (matches OGG behavior)
            Log.w(TAG, "No speech segments, returning all $writeIndex samples")
            val result = ShortArray(writeIndex)
            for (i in 0 until writeIndex) {
                result[i] = buffer[i % BUFFER_SIZE_SAMPLES]
            }
            return result
        }

        // Merge overlapping segments
        val merged = mutableListOf<SpeechSegment>()
        var current = segments.first()

        for (segment in segments.drop(1)) {
            if (segment.startIndex <= current.endIndex) {
                current = SpeechSegment(
                    current.startIndex,
                    maxOf(current.endIndex, segment.endIndex),
                    current.startUs,
                    maxOf(current.endUs, segment.endUs)
                )
            } else {
                merged.add(current)
                current = segment
            }
        }
        merged.add(current)

        // Extract samples from merged segments
        val result = mutableListOf<Short>()
        for (segment in merged) {
            val bufferBefore = ((BUFFER_BEFORE_MS * SAMPLE_RATE) / 1000).toInt()
            val bufferAfter = ((BUFFER_AFTER_MS * SAMPLE_RATE) / 1000).toInt()

            val start = (segment.startIndex - bufferBefore + BUFFER_SIZE_SAMPLES) % BUFFER_SIZE_SAMPLES
            val end = (segment.endIndex + bufferAfter) % BUFFER_SIZE_SAMPLES
            val length = if (end >= start) end - start else (BUFFER_SIZE_SAMPLES - start) + end

            for (i in 0 until length) {
                result.add(buffer[(start + i) % BUFFER_SIZE_SAMPLES])
            }
        }

        return result.toShortArray()
    }

    private fun calculateRmsDb(samples: ShortArray): Float {
        if (samples.isEmpty()) return -100f

        var sum = 0.0
        for (sample in samples) {
            val sampleDouble = sample.toDouble()
            sum += sampleDouble * sampleDouble
        }

        val rms = sqrt(sum / samples.size)

        return if (rms > 0) {
            (20 * kotlin.math.log10(rms / Short.MAX_VALUE)).toFloat()
        } else {
            -100f
        }
    }

    private fun samplesToWav(samples: ShortArray): ByteArray {
        val output = ByteArrayOutputStream()

        // WAV header
        val dataSize = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataSize)

        output.write(header.array())

        // Audio data
        val audioData = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            audioData.putShort(sample)
        }
        output.write(audioData.array())

        return output.toByteArray()
    }

    private fun cleanup() {
        try {
            audioRecord?.release()
            encoder?.stop()
            encoder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }

        audioRecord = null
        encoder = null
        recordingThread = null
        wavRmsThread = null
        oggRmsThread = null
        oggEncoderThread = null
        currentCallback = null
    }
}
