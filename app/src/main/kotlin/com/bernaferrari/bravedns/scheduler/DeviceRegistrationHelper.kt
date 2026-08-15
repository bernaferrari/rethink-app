/*
 * Copyright 2025 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.scheduler

import Logger
import Logger.LOG_IAB
import com.bernaferrari.bravedns.iab.BillingBackendClient
import com.bernaferrari.bravedns.iab.InAppBillingHandler
import com.bernaferrari.bravedns.iab.PurchaseDetail
import com.bernaferrari.bravedns.iab.RegisterDeviceResult
import com.bernaferrari.bravedns.iab.ServerApiError

/**
 * Shared `/g/reg` calls used by [RpnProxyUpdateWorker] and
 * [com.bernaferrari.bravedns.iab.SubscriptionCheckWorker].
 */
object DeviceRegistrationHelper {

    suspend fun registerBare(
        logTag: String,
        billingBackendClient: BillingBackendClient,
        accountId: String,
        deviceId: String
    ) {
        val mname = "callBareDeviceRegistration"
        when (val result = billingBackendClient.registerDeviceWithDeviceMeta(accountId, deviceId)) {
            is RegisterDeviceResult.Success -> {
                Logger.i(LOG_IAB, "$logTag; $mname: bare registration succeeded")
            }
            is RegisterDeviceResult.Unauthorized -> {
                Logger.e(LOG_IAB, "$logTag; $mname: 401 unauthorized; posting DeviceAuthError to UI")
                InAppBillingHandler.handleUnauthorized401(
                    operation = ServerApiError.Operation.DEVICE,
                    accountId = accountId,
                    deviceId = deviceId
                )
            }
            is RegisterDeviceResult.Conflict -> {
                Logger.w(LOG_IAB, "$logTag; $mname: 409 conflict: device already registered (non-fatal)")
            }
            is RegisterDeviceResult.Failure -> {
                Logger.e(LOG_IAB, "$logTag; $mname: bare registration failed: code=${result.httpCode} (non-fatal)")
            }
        }
    }

    suspend fun registerWithMeta(
        logTag: String,
        billingBackendClient: BillingBackendClient,
        accountId: String,
        deviceId: String,
        purchaseDetail: PurchaseDetail
    ) {
        val mname = "callDeviceRegistrationWithMeta"
        Logger.i(LOG_IAB, "$logTag; $mname: initiate registration, productId=${purchaseDetail.productId}")
        val meta = billingBackendClient.buildDeviceMeta(purchaseDetail.productId)
        InAppBillingHandler.registerDevice(accountId, deviceId, meta)
    }
}
