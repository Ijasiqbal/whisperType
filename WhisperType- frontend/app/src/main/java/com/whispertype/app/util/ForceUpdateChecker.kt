package com.whispertype.app.util

import android.util.Log
import com.whispertype.app.config.RemoteConfigManager

/**
 * ForceUpdateChecker - Utility to check if app version requires update
 * 
 * Checks two conditions:
 * 1. Version is below minimum required version
 * 2. Version is in the blocked versions list
 * 
 * Supports both force update (blocking) and soft update (dismissible)
 */
object ForceUpdateChecker {
    private const val TAG = "ForceUpdateChecker"
    
    /**
     * Update status for the current app version
     */
    enum class UpdateStatus {
        UP_TO_DATE,      // No update needed, app is on acceptable version
        SOFT_UPDATE,     // Optional update available (user can dismiss)
        FORCE_UPDATE     // Mandatory update required (blocks app usage)
    }
    
    /**
     * Check if current app version requires update
     * 
     * @param currentVersionCode Current app version code (e.g., BuildConfig.VERSION_CODE)
     * @param config Update configuration from Remote Config
     * @return UpdateStatus indicating if update is required
     */
    fun checkUpdateStatus(
        currentVersionCode: Int,
        config: RemoteConfigManager.UpdateConfig
    ): UpdateStatus {
        
        Log.d(TAG, "Checking update status for version code: $currentVersionCode")
        Log.d(TAG, "Config: forceEnabled=${config.forceUpdateEnabled}, minVersion=${config.forceUpdateMinVersion}, blocked=${config.forceUpdateBlockedVersions}")
        
        // Check FORCE update first (blocking) - highest priority
        if (config.forceUpdateEnabled) {
            // Check if version is at or below minimum required (inclusive)
            if (currentVersionCode <= config.forceUpdateMinVersion) {
                Log.w(TAG, "Version $currentVersionCode is at or below minimum ${config.forceUpdateMinVersion} - FORCE UPDATE required")
                return UpdateStatus.FORCE_UPDATE
            }
            
            // Check if version is in the blocked list
            if (currentVersionCode in config.forceUpdateBlockedVersions) {
                Log.w(TAG, "Version $currentVersionCode is in blocked list ${config.forceUpdateBlockedVersions} - FORCE UPDATE required")
                return UpdateStatus.FORCE_UPDATE
            }
        }
        
        // Check SOFT update (dismissible) - lower priority
        if (config.softUpdateEnabled) {
            // Check if version is at or below soft update minimum (inclusive)
            if (currentVersionCode <= config.softUpdateMinVersion) {
                Log.i(TAG, "Version $currentVersionCode is at or below soft minimum ${config.softUpdateMinVersion} - SOFT UPDATE suggested")
                return UpdateStatus.SOFT_UPDATE
            }
            
            // Check if version is in the soft blocked list
            if (currentVersionCode in config.softUpdateBlockedVersions) {
                Log.i(TAG, "Version $currentVersionCode is in soft blocked list ${config.softUpdateBlockedVersions} - SOFT UPDATE suggested")
                return UpdateStatus.SOFT_UPDATE
            }
        }
        
        // Version is acceptable
        Log.d(TAG, "Version $currentVersionCode is up to date")
        return UpdateStatus.UP_TO_DATE
    }
}
