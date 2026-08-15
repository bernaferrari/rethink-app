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
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryPurchasesParams
import com.bernaferrari.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.bernaferrari.bravedns.rpnproxy.SubscriptionStateMachineV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Play purchase callbacks, queryPurchases, and [handlePurchase] reconciliation.
 */
internal class PurchaseResultProcessor(
    private val scope: CoroutineScope,
    private val subscriptionStateMachine: SubscriptionStateMachineV2,
    private val storeProductDetails: CopyOnWriteArrayList<QueryProductDetail>,
    private val transactionErrorLiveData: MutableLiveData<BillingResult?>,
    private val oneTimePurchaseCompletedFlow: MutableSharedFlow<Unit>,
    private val billingClient: () -> BillingClient?,
    private val isBillingClientSetup: () -> Boolean,
    private val detailFactory: PurchaseDetailFactory,
    private val subsProcessor: () -> SubscriptionPurchaseProcessor?,
    private val oneTimeProcessor: () -> OneTimePurchaseProcessor?,
    private val queryEntitlementFromServer: suspend (String, String, PurchaseDetail) -> PurchaseDetail,
    private val getObfuscatedAccountId: suspend () -> String,
    private val getObfuscatedDeviceId: suspend () -> String,
    private val resolveDeviceId: suspend (String) -> String?,
    private val registerDevice: suspend (String, String) -> Unit,
    private val reconcileDidForCid: suspend (String) -> DidResult,
    private val handleUnauthorized401: suspend (ServerApiError.Operation, String, String) -> Unit,
    private val handleConflict409: suspend (ServerApiError.Operation, String, String, String, String, String?) -> Pair<Boolean, String>
) {

    private val emptyQueryThreshold = 3

    @Volatile private var consecutiveEmptySubsQueries = 0
    @Volatile private var consecutiveEmptyInAppQueries = 0
    private val unackedSeenCount: ConcurrentHashMap<String, Int> = ConcurrentHashMap()

    private fun logd(methodName: String, msg: String) = BillingHandlerLog.d(methodName, msg)
    private fun logv(methodName: String, msg: String) = BillingHandlerLog.v(methodName, msg)
    private fun loge(methodName: String, msg: String, e: Exception? = null) =
        BillingHandlerLog.e(methodName, msg, e)
    private fun log(methodName: String, msg: String) = BillingHandlerLog.i(methodName, msg)

    fun resetEmptyQueryCounters() {
        consecutiveEmptySubsQueries = 0
        consecutiveEmptyInAppQueries = 0
    }

    val purchasesUpdatedListener: PurchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchasesList ->
            val mname = "purchasesUpdatedListener"
            logd(mname, "Purchase update: code=${billingResult.responseCode}, msg=${billingResult.debugMessage}")

            val response = BillingResponse(billingResult.responseCode)

            scope.launch {
                try {
                    when {
                        response.isOk -> {
                            log(mname, "purchase successful, processing ${purchasesList?.size ?: 0} items")
                            val newTokens: Set<String> = purchasesList
                                ?.filter { purchase ->
                                    try { subscriptionStateMachine.isNewPurchase(purchase.purchaseToken) }
                                    catch (ignored: IllegalStateException) {
                                        loge(mname, "isNewPurchase check failed: ${ignored.message}", ignored)
                                        false
                                    }
                                }
                                ?.map { it.purchaseToken }
                                ?.toSet()
                                ?: emptySet()

                            if (newTokens.isNotEmpty()) {
                                scope.launch {
                                    try {
                                        var deviceId = resolveDeviceId(mname)
                                        purchasesList
                                            ?.filter { it.purchaseToken in newTokens }
                                            ?.forEach { purchase ->
                                                val accountId = purchase.accountIdentifiers?.obfuscatedAccountId
                                                val storedAccId = getObfuscatedAccountId()
                                                var isRegistered = false

                                                if (accountId.isNullOrBlank()) {
                                                    loge(mname, "obfuscatedAccountId missing from purchase ${purchase.purchaseToken.take(8)}; skipping registerDevice")
                                                    return@forEach
                                                }
                                                if (deviceId == null || accountId != storedAccId) {
                                                    val didResult = reconcileDidForCid(accountId)
                                                    if (didResult.errorCode == 401) {
                                                        loge(mname, "createOrRegisterDid 401 for accountId-len=${accountId.length}; showing auth error")
                                                        handleUnauthorized401(ServerApiError.Operation.CUSTOMER, accountId, "")
                                                        return@forEach
                                                    }
                                                    if (didResult.errorCode == 409) {
                                                        loge(mname, "createOrRegisterDid 409 conflict for accountId-len=${accountId.length}")
                                                        handleConflict409(ServerApiError.Operation.CUSTOMER, accountId, "", "", "", null)
                                                        return@forEach
                                                    }
                                                    deviceId = didResult.deviceId.takeIf { it.isNotBlank() }
                                                    isRegistered = true
                                                }
                                                try {
                                                    logd(mname, "registerDevice for new token=${purchase.purchaseToken.take(8)}, accountId-len=${accountId.length}, deviceId-len=${deviceId?.length}")
                                                    if (deviceId == null) {
                                                        loge(mname, "deviceId missing from purchase ${purchase.purchaseToken.take(8)}; skipping registerDevice")
                                                    } else {
                                                        if (!isRegistered) registerDevice(accountId, deviceId)
                                                    }
                                                } catch (e: java.io.IOException) {
                                                    loge(mname, "registerDevice for failed (non-fatal): ${e.message}", e)
                                                } catch (e: IllegalStateException) {
                                                    loge(mname, "registerDevice for failed (non-fatal): ${e.message}", e)
                                                }
                                            }
                                    } catch (e: java.io.IOException) {
                                        loge(mname, "registerDevice batch for failed (non-fatal): ${e.message}", e)
                                    } catch (e: IllegalStateException) {
                                        loge(mname, "registerDevice batch for failed (non-fatal): ${e.message}", e)
                                    }
                                }
                            }

                            handlePurchase(purchasesList)

                            if (purchasesList?.any { detailFactory.getProductType(it) == ProductType.INAPP } == true) {
                                oneTimePurchaseCompletedFlow.tryEmit(Unit)
                            }
                        }

                        response.isAlreadyOwned -> {
                            log(mname, "item already owned; restoring subscription")
                            purchasesList?.forEach { purchase ->
                                try {
                                    val purchaseDetail = detailFactory.createPurchaseDetailFromPurchase(purchase) ?: return@forEach
                                    subscriptionStateMachine.restoreSubscription(purchaseDetail)
                                } catch (e: IllegalStateException) {
                                    loge(mname, "err restoring purchase: ${e.message}", e)
                                }
                            }
                        }

                        response.isUserCancelled -> {
                            log(mname, "user cancelled purchase flow")
                            transactionErrorLiveData.postValue(billingResult)
                            try {
                                subscriptionStateMachine.userCancelled()
                            } catch (e: IllegalStateException) {
                                loge(mname, "err notifying cancellation: ${e.message}", e)
                            }
                        }

                        response.isTerribleFailure || response.isNonrecoverableError -> {
                            loge(mname, "fatal billing error: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                            transactionErrorLiveData.postValue(billingResult)
                            subscriptionStateMachine.purchaseFailed(
                                "Fatal error: ${billingResult.debugMessage}",
                                billingResult.responseCode
                            )
                        }

                        response.isRecoverableError -> {
                            log(mname, "recoverable billing error: ${billingResult.debugMessage}")
                            transactionErrorLiveData.postValue(billingResult)
                            subscriptionStateMachine.purchaseFailed(
                                "Recoverable error: ${billingResult.debugMessage}",
                                billingResult.responseCode
                            )
                        }

                        else -> {
                            loge(mname, "unknown billing error: ${billingResult.responseCode}")
                            transactionErrorLiveData.postValue(billingResult)
                            subscriptionStateMachine.purchaseFailed(
                                "Unknown error: ${billingResult.debugMessage}",
                                billingResult.responseCode
                            )
                        }
                    }
                } catch (e: IllegalStateException) {
                    loge(mname, "err in purchase listener: ${e.message}", e)
                    try {
                        subscriptionStateMachine.purchaseFailed("Critical error: ${e.message}", billingResult.responseCode)
                    } catch (smError: IllegalStateException) {
                        loge(mname, "failed to notify state machine: ${smError.message}", smError)
                    }
                }
            }
        }

    suspend fun handlePurchase(
        purchasesList: List<Purchase>?,
        queriedProductType: String? = null
    ) {
        val mname = "handlePurchase"
        val normalized = purchasesList?.distinctBy { it.purchaseToken } ?: emptyList()
        logd(mname, "processing ${normalized.size} play purchases (prdType=$queriedProductType)")

        if (normalized.isEmpty()) {
            if (queriedProductType == null) return

            if (queriedProductType == ProductType.SUBS) {
                consecutiveEmptySubsQueries++
                if (consecutiveEmptySubsQueries < emptyQueryThreshold) {
                    logd(mname, "SUBS, empty purchase, ignoring (consecutive=$consecutiveEmptySubsQueries/$emptyQueryThreshold)")
                    queryPurchases(queriedProductType, false)
                    return
                }
                logd(mname, "SUBS query returned empty, threshold reached ($consecutiveEmptySubsQueries/$emptyQueryThreshold), reconcile...")

                consecutiveEmptySubsQueries = 0
                subscriptionStateMachine.reconcileWithPlayBilling(
                    purchases = emptyList(),
                    queriedProductType = queriedProductType
                )
            } else {
                consecutiveEmptyInAppQueries++
                if (consecutiveEmptyInAppQueries < emptyQueryThreshold) {
                    logd(mname, "INAPP, empty purchase, ignoring (consecutive=$consecutiveEmptyInAppQueries/$emptyQueryThreshold)")
                    queryPurchases(queriedProductType, false)
                    return
                }
                logd(mname, "INAPP query returned empty, threshold reached ($consecutiveEmptyInAppQueries/$emptyQueryThreshold), expire old purchases...")

                consecutiveEmptyInAppQueries = 0

                val serverConfirmedValidTokens = mutableSetOf<String>()
                try {
                    val activeInAppRows = subscriptionStateMachine.getActiveInAppPurchase()
                    logd(mname, "INAPP empty-threshold: checking ${activeInAppRows.size} active INAPP row(s) against server")
                    if (activeInAppRows.isNotEmpty()) {
                        val deviceId = getObfuscatedDeviceId()
                        val now = System.currentTimeMillis()
                        for (sub in activeInAppRows) {
                            try {
                                if (sub.purchaseToken.isBlank()) {
                                    logd(mname, "INAPP row id=${sub.id} has blank token; fail-safe keep")
                                    continue
                                }
                                val effectiveAccountId = sub.accountId.ifEmpty { getObfuscatedAccountId() }
                                val purchaseDetailForQuery = PurchaseDetail(
                                    productId = sub.productId,
                                    planId = sub.planId,
                                    productTitle = sub.productTitle,
                                    state = sub.state,
                                    planTitle = "",
                                    purchaseToken = sub.purchaseToken,
                                    productType = ProductType.INAPP,
                                    purchaseTime = sub.purchaseTime.toString(),
                                    purchaseTimeMillis = sub.purchaseTime,
                                    isAutoRenewing = false,
                                    accountId = effectiveAccountId,
                                    deviceId = deviceId,
                                    payload = sub.developerPayload,
                                    expiryTime = sub.billingExpiry,
                                    status = sub.status,
                                    windowDays = sub.windowDays,
                                    orderId = sub.orderId
                                )
                                val updatedDetail = try {
                                    queryEntitlementFromServer(effectiveAccountId, deviceId, purchaseDetailForQuery)
                                } catch (serverEx: java.io.IOException) {
                                    loge(mname, "server entitlement check failed for token=${sub.purchaseToken.take(8)}: ${serverEx.message}", serverEx)
                                    serverConfirmedValidTokens.add(sub.purchaseToken)
                                    continue
                                }

                                if (updatedDetail.payload.isNotEmpty() && updatedDetail.payload != sub.developerPayload) {
                                    logd(mname, "INAPP token=${sub.purchaseToken.take(8)}: payload updated from server, syncing...")
                                    subscriptionStateMachine.paymentSuccessful(updatedDetail)
                                }

                                val serverAuthoritativelyExpired = updatedDetail.expiryTime == 0L &&
                                    updatedDetail.payload.isEmpty() &&
                                    sub.billingExpiry > 0L
                                if (serverAuthoritativelyExpired) {
                                    logd(mname, "INAPP token=${sub.purchaseToken.take(8)}: server authoritatively " +
                                        "confirmed expired (expiryTime=0, payload cleared); will expire")
                                } else {
                                    val tunnelExpiry: Long = com.bernaferrari.bravedns.rpnproxy.RpnProxyManager.getExpiryFromPayload(updatedDetail.payload) ?: 0L
                                    val billingKnownExpired = sub.billingExpiry > 0L &&
                                        sub.billingExpiry != Long.MAX_VALUE &&
                                        sub.billingExpiry <= now
                                    logd(mname, "INAPP entitlement for token=${sub.purchaseToken.take(8)}: " +
                                        "tunnelExpiry=$tunnelExpiry, billingExpiry=${sub.billingExpiry}, " +
                                        "now=$now, billingKnownExpired=$billingKnownExpired, did=${deviceId.take(8)}")
                                    if (tunnelExpiry > now) {
                                        logd(mname, "INAPP token=${sub.purchaseToken.take(8)} server-confirmed valid " +
                                            "(tunnelExpiry=$tunnelExpiry); skipping expire")
                                        serverConfirmedValidTokens.add(sub.purchaseToken)
                                    } else if (!billingKnownExpired) {
                                        logd(mname, "INAPP token=${sub.purchaseToken.take(8)}: tunnelExpiry expired/zero " +
                                            "but billing window not expired (billingExpiry=${sub.billingExpiry}); " +
                                            "preserving (fail-safe — internet/server issues must not expire a valid purchase)")
                                        serverConfirmedValidTokens.add(sub.purchaseToken)
                                    } else {
                                        logd(mname, "INAPP token=${sub.purchaseToken.take(8)}: tunnelExpiry=$tunnelExpiry " +
                                            "and billing=${sub.billingExpiry} both expired; will expire")
                                    }
                                }
                            } catch (e: java.io.IOException) {
                                loge(mname, "unexpected error checking INAPP entitlement for id=${sub.id}: ${e.message}", e)
                                serverConfirmedValidTokens.add(sub.purchaseToken)
                            } catch (e: IllegalStateException) {
                                loge(mname, "unexpected error checking INAPP entitlement for id=${sub.id}: ${e.message}", e)
                                serverConfirmedValidTokens.add(sub.purchaseToken)
                            }
                        }
                    }
                } catch (outerEx: android.database.SQLException) {
                    loge(mname, "error fetching active INAPP rows before expiry: ${outerEx.message}", outerEx)
                    return
                } catch (outerEx: IllegalStateException) {
                    loge(mname, "error fetching active INAPP rows before expiry: ${outerEx.message}", outerEx)
                    return
                }
                logd(mname, "INAPP empty-threshold: serverConfirmedValidTokens=${serverConfirmedValidTokens.size}")
                subscriptionStateMachine.expireStaleInAppFromDb(playTokens = serverConfirmedValidTokens)
            }

            val currentState = subscriptionStateMachine.getCurrentState()
            if (currentState == SubscriptionStateMachineV2.SubscriptionState.PurchasePending) {
                val pendingProductId = subscriptionStateMachine.getSubscriptionData()
                    ?.subscriptionStatus?.productId ?: ""
                val pendingIsInApp = SubscriptionStateMachineV2.isInAppProduct(pendingProductId)
                val pendingMatchesQueriedType = when (queriedProductType) {
                    ProductType.INAPP -> pendingIsInApp
                    ProductType.SUBS -> !pendingIsInApp && pendingProductId.isNotBlank()
                    else -> false
                }
                if (pendingMatchesQueriedType) {
                    logd(mname, "purchase pending in db but play returned 0 $queriedProductType purchases, mark failed...")
                    subscriptionStateMachine.purchaseFailed(
                        "No purchase found in Play after repeated queries",
                        null
                    )
                } else {
                    logd(mname, "PurchasePending is for a different product type than $queriedProductType " +
                        "(pendingProduct=$pendingProductId), skipping failure")
                }
            }
            return
        }

        when (queriedProductType) {
            ProductType.SUBS -> {
                logd(mname, "non-empty SUBS response, size=${purchasesList?.size}; resetting SUBS empty counter (was $consecutiveEmptySubsQueries)")
                consecutiveEmptySubsQueries = 0
            }
            ProductType.INAPP -> {
                logd(mname, "non-empty INAPP response, size=${purchasesList?.size}; resetting INAPP empty counter (was $consecutiveEmptyInAppQueries)")
                consecutiveEmptyInAppQueries = 0
            }
            else -> {
                val hasSubs = normalized.any { detailFactory.getProductType(it, null) == ProductType.SUBS }
                val hasInApp = normalized.any { detailFactory.getProductType(it, null) == ProductType.INAPP }
                if (hasSubs) {
                    logd(mname, "non-empty SUBS (inferred), size=${purchasesList?.size}; resetting SUBS empty counter (was $consecutiveEmptySubsQueries)")
                    consecutiveEmptySubsQueries = 0
                }
                if (hasInApp) {
                    logd(mname, "non-empty INAPP (inferred), size=${purchasesList?.size}; resetting INAPP empty counter (was $consecutiveEmptyInAppQueries)")
                    consecutiveEmptyInAppQueries = 0
                }
                if (!hasSubs && !hasInApp) {
                    logd(mname, "non-empty response, size=${purchasesList?.size}; resetting both empty counters")
                    consecutiveEmptySubsQueries = 0
                    consecutiveEmptyInAppQueries = 0
                }
            }
        }

        if (DEBUG) {
            purchasesList?.forEach {
                logd(
                    mname,
                    "purchase: productIds: ${it.products}}, purchaseTime: ${it.purchaseTime}, purchaseState: ${it.purchaseState}"
                )
            }
        }

        val productMetaByPlanId: Map<String, Pair<String, String>> = buildMap {
            val grouped = storeProductDetails.groupBy { it.productDetail.productId }
            grouped.forEach { (productId, entries) ->
                entries.forEach { qpd ->
                    val planId = qpd.productDetail.planId.ifBlank { qpd.offerDetails?.basePlanId ?: "" }
                    val title = qpd.productDetail.productTitle.ifBlank { qpd.productDetails.title }
                    if (planId.isNotEmpty()) put(planId, Pair(planId, title))
                }

                val basePlan = entries.firstOrNull { it.offerDetails?.offerId == null }
                    ?: entries.firstOrNull()
                if (basePlan != null) {
                    val planId = basePlan.productDetail.planId.ifBlank { basePlan.offerDetails?.basePlanId ?: "" }
                    val title = basePlan.productDetail.productTitle.ifBlank { basePlan.productDetails.title }
                    putIfAbsent(productId, Pair(planId, title))
                    put(productId, Pair(planId, title))
                }
            }
        }
        logd(mname, "productMetaByPlanId built: ${productMetaByPlanId.size} entries")
        if (DEBUG) {
            productMetaByPlanId.entries.forEach { (k, v) ->
                logd(mname, "meta[$k] → planId=${v.first}, title=${v.second}")
            }
        }

        val purchaseExpiryMap: Map<String, Long> = normalized.associate { purchase ->
            val offerDetails = detailFactory.resolveOfferDetailsForPurchase(purchase)
            val expiry = detailFactory.calculateExpiryTime(purchase, offerDetails)
            logd(mname, "expiry for ${purchase.purchaseToken.take(8)}: " +
                    "productId=${purchase.products.firstOrNull()}, " +
                    "expiry=$expiry")
            purchase.purchaseToken to expiry
        }

        val (subsPurchases, oneTimePurchases) = normalized.partition {
            detailFactory.getProductType(it, queriedProductType) == ProductType.SUBS
        }
        logd(mname, "Reconciling ${subsPurchases.size} subscription purchases")
        logd(mname, "Handling ${oneTimePurchases.size} one-time purchases")

        if (subsPurchases.isNotEmpty()) {
            consecutiveEmptySubsQueries = 0

            val (unackedSubs, ackedSubs) = subsPurchases.partition { !it.isAcknowledged }

            val subsForReconcile = ackedSubs.toMutableList()
            val subsToEscalate = mutableListOf<Purchase>()

            ackedSubs.forEach { purchase ->
                unackedSeenCount.remove(purchase.purchaseToken)
            }

            for (purchase in unackedSubs) {
                val count = unackedSeenCount.merge(purchase.purchaseToken, 1, Int::plus) ?: 1
                val escalate = count >= InAppBillingHandler.UNACK_ESCALATION_THRESHOLD
                logd(mname, "SUBS token=${purchase.purchaseToken.take(8)} unacked: " +
                        "seenCount=$count/${InAppBillingHandler.UNACK_ESCALATION_THRESHOLD}, escalate=$escalate")
                if (escalate) {
                    unackedSeenCount.remove(purchase.purchaseToken)
                    subsToEscalate.add(purchase)
                } else {
                    subsForReconcile.add(purchase)
                }
            }

            if (subsForReconcile.isNotEmpty()) {
                try {
                    subscriptionStateMachine.reconcileWithPlayBilling(
                        subsForReconcile,
                        productMetaByPlanId,
                        purchaseExpiryMap,
                        queriedProductType ?: ProductType.SUBS
                    )
                } catch (e: IllegalStateException) {
                    loge(mname, "err reconciling subscription purchases: ${e.message}", e)
                }
            }

            if (subsToEscalate.isNotEmpty()) {
                val processor = subsProcessor()
                if (processor == null) {
                    loge(mname, "subsProcessor not ready; cannot escalate ${subsToEscalate.size} SUBS token(s)")
                } else {
                    subsToEscalate.forEach { purchase ->
                        logd(mname, "SUBS token=${purchase.purchaseToken.take(8)} escalating to server ack")
                        try {
                            processor.process(purchase, shouldEscalateToServer = true)
                        } catch (e: IllegalStateException) {
                            loge(mname, "err escalating SUBS token=${purchase.purchaseToken.take(8)}: ${e.message}", e)
                        }
                    }
                }
            }
        } else if (queriedProductType == ProductType.SUBS) {
            consecutiveEmptySubsQueries++
            if (consecutiveEmptySubsQueries >= emptyQueryThreshold) {
                logd(mname, "No SUBS in purchase list despite SUBS query;" +
                    "threshold reached ($consecutiveEmptySubsQueries/$emptyQueryThreshold), " +
                    "expiring stale SUBS DB rows")
                consecutiveEmptySubsQueries = 0
                try {
                    subscriptionStateMachine.reconcileWithPlayBilling(
                        purchases = emptyList(),
                        queriedProductType = ProductType.SUBS
                    )
                } catch (e: IllegalStateException) {
                    loge(mname, "Error expiring stale SUBS rows: ${e.message}", e)
                }
            } else {
                logd(mname, "No SUBS in purchase list despite SUBS query;" +
                    "ignoring (consecutive=$consecutiveEmptySubsQueries/$emptyQueryThreshold)")
            }
        }

        if (oneTimePurchases.isNotEmpty()) {
            oneTimePurchases.forEach { purchase ->
                try {
                    processSinglePurchase(purchase)
                } catch (e: IllegalStateException) {
                    loge(mname, "err processing one-time purchase ${purchase.purchaseToken.take(8)}: ${e.message}", e)
                }
            }
            val oneTimePlayTokens = oneTimePurchases.map { it.purchaseToken }.toSet()
            try {
                subscriptionStateMachine.expireStaleInAppFromDb(oneTimePlayTokens)
            } catch (e: IllegalStateException) {
                loge(mname, "err expiring stale one-time DB rows: ${e.message}", e)
            }
        }
    }

    suspend fun processSinglePurchase(purchase: Purchase) {
        val mname = "processSinglePurchase"
        val processor = oneTimeProcessor() ?: run {
            loge(mname, "inAppProcessor not ready; setupProcessors() not called yet")
            return
        }

        if (purchase.isAcknowledged) {
            unackedSeenCount.remove(purchase.purchaseToken)
            processor.process(purchase, shouldEscalateToServer = false)
            return
        }

        val count = unackedSeenCount.merge(purchase.purchaseToken, 1, Int::plus) ?: 1
        val escalate = count >= InAppBillingHandler.UNACK_ESCALATION_THRESHOLD
        logd(mname, "INAPP token=${purchase.purchaseToken.take(8)} unacked: " +
                "seenCount=$count/${InAppBillingHandler.UNACK_ESCALATION_THRESHOLD}, escalate=$escalate")

        if (escalate) {
            unackedSeenCount.remove(purchase.purchaseToken)
        }

        processor.process(purchase, shouldEscalateToServer = escalate)
    }

    fun fetchPurchases(productType: List<String>) {
        if (!isBillingClientSetup()) {
            loge("fetchPurchases", "billing client not setup, cannot fetch purchases")
            return
        }
        scope.launch {
            val mname = "fetchPurchases"
            logv(mname, "fetching purchases for types: $productType")
            val hasInApp = productType.any { it == ProductType.INAPP }
            val hasSubs = productType.any { it == ProductType.SUBS }
            val hasBoth = productType.any { it == ProductType.INAPP } &&
                    productType.any { it == ProductType.SUBS }

            when {
                hasBoth -> queryPurchases(ProductType.INAPP, true)
                hasInApp -> queryPurchases(ProductType.INAPP, false)
                hasSubs -> queryPurchases(ProductType.SUBS, false)
                else -> {
                    loge(mname, "invalid product type for purchases fetch, $productType")
                    return@launch
                }
            }
            logv(mname, "purchases fetch complete")
        }
    }

    fun queryPurchases(pt: String, hasBoth: Boolean) {
        val client = billingClient()
        if (client == null) {
            loge("queryPurchases", "billingClient not initialized, cannot query purchases for $pt")
            return
        }
        val mname = "queryPurchases"
        log(mname, "querying purchases for type: $pt, hasBoth: $hasBoth")

        val queryPurchasesParams = QueryPurchasesParams.newBuilder().setProductType(pt).build()

        client.queryPurchasesAsync(queryPurchasesParams) { result, purchases ->
            log(mname, "query result for $pt: code: ${result.responseCode}, purchases: ${purchases.size}")
            if (DEBUG) {
                purchases.forEachIndexed { index, purchase ->
                    log(mname, "purchase ($index): $purchase")
                }
            }

            val processingJob = if (BillingResponse(result.responseCode).isOk) {
                scope.launch {
                    try {
                        logv(mname, "processing($pt) ${purchases.size} purchases")
                        handlePurchase(purchases, pt)
                    } catch (e: IllegalStateException) {
                        loge(mname, "err processing($pt) purchases: ${e.message}", e)
                    }
                }
            } else {
                loge(mname, "err in query purchases response $pt: ${result.responseCode}, ${result.debugMessage}")
                null
            }

            if (pt == ProductType.INAPP && hasBoth) {
                if (processingJob != null) {
                    processingJob.invokeOnCompletion { queryPurchases(ProductType.SUBS, false) }
                } else {
                    queryPurchases(ProductType.SUBS, false)
                }
            }
        }
    }
}
