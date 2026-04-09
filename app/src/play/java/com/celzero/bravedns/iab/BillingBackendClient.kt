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
package com.celzero.bravedns.iab

import Logger
import Logger.LOG_IAB
import android.os.Build
import com.android.billingclient.api.BillingClient.ProductType
import com.celzero.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.celzero.bravedns.customdownloader.IBillingServerApi
import com.celzero.bravedns.customdownloader.IBillingServerApiTest
import com.celzero.bravedns.customdownloader.SafeResponseConverterFactory
import com.celzero.bravedns.customdownloader.RetrofitManager
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import com.celzero.bravedns.service.PersistentState
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.Instant

/**
 * Typed result for [BillingBackendClient.fetchAndStoreCustomerFromServer].
 *
 * @param accountId   Server-assigned account ID, blank on error.
 * @param deviceId    Server-assigned device ID, blank on error.
 * @param errorCode   0 = success; non-zero = HTTP error code (401, 409, …).
 */
data class CustomerFetchResult(
    val accountId: String,
    val deviceId: String,
    val errorCode: Int = 0
) {
    val isSuccess: Boolean get() = errorCode == 0 && accountId.isNotBlank() && deviceId.isNotBlank()

    companion object {
        val EMPTY = CustomerFetchResult("", "", 0)
        fun error(code: Int) = CustomerFetchResult("", "", code)
    }
}

/**
 * Typed result for [BillingBackendClient.registerDevice].
 *
 * Callers pattern-match on this to decide whether to surface a UI error.
 */
sealed class RegisterDeviceResult {
    /** HTTP 2xx — device registered successfully. */
    object Success : RegisterDeviceResult()

    /**
     * HTTP 401 — the server refused to authorize this device.
     * Carry the IDs that were used so the caller can show them in the error UI.
     */
    data class Unauthorized(val accountId: String, val deviceId: String) : RegisterDeviceResult()

    /** HTTP 409 — device already registered (conflict). */
    object Conflict : RegisterDeviceResult()

    /** Any other non-2xx response or network exception. */
    data class Failure(val httpCode: Int, val message: String? = null) : RegisterDeviceResult()

    val isSuccess: Boolean get() = this is Success
}

/**
 * Pure data/service layer for all Rethink backend (non-Play) billing API calls.
 *
 * This class owns every HTTP interaction with the `/customer`, `/g/device`,
 * `/g/ack`, `/g/stop`, `/g/refund`, and `/g/consume` endpoints.
 *
 * ### Responsibilities
 * - Build and dispatch all [IBillingServerApi] requests.
 * - Read accountId / deviceId from [SecureIdentityStore] (encrypted file).
 * - Construct meta JSON objects for registration calls.
 * - Resolve the `test` query-param via [RpnProxyManager].
 * - Parse and surface [RpnPurchaseAckServerResponse].
 *
 * ### Non-responsibilities
 * - No Android UI, LiveData, or Activity dependencies.
 * - No Google Play BillingClient interactions.
 * - No state-machine transitions (callers handle those).
 *
 * ### Flavors
 * Used only by `play` and `website` source sets.  `fdroid` does not depend on
 * this class.
 */
class BillingBackendClient(
    private val identityStore: SecureIdentityStore
) : KoinComponent {

    private val persistentState by inject<PersistentState>()

    companion object {
        private const val TAG = "BillingBackendClient"

        /**
         * Back-off delays (ms) applied **between** consecutive acknowledgePurchase attempts.
         *
         * Attempt schedule:
         *   1st — immediate
         *   2nd — after 300 ms
         *   3rd — after 1 200 ms
         *   4th — after 5 000 ms  → fail if still unsuccessful
         */
        private val ACK_RETRY_DELAYS = longArrayOf(300L, 1_200L, 5_000L)
    }

    /**
     * Returns a server-driven **account** identifier.
     *
     * Resolution order:
     * 1. In-memory cache — fastest path, no I/O.
     * 2. [SecureIdentityStore] (AES-256-GCM encrypted file) — survived process restart.
     * 3. `POST /customer` API call — server assigns / confirms IDs; persisted on success.
     *
     * Returns an empty string if all sources fail. Callers must treat a blank return
     * as "IDs not yet available" and abort or retry rather than proceeding with empty IDs.
     */
    suspend fun getAccountId(): String {
        val mname = this::getAccountId.name
        val (storedAcc, _) = identityStore.get()
        if (!storedAcc.isNullOrBlank()) {
            Logger.v(LOG_IAB, "$TAG $mname: loaded from SecureIdentityStore (len=${storedAcc.length})")
            return storedAcc
        }
        val result = fetchAndStoreCustomerFromServer("", "")
        if (result.accountId.isNotBlank()) {
            Logger.i(LOG_IAB, "$TAG $mname: received from server (len=${result.accountId.length})")
            return result.accountId
        }
        Logger.w(LOG_IAB, "$TAG $mname: /acc unavailable; returning blank")
        return ""
    }

    /**
     * Returns a server-driven **device** identifier.
     *
     * Same resolution order as [getAccountId]:
     * cache → encrypted file → `/customer`.
     *
     * reconciles in case recvCid is different from stored cid
     * Returns an empty string if all sources fail. Callers must treat a blank return
     * as "IDs not yet available" and abort or retry rather than proceeding with empty IDs.
     */
    suspend fun getDeviceId(recvCid: String = ""): String {
        val mname = this::getDeviceId.name
        val (storedCid, storedDid) = identityStore.get()
        if (recvCid.isNotEmpty() && storedCid != recvCid) {
            val result = fetchAndStoreCustomerFromServer(recvCid, "")
            if (result.deviceId.isNotBlank()) {
                Logger.i(LOG_IAB, "$TAG $mname: received from server (len=${result.deviceId.length})")
                return result.deviceId
            }
        }
        if (!storedDid.isNullOrBlank()) {
            Logger.v(LOG_IAB, "$TAG $mname: loaded from SecureIdentityStore (len=${storedDid.length})")
            return storedDid
        }
        val result = fetchAndStoreCustomerFromServer(storedCid ?: "", "")
        if (result.deviceId.isNotBlank()) {
            Logger.i(LOG_IAB, "$TAG $mname: received from server (len=${result.deviceId.length})")
            return result.deviceId
        }
        Logger.w(LOG_IAB, "$TAG $mname: /acc unavailable; returning blank")
        return ""
    }

    /**
     * Returns the current (accountId, deviceId) pair.
     *
     * Resolution order:
     * 1. [SecureIdentityStore] (encrypted file) — primary source.
     * 2. `POST /customer` API — obtains fresh IDs; persists to [SecureIdentityStore].
     *
     * Returns `Pair("", "")` when both sources fail. Callers must check for blank IDs
     * and abort rather than proceeding with an empty identity.
     */
    suspend fun resolveIdentity(): Pair<String, String> {
        val mname = this::resolveIdentity.name
        // 1. Encrypted file
        val (storedAcc, storedDev) = identityStore.get()
        if (!storedAcc.isNullOrBlank() && !storedDev.isNullOrBlank()) {
            Logger.d(LOG_IAB, "$TAG $mname: /acc loaded from SecureIdentityStore")
            return Pair(storedAcc, storedDev)
        }
        // 2. Server /customer call
        val result = fetchAndStoreCustomerFromServer(storedAcc ?: "", storedDev ?: "")
        if (result.accountId.isNotBlank() && result.deviceId.isNotBlank()) {
            Logger.i(LOG_IAB, "$TAG $mname: obtained from /acc and persisted")
            return Pair(result.accountId, result.deviceId)
        }
        Logger.w(LOG_IAB, "$TAG $mname: /acc unavailable; returning blank pair")
        return Pair("", "")
    }

    /**
     * Calls `POST /d/acc?cid=<accountId>&did=<deviceId>` with the meta JSON as body.
     *
     * [existingAccountId] and [existingDeviceId] are sent as query parameters so the
     * server can look up an existing account.  Pass empty strings for new users —
     * the server will assign fresh IDs.
     *
     * On success: persists both IDs to [SecureIdentityStore] and updates in-memory caches.
     * Idempotent: passing existing IDs lets the server confirm or correct them.
     * Retry-safe: [SecureIdentityStore] is only written on full success.
     *
     * ### Error codes surfaced in [CustomerFetchResult.errorCode]
     * - 0   → success (IDs are valid and non-blank)
     * - 401 → server refused to authorize this device / account
     * - 409 → conflict; account already exists in an incompatible state
     * - other non-zero → generic server / network error
     */
    suspend fun fetchAndStoreCustomerFromServer(
        existingAccountId: String = "",
        existingDeviceId: String = ""
    ): CustomerFetchResult = withContext(Dispatchers.IO) {
        val mname = "fetchAndStoreCustomerFromServer"
        var response: Response<JsonObject?>? = null
        try {
            val meta = buildCustomerMeta()
            Logger.v(LOG_IAB, "$TAG $mname: /acc, existing=${existingAccountId.length}, dev=${existingDeviceId.length}, meta=$meta")
            response = if (persistentState.appTestMode) {
                buildTestApi().registerCustomer(existingAccountId, existingDeviceId, test = "", meta)
            } else {
                buildProductionApi().registerCustomer(existingAccountId, existingDeviceId, meta)
            }

            return@withContext when {
                response == null -> {
                    Logger.e(LOG_IAB, "$TAG $mname: /acc returned null response")
                    CustomerFetchResult.EMPTY
                }
                response.isSuccessful -> {
                    val json = response.body()
                    val serverAcc = json?.get("cid")?.asString?.trim() ?: ""
                    val serverDev = json?.get("did")?.asString?.trim()  ?: ""
                    if (serverAcc.isBlank() || serverDev.isBlank()) {
                        Logger.w(LOG_IAB, "$TAG $mname: server returned blank id(s), ${response.body()}, err=${response.message()}")
                        return@withContext CustomerFetchResult.EMPTY
                    }
                    identityStore.save(serverAcc, serverDev)
                    Logger.i(LOG_IAB, "$TAG $mname: /acc success (accLen=${serverAcc.length}, devLen=${serverDev.length})")
                    CustomerFetchResult(serverAcc, serverDev, 0)
                }
                response.code() == 401 -> {
                    Logger.e(LOG_IAB, "$TAG $mname: 401 unauthorized, err=${response.message()}, ${response.errorBody()?.string()}")
                    CustomerFetchResult.error(401)
                }
                response.code() == 409 -> {
                    Logger.w(LOG_IAB, "$TAG $mname: 409 conflict (registration failure), err=${response.message()}, ${response.errorBody()?.string()}")
                    CustomerFetchResult.error(409)
                }
                else -> {
                    Logger.e(LOG_IAB, "$TAG $mname: failed code=${response.code()}, err=${response.message()}, ${response.errorBody()?.string()}")
                    CustomerFetchResult.error(response.code())
                }
            }
        } catch (e: UnknownHostException) { // see if any reconnect attempt need to be done for some of the exceptions
            Logger.w(LOG_IAB, "$TAG $mname: no internet; ${e.message}")
            return@withContext CustomerFetchResult.EMPTY
        } catch (e: ConnectException) {
            Logger.w(LOG_IAB, "$TAG $mname: connection failed; ${e.message}")
            return@withContext CustomerFetchResult.EMPTY
        } catch (e: SocketTimeoutException) {
            Logger.w(LOG_IAB, "$TAG $mname: timeout; ${e.message}")
            return@withContext CustomerFetchResult.EMPTY
        } catch (e: Exception) {
            Logger.e(LOG_IAB, "$TAG $mname: unexpected error; ${e.message}", e)
            return@withContext CustomerFetchResult.EMPTY
        }
    }


    /**
     * Calls `POST /g/device` to register [accountId] + [deviceId] with the server.
     *
     * @param m Optional JSON body (appVersion, productId, planId, orderId).
     * @return [RegisterDeviceResult.Success] on HTTP 2xx.
     *   [RegisterDeviceResult.Unauthorized] on HTTP 401 (device not authorized).
     *   [RegisterDeviceResult.Conflict] on HTTP 409 (device already registered).
     *   [RegisterDeviceResult.Failure] on any other non-2xx or exception.
     */
    suspend fun registerDevice(
        accountId: String,
        deviceId: String,
        m: JsonObject? = null
    ): RegisterDeviceResult = withContext(Dispatchers.IO) {
        val mname = "registerDevice"
        if (accountId.isBlank() || deviceId.isBlank()) {
            Logger.e(LOG_IAB, "$TAG $mname: blank accountId or deviceId skipping")
            return@withContext RegisterDeviceResult.Failure(-1, "blank accountId or deviceId")
        }
        val meta = m ?: buildCustomerMeta()
        try {
            val response = when (val handle = resolveApi()) {
                is ApiHandle.Production -> handle.api.registerDevice(accountId, deviceId, meta = meta)
                is ApiHandle.Test -> handle.api.registerDevice(accountId, deviceId, test = "", meta = meta)
            }
            when {
                response == null -> {
                    Logger.e(LOG_IAB, "$TAG $mname: null response")
                    RegisterDeviceResult.Failure(0, "null response")
                }
                response.isSuccessful -> {
                    Logger.i(LOG_IAB, "$TAG $mname: success " +
                        "(accLen=${accountId.length}, devLen=${deviceId.length})")
                    persistentState.deviceRegistrationTimestamp = System.currentTimeMillis()
                    RegisterDeviceResult.Success
                }
                response.code() == 401 -> {
                    Logger.e(LOG_IAB, "$TAG $mname: 401 unauthorized; accLen=${accountId.length}, devLen=${deviceId.length}, err=${response.errorBody()?.string()}")
                    RegisterDeviceResult.Unauthorized(accountId, deviceId)
                }
                response.code() == 409 -> {
                    Logger.w(LOG_IAB, "$TAG $mname: 409 (device already registered), err=${response.errorBody()?.string()}")
                    RegisterDeviceResult.Conflict
                }
                else -> {
                    Logger.e(LOG_IAB, "$TAG $mname: failed code=${response.code()}, err=${response.errorBody()?.string()}")
                    RegisterDeviceResult.Failure(response.code(), response.message())
                }
            }
        } catch (e: Exception) {
            Logger.e(LOG_IAB, "$TAG $mname: exception; ${e.message}", e)
            RegisterDeviceResult.Failure(-1, e.message)
        }
    }

    /**
     * Convenience overload that uses the device registration meta format
     * (deviceName + appVersion) — mirrors the format previously in
     * [InAppBillingHandler] before server-call extraction.
     */
    suspend fun registerDeviceWithDeviceMeta(
        accountId: String,
        deviceId:  String
    ): RegisterDeviceResult = registerDevice(accountId, deviceId, buildDeviceMeta())

    /**
     * Calls `POST /g/ack` to acknowledge a purchase server-side.
     *
     * Retries on transient failures (network errors, 5xx responses) using a fixed
     * back-off schedule before giving up:
     *
     * Hard failures — 409 Conflict, 4xx client errors, or a successfully parsed
     * server response (Ok or Err) — are returned immediately without retrying.
     *
     * @return Pair(success, developerPayload or error message).
     */
    suspend fun acknowledgePurchase(
        accountId: String,
        deviceId: String,
        purchaseToken: String,
        productType: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val mname = "acknowledgePurchase"
        Logger.d(LOG_IAB, "$TAG $mname: accLen=${accountId.length}, type=$productType")

        val pt  = URLEncoder.encode(purchaseToken, "UTF-8")
        val sku = skuForType(productType)

        // TODO: for now the deviceId is not used in /g/ack, see if its needed
        executeAckWithRetry(mname) { handle ->
            when (handle) {
                is ApiHandle.Production -> handle.api.acknowledgePurchase(accountId, deviceId, sku, pt)
                is ApiHandle.Test -> handle.api.acknowledgePurchase(accountId, deviceId, sku, pt, test = "")
            }
        }
    }

    /**
     * Core retry engine for [acknowledgePurchase].
     *
     * Executes [attempt] up to 4 times with delays of 300 ms, 1 200 ms, and 5 000 ms
     * between consecutive tries.  The [ApiHandle] is resolved **once** before the first
     * attempt so the test/production split is stable for the entire retry sequence.
     *
     * ### Retry policy
     * - **Retry**: [UnknownHostException], [ConnectException], [SocketTimeoutException],
     *   any other [Exception] (defensive catch-all), or an HTTP 5xx response.
     * - **No retry** (return immediately):
     *   - HTTP 409 — conflict; must be handled by the caller.
     *   - HTTP 4xx — permanent client error; retrying will not help.
     *   - [RpnPurchaseAckServerResponse.Ok] — success.
     *   - [RpnPurchaseAckServerResponse.Err] — server-side business error (e.g. invalid token);
     *     retrying will not change the outcome.
     *
     * @param caller   Name of the calling method, used only in log messages.
     * @param attempt  Lambda that performs one HTTP call given a resolved [ApiHandle].
     */
    private suspend fun executeAckWithRetry(
        caller: String,
        attempt: suspend (ApiHandle) -> Response<JsonObject?>?
    ): Pair<Boolean, String> {
        // Resolve the test/production split once — stable across all retries.
        val handle = resolveApi()

        // Back-off delays in ms.  The first attempt is immediate (no leading delay).
        // After attempt N fails transiently, wait ACK_RETRY_DELAYS[N] before attempt N+1.
        // When all delays are exhausted the loop exits and we return the last failure.
        val delaysMs = ACK_RETRY_DELAYS
        val maxAttempts = delaysMs.size + 1   // 4: one immediate + one per delay

        var lastFailureMsg = "No response from server"

        for (attemptIndex in 0 until maxAttempts) {
            // Wait before every attempt except the first.
            if (attemptIndex > 0) {
                val waitMs = delaysMs[attemptIndex - 1]
                Logger.i(LOG_IAB, "$TAG $caller: retry #$attemptIndex, waiting ${waitMs}ms")
                delay(waitMs)
            }

            try {
                val response = attempt(handle)

                if (response == null) {
                    lastFailureMsg = "No response from server"
                    Logger.w(LOG_IAB, "$TAG $caller: attempt ${attemptIndex + 1} null response, will retry")
                    continue   // transient retry
                }

                val httpCode = response.code()
                val url = response.raw().request.url.toString()
                Logger.d(LOG_IAB, "$TAG $caller: attempt ${attemptIndex + 1} for url: $url, code: $httpCode, err: ${response.errorBody()?.string()}")

                if (httpCode == 409) {
                    Logger.w(LOG_IAB, "$TAG $caller: 409 conflict, not retrying; ${response.errorBody()?.string()}")
                    return Pair(false, "Conflict: 409")
                }

                if (httpCode in 400..499) {
                    Logger.e(LOG_IAB, "$TAG $caller: permanent client error $httpCode not retrying; ${response.errorBody()?.string()}")
                    return Pair(false, "Client error: $httpCode")
                }

                if (httpCode >= 500) {
                    lastFailureMsg = "Server error: $httpCode"
                    Logger.w(LOG_IAB, "$TAG $caller: attempt ${attemptIndex + 1} server error $httpCode, will retry; ${response.errorBody()?.string()}")
                    continue
                }

                val bodyStr = response.body()?.toString()
                val rawForParsing = bodyStr?.takeIf { it.isNotBlank() }
                    ?: response.errorBody()?.string()
                if (DEBUG) Logger.d(LOG_IAB, "$TAG $caller: body=$rawForParsing")

                return when (val result = RpnPurchaseAckServerResponse.from(rawForParsing, httpCode)) {
                    is RpnPurchaseAckServerResponse.Ok -> {
                        Logger.d(LOG_IAB, "$TAG $caller: ack ok, hasEntitlement=${result.payload.hasEntitlement}")
                        Pair(true, result.payload.developerPayload ?: "")
                    }
                    is RpnPurchaseAckServerResponse.Err -> {
                        // Business-level error (e.g. invalid token): do not retry.
                        Logger.e(LOG_IAB, "$TAG $caller: server business error: ${result.payload.error}")
                        Pair(false, "Server error: ${result.payload.error}")
                    }
                }
            } catch (e: UnknownHostException) {
                lastFailureMsg = "No internet: ${e.message}"
                Logger.w(LOG_IAB, "$TAG $caller: attempt ${attemptIndex + 1}; no internet (${e.message}), will retry")
            } catch (e: ConnectException) {
                lastFailureMsg = "Connection failed: ${e.message}"
                Logger.w(LOG_IAB, "$TAG $caller: attempt ${attemptIndex + 1}; connect error (${e.message}), will retry")
            } catch (e: SocketTimeoutException) {
                lastFailureMsg = "Timeout: ${e.message}"
                Logger.w(LOG_IAB, "$TAG $caller: attempt ${attemptIndex + 1}; timeout (${e.message}), will retry")
            } catch (e: Exception) {
                lastFailureMsg = "Error: ${e.message}"
                Logger.e(LOG_IAB, "$TAG $caller: attempt ${attemptIndex + 1}; unexpected (${e.message}), will retry", e)
            }
        }

        Logger.e(LOG_IAB, "$TAG $caller: all $maxAttempts attempts exhausted; $lastFailureMsg")
        return Pair(false, lastFailureMsg)
    }

    /**
     * Calls `POST /g/ack` to query entitlement for an existing purchase.
     * Returns the [PurchaseDetail] updated with expiry + payload from the server,
     * or the original [purchase] unchanged on any failure.
     */
    suspend fun queryEntitlement(
        accountId: String,
        deviceId: String,
        purchase:  PurchaseDetail,
        purchaseToken: String
    ): PurchaseDetail = withContext(Dispatchers.IO) {
        val mname = "queryEntitlement"
        if (accountId.isEmpty()) {
            Logger.e(LOG_IAB, "$TAG $mname: empty accountId skipping")
            return@withContext purchase
        }
        try {
            val encodedPt = URLEncoder.encode(purchaseToken, "UTF-8")
            val sku = skuForType(purchase.productType)
            val response = when (val handle = resolveApi()) {
                is ApiHandle.Production -> handle.api.acknowledgePurchase(accountId, deviceId, sku, encodedPt)
                is ApiHandle.Test -> handle.api.acknowledgePurchase(accountId, deviceId, sku, encodedPt, test = "")
            }
            Logger.d(LOG_IAB, "$TAG $mname: code=${response?.code()}")
            if (response == null) return@withContext purchase
            val bodyStr = response.body()?.toString() ?: response.errorBody()?.string()
            when (val result = RpnPurchaseAckServerResponse.from(bodyStr, response.code())) {
                is RpnPurchaseAckServerResponse.Ok  -> {
                    Logger.d(LOG_IAB, "$TAG $mname: ok, hasEntitlement=${result.payload.hasEntitlement}")
                    purchase.copy(
                        payload     = result.payload.developerPayload ?: "",
                        expiryTime  = parseIsoToEpochMillis(result.payload.expiry),
                        windowDays  = result.payload.windowDays ?: 0
                    )
                }
                is RpnPurchaseAckServerResponse.Err -> {
                    Logger.e(LOG_IAB, "$TAG $mname: error, ${result.payload}")
                    purchase.copy(
                        expiryTime = parseIsoToEpochMillis(result.payload.expiry),
                        windowDays = result.payload.windowDays ?: 0
                    )
                }
            }
        } catch (e: Exception) {
            Logger.e(LOG_IAB, "$TAG $mname: err  ${e.message}", e)
            purchase
        }
    }

    /**
     * Calls `POST /g/stop` to cancel a subscription or one-time purchase.
     *
     * @return Pair(success, message). On 409 returns (false, "Conflict: 409").
     */
    suspend fun cancelPurchase(
        accountId: String,
        deviceId: String,
        sku: String,
        purchaseToken: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val mname = "cancelPurchase"
        try {
            val response = when (val handle = resolveApi()) {
                is ApiHandle.Production -> handle.api.cancelPurchase(accountId, deviceId, sku, purchaseToken)
                is ApiHandle.Test       -> handle.api.cancelSubscription(accountId, deviceId, sku, purchaseToken, test = "")
            } ?: return@withContext Pair(false, "No response")
            when {
                response.code() == 409 -> Pair(false, "Conflict: 409")
                response.isSuccessful  -> {
                    Logger.i(LOG_IAB, "$TAG $mname: cancelled sku=$sku")
                    Pair(true, "Cancelled")
                }
                else -> {
                    Logger.e(LOG_IAB, "$TAG $mname: error code=${response.code()}")
                    Pair(false, "Server error: ${response.code()}")
                }
            }
        } catch (e: Exception) {
            Logger.e(LOG_IAB, "$TAG $mname: err ${e.message}", e)
            Pair(false, "Exception: ${e.message}")
        }
    }

    /**
     * Calls `POST /g/refund` to revoke/refund a purchase.
     *
     * @return Pair(success, message). On 409 returns (false, "Conflict: 409").
     */
    suspend fun revokePurchase(
        accountId: String,
        deviceId: String,
        sku: String,
        purchaseToken: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val mname = "revokePurchase"
        try {
            val response = when (val handle = resolveApi()) {
                is ApiHandle.Production -> handle.api.revokeSubscription(accountId, deviceId, sku, purchaseToken)
                is ApiHandle.Test       -> handle.api.revokeSubscription(accountId, deviceId, sku, purchaseToken, test = "")
            } ?: return@withContext Pair(false, "No response")
            when {
                response.code() == 409 -> Pair(false, "Conflict: 409")
                response.isSuccessful  -> {
                    Logger.i(LOG_IAB, "$TAG $mname: revoked sku=$sku, raw: ${response.raw().body}")
                    Pair(true, "Revoked")
                }
                else -> {
                    Logger.e(LOG_IAB, "$TAG $mname: err code=${response.code()}, " +
                        "url=${response.raw().request.url}")
                    Pair(false, "server err: ${response.code()}, ${response.errorBody()?.string()}")
                }
            }
        } catch (e: Exception) {
            Logger.e(LOG_IAB, "$TAG $mname: err ${e.message}", e)
            Pair(false, "err: ${e.message}")
        }
    }

    /**
     * Calls `POST /g/consume` to consume an expired one-time purchase server-side.
     *
     * Idempotent: "already consumed" 409 responses are treated as success.
     *
     * @return true on 2xx or idempotent 409; false on any other failure.
     */
    suspend fun consumePurchase(
        accountId: String,
        deviceId: String,
        sku: String,
        purchaseToken: String
    ): Boolean = withContext(Dispatchers.IO) {
        val mname = "consumePurchase"
        if (accountId.isBlank()) {
            Logger.e(LOG_IAB, "$TAG $mname: blank accountId skipping")
            return@withContext false
        }
        try {
            val encodedToken = URLEncoder.encode(purchaseToken, "UTF-8")
            val response = when (val handle = resolveApi()) {
                is ApiHandle.Production -> handle.api.consumePurchase(accountId, deviceId, sku, encodedToken)
                is ApiHandle.Test       -> handle.api.consumePurchase(accountId, deviceId, sku, encodedToken, test = "")
            }
            if (response == null) {
                Logger.e(LOG_IAB, "$TAG $mname: null response")
                return@withContext false
            }
            val bodyStr  = response.body()?.toString() ?: response.errorBody()?.string()
            val httpCode = response.code()
            when {
                response.isSuccessful -> {
                    Logger.i(LOG_IAB, "$TAG $mname: consumed sku=$sku body=$bodyStr")
                    true
                }
                httpCode == 409 && bodyStr?.contains("already consumed", ignoreCase = true) == true -> {
                    Logger.i(LOG_IAB, "$TAG $mname: idempotent already consumed sku=$sku")
                    true
                }
                httpCode == 409 -> {
                    Logger.w(LOG_IAB, "$TAG $mname: 409 conflict sku=$sku body=$bodyStr")
                    false
                }
                httpCode in 400..499 -> {
                    Logger.e(LOG_IAB, "$TAG $mname: permanent client error $httpCode sku=$sku")
                    false
                }
                else -> {
                    Logger.w(LOG_IAB, "$TAG $mname: transient server error $httpCode sku=$sku")
                    false
                }
            }
        } catch (e: Exception) {
            Logger.e(LOG_IAB, "$TAG $mname: err ${e.message}", e)
            false
        }
    }

    /**
     * Builds the device-registration meta JSON (deviceName + appVersion).
     */
    fun buildDeviceMeta(prodId: String = ""): JsonObject {
        val meta = JsonObject()
        meta.addProperty("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
        meta.addProperty("appVersion", persistentState.appVersion)
        meta.addProperty("purchasedProdId", prodId)
        return meta
    }

    /**
     * Builds the meta JSON body for `POST /customer`.
     *
     * The accountId and deviceId are passed as query params by [fetchAndStoreCustomerFromServer].
     * This body carries supplementary correlation data only:
     *
     * ```json
     * {
     *   "deviceId":   "<local pip device token, or null>",
     *   "appVersion": <int>
     * }
     * ```
     *
     * `deviceId` here is the **locally-generated pip device token** (not the server-issued
     * one) so the server can correlate a newly-assigned server ID with any pre-existing
     * pip token. It is sent as JSON `null` when no local token has been generated yet.
     */
    fun buildCustomerMeta(): JsonObject {
        return JsonObject().apply {
            addProperty("model", Build.MANUFACTURER + " " + Build.MODEL)
            addProperty("appVersion", persistentState.appVersion)
        }
    }

    /**
     * Discriminated union returned by [resolveApi].
     *
     * [Production] carries the standard [IBillingServerApi] instance; the `test`
     * query param must be passed as `null` for every call to ensure it is omitted
     * from the URL entirely.
     *
     * [Test] carries the [IBillingServerApiTest] instance plus the non-null test
     * string that must be forwarded as the `test` query param.
     */
    private sealed class ApiHandle {
        /** Use for production entitlements. Pass `test = null` to every endpoint. */
        data class Production(val api: IBillingServerApi) : ApiHandle()
        /** Use for test entitlements. Pass `test = testValue` to every endpoint. */
        data class Test(val api: IBillingServerApiTest, val testValue: Boolean) : ApiHandle()
    }

    /**
     * Resolves which Retrofit interface to use for the current request.
     *
     * Calls [RpnProxyManager.getIsTestEntitlement] **once** and returns:
     * - [ApiHandle.Test]       when the entitlement is a test one (non-null value).
     * - [ApiHandle.Production] when the entitlement is production (null value) or when
     *   the lookup throws (fail-safe: always prefer production on error).
     *
     * The Retrofit instances are created fresh per-call — Retrofit interface creation
     * is lightweight (no network I/O) and this avoids threading concerns around caching
     * a potentially stale test/production split.
     */
    private suspend fun resolveApi(): ApiHandle {
        val testValue = try {
            RpnProxyManager.getIsTestEntitlement()
        } catch (e: Exception) {
            Logger.w(LOG_IAB, "$TAG resolveApi: getIsTestEntitlement, ${e.message}")
            // happens when there is server calls before entitlement
            val test = persistentState.appTestMode
            Logger.w(LOG_IAB, "$TAG resolveApi: using TEST interface (testValue=$test)")
            test
        }
        return if (testValue) {
            Logger.d(LOG_IAB, "$TAG resolveApi: using TEST interface")
            ApiHandle.Test(buildTestApi(), true)
        } else {
            Logger.d(LOG_IAB, "$TAG resolveApi: using PROD interface")
            ApiHandle.Production(buildProductionApi())
        }
    }

    /**
     * Builds a Retrofit instance backed by [IBillingServerApi] (production).
     *
     * Used when [RpnProxyManager.getIsTestEntitlement] returns `null`.
     * Endpoints that accept `test` will have it omitted from the URL.
     */
    private fun buildProductionApi(): IBillingServerApi {
        return RetrofitManager
            .getTcpProxyBaseBuilder(persistentState.routeRethinkInRethink)
            .addConverterFactory(SafeResponseConverterFactory())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IBillingServerApi::class.java)
    }

    /**
     * Builds a Retrofit instance backed by [IBillingServerApiTest] (test).
     *
     * Used when [RpnProxyManager.getIsTestEntitlement] returns a non-null string.
     * All endpoints on this interface require the `test` param — the compiler
     * enforces it so it can never be accidentally omitted.
     */
    private fun buildTestApi(): IBillingServerApiTest {
        return RetrofitManager
            .getTcpProxyBaseBuilder(persistentState.routeRethinkInRethink)
            .addConverterFactory(SafeResponseConverterFactory())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IBillingServerApiTest::class.java)
    }

    private fun skuForType(productType: String): String =
        if (productType == ProductType.SUBS) InAppBillingHandler.STD_PRODUCT_ID
        else InAppBillingHandler.ONE_TIME_PRODUCT_ID

    private fun parseIsoToEpochMillis(timeIso: String?): Long {
        if (timeIso.isNullOrEmpty()) return 0L
        return try {
            Instant.parse(timeIso).toEpochMilli()
        } catch (e: Exception) {
            Logger.w(LOG_IAB, "$TAG parseIsoToEpochMillis: parse failed for '$timeIso': ${e.message}")
            0L
        }
    }
}
