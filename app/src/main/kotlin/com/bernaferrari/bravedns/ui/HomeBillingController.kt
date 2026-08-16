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
package com.bernaferrari.bravedns.ui

import android.content.Intent
import com.bernaferrari.bravedns.iab.InAppBillingHandler
import com.bernaferrari.bravedns.iab.ServerApiError
import com.bernaferrari.bravedns.util.Constants

/**
 * Wires notification extras into [InAppBillingHandler] so the Compose account screen
 * can offer the same recovery path as an in-process server failure.
 *
 * Public billing types ([InAppBillingHandler], [ServerApiError]) are used as-is.
 */
internal class HomeBillingController {

    fun applyNotification(intent: Intent) {
        InAppBillingHandler.serverApiErrorLiveData.value = errorFromNotification(intent)
    }

    fun errorFromNotification(intent: Intent): ServerApiError? {
        fun operation(extra: String): ServerApiError.Operation =
            intent.getStringExtra(extra)
                ?.let { name -> ServerApiError.Operation.entries.firstOrNull { it.name == name } }
                ?: ServerApiError.Operation.CUSTOMER

        return when {
            intent.getStringExtra(Constants.NOTIF_INTENT_EXTRA_IAB_CONFLICT_NAME) ==
                Constants.NOTIF_INTENT_EXTRA_IAB_CONFLICT_VALUE ->
                ServerApiError.Conflict409(
                    endpoint = intent.getStringExtra("conflict_endpoint").orEmpty(),
                    operation = operation("conflict_operation"),
                    serverMessage = intent.getStringExtra("conflict_server_msg"),
                    accountId = intent.getStringExtra("conflict_account_id").orEmpty(),
                    purchaseToken = intent.getStringExtra("conflict_purchase_token").orEmpty(),
                    sku = intent.getStringExtra("conflict_sku").orEmpty(),
                )
            intent.getStringExtra(Constants.NOTIF_INTENT_EXTRA_IAB_DEVICE_AUTH_ERROR_NAME) ==
                Constants.NOTIF_INTENT_EXTRA_IAB_DEVICE_AUTH_ERROR_VALUE ->
                ServerApiError.Unauthorized401(
                    operation = operation("auth_error_operation"),
                    accountId = intent.getStringExtra("auth_error_account_id").orEmpty(),
                    deviceIdPrefix = intent.getStringExtra("auth_error_device_id_prefix").orEmpty(),
                )
            intent.getStringExtra(Constants.NOTIF_INTENT_EXTRA_IAB_DEVICE_NOT_REGISTERED_NAME) ==
                Constants.NOTIF_INTENT_EXTRA_IAB_DEVICE_NOT_REGISTERED_VALUE ->
                ServerApiError.DeviceNotRegistered(
                    entitlementCid = intent.getStringExtra("dnr_entitlement_cid").orEmpty(),
                    storedCid = intent.getStringExtra("dnr_stored_cid").orEmpty(),
                    deviceIdPrefix = intent.getStringExtra("dnr_device_id_prefix").orEmpty(),
                )
            else -> null
        }
    }
}
