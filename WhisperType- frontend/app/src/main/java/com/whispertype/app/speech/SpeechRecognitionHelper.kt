package com.whispertype.app.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
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
    private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var isListening = false
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

        val started = audioRecorder.startRecording(object : AudioRecorder.RecordingCallback {
            override fun onRecordingStarted() {
                mainHandler.post {
                    isListening = true
                    callback.onReadyForSpeech()
                    callback.onBeginningOfSpeech()
                    
                    // Show a "recording" indicator since we don't have real-time partial results
                    callback.onPartialResults("🎤 Recording...")
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
        
        // Signal end of speech
        callback.onEndOfSpeech()
        
        // Update UI to show processing
        callback.onPartialResults("⏳ Transcribing...")

        audioRecorder.stopRecording(object : AudioRecorder.RecordingCallback {
            override fun onRecordingStarted() {
                // Not used here
            }

            override fun onRecordingStopped(audioBytes: ByteArray) {
                val originalSize = audioBytes.size
                Log.d(TAG, "Recording stopped, audio size: $originalSize bytes")
                
                // Check if audio is too short (likely empty)
                if (originalSize < 1000) {
                    mainHandler.post {
                        callback.onError("Recording too short")
                    }
                    return
                }
                
                // Get the output file path for processing
                val outputFilePath = audioRecorder.getOutputFilePath()
                if (outputFilePath == null) {
                    Log.w(TAG, "No output file available, sending original audio")
                    transcribeAudio(audioBytes, "m4a")
                    return
                }
                
                // Update UI to show processing
                mainHandler.post {
                    callback.onPartialResults("⏳ Processing audio...")
                }
                
                // Process audio to remove silence (async)
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
                        if (processedBytes.size < 500) {
                            mainHandler.post {
                                callback.onError("No speech detected")
                            }
                            return@launch
                        }
                        
                        mainHandler.post {
                            callback.onPartialResults("⏳ Transcribing...")
                        }
                        
                        // Send processed audio to API with correct format
                        transcribeAudio(processedBytes, processedResult.format)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing audio, sending original", e)
                        // Fallback: send original audio (M4A format)
                        transcribeAudio(audioBytes, "m4a")
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
     */
    private fun transcribeAudio(audioBytes: ByteArray, audioFormat: String = "m4a") {
        Log.d(TAG, "Sending audio to WhisperType API, format: $audioFormat")

        // Get auth token asynchronously, then make API call
        processingScope.launch {
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

            // Make API call with auth token
            whisperApiClient.transcribe(audioBytes, token, audioFormat, object : WhisperApiClient.TranscriptionCallback {
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
     * Clean up resources
     */
    fun destroy() {
        Log.d(TAG, "Destroying SpeechRecognitionHelper")
        
        processingScope.cancel()
        if (isListening) {
            audioRecorder.cancelRecording()
        }
        audioRecorder.release()
        whisperApiClient.cancelAll()
        isListening = false
    }
}
