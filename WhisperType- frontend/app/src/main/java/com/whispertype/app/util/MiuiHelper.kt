package com.whispertype.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * MiuiHelper - Utility for detecting and handling MIUI-specific requirements
 * 
 * MIUI (Xiaomi/Redmi/POCO) has aggressive battery optimization that kills services
 * even with foreground notifications. Users must enable AutoStart permission.
 * 
 * This helper:
 * 1. Detects if device is running MIUI
 * 2. Provides deep links to MIUI-specific settings (AutoStart, Battery)
 * 3. Checks if AutoStart setup has been prompted
 */
object MiuiHelper {
    private const val TAG = "MiuiHelper"
    private const val PREFS_KEY_MIUI_SETUP_SHOWN = "miui_autostart_setup_shown"
    private const val PREFS_KEY_MIUI_SETUP_DISMISSED = "miui_autostart_setup_dismissed"

    /**
     * Check if the device is running MIUI (Xiaomi/Redmi/POCO)
     */
    fun isMiuiDevice(): Boolean {
        return getMiuiVersion() != null || isXiaomiManufacturer()
    }

    /**
     * Check if manufacturer is Xiaomi, Redmi, or POCO
     */
    private fun isXiaomiManufacturer(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("xiaomi") ||
               manufacturer.contains("redmi") ||
               manufacturer.contains("poco")
    }

    /**
     * Get MIUI version string (e.g., "V14", "V13")
     * Returns null if not MIUI
     */
    fun getMiuiVersion(): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val version = method.invoke(null, "ro.miui.ui.version.name") as? String
            if (version.isNullOrEmpty()) null else version
        } catch (e: Exception) {
            Log.d(TAG, "Not MIUI device or unable to get MIUI version")
            null
        }
    }

    /**
     * Get device info string for display
     */
    fun getDeviceInfo(): String {
        val miuiVersion = getMiuiVersion()
        return if (miuiVersion != null) {
            "${Build.MANUFACTURER} ${Build.MODEL} (MIUI $miuiVersion)"
        } else {
            "${Build.MANUFACTURER} ${Build.MODEL}"
        }
    }

    /**
     * Check if MIUI setup prompt has been shown before
     */
    fun hasShownSetupPrompt(context: Context): Boolean {
        val prefs = context.getSharedPreferences("whispertype_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREFS_KEY_MIUI_SETUP_SHOWN, false)
    }

    /**
     * Mark MIUI setup prompt as shown
     */
    fun markSetupPromptShown(context: Context) {
        val prefs = context.getSharedPreferences("whispertype_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREFS_KEY_MIUI_SETUP_SHOWN, true).apply()
    }

    /**
     * Check if user dismissed the MIUI setup prompt
     */
    fun hasUserDismissedSetup(context: Context): Boolean {
        val prefs = context.getSharedPreferences("whispertype_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREFS_KEY_MIUI_SETUP_DISMISSED, false)
    }

    /**
     * Mark that user dismissed the MIUI setup prompt
     */
    fun markSetupDismissed(context: Context) {
        val prefs = context.getSharedPreferences("whispertype_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREFS_KEY_MIUI_SETUP_DISMISSED, true).apply()
    }

    /**
     * Reset the dismissed state (for testing or if user wants to see prompt again)
     */
    fun resetSetupDismissed(context: Context) {
        val prefs = context.getSharedPreferences("whispertype_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREFS_KEY_MIUI_SETUP_DISMISSED, false).apply()
    }

    /**
     * Check if we should show the MIUI setup prompt
     */
    fun shouldShowSetupPrompt(context: Context): Boolean {
        return isMiuiDevice() && !hasUserDismissedSetup(context)
    }

    /**
     * Open MIUI AutoStart settings
     * Tries multiple intents as MIUI versions have different paths
     */
    fun openAutoStartSettings(context: Context): Boolean {
        val intents = listOf(
            // MIUI 10+ AutoStart manager
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            },
            // Alternative path for some MIUI versions
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
            },
            // Security app main screen
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.securitycenter.MainActivity"
                )
            },
            // MIUI 12+ alternative
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", context.packageName)
            },
            // Fallback to app info
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Opened AutoStart settings with: ${intent.component ?: intent.action}")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Failed to open with intent: ${e.message}")
                continue
            }
        }

        Log.e(TAG, "Could not open any AutoStart settings screen")
        return false
    }

    /**
     * Open MIUI Battery Saver settings for this app
     */
    fun openBatterySettings(context: Context): Boolean {
        val intents = listOf(
            // MIUI Battery saver app settings
            Intent().apply {
                component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("package_label", "WhisperType")
            },
            // Alternative battery settings
            Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").apply {
                putExtra("package_name", context.packageName)
            },
            // App-specific battery settings (Android standard)
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Opened Battery settings with: ${intent.component ?: intent.action}")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Failed to open battery settings: ${e.message}")
                continue
            }
        }

        return false
    }

    /**
     * Open MIUI "Lock app in recents" tutorial
     * This can't be done programmatically - user must long-press in recents
     */
    fun getLockAppInstructions(): String {
        return "1. Open Recent Apps (swipe up from bottom)\n" +
               "2. Find WhisperType card\n" +
               "3. Long press on the card\n" +
               "4. Tap the Lock icon (🔒)"
    }
}
