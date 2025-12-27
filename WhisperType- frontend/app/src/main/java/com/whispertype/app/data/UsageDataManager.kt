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
     * User plan enum for Iteration 3
     */
    enum class Plan {
        FREE_TRIAL,
        PRO;
        
        companion object {
            fun fromString(value: String): Plan {
                return when (value) {
                    "pro" -> PRO
                    else -> FREE_TRIAL
                }
            }
        }
    }
    
    /**
     * Data class representing usage and trial state
     */
    data class UsageState(
        // Loading state - true until first API fetch completes
        val isLoading: Boolean = true,
        // Current plan (Iteration 3)
        val currentPlan: Plan = Plan.FREE_TRIAL,
        // Existing fields
        val lastSecondsUsed: Int = 0,           // Seconds used in last transcription
        val totalSecondsThisMonth: Int = 0,     // Total seconds used this month
        val lastUpdated: Long = 0,              // Timestamp of last update
        // Trial fields
        val trialStatus: TrialStatus = TrialStatus.ACTIVE,
        val freeSecondsUsed: Int = 0,           // Lifetime usage
        val freeSecondsRemaining: Int = 1200,   // Remaining seconds
        val totalTrialSeconds: Int = 1200,      // Total trial limit in seconds (from Remote Config)
        val trialExpiryDateMs: Long = 0,        // Trial expiry timestamp
        val warningLevel: WarningLevel = WarningLevel.NONE,
        // Pro plan fields (Iteration 3)
        val proSecondsUsed: Int = 0,
        val proSecondsRemaining: Int = 9000,    // 150 minutes default
        val proSecondsLimit: Int = 9000,
        val proResetDateMs: Long = 0            // Next monthly reset date
    ) {
        /**
         * Whether user is on Pro plan
         */
        val isProUser: Boolean
            get() = currentPlan == Plan.PRO
        
        /**
         * Current quota seconds remaining (trial or Pro based on plan)
         */
        val currentSecondsRemaining: Int
            get() = if (isProUser) proSecondsRemaining else freeSecondsRemaining
        
        /**
         * Current quota is valid (has minutes remaining)
         */
        val isQuotaValid: Boolean
            get() = if (isProUser) proSecondsRemaining > 0 else isTrialValid
        /**
         * Minutes remaining in trial (calculated)
         */
        val freeMinutesRemaining: Float
            get() = freeSecondsRemaining / 60f
        
        /**
         * Time remaining formatted as M:SS (e.g., "2:50" for 170 seconds)
         */
        val formattedTimeRemaining: String
            get() {
                val minutes = freeSecondsRemaining / 60
                val seconds = freeSecondsRemaining % 60
                return String.format("%d:%02d", minutes, seconds)
            }
        
        /**
         * Minutes used in trial (calculated)
         */
        val freeMinutesUsed: Float
            get() = freeSecondsUsed / 60f
        
        /**
         * Trial time used formatted as M:SS (e.g., "5:30" for 330 seconds)
         */
        val formattedTimeUsed: String
            get() {
                val minutes = freeSecondsUsed / 60
                val seconds = freeSecondsUsed % 60
                return String.format("%d:%02d", minutes, seconds)
            }
        
        /**
         * Monthly usage formatted as M:SS (e.g., "3:45" for 225 seconds)
         */
        val formattedMonthlyUsage: String
            get() {
                val minutes = totalSecondsThisMonth / 60
                val seconds = totalSecondsThisMonth % 60
                return String.format("%d:%02d", minutes, seconds)
            }
        
        /**
         * Total trial time formatted as MM:SS (e.g., "20:00" for 1200 seconds)
         */
        val formattedTotalTime: String
            get() {
                val minutes = totalTrialSeconds / 60
                val seconds = totalTrialSeconds % 60
                return String.format("%d:%02d", minutes, seconds)
            }
        
        /**
         * Percentage of trial used (0-100)
         */
        val usagePercentage: Float
            get() = if (totalTrialSeconds > 0) {
                (freeSecondsUsed.toFloat() / totalTrialSeconds.toFloat()) * 100f
            } else {
                0f
            }
        
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
        warningLevel: String,
        totalTrialSeconds: Int = freeSecondsUsed + freeSecondsRemaining
    ) {
        _usageState.value = _usageState.value.copy(
            isLoading = false,
            trialStatus = TrialStatus.fromString(status),
            freeSecondsUsed = freeSecondsUsed,
            freeSecondsRemaining = freeSecondsRemaining,
            totalTrialSeconds = totalTrialSeconds,
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
        warningLevel: String,
        totalTrialSeconds: Int = freeSecondsUsed + freeSecondsRemaining
    ) {
        _usageState.value = UsageState(
            isLoading = false,
            lastSecondsUsed = secondsUsed,
            totalSecondsThisMonth = totalSecondsThisMonth,
            lastUpdated = System.currentTimeMillis(),
            trialStatus = TrialStatus.fromString(status),
            freeSecondsUsed = freeSecondsUsed,
            freeSecondsRemaining = freeSecondsRemaining,
            totalTrialSeconds = totalTrialSeconds,
            trialExpiryDateMs = trialExpiryDateMs,
            warningLevel = WarningLevel.fromString(warningLevel)
        )
    }
    
    /**
     * Mark trial as expired (called when 403 received)
     */
    fun markTrialExpired(status: TrialStatus) {
        _usageState.value = _usageState.value.copy(
            isLoading = false,
            trialStatus = status,
            freeSecondsRemaining = 0,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * Update Pro subscription status (Iteration 3)
     */
    fun updateProStatus(
        proSecondsUsed: Int,
        proSecondsRemaining: Int,
        proSecondsLimit: Int,
        proResetDateMs: Long
    ) {
        _usageState.value = _usageState.value.copy(
            isLoading = false,
            currentPlan = Plan.PRO,
            proSecondsUsed = proSecondsUsed,
            proSecondsRemaining = proSecondsRemaining,
            proSecondsLimit = proSecondsLimit,
            proResetDateMs = proResetDateMs,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * Update subscription status from getSubscriptionStatus endpoint (Iteration 3)
     * Handles both trial and Pro users
     */
    fun updateSubscriptionStatus(
        plan: String,
        status: String,
        secondsRemaining: Int,
        warningLevel: String,
        // Trial-specific
        freeSecondsUsed: Int? = null,
        trialExpiryDateMs: Long? = null,
        // Pro-specific
        proSecondsUsed: Int? = null,
        proSecondsLimit: Int? = null,
        resetDateMs: Long? = null
    ) {
        val currentPlan = Plan.fromString(plan)
        
        _usageState.value = _usageState.value.copy(
            isLoading = false,
            currentPlan = currentPlan,
            trialStatus = TrialStatus.fromString(status),
            warningLevel = WarningLevel.fromString(warningLevel),
            // Trial fields
            freeSecondsUsed = freeSecondsUsed ?: _usageState.value.freeSecondsUsed,
            freeSecondsRemaining = if (currentPlan == Plan.FREE_TRIAL) secondsRemaining else _usageState.value.freeSecondsRemaining,
            trialExpiryDateMs = trialExpiryDateMs ?: _usageState.value.trialExpiryDateMs,
            // Pro fields
            proSecondsUsed = proSecondsUsed ?: _usageState.value.proSecondsUsed,
            proSecondsRemaining = if (currentPlan == Plan.PRO) secondsRemaining else _usageState.value.proSecondsRemaining,
            proSecondsLimit = proSecondsLimit ?: _usageState.value.proSecondsLimit,
            proResetDateMs = resetDateMs ?: _usageState.value.proResetDateMs,
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

