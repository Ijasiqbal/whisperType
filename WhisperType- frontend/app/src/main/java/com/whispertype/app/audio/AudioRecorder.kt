package com.whispertype.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream

/**
 * AudioRecorder - Records audio to a file for transcription
 * 
 * This class:
 * 1. Records audio using MediaRecorder
 * 2. Saves to a temporary file
 * 3. Returns audio bytes when recording stops
 * 
 * OUTPUT FORMAT:
 * - Uses MPEG_4 container with AAC audio encoding
 * - This creates an M4A file which is supported by the WhisperType API
 * - Good compression while maintaining audio quality
 * 
 * PERMISSIONS:
 * - Requires RECORD_AUDIO permission
 */
class AudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecorder"
        private const val AUDIO_FILE_NAME = "whisper_recording.m4a"
        
        // Audio quality settings
        private const val AUDIO_SAMPLE_RATE = 16000  // 16kHz is optimal for speech
        private const val AUDIO_BIT_RATE = 64000     // 64kbps for good quality
        private const val AUDIO_CHANNELS = 1         // Mono
        
        // Amplitude monitoring
        private const val AMPLITUDE_CHECK_INTERVAL = 100L  // Check amplitude every 100ms
    }

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    
    // Amplitude monitoring
    private var amplitudeHandler: Handler? = null
    private var amplitudeCallback: AmplitudeCallback? = null
    private val amplitudeRunnable = object : Runnable {
        override fun run() {
            if (isRecording && mediaRecorder != null) {
                try {
                    val amplitude = mediaRecorder?.maxAmplitude ?: 0
                    amplitudeCallback?.onAmplitude(amplitude)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting amplitude", e)
                }
                amplitudeHandler?.postDelayed(this, AMPLITUDE_CHECK_INTERVAL)
            }
        }
    }

    /**
     * Callback interface for recording events
     */
    interface RecordingCallback {
        fun onRecordingStarted()
        fun onRecordingStopped(audioBytes: ByteArray)
        fun onRecordingError(error: String)
    }
    
    /**
     * Callback interface for amplitude updates (for voice activity visualization)
     */
    interface AmplitudeCallback {
        /**
         * Called periodically with the current amplitude value
         * @param amplitude Value from 0 (silence) to ~32767 (max volume)
         */
        fun onAmplitude(amplitude: Int)
    }

    /**
     * Check if microphone permission is granted
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start recording audio
     * 
     * @param callback Callback for recording events
     * @return true if recording started successfully
     */
    fun startRecording(callback: RecordingCallback): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }

        if (!hasPermission()) {
            callback.onRecordingError("Microphone permission not granted")
            return false
        }

        try {
            // Create output file in cache directory
            outputFile = File(context.cacheDir, AUDIO_FILE_NAME)
            
            // Delete existing file if any
            if (outputFile?.exists() == true) {
                outputFile?.delete()
            }

            // Create and configure MediaRecorder
            mediaRecorder = createMediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(AUDIO_SAMPLE_RATE)
                setAudioEncodingBitRate(AUDIO_BIT_RATE)
                setAudioChannels(AUDIO_CHANNELS)
                setOutputFile(outputFile?.absolutePath)
                
                prepare()
                start()
            }

            isRecording = true
            Log.d(TAG, "Recording started, output: ${outputFile?.absolutePath}")
            callback.onRecordingStarted()
            
            // Start amplitude monitoring
            startAmplitudeMonitoring()
            
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            callback.onRecordingError("Failed to start recording: ${e.message}")
            return false
        }
    }

    /**
     * Stop recording and return the audio bytes
     * 
     * @param callback Callback to receive the audio bytes
     */
    fun stopRecording(callback: RecordingCallback) {
        if (!isRecording || mediaRecorder == null) {
            Log.w(TAG, "Not recording")
            callback.onRecordingError("Not recording")
            return
        }

        try {
            // Stop amplitude monitoring
            stopAmplitudeMonitoring()
            
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            // Read audio file to bytes
            val audioBytes = readAudioFile()
            
            if (audioBytes != null && audioBytes.isNotEmpty()) {
                Log.d(TAG, "Recording stopped, audio size: ${audioBytes.size} bytes")
                callback.onRecordingStopped(audioBytes)
            } else {
                Log.e(TAG, "Failed to read audio file")
                callback.onRecordingError("Failed to read recorded audio")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            cleanup()
            callback.onRecordingError("Error stopping recording: ${e.message}")
        }
    }

    /**
     * Cancel recording without saving
     */
    fun cancelRecording() {
        if (isRecording) {
            stopAmplitudeMonitoring()
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling recording", e)
            }
            cleanup()
            Log.d(TAG, "Recording canceled")
        }
    }
    
    /**
     * Set the amplitude callback for voice activity visualization
     */
    fun setAmplitudeCallback(callback: AmplitudeCallback?) {
        amplitudeCallback = callback
    }
    
    /**
     * Start amplitude monitoring
     */
    private fun startAmplitudeMonitoring() {
        amplitudeHandler = Handler(Looper.getMainLooper())
        amplitudeHandler?.postDelayed(amplitudeRunnable, AMPLITUDE_CHECK_INTERVAL)
    }
    
    /**
     * Stop amplitude monitoring
     */
    private fun stopAmplitudeMonitoring() {
        amplitudeHandler?.removeCallbacks(amplitudeRunnable)
        amplitudeHandler = null
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Get the path to the output file for audio processing
     */
    fun getOutputFilePath(): String? = outputFile?.absolutePath

    /**
     * Clean up resources
     */
    fun release() {
        cancelRecording()
    }

    /**
     * Create MediaRecorder instance (handles API level differences)
     */
    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }

    /**
     * Read the recorded audio file to byte array
     */
    private fun readAudioFile(): ByteArray? {
        return try {
            outputFile?.let { file ->
                if (file.exists()) {
                    FileInputStream(file).use { fis ->
                        fis.readBytes()
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading audio file", e)
            null
        }
    }

    /**
     * Clean up MediaRecorder and temporary files
     */
    private fun cleanup() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder", e)
        }
        mediaRecorder = null
        isRecording = false
        
        // Delete temporary file
        try {
            outputFile?.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting temp file", e)
        }
    }
}
