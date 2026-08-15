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
import com.bernaferrari.bravedns.util.JsonHelper
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * kotlinx.serialization [Json] used for billing request/response bodies.
 *
 * Must stay identical to the former Retrofit converter:
 * [JsonHelper.json] plus [JsonBuilder.coerceInputValues] = true.
 */
internal object BillingApiJson {

    private const val TAG = "SafeResponseConverter"

    val json: Json = Json(JsonHelper.json) {
        coerceInputValues = true
    }

    fun encode(obj: JsonObject): String =
        json.encodeToString(JsonObject.serializer(), obj)

    /** Customer registration body for `POST /d/acc`. */
    fun customerMeta(model: String, appVersion: Int): JsonObject = buildJsonObject {
        put("model", model)
        put("appVersion", appVersion)
    }

    /** Device registration body for `POST /d/reg`. */
    fun deviceMeta(deviceName: String, appVersion: Int, purchasedProdId: String): JsonObject =
        buildJsonObject {
            put("deviceName", deviceName)
            put("appVersion", appVersion)
            put("purchasedProdId", purchasedProdId)
        }

    /**
     * Reimplements [com.bernaferrari.bravedns.customdownloader.SafeResponseConverterFactory]:
     * empty / non-JSON bodies become null instead of throwing.
     */
    fun parseObjectOrNull(raw: String?): JsonObject? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            Logger.d(LOG_IAB, "$TAG: empty body after buffering, returning null")
            return null
        }
        return when (trimmed[0]) {
            '{', '[' -> try {
                json.parseToJsonElement(trimmed) as? JsonObject
            } catch (e: SerializationException) {
                Logger.w(LOG_IAB, "$TAG: JSON parse failed, returning null. Preview: ${trimmed.take(120)}", e)
                null
            } catch (e: IllegalArgumentException) {
                Logger.w(LOG_IAB, "$TAG: JSON parse failed, returning null. Preview: ${trimmed.take(120)}", e)
                null
            }
            '<' -> {
                Logger.w(LOG_IAB, "$TAG: HTML/XML body received, not JSON, returning null. " +
                    "Preview: ${trimmed.take(120)}")
                null
            }
            else -> {
                Logger.w(LOG_IAB, "$TAG: non-JSON body received, returning null. " +
                    "Preview: ${trimmed.take(120)}")
                null
            }
        }
    }
}
