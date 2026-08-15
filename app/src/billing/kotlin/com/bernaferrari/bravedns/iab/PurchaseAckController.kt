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

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.BillingClient.ProductType
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Server-side ack/consume/entitlement plus identity + 401/409 surfacing.
 *
 * Cancel/revoke **bodies** live here. [InAppBillingHandler] still acquires
 * [InAppBillingHandler]'s `connectionMutex` around these calls so lock ownership
 * does not move.
 */
internal class PurchaseAckController(
    private val billingBackendClient: BillingBackendClient,
    private val persistentState: () -> PersistentState,
    private val eventLogger: () -> EventLogger,
    private val serverApiErrorLiveData: MutableLiveData<ServerApiError?>,
    private val appContext: () -> Context?
) {

    private fun logd(methodName: String, msg: String) = BillingHandlerLog.d(methodName, msg)
    private fun loge(methodName: String, msg: String, e: Exception? = null) =
        BillingHandlerLog.e(methodName, msg, e)

    fun skuForType(productType: String): String =
        if (productType == ProductType.SUBS) InAppBillingHandler.STD_PRODUCT_ID
        else InAppBillingHandler.ONE_TIME_PRODUCT_ID

    suspend fun getObfuscatedAccountId(): String {
        val mname = "getObfuscatedAccountId"
        return when (val result = billingBackendClient.resolveIdentity()) {
            is RefreshIdentityResult.Success -> result.cid
            is RefreshIdentityResult.Unauthorized -> {
                loge(mname, "401 unauthorized on account ID resolution; surfacing auth error")
                handleUnauthorized401(ServerApiError.Operation.CUSTOMER, "", "")
                ""
            }
            is RefreshIdentityResult.Conflict -> {
                loge(mname, "409 conflict on account ID resolution; surfacing conflict error")
                handleConflict409(ServerApiError.Operation.CUSTOMER, "", "", "", "", null)
                ""
            }
            is RefreshIdentityResult.Failure -> {
                loge(mname, "account ID resolution failed (transient)")
                ""
            }
        }
    }

    suspend fun getObfuscatedDeviceId(): String {
        val mname = "getObfuscatedDeviceId"
        return when (val result = billingBackendClient.resolveIdentity()) {
            is RefreshIdentityResult.Success -> result.did
            is RefreshIdentityResult.Unauthorized -> {
                loge(mname, "401 unauthorized on device ID resolution; surfacing auth error")
                handleUnauthorized401(ServerApiError.Operation.CUSTOMER, "", "")
                ""
            }
            is RefreshIdentityResult.Conflict -> {
                loge(mname, "409 conflict on device ID resolution; surfacing conflict error")
                handleConflict409(ServerApiError.Operation.CUSTOMER, "", "", "", "", null)
                ""
            }
            is RefreshIdentityResult.Failure -> {
                loge(mname, "device ID resolution failed (transient)")
                ""
            }
        }
    }

    suspend fun fetchOrEnsureCustomerIds(): Pair<String, String> {
        val mname = "fetchOrEnsureCustomerIds"
        logd(mname, "resolving identity via BillingServerRepository")
        return when (val result = billingBackendClient.resolveIdentity()) {
            is RefreshIdentityResult.Success -> {
                logd(mname, "identity resolved (cidLen=${result.cid.length}, didLen=${result.did.length})")
                Pair(result.cid, result.did)
            }
            is RefreshIdentityResult.Unauthorized -> {
                loge(mname, "401 unauthorized from identity resolution; surfacing auth error")
                handleUnauthorized401(ServerApiError.Operation.CUSTOMER, "", "")
                Pair("", "")
            }
            is RefreshIdentityResult.Conflict -> {
                loge(mname, "409 conflict from identity resolution; surfacing conflict error")
                handleConflict409(ServerApiError.Operation.CUSTOMER, "", "", "", "", null)
                Pair("", "")
            }
            is RefreshIdentityResult.Failure -> {
                loge(mname, "identity resolution failed (transient)")
                Pair("", "")
            }
        }
    }

    suspend fun resolveDeviceId(caller: String): String? {
        return try {
            val id = getObfuscatedDeviceId()
            id.ifBlank {
                loge(caller, "resolveDeviceId: blank deviceId from all sources; skipping registerDevice")
                null
            }
        } catch (e: IllegalStateException) {
            loge(caller, "resolveDeviceId failed; skipping registerDevice: ${e.message}", e)
            null
        }
    }

    suspend fun handleUnauthorized401(
        operation: ServerApiError.Operation,
        accountId: String,
        deviceId: String
    ) {
        val error = ServerApiError.Unauthorized401(
            operation = operation,
            accountId = accountId,
            deviceIdPrefix = deviceId.take(6)
        )
        withContext(Dispatchers.Main) {
            val alreadyAuthErrorActive = serverApiErrorLiveData.value is ServerApiError.Unauthorized401
            serverApiErrorLiveData.value = error
            if (!alreadyAuthErrorActive && !serverApiErrorLiveData.hasActiveObservers()) {
                val ctx = appContext()
                if (ctx != null) {
                    logd("handleUnauthorized401", "posting auth-error notification")
                    DeviceAuthErrorNotifier.notify(ctx, error, persistentState().theme)
                } else {
                    loge("handleUnauthorized401", "appContext null; cannot post auth-error notification")
                }
            } else if (alreadyAuthErrorActive) {
                logd("handleUnauthorized401", "auth error already active, skipping duplicate notification")
            }
        }
        loge("handleUnauthorized401", "401 on ${operation.endpoint}: accLen=${accountId.length}")
    }

    suspend fun handleConflict409(
        operation: ServerApiError.Operation,
        accountId: String,
        deviceId: String,
        purchaseToken: String,
        sku: String,
        serverMsg: String?
    ): Pair<Boolean, String> {
        val error = ServerApiError.Conflict409(
            endpoint = operation.endpoint,
            operation = operation,
            serverMessage = serverMsg?.takeIf { it.isNotBlank() },
            accountId = accountId,
            purchaseToken = purchaseToken,
            sku = sku
        )
        withContext(Dispatchers.Main) {
            val alreadyConflictActive = serverApiErrorLiveData.value is ServerApiError.Conflict409
            serverApiErrorLiveData.value = error
            if (!alreadyConflictActive && !serverApiErrorLiveData.hasActiveObservers()) {
                val ctx = appContext()
                if (ctx != null) {
                    logd("handleConflict409", "posting conflict notification")
                    PurchaseConflictNotifier.notify(ctx, error, persistentState().theme)
                } else {
                    loge("handleConflict409", "appContext null; cannot post conflict notification")
                }
            } else if (alreadyConflictActive) {
                logd("handleConflict409", "conflict already active, skipping duplicate notification")
            }
        }
        loge("handleConflict409", "409 on ${operation.endpoint}: $serverMsg")
        return Pair(false, serverMsg ?: "Conflict: 409")
    }

    suspend fun registerDevice(
        accountId: String,
        deviceId: String,
        meta: JsonObject? = null
    ) = withContext(Dispatchers.IO) {
        val mname = "registerDevice"
        if (accountId.isBlank() || deviceId.isBlank()) {
            loge(mname, "accountId or deviceId is blank; skipping registerDevice")
            return@withContext
        }
        val effectiveMeta = meta ?: billingBackendClient.buildDeviceMeta()
        when (val result = billingBackendClient.registerDevice(accountId, deviceId, effectiveMeta)) {
            is RegisterDeviceResult.Success -> {
                logd(mname, "registerDevice success")
                logEvent(EventType.PROXY_CONNECT, Severity.LOW, "registerDevice", "registerDevice success")
            }
            is RegisterDeviceResult.Unauthorized -> {
                loge(mname, "registerDevice 401 unauthorized; surfacing DeviceAuthError to UI")
                handleUnauthorized401(ServerApiError.Operation.DEVICE, accountId, deviceId)
                logEvent(EventType.PROXY_ERROR, Severity.HIGH, "registerDevice", "registerDevice 401 unauthorized")
            }
            is RegisterDeviceResult.Conflict -> {
                loge(mname, "registerDevice 409 conflict (device already registered, non-fatal)")
                logEvent(EventType.PROXY_ERROR, Severity.HIGH, "registerDevice", "registerDevice 409 conflict")
            }
            is RegisterDeviceResult.Failure -> {
                loge(mname, "registerDevice failed: code=${result.httpCode} msg=${result.message} (non-fatal)")
                logEvent(EventType.PROXY_ERROR, Severity.HIGH, "registerDevice", "registerDevice failed: ${result.message}")
            }
        }
    }

    suspend fun queryEntitlementFromServer(
        accountId: String,
        deviceId: String,
        purchase: PurchaseDetail
    ): PurchaseDetail {
        val mname = "queryEntitlementFromServer"
        logd(mname, "delegating to BillingServerRepository, accLen=${accountId.length}")
        val pt = purchase.purchaseToken
        if (pt.isEmpty()) {
            logd(mname, "no purchase token; skipping")
            return purchase
        }

        return when (val result = billingBackendClient.queryEntitlement(accountId, deviceId, purchase, pt)) {
            is QueryEntitlementResult.Success -> result.purchase
            is QueryEntitlementResult.Unauthorized -> {
                loge(mname, "queryEntitlement 401 unauthorized for token=${pt.take(8)}; surfacing auth error")
                handleUnauthorized401(ServerApiError.Operation.ACKNOWLEDGE, result.accountId, result.deviceId)
                purchase
            }
            is QueryEntitlementResult.Conflict -> {
                loge(mname, "queryEntitlement 409 conflict for token=${pt.take(8)}")
                handleConflict409(
                    ServerApiError.Operation.ACKNOWLEDGE, accountId, deviceId,
                    pt, skuForType(purchase.productType), "Conflict: 409"
                )
                purchase
            }
            is QueryEntitlementResult.Failure -> {
                val linked = result.linkedPurchaseId
                if (!linked.isNullOrBlank()) {
                    loge(mname, "queryEntitlement server business error for token=${pt.take(8)}; linkedPurchaseId present, attempting reactivation of linkedToken=${linked.take(8)}")
                    try {
                        RpnProxyManager.tryReactivateLinkedPurchase(accountId, deviceId, linked)
                    } catch (e: IllegalStateException) {
                        loge(mname, "tryReactivateLinkedPurchase threw for linkedToken=${linked.take(8)}: ${e.message}", e)
                    }
                    result.purchase
                } else {
                    loge(mname, "queryEntitlement server business error for token=${pt.take(8)}; no linkedPurchaseId, expiring local purchase")
                    result.purchase.copy(expiryTime = 0L, payload = "")
                }
            }
            is QueryEntitlementResult.Expired -> {
                loge(mname, "queryEntitlement server confirmed subscription expired for token=${pt.take(8)}; expiring local purchase")
                result.purchase.copy(expiryTime = 0L, payload = "")
            }
            is QueryEntitlementResult.Transient -> {
                logd(mname, "queryEntitlement transient failure for token=${pt.take(8)}; preserving original purchase (fail-safe)")
                result.purchase
            }
        }
    }

    suspend fun acknowledgePurchaseFromServer(
        accountId: String,
        deviceId: String,
        purchaseToken: String,
        productType: String
    ): Pair<Boolean, String> {
        val mname = "acknowledgePurchaseFromServer"
        logd(mname, "delegating to BillingServerRepository, accLen=${accountId.length}")
        val (success, payload) = billingBackendClient.acknowledgePurchase(accountId, deviceId, purchaseToken, productType)
        if (!success && payload.startsWith("Unauthorized")) {
            loge(mname, "acknowledgePurchaseFromServer 401: acc=${accountId.take(8)}, prod=$productType, token=${purchaseToken.take(8)}")
            handleUnauthorized401(ServerApiError.Operation.ACKNOWLEDGE, accountId, deviceId)
        }
        if (!success && payload.startsWith("Conflict")) {
            loge(mname, "acknowledgePurchaseFromServer 409: acc=${accountId.take(8)}, prod=$productType, token=${purchaseToken.take(8)}")
            handleConflict409(
                ServerApiError.Operation.ACKNOWLEDGE, accountId, deviceId,
                purchaseToken, skuForType(productType), payload
            )
        }
        return Pair(success, payload)
    }

    /**
     * Shared cancel/revoke body. Caller **must** already hold `connectionMutex`.
     */
    suspend fun cancelOrRevokeUnderLock(
        mname: String,
        accountId: String,
        deviceId: String,
        purchaseToken: String,
        sku: String,
        operation: ServerApiError.Operation,
        backendCall: suspend (accountId: String, deviceId: String, sku: String, purchaseToken: String) -> Pair<Boolean, String>,
        updateLocal: suspend (accountId: String, purchaseToken: String) -> Boolean,
        notifyStateMachine: suspend () -> Unit,
        successMessage: String,
        logSuccessMessage: String,
        stateSyncFailMessage: String
    ): Pair<Boolean, String> {
        logd(mname, "delegating to BillingServerRepository, accLen=${accountId.length}")
        val (success, msg) = backendCall(accountId, deviceId, sku, purchaseToken)
        if (!success && msg.startsWith("Unauthorized")) {
            loge(mname, "$mname 401; surfacing auth error")
            handleUnauthorized401(operation, accountId, deviceId)
            return Pair(false, msg)
        }
        if (!success && msg.startsWith("Conflict")) {
            return handleConflict409(operation, accountId, deviceId, purchaseToken, sku, msg)
        }
        if (!success) return Pair(false, msg)
        val localSuccess = updateLocal(accountId, purchaseToken)
        if (!localSuccess) {
            logEvent(EventType.PROXY_ERROR, Severity.HIGH, mname, "Local state update failed")
            return Pair(false, "Local state update failed")
        } else {
            logEvent(EventType.PROXY_SWITCH, Severity.LOW, mname, "$mname success")
        }
        return try {
            notifyStateMachine()
            logd(mname, logSuccessMessage)
            Pair(true, successMessage)
        } catch (e: IllegalStateException) {
            loge(mname, "state machine update failed: ${e.message}", e)
            logEvent(EventType.PROXY_ERROR, Severity.HIGH, mname, "State machine update failed: ${e.message}")
            Pair(false, stateSyncFailMessage)
        }
    }

    suspend fun reconcileCidDidFromPurchase(
        cid: String,
        secureIdentityStore: SecureIdentityStore,
        currentEnv: () -> SecureIdentityStore.Env
    ) {
        val mname = "reconcileCidDidFromPurchase"
        if (cid.isEmpty()) {
            logd(mname, "cid empty, skipping reconcile")
            return
        }
        val env = currentEnv()
        val (storedCid, storedDid) = secureIdentityStore.get(env)
        if (storedCid == cid && storedDid?.isNotEmpty() == true) {
            BillingHandlerLog.v(mname, "no need to reconcile cid, did from purchase")
            return
        }

        logd(mname, "reconciling did for new cid=${cid.take(8)}")
        val didResult = billingBackendClient.reconcileDidForCid(cid)
        when {
            didResult.isSuccess -> {
                logd(mname, "reconcile succeeded; did persisted by BillingBackendClient (len=${didResult.deviceId.length})")
            }
            didResult.errorCode == 401 -> {
                loge(mname, "401 unauthorized on device re-registration for cid=${cid.take(8)}; surfacing auth error")
                handleUnauthorized401(ServerApiError.Operation.DEVICE, cid, "")
            }
            didResult.errorCode == 409 -> {
                loge(mname, "409 conflict on device re-registration for cid=${cid.take(8)}; surfacing conflict error")
                handleConflict409(ServerApiError.Operation.DEVICE, cid, "", "", "", "Conflict: 409")
            }
            else -> {
                loge(mname, "device re-registration failed (code=${didResult.errorCode}) for cid=${cid.take(8)}; non-fatal")
            }
        }
    }

    private fun logEvent(eventType: EventType, severity: Severity, msg: String, details: String) {
        eventLogger().log(eventType, severity, msg, EventSource.PROXY, false, details)
    }
}
