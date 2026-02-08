package com.whispertype.app.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.whispertype.app.Constants
import com.whispertype.app.api.WhisperApiClient
import com.whispertype.app.data.UsageDataManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BillingManager - Handles Google Play Billing for subscriptions
 *
 * Supports 3 tiers: Starter, Pro, Unlimited (India + International)
 */
class BillingManager(private val context: Context) {

    companion object {
        private const val TAG = "BillingManager"

        // All subscription product IDs
        val ALL_PRODUCT_IDS = listOf(
            Constants.PRODUCT_ID_STARTER,
            Constants.PRODUCT_ID_PRO,
            Constants.PRODUCT_ID_UNLIMITED
        )

        // Legacy compat
        const val PRO_MONTHLY_PRODUCT_ID = "whispertype_pro_monthly"
    }

    private var billingClient: BillingClient? = null

    // Map of productId -> ProductDetails for all queried products
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    // Handler to ensure callbacks run on the main thread
    private val mainHandler = Handler(Looper.getMainLooper())

    // Callback to invoke on successful purchase
    private var pendingSuccessCallback: (() -> Unit)? = null

    // Auth token provider for backend verification
    private var authTokenProvider: (() -> String?)? = null

    // API client for backend verification
    private val apiClient = WhisperApiClient()

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
            }
        })
    }

    /**
     * Query all subscription product details
     */
    suspend fun queryAllProducts(): Map<String, ProductDetails> {
        val billingClient = this.billingClient ?: return emptyMap()

        val productList = ALL_PRODUCT_IDS.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = billingClient.queryProductDetails(params)

        if (result.billingResult.responseCode == BillingResponseCode.OK) {
            productDetailsMap.clear()
            result.productDetailsList?.forEach { details ->
                productDetailsMap[details.productId] = details
                Log.d(TAG, "Product loaded: ${details.productId} - ${details.title}")
            }
            Log.d(TAG, "Loaded ${productDetailsMap.size} products")
            return productDetailsMap.toMap()
        } else {
            Log.e(TAG, "Failed to query products: ${result.billingResult.debugMessage}")
            return emptyMap()
        }
    }

    /**
     * Legacy: Query single Pro subscription
     */
    suspend fun queryProSubscription(): ProductDetails? {
        val products = queryAllProducts()
        return products[PRO_MONTHLY_PRODUCT_ID]
    }

    /**
     * Launch the purchase flow for a specific product
     */
    fun launchPurchaseFlow(
        activity: Activity,
        productId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val billingClient = this.billingClient
        val details = productDetailsMap[productId]

        if (billingClient == null) {
            onError("Billing not initialized")
            return
        }

        if (details == null) {
            onError("Product not found: $productId")
            return
        }

        pendingSuccessCallback = onSuccess

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
     * Legacy: Launch purchase for the default Pro product
     */
    fun launchPurchaseFlow(
        activity: Activity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        launchPurchaseFlow(activity, PRO_MONTHLY_PRODUCT_ID, onSuccess, onError)
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
                // Find any active subscription from our product IDs
                val activePurchase = purchasesList.find { purchase ->
                    purchase.products.any { it in ALL_PRODUCT_IDS || it == PRO_MONTHLY_PRODUCT_ID } &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }

                if (activePurchase != null) {
                    val activeProductId = activePurchase.products.firstOrNull() ?: PRO_MONTHLY_PRODUCT_ID
                    Log.d(TAG, "Active subscription found: $activeProductId")
                    _isProUser.value = true
                    _subscriptionStatus.value = SubscriptionStatus.Active

                    if (!activePurchase.isAcknowledged) {
                        acknowledgePurchase(activePurchase)
                    }

                    // Verify with backend to get actual quota values
                    val authToken = authTokenProvider?.invoke()
                    if (authToken != null) {
                        Log.d(TAG, "Verifying subscription with backend to get Pro quota")
                        apiClient.verifySubscription(
                            authToken = authToken,
                            purchaseToken = activePurchase.purchaseToken,
                            productId = activeProductId,
                            onSuccess = { proCreditsRemaining, proCreditsLimit, resetDateMs ->
                                Log.d(TAG, "Backend verification success: $proCreditsRemaining credits remaining")
                            },
                            onError = { error ->
                                Log.w(TAG, "Backend verification failed: $error, using defaults")
                                val credits = creditsForProduct(activeProductId)
                                UsageDataManager.updateProStatus(
                                    proCreditsUsed = 0,
                                    proCreditsRemaining = credits,
                                    proCreditsLimit = credits,
                                    proResetDateMs = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                                )
                            }
                        )
                    } else {
                        Log.w(TAG, "No auth token available, using default Pro values")
                        val credits = creditsForProduct(activeProductId)
                        UsageDataManager.updateProStatus(
                            proCreditsUsed = 0,
                            proCreditsRemaining = credits,
                            proCreditsLimit = credits,
                            proResetDateMs = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                        )
                    }
                } else {
                    Log.d(TAG, "No active subscription")
                    _isProUser.value = false
                    _subscriptionStatus.value = SubscriptionStatus.NotSubscribed
                }
            } else {
                Log.e(TAG, "Failed to query purchases: ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Get credits limit for a product ID
     */
    private fun creditsForProduct(productId: String): Int {
        return when (productId) {
            Constants.PRODUCT_ID_STARTER -> Constants.CREDITS_STARTER
            Constants.PRODUCT_ID_PRO -> Constants.CREDITS_PRO
            Constants.PRODUCT_ID_UNLIMITED -> Constants.CREDITS_UNLIMITED
            else -> Constants.CREDITS_PRO // Legacy fallback
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
            val purchasedProductId = purchase.products.firstOrNull() ?: return
            Log.d(TAG, "Purchase completed: $purchasedProductId")

            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            }

            verifyWithBackend(
                purchaseToken = purchase.purchaseToken,
                productId = purchasedProductId,
                onSuccess = {
                    _isProUser.value = true
                    _subscriptionStatus.value = SubscriptionStatus.Active
                    pendingSuccessCallback?.invoke()
                    pendingSuccessCallback = null
                },
                onError = { error ->
                    Log.e(TAG, "Backend verification failed: $error")
                    _isProUser.value = true
                    _subscriptionStatus.value = SubscriptionStatus.Active
                    val credits = creditsForProduct(purchasedProductId)
                    UsageDataManager.updateProStatus(
                        proCreditsUsed = 0,
                        proCreditsRemaining = credits,
                        proCreditsLimit = credits,
                        proResetDateMs = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                    )
                    pendingSuccessCallback?.invoke()
                    pendingSuccessCallback = null
                }
            )
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Log.d(TAG, "Purchase pending")
        }
    }

    /**
     * Verify purchase with backend server
     */
    private fun verifyWithBackend(
        purchaseToken: String,
        productId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val authToken = authTokenProvider?.invoke()
        if (authToken == null) {
            Log.e(TAG, "No auth token available for verification")
            onError("Authentication required")
            return
        }

        apiClient.verifySubscription(
            authToken = authToken,
            purchaseToken = purchaseToken,
            productId = productId,
            onSuccess = { _, _, _ ->
                Log.d(TAG, "Backend verification successful")
                mainHandler.post { onSuccess() }
            },
            onError = { error ->
                Log.e(TAG, "Backend verification error: $error")
                mainHandler.post { onError(error) }
            }
        )
    }

    /**
     * Set the auth token provider for backend verification
     */
    fun setAuthTokenProvider(provider: () -> String?) {
        authTokenProvider = provider
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
     * Get formatted price string for a specific product
     */
    fun getFormattedPrice(productId: String): String? {
        return productDetailsMap[productId]?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice
    }

    /**
     * Legacy: Get formatted price for default Pro product
     */
    fun getFormattedPrice(): String? = getFormattedPrice(PRO_MONTHLY_PRODUCT_ID)

    /**
     * Cleanup billing client connection
     */
    fun release() {
        billingClient?.endConnection()
        billingClient = null
    }
}
