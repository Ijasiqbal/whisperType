package com.whispertype.app

import android.content.Context
import android.content.SharedPreferences

/**
 * ShortcutPreferences - Manages user preferences for shortcut activation
 * 
 * Stores and retrieves the user's preferred volume button shortcut mode.
 */
object ShortcutPreferences {
    private const val KEY_SHORTCUT_MODE = "shortcut_mode"
    private const val KEY_WHISPER_MODEL = "whisper_model"
    
    /**
     * Available shortcut modes
     */
    enum class ShortcutMode(val displayName: String) {
        DOUBLE_VOLUME_UP("Double Volume Up"),
        DOUBLE_VOLUME_DOWN("Double Volume Down"),
        BOTH_VOLUME_BUTTONS("Both Buttons Together")
    }
    
    /**
     * Available Whisper transcription models
     */
    enum class WhisperModel(val displayName: String, val modelId: String) {
        GPT4O_TRANSCRIBE("GPT-4o Transcribe (Standard)", "gpt-4o-transcribe"),
        GPT4O_TRANSCRIBE_MINI("GPT-4o Transcribe Mini (Fast)", "gpt-4o-mini-transcribe")
    }
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Get the current shortcut mode
     */
    fun getShortcutMode(context: Context): ShortcutMode {
        val modeName = getPrefs(context).getString(KEY_SHORTCUT_MODE, ShortcutMode.DOUBLE_VOLUME_UP.name)
        return try {
            ShortcutMode.valueOf(modeName ?: ShortcutMode.DOUBLE_VOLUME_UP.name)
        } catch (e: IllegalArgumentException) {
            ShortcutMode.DOUBLE_VOLUME_UP
        }
    }
    
    /**
     * Set the shortcut mode
     */
    fun setShortcutMode(context: Context, mode: ShortcutMode) {
        getPrefs(context).edit().putString(KEY_SHORTCUT_MODE, mode.name).apply()
    }
    
    /**
     * Get the current Whisper model
     */
    fun getWhisperModel(context: Context): WhisperModel {
        val modelName = getPrefs(context).getString(KEY_WHISPER_MODEL, WhisperModel.GPT4O_TRANSCRIBE.name)
        return try {
            WhisperModel.valueOf(modelName ?: WhisperModel.GPT4O_TRANSCRIBE.name)
        } catch (e: IllegalArgumentException) {
            WhisperModel.GPT4O_TRANSCRIBE
        }
    }
    
    /**
     * Set the Whisper model
     */
    fun setWhisperModel(context: Context, model: WhisperModel) {
        getPrefs(context).edit().putString(KEY_WHISPER_MODEL, model.name).apply()
    }
}
