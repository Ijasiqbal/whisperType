package com.whispertype.app.config

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RemoteConfigManager - Singleton for fetching Firebase Remote Config values
 * 
 * Fetches plan configuration:
 * - pro_price_display: Display price (e.g., "₹79/month")
 * - pro_plan_name: Plan name (e.g., "WhisperType Pro")
 * - pro_minutes_limit: Monthly minutes limit (e.g., 150)
 */
object RemoteConfigManager {
    private const val TAG = "RemoteConfigManager"
    
    // Remote Config keys (must match Firebase Console)
    private const val KEY_PRO_PRICE_DISPLAY = "pro_price_display"
    private const val KEY_PRO_PLAN_NAME = "pro_plan_name"
    private const val KEY_PRO_MINUTES_LIMIT = "pro_minutes_limit"
    
    // Default values (fallback if Remote Config fails)
    private const val DEFAULT_PRO_PRICE_DISPLAY = "₹79/month"
    private const val DEFAULT_PRO_PLAN_NAME = "WhisperType Pro"
    private const val DEFAULT_PRO_MINUTES_LIMIT = 150
    
    // Minimum fetch interval (1 hour for production, 0 for debug)
    private const val FETCH_INTERVAL_SECONDS = 3600L
    
    /**
     * Data class holding all Remote Config values
     */
    data class PlanConfig(
        val proPriceDisplay: String = DEFAULT_PRO_PRICE_DISPLAY,
        val proPlanName: String = DEFAULT_PRO_PLAN_NAME,
        val proMinutesLimit: Int = DEFAULT_PRO_MINUTES_LIMIT
    )
    
    private val _planConfig = MutableStateFlow(PlanConfig())
    val planConfig: StateFlow<PlanConfig> = _planConfig.asStateFlow()
    
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
            KEY_PRO_MINUTES_LIMIT to DEFAULT_PRO_MINUTES_LIMIT
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
    }
}
