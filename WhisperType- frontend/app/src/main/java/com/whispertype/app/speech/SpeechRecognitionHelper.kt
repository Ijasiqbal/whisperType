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
import com.whispertype.app.audio.RealtimeRmsRecorder
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
    private val realtimeRmsRecorder = RealtimeRmsRecorder(context)
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

        // Check which flow is selected to use appropriate recorder
        val selectedFlow = TranscriptionFlow.getSelectedFlow(context)
        Log.d(TAG, "Starting audio recording with flow: ${selectedFlow.name}")

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
        whisperApiClient.warmForFlow(context)  // Warm the correct endpoint for selected flow

        // Use RealtimeRmsRecorder for ARAMUS flow (parallel RMS analysis)
        if (selectedFlow == TranscriptionFlow.ARAMUS_OPENAI) {
            Log.d(TAG, "ARAMUS_OPENAI flow: Using RealtimeRmsRecorder with parallel RMS analysis")
            realtimeRmsRecorder.startRecording(object : RealtimeRmsRecorder.RecordingCallback {
                override fun onRecordingStarted() {
                    mainHandler.post {
                        isListening = true
                        callback.onReadyForSpeech()
                        callback.onBeginningOfSpeech()
                    }
                }

                override fun onRecordingStopped(trimmedAudioBytes: ByteArray, rawAudioBytes: ByteArray, metadata: RealtimeRmsRecorder.RmsMetadata) {
                    // Audio is already trimmed by parallel RMS analysis
                    Log.d(TAG, "ARAMUS_OPENAI: Recording stopped. Trimmed: ${trimmedAudioBytes.size} bytes, " +
                            "Raw: ${rawAudioBytes.size} bytes, Speech: ${metadata.speechDurationMs}ms of ${metadata.originalDurationMs}ms")
                    pendingAudioBytes = trimmedAudioBytes
                }

                override fun onRecordingError(error: String) {
                    mainHandler.post {
                        isListening = false
                        callback.onError(error)
                    }
                }
            })
            return
        }

        // Default: Use AudioRecorder (MediaRecorder) for other flows
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

        // Check which flow is selected
        val selectedFlow = TranscriptionFlow.getSelectedFlow(context)

        // Handle ARAMUS flow separately - uses RealtimeRmsRecorder
        if (selectedFlow == TranscriptionFlow.ARAMUS_OPENAI) {
            realtimeRmsRecorder.stopRecording()
            // The callback was registered in startListening(), audio will be in pendingAudioBytes
            // Wait a brief moment for the callback to be processed
            mainHandler.postDelayed({
                val trimmedAudio = pendingAudioBytes
                if (trimmedAudio == null || trimmedAudio.isEmpty()) {
                    callback.onError("No audio recorded")
                    return@postDelayed
                }

                if (trimmedAudio.size < Constants.MIN_AUDIO_SIZE_BYTES) {
                    callback.onError("No speech detected")
                    return@postDelayed
                }

                // Estimate duration: WAV at 16kHz, mono, 16-bit = 32000 bytes per second
                val estimatedDurationMs = (trimmedAudio.size.toLong() * 1000 / 32000).coerceAtLeast(1000)

                // ARAMUS_OPENAI: Send WAV directly
                Log.d(TAG, "ARAMUS_OPENAI: Sending pre-trimmed WAV audio, size: ${trimmedAudio.size} bytes")
                transcribeAudio(trimmedAudio, "wav", estimatedDurationMs)
            }, 100)
            return
        }

        // Default path: Use AudioRecorder (MediaRecorder) for other flows
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
                
                // Get the output file path and format for processing
                val outputFilePath = audioRecorder.getOutputFilePath()
                val audioFormat = audioRecorder.getAudioFormat()
                
                // Check which flow is selected
                val selectedFlow = TranscriptionFlow.getSelectedFlow(context)
                
                // For flows that skip silence trimming: GROQ_WHISPER, FLOW_3, and FLOW_4
                if (selectedFlow == TranscriptionFlow.GROQ_WHISPER || selectedFlow == TranscriptionFlow.FLOW_3 || selectedFlow == TranscriptionFlow.FLOW_4) {
                    Log.d(TAG, "${selectedFlow.name} flow: skipping silence trimming, sending raw audio")
                    // Estimate duration based on format bitrate
                    val bitrate = if (audioFormat == "ogg") 24 else 64  // kbps
                    val estimatedDurationMs = (audioBytes.size.toLong() * 8 / bitrate).coerceAtLeast(1000)
                    transcribeAudio(audioBytes, audioFormat, estimatedDurationMs)
                    return
                }
                
                // For other flows: apply silence trimming
                if (outputFilePath == null) {
                    Log.w(TAG, "No output file available, sending original audio")
                    // Estimate duration based on format bitrate
                    val bitrate = if (audioFormat == "ogg") 24 else 64  // kbps
                    val estimatedDurationMs = (audioBytes.size.toLong() * 8 / bitrate).coerceAtLeast(1000)
                    transcribeAudio(audioBytes, audioFormat, estimatedDurationMs)
                    return
                }

                // Process audio to remove silence (async on IO dispatcher for file operations)
                // AudioProcessor supports both M4A and OGG/Opus formats
                processingScope.launch {
                    try {
                        val inputFile = File(outputFilePath)
                        val processedResult = audioProcessor.trimSilence(inputFile, audioFormat)
                        
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
                        
                        // Send processed audio to API with correct format and original duration for billing
                        transcribeAudio(processedBytes, processedResult.format, processedResult.originalDurationMs)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing audio, sending original", e)
                        // Fallback: send original audio with estimated duration
                        val bitrate = if (audioFormat == "ogg") 24 else 64  // kbps
                        val estimatedDurationMs = (audioBytes.size.toLong() * 8 / bitrate).coerceAtLeast(1000)
                        transcribeAudio(audioBytes, audioFormat, estimatedDurationMs)
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
        
        // Check which transcription flow is selected
        val selectedFlow = TranscriptionFlow.getSelectedFlow(context)
        Log.d(TAG, "Using transcription flow: ${selectedFlow.name}")
        
        when (selectedFlow) {
            TranscriptionFlow.GROQ_WHISPER -> {
                // Groq Whisper flow - use Groq's ultra-fast whisper-large-v3
                Log.d(TAG, "GROQ_WHISPER flow: sending audio to Groq API, format: $audioFormat")
                transcribeWithGroqApi(audioBytes, audioFormat, durationMs)
            }
            TranscriptionFlow.FLOW_3 -> {
                // Flow 3 - Groq Turbo (whisper-large-v3-turbo) - fastest transcription
                Log.d(TAG, "FLOW_3 (Groq Turbo): sending audio to Groq API with whisper-large-v3-turbo, format: $audioFormat")
                transcribeWithGroqTurboApi(audioBytes, audioFormat, durationMs)
            }
            TranscriptionFlow.FLOW_4 -> {
                // Flow 4 - OpenAI GPT-4o-mini-transcribe without silence trimming
                Log.d(TAG, "FLOW_4 (OpenAI Mini No Trim): sending raw audio to Cloud API with gpt-4o-mini-transcribe, format: $audioFormat")
                transcribeWithCloudApiNoTrim(audioBytes, audioFormat, durationMs)
            }
            TranscriptionFlow.ARAMUS_OPENAI -> {
                // Aramus + OpenAI - Parallel RMS (already trimmed) with GPT-4o-mini-transcribe
                Log.d(TAG, "ARAMUS_OPENAI: sending pre-trimmed WAV to Cloud API with gpt-4o-mini-transcribe, format: $audioFormat")
                transcribeWithAramusFlow(audioBytes, audioFormat, durationMs)
            }
        }
    }
    
    /**
     * Transcribe using Cloud API (OpenAI Whisper via WhisperType backend)
     */
    private fun transcribeWithCloudApi(audioBytes: ByteArray, audioFormat: String, durationMs: Long) {
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
     * Transcribe using Cloud API without silence trimming (GPT-4o-mini-transcribe)
     * This uses OpenAI's gpt-4o-mini-transcribe model directly without pre-processing
     */
    private fun transcribeWithCloudApiNoTrim(audioBytes: ByteArray, audioFormat: String, durationMs: Long) {
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

            // Force use of gpt-4o-mini-transcribe model for this flow
            val modelId = "gpt-4o-mini-transcribe"
            Log.d(TAG, "[OpenAI No Trim] Using model: $modelId, format: $audioFormat, duration: ${durationMs}ms")

            // Notify UI that transcription is starting
            mainHandler.post {
                if (!isDestroyed) {
                    callback.onTranscribing()
                }
            }

            // Make API call with auth token and gpt-4o-mini-transcribe model
            whisperApiClient.transcribe(audioBytes, token, audioFormat, modelId, durationMs, object : WhisperApiClient.TranscriptionCallback {
                override fun onSuccess(text: String) {
                    Log.d(TAG, "[OpenAI No Trim] Transcription successful: ${text.take(50)}...")
                    mainHandler.post {
                        callback.onResults(text)
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "[OpenAI No Trim] Transcription error: $error")
                    mainHandler.post {
                        callback.onError(error)
                    }
                }
            })
        }
    }

    /**
     * Transcribe using Groq API (whisper-large-v3)
     * This method skips silence trimming and uses Groq's ultra-fast transcription
     */
    private fun transcribeWithGroqApi(audioBytes: ByteArray, audioFormat: String, durationMs: Long) {
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

            Log.d(TAG, "Calling Groq API with format: $audioFormat, duration: ${durationMs}ms")

            // Notify UI that transcription is starting
            mainHandler.post {
                if (!isDestroyed) {
                    callback.onTranscribing()
                }
            }

            // Make Groq API call
            whisperApiClient.transcribeWithGroq(audioBytes, token, audioFormat, durationMs, null, object : WhisperApiClient.TranscriptionCallback {
                override fun onSuccess(text: String) {
                    Log.d(TAG, "[Groq] Transcription successful: ${text.take(50)}...")
                    mainHandler.post {
                        callback.onResults(text)
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "[Groq] Transcription error: $error")
                    mainHandler.post {
                        callback.onError(error)
                    }
                }
            })
        }
    }

    /**
     * Transcribe using Groq Turbo API (whisper-large-v3-turbo)
     * This is the fastest transcription option with slightly lower accuracy
     */
    private fun transcribeWithGroqTurboApi(audioBytes: ByteArray, audioFormat: String, durationMs: Long) {
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

            Log.d(TAG, "[Groq Turbo] Calling Groq API with whisper-large-v3-turbo, format: $audioFormat, duration: ${durationMs}ms")

            // Notify UI that transcription is starting
            mainHandler.post {
                if (!isDestroyed) {
                    callback.onTranscribing()
                }
            }

            // Make Groq API call with whisper-large-v3-turbo model
            whisperApiClient.transcribeWithGroq(audioBytes, token, audioFormat, durationMs, "whisper-large-v3-turbo", object : WhisperApiClient.TranscriptionCallback {
                override fun onSuccess(text: String) {
                    Log.d(TAG, "[Groq Turbo] Transcription successful: ${text.take(50)}...")
                    mainHandler.post {
                        callback.onResults(text)
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "[Groq Turbo] Transcription error: $error")
                    mainHandler.post {
                        callback.onError(error)
                    }
                }
            })
        }
    }

    /**
     * Transcribe using Aramus + OpenAI flow (Parallel RMS + GPT-4o-mini-transcribe)
     * Audio is already trimmed by RealtimeRmsRecorder's parallel RMS analysis
     * This uses OpenAI's gpt-4o-mini-transcribe model
     */
    private fun transcribeWithAramusFlow(audioBytes: ByteArray, audioFormat: String, durationMs: Long) {
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

            // Use gpt-4o-mini-transcribe model (same as FLOW_4 but with pre-trimmed audio)
            val modelId = "gpt-4o-mini-transcribe"
            Log.d(TAG, "[Aramus+OpenAI] Using model: $modelId, format: $audioFormat, duration: ${durationMs}ms")

            // Notify UI that transcription is starting
            mainHandler.post {
                if (!isDestroyed) {
                    callback.onTranscribing()
                }
            }

            // Make API call with auth token and gpt-4o-mini-transcribe model
            whisperApiClient.transcribe(audioBytes, token, audioFormat, modelId, durationMs, object : WhisperApiClient.TranscriptionCallback {
                override fun onSuccess(text: String) {
                    Log.d(TAG, "[Aramus+OpenAI] Transcription successful: ${text.take(50)}...")
                    mainHandler.post {
                        callback.onResults(text)
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "[Aramus+OpenAI] Transcription error: $error")
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
