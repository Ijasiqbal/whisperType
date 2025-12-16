package com.whispertype.app.api

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

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
 */
class WhisperApiClient {

    companion object {
        private const val TAG = "WhisperApiClient"
        private const val API_URL = "https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudio"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)  // Transcription can take time
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Request body for the transcription API
     */
    private data class TranscribeRequest(
        @SerializedName("audioBase64")
        val audioBase64: String,
        @SerializedName("audioFormat")
        val audioFormat: String = "m4a",  // Default to m4a for backwards compatibility
        @SerializedName("model")
        val model: String? = null  // Optional model parameter
    )

    /**
     * Response body from the transcription API
     */
    private data class TranscribeResponse(
        @SerializedName("text")
        val text: String?
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
    }

    /**
     * Transcribe audio bytes using the WhisperType API
     * 
     * @param audioBytes Raw audio bytes (M4A, WAV, etc.)
     * @param authToken Firebase Auth ID token for authentication
     * @param audioFormat File format extension ("m4a", "wav", etc.)
     * @param model Optional Whisper model to use (e.g., "gpt-4o-transcribe", "gpt-4o-transcribe-mini")
     * @param callback Callback for success/error results
     */
    fun transcribe(
        audioBytes: ByteArray,
        authToken: String,
        audioFormat: String = "m4a",
        model: String? = null,
        callback: TranscriptionCallback
    ) {
        Log.d(TAG, "Starting transcription, audio size: ${audioBytes.size} bytes, format: $audioFormat, model: ${model ?: "default"}")

        // Encode audio to base64 (NO_WRAP to avoid line breaks)
        val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        Log.d(TAG, "Base64 encoded, length: ${audioBase64.length}")

        // Create request body with format and model hints
        val requestBody = TranscribeRequest(audioBase64, audioFormat, model)
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
                Log.e(TAG, "Network request failed", e)
                
                val errorMessage = when {
                    e.message?.contains("timeout", ignoreCase = true) == true -> 
                        "Request timed out. Please try again."
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> 
                        "No internet connection"
                    else -> 
                        "Network error. Please check your connection."
                }
                
                callback.onError(errorMessage)
            }
        })
    }

    /**
     * Cancel all pending requests
     */
    fun cancelAll() {
        client.dispatcher.cancelAll()
    }
}
