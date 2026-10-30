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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

// ref: github.com/celzero/redir: PlayErr / PlayOk classes in src/d.js

/**
 * Sealed result returned by every Rethink server billing endpoint.
 *
 * Use [RpnPurchaseAckServerResponse.from] to deserialise raw HTTP response bodies.
 * A 2xx response with `"success" != false` is treated as [Ok]; everything
 * else (non-2xx, `"error"` key present, or `"success" == false`) is [Err].
 */
sealed class RpnPurchaseAckServerResponse {
    data class Ok(val payload: ResponseOk) : RpnPurchaseAckServerResponse()
    data class Err(val payload: ResponseErr) : RpnPurchaseAckServerResponse()

    companion object {
        /**
         * Parse a raw response body string + HTTP status code into a [RpnPurchaseAckServerResponse].
         *
         * @param body       Raw response body string (may be null/empty on network errors).
         * @param httpCode   HTTP status code from the response (e.g. 200, 400, 500).
         */
        fun from(body: String?, httpCode: Int): RpnPurchaseAckServerResponse {
            // Non-2xx → always an error, even if the body looks OK-ish
            if (httpCode !in 200..<300) {
                val json = parseOrNull(body)
                val payload = ResponseErr.fromJson(json, httpCode)
                Logger.d(LOG_IAB, "Billing response rejected (HTTP $httpCode)")
                return Err(payload)
            }

            val json = BillingApiJson.parseObjectOrNull(body)
                ?: return Err(ResponseErr(error = "malformed response", httpCode = httpCode))

            // If "error" key is present, or "success" is explicitly false → error
            return if (json.containsKey("error") || json.booleanFieldOrNull("success") == false) {
                val payload = ResponseErr.fromJson(json, httpCode)
                Logger.d(LOG_IAB, "Billing response rejected (HTTP $httpCode)")
                Err(payload)
            } else {

                val payload = ResponseOk.fromJson(json)
                Logger.d(LOG_IAB, "Billing response accepted (HTTP $httpCode)")
                Ok(payload)
            }
        }

        // Invalid / empty bodies parse to an empty object, mirroring the former
        // org.json behavior where JSONObject() was used as the fallback.
        private fun parseOrNull(body: String?): JsonObject =
            BillingApiJson.parseObjectOrNull(body) ?: JsonObject(emptyMap())
    }
}

/**
 * Success payload returned by the Rethink Play billing server.
 *
 * All fields are optional except [success] (defaults to `true`) and [ray].
 * Null fields indicate the server did not include that key in the response.
 */
data class ResponseOk(
    /** True if the operation succeeded; may be false for soft/partial failures. */
    val success: Boolean = true,
    /** Human-readable success or failure (non-error) message. */
    val message: String? = null,
    /** Customer identifier (obfuscated account ID). */
    val cid: String? = null,
    /** Product / SKU identifier. */
    val sku: String? = null,
    /** Obfuscated purchase identifier. */
    val purchaseId: String? = null,
    /** Google Play order identifier. */
    val orderId: String? = null,
    /** Purchase state string (e.g. "purchased", "pending"). */
    val state: String? = null,
    /** Account status (e.g. "active", "canceled"). */
    val status: String? = null,
    /** True if this is a test-mode transaction. */
    val test: Boolean? = null,
    /** Subscription or entitlement expiry as ISO 8601 string. */
    val expiry: String? = null,
    /** Subscription or purchase start date as ISO 8601 string. */
    val start: String? = null,
    /** Refund window in calendar days. */
    val windowDays: Int? = null,
    /** Cancellation context string, if any. */
    val cancelCtx: String? = null,
    /** All product identifiers covered by this purchase. */
    val allProducts: List<String>? = null,
    /** Product identifiers not yet consumed. */
    val unconsumedProducts: List<String>? = null,
    /**
     * Developer payload returned by the server after a successful acknowledgement.
     * Contains the session token and other entitlement data.
     */
    val developerPayload: String? = null,
    /** True if the account previously had an entitlement. */
    val hadEntitlement: Boolean? = null,
    /** True if the entitlement was deleted by this operation. */
    val deletedEntitlement: Boolean? = null,
    /** True if the account was already fully refunded before this call. */
    val wasAlreadyFullyRefunded: Boolean? = null,
    /** Cloudflare Ray ID for server-side log correlation. */
    val ray: String? = null,
) {
    companion object {
        fun fromJson(json: JsonObject): ResponseOk = ResponseOk(
            success = json.booleanFieldOrNull("success") ?: true,
            message = json.stringFieldOrNull("message"),
            cid = json.stringFieldOrNull("cid"),
            sku = json.stringFieldOrNull("sku"),
            purchaseId = json.stringFieldOrNull("purchaseId"),
            orderId = json.stringFieldOrNull("orderId"),
            state = json.stringFieldOrNull("state"),
            status = json.stringFieldOrNull("status"),
            test = json.booleanFieldOrNull("test"),
            expiry = json.stringFieldOrNull("expiry"),
            start = json.stringFieldOrNull("start"),
            windowDays = json.intFieldOrNull("windowDays"),
            cancelCtx = json.stringFieldOrNull("cancelCtx"),
            allProducts = json.stringListOrNull("allProducts"),
            unconsumedProducts = json.stringListOrNull("unconsumedProducts"),
            developerPayload = json.stringFieldOrNull("developerPayload"),
            hadEntitlement = json.booleanFieldOrNull("hadEntitlement"),
            deletedEntitlement = json.booleanFieldOrNull("deletedEntitlement"),
            wasAlreadyFullyRefunded = json.booleanFieldOrNull("wasAlreadyFullyRefunded"),
            ray = json.stringFieldOrNull("ray"),
        )
    }

    /** True if the server confirmed the purchase as active/valid. */
    val isActive: Boolean
        get() = success && (status == "active" || status == "valid")

    /** True if the subscription is in a cancelled (but potentially still usable) state. */
    val isCancelled: Boolean
        get() = status == "canceled" || status == "cancelled"

    /** True if the response carries entitlement data (developerPayload present). */
    val hasEntitlement: Boolean
        get() = !developerPayload.isNullOrEmpty()
}

/**
 * Error payload returned by the Rethink Play billing server.
 *
 * The server may return error details on both 4xx and 5xx responses, or when
 * a 2xx response carries `"success": false` or an `"error"` key.
 */
data class ResponseErr(
    /** Primary error message (mapped from "error" or "message" field). */
    val error: String = "",
    /** Extended error details, if provided by the server. */
    val details: String? = null,
    /** Customer identifier (obfuscated account ID). */
    val cid: String? = null,
    /** Product / SKU identifier. */
    val sku: String? = null,
    /** Obfuscated purchase identifier. */
    val purchaseId: String? = null,
    /** Google Play order identifier. */
    val orderId: String? = null,
    /** Purchase state string at the time of the error. */
    val state: String? = null,
    /** Account status at the time of the error. */
    val status: String? = null,
    /** True if this was a test-mode transaction. */
    val test: Boolean? = null,
    /** Entitlement expiry as ISO 8601 string, if known. */
    val expiry: String? = null,
    /** Subscription start date as ISO 8601 string, if known. */
    val start: String? = null,
    /** Refund window in calendar days, if applicable. */
    val windowDays: Int? = null,
    /** All product identifiers covered by the purchase, if known. */
    val allProducts: List<String>? = null,
    /** Unconsumed product identifiers, if known. */
    val unconsumedProducts: List<String>? = null,
    /**
     * Google Play purchase token of an older purchase that was superseded by the one
     * identified by [purchaseId].  Present only when the server cancelled [purchaseId]
     * but detected a still-valid predecessor; the client should query /g/ack for this
     * token and reactivate the corresponding DB row if the server confirms it is valid.
     */
    val linkedPurchaseId: String? = null,
    /** Cloudflare Ray ID for server-side log correlation. */
    val ray: String? = null,
    /** HTTP status code from the server response; 0 if not from an HTTP layer. */
    val httpCode: Int = 0,
) {
    companion object {
        fun fromJson(json: JsonObject, httpCode: Int = 0): ResponseErr = ResponseErr(
            // "error" takes precedence; fall back to "message" (mirrors the JS constructor)
            error = json.stringFieldOrNull("error") ?: json.stringFieldOrNull("message") ?: "",
            details = json.stringFieldOrNull("details"),
            cid = json.stringFieldOrNull("cid"),
            sku = json.stringFieldOrNull("sku"),
            purchaseId = json.stringFieldOrNull("purchaseId"),
            orderId = json.stringFieldOrNull("orderId"),
            state = json.stringFieldOrNull("state"),
            status = json.stringFieldOrNull("status"),
            test = json.booleanFieldOrNull("test"),
            expiry = json.stringFieldOrNull("expiry"),
            start = json.stringFieldOrNull("start"),
            windowDays = json.intFieldOrNull("windowDays"),
            allProducts = json.stringListOrNull("allProducts"),
            unconsumedProducts = json.stringListOrNull("unconsumedProducts"),
            linkedPurchaseId = json.stringFieldOrNull("linkedPurchaseId"),
            ray = json.stringFieldOrNull("ray"),
            httpCode = httpCode,
        )
    }

    /**
     * True when the server has authoritatively confirmed the subscription is expired.
     *
     * A response with [state] == "SUBSCRIPTION_STATE_EXPIRED" is a definitive signal
     * from the Google Play billing backend (forwarded by our server) that the subscription
     * is no longer active.  Callers should expire the local purchase rather than preserving it.
     */
    val isSubscriptionExpired: Boolean
        get() = state == "SUBSCRIPTION_STATE_EXPIRED"

    override fun toString(): String =
        "PlayErr(http=$httpCode, error='$error', state=$state, status=$status, sku=$sku, ray=$ray)"
}

/** Returns the boolean value for [key], or null if absent or not a boolean(-like primitive). */
private fun JsonObject.booleanFieldOrNull(key: String): Boolean? =
    this[key].primitiveOrNull()?.booleanOrNull

/**
 * Returns the string value for [key], or null if missing or empty.
 *
 * Mirrors org.json `optString`: non-string primitives (numbers, booleans) are
 * coerced to their string form; JSON null and missing keys yield null.
 */
private fun JsonObject.stringFieldOrNull(key: String): String? {
    val primitive = this[key].primitiveOrNull() ?: return null
    if (primitive is JsonNull) return null
    return primitive.content.takeUnless { it.isEmpty() || it == "null" }
}

/** Returns the int value for [key], or null if the key is absent or not a number. */
private fun JsonObject.intFieldOrNull(key: String): Int? =
    this[key].primitiveOrNull()?.intOrNull

/** Returns the list of strings for a JSON array at [key], or null if absent. */
private fun JsonObject.stringListOrNull(key: String): List<String>? {
    val arr = this[key] as? JsonArray ?: return null
    return arr.mapNotNull { item ->
        (item as? JsonPrimitive)?.content?.takeUnless { s -> s.isEmpty() }
    }.takeIf { it.isNotEmpty() }
}

/** Narrows a [JsonElement] to a primitive without throwing on objects/arrays. */
private fun JsonElement?.primitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive
