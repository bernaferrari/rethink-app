/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.celzero.bravedns.network

import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.JsonHelper
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject

object BlocklistApi {
    private fun url(path: String): String = Constants.DOWNLOAD_BASE_URL.trimEnd('/') + path

    suspend fun downloadLocalBlocklistFile(
        isRinRActive: Boolean,
        fileName: String,
        vcode: Int,
        compressed: String,
    ): ApiResponse<StreamingBody?> {
        val client = HttpClientManager.blocklistClient(isRinRActive)
        return try {
            val response =
                client.get(url("/$fileName")) {
                    parameter("vcode", vcode)
                    parameter("compressed", compressed)
                }
            if (response.status.isSuccess()) {
                ApiResponse(
                    code = response.status.value,
                    body =
                        StreamingBody(
                            response.bodyAsChannel(),
                            response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L,
                        ),
                    message = response.status.description,
                    requestUrl = url("/$fileName"),
                )
            } else {
                ApiResponse(response.status.value, null, response.status.description, url("/$fileName"))
            }
        } catch (e: Exception) {
            ApiResponse(-1, null, e.message ?: "request failed", url("/$fileName"))
        }
    }

    suspend fun downloadRemoteBlocklistFile(
        isRinRActive: Boolean,
        fileName: String,
        vcode: Int,
        compressed: String,
    ): ApiResponse<JsonObject?> {
        val client = HttpClientManager.blocklistClient(isRinRActive)
        val requestPath = url("/$fileName")
        return try {
            val response =
                client.get(requestPath) {
                    parameter("vcode", vcode)
                    parameter("compressed", compressed)
                }
            val body =
                if (response.status.isSuccess()) {
                    JsonHelper.parseObject(response.bodyAsText())
                } else {
                    null
                }
            ApiResponse(response.status.value, body, response.status.description, requestPath)
        } catch (e: Exception) {
            ApiResponse(-1, null, e.message ?: "request failed", requestPath)
        }
    }

    suspend fun downloadAvailabilityCheck(
        isRinRActive: Boolean,
        update: String,
        blocklist: String,
        tStamp: Long,
        vcode: Int,
    ): ApiResponse<JsonObject?> {
        val client = HttpClientManager.blocklistClient(isRinRActive)
        val requestPath = url("/$update/$blocklist")
        return try {
            val response =
                client.get(requestPath) {
                    parameter("tstamp", tStamp)
                    parameter("vcode", vcode)
                }
            val body =
                if (response.status.isSuccess()) {
                    JsonHelper.parseObject(response.bodyAsText())
                } else {
                    null
                }
            ApiResponse(response.status.value, body, response.status.description, requestPath)
        } catch (e: Exception) {
            ApiResponse(-1, null, e.message ?: "request failed", requestPath)
        }
    }
}
