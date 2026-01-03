package com.whispertype.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.whispertype.app.Constants
import java.io.File

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
 * 
 * MEMORY SAFETY:
 * - Properly cleans up handlers and callbacks to prevent leaks
 * - Uses try-finally blocks for file operations
 */
class AudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecorder"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    
    // Wake lock to prevent device from sleeping during recording
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Amplitude monitoring (battery optimized)
    @Volatile
    private var amplitudeHandler: Handler? = null
    
    @Volatile
    private var amplitudeCallback: AmplitudeCallback? = null
    
    @Volatile
    private var lastVoiceDetectedTime = 0L
    
    /**
     * Adaptive amplitude monitoring runnable
     * Battery optimization: Reduces check frequency when no voice is detected
     */
    private val amplitudeRunnable = object : Runnable {
        override fun run() {
            if (isRecording && mediaRecorder != null) {
                try {
                    val amplitude = mediaRecorder?.maxAmplitude ?: 0
                    amplitudeCallback?.onAmplitude(amplitude)
                    
                    // Track when voice was last detected
                    if (amplitude > Constants.VOICE_ACTIVITY_THRESHOLD) {
                        lastVoiceDetectedTime = System.currentTimeMillis()
                    }
                    
                    // Battery optimization: Use longer interval if no voice for 2+ seconds
                    val timeSinceVoice = System.currentTimeMillis() - lastVoiceDetectedTime
                    val nextInterval = if (timeSinceVoice > Constants.SILENCE_DURATION_FOR_IDLE_MS) {
                        Constants.AMPLITUDE_CHECK_INTERVAL_IDLE_MS  // 300ms when idle
                    } else {
                        Constants.AMPLITUDE_CHECK_INTERVAL_MS  // 150ms when speaking
                    }
                    
                    amplitudeHandler?.postDelayed(this, nextInterval)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting amplitude", e)
                    // Fallback to normal interval on error
                    amplitudeHandler?.postDelayed(this, Constants.AMPLITUDE_CHECK_INTERVAL_MS)
                }
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

        return try {
            // Create output file in cache directory
            outputFile = File(context.cacheDir, Constants.AUDIO_FILE_NAME)
            
            // Delete existing file if any
            outputFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                }
            }

            // Create and configure MediaRecorder
            mediaRecorder = createMediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(Constants.AUDIO_SAMPLE_RATE)
                setAudioEncodingBitRate(Constants.AUDIO_BIT_RATE)
                setAudioChannels(Constants.AUDIO_CHANNELS)
                setOutputFile(outputFile?.absolutePath)
                
                prepare()
                start()
            }

            isRecording = true
            
            // Acquire wake lock to prevent device from sleeping during recording
            acquireWakeLock()
            
            Log.d(TAG, "Recording started, output: ${outputFile?.absolutePath}")
            callback.onRecordingStarted()
            
            // Start amplitude monitoring
            startAmplitudeMonitoring()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            callback.onRecordingError("Failed to start recording: ${e.message}")
            false
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
            
            // Release wake lock
            releaseWakeLock()
            
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            // Small delay to ensure file system has flushed data
            Thread.sleep(20)

            // Read audio file with retry logic
            // Some devices need time to fully write the file after MediaRecorder.stop()
            var audioBytes: ByteArray? = null
            val maxRetries = 3
            val retryDelayMs = 30L
            
            for (attempt in 1..maxRetries) {
                audioBytes = readAudioFile()
                val fileSize = outputFile?.length() ?: 0
                Log.d(TAG, "Read attempt $attempt: file size = $fileSize bytes, audioBytes size = ${audioBytes?.size ?: 0}")
                
                if (audioBytes != null && audioBytes.isNotEmpty()) {
                    break  // Success, exit retry loop
                }
                
                if (attempt < maxRetries) {
                    Log.d(TAG, "Retrying file read after ${retryDelayMs}ms...")
                    Thread.sleep(retryDelayMs)
                }
            }
            
            if (audioBytes != null && audioBytes.isNotEmpty()) {
                Log.d(TAG, "Recording stopped, audio size: ${audioBytes.size} bytes")
                callback.onRecordingStopped(audioBytes)
            } else {
                val fileExists = outputFile?.exists() ?: false
                val fileSize = outputFile?.length() ?: 0
                Log.e(TAG, "Failed to read audio file after $maxRetries attempts. File exists: $fileExists, File size: $fileSize")
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
     * Start amplitude monitoring with proper handler lifecycle management
     * Battery optimized: Uses adaptive polling frequency
     */
    private fun startAmplitudeMonitoring() {
        // Ensure we clean up any existing handler first
        stopAmplitudeMonitoring()
        
        // Reset voice detection timer
        lastVoiceDetectedTime = System.currentTimeMillis()
        
        amplitudeHandler = Handler(Looper.getMainLooper())
        amplitudeHandler?.postDelayed(amplitudeRunnable, Constants.AMPLITUDE_CHECK_INTERVAL_MS)
    }
    
    /**
     * Stop amplitude monitoring and clean up handler resources
     * Note: Does NOT clear amplitudeCallback - that's managed separately via setAmplitudeCallback()
     */
    private fun stopAmplitudeMonitoring() {
        amplitudeHandler?.removeCallbacks(amplitudeRunnable)
        amplitudeHandler = null
        lastVoiceDetectedTime = 0L
    }
    
    /**
     * Acquire a partial wake lock to keep the CPU running during recording.
     * This prevents the device from sleeping and interrupting the recording.
     */
    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WhisperType::RecordingWakeLock"
            ).apply {
                // Set a timeout of 10 minutes to prevent battery drain if not properly released
                acquire(10 * 60 * 1000L)
            }
            Log.d(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }
    
    /**
     * Release the wake lock when recording stops
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
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
     * Uses use {} to ensure proper file closure
     */
    private fun readAudioFile(): ByteArray? {
        return try {
            outputFile?.takeIf { it.exists() }?.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading audio file", e)
            null
        }
    }

    /**
     * Clean up MediaRecorder and temporary files
     * Ensures all resources are properly released to prevent leaks
     */
    private fun cleanup() {
        // Stop amplitude monitoring first to prevent dangling callbacks
        stopAmplitudeMonitoring()
        
        // Release wake lock to prevent battery drain
        releaseWakeLock()
        
        // Release MediaRecorder
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // Ignore errors if already stopped/released
            Log.d(TAG, "MediaRecorder cleanup: ${e.message}")
        } finally {
            mediaRecorder = null
        }
        
        isRecording = false
        
        // Delete temporary file
        try {
            outputFile?.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting temp file", e)
        } finally {
            outputFile = null
        }
    }
}
