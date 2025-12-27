package com.whispertype.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BillingManager - Handles Google Play Billing for Pro subscription
 * 
 * Iteration 3: Manages subscription purchase flow and status
 */
class BillingManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BillingManager"
        
        // Product ID - should match Google Play Console subscription
        const val PRO_MONTHLY_PRODUCT_ID = "whispertype_pro_monthly"
    }
    
    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null
    
    // Callback to invoke on successful purchase
    private var pendingSuccessCallback: (() -> Unit)? = null
    
    // Subscription state
    private val _isProUser = MutableStateFlow(false)
    val isProUser: StateFlow<Boolean> = _isProUser.asStateFlow()
    
    private val _subscriptionStatus = MutableStateFlow<SubscriptionStatus>(SubscriptionStatus.Unknown)
    val subscriptionStatus: StateFlow<SubscriptionStatus> = _subscriptionStatus.asStateFlow()
    
    sealed class SubscriptionStatus {
        object Unknown : SubscriptionStatus()
        object NotSubscribed : SubscriptionStatus()
        object Active : SubscriptionStatus()
        data class Error(val message: String) : SubscriptionStatus()
    }
    
    /**
     * Initialize the billing client and connect to Google Play
     */
    fun initialize(onReady: () -> Unit = {}) {
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .build()
        
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    onReady()
                    // Check for existing purchases
                    queryExistingPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _subscriptionStatus.value = SubscriptionStatus.Error(
                        "Failed to connect to Google Play"
                    )
                }
            }
            
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected, will retry")
                // Billing client will auto-retry connection
            }
        })
    }
    
    /**
     * Query available Pro subscription product details
     */
    suspend fun queryProSubscription(): ProductDetails? {
        val billingClient = this.billingClient ?: return null
        
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRO_MONTHLY_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        val result = billingClient.queryProductDetails(params)
        
        if (result.billingResult.responseCode == BillingResponseCode.OK) {
            productDetails = result.productDetailsList?.firstOrNull()
            Log.d(TAG, "Product details loaded: ${productDetails?.title}")
            return productDetails
        } else {
            Log.e(TAG, "Failed to query products: ${result.billingResult.debugMessage}")
            return null
        }
    }
    
    /**
     * Launch the purchase flow for Pro subscription
     */
    fun launchPurchaseFlow(
        activity: Activity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val billingClient = this.billingClient
        val details = this.productDetails
        
        if (billingClient == null || details == null) {
            onError("Billing not initialized")
            return
        }
        
        // Store success callback for when purchase completes
        pendingSuccessCallback = onSuccess
        
        // Get the offer token for the subscription
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            onError("No subscription offer available")
            return
        }
        
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingResponseCode.OK) {
            Log.e(TAG, "Failed to launch billing flow: ${result.debugMessage}")
            pendingSuccessCallback = null
            onError("Failed to start purchase: ${result.debugMessage}")
        }
    }
    
    /**
     * Check for existing subscription purchases
     */
    private fun queryExistingPurchases() {
        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                val activePurchase = purchasesList.find { purchase ->
                    purchase.products.contains(PRO_MONTHLY_PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                
                if (activePurchase != null) {
                    Log.d(TAG, "Active Pro subscription found")
                    _isProUser.value = true
                    _subscriptionStatus.value = SubscriptionStatus.Active
                    
                    // Acknowledge if not yet acknowledged
                    if (!activePurchase.isAcknowledged) {
                        acknowledgePurchase(activePurchase)
                    }
                } else {
                    Log.d(TAG, "No active Pro subscription")
                    _isProUser.value = false
                    _subscriptionStatus.value = SubscriptionStatus.NotSubscribed
                }
            } else {
                Log.e(TAG, "Failed to query purchases: ${billingResult.debugMessage}")
            }
        }
    }
    
    /**
     * Handle purchase updates from Google Play
     */
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User cancelled purchase")
            }
            else -> {
                Log.e(TAG, "Purchase error: ${billingResult.debugMessage}")
                _subscriptionStatus.value = SubscriptionStatus.Error(
                    billingResult.debugMessage ?: "Purchase failed"
                )
            }
        }
    }
    
    /**
     * Process a completed purchase
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            Log.d(TAG, "Purchase completed: ${purchase.products}")
            
            if (purchase.products.contains(PRO_MONTHLY_PRODUCT_ID)) {
                _isProUser.value = true
                _subscriptionStatus.value = SubscriptionStatus.Active
                
                // Acknowledge the purchase
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
                
                // Call success callback
                pendingSuccessCallback?.invoke()
                pendingSuccessCallback = null
                
                // TODO: Verify with backend and update user document
                // verifyWithBackend(purchase.purchaseToken)
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Log.d(TAG, "Purchase pending")
            // Handle pending purchase (e.g., show message to user)
        }
    }
    
    /**
     * Acknowledge purchase to prevent automatic refund
     */
    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        billingClient?.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged")
            } else {
                Log.e(TAG, "Failed to acknowledge: ${result.debugMessage}")
            }
        }
    }
    
    /**
     * Get formatted price string for UI
     */
    fun getFormattedPrice(): String? {
        return productDetails?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice
    }
    
    /**
     * Cleanup billing client connection
     */
    fun release() {
        billingClient?.endConnection()
        billingClient = null
    }
}
