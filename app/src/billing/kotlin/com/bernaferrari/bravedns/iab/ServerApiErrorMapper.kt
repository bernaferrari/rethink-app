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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Maps Retrofit billing responses onto the typed results the facade already returns.
 * Log text and result values are load-bearing — keep them identical to the pre-split client.
 */
internal object ServerApiErrorMapper {

    private const val TAG = "BillingBackendClient"

    fun mapCidResponse(
        response: BillingHttpResponse?,
        envLabel: String,
        mname: String,
    ): CidResult = when {
        response == null -> {
            Logger.e(LOG_IAB, "$TAG $mname [$envLabel]: null response")
            CidResult.EMPTY
        }
        response.isSuccessful -> {
            val cid = response.body.primitiveString("cid")
            if (cid.isBlank()) {
                Logger.w(LOG_IAB, "$TAG $mname [$envLabel]: server returned blank cid, body=${response.body}, msg=${response.message}")
                CidResult.EMPTY
            } else {
                Logger.i(LOG_IAB, "$TAG $mname [$envLabel]: received cid (len=${cid.length})")
                CidResult(cid)
            }
        }
        response.code == 401 -> {
            Logger.e(LOG_IAB, "$TAG $mname [$envLabel]: 401 unauthorized, err=${response.errorBody}")
            CidResult.error(401)
        }
        response.code == 409 -> {
            Logger.w(LOG_IAB, "$TAG $mname [$envLabel]: 409 conflict, err=${response.errorBody}")
            CidResult.error(409)
        }
        else -> {
            Logger.e(LOG_IAB, "$TAG $mname [$envLabel]: failed code=${response.code}, err=${response.errorBody}")
            CidResult.error(response.code)
        }
    }

    fun mapDidResponse(
        response: BillingHttpResponse?,
        envLabel: String,
        mname: String,
    ): DidResult = when {
        response == null -> {
            Logger.e(LOG_IAB, "$TAG $mname [$envLabel]: null response")
            DidResult.EMPTY
        }
        response.isSuccessful -> {
            val did = response.body.primitiveString("did")
            if (did.isBlank()) {
                Logger.w(LOG_IAB, "$TAG $mname [$envLabel]: server returned blank did, body=${response.body}, msg=${response.message}")
                DidResult.EMPTY
            } else {
                Logger.i(LOG_IAB, "$TAG $mname [$envLabel]: received did (len=${did.length})")
                DidResult(did)
            }
        }
        response.code == 401 -> {
            Logger.e(LOG_IAB, "$TAG $mname [$envLabel]: 401 unauthorized, err=${response.errorBody}")
            DidResult.error(401)
        }
        response.code == 409 -> {
            Logger.w(LOG_IAB, "$TAG $mname [$envLabel]: 409 conflict, err=${response.errorBody}")
            DidResult.error(409)
        }
        else -> {
            Logger.e(LOG_IAB, "$TAG $mname [$envLabel]: failed code=${response.code}, err=${response.errorBody}")
            DidResult.error(response.code)
        }
    }

    fun mapRegisterDeviceResponse(
        response: BillingHttpResponse?,
        envLabel: String,
        mname: String,
        accountId: String,
        deviceId: String,
        onSuccess: () -> Unit,
    ): RegisterDeviceResult = when {
        response == null -> {
            Logger.e(LOG_IAB, "$TAG $mname [$envLabel]: null response")
            RegisterDeviceResult.Failure(0, "null response")
        }
        response.isSuccessful -> {
            Logger.i(LOG_IAB, "$TAG $mname [$envLabel]: success " +
                "(accLen=${accountId.length}, devLen=${deviceId.length}, res=${response.message}, body=${response.body})")
            onSuccess()
            RegisterDeviceResult.Success
        }
        response.code == 401 -> {
            Logger.e(LOG_IAB, "$TAG $mname [$envLabel]: 401 unauthorized; accLen=${accountId.length}, devLen=${deviceId.length}, err=${response.errorBody}")
            RegisterDeviceResult.Unauthorized(accountId, deviceId)
        }
        response.code == 409 -> {
            Logger.w(LOG_IAB, "$TAG $mname [$envLabel]: 409 (device already registered), err=${response.errorBody}")
            RegisterDeviceResult.Conflict
        }
        else -> {
            Logger.e(LOG_IAB, "$TAG $mname [$envLabel]: failed code=${response.code}, err=${response.errorBody}")
            RegisterDeviceResult.Failure(response.code, response.message)
        }
    }

    fun mapCancelResponse(
        response: BillingHttpResponse,
        envLabel: String,
        mname: String,
        accountId: String,
        sku: String,
    ): Pair<Boolean, String> = when {
        response.code == 401 -> {
            Logger.e(LOG_IAB, "$TAG $mname [$envLabel]: 401 unauthorized; accLen=${accountId.length}, err=${response.errorBody}")
            Pair(false, "Unauthorized: 401")
        }
        response.code == 409 -> Pair(false, "Conflict: 409")
        response.isSuccessful -> {
            Logger.i(LOG_IAB, "$TAG $mname [$envLabel]: cancelled sku=$sku")
            Pair(true, "Cancelled")
        }
        else -> {
            Logger.e(LOG_IAB, "$TAG $mname [$envLabel]: error code=${response.code}, msg=${response.message}, err=${response.errorBody}")
            Pair(false, "Server error: ${response.code}")
        }
    }

    fun mapRevokeResponse(
        response: BillingHttpResponse,
        envLabel: String,
        mname: String,
        accountId: String,
        sku: String,
    ): Pair<Boolean, String> = when {
        response.code == 401 -> {
            Logger.e(LOG_IAB, "$TAG $mname [$envLabel]: 401 unauthorized; accLen=${accountId.length}, err=${response.errorBody}")
            Pair(false, "Unauthorized: 401")
        }
        response.code == 409 -> Pair(false, "Conflict: 409")
        response.isSuccessful -> {
            Logger.i(LOG_IAB, "$TAG $mname [$envLabel]: revoked sku=$sku, raw: ${response.body}")
            Pair(true, "Revoked")
        }
        else -> {
            Logger.e(LOG_IAB, "$TAG $mname [$envLabel]: err code=${response.code}, " +
                "url=${response.requestUrl}")
            Pair(false, "server err: ${response.code}, ${response.errorBody}")
        }
    }

    private fun JsonObject?.primitiveString(key: String): String =
        (this?.get(key) as? JsonPrimitive)?.content?.trim().orEmpty()
}
