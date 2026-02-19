package com.whispertype.app.billing

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.whispertype.app.Constants
import com.whispertype.app.data.UsageDataManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MockBillingManager - Fake billing manager for local development testing
 *
 * Simulates Google Play Billing behavior without requiring Play Store.
 * Supports 3-tier plans: Starter, Pro, Unlimited.
 */
class MockBillingManager {

    companion object {
        private const val TAG = "MockBillingManager"
        private const val MOCK_DELAY_MS = 2000L
    }

    // Mock product catalog
    private val mockProducts = mapOf(
        Constants.PRODUCT_ID_STARTER to MockProductDetails(
            Constants.PRODUCT_ID_STARTER, "Wozcribe Starter (Mock)",
            "${Constants.PRICE_STARTER_FALLBACK}/month", Constants.CREDITS_STARTER
        ),
        Constants.PRODUCT_ID_PRO to MockProductDetails(
            Constants.PRODUCT_ID_PRO, "Wozcribe Pro (Mock)",
            "${Constants.PRICE_PRO_FALLBACK}/month", Constants.CREDITS_PRO
        ),
        Constants.PRODUCT_ID_UNLIMITED to MockProductDetails(
            Constants.PRODUCT_ID_UNLIMITED, "Wozcribe Unlimited (Mock)",
            "${Constants.PRICE_UNLIMITED_FALLBACK}/month", Constants.CREDITS_UNLIMITED
        )
    )

    // Subscription state
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

    fun initialize(onReady: () -> Unit = {}) {
        Log.d(TAG, "MockBillingManager initialized (DEBUG MODE)")
        _subscriptionStatus.value = SubscriptionStatus.NotSubscribed
        onReady()
    }

    /**
     * Query all mock products
     */
    suspend fun queryProducts(): Map<String, MockProductDetails> {
        Log.d(TAG, "Querying ${mockProducts.size} mock products")
        return mockProducts
    }

    /**
     * Legacy compat
     */
    suspend fun queryProSubscription(): MockProductDetails {
        return mockProducts[Constants.PRODUCT_ID_PRO]!!
    }

    /**
     * Launch purchase flow for a specific product
     */
    fun launchPurchaseFlow(
        activity: Activity,
        productId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val product = mockProducts[productId]
        if (product == null) {
            onError("Unknown product: $productId")
            return
        }

        Log.d(TAG, "Launching mock purchase for: ${product.title}")

        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Mock purchase SUCCESSFUL! Plan: ${product.title}")
            _isProUser.value = true
            _subscriptionStatus.value = SubscriptionStatus.Active

            UsageDataManager.updateProStatus(
                proCreditsUsed = 0,
                proCreditsRemaining = product.credits,
                proCreditsLimit = product.credits,
                proResetDateMs = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
            )

            onSuccess()
        }, MOCK_DELAY_MS)
    }

    /**
     * Legacy compat
     */
    fun launchPurchaseFlow(
        activity: Activity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        launchPurchaseFlow(activity, Constants.PRODUCT_ID_PRO, onSuccess, onError)
    }

    fun getFormattedPrice(productId: String): String {
        return mockProducts[productId]?.price ?: "${Constants.PRICE_PRO_FALLBACK}/month (Mock)"
    }

    fun getFormattedPrice(): String = getFormattedPrice(Constants.PRODUCT_ID_PRO)

    fun resetSubscription() {
        Log.d(TAG, "Resetting subscription to NotSubscribed")
        _isProUser.value = false
        _subscriptionStatus.value = SubscriptionStatus.NotSubscribed
        UsageDataManager.clear()
    }

    fun release() {
        Log.d(TAG, "MockBillingManager released")
    }

    data class MockProductDetails(
        val productId: String,
        val title: String,
        val price: String,
        val credits: Int
    )
}
