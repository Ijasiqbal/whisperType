package com.whispertype.app.speech

import android.content.Context
import android.util.Log

/**
 * Enum representing different transcription flows available in the app
 * 
 * These flows can be switched at runtime by the user (debug mode only).
 * In the future, each flow may have different backends, models, or processing pipelines.
 */
enum class TranscriptionFlow(
    val displayName: String,
    val description: String
) {
    /**
     * Cloud API flow - current default implementation
     * Audio is recorded → silence trimmed → sent to WhisperType backend → OpenAI Whisper API
     */
    CLOUD_API(
        displayName = "Cloud API (Default)",
        description = "Uses WhisperType backend with OpenAI Whisper"
    ),
    
    /**
     * Groq Whisper flow - uses Groq's ultra-fast whisper-large-v3 model
     * Audio is recorded → sent directly to Groq API (no silence trimming)
     */
    GROQ_WHISPER(
        displayName = "Groq Whisper (Fast)",
        description = "Ultra-fast transcription via Groq"
    ),
    
    /**
     * Flow 3 - Groq Whisper Turbo (whisper-large-v3-turbo)
     * Faster variant of Groq Whisper with slightly lower accuracy
     */
    FLOW_3(
        displayName = "Groq Turbo (Fastest)",
        description = "Ultra-fast transcription via Groq Turbo"
    ),

    /**
     * Flow 4 - OpenAI GPT-4o-mini-transcribe without silence trimming
     * Uses OpenAI's latest transcription model without pre-processing
     */
    FLOW_4(
        displayName = "OpenAI Mini (No Trim)",
        description = "GPT-4o-mini-transcribe without silence trimming"
    );
    
    companion object {
        private const val TAG = "TranscriptionFlow"
        private const val PREFS_NAME = "transcription_flow_prefs"
        private const val KEY_SELECTED_FLOW = "selected_flow"
        
        /**
         * Get the currently selected transcription flow
         */
        fun getSelectedFlow(context: Context): TranscriptionFlow {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val flowName = prefs.getString(KEY_SELECTED_FLOW, CLOUD_API.name) ?: CLOUD_API.name
            return try {
                valueOf(flowName)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid flow name: $flowName, defaulting to CLOUD_API")
                CLOUD_API
            }
        }
        
        /**
         * Set the selected transcription flow
         */
        fun setSelectedFlow(context: Context, flow: TranscriptionFlow) {
            Log.d(TAG, "Setting transcription flow to: ${flow.name}")
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_SELECTED_FLOW, flow.name).apply()
        }
    }
}
