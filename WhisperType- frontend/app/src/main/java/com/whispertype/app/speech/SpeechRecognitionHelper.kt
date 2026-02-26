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
import com.whispertype.app.audio.AudioRecorder
import com.whispertype.app.audio.ParallelOpusRecorder
import com.whispertype.app.audio.RealtimeRmsRecorder
import com.whispertype.app.auth.FirebaseAuthManager
import kotlinx.coroutines.*

/**
 * SpeechRecognitionHelper - Encapsulates audio recording and WhisperType API transcription
 *
 * UNIFIED RECORDING FLOW:
 * All model tiers (Free, Standard, Premium) use the same recorder (ParallelOpusRecorder).
 * The tier selection only affects which transcription backend receives the audio AFTER recording stops.
 *
 * This solves the crash/stop issue when switching tiers during recording - since all tiers
 * use the same recorder, tier changes during recording are safe.
 *
 * Flow:
 * 1. startListening() -> ParallelOpusRecorder starts (all tiers)
 * 2. User speaks into microphone
 * 3. Parallel RMS analysis detects speech/silence in real-time
 * 4. Parallel Opus encoding compresses audio in real-time
 * 5. stopListening() -> Get trimmed OGG audio
 * 6. Check CURRENT tier at stop time
 * 7. Route to appropriate backend (Groq Turbo/Groq Whisper/OpenAI Mini)
 *
 * Fallback: Android < 10 (no Opus support) uses RealtimeRmsRecorder (WAV output)
 */
class SpeechRecognitionHelper(
    private val context: Context,
    private val callback: Callback
) {

    companion object {
        private const val TAG = "SpeechRecHelper"
    }

    // Primary recorder: ParallelOpusRecorder (Android 10+)
    private val parallelOpusRecorder = ParallelOpusRecorder(context)

    // Fallback recorder: RealtimeRmsRecorder for Android < 10 (outputs WAV)
    private val realtimeRmsRecorder = RealtimeRmsRecorder(context)

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

    // Pending audio data from recorder callback
    private var pendingAudioBytes: ByteArray? = null
    private var pendingAudioFormat: String = "ogg"  // "ogg" for ParallelOpusRecorder, "wav" for fallback
    private var pendingDurationMs: Long = 0L

    // Track which recorder is active (for cleanup)
    private var usingFallbackRecorder = false

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
    
    private var amplitudeCallback: AudioRecorder.AmplitudeCallback? = null

    /**
     * Set amplitude callback for voice activity visualization
     * This ensures the callback is attached to the active recorder
     */
    fun setAmplitudeCallback(callback: AudioRecorder.AmplitudeCallback?) {
        this.amplitudeCallback = callback
        // Set on both recorders to ensure whichever is used reports amplitude
        parallelOpusRecorder.setAmplitudeCallback(callback)
        realtimeRmsRecorder.setAmplitudeCallback(callback)
    }

    /**
     * Start listening for speech
     *
     * UNIFIED RECORDING: All model tiers use the same recorder (ParallelOpusRecorder).
     * The tier is checked at STOP time, not start time, allowing safe tier switching during recording.
     *
     * Call stopListening() when the user is done speaking to send the audio to the API.
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

        // Reset pending data
        pendingAudioBytes = null
        pendingAudioFormat = "ogg"
        pendingDurationMs = 0L

        Log.d(TAG, "Starting unified recording (all tiers use same recorder)")

        // Warm up: Start auth token refresh in parallel
        // This runs during recording so everything is ready when user stops speaking
        processingScope.launch {
            try {
                authManager.ensureSignedIn()?.let {
                    authManager.getIdToken()
                    Log.d(TAG, "Auth token pre-fetched")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Auth warmup failed (non-critical): ${e.message}")
            }
        }

        // Warm all potential endpoints since user might switch tiers during recording
        whisperApiClient.warmAllEndpoints()

        // Check if device supports Opus encoding (Android 10+)
        if (ParallelOpusRecorder.isSupported()) {
            // Primary path: ParallelOpusRecorder (parallel RMS + Opus encoding)
            usingFallbackRecorder = false
            Log.d(TAG, "Using ParallelOpusRecorder (parallel RMS + Opus encoding)")

            parallelOpusRecorder.setAmplitudeCallback(amplitudeCallback)
            parallelOpusRecorder.startRecording(object : ParallelOpusRecorder.RecordingCallback {
                override fun onRecordingStarted() {
                    mainHandler.post {
                        isListening = true
                        callback.onReadyForSpeech()
                        callback.onBeginningOfSpeech()
                    }
                }

                override fun onRecordingStopped(trimmedOggBytes: ByteArray, rawOggBytes: ByteArray, metadata: ParallelOpusRecorder.Metadata) {
                    Log.d(TAG, "Recording stopped. Trimmed: ${trimmedOggBytes.size} bytes, " +
                            "Raw: ${rawOggBytes.size} bytes, Speech: ${metadata.speechDurationMs}ms of ${metadata.originalDurationMs}ms")
                    pendingAudioBytes = trimmedOggBytes
                    pendingAudioFormat = "ogg"
                    pendingDurationMs = metadata.speechDurationMs
                }

                override fun onRecordingError(error: String) {
                    mainHandler.post {
                        isListening = false
                        callback.onError(error)
                    }
                }
            })
        } else {
            // Fallback path: RealtimeRmsRecorder for Android < 10 (outputs WAV)
            usingFallbackRecorder = true
            Log.d(TAG, "Android < 10: Using RealtimeRmsRecorder fallback (WAV output)")

            realtimeRmsRecorder.setAmplitudeCallback(amplitudeCallback)
            realtimeRmsRecorder.startRecording(object : RealtimeRmsRecorder.RecordingCallback {
                override fun onRecordingStarted() {
                    mainHandler.post {
                        isListening = true
                        callback.onReadyForSpeech()
                        callback.onBeginningOfSpeech()
                    }
                }

                override fun onRecordingStopped(trimmedAudioBytes: ByteArray, rawAudioBytes: ByteArray, metadata: RealtimeRmsRecorder.RmsMetadata) {
                    Log.d(TAG, "Recording stopped (fallback). Trimmed: ${trimmedAudioBytes.size} bytes, " +
                            "Raw: ${rawAudioBytes.size} bytes, Speech: ${metadata.speechDurationMs}ms of ${metadata.originalDurationMs}ms")
                    pendingAudioBytes = trimmedAudioBytes
                    pendingAudioFormat = "wav"
                    pendingDurationMs = metadata.speechDurationMs
                }

                override fun onRecordingError(error: String) {
                    mainHandler.post {
                        isListening = false
                        callback.onError(error)
                    }
                }
            })
        }
    }

    /**
     * Stop listening for speech and transcribe the recorded audio
     *
     * UNIFIED FLOW: Stops the active recorder, then checks the CURRENT model tier
     * to determine which transcription backend to use. This allows safe tier switching
     * during recording.
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

        // Stop the active recorder
        if (usingFallbackRecorder) {
            realtimeRmsRecorder.stopRecording()
        } else {
            parallelOpusRecorder.stopRecording()
        }

        // Wait for recorder callback to populate pendingAudioBytes, then route based on CURRENT tier
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

            // Get CURRENT tier at stop time (allows switching during recording)
            val currentTier = ShortcutPreferences.getModelTier(context)
            val flow = TranscriptionFlow.fromModelTier(currentTier)

            Log.d(TAG, "Routing to transcription: tier=$currentTier, flow=${flow.name}, " +
                    "format=$pendingAudioFormat, size=${trimmedAudio.size} bytes")

            // Route to appropriate transcription backend
            transcribeWithFlow(trimmedAudio, pendingAudioFormat, pendingDurationMs, flow)
        }, 100)
    }

    /**
     * Route audio to appropriate transcription backend based on flow
     *
     * This is the main routing method used by the unified recording flow.
     * The flow is determined by the model tier at stop time.
     *
     * @param audioBytes Trimmed audio bytes (OGG or WAV depending on recorder)
     * @param audioFormat File format ("ogg" for ParallelOpusRecorder, "wav" for fallback)
     * @param durationMs Estimated duration in milliseconds (for usage tracking)
     * @param flow The transcription flow to use (determined by model tier)
     */
    private fun transcribeWithFlow(audioBytes: ByteArray, audioFormat: String, durationMs: Long, flow: TranscriptionFlow) {
        if (isDestroyed) return

        Log.d(TAG, "transcribeWithFlow: flow=${flow.name}, format=$audioFormat, size=${audioBytes.size}, duration=${durationMs}ms")

        when (flow) {
            TranscriptionFlow.FLOW_3 -> {
                // FREE tier → Groq Turbo (whisper-large-v3-turbo) - fastest, unlimited
                Log.d(TAG, "[FREE] Sending to Groq Turbo API")
                transcribeWithGroqTurboApi(audioBytes, audioFormat, durationMs)
            }
            TranscriptionFlow.GROQ_WHISPER -> {
                // STANDARD tier → Groq Whisper (whisper-large-v3) - high accuracy
                Log.d(TAG, "[STANDARD] Sending to Groq Whisper API")
                transcribeWithGroqApi(audioBytes, audioFormat, durationMs)
            }
            TranscriptionFlow.PARALLEL_OPUS -> {
                // PREMIUM tier → OpenAI (gpt-4o-mini-transcribe) - best quality
                Log.d(TAG, "[PREMIUM] Sending to OpenAI API")
                transcribeWithParallelOpusFlow(audioBytes, audioFormat, durationMs)
            }
            TranscriptionFlow.TWO_STAGE_AUTO -> {
                // NEW AUTO → Two-stage: Groq Turbo (no prompt) → Llama cleanup
                Log.d(TAG, "[NEW_AUTO] Sending to Two-Stage API (turbo → llama)")
                transcribeWithTwoStageApi(audioBytes, audioFormat, durationMs, "whisper-large-v3-turbo", "AUTO", null)
            }
            TranscriptionFlow.TWO_STAGE_NEWER_AUTO -> {
                // NEWER AUTO → Two-stage: Groq Turbo (no prompt) → GPT-OSS cleanup
                Log.d(TAG, "[NEWER_AUTO] Sending to Two-Stage API (turbo → llama-3.3-70b-specdec)")
                transcribeWithTwoStageApi(audioBytes, audioFormat, durationMs, "whisper-large-v3-turbo", "AUTO", "openai/gpt-oss-20b")
            }
            else -> {
                // Fallback to premium flow for any other flows (ARAMUS_OPENAI, FLOW_4)
                Log.d(TAG, "[FALLBACK] Using OpenAI API for flow: ${flow.name}")
                transcribeWithParallelOpusFlow(audioBytes, audioFormat, durationMs)
            }
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
     * Transcribe using OpenAI API (gpt-4o-mini-transcribe)
     * Used for PREMIUM tier transcription - best quality
     */
    private fun transcribeWithParallelOpusFlow(audioBytes: ByteArray, audioFormat: String, durationMs: Long) {
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

            val modelId = "gpt-4o-mini-transcribe"
            Log.d(TAG, "[OpenAI] Using model: $modelId, format: $audioFormat, duration: ${durationMs}ms, size: ${audioBytes.size} bytes")

            // Notify UI that transcription is starting
            mainHandler.post {
                if (!isDestroyed) {
                    callback.onTranscribing()
                }
            }

            // Make API call with auth token and gpt-4o-mini-transcribe model
            whisperApiClient.transcribe(audioBytes, token, audioFormat, modelId, durationMs, object : WhisperApiClient.TranscriptionCallback {
                override fun onSuccess(text: String) {
                    Log.d(TAG, "[OpenAI] Transcription successful: ${text.take(50)}...")
                    mainHandler.post {
                        callback.onResults(text)
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "[OpenAI] Transcription error: $error")
                    mainHandler.post {
                        callback.onError(error)
                    }
                }
            })
        }
    }

    /**
     * Transcribe using the two-stage pipeline:
     * Stage 1: Groq Whisper (no prompt) for raw transcription
     * Stage 2: Groq Llama for cleanup, formatting, and punctuation
     *
     * @param audioBytes Audio bytes to transcribe
     * @param audioFormat Audio format ("ogg", "wav", etc.)
     * @param durationMs Audio duration in milliseconds
     * @param model Groq STT model to use ("whisper-large-v3-turbo" or "whisper-large-v3")
     * @param tier Billing tier ("AUTO", "STANDARD", or "PREMIUM")
     */
    private fun transcribeWithTwoStageApi(
        audioBytes: ByteArray,
        audioFormat: String,
        durationMs: Long,
        model: String,
        tier: String,
        llmModel: String?
    ) {
        processingScope.launch {
            if (isDestroyed) return@launch

            val user = authManager.ensureSignedIn()
            if (user == null) {
                mainHandler.post {
                    callback.onError("Authentication failed. Please restart the app.")
                }
                return@launch
            }

            val token = authManager.getIdToken()
            if (token == null) {
                mainHandler.post {
                    callback.onError("Failed to get authentication token.")
                }
                return@launch
            }

            Log.d(TAG, "[TwoStage] Calling API with model: $model, tier: $tier, llmModel: $llmModel, format: $audioFormat, duration: ${durationMs}ms")

            mainHandler.post {
                if (!isDestroyed) {
                    callback.onTranscribing()
                }
            }

            whisperApiClient.transcribeWithTwoStage(
                audioBytes, token, audioFormat, durationMs, model, tier, llmModel,
                object : WhisperApiClient.TranscriptionCallback {
                    override fun onSuccess(text: String) {
                        Log.d(TAG, "[TwoStage] Transcription successful: ${text.take(50)}...")
                        mainHandler.post {
                            callback.onResults(text)
                        }
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "[TwoStage] Transcription error: $error")
                        mainHandler.post {
                            callback.onError(error)
                        }
                    }
                }
            )
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
