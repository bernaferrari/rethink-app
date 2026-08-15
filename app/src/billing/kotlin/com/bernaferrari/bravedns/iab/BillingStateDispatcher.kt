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
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingResult
import com.bernaferrari.bravedns.rpnproxy.SubscriptionStateMachineV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Observes [SubscriptionStateMachineV2] and pushes purchase / error LiveData for the UI.
 */
internal class BillingStateDispatcher(
    private val scope: CoroutineScope,
    private val subscriptionStateMachine: SubscriptionStateMachineV2,
    private val purchasesLiveData: MutableLiveData<List<PurchaseDetail>>,
    private val transactionErrorLiveData: MutableLiveData<BillingResult?>
) {

    private fun logd(methodName: String, msg: String) = BillingHandlerLog.d(methodName, msg)
    private fun loge(methodName: String, msg: String, e: Exception? = null) =
        BillingHandlerLog.e(methodName, msg, e)

    fun startStateObserver() {
        scope.launch {
            try {
                subscriptionStateMachine.currentState.collect { state ->
                    logd("StateObserver", "Subscription state changed to: ${state.name}")
                    handleStateChange(state)
                    updateUIForState(state)
                }
            } catch (e: IllegalStateException) {
                loge("StateObserver", "Critical error in state observer: ${e.message}", e)
                try {
                    subscriptionStateMachine.systemCheck()
                } catch (recoveryError: IllegalStateException) {
                    loge("StateObserver", "Failed system check during recovery: ${recoveryError.message}", recoveryError)
                }
            }
        }
    }

    /**
     * Updates all billing-related LiveData based on the current subscription [state].
     *
     * Design:
     * - **Active / Cancelled**: the user has (or recently had) a
     *   valid subscription: post current purchase details so the UI can show subscription
     *   info. For Active also clear any stale transaction error LiveData.
     * - **PurchasePending / PurchaseInitiated**: purchase is in flight, keep whatever the
     *   UI currently shows; do not clear or update mid-flight.
     * - **Expired / Revoked / Error / Initial / Uninitialized**: no valid purchase, post
     *   an empty list so the UI clears previously shown subscription details.
     *
     * The [state] parameter is the primary driver, it is never ignored.
     */
    suspend fun updateUIForState(state: SubscriptionStateMachineV2.SubscriptionState) {
        withContext(Dispatchers.Main) {
            when (state) {
                is SubscriptionStateMachineV2.SubscriptionState.Active,
                is SubscriptionStateMachineV2.SubscriptionState.Cancelled -> {
                    val purchaseDetails = buildPurchaseListFromState()
                    logd("updateUIForState", "state=${state.name} → posting ${purchaseDetails.size} purchases")
                    purchasesLiveData.postValue(purchaseDetails)
                    if (state is SubscriptionStateMachineV2.SubscriptionState.Active) {
                        transactionErrorLiveData.postValue(null)
                    }
                }

                is SubscriptionStateMachineV2.SubscriptionState.PurchasePending,
                is SubscriptionStateMachineV2.SubscriptionState.PurchaseInitiated -> {
                    logd("updateUIForState", "state=${state.name} → keeping current UI state (purchase in flight)")
                }

                is SubscriptionStateMachineV2.SubscriptionState.Expired,
                is SubscriptionStateMachineV2.SubscriptionState.Revoked,
                is SubscriptionStateMachineV2.SubscriptionState.Error,
                is SubscriptionStateMachineV2.SubscriptionState.Initial,
                is SubscriptionStateMachineV2.SubscriptionState.Uninitialized -> {
                    logd("updateUIForState", "state=${state.name} → clearing purchase list")
                    purchasesLiveData.postValue(emptyList())
                }
            }
        }
    }

    /**
     * Builds the list of [PurchaseDetail] to post to [purchasesLiveData].
     * Uses the state machine as the single source of truth, then merges in any
     * already-posted INAPP purchases that may not yet have been reconciled into the
     * state machine (e.g. first launch before the first Play billing query completes).
     */
    fun buildPurchaseListFromState(): List<PurchaseDetail> {
        val result = mutableListOf<PurchaseDetail>()
        subscriptionStateMachine.getSubscriptionData()?.purchaseDetail?.let {
            result.add(it)
        }
        val cachedInApp = purchasesLiveData.value
            ?.filter { it.productType == ProductType.INAPP }
            ?.filter { existing -> result.none { it.purchaseToken == existing.purchaseToken } }
            ?: emptyList()
        result.addAll(cachedInApp)
        return result
    }

    /**
     * Handles non-UI side-effects when the subscription state changes.
     * UI updates (LiveData) are handled by [updateUIForState] which is always
     * called immediately after this function from [startStateObserver].
     */
    fun handleStateChange(state: SubscriptionStateMachineV2.SubscriptionState) {
        val mname = "handleStateChange"
        when (state) {
            is SubscriptionStateMachineV2.SubscriptionState.Active ->
                logd(mname, "subscription is active")

            is SubscriptionStateMachineV2.SubscriptionState.Cancelled ->
                logd(mname, "subscription cancelled; still valid until billing expiry")

            is SubscriptionStateMachineV2.SubscriptionState.Expired ->
                logd(mname, "subscription expired")

            is SubscriptionStateMachineV2.SubscriptionState.Revoked ->
                loge(mname, "subscription revoked")

            is SubscriptionStateMachineV2.SubscriptionState.Error ->
                loge(mname, "state machine entered error state; Play will re-confirm on next query")

            is SubscriptionStateMachineV2.SubscriptionState.PurchasePending,
            is SubscriptionStateMachineV2.SubscriptionState.PurchaseInitiated ->
                logd(mname, "purchase in progress: ${state.name}")

            else ->
                logd(mname, "state changed to: ${state.name}")
        }
    }
}
