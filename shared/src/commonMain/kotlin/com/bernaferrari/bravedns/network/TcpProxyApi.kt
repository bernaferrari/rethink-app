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
package com.bernaferrari.bravedns.network

import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.JsonHelper
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject

object TcpProxyApi {
    private fun url(path: String): String = Constants.TCP_PROXY_BASE_URL.trimEnd('/') + path

    suspend fun getPublicKey(isRinRActive: Boolean, appVersion: String): ApiResponse<JsonObject?> =
        getJson(isRinRActive, url("/p/$appVersion"))

    suspend fun getPaymentStatus(isRinRActive: Boolean, refId: String): ApiResponse<JsonObject?> =
        getJson(isRinRActive, url("/p")) { parameter("ref_id", refId) }

    suspend fun checkForPaymentAcknowledgement(
        isRinRActive: Boolean,
        refId: String,
        purchaseToken: String,
    ): ApiResponse<JsonObject?> = getJson(isRinRActive, url("/g/$refId/$purchaseToken"))

    suspend fun cancelSubscription(
        isRinRActive: Boolean,
        accountId: String,
        purchaseToken: String,
        test: Boolean,
    ): ApiResponse<JsonObject?> =
        postJson(isRinRActive, url("/g/stop")) {
            parameter("cid", accountId)
            parameter("purchaseToken", purchaseToken)
            parameter("test", test)
        }

    suspend fun revokeSubscription(
        isRinRActive: Boolean,
        accountId: String,
        purchaseToken: String,
        test: Boolean,
    ): ApiResponse<JsonObject?> =
        postJson(isRinRActive, url("/g/refund")) {
            parameter("cid", accountId)
            parameter("purchaseToken", purchaseToken)
            parameter("test", test)
        }

    suspend fun queryEntitlement(
        isRinRActive: Boolean,
        accountId: String,
        test: Boolean,
    ): ApiResponse<JsonObject?> =
        getJson(isRinRActive, url("/g/ent")) {
            parameter("cid", accountId)
            parameter("test", test)
        }

    private suspend fun getJson(
        isRinRActive: Boolean,
        requestUrl: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): ApiResponse<JsonObject?> {
        val client = HttpClientManager.tcpProxyClient(isRinRActive)
        return try {
            val response = client.get(requestUrl) { block() }
            val body =
                if (response.status.isSuccess()) {
                    JsonHelper.parseObject(response.bodyAsText())
                } else {
                    null
                }
            ApiResponse(response.status.value, body, response.status.description, requestUrl)
        } catch (e: Exception) {
            ApiResponse(-1, null, e.message ?: "request failed", requestUrl)
        }
    }

    private suspend fun postJson(
        isRinRActive: Boolean,
        requestUrl: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): ApiResponse<JsonObject?> {
        val client = HttpClientManager.tcpProxyClient(isRinRActive)
        return try {
            val response = client.post(requestUrl) { block() }
            val body =
                if (response.status.isSuccess()) {
                    JsonHelper.parseObject(response.bodyAsText())
                } else {
                    null
                }
            ApiResponse(response.status.value, body, response.status.description, requestUrl)
        } catch (e: Exception) {
            ApiResponse(-1, null, e.message ?: "request failed", requestUrl)
        }
    }
}