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
        QUALITY_TRANSCRIBE("Quality Transcribe", "standard"),
        PREMIUM_TRANSCRIBE("Premium Transcribe", "premium")
    }

    /**
     * Model tiers for transcription quality selection
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
        val modeName = getPrefs(context).getString(KEY_SHORTCUT_MODE, ShortcutMode.BOTH_VOLUME_BUTTONS.name)
        return try {
            ShortcutMode.valueOf(modeName ?: ShortcutMode.BOTH_VOLUME_BUTTONS.name)
        } catch (e: IllegalArgumentException) {
            ShortcutMode.BOTH_VOLUME_BUTTONS
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
        val modelName = getPrefs(context).getString(KEY_WHISPER_MODEL, WhisperModel.PREMIUM_TRANSCRIBE.name)
        return try {
            WhisperModel.valueOf(modelName ?: WhisperModel.PREMIUM_TRANSCRIBE.name)
        } catch (e: IllegalArgumentException) {
            WhisperModel.PREMIUM_TRANSCRIBE
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
     * Default is PREMIUM for best quality
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
