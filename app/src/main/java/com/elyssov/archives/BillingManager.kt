package com.elyssov.archives

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType

/**
 * Thin wrapper around the Play Billing Client for the tip-jar flow.
 *
 * The five SKUs below must be created in Play Console as **managed,
 * consumable in-app products** (NOT subscriptions). Any successful
 * tip — at any of the five price points — flips `hasEverTipped` to
 * true; the launch prompt then never shows again on this account.
 *
 * The flag lives in two places:
 *   1. A SharedPreferences cache for instant launch-time check.
 *   2. The Play account itself (queryPurchasesAsync) — authoritative,
 *      survives reinstalls and new devices, restored on every launch.
 *
 * Products are *consumed* on purchase so the user can tip again
 * later if they want; the SharedPrefs flag, once true, stays true.
 */
class BillingManager(
    private val context: Context,
    private val onReady: () -> Unit,
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val client: BillingClient = BillingClient.newBuilder(context)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .setListener(this)
        .build()

    /**
     * True once we have either:
     *   a) a successful purchase recorded in this app's prefs, or
     *   b) confirmed via queryPurchasesAsync that the Play account has
     *      one or more historic tip purchases on file.
     *
     * Driven UI should consult this AFTER [onReady] fires; before that
     * we may not yet have heard back from Play.
     */
    val hasEverTipped: Boolean
        get() = prefs.getBoolean(KEY_TIPPED, false)

    /** Latest fetched ProductDetails, keyed by SKU. May be empty if offline. */
    private var productDetailsBySku: Map<String, ProductDetails> = emptyMap()

    /** Whether we've finished the initial connect + restore round-trip. */
    private var initialQueryDone = false

    fun start() {
        client.startConnection(this)
    }

    fun release() {
        if (client.isReady) client.endConnection()
    }

    /**
     * Launch the in-app billing flow for [sku]. Should be one of the five
     * constants defined on the companion. Safe to call on the UI thread.
     * Result arrives in [onPurchasesUpdated].
     */
    fun launchBilling(activity: Activity, sku: String) {
        val details = productDetailsBySku[sku]
        if (details == null) {
            Log.w(TAG, "launchBilling: no ProductDetails for $sku — billing not ready")
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    // ------------------------------------------------------------------
    // BillingClientStateListener
    // ------------------------------------------------------------------

    override fun onBillingSetupFinished(result: BillingResult) {
        if (result.responseCode == BillingResponseCode.OK) {
            queryProducts()
            restorePurchases()
        } else {
            Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
            // Without billing we still let the rest of the app proceed
            // — the tip dialog will see no ProductDetails and stay disabled.
            markReady()
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.w(TAG, "Billing service disconnected — will retry on next start()")
        // BillingClient handles reconnection lazily on next API call; no-op.
    }

    // ------------------------------------------------------------------
    // Product catalog
    // ------------------------------------------------------------------

    private fun queryProducts() {
        val products = TIP_SKUS.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingResponseCode.OK) {
                // Billing 8.x: callback delivers QueryProductDetailsResult,
                // not List<ProductDetails> as in 7.x. Use .productDetailsList.
                val list = queryResult.productDetailsList
                productDetailsBySku = list.associateBy { it.productId }
                Log.d(TAG, "Loaded ${list.size}/${TIP_SKUS.size} tip ProductDetails")
            } else {
                Log.w(TAG, "queryProductDetailsAsync failed: ${result.debugMessage}")
            }
            markReady()
        }
    }

    // ------------------------------------------------------------------
    // Restore (authoritative check on launch, also carries new devices)
    // ------------------------------------------------------------------

    private fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) {
                if (purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                    setTipped()
                }
                // Anything pending/unacknowledged: handle the standard ack +
                // consume cycle so the user can tip again later if they wish.
                purchases
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach { ackAndConsume(it) }
            }
            markReady()
        }
    }

    // ------------------------------------------------------------------
    // PurchasesUpdatedListener
    // ------------------------------------------------------------------

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingResponseCode.OK || purchases == null) return
        for (p in purchases) {
            if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                setTipped()
                ackAndConsume(p)
            }
        }
    }

    private fun ackAndConsume(purchase: Purchase) {
        // Consumables MUST be consumed for the user to repurchase. For tips,
        // a single ack-then-consume is the canonical pattern.
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.consumeAsync(params) { result, _ ->
            if (result.responseCode != BillingResponseCode.OK) {
                Log.w(TAG, "consumeAsync failed: ${result.debugMessage}")
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun markReady() {
        if (!initialQueryDone) {
            initialQueryDone = true
            onReady()
        }
    }

    private fun setTipped() {
        if (!prefs.getBoolean(KEY_TIPPED, false)) {
            prefs.edit().putBoolean(KEY_TIPPED, true).apply()
        }
    }

    companion object {
        private const val TAG = "BillingManager"
        private const val PREFS = "tip_jar"
        private const val KEY_TIPPED = "has_tipped"

        // Five managed-consumable SKUs. Create them in Play Console with
        // the corresponding USD prices ($1 / $5 / $10 / $15 / $20). The
        // IDs are referenced from TipDialog as well.
        const val SKU_1 = "tip_1"
        const val SKU_5 = "tip_5"
        const val SKU_10 = "tip_10"
        const val SKU_15 = "tip_15"
        const val SKU_20 = "tip_20"

        val TIP_SKUS = listOf(SKU_1, SKU_5, SKU_10, SKU_15, SKU_20)
    }
}
