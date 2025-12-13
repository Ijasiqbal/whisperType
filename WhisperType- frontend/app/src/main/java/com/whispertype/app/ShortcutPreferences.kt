package com.whispertype.app

import android.content.Context
import android.content.SharedPreferences

/**
 * ShortcutPreferences - Manages user preferences for shortcut activation
 * 
 * Stores and retrieves the user's preferred volume button shortcut mode.
 */
object ShortcutPreferences {
    private const val PREFS_NAME = "whispertype_prefs"
    private const val KEY_SHORTCUT_MODE = "shortcut_mode"
    
    /**
     * Available shortcut modes
     */
    enum class ShortcutMode(val displayName: String) {
        DOUBLE_VOLUME_UP("Double Volume Up"),
        DOUBLE_VOLUME_DOWN("Double Volume Down"),
        BOTH_VOLUME_BUTTONS("Both Buttons Together")
    }
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
}
