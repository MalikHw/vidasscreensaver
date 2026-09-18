package com.malikhw.vidasscrsvr.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DonateHelper(private val context: Context, private val onPurchaseSuccess: () -> Unit = {}) {

    companion object {
        val PRODUCT_IDS = listOf("donation_1", "donation_2", "donation_5", "donation_10", "donation_20")
    }

    sealed class State {
        object Disconnected : State()
        object Connecting   : State()
        object Ready        : State()
        data class Error(val message: String) : State()
    }

    private val _state    = MutableStateFlow<State>(State.Disconnected)
    val state: StateFlow<State> = _state

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products

    private var billingClient: BillingClient? = null

    // connection

    fun connect(scope: CoroutineScope) {
        _state.value = State.Connecting
        billingClient = BillingClient.newBuilder(context)
            .setListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases?.forEach { purchase ->
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val consumeParams = ConsumeParams.newBuilder()
                                    .setPurchaseToken(purchase.purchaseToken)
                                    .build()
                                val client = billingClient ?: return@launch
                                repeat(3) { attempt ->
                                    val consumeResult = kotlinx.coroutines.suspendCancellableCoroutine<BillingResult> { cont ->
                                        client.consumeAsync(consumeParams) { billingResult, _ ->
                                            cont.resume(billingResult) {}
                                        }
                                    }
                                    if (consumeResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                        onPurchaseSuccess()
                                        return@launch
                                    }
                                    kotlinx.coroutines.delay(1000L * (attempt + 1))
                                }
                            }
                        }
                    }
                }
            }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()

        billingClient!!.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = State.Ready
                    scope.launch {
                        consumePendingPurchases()
                        fetchProducts()
                    }
                } else {
                    _state.value = State.Error("Billing unavailable (${result.responseCode})")
                }
            }
            override fun onBillingServiceDisconnected() {
                _state.value = State.Disconnected
            }
        })
    }

    fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
        _state.value = State.Disconnected
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun consumePendingPurchases() {
        val client = billingClient ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = withContext(Dispatchers.IO) {
            suspendCancellableCoroutine<Pair<BillingResult, List<Purchase>>> { cont ->
                client.queryPurchasesAsync(params) { billingResult, purchases ->
                    cont.resume(Pair(billingResult, purchases)) {}
                }
            }
        }
        if (result.first.responseCode == BillingClient.BillingResponseCode.OK) {
            result.second
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { purchase ->
                    val consumeParams = ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    kotlinx.coroutines.suspendCancellableCoroutine<BillingResult> { cont ->
                        client.consumeAsync(consumeParams) { billingResult, _ ->
                            cont.resume(billingResult) {}
                        }
                    }
                }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    // product fetch
    suspend fun fetchProducts() {
        val client = billingClient ?: return
        if (_state.value !is State.Ready) return

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                PRODUCT_IDS.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            )
            .build()

        val result = withContext(Dispatchers.IO) {
            suspendCancellableCoroutine<Pair<BillingResult, QueryProductDetailsResult>> { cont ->
                client.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
                    cont.resume(Pair(billingResult, queryProductDetailsResult)) {}
                }
            }
        }

        if (result.first.responseCode == BillingClient.BillingResponseCode.OK) {
            _products.value = result.second.productDetailsList
                .sortedBy { it.oneTimePurchaseOfferDetails?.priceAmountMicros ?: Long.MAX_VALUE }
        } else {
            _state.value = State.Error("Could not load donation tiers")
        }
    }

    // purchase
    fun launchPurchase(activity: Activity, product: ProductDetails): BillingResult {
        val client = billingClient ?: return BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
            .build()

        val productList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)
                .build()
        )
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productList)
            .build()

        return client.launchBillingFlow(activity, flowParams)
    }
}
