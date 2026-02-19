package com.whispertype.app.config

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.whispertype.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RemoteConfigManager - Singleton for fetching Firebase Remote Config values
 * 
 * Fetches plan configuration:
 * - pro_price_display: Display price (e.g., "₹79/month")
 * - pro_plan_name: Plan name (e.g., "Wozcribe Pro")
 * - pro_minutes_limit: Monthly minutes limit (e.g., 150)
 */
object RemoteConfigManager {
    private const val TAG = "RemoteConfigManager"
    
    // Remote Config keys (must match Firebase Console)
    private const val KEY_PRO_PRICE_DISPLAY = "pro_price_display"
    private const val KEY_PRO_PLAN_NAME = "pro_plan_name"
    private const val KEY_PRO_MINUTES_LIMIT = "pro_minutes_limit"
    
    // Force update keys
    private const val KEY_FORCE_UPDATE_ENABLED = "force_update_enabled"
    private const val KEY_FORCE_UPDATE_MIN_VERSION = "force_update_min_version_code"
    private const val KEY_FORCE_UPDATE_BLOCKED_VERSIONS = "force_update_blocked_versions"
    private const val KEY_FORCE_UPDATE_TITLE = "force_update_title"
    private const val KEY_FORCE_UPDATE_MESSAGE = "force_update_message"
    
    // Soft update keys
    private const val KEY_SOFT_UPDATE_ENABLED = "soft_update_enabled"
    private const val KEY_SOFT_UPDATE_MIN_VERSION = "soft_update_min_version_code"
    private const val KEY_SOFT_UPDATE_BLOCKED_VERSIONS = "soft_update_blocked_versions"
    
    // Guide video URL key
    private const val KEY_GUIDE_VIDEO_URL = "guide_video_url"

    // Guest login key
    private const val KEY_GUEST_LOGIN_ENABLED = "guest_login_enabled"

    // Default values (fallback if Remote Config fails)
    private const val DEFAULT_PRO_PRICE_DISPLAY = "₹79/month"
    private const val DEFAULT_PRO_PLAN_NAME = "Wozcribe Pro"
    private const val DEFAULT_PRO_MINUTES_LIMIT = 150
    
    // Force update defaults
    private const val DEFAULT_FORCE_UPDATE_ENABLED = false
    private const val DEFAULT_FORCE_UPDATE_MIN_VERSION = 1
    private const val DEFAULT_FORCE_UPDATE_BLOCKED_VERSIONS = ""
    private const val DEFAULT_FORCE_UPDATE_TITLE = "Update Required"
    private const val DEFAULT_FORCE_UPDATE_MESSAGE = "A critical security update is available. Please update to continue using Wozcribe."
    
    // Soft update defaults
    private const val DEFAULT_SOFT_UPDATE_ENABLED = false
    private const val DEFAULT_SOFT_UPDATE_MIN_VERSION = 1
    private const val DEFAULT_SOFT_UPDATE_BLOCKED_VERSIONS = ""
    
    // Guide video default (empty = button hidden)
    private const val DEFAULT_GUIDE_VIDEO_URL = ""

    // Guest login default (false = disabled, only Google sign-in allowed)
    private const val DEFAULT_GUEST_LOGIN_ENABLED = false

    // Minimum fetch interval (5 minutes for production, 0 for debug/testing)
    private val FETCH_INTERVAL_SECONDS: Long
        get() = if (BuildConfig.DEBUG) 0L else 300L
    
    /**
     * Data class holding all Remote Config values
     */
    data class PlanConfig(
        val proPriceDisplay: String = DEFAULT_PRO_PRICE_DISPLAY,
        val proPlanName: String = DEFAULT_PRO_PLAN_NAME,
        val proMinutesLimit: Int = DEFAULT_PRO_MINUTES_LIMIT
    )
    
    /**
     * Data class holding update/version control config
     */
    data class UpdateConfig(
        // Force update (blocking)
        val forceUpdateEnabled: Boolean = DEFAULT_FORCE_UPDATE_ENABLED,
        val forceUpdateMinVersion: Int = DEFAULT_FORCE_UPDATE_MIN_VERSION,
        val forceUpdateBlockedVersions: List<Int> = emptyList(),
        val forceUpdateTitle: String = DEFAULT_FORCE_UPDATE_TITLE,
        val forceUpdateMessage: String = DEFAULT_FORCE_UPDATE_MESSAGE,
        
        // Soft update (dismissible)
        val softUpdateEnabled: Boolean = DEFAULT_SOFT_UPDATE_ENABLED,
        val softUpdateMinVersion: Int = DEFAULT_SOFT_UPDATE_MIN_VERSION,
        val softUpdateBlockedVersions: List<Int> = emptyList()
    )
    
    private val _planConfig = MutableStateFlow(PlanConfig())
    val planConfig: StateFlow<PlanConfig> = _planConfig.asStateFlow()
    
    private val _updateConfig = MutableStateFlow(UpdateConfig())
    val updateConfig: StateFlow<UpdateConfig> = _updateConfig.asStateFlow()
    
    // Guide video URL - button shown only when non-empty
    private val _guideVideoUrl = MutableStateFlow(DEFAULT_GUIDE_VIDEO_URL)
    val guideVideoUrl: StateFlow<String> = _guideVideoUrl.asStateFlow()

    // Guest login enabled - controls whether guest/anonymous login is allowed
    private val _guestLoginEnabled = MutableStateFlow(DEFAULT_GUEST_LOGIN_ENABLED)
    val guestLoginEnabled: StateFlow<Boolean> = _guestLoginEnabled.asStateFlow()

    // Loading state - true until first fetch completes
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private var isInitialized = false
    
    /**
     * Initialize and fetch Remote Config values
     * Call this once when the app starts (e.g., in MainActivity)
     */
    fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "Already initialized, skipping")
            // Data is already loaded, ensure loading state is false
            _isLoading.value = false
            return
        }
        
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        
        // Configure settings
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(FETCH_INTERVAL_SECONDS)
            .build()
        
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        // Set default values
        val defaults = mapOf(
            KEY_PRO_PRICE_DISPLAY to DEFAULT_PRO_PRICE_DISPLAY,
            KEY_PRO_PLAN_NAME to DEFAULT_PRO_PLAN_NAME,
            KEY_PRO_MINUTES_LIMIT to DEFAULT_PRO_MINUTES_LIMIT,
            
            // Force update defaults
            KEY_FORCE_UPDATE_ENABLED to DEFAULT_FORCE_UPDATE_ENABLED,
            KEY_FORCE_UPDATE_MIN_VERSION to DEFAULT_FORCE_UPDATE_MIN_VERSION,
            KEY_FORCE_UPDATE_BLOCKED_VERSIONS to DEFAULT_FORCE_UPDATE_BLOCKED_VERSIONS,
            KEY_FORCE_UPDATE_TITLE to DEFAULT_FORCE_UPDATE_TITLE,
            KEY_FORCE_UPDATE_MESSAGE to DEFAULT_FORCE_UPDATE_MESSAGE,
            
            // Soft update defaults
            KEY_SOFT_UPDATE_ENABLED to DEFAULT_SOFT_UPDATE_ENABLED,
            KEY_SOFT_UPDATE_MIN_VERSION to DEFAULT_SOFT_UPDATE_MIN_VERSION,
            KEY_SOFT_UPDATE_BLOCKED_VERSIONS to DEFAULT_SOFT_UPDATE_BLOCKED_VERSIONS,
            
            // Guide video default
            KEY_GUIDE_VIDEO_URL to DEFAULT_GUIDE_VIDEO_URL,

            // Guest login default
            KEY_GUEST_LOGIN_ENABLED to DEFAULT_GUEST_LOGIN_ENABLED
        )
        remoteConfig.setDefaultsAsync(defaults)
        
        // Fetch and activate
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Remote Config fetch successful, updated: $updated")
                    updateConfigValues(remoteConfig)
                } else {
                    Log.w(TAG, "Remote Config fetch failed, using defaults")
                    // Still update from cache/defaults
                    updateConfigValues(remoteConfig)
                }
                // Mark loading as complete regardless of success/failure
                _isLoading.value = false
            }
        
        isInitialized = true
    }
    
    /**
     * Force refresh Remote Config values
     * Use sparingly - respects minimum fetch interval
     */
    fun refresh() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Remote Config refresh successful")
                    updateConfigValues(remoteConfig)
                } else {
                    Log.w(TAG, "Remote Config refresh failed")
                }
            }
    }
    
    private fun updateConfigValues(remoteConfig: FirebaseRemoteConfig) {
        val priceDisplay = remoteConfig.getString(KEY_PRO_PRICE_DISPLAY)
            .takeIf { it.isNotEmpty() } ?: DEFAULT_PRO_PRICE_DISPLAY
        
        val planName = remoteConfig.getString(KEY_PRO_PLAN_NAME)
            .takeIf { it.isNotEmpty() } ?: DEFAULT_PRO_PLAN_NAME
        
        val minutesLimit = remoteConfig.getLong(KEY_PRO_MINUTES_LIMIT).toInt()
            .takeIf { it > 0 } ?: DEFAULT_PRO_MINUTES_LIMIT
        
        _planConfig.value = PlanConfig(
            proPriceDisplay = priceDisplay,
            proPlanName = planName,
            proMinutesLimit = minutesLimit
        )
        
        Log.d(TAG, "Config updated: price=$priceDisplay, name=$planName, limit=$minutesLimit")
        
        // Update version control config
        val forceUpdateEnabled = remoteConfig.getBoolean(KEY_FORCE_UPDATE_ENABLED)
        val forceUpdateMinVersion = remoteConfig.getLong(KEY_FORCE_UPDATE_MIN_VERSION).toInt()
        val forceUpdateBlockedVersionsString = remoteConfig.getString(KEY_FORCE_UPDATE_BLOCKED_VERSIONS)
        Log.d(TAG, "Raw blocked versions string from Firebase: '$forceUpdateBlockedVersionsString'")
        val forceUpdateBlockedVersions = parseVersionList(forceUpdateBlockedVersionsString)
        Log.d(TAG, "Parsed blocked versions list: $forceUpdateBlockedVersions")
        val forceUpdateTitle = remoteConfig.getString(KEY_FORCE_UPDATE_TITLE)
            .takeIf { it.isNotEmpty() } ?: DEFAULT_FORCE_UPDATE_TITLE
        val forceUpdateMessage = remoteConfig.getString(KEY_FORCE_UPDATE_MESSAGE)
            .takeIf { it.isNotEmpty() } ?: DEFAULT_FORCE_UPDATE_MESSAGE
        
        val softUpdateEnabled = remoteConfig.getBoolean(KEY_SOFT_UPDATE_ENABLED)
        val softUpdateMinVersion = remoteConfig.getLong(KEY_SOFT_UPDATE_MIN_VERSION).toInt()
        val softUpdateBlockedVersions = parseVersionList(
            remoteConfig.getString(KEY_SOFT_UPDATE_BLOCKED_VERSIONS)
        )
        
        _updateConfig.value = UpdateConfig(
            forceUpdateEnabled = forceUpdateEnabled,
            forceUpdateMinVersion = forceUpdateMinVersion,
            forceUpdateBlockedVersions = forceUpdateBlockedVersions,
            forceUpdateTitle = forceUpdateTitle,
            forceUpdateMessage = forceUpdateMessage,
            softUpdateEnabled = softUpdateEnabled,
            softUpdateMinVersion = softUpdateMinVersion,
            softUpdateBlockedVersions = softUpdateBlockedVersions
        )
        
        Log.d(TAG, "Update config: forceEnabled=$forceUpdateEnabled, minVersion=$forceUpdateMinVersion, blocked=$forceUpdateBlockedVersions")
        
        // Update guide video URL
        val guideVideoUrl = remoteConfig.getString(KEY_GUIDE_VIDEO_URL)
        _guideVideoUrl.value = guideVideoUrl
        Log.d(TAG, "Guide video URL: $guideVideoUrl")

        // Update guest login enabled
        val guestLoginEnabled = remoteConfig.getBoolean(KEY_GUEST_LOGIN_ENABLED)
        _guestLoginEnabled.value = guestLoginEnabled
        Log.d(TAG, "Guest login enabled: $guestLoginEnabled")
    }
    
    /**
     * Extract YouTube video ID from a YouTube URL
     * Supports formats:
     * - https://www.youtube.com/watch?v=VIDEO_ID
     * - https://youtu.be/VIDEO_ID
     * - https://youtube.com/watch?v=VIDEO_ID
     */
    fun extractVideoId(url: String): String? {
        if (url.isBlank()) return null
        
        return try {
            when {
                url.contains("youtu.be/") -> {
                    url.substringAfter("youtu.be/").substringBefore("?")
                }
                url.contains("watch?v=") -> {
                    url.substringAfter("watch?v=").substringBefore("&")
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract video ID from URL: $url", e)
            null
        }
    }
    
    /**
     * Parse comma-separated version list (e.g., "6,8,10" -> [6, 8, 10])
     */
    private fun parseVersionList(versionsString: String): List<Int> {
        if (versionsString.isBlank()) return emptyList()
        
        return try {
            versionsString.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { it.toIntOrNull() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse version list: $versionsString", e)
            emptyList()
        }
    }
    
    /**
     * Check if force update is required for current app version.
     * Used by services (like OverlayService) to block functionality when update is mandatory.
     * 
     * @return true if the app should block all functionality until updated
     */
    fun isForceUpdateRequired(): Boolean {
        val config = _updateConfig.value
        val currentVersionCode = BuildConfig.VERSION_CODE
        
        if (!config.forceUpdateEnabled) return false
        
        // Check if version is at or below minimum required
        if (currentVersionCode <= config.forceUpdateMinVersion) {
            return true
        }
        
        // Check if version is in the blocked list
        if (currentVersionCode in config.forceUpdateBlockedVersions) {
            return true
        }
        
        return false
    }
}
