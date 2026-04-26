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

sealed class ServerApiError {

    object None : ServerApiError()

    data class Conflict409(
        val endpoint: String,
        val operation: Operation,
        val serverMessage: String?,
        val accountId: String,
        val purchaseToken: String,
        val sku: String
    ) : ServerApiError()

    data class Unauthorized401(
        val operation: Operation,
        val accountId: String,
        val deviceIdPrefix: String
    ) : ServerApiError()

    data class GenericError(
        val httpCode: Int,
        val message: String
    ) : ServerApiError()

    data class NetworkError(val message: String?) : ServerApiError()

    data class DeviceNotRegistered(
        val entitlementCid: String,
        val storedCid: String,
        val deviceIdPrefix: String
    ) : ServerApiError()

    val isConflict: Boolean get() = this is Conflict409
    val isUnauthorized: Boolean get() = this is Unauthorized401
    val isDeviceNotRegistered: Boolean get() = this is DeviceNotRegistered
    val isNone: Boolean get() = this is None

    enum class Operation(val endpoint: String) {
        CUSTOMER("/d/acc"),
        CANCEL("/g/stop"),
        REVOKE("/g/refund"),
        ACKNOWLEDGE("/g/ack"),
        CONSUME("/g/con"),
        DEVICE("/d/reg");

        val canRefund: Boolean
            get() = this == CANCEL || this == REVOKE || this == CONSUME || this == DEVICE || this == ACKNOWLEDGE
    }
}

