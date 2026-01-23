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
    ),

    /**
     * ARAMUS_OPENAI - Default flow with parallel RMS analysis + GPT-4o-mini-transcribe
     * Uses AudioRecord (not MediaRecorder) with real-time RMS silence detection
     * RMS analysis runs in parallel during recording for faster processing
     * Output: WAV format
     */
    ARAMUS_OPENAI(
        displayName = "Aramus + OpenAI (Default)",
        description = "Parallel RMS + GPT-4o-mini-transcribe"
    );
    
    companion object {
        private const val TAG = "TranscriptionFlow"
        private const val PREFS_NAME = "transcription_flow_prefs"
        private const val KEY_SELECTED_FLOW = "selected_flow"

        /**
         * The default transcription flow used when no preference is set.
         * Change this to switch the default flow for the entire app.
         * Available options: GROQ_WHISPER, FLOW_3, FLOW_4, ARAMUS_OPENAI
         */
        val DEFAULT_FLOW = ARAMUS_OPENAI

        /**
         * Get the currently selected transcription flow
         */
        fun getSelectedFlow(context: Context): TranscriptionFlow {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val flowName = prefs.getString(KEY_SELECTED_FLOW, DEFAULT_FLOW.name) ?: DEFAULT_FLOW.name
            return try {
                valueOf(flowName)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid flow name: $flowName, defaulting to ${DEFAULT_FLOW.name}")
                DEFAULT_FLOW
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
