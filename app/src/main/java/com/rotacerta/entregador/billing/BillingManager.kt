package com.rotacerta.entregador.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Controla a assinatura mensal "Flex Pro" (o único jeito de pagar do app — sem compra
 * vitalícia, sem anúncio). Depois dos 10 dias de teste grátis (ver [TrialManager]), o app
 * exige essa assinatura ativa pra continuar usando.
 *
 * >>> TROCAR <<< pelo ID exato criado no Play Console em Monetização > Produtos de
 * assinatura. Produtos só ficam disponíveis pra teste depois do primeiro upload do app
 * (mesmo em teste interno).
 */
object BillingManager {

    // >>> TROCAR pelo ID real do produto de assinatura mensal <<<
    const val PRO_MONTHLY_PRODUCT_ID = "flex_otimizador_pro_mensal"

    private var billingClient: BillingClient? = null
    private var monthlyProductDetails: ProductDetails? = null

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro

    private val _monthlyPriceLabel = MutableStateFlow<String?>(null)
    val monthlyPriceLabel: StateFlow<String?> = _monthlyPriceLabel

    fun initialize(context: Context) {
        if (billingClient != null) return

        val purchasesListener = PurchasesUpdatedListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                purchases.forEach { handlePurchase(it) }
            }
        }

        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesListener)
            // Billing Library 8+ removeu o enablePendingPurchases() sem parâmetros;
            // agora exige declarar explicitamente para quais tipos de produto habilitar
            // (mesmo comportamento de antes, só que explícito). Assinatura não usa
            // "pending purchases" (isso é só pra compras únicas tipo boleto/PIX
            // assíncrono), mas o método precisa ser chamado do mesmo jeito.
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                    restorePurchases()
                }
            }
            override fun onBillingServiceDisconnected() {
                // A própria Billing Library tenta reconectar sozinha quando necessário.
            }
        })
    }

    private fun queryProductDetails() {
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRO_MONTHLY_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // Billing Library 8+ mudou esse callback de novo: agora vem um objeto
                // QueryProductDetailsResult (com productDetailsList + unfetchedProductList),
                // não mais a lista direto como era até a v7.
                val details = result.productDetailsList.firstOrNull { it.productId == PRO_MONTHLY_PRODUCT_ID }
                monthlyProductDetails = details
                _monthlyPriceLabel.value = details?.subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    ?.formattedPrice
            }
        }
    }

    /** Abre o fluxo de assinatura nativo do Google Play. */
    fun launchPurchase(activity: Activity) {
        val details = monthlyProductDetails ?: return
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return

        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()
        billingClient?.launchBillingFlow(activity, flowParams)
    }

    /** Restaura a assinatura automaticamente ao abrir o app (se já tiver uma ativa). */
    private fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        billingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        val isProProduct = purchase.products.any { it == PRO_MONTHLY_PRODUCT_ID }
        if (isProProduct && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            _isPro.value = true
            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient?.acknowledgePurchase(ackParams) { }
            }
        }
    }
}
