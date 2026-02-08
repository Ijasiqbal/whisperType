package com.whispertype.app.repository

import android.app.Activity
import com.whispertype.app.billing.BillingManager
import com.whispertype.app.billing.IBillingManager
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BillingRepositoryImpl - Implementation of BillingRepository
 * 
 * Wraps IBillingManager (which can be mock or real) to provide a testable interface.
 */
@Singleton
class BillingRepositoryImpl @Inject constructor(
    private val billingManager: IBillingManager
) : BillingRepository {
    
    // Note: We need to map from IBillingManager's isProUser to our interface
    // This requires casting since IBillingManager doesn't expose subscriptionStatus
    private val realBillingManager: BillingManager?
        get() = (billingManager as? com.whispertype.app.billing.RealBillingWrapper)?.let {
            // Access underlying manager via reflection or keep reference
            null // We'll use the interface methods instead
        }
    
    override val isProUser: StateFlow<Boolean>
        get() = billingManager.isProUser
    
    // For subscriptionStatus, we create a derived flow from isProUser
    // since IBillingManager doesn't expose subscriptionStatus directly
    private val _subscriptionStatus = kotlinx.coroutines.flow.MutableStateFlow<BillingManager.SubscriptionStatus>(
        BillingManager.SubscriptionStatus.Unknown
    )
    override val subscriptionStatus: StateFlow<BillingManager.SubscriptionStatus>
        get() = _subscriptionStatus
    
    override fun initialize(onReady: () -> Unit) {
        billingManager.initialize {
            // Update subscription status based on Pro user state
            updateSubscriptionStatus()
            onReady()
        }
    }
    
    override suspend fun querySubscription() {
        billingManager.queryProducts()
        updateSubscriptionStatus()
    }

    private fun updateSubscriptionStatus() {
        _subscriptionStatus.value = if (isProUser.value) {
            BillingManager.SubscriptionStatus.Active
        } else {
            BillingManager.SubscriptionStatus.NotSubscribed
        }
    }

    override fun launchPurchase(
        activity: Activity,
        productId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        billingManager.launchPurchaseFlow(
            activity = activity,
            productId = productId,
            onSuccess = {
                updateSubscriptionStatus()
                onSuccess()
            },
            onError = onError
        )
    }

    override fun getFormattedPrice(productId: String): String? {
        return billingManager.getFormattedPrice(productId)
    }
    
    override fun setAuthTokenProvider(provider: () -> String?) {
        billingManager.setAuthTokenProvider(provider)
    }
    
    override fun release() {
        billingManager.release()
    }
}
