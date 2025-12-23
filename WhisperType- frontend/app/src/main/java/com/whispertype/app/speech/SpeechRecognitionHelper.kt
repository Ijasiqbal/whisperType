package com.whispertype.app.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.whispertype.app.Constants
import com.whispertype.app.ShortcutPreferences
import com.whispertype.app.api.WhisperApiClient
import com.whispertype.app.audio.AudioProcessor
import com.whispertype.app.audio.AudioRecorder
import com.whispertype.app.auth.FirebaseAuthManager
import kotlinx.coroutines.*
import java.io.File

/**
 * SpeechRecognitionHelper - Encapsulates audio recording and WhisperType API transcription
 * 
 * This helper class:
 * 1. Records audio using AudioRecorder
 * 2. Sends recorded audio to WhisperType API for transcription
 * 3. Provides clean callbacks for recognition events
 * 4. Handles errors gracefully
 * 
 * TRANSCRIPTION FLOW:
 * 
 * 1. startListening() -> Starts audio recording
 * 2. User speaks into microphone
 * 3. stopListening() -> Stops recording, sends audio to API
 * 4. API returns transcribed text
 * 5. onResults() callback is invoked with the text
 * 
 * LIMITATIONS:
 * 
 * - No real-time partial results (API only returns after processing complete audio)
 * - Requires network connectivity
 * - Audio file size should be under 25MB (WhisperType API limit)
 * 
 * MEMORY SAFETY:
 * - Properly cleans up coroutine scope
 * - Releases all resources on destroy
 */
class SpeechRecognitionHelper(
    private val context: Context,
    private val callback: Callback
) {

    companion object {
        private const val TAG = "SpeechRecHelper"
    }

    private val audioRecorder = AudioRecorder(context)
    private val audioProcessor = AudioProcessor(context)
    private val whisperApiClient = WhisperApiClient()
    private val authManager = FirebaseAuthManager()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * Battery optimized: Uses IO dispatcher for network/file operations
     * IO dispatcher is more efficient than Default for I/O-bound operations
     */
    private val processingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    @Volatile
    private var isListening = false
    
    @Volatile
    private var isDestroyed = false
    
    private var pendingAudioBytes: ByteArray? = null

    init {
        // Sign in anonymously on initialization
        processingScope.launch {
            authManager.ensureSignedIn()
        }
    }

    /**
     * Callback interface for speech recognition events
     * 
     * Note: This interface is kept compatible with the previous SpeechRecognizer-based
     * implementation to minimize changes to OverlayService.
     */
    interface Callback {
        fun onReadyForSpeech()
        fun onBeginningOfSpeech()
        fun onPartialResults(partialText: String)
        fun onResults(finalText: String)
        fun onError(errorMessage: String)
        fun onEndOfSpeech()
        fun onTranscribing()  // Called when audio is sent to API for transcription
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
     * Get the audio recorder for amplitude callback configuration
     */
    fun getAudioRecorder(): AudioRecorder = audioRecorder

    /**
     * Start listening for speech
     * 
     * This starts audio recording. Call stopListening() when the user is done speaking
     * to send the audio to the API for transcription.
     */
    fun startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening")
            return
        }

        if (!hasPermission()) {
            callback.onError("Microphone permission not granted")
            return
        }

        Log.d(TAG, "Starting audio recording")
        
        // Warm up: Start auth token refresh and Firebase Function warmup in parallel
        // This runs during recording so everything is ready when user stops speaking
        processingScope.launch {
            try {
                // Refresh auth token (will be cached for later use)
                authManager.ensureSignedIn()?.let {
                    authManager.getIdToken()
                    Log.d(TAG, "Auth token pre-fetched")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Auth warmup failed (non-critical): ${e.message}")
            }
        }
        whisperApiClient.warmHealth()  // Fire-and-forget health ping

        val started = audioRecorder.startRecording(object : AudioRecorder.RecordingCallback {
            override fun onRecordingStarted() {
                mainHandler.post {
                    isListening = true
                    callback.onReadyForSpeech()
                    callback.onBeginningOfSpeech()
                }
            }

            override fun onRecordingStopped(audioBytes: ByteArray) {
                // This is called from stopListening(), handled there
                pendingAudioBytes = audioBytes
            }

            override fun onRecordingError(error: String) {
                mainHandler.post {
                    isListening = false
                    callback.onError(error)
                }
            }
        })

        if (!started) {
            Log.e(TAG, "Failed to start recording")
        }
    }

    /**
     * Stop listening for speech and transcribe the recorded audio
     * 
     * This stops the audio recording and sends the audio to the WhisperType API
     * for transcription. The result will be delivered via the callback.
     */
    fun stopListening() {
        if (!isListening) {
            Log.w(TAG, "Not listening")
            return
        }

        Log.d(TAG, "Stopping audio recording")
        isListening = false
        
        // Update UI to show processing
        callback.onEndOfSpeech()

        audioRecorder.stopRecording(object : AudioRecorder.RecordingCallback {
            override fun onRecordingStarted() {
                // Not used here
            }

            override fun onRecordingStopped(audioBytes: ByteArray) {
                if (isDestroyed) return
                
                val originalSize = audioBytes.size
                Log.d(TAG, "Recording stopped, audio size: $originalSize bytes")
                
                // Check if audio is too short (likely empty)
                if (originalSize < Constants.SHORT_AUDIO_THRESHOLD_BYTES) {
                    mainHandler.post {
                        if (!isDestroyed) {
                            callback.onError("Recording too short")
                        }
                    }
                    return
                }
                
                // Get the output file path for processing
                val outputFilePath = audioRecorder.getOutputFilePath()
                if (outputFilePath == null) {
                    Log.w(TAG, "No output file available, sending original audio")
                    // Estimate duration: 1 second minimum
                    val estimatedDurationMs = (audioBytes.size.toLong() * 8 / 64).coerceAtLeast(1000)
                    transcribeAudio(audioBytes, "m4a", estimatedDurationMs)
                    return
                }
                
                // Process audio to remove silence (async on IO dispatcher for file operations)
                processingScope.launch {
                    try {
                        val inputFile = File(outputFilePath)
                        val processedResult = audioProcessor.trimSilence(inputFile)
                        
                        // Read processed audio bytes
                        val processedBytes = processedResult.file.readBytes()
                        val savedPercent = if (originalSize > 0) {
                            ((originalSize - processedBytes.size) * 100 / originalSize)
                        } else 0
                        
                        Log.d(TAG, "Audio processed: $originalSize -> ${processedBytes.size} bytes ($savedPercent% saved)")
                        
                        // Check if processed audio is valid
                        if (processedBytes.size < Constants.MIN_AUDIO_SIZE_BYTES) {
                            mainHandler.post {
                                if (!isDestroyed) {
                                    callback.onError("No speech detected")
                                }
                            }
                            return@launch
                        }
                        
                        // Send processed audio to API with correct format and duration
                        transcribeAudio(processedBytes, processedResult.format, processedResult.durationMs)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing audio, sending original", e)
                        // Fallback: send original audio (M4A format) with estimated duration
                        val estimatedDurationMs = (audioBytes.size.toLong() * 8 / 64).coerceAtLeast(1000)
                        transcribeAudio(audioBytes, "m4a", estimatedDurationMs)
                    }
                }
            }

            override fun onRecordingError(error: String) {
                mainHandler.post {
                    callback.onError(error)
                }
            }
        })
    }

    /**
     * Send audio to WhisperType API for transcription
     * @param audioBytes Raw audio bytes
     * @param audioFormat File format ("wav" or "m4a")
     * @param durationMs Duration of audio in milliseconds (for usage tracking)
     */
    private fun transcribeAudio(audioBytes: ByteArray, audioFormat: String = "m4a", durationMs: Long) {
        if (isDestroyed) return
        
        Log.d(TAG, "Sending audio to WhisperType API, format: $audioFormat")

        // Get auth token asynchronously, then make API call
        processingScope.launch {
            if (isDestroyed) return@launch
            
            // Ensure signed in
            val user = authManager.ensureSignedIn()
            if (user == null) {
                mainHandler.post {
                    callback.onError("Authentication failed. Please restart the app.")
                }
                return@launch
            }

            // Get ID token
            val token = authManager.getIdToken()
            if (token == null) {
                mainHandler.post {
                    callback.onError("Failed to get authentication token.")
                }
                return@launch
            }

            // Get user's selected model
            val selectedModel = ShortcutPreferences.getWhisperModel(context)
            val modelId = selectedModel.modelId
            Log.d(TAG, "Using model: $modelId (${selectedModel.displayName})")

            // Notify UI that transcription is starting
            mainHandler.post {
                if (!isDestroyed) {
                    callback.onTranscribing()
                }
            }

            // Make API call with auth token, selected model, and duration
            whisperApiClient.transcribe(audioBytes, token, audioFormat, modelId, durationMs, object : WhisperApiClient.TranscriptionCallback {
                override fun onSuccess(text: String) {
                    Log.d(TAG, "Transcription successful: ${text.take(50)}...")
                    mainHandler.post {
                        callback.onResults(text)
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Transcription error: $error")
                    mainHandler.post {
                        callback.onError(error)
                    }
                }
            })
        }
    }

    /**
     * Clean up resources and prevent further callbacks
     */
    fun destroy() {
        Log.d(TAG, "Destroying SpeechRecognitionHelper")
        
        isDestroyed = true
        isListening = false
        
        // Cancel ongoing coroutines
        processingScope.cancel()
        
        // Stop and release audio recorder
        try {
            if (audioRecorder.isRecording()) {
                audioRecorder.cancelRecording()
            }
            audioRecorder.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio recorder", e)
        }
        
        // Cancel API requests
        try {
            whisperApiClient.cancelAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling API requests", e)
        }
        
        // Remove all pending handler callbacks
        try {
            mainHandler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing handler callbacks", e)
        }
        
        // Clear pending data
        pendingAudioBytes = null
        
        Log.d(TAG, "SpeechRecognitionHelper destroyed")
    }
}
