package com.whispertype.app.billing

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.whispertype.app.data.UsageDataManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MockBillingManager - Fake billing manager for local development testing
 * 
 * This simulates Google Play Billing behavior without requiring Play Store.
 * Use this in DEBUG builds to test UI and purchase flows locally.
 */
class MockBillingManager {
    
    companion object {
        private const val TAG = "MockBillingManager"
        
        // Simulate delay for realistic testing (milliseconds)
        private const val MOCK_DELAY_MS = 2000L
    }
    
    // Subscription state (mirrors real BillingManager)
    private val _isProUser = MutableStateFlow(false)
    val isProUser: StateFlow<Boolean> = _isProUser.asStateFlow()
    
    private val _subscriptionStatus = MutableStateFlow<SubscriptionStatus>(SubscriptionStatus.NotSubscribed)
    val subscriptionStatus: StateFlow<SubscriptionStatus> = _subscriptionStatus.asStateFlow()
    
    sealed class SubscriptionStatus {
        object Unknown : SubscriptionStatus()
        object NotSubscribed : SubscriptionStatus()
        object Active : SubscriptionStatus()
        data class Error(val message: String) : SubscriptionStatus()
    }
    
    /**
     * Initialize - immediately ready (no Google Play connection needed)
     */
    fun initialize(onReady: () -> Unit = {}) {
        Log.d(TAG, "🧪 MockBillingManager initialized (DEBUG MODE)")
        _subscriptionStatus.value = SubscriptionStatus.NotSubscribed
        onReady()
    }
    
    /**
     * Query Pro subscription - returns mock product details
     */
    suspend fun queryProSubscription(): MockProductDetails {
        Log.d(TAG, "🧪 Querying mock product details")
        return MockProductDetails(
            productId = "whispertype_pro_monthly",
            title = "VoxType Pro (Mock)",
            price = "₹199.00"
        )
    }
    
    /**
     * Launch purchase flow - simulates successful purchase after delay
     */
    fun launchPurchaseFlow(
        activity: Activity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        Log.d(TAG, "🧪 Launching mock purchase flow...")
        
        // Simulate purchase dialog delay
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "🧪 Mock purchase SUCCESSFUL!")
            _isProUser.value = true
            _subscriptionStatus.value = SubscriptionStatus.Active
            
            // Update UsageDataManager so UI reflects Pro status
            UsageDataManager.updateProStatus(
                proSecondsUsed = 0,
                proSecondsRemaining = 9000,  // 150 minutes
                proSecondsLimit = 9000,
                proResetDateMs = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000) // 30 days from now
            )
            Log.d(TAG, "🧪 UsageDataManager updated to Pro status")
            
            // Call success callback
            onSuccess()
        }, MOCK_DELAY_MS)
    }
    
    /**
     * Simulate a failed purchase (for testing error handling)
     */
    fun simulatePurchaseFailure(
        activity: Activity,
        onError: (String) -> Unit = {}
    ) {
        Log.d(TAG, "🧪 Simulating purchase failure...")
        
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "🧪 Mock purchase FAILED!")
            _subscriptionStatus.value = SubscriptionStatus.Error("Mock error: Purchase cancelled")
            onError("Mock error: Purchase cancelled")
        }, MOCK_DELAY_MS)
    }
    
    /**
     * Simulate user cancellation
     */
    fun simulatePurchaseCancellation(
        activity: Activity,
        onError: (String) -> Unit = {}
    ) {
        Log.d(TAG, "🧪 Simulating user cancellation...")
        
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "🧪 User cancelled purchase")
            // Status stays as NotSubscribed
        }, MOCK_DELAY_MS)
    }
    
    /**
     * Get formatted price string for UI
     */
    fun getFormattedPrice(): String {
        return "₹199.00/month (Mock)"
    }
    
    /**
     * Reset to non-subscribed state (for testing)
     */
    fun resetSubscription() {
        Log.d(TAG, "🧪 Resetting subscription to NotSubscribed")
        _isProUser.value = false
        _subscriptionStatus.value = SubscriptionStatus.NotSubscribed
        
        // Reset UsageDataManager to free trial
        UsageDataManager.clear()
        Log.d(TAG, "🧪 UsageDataManager reset to free trial")
    }
    
    /**
     * Cleanup - nothing to clean up for mock
     */
    fun release() {
        Log.d(TAG, "🧪 MockBillingManager released")
    }
    
    /**
     * Mock product details class
     */
    data class MockProductDetails(
        val productId: String,
        val title: String,
        val price: String
    )
}
