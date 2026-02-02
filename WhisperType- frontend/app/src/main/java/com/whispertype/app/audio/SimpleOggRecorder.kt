package com.whispertype.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.util.Log
import com.whispertype.app.Constants
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * SimpleOggRecorder - Records audio using AudioRecord with parallel Opus encoding AND RMS-based silence trimming
 *
 * This implements a three-thread architecture (same as ParallelOpusRecorder):
 * 1. Recording Thread: Captures raw PCM audio at 16kHz using AudioRecord
 * 2. RMS Analysis Thread: Analyzes each chunk in real-time to detect speech/silence
 * 3. Encoder Thread: Encodes PCM chunks to Opus format in real-time
 *
 * When recording stops:
 * - Speech segments are already identified (from RMS thread)
 * - Encoded frames are already available (from Encoder thread)
 * - Filter encoded frames by speech segments and mux to OGG
 *
 * Output: Compressed OGG file with silence trimmed
 *
 * Requirements: Android 10+ (API 29+) for MediaCodec Opus encoding
 */
class SimpleOggRecorder(private val context: Context) {

    companion object {
        private const val TAG = "SimpleOggRecorder"

        // Audio configuration
        private const val SAMPLE_RATE = 16000 // 16kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Chunk size: 20ms window (Opus frame size)
        // Opus works best with 20ms frames at 16kHz = 320 samples
        private const val CHUNK_SAMPLES = 320 // 20ms at 16kHz

        // RMS threshold for speech detection
        private const val SPEECH_THRESHOLD_DB = Constants.SILENCE_THRESHOLD_DB // -40dB

        // Segment merging: merge if silence gap < 100ms
        private const val MIN_SILENCE_DURATION_MS = Constants.MIN_SILENCE_DURATION_MS // 100ms

        // Padding around speech segments
        private const val BUFFER_BEFORE_MS = Constants.AUDIO_BUFFER_BEFORE_MS // 150ms
        private const val BUFFER_AFTER_MS = Constants.AUDIO_BUFFER_AFTER_MS // 200ms

        // Opus encoder settings
        private const val OPUS_BIT_RATE = Constants.AUDIO_BIT_RATE_OPUS // 24000 bps

        /**
         * Check if device supports Opus encoding via MediaCodec (Android 10+)
         */
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    /**
     * Callback interface for recording events
     */
    interface RecordingCallback {
        fun onRecordingStarted()
        fun onRecordingStopped(trimmedOggBytes: ByteArray, rawOggBytes: ByteArray, metadata: Metadata)
        fun onRecordingError(error: String)
    }

    /**
     * Metadata about the recorded audio
     */
    data class Metadata(
        val durationMs: Long,
        val speechDurationMs: Long,
        val fileSizeBytes: Int,
        val rawFileSizeBytes: Int,
        val frameCount: Int,
        val speechSegmentCount: Int,
        val silenceTrimmingApplied: Boolean
    )

    /**
     * Data passed from recording thread to processing threads
     */
    private data class ChunkData(
        val samples: ShortArray,
        val timeUs: Long,  // Timestamp in microseconds
        val globalIndex: Long  // Starting sample index
    )

    /**
     * Speech segment detected by RMS analysis
     */
    private data class SpeechSegment(
        val startUs: Long,
        val endUs: Long,
        val startIndex: Long,
        val endIndex: Long
    )

    /**
     * Encoded Opus frame with timestamp
     */
    private data class EncodedFrame(
        val data: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int,
        val size: Int
    )

    // Lock-free queue for passing chunks to RMS thread
    private val rmsQueue = ConcurrentLinkedQueue<ChunkData>()

    // Lock-free queue for passing chunks to Encoder thread
    private val encoderQueue = ConcurrentLinkedQueue<ChunkData>()

    // Thread-safe speech segments (computed in real-time)
    private val speechSegments = ConcurrentLinkedQueue<SpeechSegment>()

    // Thread-safe encoded frames (computed in real-time)
    private val encodedFrames = ConcurrentLinkedQueue<EncodedFrame>()

    // RMS analysis state
    @Volatile
    private var currentSpeechStart: Long? = null
    @Volatile
    private var currentSpeechStartIndex: Long = 0
    @Volatile
    private var lastSpeechEnd: Long = 0L

    // Thread control
    private val isRecording = AtomicBoolean(false)
    private val isRmsProcessing = AtomicBoolean(false)
    private val isEncoderProcessing = AtomicBoolean(false)
    private var recordingThread: Thread? = null
    private var rmsThread: Thread? = null
    private var encoderThread: Thread? = null

    // Sample counter for duration calculation
    private val totalSamplesRecorded = AtomicLong(0)

    // AudioRecord instance
    private var audioRecord: AudioRecord? = null

    // MediaCodec encoder
    private var encoder: MediaCodec? = null
    @Volatile
    private var encoderOutputFormat: MediaFormat? = null

    // Callback
    private var callback: RecordingCallback? = null

    // Amplitude callback for waveform visualization
    @Volatile
    private var amplitudeCallback: AudioRecorder.AmplitudeCallback? = null

    // Timing
    private var recordingStartTimeUs = 0L

    /**
     * Set the amplitude callback for voice activity visualization
     */
    fun setAmplitudeCallback(callback: AudioRecorder.AmplitudeCallback?) {
        amplitudeCallback = callback
    }

    /**
     * Start recording with parallel RMS analysis and Opus encoding
     */
    fun startRecording(callback: RecordingCallback) {
        if (!isSupported()) {
            callback.onRecordingError("Opus encoding requires Android 10+")
            return
        }

        if (isRecording.get()) {
            Log.w(TAG, "Already recording")
            return
        }

        this.callback = callback

        // Reset state
        speechSegments.clear()
        encodedFrames.clear()
        rmsQueue.clear()
        encoderQueue.clear()
        totalSamplesRecorded.set(0)
        currentSpeechStart = null
        currentSpeechStartIndex = 0
        lastSpeechEnd = 0L
        encoderOutputFormat = null
        recordingStartTimeUs = System.nanoTime() / 1000

        // Initialize AudioRecord
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = max(minBufferSize, CHUNK_SAMPLES * 4) // Ensure adequate buffer

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                callback.onRecordingError("Failed to initialize AudioRecord")
                audioRecord?.release()
                audioRecord = null
                return
            }

            // Initialize MediaCodec encoder
            if (!initializeEncoder()) {
                callback.onRecordingError("Failed to initialize Opus encoder")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            isRmsProcessing.set(true)
            isEncoderProcessing.set(true)

            // Start recording thread
            recordingThread = Thread({ recordingLoop() }, "SimpleOgg-Recording").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }

            // Start RMS analysis thread
            rmsThread = Thread({ rmsProcessingLoop() }, "SimpleOgg-RMS").apply {
                priority = Thread.NORM_PRIORITY - 1
                start()
            }

            // Start encoder thread
            encoderThread = Thread({ encoderProcessingLoop() }, "SimpleOgg-Encoder").apply {
                priority = Thread.NORM_PRIORITY
                start()
            }

            callback.onRecordingStarted()
            Log.d(TAG, "Recording started with parallel RMS + Opus encoding")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            cleanup()
            callback.onRecordingError("Failed to start recording: ${e.message}")
        }
    }

    /**
     * Initialize MediaCodec Opus encoder
     */
    private fun initializeEncoder(): Boolean {
        return try {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                SAMPLE_RATE,
                1 // Mono
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, OPUS_BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK_SAMPLES * 2) // bytes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setInteger(MediaFormat.KEY_COMPLEXITY, 5) // Balance quality/speed
                }
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            Log.d(TAG, "Opus encoder initialized: $SAMPLE_RATE Hz, $OPUS_BIT_RATE bps")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Opus encoder", e)
            false
        }
    }

    /**
     * Stop recording and return trimmed OGG audio
     */
    fun stopRecording() {
        if (!isRecording.get()) {
            Log.w(TAG, "Not recording")
            return
        }

        Log.d(TAG, "Stopping recording...")

        // Signal threads to stop
        isRecording.set(false)

        // Wait for recording thread to finish
        recordingThread?.join(1000)

        // Signal processing threads to stop and wait
        isRmsProcessing.set(false)
        isEncoderProcessing.set(false)
        rmsThread?.join(1000)
        encoderThread?.join(2000) // Give encoder more time to drain

        // Stop AudioRecord
        audioRecord?.stop()

        // Calculate timing
        val recordingEndTimeUs = System.nanoTime() / 1000
        val totalDurationUs = recordingEndTimeUs - recordingStartTimeUs
        val durationMs = (totalSamplesRecorded.get() * 1000) / SAMPLE_RATE

        Log.d(TAG, "Recording stopped. Duration: ${durationMs}ms, " +
                "Segments: ${speechSegments.size}, EncodedFrames: ${encodedFrames.size}")

        // Process any remaining segments (close open speech segment)
        finalizeSegments(totalDurationUs)

        // Filter encoded frames and mux to OGG
        try {
            val result = filterAndMuxToOgg(totalDurationUs)
            callback?.onRecordingStopped(result.trimmedBytes, result.rawBytes, result.metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio", e)
            callback?.onRecordingError("Failed to process audio: ${e.message}")
        } finally {
            cleanup()
        }
    }

    /**
     * Recording thread loop
     * Captures audio from AudioRecord and distributes to processing threads
     */
    private fun recordingLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val chunkBuffer = ShortArray(CHUNK_SAMPLES)

        Log.d(TAG, "Recording thread started")

        while (isRecording.get()) {
            val audioRecord = this.audioRecord ?: break

            // Read from AudioRecord
            val samplesRead = audioRecord.read(chunkBuffer, 0, CHUNK_SAMPLES)

            if (samplesRead <= 0) {
                if (samplesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "AudioRecord not properly initialized")
                    break
                }
                continue
            }

            // Track total samples for duration calculation
            val currentTotal = totalSamplesRecorded.get()
            totalSamplesRecorded.addAndGet(samplesRead.toLong())

            // Calculate timestamp
            val timeUs = (currentTotal * 1_000_000L) / SAMPLE_RATE

            // Create chunk data (copy to avoid race conditions)
            val chunkCopy = ShortArray(samplesRead)
            System.arraycopy(chunkBuffer, 0, chunkCopy, 0, samplesRead)

            val chunkData = ChunkData(
                samples = chunkCopy,
                timeUs = timeUs,
                globalIndex = currentTotal
            )

            // Queue to both processing threads
            rmsQueue.offer(chunkData)
            encoderQueue.offer(chunkData)
        }

        Log.d(TAG, "Recording thread stopped")
    }

    /**
     * RMS analysis thread loop
     * Processes chunks from queue and detects speech/silence
     */
    private fun rmsProcessingLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)

        Log.d(TAG, "RMS analysis thread started")

        while (isRmsProcessing.get() || rmsQueue.isNotEmpty()) {
            val chunk = rmsQueue.poll()

            if (chunk == null) {
                Thread.sleep(5)
                continue
            }

            // Calculate RMS amplitude in dB
            val rmsDb = calculateRmsDb(chunk.samples)
            val isSpeech = rmsDb > SPEECH_THRESHOLD_DB

            // Calculate peak amplitude for visualization
            if (amplitudeCallback != null) {
                var maxAmp = 0
                for (sample in chunk.samples) {
                    val absSample = if (sample < 0) -sample.toInt() else sample.toInt()
                    if (absSample > maxAmp) maxAmp = absSample
                }
                amplitudeCallback?.onAmplitude(maxAmp)
            }

            // Update speech segments
            if (isSpeech) {
                if (currentSpeechStart == null) {
                    val segmentsList = speechSegments.toList()
                    val lastSegment = segmentsList.lastOrNull()

                    // Merge with previous segment if gap is small
                    if (lastSegment != null &&
                        chunk.timeUs - lastSpeechEnd < MIN_SILENCE_DURATION_MS * 1000) {
                        speechSegments.remove(lastSegment)
                        currentSpeechStart = lastSegment.startUs
                        currentSpeechStartIndex = lastSegment.startIndex
                    } else {
                        currentSpeechStart = chunk.timeUs
                        currentSpeechStartIndex = chunk.globalIndex
                    }
                }
            } else {
                if (currentSpeechStart != null) {
                    val endTimeUs = chunk.timeUs
                    val endIndex = chunk.globalIndex

                    speechSegments.offer(SpeechSegment(
                        startUs = currentSpeechStart!!,
                        endUs = endTimeUs,
                        startIndex = currentSpeechStartIndex,
                        endIndex = endIndex
                    ))

                    lastSpeechEnd = endTimeUs
                    currentSpeechStart = null
                }
            }
        }

        Log.d(TAG, "RMS analysis thread stopped, detected ${speechSegments.size} segments")
    }

    /**
     * Encoder thread loop
     * Encodes PCM chunks to Opus format
     */
    private fun encoderProcessingLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)

        Log.d(TAG, "Encoder thread started")

        encoder?.start()
        val bufferInfo = MediaCodec.BufferInfo()

        while (isEncoderProcessing.get() || encoderQueue.isNotEmpty()) {
            val chunk = encoderQueue.poll()

            if (chunk == null) {
                // Still drain output even when queue is empty
                drainEncoder(bufferInfo)
                Thread.sleep(5)
                continue
            }

            // Feed input to encoder
            val inputIndex = encoder?.dequeueInputBuffer(10_000) ?: -1
            if (inputIndex >= 0) {
                val inputBuffer = encoder?.getInputBuffer(inputIndex)
                inputBuffer?.apply {
                    clear()
                    order(ByteOrder.LITTLE_ENDIAN)
                    // Convert ShortArray to bytes
                    for (sample in chunk.samples) {
                        putShort(sample)
                    }
                }
                encoder?.queueInputBuffer(
                    inputIndex,
                    0,
                    chunk.samples.size * 2, // bytes
                    chunk.timeUs,
                    0
                )
            }

            // Drain output
            drainEncoder(bufferInfo)
        }

        // Signal end of stream and drain remaining
        signalEncoderEOS(bufferInfo)

        Log.d(TAG, "Encoder thread stopped, total frames: ${encodedFrames.size}")
    }

    /**
     * Drain encoder output buffers
     */
    private fun drainEncoder(bufferInfo: MediaCodec.BufferInfo) {
        while (true) {
            val outputIndex = encoder?.dequeueOutputBuffer(bufferInfo, 0) ?: -1

            when {
                outputIndex >= 0 -> {
                    val outputBuffer = encoder?.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val frameData = ByteArray(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.get(frameData, 0, bufferInfo.size)

                        encodedFrames.offer(EncodedFrame(
                            data = frameData,
                            presentationTimeUs = bufferInfo.presentationTimeUs,
                            flags = bufferInfo.flags,
                            size = bufferInfo.size
                        ))
                    }
                    encoder?.releaseOutputBuffer(outputIndex, false)
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    encoderOutputFormat = encoder?.outputFormat
                    Log.d(TAG, "Encoder output format changed: $encoderOutputFormat")
                }
                else -> break
            }
        }
    }

    /**
     * Signal end of stream to encoder and drain remaining output
     */
    private fun signalEncoderEOS(bufferInfo: MediaCodec.BufferInfo) {
        try {
            val inputIndex = encoder?.dequeueInputBuffer(10_000) ?: -1
            if (inputIndex >= 0) {
                encoder?.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }

            // Drain remaining output
            var eos = false
            while (!eos) {
                val outputIndex = encoder?.dequeueOutputBuffer(bufferInfo, 10_000) ?: -1
                if (outputIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        eos = true
                    }

                    val outputBuffer = encoder?.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val frameData = ByteArray(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.get(frameData, 0, bufferInfo.size)

                        encodedFrames.offer(EncodedFrame(
                            data = frameData,
                            presentationTimeUs = bufferInfo.presentationTimeUs,
                            flags = bufferInfo.flags,
                            size = bufferInfo.size
                        ))
                    }
                    encoder?.releaseOutputBuffer(outputIndex, false)
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error signaling EOS", e)
        }
    }

    /**
     * Finalize segments after recording stops
     */
    private fun finalizeSegments(totalDurationUs: Long) {
        // Close open speech segment
        if (currentSpeechStart != null) {
            val totalSamples = totalSamplesRecorded.get()

            speechSegments.offer(SpeechSegment(
                startUs = currentSpeechStart!!,
                endUs = totalDurationUs,
                startIndex = currentSpeechStartIndex,
                endIndex = totalSamples
            ))

            currentSpeechStart = null
        }

        // Add padding to all segments and merge overlapping
        if (speechSegments.isNotEmpty()) {
            val paddedSegments = mutableListOf<SpeechSegment>()

            for (segment in speechSegments) {
                val paddedStartUs = max(0, segment.startUs - BUFFER_BEFORE_MS * 1000)
                val paddedEndUs = min(totalDurationUs, segment.endUs + BUFFER_AFTER_MS * 1000)
                val startIndex = (paddedStartUs * SAMPLE_RATE) / 1_000_000
                val endIndex = (paddedEndUs * SAMPLE_RATE) / 1_000_000

                paddedSegments.add(SpeechSegment(
                    startUs = paddedStartUs,
                    endUs = paddedEndUs,
                    startIndex = startIndex,
                    endIndex = endIndex
                ))
            }

            // Merge overlapping segments
            val merged = mergeOverlappingSegments(paddedSegments)

            speechSegments.clear()
            speechSegments.addAll(merged)

            Log.d(TAG, "Finalized ${merged.size} speech segments")
        }
    }

    /**
     * Merge overlapping or adjacent segments
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
     * Filter encoded frames by speech segments and mux to OGG
     */
    private fun filterAndMuxToOgg(totalDurationUs: Long): MuxResult {
        val segmentsList = speechSegments.toList()
        val framesList = encodedFrames.toList()
        val durationMs = (totalSamplesRecorded.get() * 1000) / SAMPLE_RATE

        Log.d(TAG, "Filtering ${framesList.size} frames by ${segmentsList.size} segments")

        // Mux all frames to OGG for raw (untrimmed) output
        val rawBytes = muxFramesToOgg(framesList, "raw_simple_ogg.ogg")

        // If no speech detected, return all frames as fallback
        if (segmentsList.isEmpty()) {
            Log.w(TAG, "No speech detected, using all audio as fallback")
            return MuxResult(
                trimmedBytes = rawBytes,
                rawBytes = rawBytes,
                metadata = Metadata(
                    durationMs = durationMs,
                    speechDurationMs = durationMs,
                    fileSizeBytes = rawBytes.size,
                    rawFileSizeBytes = rawBytes.size,
                    frameCount = framesList.size,
                    speechSegmentCount = 0,
                    silenceTrimmingApplied = false
                )
            )
        }

        // Filter frames that fall within speech segments
        val filteredFrames = framesList.filter { frame ->
            segmentsList.any { segment ->
                // Frame overlaps with segment (include frames at boundaries)
                frame.presentationTimeUs >= segment.startUs &&
                frame.presentationTimeUs <= segment.endUs
            }
        }

        Log.d(TAG, "Filtered to ${filteredFrames.size} frames (from ${framesList.size})")

        // If filtering removed all frames, use all frames as fallback
        if (filteredFrames.isEmpty()) {
            Log.w(TAG, "Filtering removed all frames, using all frames as fallback")
            return MuxResult(
                trimmedBytes = rawBytes,
                rawBytes = rawBytes,
                metadata = Metadata(
                    durationMs = durationMs,
                    speechDurationMs = durationMs,
                    fileSizeBytes = rawBytes.size,
                    rawFileSizeBytes = rawBytes.size,
                    frameCount = framesList.size,
                    speechSegmentCount = segmentsList.size,
                    silenceTrimmingApplied = false
                )
            )
        }

        // Calculate speech duration
        val speechDurationUs = segmentsList.sumOf { it.endUs - it.startUs }

        // Mux trimmed frames to OGG
        val trimmedBytes = muxFramesToOgg(filteredFrames, "trimmed_simple_ogg.ogg")

        // Trimming applied if we filtered out any frames
        val trimmingApplied = filteredFrames.size < framesList.size

        return MuxResult(
            trimmedBytes = trimmedBytes,
            rawBytes = rawBytes,
            metadata = Metadata(
                durationMs = durationMs,
                speechDurationMs = speechDurationUs / 1000,
                fileSizeBytes = trimmedBytes.size,
                rawFileSizeBytes = rawBytes.size,
                frameCount = filteredFrames.size,
                speechSegmentCount = segmentsList.size,
                silenceTrimmingApplied = trimmingApplied
            )
        )
    }

    /**
     * Mux encoded frames to OGG container
     */
    private fun muxFramesToOgg(frames: List<EncodedFrame>, fileName: String): ByteArray {
        if (frames.isEmpty() || encoderOutputFormat == null) {
            Log.w(TAG, "No frames to mux or no output format")
            return ByteArray(0)
        }

        val outputFile = File(context.cacheDir, fileName)
        outputFile.delete()

        var muxer: MediaMuxer? = null
        try {
            muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
            )

            val trackIndex = muxer.addTrack(encoderOutputFormat!!)
            muxer.start()

            // Adjust timestamps to start from 0 for filtered frames
            val baseTimeUs = frames.firstOrNull()?.presentationTimeUs ?: 0L

            for (frame in frames) {
                val bufferInfo = MediaCodec.BufferInfo().apply {
                    set(0, frame.size, frame.presentationTimeUs - baseTimeUs, frame.flags)
                }
                val buffer = ByteBuffer.wrap(frame.data)
                muxer.writeSampleData(trackIndex, buffer, bufferInfo)
            }

            muxer.stop()
            muxer.release()
            muxer = null

            val bytes = outputFile.readBytes()
            outputFile.delete()

            Log.d(TAG, "Muxed ${frames.size} frames to OGG: ${bytes.size} bytes")
            return bytes

        } catch (e: Exception) {
            Log.e(TAG, "Error muxing to OGG", e)
            muxer?.release()
            return ByteArray(0)
        }
    }

    /**
     * Calculate RMS amplitude in decibels
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
     * Clean up resources
     */
    private fun cleanup() {
        try {
            encoder?.stop()
            encoder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing encoder", e)
        }
        encoder = null

        audioRecord?.release()
        audioRecord = null
        recordingThread = null
        rmsThread = null
        encoderThread = null
        callback = null
    }

    /**
     * Result of muxing operation
     */
    private data class MuxResult(
        val trimmedBytes: ByteArray,
        val rawBytes: ByteArray,
        val metadata: Metadata
    )
}
