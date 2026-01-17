package com.whispertype.app.api

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.whispertype.app.Constants
import com.whispertype.app.data.UsageDataManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * WhisperApiClient - Handles communication with the WhisperType transcription API
 * 
 * This client:
 * 1. Sends base64-encoded audio to the transcription endpoint
 * 2. Parses the JSON response
 * 3. Handles errors appropriately
 * 
 * API Endpoint: POST https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudio
 * Request: {"audioBase64": "<base64_audio>"}
 * Response: {"text": "transcribed text"}
 * 
 * NOTE: Uses a singleton OkHttpClient for better connection pooling and resource management
 */
class WhisperApiClient {

    companion object {
        private const val TAG = "WhisperApiClient"
        private const val API_URL = "https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudio"
        private const val TRIAL_STATUS_URL = "https://us-central1-whispertype-1de9f.cloudfunctions.net/getTrialStatus"
        private const val HEALTH_URL = "https://us-central1-whispertype-1de9f.cloudfunctions.net/health"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        // Retry configuration
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 8000L
        private const val BACKOFF_MULTIPLIER = 2.0

        /**
         * Singleton OkHttpClient for efficient connection pooling and resource reuse
         * Using lazy initialization for thread-safe singleton
         * Includes retry interceptor for handling transient failures
         */
        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(Constants.API_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(Constants.API_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(Constants.API_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(RetryInterceptor(MAX_RETRIES, INITIAL_BACKOFF_MS, BACKOFF_MULTIPLIER, MAX_BACKOFF_MS))
                .build()
        }

        private val gson: Gson by lazy { Gson() }

        /**
         * Check if an exception is retryable (transient network issue)
         */
        private fun isRetryableException(e: Exception): Boolean {
            return when (e) {
                is SocketTimeoutException -> true
                is UnknownHostException -> true
                is SSLException -> e.message?.contains("Connection reset", ignoreCase = true) == true
                is IOException -> {
                    val message = e.message?.lowercase() ?: ""
                    message.contains("timeout") ||
                    message.contains("connection reset") ||
                    message.contains("broken pipe") ||
                    message.contains("connection refused")
                }
                else -> false
            }
        }

        /**
         * Check if a response code is retryable (server-side transient error)
         */
        private fun isRetryableResponseCode(code: Int): Boolean {
            return code == 502 || code == 503 || code == 504 || code == 408
        }
    }

    /**
     * OkHttp Interceptor that implements retry logic with exponential backoff
     *
     * Retries on:
     * - Network failures (timeout, connection reset, etc.)
     * - Server errors (502, 503, 504, 408)
     *
     * Does NOT retry on:
     * - Client errors (4xx except 408)
     * - Successful responses (2xx)
     * - Non-retryable exceptions
     */
    private class RetryInterceptor(
        private val maxRetries: Int,
        private val initialBackoffMs: Long,
        private val backoffMultiplier: Double,
        private val maxBackoffMs: Long
    ) : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            var lastException: IOException? = null
            var lastResponse: Response? = null

            for (attempt in 0..maxRetries) {
                // Close previous response if retrying
                lastResponse?.close()

                try {
                    val response = chain.proceed(request)

                    // Check if response code is retryable
                    if (isRetryableResponseCode(response.code) && attempt < maxRetries) {
                        Log.w(TAG, "Retryable response code ${response.code}, attempt ${attempt + 1}/$maxRetries")
                        response.close()
                        waitWithBackoff(attempt)
                        continue
                    }

                    return response

                } catch (e: IOException) {
                    lastException = e

                    if (attempt < maxRetries && isRetryableException(e)) {
                        Log.w(TAG, "Retryable exception: ${e.javaClass.simpleName}, attempt ${attempt + 1}/$maxRetries")
                        waitWithBackoff(attempt)
                    } else {
                        Log.e(TAG, "Non-retryable or max retries reached: ${e.message}")
                        throw e
                    }
                }
            }

            // If we've exhausted retries, throw the last exception
            throw lastException ?: IOException("Request failed after $maxRetries retries")
        }

        private fun waitWithBackoff(attempt: Int) {
            val backoffMs = (initialBackoffMs * Math.pow(backoffMultiplier, attempt.toDouble()))
                .toLong()
                .coerceAtMost(maxBackoffMs)

            Log.d(TAG, "Waiting ${backoffMs}ms before retry")

            try {
                Thread.sleep(backoffMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Retry interrupted", e)
            }
        }
    }

    /**
     * Request body for the transcription API
     */
    private data class TranscribeRequest(
        @SerializedName("audioBase64")
        val audioBase64: String,
        @SerializedName("audioFormat")
        val audioFormat: String = "m4a",  // Default to m4a for backwards compatibility
        @SerializedName("model")
        val model: String? = null,  // Optional model parameter
        @SerializedName("audioDurationMs")
        val audioDurationMs: Long? = null  // Duration in milliseconds for usage tracking
    )

    /**
     * Response body from the transcription API
     */
    private data class TranscribeResponse(
        @SerializedName("text")
        val text: String?,
        @SerializedName("secondsUsed")
        val secondsUsed: Int?,
        @SerializedName("totalSecondsThisMonth")
        val totalSecondsThisMonth: Int?,
        // Iteration 2: Trial status fields
        @SerializedName("trialStatus")
        val trialStatus: TrialStatusResponse?,
        // Iteration 3: Pro status fields
        @SerializedName("proStatus")
        val proStatus: ProStatusResponse?,
        @SerializedName("plan")
        val plan: String?  // "free" or "pro"
    )
    
    /**
     * Trial status response nested object
     */
    private data class TrialStatusResponse(
        @SerializedName("status")
        val status: String?,
        @SerializedName("freeSecondsUsed")
        val freeSecondsUsed: Int?,
        @SerializedName("freeSecondsRemaining")
        val freeSecondsRemaining: Int?,
        @SerializedName("trialExpiryDateMs")
        val trialExpiryDateMs: Long?,
        @SerializedName("warningLevel")
        val warningLevel: String?,
        @SerializedName("totalSecondsThisMonth")
        val totalSecondsThisMonth: Int?
    )
    
    /**
     * Quota exceeded response (403) - handles both trial and Pro
     */
    private data class QuotaExceededResponse(
        @SerializedName("error")
        val error: String?,  // "TRIAL_EXPIRED" or "PRO_LIMIT_REACHED"
        @SerializedName("message")
        val message: String?,
        @SerializedName("plan")
        val plan: String?,  // "free" or "pro"
        @SerializedName("trialStatus")
        val trialStatus: TrialStatusResponse?,
        @SerializedName("proStatus")
        val proStatus: ProQuotaStatus?
    )

    /**
     * Pro quota status in 403 response
     */
    private data class ProQuotaStatus(
        @SerializedName("proSecondsRemaining")
        val proSecondsRemaining: Int?,
        @SerializedName("resetDateMs")
        val resetDateMs: Long?
    )

    /**
     * Error response from the API
     */
    private data class ErrorResponse(
        @SerializedName("error")
        val error: String?
    )

    /**
     * Callback interface for transcription results
     */
    interface TranscriptionCallback {
        fun onSuccess(text: String)
        fun onError(error: String)
        /**
         * Called when trial has expired (403 TRIAL_EXPIRED)
         * Override to handle trial expiry specifically
         */
        fun onTrialExpired(message: String) {
            // Default: treat as regular error
            onError(message)
        }
    }

    /**
     * Transcribe audio bytes using the WhisperType API
     * 
     * @param audioBytes Raw audio bytes (M4A, WAV, etc.)
     * @param authToken Firebase Auth ID token for authentication
     * @param audioFormat File format extension ("m4a", "wav", etc.)
     * @param model Optional Whisper model to use (e.g., "gpt-4o-transcribe", "gpt-4o-transcribe-mini")
     * @param audioDurationMs Duration of audio in milliseconds (for usage tracking)
     * @param callback Callback for success/error results
     */
    fun transcribe(
        audioBytes: ByteArray,
        authToken: String,
        audioFormat: String = "m4a",
        model: String? = null,
        audioDurationMs: Long? = null,
        callback: TranscriptionCallback
    ) {
        Log.d(TAG, "Starting transcription, audio size: ${audioBytes.size} bytes, format: $audioFormat, model: ${model ?: "default"}, duration: ${audioDurationMs}ms")

        // Encode audio to base64 (NO_WRAP to avoid line breaks)
        val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        Log.d(TAG, "Base64 encoded, length: ${audioBase64.length}")

        // Create request body with format, model, and duration hints
        val requestBody = TranscribeRequest(audioBase64, audioFormat, model, audioDurationMs)
        val jsonBody = gson.toJson(requestBody)

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $authToken")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        // Execute request asynchronously
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Response code: ${response.code}")

                when (response.code) {
                    200 -> {
                        try {
                            val transcribeResponse = gson.fromJson(responseBody, TranscribeResponse::class.java)
                            val text = transcribeResponse.text
                            
                            if (text.isNullOrBlank()) {
                                Log.w(TAG, "Empty transcription result")
                                callback.onError("No speech detected")
                            } else {
                                // Log usage info (in seconds)
                                val secondsUsed = transcribeResponse.secondsUsed ?: 0
                                val totalSecondsThisMonth = transcribeResponse.totalSecondsThisMonth ?: 0
                                Log.d(TAG, "Usage: ${secondsUsed}s used, ${totalSecondsThisMonth}s total this month")
                                
                                // Check if Pro user (Iteration 3)
                                val proStatus = transcribeResponse.proStatus
                                val isPro = transcribeResponse.plan == "pro" || proStatus != null
                                
                                if (isPro && proStatus != null) {
                                    // Pro user - update Pro status
                                    Log.d(TAG, "Pro: ${proStatus.proSecondsUsed}s used, ${proStatus.proSecondsRemaining}s remaining")
                                    UsageDataManager.updateProStatus(
                                        proSecondsUsed = proStatus.proSecondsUsed ?: 0,
                                        proSecondsRemaining = proStatus.proSecondsRemaining ?: 9000,
                                        proSecondsLimit = proStatus.proSecondsLimit ?: 9000,
                                        proResetDateMs = proStatus.currentPeriodEndMs ?: 0
                                    )
                                    // Also update legacy fields for compatibility
                                    UsageDataManager.updateUsage(secondsUsed, totalSecondsThisMonth)
                                } else {
                                    // Update trial status if present (Iteration 2)
                                    val trial = transcribeResponse.trialStatus
                                    if (trial != null) {
                                        Log.d(TAG, "Trial: ${trial.status}, ${trial.freeSecondsRemaining}s remaining")
                                        UsageDataManager.updateFull(
                                            secondsUsed = secondsUsed,
                                            totalSecondsThisMonth = totalSecondsThisMonth,
                                            status = trial.status ?: "active",
                                            freeSecondsUsed = trial.freeSecondsUsed ?: 0,
                                            freeSecondsRemaining = trial.freeSecondsRemaining ?: 1200,
                                            trialExpiryDateMs = trial.trialExpiryDateMs ?: 0,
                                            warningLevel = trial.warningLevel ?: "none"
                                        )
                                    } else {
                                        // Legacy response (pre-Iteration 2)
                                        UsageDataManager.updateUsage(secondsUsed, totalSecondsThisMonth)
                                    }
                                }
                                
                                // Log raw text for debugging
                                Log.d(TAG, "Raw transcription: '$text'")
                                
                                // Strip "message" prefix if present (artifact from Whisper API)
                                // Uses regex for robust matching of any case and formatting
                                val cleanedText = text.trim().let { t ->
                                    // Regex matches: "Message", "message", "MESSAGE" etc.
                                    // Optionally followed by ":", " ", or nothing
                                    val messagePattern = Regex("^[Mm][Ee][Ss][Ss][Aa][Gg][Ee][:\\s]*", RegexOption.IGNORE_CASE)
                                    val result = messagePattern.replaceFirst(t, "").trim()
                                    if (result != t) {
                                        Log.d(TAG, "Stripped 'Message' prefix, result: '$result'")
                                    }
                                    result.ifEmpty { t }  // Fallback to original if result is empty
                                }
                                Log.d(TAG, "Final transcription: ${cleanedText.take(50)}...")
                                callback.onSuccess(cleanedText)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse response", e)
                            callback.onError("Failed to parse transcription response")
                        }
                    }
                    // Quota exceeded - handles both trial and Pro users
                    403 -> {
                        Log.w(TAG, "Quota exceeded (403)")
                        try {
                            val errorResponse = gson.fromJson(responseBody, QuotaExceededResponse::class.java)
                            val message = errorResponse?.message ?: "You have used all your quota"
                            val isPro = errorResponse?.error == "PRO_LIMIT_REACHED" || errorResponse?.plan == "pro"

                            if (isPro) {
                                // Pro user ran out of quota - update Pro status
                                Log.d(TAG, "Pro quota exceeded")
                                val proStatus = errorResponse?.proStatus
                                UsageDataManager.updateProStatus(
                                    proSecondsUsed = 9000,  // Limit reached
                                    proSecondsRemaining = 0,
                                    proSecondsLimit = 9000,
                                    proResetDateMs = proStatus?.resetDateMs ?: (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
                                )
                            } else {
                                // Trial user - update trial status
                                val trial = errorResponse?.trialStatus
                                if (trial != null) {
                                    UsageDataManager.updateTrialStatus(
                                        status = trial.status ?: "expired_usage",
                                        freeSecondsUsed = trial.freeSecondsUsed ?: 1200,
                                        freeSecondsRemaining = 0,
                                        trialExpiryDateMs = trial.trialExpiryDateMs ?: 0,
                                        warningLevel = "none"
                                    )
                                } else {
                                    UsageDataManager.markTrialExpired(
                                        UsageDataManager.TrialStatus.EXPIRED_USAGE
                                    )
                                }
                            }

                            callback.onTrialExpired(message)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse 403 response", e)
                            UsageDataManager.markTrialExpired(
                                UsageDataManager.TrialStatus.EXPIRED_USAGE
                            )
                            callback.onTrialExpired("You have used all your quota")
                        }
                    }
                    400 -> {
                        val errorResponse = try {
                            gson.fromJson(responseBody, ErrorResponse::class.java)
                        } catch (e: Exception) { null }
                        
                        val errorMessage = errorResponse?.error ?: "Invalid audio data"
                        Log.e(TAG, "Bad request: $errorMessage")
                        callback.onError(errorMessage)
                    }
                    401 -> {
                        Log.e(TAG, "Unauthorized: Authentication failed")
                        callback.onError("Authentication failed. Please restart the app.")
                    }
                    405 -> {
                        Log.e(TAG, "Method not allowed")
                        callback.onError("API configuration error")
                    }
                    500 -> {
                        val errorResponse = try {
                            gson.fromJson(responseBody, ErrorResponse::class.java)
                        } catch (e: Exception) { null }
                        
                        val errorMessage = errorResponse?.error ?: "Server error"
                        Log.e(TAG, "Server error: $errorMessage")
                        callback.onError("Transcription failed. Please try again.")
                    }
                    else -> {
                        Log.e(TAG, "Unexpected response code: ${response.code}")
                        callback.onError("Unexpected error (${response.code})")
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network request failed after retries", e)

                val errorMessage = when {
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "Request timed out. Please check your connection and try again."
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                        "No internet connection. Please check your network."
                    e.message?.contains("after $MAX_RETRIES retries", ignoreCase = true) == true ->
                        "Server temporarily unavailable. Please try again later."
                    e is SocketTimeoutException ->
                        "Connection timed out. Please try again."
                    e is UnknownHostException ->
                        "No internet connection. Please check your network."
                    else ->
                        "Network error. Please check your connection and try again."
                }

                callback.onError(errorMessage)
            }
        })
    }

    /**
     * Warm up the Firebase Function by pinging the health endpoint
     * @deprecated Use warmTranscribeFunction() instead - health endpoint is a different Cloud Run instance
     */
    @Deprecated("Use warmTranscribeFunction() instead", ReplaceWith("warmTranscribeFunction()"))
    fun warmHealth() {
        warmTranscribeFunction()
    }

    /**
     * Warm up the transcribeAudio Firebase Function
     *
     * IMPORTANT: Each Firebase Function (Gen 2) runs in a separate Cloud Run instance.
     * Warming up the 'health' function does NOT warm up 'transcribeAudio'.
     * This method directly warms up the transcribeAudio function by sending a GET request.
     *
     * Fire-and-forget: doesn't block or return result
     * Call this when recording starts so the function is warm when recording stops.
     */
    fun warmTranscribeFunction() {
        Log.d(TAG, "Warming up transcribeAudio function")

        val request = Request.Builder()
            .url(API_URL)  // Use the actual transcribeAudio endpoint
            .get()         // GET request triggers warmup mode in the function
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "TranscribeAudio warmup: ${response.code}")
                response.close()
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "TranscribeAudio warmup failed (non-critical): ${e.message}")
            }
        })
    }
    
    /**
     * Cancel all pending requests for this client
     * Note: Since we use a shared client, consider using tags if you need per-instance cancellation
     */
    fun cancelAll() {
        client.dispatcher.cancelAll()
    }
    
    /**
     * Cleanup resources if needed
     * The singleton client will be reused, but this method is here for API compatibility
     */
    fun release() {
        // No-op: Client is a singleton and should not be closed per instance
        // If you need to clean up, call cancelAll() instead
    }
    
    /**
     * Fetch trial status from the backend (Iteration 2)
     * Call this on app launch to get current trial status
     *
     * @param authToken Firebase Auth ID token for authentication
     * @param onSuccess Callback with trial status data
     * @param onError Callback for errors
     */
    fun getTrialStatus(
        authToken: String,
        onSuccess: (status: String, freeSecondsUsed: Int, freeSecondsRemaining: Int, 
                    trialExpiryDateMs: Long, warningLevel: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        Log.d(TAG, "Fetching trial status")
        
        val request = Request.Builder()
            .url(TRIAL_STATUS_URL)
            .addHeader("Authorization", "Bearer $authToken")
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Trial status response code: ${response.code}")
                
                when (response.code) {
                    200 -> {
                        try {
                            val trialResponse = gson.fromJson(responseBody, TrialStatusResponse::class.java)
                            
                            // Update UsageDataManager with full data including monthly usage
                            UsageDataManager.updateFull(
                                secondsUsed = 0, // No new seconds used, just fetching status
                                totalSecondsThisMonth = trialResponse.totalSecondsThisMonth ?: 0,
                                status = trialResponse.status ?: "active",
                                freeSecondsUsed = trialResponse.freeSecondsUsed ?: 0,
                                freeSecondsRemaining = trialResponse.freeSecondsRemaining ?: 1200,
                                trialExpiryDateMs = trialResponse.trialExpiryDateMs ?: 0,
                                warningLevel = trialResponse.warningLevel ?: "none"
                            )
                            
                            Log.d(TAG, "Trial: ${trialResponse.status}, ${trialResponse.freeSecondsRemaining}s remaining, ${trialResponse.totalSecondsThisMonth}s this month")
                            
                            onSuccess(
                                trialResponse.status ?: "active",
                                trialResponse.freeSecondsUsed ?: 0,
                                trialResponse.freeSecondsRemaining ?: 1200,
                                trialResponse.trialExpiryDateMs ?: 0,
                                trialResponse.warningLevel ?: "none"
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse trial status response", e)
                            onError("Failed to parse trial status")
                        }
                    }
                    401 -> {
                        Log.e(TAG, "Unauthorized: Authentication failed")
                        onError("Authentication failed")
                    }
                    else -> {
                        Log.e(TAG, "Unexpected response: ${response.code}")
                        onError("Failed to get trial status")
                    }
                }
            }
            
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Trial status request failed after retries", e)
                val errorMessage = when (e) {
                    is SocketTimeoutException -> "Connection timed out"
                    is UnknownHostException -> "No internet connection"
                    else -> "Network error. Please try again."
                }
                onError(errorMessage)
            }
        })
    }

    /**
     * Verify subscription purchase with backend (Iteration 3)
     * Sends purchase token to backend for Google Play verification
     *
     * @param authToken Firebase Auth ID token for authentication
     * @param purchaseToken The purchase token from Google Play
     * @param productId The subscription product ID
     * @param onSuccess Callback with Pro status data
     * @param onError Callback for errors
     */
    fun verifySubscription(
        authToken: String,
        purchaseToken: String,
        productId: String,
        onSuccess: (proSecondsRemaining: Int, proSecondsLimit: Int, resetDateMs: Long) -> Unit,
        onError: (error: String) -> Unit
    ) {
        Log.d(TAG, "Verifying subscription: $productId")
        
        val requestBody = mapOf(
            "purchaseToken" to purchaseToken,
            "productId" to productId
        )
        val jsonBody = gson.toJson(requestBody)
        
        val request = Request.Builder()
            .url("https://us-central1-whispertype-1de9f.cloudfunctions.net/verifySubscription")
            .addHeader("Authorization", "Bearer $authToken")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Verify subscription response: ${response.code}")
                
                when (response.code) {
                    200 -> {
                        try {
                            val result = gson.fromJson(responseBody, VerifySubscriptionResponse::class.java)
                            
                            if (result.success == true && result.proStatus != null) {
                                Log.d(TAG, "Subscription verified: ${result.proStatus.proSecondsRemaining}s remaining")
                                
                                // Update UsageDataManager with Pro status
                                UsageDataManager.updateProStatus(
                                    proSecondsUsed = result.proStatus.proSecondsUsed ?: 0,
                                    proSecondsRemaining = result.proStatus.proSecondsRemaining ?: 9000,
                                    proSecondsLimit = result.proStatus.proSecondsLimit ?: 9000,
                                    proResetDateMs = result.proStatus.currentPeriodEndMs ?: 0
                                )
                                
                                onSuccess(
                                    result.proStatus.proSecondsRemaining ?: 9000,
                                    result.proStatus.proSecondsLimit ?: 9000,
                                    result.proStatus.currentPeriodEndMs ?: 0
                                )
                            } else {
                                Log.e(TAG, "Verification failed: success=${result.success}")
                                onError("Subscription verification failed")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse verification response", e)
                            onError("Failed to verify subscription")
                        }
                    }
                    400 -> {
                        Log.e(TAG, "Invalid purchase: $responseBody")
                        onError("Invalid purchase")
                    }
                    401 -> {
                        Log.e(TAG, "Unauthorized")
                        onError("Authentication failed")
                    }
                    else -> {
                        Log.e(TAG, "Unexpected response: ${response.code}")
                        onError("Verification failed")
                    }
                }
            }
            
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Subscription verification request failed after retries", e)
                val errorMessage = when (e) {
                    is SocketTimeoutException -> "Connection timed out"
                    is UnknownHostException -> "No internet connection"
                    else -> "Network error. Please try again."
                }
                onError(errorMessage)
            }
        })
    }

    /**
     * Response for subscription verification
     */
    private data class VerifySubscriptionResponse(
        @SerializedName("success")
        val success: Boolean?,
        @SerializedName("plan")
        val plan: String?,
        @SerializedName("proStatus")
        val proStatus: ProStatusResponse?
    )
    
    /**
     * Pro status nested object
     */
    private data class ProStatusResponse(
        @SerializedName("isActive")
        val isActive: Boolean?,
        @SerializedName("proSecondsUsed")
        val proSecondsUsed: Int?,
        @SerializedName("proSecondsRemaining")
        val proSecondsRemaining: Int?,
        @SerializedName("proSecondsLimit")
        val proSecondsLimit: Int?,
        @SerializedName("currentPeriodEndMs")
        val currentPeriodEndMs: Long?
    )
}
