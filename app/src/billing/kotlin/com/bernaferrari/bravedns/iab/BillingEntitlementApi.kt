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

import Logger
import Logger.LOG_IAB
import android.os.Build
import com.android.billingclient.api.BillingClient.ProductType
import com.bernaferrari.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.bernaferrari.bravedns.iab.BillingApiFactory.ApiHandle
import com.bernaferrari.bravedns.service.PersistentState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.Instant

/**
 * Register / entitlement / ack (and sibling write) calls against the billing backend.
 * Request builders and the ack retry engine live here; Retrofit construction is [BillingApiFactory].
 */
internal class BillingEntitlementApi(
    private val persistentState: PersistentState,
    private val apiFactory: BillingApiFactory,
) {

    companion object {
        private const val TAG = "BillingBackendClient"
        private val ACK_RETRY_DELAYS = longArrayOf(4_000L, 11_000L)
    }

    private fun currentEnv(): SecureIdentityStore.Env =
        if (persistentState.appTestMode) SecureIdentityStore.Env.TEST
        else SecureIdentityStore.Env.PROD

    suspend fun createOrRegisterCid(existingCid: String = ""): CidResult = withContext(Dispatchers.IO) {
        val mname = "createOrRegisterCid"
        val env = currentEnv()
        try {
            val meta = buildCustomerMeta()
            val cidHeader = existingCid.takeIf { it.isNotBlank() }
            val vcode = persistentState.appVersion.toString()
            Logger.v(LOG_IAB, "$TAG $mname [${env.label}]: calling /d/acc, cidHeader=${if (cidHeader != null) "present(len=${cidHeader.length})" else "absent"}")
            val response = if (persistentState.appTestMode) {
                apiFactory.buildTestApi().registerCustomer(cidHeader, "", vcode, test = "", meta)
            } else {
                apiFactory.buildProductionApi().registerCustomer(cidHeader, "", vcode, meta)
            }
            ServerApiErrorMapper.mapCidResponse(response.toBillingHttpOrNull(), env.label, mname)
        } catch (e: UnknownHostException) {
            Logger.w(LOG_IAB, "$TAG $mname [${env.label}]: no internet; ${e.message}")
            CidResult.EMPTY
        } catch (e: ConnectException) {
            Logger.w(LOG_IAB, "$TAG $mname [${env.label}]: connection failed; ${e.message}")
            CidResult.EMPTY
        } catch (e: SocketTimeoutException) {
            Logger.w(LOG_IAB, "$TAG $mname [${env.label}]: timeout; ${e.message}")
            CidResult.EMPTY
        } catch (e: IOException) {
            Logger.e(LOG_IAB, "$TAG $mname [${env.label}]: unexpected error; ${e.message}", e)
            CidResult.EMPTY
        }
    }

    suspend fun createOrRegisterDid(
        accountId: String,
        existingDeviceId: String = ""
    ): DidResult = withContext(Dispatchers.IO) {
        val mname = "createOrRegisterDid"
        val env = currentEnv()
        if (accountId.isBlank()) {
            Logger.e(LOG_IAB, "$TAG $mname [${env.label}]: blank accountId; cannot register device without account")
            return@withContext DidResult.error(-1)
        }
        try {
            val meta = buildCustomerMeta()
            val didHeader = existingDeviceId.takeIf { it.isNotBlank() }
            val vcode = persistentState.appVersion.toString()
            Logger.v(LOG_IAB, "$TAG $mname [${env.label}]: calling /d/reg, cidLen=${accountId.length}, didHeader=${if (didHeader != null) "present(len=${didHeader.length})" else "absent"}")
            val response = if (persistentState.appTestMode) {
                apiFactory.buildTestApi().registerDevice(accountId, didHeader, vcode, test = "", meta = meta)
            } else {
                apiFactory.buildProductionApi().registerDevice(accountId, didHeader, vcode, meta = meta)
            }
            ServerApiErrorMapper.mapDidResponse(response.toBillingHttpOrNull(), env.label, mname)
        } catch (e: UnknownHostException) {
            Logger.w(LOG_IAB, "$TAG $mname [${env.label}]: no internet; ${e.message}")
            DidResult.EMPTY
        } catch (e: ConnectException) {
            Logger.w(LOG_IAB, "$TAG $mname [${env.label}]: connection failed; ${e.message}")
            DidResult.EMPTY
        } catch (e: SocketTimeoutException) {
            Logger.w(LOG_IAB, "$TAG $mname [${env.label}]: timeout; ${e.message}")
            DidResult.EMPTY
        } catch (e: IOException) {
            Logger.e(LOG_IAB, "$TAG $mname [${env.label}]: unexpected error; ${e.message}", e)
            DidResult.EMPTY
        }
    }

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
            val handle = apiFactory.resolveApi()
            val response = when (handle) {
                is ApiHandle.Production -> handle.api.registerDevice(accountId, deviceId, vcode = persistentState.appVersion.toString(), meta = meta)
                is ApiHandle.Test -> handle.api.registerDevice(accountId, deviceId, vcode = persistentState.appVersion.toString(), test = "", meta = meta)
            }
            ServerApiErrorMapper.mapRegisterDeviceResponse(
                response.toBillingHttpOrNull(), handle.envLabel, mname, accountId, deviceId
            ) {
                persistentState.deviceRegistrationTimestamp = System.currentTimeMillis()
            }
        } catch (e: IOException) {
            Logger.e(LOG_IAB, "$TAG $mname: exception; ${e.message}", e)
            RegisterDeviceResult.Failure(-1, e.message)
        }
    }

    suspend fun registerDeviceWithDeviceMeta(
        accountId: String,
        deviceId: String
    ): RegisterDeviceResult = registerDevice(accountId, deviceId, buildDeviceMeta())

    suspend fun acknowledgePurchase(
        accountId: String,
        deviceId: String,
        purchaseToken: String,
        productType: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val mname = "acknowledgePurchase"
        Logger.d(LOG_IAB, "$TAG $mname [${currentEnv().label}]: accLen=${accountId.length}, type=$productType")
        val pt = URLEncoder.encode(purchaseToken, "UTF-8")
        val sku = skuForType(productType)
        executeAckWithRetry(mname) { handle ->
            val vcode = persistentState.appVersion.toString()
            when (handle) {
                is ApiHandle.Production -> handle.api.acknowledgePurchase(accountId, deviceId, sku, pt, vcode)
                is ApiHandle.Test -> handle.api.acknowledgePurchase(accountId, deviceId, sku, pt, vcode, test = "")
            }
        }
    }

    private suspend fun executeAckWithRetry(
        caller: String,
        attempt: suspend (ApiHandle) -> Response<JsonObject?>?
    ): Pair<Boolean, String> {
        val handle = apiFactory.resolveApi()
        val delaysMs = ACK_RETRY_DELAYS
        val maxAttempts = delaysMs.size + 1
        var lastFailureMsg = "No response from server"

        for (attemptIndex in 0 until maxAttempts) {
            if (attemptIndex > 0) {
                val waitMs = delaysMs[attemptIndex - 1]
                Logger.i(LOG_IAB, "$TAG $caller [${handle.envLabel}]: retry #$attemptIndex, waiting ${waitMs}ms")
                delay(waitMs)
            }
            try {
                val response = attempt(handle)
                if (response == null) {
                    lastFailureMsg = "No response from server"
                    Logger.w(LOG_IAB, "$TAG $caller [${handle.envLabel}]: attempt ${attemptIndex + 1} null response, will retry")
                    continue
                }
                val httpCode = response.code()
                val url = response.raw().request.url.toString()
                val errorBody = response.errorBody()?.string()
                Logger.d(LOG_IAB, "$TAG $caller [${handle.envLabel}]: attempt ${attemptIndex + 1} for url: $url, code: $httpCode, err: $errorBody")
                when {
                    httpCode == 401 -> {
                        Logger.e(LOG_IAB, "$TAG $caller [${handle.envLabel}]: 401 unauthorized, not retrying; $errorBody")
                        return Pair(false, "Unauthorized: 401")
                    }
                    httpCode == 409 -> {
                        Logger.w(LOG_IAB, "$TAG $caller [${handle.envLabel}]: 409 conflict, not retrying; $errorBody")
                        return Pair(false, "Conflict: 409")
                    }
                    httpCode == 429 -> {
                        Logger.w(LOG_IAB, "$TAG $caller [${handle.envLabel}]: 429 too many req, retrying with timeout $errorBody")
                        continue
                    }
                    httpCode in 400..499 -> {
                        Logger.e(LOG_IAB, "$TAG $caller [${handle.envLabel}]: permanent client error $httpCode not retrying; $errorBody")
                        return Pair(false, "Client error: $httpCode")
                    }
                    httpCode >= 500 -> {
                        lastFailureMsg = "Server error: $httpCode"
                        Logger.w(LOG_IAB, "$TAG $caller [${handle.envLabel}]: attempt ${attemptIndex + 1} server error $httpCode, will retry; $errorBody")
                        continue
                    }
                }
                val bodyStr = response.body()?.toString()
                val rawForParsing = bodyStr?.takeIf { it.isNotBlank() } ?: errorBody
                if (DEBUG) Logger.d(LOG_IAB, "$TAG $caller [${handle.envLabel}]: body=$rawForParsing")
                return when (val result = RpnPurchaseAckServerResponse.from(rawForParsing, httpCode)) {
                    is RpnPurchaseAckServerResponse.Ok -> {
                        Logger.d(LOG_IAB, "$TAG $caller [${handle.envLabel}]: ack ok, hasEntitlement=${result.payload.hasEntitlement}")
                        Pair(true, result.payload.developerPayload.orEmpty())
                    }
                    is RpnPurchaseAckServerResponse.Err -> {
                        Logger.e(LOG_IAB, "$TAG $caller [${handle.envLabel}]: server business error: ${result.payload}")
                        Pair(false, "Server error: ${result.payload.error}")
                    }
                }
            } catch (e: UnknownHostException) {
                lastFailureMsg = "No internet: ${e.message}"
                Logger.w(LOG_IAB, "$TAG $caller [${handle.envLabel}]: attempt ${attemptIndex + 1}; no internet (${e.message}), will retry")
            } catch (e: ConnectException) {
                lastFailureMsg = "Connection failed: ${e.message}"
                Logger.w(LOG_IAB, "$TAG $caller [${handle.envLabel}]: attempt ${attemptIndex + 1}; connect error (${e.message}), will retry")
            } catch (e: SocketTimeoutException) {
                lastFailureMsg = "Timeout: ${e.message}"
                Logger.w(LOG_IAB, "$TAG $caller [${handle.envLabel}]: attempt ${attemptIndex + 1}; timeout (${e.message}), will retry")
            } catch (e: IOException) {
                lastFailureMsg = "Error: ${e.message}"
                Logger.e(LOG_IAB, "$TAG $caller [${handle.envLabel}]: attempt ${attemptIndex + 1}; unexpected (${e.message}), will retry", e)
            }
        }
        Logger.e(LOG_IAB, "$TAG $caller [${handle.envLabel}]: all $maxAttempts attempts exhausted; $lastFailureMsg")
        return Pair(false, lastFailureMsg)
    }

    suspend fun queryEntitlement(
        accountId: String,
        deviceId: String,
        purchase: PurchaseDetail,
        purchaseToken: String
    ): QueryEntitlementResult = withContext(Dispatchers.IO) {
        val mname = "queryEntitlement"
        if (accountId.isEmpty()) {
            Logger.e(LOG_IAB, "$TAG $mname: empty accountId skipping")
            return@withContext QueryEntitlementResult.Transient(purchase)
        }
        try {
            val encodedPt = URLEncoder.encode(purchaseToken, "UTF-8")
            val sku = skuForType(purchase.productType)
            val handle = apiFactory.resolveApi()
            val vcode = persistentState.appVersion.toString()
            val response = when (handle) {
                is ApiHandle.Production -> handle.api.queryEntitlement(accountId, deviceId, sku, encodedPt, vcode)
                is ApiHandle.Test -> handle.api.queryEntitlement(accountId, deviceId, sku, encodedPt, vcode, test = "")
            }
            if (response == null) {
                Logger.w(LOG_IAB, "$TAG $mname [${handle.envLabel}]: null response from server for entitlement query")
                return@withContext QueryEntitlementResult.Transient(purchase)
            }
            val httpCode = response.code()
            val bodyStr = response.body()?.toString() ?: response.errorBody()?.string()
            Logger.d(LOG_IAB, "$TAG $mname [${handle.envLabel}]: code=$httpCode, body? $bodyStr")
            if (httpCode == 401) {
                Logger.e(LOG_IAB, "$TAG $mname [${handle.envLabel}]: 401 unauthorized; accLen=${accountId.length}, devLen=${deviceId.length}")
                return@withContext QueryEntitlementResult.Unauthorized(accountId, deviceId)
            }
            if (httpCode == 409) {
                Logger.w(LOG_IAB, "$TAG $mname [${handle.envLabel}]: 409 conflict")
                return@withContext QueryEntitlementResult.Conflict
            }
            when (val result = RpnPurchaseAckServerResponse.from(bodyStr, httpCode)) {
                is RpnPurchaseAckServerResponse.Ok -> {
                    Logger.d(LOG_IAB, "$TAG $mname [${handle.envLabel}]: ok, hasEntitlement=${result.payload.hasEntitlement}")
                    QueryEntitlementResult.Success(
                        purchase.copy(
                            payload = result.payload.developerPayload.orEmpty(),
                            expiryTime = parseIsoToEpochMillis(result.payload.expiry),
                            windowDays = result.payload.windowDays ?: 0
                        )
                    )
                }
                is RpnPurchaseAckServerResponse.Err -> {
                    Logger.e(LOG_IAB, "$TAG $mname [${handle.envLabel}]: server business error, ${result.payload}")
                    if (result.payload.isSubscriptionExpired) {
                        Logger.w(LOG_IAB, "$TAG $mname [${handle.envLabel}]: subscription definitively expired on server " +
                            "(state=${result.payload.state}); returning Expired to caller")
                        QueryEntitlementResult.Expired(purchase)
                    } else {
                        QueryEntitlementResult.Failure(
                            purchase = purchase,
                            linkedPurchaseId = result.payload.linkedPurchaseId,
                        )
                    }
                }
            }
        } catch (e: UnknownHostException) {
            Logger.w(LOG_IAB, "$TAG $mname: no internet (${e.message}); transient failure")
            QueryEntitlementResult.Transient(purchase)
        } catch (e: ConnectException) {
            Logger.w(LOG_IAB, "$TAG $mname: connection failed (${e.message}); transient failure")
            QueryEntitlementResult.Transient(purchase)
        } catch (e: SocketTimeoutException) {
            Logger.w(LOG_IAB, "$TAG $mname: timeout (${e.message}); transient failure")
            QueryEntitlementResult.Transient(purchase)
        } catch (e: IOException) {
            Logger.e(LOG_IAB, "$TAG $mname: unexpected error (${e.message}); transient failure", e)
            QueryEntitlementResult.Transient(purchase)
        }
    }

    suspend fun cancelPurchase(
        accountId: String,
        deviceId: String,
        sku: String,
        purchaseToken: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val mname = "cancelPurchase"
        try {
            val handle = apiFactory.resolveApi()
            val vcode = persistentState.appVersion.toString()
            Logger.d(LOG_IAB, "$TAG $mname [${handle.envLabel}]: accLen=${accountId.length}, devLen=${deviceId.length}, sku=$sku")
            val response = when (handle) {
                is ApiHandle.Production -> handle.api.cancelPurchase(accountId, deviceId, sku, purchaseToken, vcode)
                is ApiHandle.Test -> handle.api.cancelSubscription(accountId, deviceId, sku, purchaseToken, vcode, test = "")
            } ?: return@withContext Pair(false, "No response")
            ServerApiErrorMapper.mapCancelResponse(response.toBillingHttp(), handle.envLabel, mname, accountId, sku)
        } catch (e: IOException) {
            Logger.e(LOG_IAB, "$TAG $mname: err ${e.message}", e)
            Pair(false, "Exception: ${e.message}")
        }
    }

    suspend fun revokePurchase(
        accountId: String,
        deviceId: String,
        sku: String,
        purchaseToken: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val mname = "revokePurchase"
        try {
            val handle = apiFactory.resolveApi()
            val vcode = persistentState.appVersion.toString()
            val response = when (handle) {
                is ApiHandle.Production -> handle.api.revokeSubscription(accountId, deviceId, sku, purchaseToken, vcode)
                is ApiHandle.Test -> handle.api.revokeSubscription(accountId, deviceId, sku, purchaseToken, vcode, test = "")
            } ?: return@withContext Pair(false, "No response")
            ServerApiErrorMapper.mapRevokeResponse(response.toBillingHttp(), handle.envLabel, mname, accountId, sku)
        } catch (e: IOException) {
            Logger.e(LOG_IAB, "$TAG $mname: err ${e.message}", e)
            Pair(false, "err: ${e.message}")
        }
    }

    suspend fun fetchPurchaseHistory(
        accountId: String,
        deviceId: String,
        purchaseToken: String,
        total: Int,
    ): FetchOrdersRawResult = withContext(Dispatchers.IO) {
        val mname = "fetchPurchaseHistory"
        if (accountId.isBlank() || purchaseToken.isBlank()) {
            Logger.w(LOG_IAB, "$TAG $mname: accountId or purchaseToken blank")
            return@withContext FetchOrdersRawResult.NoCredentials
        }
        try {
            val handle = apiFactory.resolveApi()
            val vcode = persistentState.appVersion.toString()
            val response = when (handle) {
                is ApiHandle.Production -> handle.api.getPurchaseHistory(
                    accountId = accountId,
                    deviceId = deviceId,
                    purchaseToken = purchaseToken,
                    total = total,
                    vcode = vcode
                )
                is ApiHandle.Test -> handle.api.getPurchaseHistory(
                    accountId = accountId,
                    deviceId = deviceId,
                    purchaseToken = purchaseToken,
                    total = total,
                    vcode = vcode,
                    test = "",
                )
            }
            PurchaseHistoryParser.mapHttpResponse(response.toBillingHttpOrNull(), handle.envLabel, mname)
        } catch (e: IOException) {
            Logger.e(LOG_IAB, "$TAG $mname: exception: ${e.message}", e)
            FetchOrdersRawResult.Error(e.localizedMessage ?: "Unknown error")
        }
    }

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
            val handle = apiFactory.resolveApi()
            val vcode = persistentState.appVersion.toString()
            val response = when (handle) {
                is ApiHandle.Production -> handle.api.consumePurchase(accountId, deviceId, sku, encodedToken, vcode)
                is ApiHandle.Test -> handle.api.consumePurchase(accountId, deviceId, sku, encodedToken, vcode, test = "")
            }
            if (response == null) {
                Logger.e(LOG_IAB, "$TAG $mname [${handle.envLabel}]: null response")
                return@withContext false
            }
            val bodyStr = response.body()?.toString() ?: response.errorBody()?.string()
            val httpCode = response.code()
            when {
                response.isSuccessful -> {
                    Logger.i(LOG_IAB, "$TAG $mname [${handle.envLabel}]: consumed sku=$sku body=$bodyStr")
                    true
                }
                httpCode == 409 && bodyStr?.contains("already consumed", ignoreCase = true) == true -> {
                    Logger.i(LOG_IAB, "$TAG $mname [${handle.envLabel}]: idempotent already consumed sku=$sku")
                    true
                }
                httpCode == 409 -> {
                    Logger.w(LOG_IAB, "$TAG $mname [${handle.envLabel}]: 409 conflict sku=$sku body=$bodyStr")
                    false
                }
                httpCode in 400..499 -> {
                    Logger.e(LOG_IAB, "$TAG $mname [${handle.envLabel}]: permanent client error $httpCode sku=$sku body=$bodyStr")
                    false
                }
                else -> {
                    Logger.w(LOG_IAB, "$TAG $mname [${handle.envLabel}]: transient server error $httpCode sku=$sku")
                    false
                }
            }
        } catch (e: IOException) {
            Logger.e(LOG_IAB, "$TAG $mname: err ${e.message}", e)
            false
        }
    }

    fun buildDeviceMeta(prodId: String = ""): JsonObject =
        BillingApiJson.deviceMeta(
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            appVersion = persistentState.appVersion,
            purchasedProdId = prodId,
        )

    fun buildCustomerMeta(): JsonObject =
        BillingApiJson.customerMeta(
            model = Build.MANUFACTURER + " " + Build.MODEL,
            appVersion = persistentState.appVersion,
        )

    private fun skuForType(productType: String): String =
        if (productType == ProductType.SUBS) InAppBillingHandler.STD_PRODUCT_ID
        else InAppBillingHandler.ONE_TIME_PRODUCT_ID

    private fun parseIsoToEpochMillis(timeIso: String?): Long {
        if (timeIso.isNullOrEmpty()) return 0L
        return try {
            Instant.parse(timeIso).toEpochMilli()
        } catch (e: java.time.format.DateTimeParseException) {
            Logger.w(LOG_IAB, "$TAG parseIsoToEpochMillis: parse failed for '$timeIso': ${e.message}", e)
            0L
        }
    }
}
