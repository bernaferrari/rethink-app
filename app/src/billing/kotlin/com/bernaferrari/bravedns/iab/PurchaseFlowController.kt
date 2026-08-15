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

import android.app.Activity
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.bernaferrari.bravedns.rpnproxy.SubscriptionStateMachineV2
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

/**
 * Launch Play purchase flows for subscriptions and one-time products.
 */
internal class PurchaseFlowController(
    private val storeProductDetails: CopyOnWriteArrayList<QueryProductDetail>,
    private val subscriptionStateMachine: SubscriptionStateMachineV2,
    private val billingListener: () -> BillingListener?,
    private val transactionErrorLiveData: MutableLiveData<BillingResult?>,
    private val billingClient: () -> BillingClient?,
    private val queryProductDetails: suspend () -> Unit,
    private val fetchOrEnsureCustomerIds: suspend () -> Pair<String, String>
) {

    private fun logd(methodName: String, msg: String) = BillingHandlerLog.d(methodName, msg)
    private fun logv(methodName: String, msg: String) = BillingHandlerLog.v(methodName, msg)
    private fun loge(methodName: String, msg: String, e: Exception? = null) =
        BillingHandlerLog.e(methodName, msg, e)
    private fun log(methodName: String, msg: String) = BillingHandlerLog.i(methodName, msg)

    suspend fun purchaseSubs(
        activity: Activity,
        productId: String,
        planId: String,
        forceResubscribe: Boolean = false
    ) {
        val mname = "purchaseSubs"

        if (!forceResubscribe && !subscriptionStateMachine.canMakePurchase()) {
            val currentState = subscriptionStateMachine.getCurrentState()
            loge(mname, "cannot make purchase, current state: ${currentState.name}")
            billingListener()?.purchasesResult(false, emptyList())
            return
        }

        if (!forceResubscribe) {
            try {
                subscriptionStateMachine.startPurchase()
            } catch (e: IllegalStateException) {
                loge(mname, "err starting purchase in state machine: ${e.message}", e)
                billingListener()?.purchasesResult(false, emptyList())
                return
            }
        }

        log(mname, "looking for product: $productId, plan: $planId")
        var pd = storeProductDetails.find {
            it.productDetail.productId == productId &&
            it.productDetail.planId == planId &&
            it.productDetail.productType == ProductType.SUBS
        }

        if (pd == null) {
            log(mname, "product not found in cache (size=${storeProductDetails.size}), fetching on-demand and retrying")
            try {
                withTimeoutOrNull(10_000.milliseconds) { queryProductDetails() }
            } catch (e: IllegalStateException) {
                loge(mname, "on-demand product details fetch failed: ${e.message}", e)
            }
            pd = storeProductDetails.find {
                it.productDetail.productId == productId &&
                it.productDetail.planId == planId &&
                it.productDetail.productType == ProductType.SUBS
            }
        }

        if (pd == null) {
            val errorMsg = "No product details found for productId: $productId, planId: $planId"
            loge(mname, errorMsg)

            try {
                subscriptionStateMachine.purchaseFailed(errorMsg, null)
            } catch (e: IllegalStateException) {
                loge(mname, "err notifying state machine: ${e.message}", e)
            }

            billingListener()?.purchasesResult(false, emptyList())
            return
        }

        try {
            launchFlow(
                activity = activity,
                pds = pd.productDetails,
                offerToken = pd.offerDetails?.offerToken
            )
        } catch (e: IllegalStateException) {
            val errorMsg = "Failed to launch purchase flow: ${e.message}"
            loge(mname, errorMsg, e)

            try {
                subscriptionStateMachine.purchaseFailed(errorMsg, null)
            } catch (stateMachineError: IllegalStateException) {
                loge(mname, "err notifying state machine: ${stateMachineError.message}", stateMachineError)
            }

            billingListener()?.purchasesResult(false, emptyList())
        }
    }

    suspend fun purchaseOneTime(activity: Activity, productId: String, planId: String, forceExtend: Boolean = false) {
        val mname = "purchaseOneTime"

        log(mname, "init one-time purchase product: $productId, plan: $planId, forceExtend=$forceExtend")

        if (!forceExtend && !subscriptionStateMachine.canMakePurchase()) {
            val currentState = subscriptionStateMachine.getCurrentState()
            loge(mname, "cannot make one-time purchase in state: ${currentState.name}")
            billingListener()?.purchasesResult(false, emptyList())
            return
        }

        if (!forceExtend) {
            try {
                subscriptionStateMachine.startPurchase()
            } catch (e: IllegalStateException) {
                loge(mname, "err starting one-time purchase in state machine: ${e.message}", e)
                billingListener()?.purchasesResult(false, emptyList())
                return
            }
        }

        var queryProductDetail = storeProductDetails.find {
            it.productDetail.productId == productId &&
                    it.productDetail.productType == ProductType.INAPP &&
                    it.productDetail.planId == planId
        }

        if (queryProductDetail == null) {
            log(mname, "one-time product not found in cache (size=${storeProductDetails.size}), fetching on-demand and retrying")
            try {
                withTimeoutOrNull(10_000.milliseconds) { queryProductDetails() }
            } catch (e: IllegalStateException) {
                loge(mname, "on-demand product details fetch failed: ${e.message}", e)
            }
            queryProductDetail = storeProductDetails.find {
                it.productDetail.productId == productId &&
                        it.productDetail.productType == ProductType.INAPP &&
                        it.productDetail.planId == planId
            }
        }

        if (queryProductDetail == null) {
            val errorMsg = "No one-time product details found for productId: $productId, planId: $planId"
            loge(mname, errorMsg)
            try { subscriptionStateMachine.purchaseFailed(errorMsg, null) } catch (ignored: IllegalStateException) {
                loge(mname, "purchaseFailed notify failed: ${ignored.message}", ignored)
            }
            billingListener()?.purchasesResult(false, emptyList())
            return
        }

        try {
            val offerToken = queryProductDetail.oneTimeOfferDetails?.offerToken
            launchFlow(activity = activity, pds = queryProductDetail.productDetails, offerToken = offerToken)
            logv(mname, "one-time purchase flow launched for productId: $productId, offerToken: $offerToken")
        } catch (e: IllegalStateException) {
            val errorMsg = "Failed to launch one-time purchase flow: ${e.message}"
            loge(mname, errorMsg, e)
            try { subscriptionStateMachine.purchaseFailed(errorMsg, null) } catch (ignored: IllegalStateException) {
                loge(mname, "purchaseFailed notify failed: ${ignored.message}", ignored)
            }
            billingListener()?.purchasesResult(false, emptyList())
        }
    }

    private suspend fun abortPurchaseLaunch(mname: String, errorMsg: String, e: Exception? = null) {
        loge(mname, errorMsg, e)
        try {
            subscriptionStateMachine.purchaseFailed(errorMsg, null)
        } catch (ignored: IllegalStateException) {
            loge(mname, "purchaseFailed notify failed: ${ignored.message}", ignored)
        }
        billingListener()?.purchasesResult(false, emptyList())
    }

    private suspend fun launchFlow(
        activity: Activity,
        pds: ProductDetails?,
        offerToken: String?
    ) {
        val mname = "launchFlow"

        if (pds == null) {
            abortPurchaseLaunch(mname, "No product details found")
            return
        }

        logd(mname, "launching purchase flow for: ${pds.title}, ${pds.productId}, offerToken: $offerToken, ${pds.productType}, ${pds.oneTimePurchaseOfferDetailsList}")

        val ids = try {
            fetchOrEnsureCustomerIds()
        } catch (e: java.io.IOException) {
            abortPurchaseLaunch(mname, "ID resolution error: ${e.message}", e)
            return
        } catch (e: IllegalStateException) {
            abortPurchaseLaunch(mname, "ID resolution error: ${e.message}", e)
            return
        }
        val accountId = ids.first
        val resolvedDeviceId = ids.second

        if (accountId.isBlank()) {
            abortPurchaseLaunch(mname, "accountId unavailable")
            return
        }
        if (resolvedDeviceId.isBlank()) {
            abortPurchaseLaunch(mname, "deviceId unavailable")
            return
        }

        logd(mname, "accountId ready (len=${accountId.length}), deviceId ready (len=${resolvedDeviceId.length})")

        val paramsList = when (offerToken.isNullOrEmpty()) {
            true -> listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(pds).build()
            )
            false -> listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(pds).setOfferToken(offerToken).build()
            )
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(paramsList)
            .setObfuscatedAccountId(accountId)
            .build()

        val client = billingClient()
        if (client == null || !client.isReady) {
            abortPurchaseLaunch(mname, "Billing client disconnected before launch")
            return
        }

        val billingResult = client.launchBillingFlow(activity, flowParams)
        val isSuccess = billingResult.responseCode == BillingResponseCode.OK

        billingListener()?.purchasesResult(isSuccess, emptyList())

        if (!isSuccess) {
            loge(mname, "err launch billing flow: ${billingResult.responseCode}")
            transactionErrorLiveData.postValue(billingResult)
        }
    }
}
