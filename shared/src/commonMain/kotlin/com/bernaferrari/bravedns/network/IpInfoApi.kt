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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject

object IpInfoApi {
    suspend fun downloadIpInfo(isRinRActive: Boolean, ipAddress: String): ApiResponse<JsonObject?> {
        val requestUrl = Constants.IP_INFO_BASE_URL + ipAddress
        val client = HttpClientManager.ipInfoClient(isRinRActive)
        return try {
            val response = client.get(requestUrl)
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