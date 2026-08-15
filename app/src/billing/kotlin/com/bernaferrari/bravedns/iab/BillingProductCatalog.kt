/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bernaferrari.bravedns.iab

import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.bernaferrari.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.bernaferrari.bravedns.iab.InAppBillingHandler.RecurringMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Product-detail queries and the in-memory Play catalog cache.
 *
 * Owns [productCacheMutex] (not the connection mutex).
 */
internal class BillingProductCatalog(
    private val productDetails: CopyOnWriteArrayList<ProductDetail>,
    private val storeProductDetails: CopyOnWriteArrayList<QueryProductDetail>,
    private val productDetailsLiveData: MutableLiveData<List<ProductDetail>>,
    private val billingListener: () -> BillingListener?,
    private val billingClient: () -> BillingClient?,
    private val queryUtils: () -> QueryUtils,
    private val getOneTimeBillingPeriod: (String) -> String
) {

    private val productCacheMutex = Mutex()

    private fun logd(methodName: String, msg: String) = BillingHandlerLog.d(methodName, msg)
    private fun logv(methodName: String, msg: String) = BillingHandlerLog.v(methodName, msg)
    private fun loge(methodName: String, msg: String, e: Exception? = null) =
        BillingHandlerLog.e(methodName, msg, e)
    private fun log(methodName: String, msg: String) = BillingHandlerLog.i(methodName, msg)

    fun clearCaches() {
        storeProductDetails.clear()
        productDetails.clear()
    }

    suspend fun queryProductDetailsWithTimeout(timeout: Long = 10_000) {
        val mname = "queryProductDetailsWithTimeout"
        logv(mname, "querying product details with timeout: ${timeout}ms")

        if (productDetails.isNotEmpty()) {
            logd(mname, "product details cached (${productDetails.size} items), notifying listener")
            val cached = productDetails.toList()
            withContext(Dispatchers.Main) {
                productDetailsLiveData.value = cached
                billingListener()?.productResult(true, cached)
            }
            return
        }

        val result = withTimeoutOrNull(timeout) {
            queryProductDetails()
        }

        if (result == null) {
            loge(mname, "product details query timed out after ${timeout}ms")
            withContext(Dispatchers.Main) {
                billingListener()?.productResult(false, emptyList())
            }
        }
    }

    suspend fun queryProductDetails() {
        val mname = "queryProductDetails"
        val client = billingClient() ?: run {
            loge(mname, "billing client not initialized")
            return
        }
        productCacheMutex.withLock {
            storeProductDetails.clear()
            productDetails.clear()

            val inAppResult = kotlinx.coroutines.CompletableDeferred<List<ProductDetails>>()
            val subsResult = kotlinx.coroutines.CompletableDeferred<List<ProductDetails>>()

        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(InAppBillingHandler.ONE_TIME_PRODUCT_ID)
                        .setProductType(ProductType.INAPP)
                        .build()
                )
            ).build()

        logd(mname, "launching INAPP product query")
        client.queryProductDetailsAsync(inAppParams) { br, result ->
            logd(mname, "INAPP result: code=${br.responseCode}, items=${result.productDetailsList.size}")
            if (br.responseCode == BillingResponseCode.OK) {
                inAppResult.complete(result.productDetailsList)
            } else {
                loge(mname, "INAPP query failed: ${br.responseCode}, ${br.debugMessage}")
                inAppResult.complete(emptyList())
            }
        }

        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(InAppBillingHandler.STD_PRODUCT_ID)
                        .setProductType(ProductType.SUBS)
                        .build()
                )
            ).build()

        logd(mname, "launching SUBS product query")
        client.queryProductDetailsAsync(subsParams) { br, result ->
            logd(mname, "SUBS result: code=${br.responseCode}, items=${result.productDetailsList.size}")
            if (br.responseCode == BillingResponseCode.OK) {
                subsResult.complete(result.productDetailsList)
            } else {
                loge(mname, "SUBS query failed: ${br.responseCode}, ${br.debugMessage}")
                subsResult.complete(emptyList())
            }
        }

        val inAppList = inAppResult.await()
        val subsList = subsResult.await()

        if (inAppList.isNotEmpty()) processProductList(inAppList)
        if (subsList.isNotEmpty()) processProductList(subsList)

        val merged = productDetails.toList()
        logd(mname, "product query complete: ${merged.size} total products (inApp=${inAppList.size}, subs=${subsList.size})")
        withContext(Dispatchers.Main) {
            productDetailsLiveData.postValue(merged)
            billingListener()?.productResult(merged.isNotEmpty(), merged)
        }
        }
    }

    fun processProductList(pds: List<ProductDetails>) {
        val mname = "processProductList"
        val queryProductDetails = arrayListOf<QueryProductDetail>()
        logd(mname, "product details size: ${pds.size}, $pds")
        pds.forEach { pd ->
            logd(mname, "product detail: $pd")

            when (pd.productType) {
                ProductType.INAPP -> {
                    val offers = pd.oneTimePurchaseOfferDetailsList.orEmpty()
                    if (offers.isEmpty()) {
                        loge(mname, "INAPP product has no one-time offers: ${pd.productId}")
                        return@forEach
                    }

                    offers.forEachIndexed { _, offer ->
                        val billingPeriod = getOneTimeBillingPeriod(offer.purchaseOptionId ?: offer.offerId ?: pd.productId)
                        val planTitle = QueryUtils.getPlanTitle(billingPeriod)
                        val planId = offer.purchaseOptionId ?: offer.offerId ?: pd.productId

                        val pricingPhase = PricingPhase(
                            planTitle = planTitle,
                            recurringMode = RecurringMode.ORIGINAL,
                            price = offer.formattedPrice.removeSuffix(".00"),
                            currencyCode = offer.priceCurrencyCode,
                            billingCycleCount = 0,
                            billingPeriod = billingPeriod,
                            priceAmountMicros = offer.priceAmountMicros,
                            freeTrialPeriod = 0
                        )

                        val productDetail = ProductDetail(
                            productId = pd.productId,
                            planId = planId,
                            productTitle = planTitle,
                            productType = ProductType.INAPP,
                            pricingDetails = listOf(pricingPhase)
                        )
                        this.productDetails.add(productDetail)
                        queryProductDetails.add(QueryProductDetail(productDetail, pd, null, offer))
                    }
                }

                ProductType.SUBS -> {
                    pd.subscriptionOfferDetails?.let { offersList ->
                        log(mname, "subs; offersList: ${offersList.size}")
                        offersList.forEach tag@{ offer ->
                            log(
                                mname,
                                "offer: ${offer.basePlanId}, ${offer.offerId}, ${offer.pricingPhases}"
                            )
                            val isExist =
                                this.productDetails.any { it.productId == pd.productId && it.planId == offer.basePlanId }
                            val isExistInStore =
                                storeProductDetails.any { it.productDetail.productId == pd.productId && it.productDetail.planId == offer.basePlanId }
                            logd(
                                mname,
                                "exist? $isExist, $isExistInStore, for $pd, ${offer.basePlanId}, ${pd.productId}"
                            )
                            if (isExist && isExistInStore) {
                                logd(
                                    mname,
                                    "exist: ${storeProductDetails.size}, ${this.productDetails.size} $pd, ${offer.basePlanId}, ${pd.productId}"
                                )
                                loge(mname, "product already exists, skipping")
                                return@tag
                            }

                            val pricingPhaseList = arrayListOf<PricingPhase>()

                            offer.pricingPhases.pricingPhaseList.forEach { pricingPhaseItem ->
                                val pricingPhase = PricingPhase()
                                if (pricingPhaseItem.formattedPrice.equals(
                                        "Free",
                                        ignoreCase = true
                                    )
                                ) {
                                    pricingPhase.recurringMode = RecurringMode.FREE
                                    pricingPhase.freeTrialPeriod = queryUtils().getTrialDay(offer)
                                    pricingPhase.billingCycleCount = 0
                                } else if (pricingPhaseItem.recurrenceMode == 1) {
                                    pricingPhase.recurringMode = RecurringMode.ORIGINAL
                                    pricingPhase.freeTrialPeriod = 0
                                    pricingPhase.billingCycleCount = 0
                                } else if (pricingPhaseItem.recurrenceMode == 2) {
                                    pricingPhase.recurringMode = RecurringMode.DISCOUNTED
                                    pricingPhase.freeTrialPeriod = 0
                                    pricingPhase.billingCycleCount =
                                        pricingPhaseItem.billingCycleCount
                                }
                                pricingPhase.planTitle =
                                    QueryUtils.getPlanTitle(pricingPhaseItem.billingPeriod)
                                pricingPhase.currencyCode = pricingPhaseItem.priceCurrencyCode
                                pricingPhase.billingPeriod = pricingPhaseItem.billingPeriod
                                pricingPhase.price =
                                    pricingPhaseItem.formattedPrice.removeSuffix(".00")
                                pricingPhase.priceAmountMicros = pricingPhaseItem.priceAmountMicros
                                pricingPhaseList.add(pricingPhase)
                            }

                            val productDetail = ProductDetail().apply {
                                productId = pd.productId
                                planId = offer.basePlanId
                                productTitle = pd.title
                                productType = ProductType.SUBS
                                pricingDetails = pricingPhaseList
                            }
                            if (!isExist) {
                                this.productDetails.add(productDetail)
                            }
                            if (!isExistInStore) {
                                queryProductDetails.add(QueryProductDetail(productDetail, pd, offer, null))
                            }
                            logd(
                                mname,
                                "product added: ${productDetail.productId}, ${productDetail.planId}, ${productDetail.productTitle}, ${productDetail.pricingDetails.map { it.price }}"
                            )
                        }
                    }
                }
            }
        }

        storeProductDetails.addAll(queryProductDetails)
        log(mname, "processed product details list: ${storeProductDetails.size} items")

        if (DEBUG) {
            storeProductDetails.forEach {
                log(
                    mname,
                    "storeProductDetails item: ${it.productDetail.productId}, ${it.productDetail.planId}, ${it.productDetail.pricingDetails}"
                )
            }

            productDetails.forEach {
                log(mname, "productDetails item: ${it.productId}, ${it.planId}, ${it.pricingDetails}")
            }
        }

        val s = storeProductDetails.distinctBy { it.productDetail.planId }
        val p = productDetails.distinctBy { it.planId }

        storeProductDetails.clear()
        storeProductDetails.addAll(s)
        productDetails.clear()
        productDetails.addAll(p)

        logd(
            mname,
            "final storeProductDetails: ${storeProductDetails.size}, productDetails: ${productDetails.size}"
        )

        productDetailsLiveData.postValue(productDetails)
    }
}
