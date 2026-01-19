package com.whispertype.app.repository

import android.app.Activity
import com.whispertype.app.billing.BillingManager
import kotlinx.coroutines.flow.StateFlow

/**
 * BillingRepository - Single source of truth for billing/subscription state
 * 
 * This repository:
 * - Exposes reactive subscription state via StateFlow
 * - Handles purchase flow
 * - Coordinates with backend for subscription verification
 */
interface BillingRepository {
    /**
     * Reactive stream of Pro subscription status
     */
    val isProUser: StateFlow<Boolean>
    
    /**
     * Reactive stream of subscription status
     */
    val subscriptionStatus: StateFlow<BillingManager.SubscriptionStatus>
    
    /**
     * Initialize billing client
     */
    fun initialize(onReady: () -> Unit = {})
    
    /**
     * Query subscription product details
     */
    suspend fun querySubscription()
    
    /**
     * Launch purchase flow
     * 
     * @param activity Activity context for billing UI
     * @param onSuccess Called when purchase completes successfully
     * @param onError Called when purchase fails or is cancelled
     */
    fun launchPurchase(
        activity: Activity, 
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    )
    
    /**
     * Get formatted price string for display
     */
    fun getFormattedPrice(): String?
    
    /**
     * Set auth token provider for backend verification
     */
    fun setAuthTokenProvider(provider: () -> String?)
    
    /**
     * Release billing client resources
     */
    fun release()
}
