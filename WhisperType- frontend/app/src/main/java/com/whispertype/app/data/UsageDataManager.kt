package com.whispertype.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UsageDataManager - Singleton to store and provide usage data from API responses
 * 
 * This manager:
 * 1. Stores the latest usage data received from transcription API
 * 2. Provides a StateFlow for UI components to observe
 * 3. Persists data in memory (refreshed on each transcription)
 */
object UsageDataManager {
    
    /**
     * Data class representing usage state
     */
    data class UsageState(
        val lastSecondsUsed: Int = 0,           // Seconds used in last transcription
        val totalSecondsThisMonth: Int = 0,     // Total seconds used this month
        val lastUpdated: Long = 0               // Timestamp of last update
    )
    
    private val _usageState = MutableStateFlow(UsageState())
    val usageState: StateFlow<UsageState> = _usageState.asStateFlow()
    
    /**
     * Update usage data after a successful transcription
     * Called by WhisperApiClient after receiving API response
     */
    fun updateUsage(secondsUsed: Int, totalSecondsThisMonth: Int) {
        _usageState.value = UsageState(
            lastSecondsUsed = secondsUsed,
            totalSecondsThisMonth = totalSecondsThisMonth,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * Clear usage data (e.g., on sign out)
     */
    fun clear() {
        _usageState.value = UsageState()
    }
}
