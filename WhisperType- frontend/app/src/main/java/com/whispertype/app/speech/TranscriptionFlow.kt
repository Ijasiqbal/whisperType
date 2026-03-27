package com.whispertype.app.speech

import android.content.Context
import android.util.Log
import com.whispertype.app.ShortcutPreferences

/**
 * Enum representing different transcription flows available in the app
 *
 * These flows can be switched at runtime by the user (debug mode only).
 */
enum class TranscriptionFlow(
    val displayName: String,
    val description: String
) {
    /**
     * Flow 3 - Fastest transcription variant
     */
    FLOW_3(
        displayName = "Turbo (Fastest)",
        description = "Ultra-fast turbo transcription"
    ),

    /**
     * Flow 4 - Premium transcription without silence trimming
     */
    FLOW_4(
        displayName = "Premium (No Trim)",
        description = "Premium transcription without silence trimming"
    ),

    /**
     * PARALLEL_OPUS - Parallel RMS + Opus encoding + premium transcription
     * Uses AudioRecord with real-time RMS silence detection AND parallel Opus encoding
     * Both RMS analysis and Opus encoding run in parallel during recording
     * Output: OGG format (compressed)
     * Requirements: Android 10+ (API 29+)
     */
    PARALLEL_OPUS(
        displayName = "Parallel Encoding [OGG] + Premium",
        description = "Parallel RMS + Opus encoding, compressed OGG"
    ),

    /**
     * AUTO_ENHANCED - Enhanced auto transcription with post-processing cleanup
     */
    AUTO_ENHANCED(
        displayName = "Enhanced Auto",
        description = "Enhanced auto transcription with cleanup"
    );

    companion object {
        private const val TAG = "TranscriptionFlow"
        private const val PREFS_NAME = "transcription_flow_prefs"
        private const val KEY_SELECTED_FLOW = "selected_flow"

        /**
         * The default transcription flow used when no preference is set.
         * Change this to switch the default flow for the entire app.
         */
        val DEFAULT_FLOW = PARALLEL_OPUS

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

        /**
         * Maps a ModelTier to the corresponding TranscriptionFlow
         */
        fun fromModelTier(tier: ShortcutPreferences.ModelTier): TranscriptionFlow {
            return when (tier) {
                ShortcutPreferences.ModelTier.AUTO -> FLOW_3
                ShortcutPreferences.ModelTier.STANDARD -> AUTO_ENHANCED
                ShortcutPreferences.ModelTier.PREMIUM -> PARALLEL_OPUS
            }
        }
    }
}
