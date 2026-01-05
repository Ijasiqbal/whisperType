package com.whispertype.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.StateFlow

/**
 * BillingManagerFactory - Creates the appropriate billing manager based on build type
 * 
 * Usage:
 *   val billing = BillingManagerFactory.create(context)
 *   billing.initialize { 
 *       // Ready to use
 *   }
 *   billing.launchPurchase(activity) { error -> ... }
 */
object BillingManagerFactory {
    
    private const val TAG = "BillingManagerFactory"
    
    /**
     * Creates the appropriate billing manager:
     * - DEBUG builds: MockBillingManager (no Play Store needed)
     * - RELEASE builds: Real BillingManager (requires Play Store)
     */
    fun create(context: Context, forceUseMock: Boolean = false): IBillingManager {
        // Check if running a debuggable build
        val isDebugBuild = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        
        return if (isDebugBuild || forceUseMock) {
            Log.d(TAG, "🧪 Using MockBillingManager (DEBUG MODE)")
            MockBillingWrapper()
        } else {
            Log.d(TAG, "💳 Using Real BillingManager (PRODUCTION)")
            RealBillingWrapper(context)
        }
    }
}

/**
 * Common interface for both Mock and Real billing managers
 */
interface IBillingManager {
    val isProUser: StateFlow<Boolean>
    
    fun initialize(onReady: () -> Unit = {})
    suspend fun queryProSubscription(): Any?
    fun launchPurchaseFlow(
        activity: Activity, 
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    )
    fun getFormattedPrice(): String?
    fun setAuthTokenProvider(provider: () -> String?)
    fun release()
}

/**
 * Wrapper for MockBillingManager
 */
class MockBillingWrapper : IBillingManager {
    private val mock = MockBillingManager()
    
    override val isProUser: StateFlow<Boolean> = mock.isProUser
    
    override fun initialize(onReady: () -> Unit) = mock.initialize(onReady)
    override suspend fun queryProSubscription() = mock.queryProSubscription()
    override fun launchPurchaseFlow(
        activity: Activity, 
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = mock.launchPurchaseFlow(activity, onSuccess, onError)
    override fun getFormattedPrice(): String = mock.getFormattedPrice()
    override fun setAuthTokenProvider(provider: () -> String?) {
        // Mock doesn't need auth token
    }
    override fun release() = mock.release()
}

/**
 * Wrapper for real BillingManager
 */
class RealBillingWrapper(context: Context) : IBillingManager {
    private val real = BillingManager(context)
    
    override val isProUser: StateFlow<Boolean> = real.isProUser
    
    override fun initialize(onReady: () -> Unit) = real.initialize(onReady)
    override suspend fun queryProSubscription() = real.queryProSubscription()
    override fun launchPurchaseFlow(
        activity: Activity, 
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = real.launchPurchaseFlow(activity, onSuccess, onError)
    override fun getFormattedPrice(): String? = real.getFormattedPrice()
    override fun setAuthTokenProvider(provider: () -> String?) = real.setAuthTokenProvider(provider)
    override fun release() = real.release()
}

