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
 * 3. Manages trial and Pro credit status
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
                    "ninety_percent" -> NINETY_FIVE_PERCENT // Pro warning level
                    else -> NONE
                }
            }
        }
    }

    /**
     * User plan enum
     */
    enum class Plan {
        FREE,
        PRO;

        companion object {
            fun fromString(value: String): Plan {
                return when (value) {
                    "pro" -> PRO
                    else -> FREE
                }
            }
        }
    }

    /**
     * Data class representing usage and credit state
     */
    data class UsageState(
        // Loading state - true until first API fetch completes
        val isLoading: Boolean = true,
        // Current plan
        val currentPlan: Plan = Plan.FREE,
        // Last transcription info
        val lastCreditsUsed: Int = 0,           // Credits used in last transcription
        val totalCreditsThisMonth: Int = 0,     // Total credits used this month
        val lastUpdated: Long = 0,              // Timestamp of last update
        // Trial (free tier) fields
        val trialStatus: TrialStatus = TrialStatus.ACTIVE,
        val freeCreditsUsed: Int = 0,           // Lifetime usage
        val freeCreditsRemaining: Int = 1000,   // Remaining credits
        val freeTierCredits: Int = 1000,        // Total free tier limit (from Remote Config)
        val trialExpiryDateMs: Long = 0,        // Trial expiry timestamp
        val warningLevel: WarningLevel = WarningLevel.NONE,
        // Pro plan fields
        val proCreditsUsed: Int = 0,
        val proCreditsRemaining: Int = 10000,   // Default from Remote Config
        val proCreditsLimit: Int = 10000,
        val proResetDateMs: Long = 0,           // Next monthly reset date
        val proSubscriptionStartDateMs: Long = 0 // When user first subscribed (for "Member since")
    ) {
        /**
         * Whether user is on Pro plan
         */
        val isProUser: Boolean
            get() = currentPlan == Plan.PRO

        /**
         * Current credits remaining (trial or Pro based on plan)
         */
        val currentCreditsRemaining: Int
            get() = if (isProUser) proCreditsRemaining else freeCreditsRemaining

        /**
         * Current credits limit (trial or Pro based on plan)
         */
        val currentCreditsLimit: Int
            get() = if (isProUser) proCreditsLimit else freeTierCredits

        /**
         * Current quota is valid (has credits remaining)
         */
        val isQuotaValid: Boolean
            get() = if (isProUser) proCreditsRemaining > 0 else isTrialValid

        /**
         * Formatted credits remaining (e.g., "990 credits")
         */
        val formattedCreditsRemaining: String
            get() = "$currentCreditsRemaining"

        /**
         * Formatted credits used (e.g., "10 credits")
         */
        val formattedCreditsUsed: String
            get() = if (isProUser) "$proCreditsUsed" else "$freeCreditsUsed"

        /**
         * Monthly usage formatted
         */
        val formattedMonthlyUsage: String
            get() = "$totalCreditsThisMonth"

        /**
         * Percentage of credits used (0-100)
         */
        val usagePercentage: Float
            get() {
                val limit = if (isProUser) proCreditsLimit else freeTierCredits
                val used = if (isProUser) proCreditsUsed else freeCreditsUsed
                return if (limit > 0) {
                    (used.toFloat() / limit.toFloat()) * 100f
                } else {
                    0f
                }
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
    fun updateUsage(creditsUsed: Int, totalCreditsThisMonth: Int) {
        _usageState.value = _usageState.value.copy(
            lastCreditsUsed = creditsUsed,
            totalCreditsThisMonth = totalCreditsThisMonth,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * Update trial status from API response
     */
    fun updateTrialStatus(
        status: String,
        freeCreditsUsed: Int,
        freeCreditsRemaining: Int,
        trialExpiryDateMs: Long,
        warningLevel: String,
        freeTierCredits: Int = freeCreditsUsed + freeCreditsRemaining
    ) {
        _usageState.value = _usageState.value.copy(
            isLoading = false,
            trialStatus = TrialStatus.fromString(status),
            freeCreditsUsed = freeCreditsUsed,
            freeCreditsRemaining = freeCreditsRemaining,
            freeTierCredits = freeTierCredits,
            trialExpiryDateMs = trialExpiryDateMs,
            warningLevel = WarningLevel.fromString(warningLevel),
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * Full update including both legacy and new trial fields
     * Uses copy() to preserve Pro status and other fields not being updated
     */
    fun updateFull(
        creditsUsed: Int,
        totalCreditsThisMonth: Int,
        status: String,
        freeCreditsUsed: Int,
        freeCreditsRemaining: Int,
        trialExpiryDateMs: Long,
        warningLevel: String,
        freeTierCredits: Int = freeCreditsUsed + freeCreditsRemaining
    ) {
        _usageState.value = _usageState.value.copy(
            isLoading = false,
            lastCreditsUsed = creditsUsed,
            totalCreditsThisMonth = totalCreditsThisMonth,
            lastUpdated = System.currentTimeMillis(),
            trialStatus = TrialStatus.fromString(status),
            freeCreditsUsed = freeCreditsUsed,
            freeCreditsRemaining = freeCreditsRemaining,
            freeTierCredits = freeTierCredits,
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
            freeCreditsRemaining = 0,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * Update Pro subscription status
     */
    fun updateProStatus(
        proCreditsUsed: Int,
        proCreditsRemaining: Int,
        proCreditsLimit: Int,
        proResetDateMs: Long,
        proSubscriptionStartDateMs: Long? = null
    ) {
        _usageState.value = _usageState.value.copy(
            isLoading = false,
            currentPlan = Plan.PRO,
            proCreditsUsed = proCreditsUsed,
            proCreditsRemaining = proCreditsRemaining,
            proCreditsLimit = proCreditsLimit,
            proResetDateMs = proResetDateMs,
            proSubscriptionStartDateMs = proSubscriptionStartDateMs ?: _usageState.value.proSubscriptionStartDateMs,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * Update subscription status from getSubscriptionStatus endpoint
     * Handles both trial and Pro users
     */
    fun updateSubscriptionStatus(
        plan: String,
        status: String,
        creditsRemaining: Int,
        warningLevel: String,
        // Trial-specific
        freeCreditsUsed: Int? = null,
        freeTierCredits: Int? = null,
        trialExpiryDateMs: Long? = null,
        // Pro-specific
        proCreditsUsed: Int? = null,
        proCreditsLimit: Int? = null,
        resetDateMs: Long? = null,
        subscriptionStartDateMs: Long? = null
    ) {
        val currentPlan = Plan.fromString(plan)

        _usageState.value = _usageState.value.copy(
            isLoading = false,
            currentPlan = currentPlan,
            trialStatus = TrialStatus.fromString(status),
            warningLevel = WarningLevel.fromString(warningLevel),
            // Trial fields
            freeCreditsUsed = freeCreditsUsed ?: _usageState.value.freeCreditsUsed,
            freeCreditsRemaining = if (currentPlan == Plan.FREE) creditsRemaining else _usageState.value.freeCreditsRemaining,
            freeTierCredits = freeTierCredits ?: _usageState.value.freeTierCredits,
            trialExpiryDateMs = trialExpiryDateMs ?: _usageState.value.trialExpiryDateMs,
            // Pro fields
            proCreditsUsed = proCreditsUsed ?: _usageState.value.proCreditsUsed,
            proCreditsRemaining = if (currentPlan == Plan.PRO) creditsRemaining else _usageState.value.proCreditsRemaining,
            proCreditsLimit = proCreditsLimit ?: _usageState.value.proCreditsLimit,
            proResetDateMs = resetDateMs ?: _usageState.value.proResetDateMs,
            proSubscriptionStartDateMs = subscriptionStartDateMs ?: _usageState.value.proSubscriptionStartDateMs,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * Mark loading as complete (e.g., when API call fails but we want to show default data)
     */
    fun markLoadingComplete() {
        _usageState.value = _usageState.value.copy(isLoading = false)
    }

    /**
     * Clear usage data (e.g., on sign out)
     */
    fun clear() {
        _usageState.value = UsageState()
    }
}
