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
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.max

/**
 * DualOggRecorder - Records audio once and produces two OGG outputs for comparison
 *
 * This enables accurate comparison between:
 * - Sequential OGG: PCM captured during recording, encoded to OGG after recording stops (simulates MediaRecorder)
 * - Parallel OGG: PCM encoded to OGG in parallel during recording (new approach)
 *
 * Both outputs use the same audio input and produce OGG files with NO silence trimming.
 * This allows fair comparison of the encoding approaches with the same Groq API.
 */
class DualOggRecorder(private val context: Context) {

    companion object {
        private const val TAG = "DualOggRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SAMPLES = 320 // 20ms at 16kHz (Opus frame size)
        private const val BUFFER_SIZE_SAMPLES = 960_000 // 60 seconds max
        private const val OPUS_BIT_RATE = 24000

        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var recordingThread: Thread? = null
    private var parallelEncoderThread: Thread? = null

    private var amplitudeCallback: AudioRecorder.AmplitudeCallback? = null
    private var currentCallback: DualRecordingCallback? = null

    // PCM buffer for sequential encoding (stores all samples)
    private val pcmBuffer = ShortArray(BUFFER_SIZE_SAMPLES)
    private var pcmWriteIndex = 0
    private val totalSamplesRecorded = AtomicLong(0)

    // Parallel encoding pipeline
    private val parallelEncoderQueue = ConcurrentLinkedQueue<ChunkData>()
    private val parallelEncodedFrames = ConcurrentLinkedQueue<EncodedFrame>()
    private var parallelEncoder: MediaCodec? = null
    @Volatile
    private var parallelEncoderOutputFormat: MediaFormat? = null

    // Recording timing
    private var recordingStartTimeMs = 0L

    data class ChunkData(
        val samples: ShortArray,
        val timestampUs: Long
    )

    data class EncodedFrame(
        val data: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int,
        val size: Int
    )

    data class DualResult(
        val sequentialOggBytes: ByteArray,
        val parallelOggBytes: ByteArray,
        val sequentialMetadata: Metadata,
        val parallelMetadata: Metadata
    )

    data class Metadata(
        val durationMs: Long,
        val fileSizeBytes: Int,
        val encodingTimeMs: Long  // Time taken for encoding
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
        pcmWriteIndex = 0
        totalSamplesRecorded.set(0)
        parallelEncoderQueue.clear()
        parallelEncodedFrames.clear()
        parallelEncoderOutputFormat = null
        recordingStartTimeMs = System.currentTimeMillis()

        try {
            // Initialize AudioRecord
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = max(minBufferSize, CHUNK_SAMPLES * 4)

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

            // Initialize parallel encoder
            initializeParallelEncoder()

            isRecording.set(true)

            // Start threads
            startParallelEncoderThread()
            startRecordingThread()

            audioRecord?.startRecording()
            callback.onRecordingStarted()
            Log.d(TAG, "Dual OGG recording started")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            cleanup()
            callback.onRecordingError(e.message ?: "Unknown error")
        }
    }

    private fun initializeParallelEncoder() {
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

            parallelEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            parallelEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            parallelEncoder?.start()

            Log.d(TAG, "Parallel Opus encoder initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize parallel encoder", e)
            throw e
        }
    }

    private fun startRecordingThread() {
        recordingThread = thread(name = "DualOgg-Recording") {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

            val audioChunk = ShortArray(CHUNK_SAMPLES)

            while (isRecording.get()) {
                val samplesRead = audioRecord?.read(audioChunk, 0, CHUNK_SAMPLES) ?: 0

                if (samplesRead > 0) {
                    val currentTotal = totalSamplesRecorded.get()
                    val timestampUs = (currentTotal * 1_000_000L) / SAMPLE_RATE

                    // Store in PCM buffer for sequential encoding later
                    synchronized(pcmBuffer) {
                        for (i in 0 until samplesRead) {
                            if (pcmWriteIndex < BUFFER_SIZE_SAMPLES) {
                                pcmBuffer[pcmWriteIndex++] = audioChunk[i]
                            }
                        }
                    }

                    // Calculate amplitude for UI
                    var maxAmp = 0
                    for (i in 0 until samplesRead) {
                        val absSample = if (audioChunk[i] < 0) -audioChunk[i].toInt() else audioChunk[i].toInt()
                        if (absSample > maxAmp) maxAmp = absSample
                    }
                    amplitudeCallback?.onAmplitude(maxAmp)

                    // Feed to parallel encoder
                    val chunkCopy = audioChunk.copyOf(samplesRead)
                    parallelEncoderQueue.offer(ChunkData(chunkCopy, timestampUs))

                    totalSamplesRecorded.addAndGet(samplesRead.toLong())
                }
            }

            Log.d(TAG, "Recording thread finished, total samples: ${totalSamplesRecorded.get()}")
        }
    }

    private fun startParallelEncoderThread() {
        parallelEncoderThread = thread(name = "DualOgg-ParallelEncoder") {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)

            val bufferInfo = MediaCodec.BufferInfo()

            while (isRecording.get() || parallelEncoderQueue.isNotEmpty()) {
                val chunk = parallelEncoderQueue.poll()
                if (chunk == null) {
                    drainParallelEncoder(bufferInfo)
                    Thread.sleep(5)
                    continue
                }

                try {
                    // Feed input to encoder
                    val inputIndex = parallelEncoder?.dequeueInputBuffer(10_000) ?: -1
                    if (inputIndex >= 0) {
                        val inputBuffer = parallelEncoder?.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                            for (sample in chunk.samples) {
                                inputBuffer.putShort(sample)
                            }
                            parallelEncoder?.queueInputBuffer(
                                inputIndex,
                                0,
                                chunk.samples.size * 2,
                                chunk.timestampUs,
                                0
                            )
                        }
                    }

                    drainParallelEncoder(bufferInfo)

                } catch (e: Exception) {
                    Log.e(TAG, "Error in parallel encoder thread", e)
                }
            }

            // Signal EOS and drain
            signalParallelEncoderEOS(bufferInfo)

            Log.d(TAG, "Parallel encoder thread finished, encoded ${parallelEncodedFrames.size} frames")
        }
    }

    private fun drainParallelEncoder(bufferInfo: MediaCodec.BufferInfo) {
        while (true) {
            val outputIndex = parallelEncoder?.dequeueOutputBuffer(bufferInfo, 0) ?: -1

            when {
                outputIndex >= 0 -> {
                    val outputBuffer = parallelEncoder?.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val frameData = ByteArray(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.get(frameData, 0, bufferInfo.size)

                        parallelEncodedFrames.offer(EncodedFrame(
                            frameData,
                            bufferInfo.presentationTimeUs,
                            bufferInfo.flags,
                            bufferInfo.size
                        ))
                    }
                    parallelEncoder?.releaseOutputBuffer(outputIndex, false)
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    parallelEncoderOutputFormat = parallelEncoder?.outputFormat
                    Log.d(TAG, "Parallel encoder output format: $parallelEncoderOutputFormat")
                }
                else -> break
            }
        }
    }

    private fun signalParallelEncoderEOS(bufferInfo: MediaCodec.BufferInfo) {
        try {
            val eosIndex = parallelEncoder?.dequeueInputBuffer(10_000) ?: -1
            if (eosIndex >= 0) {
                parallelEncoder?.queueInputBuffer(
                    eosIndex, 0, 0, 0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }

            var sawEos = false
            while (!sawEos) {
                val outputIndex = parallelEncoder?.dequeueOutputBuffer(bufferInfo, 10_000) ?: -1
                if (outputIndex >= 0) {
                    val outputBuffer = parallelEncoder?.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val frameData = ByteArray(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.get(frameData, 0, bufferInfo.size)

                        parallelEncodedFrames.offer(EncodedFrame(
                            frameData,
                            bufferInfo.presentationTimeUs,
                            bufferInfo.flags,
                            bufferInfo.size
                        ))
                    }
                    parallelEncoder?.releaseOutputBuffer(outputIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawEos = true
                    }
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error signaling parallel encoder EOS", e)
        }
    }

    fun stopRecording() {
        if (!isRecording.get()) {
            Log.w(TAG, "Not recording")
            return
        }

        Log.d(TAG, "Stopping dual OGG recording...")
        val stopTime = System.currentTimeMillis()
        isRecording.set(false)

        try {
            // Stop audio recording
            audioRecord?.stop()

            // Wait for threads to finish
            recordingThread?.join(3000)
            parallelEncoderThread?.join(5000)

            val recordingDurationMs = stopTime - recordingStartTimeMs
            val totalSamples = totalSamplesRecorded.get()
            val durationMs = (totalSamples * 1000) / SAMPLE_RATE

            Log.d(TAG, "Recording stopped. Duration: ${durationMs}ms, Samples: $totalSamples")

            // Create parallel OGG output (already encoded)
            val parallelStartTime = System.currentTimeMillis()
            val parallelOggBytes = muxParallelFramesToOgg()
            val parallelEncodingTime = System.currentTimeMillis() - parallelStartTime

            // Create sequential OGG output (encode now from PCM buffer)
            val sequentialStartTime = System.currentTimeMillis()
            val sequentialOggBytes = encodeSequentialOgg()
            val sequentialEncodingTime = System.currentTimeMillis() - sequentialStartTime

            val result = DualResult(
                sequentialOggBytes = sequentialOggBytes,
                parallelOggBytes = parallelOggBytes,
                sequentialMetadata = Metadata(
                    durationMs = durationMs,
                    fileSizeBytes = sequentialOggBytes.size,
                    encodingTimeMs = sequentialEncodingTime
                ),
                parallelMetadata = Metadata(
                    durationMs = durationMs,
                    fileSizeBytes = parallelOggBytes.size,
                    encodingTimeMs = parallelEncodingTime  // This is just muxing time, encoding was during recording
                )
            )

            Log.d(TAG, "Dual OGG outputs ready:")
            Log.d(TAG, "  Sequential: ${sequentialOggBytes.size} bytes, encoding took ${sequentialEncodingTime}ms")
            Log.d(TAG, "  Parallel: ${parallelOggBytes.size} bytes, muxing took ${parallelEncodingTime}ms")

            currentCallback?.onRecordingStopped(result)

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            currentCallback?.onRecordingError(e.message ?: "Unknown error")
        } finally {
            cleanup()
        }
    }

    /**
     * Encode PCM buffer to OGG sequentially (after recording stops)
     * This simulates MediaRecorder behavior
     */
    private fun encodeSequentialOgg(): ByteArray {
        if (pcmWriteIndex == 0) {
            Log.w(TAG, "No PCM data to encode")
            return ByteArray(0)
        }

        var encoder: MediaCodec? = null
        val encodedFrames = mutableListOf<EncodedFrame>()
        var outputFormat: MediaFormat? = null

        try {
            // Create new encoder for sequential encoding
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                SAMPLE_RATE,
                1
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, OPUS_BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK_SAMPLES * 2)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setInteger(MediaFormat.KEY_COMPLEXITY, 5)
                }
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputOffset = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                // Feed input
                if (!inputDone) {
                    val inputIndex = encoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                            val samplesToWrite = minOf(CHUNK_SAMPLES, pcmWriteIndex - inputOffset)
                            if (samplesToWrite > 0) {
                                for (i in 0 until samplesToWrite) {
                                    inputBuffer.putShort(pcmBuffer[inputOffset + i])
                                }
                                val timestampUs = (inputOffset.toLong() * 1_000_000L) / SAMPLE_RATE
                                encoder.queueInputBuffer(inputIndex, 0, samplesToWrite * 2, timestampUs, 0)
                                inputOffset += samplesToWrite
                            } else {
                                encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            }
                        }
                    }
                }

                // Drain output
                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outputIndex >= 0 -> {
                        val outputBuffer = encoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val frameData = ByteArray(bufferInfo.size)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.get(frameData, 0, bufferInfo.size)
                            encodedFrames.add(EncodedFrame(
                                frameData,
                                bufferInfo.presentationTimeUs,
                                bufferInfo.flags,
                                bufferInfo.size
                            ))
                        }
                        encoder.releaseOutputBuffer(outputIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = encoder.outputFormat
                    }
                }
            }

            // Mux to OGG
            return muxFramesToOgg(encodedFrames, outputFormat, "sequential_ogg.ogg")

        } catch (e: Exception) {
            Log.e(TAG, "Error encoding sequential OGG", e)
            return ByteArray(0)
        } finally {
            try {
                encoder?.stop()
                encoder?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing sequential encoder", e)
            }
        }
    }

    private fun muxParallelFramesToOgg(): ByteArray {
        val frames = parallelEncodedFrames.toList()
        if (frames.isEmpty() || parallelEncoderOutputFormat == null) {
            Log.w(TAG, "No parallel frames or format to mux")
            return ByteArray(0)
        }
        return muxFramesToOgg(frames, parallelEncoderOutputFormat, "parallel_ogg.ogg")
    }

    private fun muxFramesToOgg(frames: List<EncodedFrame>, outputFormat: MediaFormat?, fileName: String): ByteArray {
        if (frames.isEmpty() || outputFormat == null) {
            return ByteArray(0)
        }

        val outputFile = File(context.cacheDir, fileName)
        var muxer: MediaMuxer? = null

        try {
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

            val bytes = outputFile.readBytes()
            outputFile.delete()

            Log.d(TAG, "Muxed ${frames.size} frames to $fileName: ${bytes.size} bytes")
            return bytes

        } catch (e: Exception) {
            Log.e(TAG, "Error muxing to OGG", e)
            return ByteArray(0)
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            outputFile.delete()
        }
    }

    private fun cleanup() {
        try {
            audioRecord?.release()
            parallelEncoder?.stop()
            parallelEncoder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }

        audioRecord = null
        parallelEncoder = null
        recordingThread = null
        parallelEncoderThread = null
        currentCallback = null
    }
}
