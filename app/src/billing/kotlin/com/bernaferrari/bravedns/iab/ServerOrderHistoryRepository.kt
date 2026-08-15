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
import Logger.LOG_TAG_UI
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager

/**
 * Fetches the purchase/order history for the current user from the billing
 * server's `GET /g/tx` endpoint and maps the server's Google Play purchase
 * objects into [ServerOrderEntry] domain values via [PurchaseHistoryParser].
 */
class ServerOrderHistoryRepository(private val billingBackendClient: BillingBackendClient) {

    companion object {
        private const val TAG = "ServerOrderRepo"
        private const val MAX_ORDERS = 20
    }

    /** Sealed result type returned to the ViewModel. */
    sealed class Result {
        data class Success(val orders: List<ServerOrderEntry>) : Result()
        data class Error(val message: String) : Result()
        object NoCredentials : Result()
    }

    /**
     * Fetches up to [MAX_ORDERS] purchase records from the server for the
     * current user.  Must be called from a background coroutine.
     */
    suspend fun fetchOrders(): Result {
        val mname = "fetchOrders"
        return try {
            val purchase = RpnProxyManager.getEffectivePurchaseDetail()
            if (purchase == null) {
                Logger.w(LOG_TAG_UI, "$TAG $mname: no purchase detail available")
                return Result.NoCredentials
            }
            val accountId = purchase.accountId
            val deviceId = InAppBillingHandler.getObfuscatedDeviceId()
            val purchaseToken = purchase.purchaseToken
            if (accountId.isBlank() || purchaseToken.isBlank()) {
                Logger.w(LOG_TAG_UI, "$TAG $mname: accountId or purchaseToken blank")
                return Result.NoCredentials
            }

            PurchaseHistoryParser.mapRaw(
                billingBackendClient.fetchPurchaseHistory(
                    accountId = accountId,
                    deviceId = deviceId,
                    purchaseToken = purchaseToken,
                    total = MAX_ORDERS,
                )
            )
        } catch (e: java.io.IOException) {
            Logger.e(LOG_TAG_UI, "$TAG $mname: exception: ${e.message}", e)
            Result.Error(e.localizedMessage ?: "Unknown error")
        } catch (e: IllegalStateException) {
            Logger.e(LOG_TAG_UI, "$TAG $mname: exception: ${e.message}", e)
            Result.Error(e.localizedMessage ?: "Unknown error")
        }
    }
}
