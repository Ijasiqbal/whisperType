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
 * 3. Manages trial status for Iteration 2 controlled trial
 */
object UsageDataManager {
    
    /**
     * Trial status enum matching backend values
     */
    enum class TrialStatus {
        ACTIVE,
        EXPIRED_TIME,
        EXPIRED_USAGE;
        
        companion object {
            fun fromString(value: String): TrialStatus {
                return when (value) {
                    "active" -> ACTIVE
                    "expired_time" -> EXPIRED_TIME
                    "expired_usage" -> EXPIRED_USAGE
                    else -> ACTIVE
                }
            }
        }
    }
    
    /**
     * Warning level enum for trial warnings
     */
    enum class WarningLevel {
        NONE,
        FIFTY_PERCENT,
        EIGHTY_PERCENT,
        NINETY_FIVE_PERCENT;
        
        companion object {
            fun fromString(value: String): WarningLevel {
                return when (value) {
                    "none" -> NONE
                    "fifty_percent" -> FIFTY_PERCENT
                    "eighty_percent" -> EIGHTY_PERCENT
                    "ninety_five_percent" -> NINETY_FIVE_PERCENT
                    else -> NONE
                }
            }
        }
    }
    
    /**
     * Data class representing usage and trial state
     */
    data class UsageState(
        // Existing fields
        val lastSecondsUsed: Int = 0,           // Seconds used in last transcription
        val totalSecondsThisMonth: Int = 0,     // Total seconds used this month
        val lastUpdated: Long = 0,              // Timestamp of last update
        // New Iteration 2 trial fields
        val trialStatus: TrialStatus = TrialStatus.ACTIVE,
        val freeSecondsUsed: Int = 0,           // Lifetime usage (out of 1200)
        val freeSecondsRemaining: Int = 1200,   // Remaining seconds (20 min = 1200s)
        val trialExpiryDateMs: Long = 0,        // Trial expiry timestamp
        val warningLevel: WarningLevel = WarningLevel.NONE
    ) {
        /**
         * Minutes remaining in trial (calculated)
         */
        val freeMinutesRemaining: Float
            get() = freeSecondsRemaining / 60f
        
        /**
         * Minutes used in trial (calculated)
         */
        val freeMinutesUsed: Float
            get() = freeSecondsUsed / 60f
        
        /**
         * Percentage of trial used (0-100)
         */
        val usagePercentage: Float
            get() = (freeSecondsUsed.toFloat() / 1200f) * 100f
        
        /**
         * Whether the trial is still valid
         */
        val isTrialValid: Boolean
            get() = trialStatus == TrialStatus.ACTIVE
    }
    
    private val _usageState = MutableStateFlow(UsageState())
    val usageState: StateFlow<UsageState> = _usageState.asStateFlow()
    
    /**
     * Update usage data after a successful transcription (legacy method)
     * Kept for backward compatibility
     */
    fun updateUsage(secondsUsed: Int, totalSecondsThisMonth: Int) {
        _usageState.value = _usageState.value.copy(
            lastSecondsUsed = secondsUsed,
            totalSecondsThisMonth = totalSecondsThisMonth,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * Update trial status from API response (Iteration 2)
     */
    fun updateTrialStatus(
        status: String,
        freeSecondsUsed: Int,
        freeSecondsRemaining: Int,
        trialExpiryDateMs: Long,
        warningLevel: String
    ) {
        _usageState.value = _usageState.value.copy(
            trialStatus = TrialStatus.fromString(status),
            freeSecondsUsed = freeSecondsUsed,
            freeSecondsRemaining = freeSecondsRemaining,
            trialExpiryDateMs = trialExpiryDateMs,
            warningLevel = WarningLevel.fromString(warningLevel),
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * Full update including both legacy and new trial fields
     */
    fun updateFull(
        secondsUsed: Int,
        totalSecondsThisMonth: Int,
        status: String,
        freeSecondsUsed: Int,
        freeSecondsRemaining: Int,
        trialExpiryDateMs: Long,
        warningLevel: String
    ) {
        _usageState.value = UsageState(
            lastSecondsUsed = secondsUsed,
            totalSecondsThisMonth = totalSecondsThisMonth,
            lastUpdated = System.currentTimeMillis(),
            trialStatus = TrialStatus.fromString(status),
            freeSecondsUsed = freeSecondsUsed,
            freeSecondsRemaining = freeSecondsRemaining,
            trialExpiryDateMs = trialExpiryDateMs,
            warningLevel = WarningLevel.fromString(warningLevel)
        )
    }
    
    /**
     * Mark trial as expired (called when 403 received)
     */
    fun markTrialExpired(status: TrialStatus) {
        _usageState.value = _usageState.value.copy(
            trialStatus = status,
            freeSecondsRemaining = 0,
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
