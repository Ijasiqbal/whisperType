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
    private const val KEY_AUTO_SEND_ENABLED = "auto_send_enabled"
    private const val KEY_AUTO_SEND_WARNING_SHOWN = "auto_send_warning_shown"
    private const val KEY_MODEL_TIER = "model_tier"
    
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

    /**
     * Model tiers for transcription quality selection
     * - AUTO: Free tier using Groq Turbo (whisper-large-v3-turbo)
     * - STANDARD: 1x credit using Groq Whisper (whisper-large-v3)
     * - PREMIUM: 2x credit using OpenAI (gpt-4o-mini-transcribe)
     */
    enum class ModelTier(
        val displayName: String,
        val creditCost: String,
        val description: String
    ) {
        AUTO("Auto", "Free", "Fast & unlimited"),
        STANDARD("Standard", "1x", "High accuracy"),
        PREMIUM("Premium", "2x", "Best quality")
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
        val modelName = getPrefs(context).getString(KEY_WHISPER_MODEL, WhisperModel.GPT4O_TRANSCRIBE_MINI.name)
        return try {
            WhisperModel.valueOf(modelName ?: WhisperModel.GPT4O_TRANSCRIBE_MINI.name)
        } catch (e: IllegalArgumentException) {
            WhisperModel.GPT4O_TRANSCRIBE_MINI
        }
    }
    
    /**
     * Set the Whisper model
     */
    fun setWhisperModel(context: Context, model: WhisperModel) {
        getPrefs(context).edit().putString(KEY_WHISPER_MODEL, model.name).apply()
    }

    /**
     * Check if auto-send is enabled
     */
    fun isAutoSendEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_SEND_ENABLED, false)
    }

    /**
     * Enable or disable auto-send
     */
    fun setAutoSendEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_SEND_ENABLED, enabled).apply()
    }

    /**
     * Check if auto-send warning has been shown to the user
     */
    fun hasShownAutoSendWarning(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_SEND_WARNING_SHOWN, false)
    }

    /**
     * Mark that auto-send warning has been shown
     */
    fun setAutoSendWarningShown(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_SEND_WARNING_SHOWN, true).apply()
    }

    /**
     * Get the current model tier
     * Default is PREMIUM (OpenAI) for best quality
     */
    fun getModelTier(context: Context): ModelTier {
        val tierName = getPrefs(context).getString(KEY_MODEL_TIER, ModelTier.PREMIUM.name)
        return try {
            ModelTier.valueOf(tierName ?: ModelTier.PREMIUM.name)
        } catch (e: IllegalArgumentException) {
            ModelTier.PREMIUM
        }
    }

    /**
     * Set the model tier
     */
    fun setModelTier(context: Context, tier: ModelTier) {
        getPrefs(context).edit().putString(KEY_MODEL_TIER, tier.name).apply()
    }
}
