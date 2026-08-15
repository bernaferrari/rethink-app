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
import Logger.LOG_TAG_UI
import com.bernaferrari.bravedns.RethinkDnsApplication.Companion.DEBUG
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.time.Instant

/**
 * Pure-ish mapping of `/g/tx` JSON (and the HTTP envelope around it) onto
 * [ServerOrderEntry] / [FetchOrdersRawResult].
 *
 * The `meta` field inside each `tx` row is walked with safe accessors rather than
 * typed model classes so proto3 int64 string-encoding and nullable sub-objects
 * cannot fail the whole batch.
 */
internal object PurchaseHistoryParser {

    private const val TAG_HTTP = "BillingBackendClient"
    private const val TAG_TX = "ServerOrderRepo"

    /**
     * Maps a `/g/tx` HTTP envelope onto [FetchOrdersRawResult].
     */
    fun mapHttpResponse(
        response: BillingHttpResponse?,
        envLabel: String,
        mname: String = "fetchPurchaseHistory",
    ): FetchOrdersRawResult = when {
        response == null -> {
            Logger.w(LOG_IAB, "$TAG_HTTP $mname [$envLabel]: null response")
            FetchOrdersRawResult.Error("Empty response from server")
        }
        !response.isSuccessful -> {
            val code = response.code
            Logger.w(LOG_IAB, "$TAG_HTTP $mname [$envLabel]: HTTP $code, ${response.errorBody}")
            FetchOrdersRawResult.Error("Server returned HTTP $code")
        }
        else -> {
            val body = response.body
            if (body == null) {
                Logger.w(LOG_IAB, "$TAG_HTTP $mname [$envLabel]: null body")
                FetchOrdersRawResult.Error("No data returned from server")
            } else {
                if (DEBUG) Logger.vv(LOG_IAB, "$TAG_HTTP $mname [$envLabel]: body=$body")
                FetchOrdersRawResult.Success(body)
            }
        }
    }

    /**
     * Maps a [FetchOrdersRawResult] onto the repository result, parsing the `tx`
     * array when the HTTP layer succeeded.
     */
    fun mapRaw(raw: FetchOrdersRawResult): ServerOrderHistoryRepository.Result = when (raw) {
        is FetchOrdersRawResult.Success -> {
            val orders = parseTxBody(raw.body)
            Logger.i(LOG_TAG_UI, "$TAG_TX fetchOrders: parsed ${orders.size} orders")
            ServerOrderHistoryRepository.Result.Success(orders)
        }
        is FetchOrdersRawResult.Error -> {
            Logger.w(LOG_TAG_UI, "$TAG_TX fetchOrders: ${raw.message}")
            ServerOrderHistoryRepository.Result.Error(raw.message)
        }
        FetchOrdersRawResult.NoCredentials -> ServerOrderHistoryRepository.Result.NoCredentials
    }

    /**
     * Walks the `/g/tx` envelope and maps each row to a [ServerOrderEntry],
     * deduplicating by purchase token. Malformed rows are skipped.
     */
    fun parseTxBody(body: JsonObject): List<ServerOrderEntry> {
        val successElem = body["success"]
        if (successElem != null && successElem !is JsonNull && successElem.safeBool() == false) {
            Logger.w(LOG_TAG_UI, "$TAG_TX mapTxBody: server returned success=false")
            return emptyList()
        }

        val txElem = body["tx"]
        if (txElem == null || txElem is JsonNull || txElem !is JsonArray) {
            Logger.w(LOG_TAG_UI, "$TAG_TX mapTxBody: 'tx' array absent or not an array")
            return emptyList()
        }

        val results = mutableListOf<ServerOrderEntry>()
        txElem.forEach { elem ->
            val row = elem as? JsonObject ?: return@forEach
            try {
                val entry = mapRow(row) ?: return@forEach
                if (results.none { it.purchaseToken == entry.purchaseToken }) {
                    results.add(entry)
                }
            } catch (e: IllegalArgumentException) {
                Logger.e(LOG_TAG_UI, "$TAG_TX mapRow error: ${e.message}", e)
            } catch (e: ClassCastException) {
                Logger.e(LOG_TAG_UI, "$TAG_TX mapRow error: ${e.message}", e)
            }
        }
        return results
    }

    private fun mapRow(row: JsonObject): ServerOrderEntry? {
        val token = row.get("purchasetoken").safeString()?.takeIf { it.isNotBlank() }
            ?: return null
        val cid = row.get("cid").safeString() ?: ""
        val mtime = isoToMillis(row.get("mtime").safeString())
        val meta = row.obj("meta") ?: return null
        val kind = meta.get("kind").safeString() ?: ""

        return if (kind == ServerOrderEntry.KIND_SUBSCRIPTION) {
            mapSubscriptionPurchase(token, cid, mtime, meta)
        } else {
            mapProductPurchase(token, cid, mtime, meta)
        }
    }

    private fun mapSubscriptionPurchase(
        token: String,
        cid: String,
        mtime: Long,
        meta: JsonObject,
    ): ServerOrderEntry {
        val orderId = meta.get("latestOrderId").safeString()
        val startTime = isoToMillis(meta.get("startTime").safeString())
        val subscriptionState = meta.get("subscriptionState").safeString()
        val isTest = meta["testPurchase"].let { it != null && it !is JsonNull }

        val lineItem = meta.firstArrayObj("lineItems")
        val productId = lineItem?.get("productId").safeString() ?: ""
        val expiryTime = isoToMillis(lineItem?.get("expiryTime").safeString())
        val planId = lineItem.obj("offerDetails")?.get("basePlanId").safeString()
        val autoRenew = lineItem.obj("autoRenewingPlan")?.get("autoRenewEnabled").safeBool() ?: false

        return ServerOrderEntry(
            purchaseToken = token,
            cid = cid,
            sku = productId,
            mtime = mtime,
            kind = ServerOrderEntry.KIND_SUBSCRIPTION,
            isSubscription = true,
            orderId = orderId,
            startTimeMs = startTime,
            expiryTimeMs = expiryTime,
            purchaseTimeMs = startTime,
            subscriptionState = subscriptionState,
            purchaseState = null,
            autoRenewEnabled = autoRenew,
            isTestPurchase = isTest,
            productId = productId,
            planId = planId ?: productId
        )
    }

    private fun mapProductPurchase(
        token: String,
        cid: String,
        mtime: Long,
        meta: JsonObject,
    ): ServerOrderEntry {
        val kind = meta.get("kind").safeString() ?: ""
        val orderId = meta.get("orderId").safeString()
        val purchaseMs = isoToMillis(meta.get("purchaseCompletionTime").safeString())
            .takeIf { it > 0L } ?: mtime
        val isTest = meta["testPurchaseContext"].let { it != null && it !is JsonNull }

        val lineItem = meta.firstArrayObj("productLineItem")
        val productId = lineItem?.get("productId").safeString() ?: ""
        val planId = lineItem.obj("productOfferDetails")
            ?.get("purchaseOptionId").safeString() ?: productId

        val purchaseState: Int? = when (
            meta.obj("purchaseStateContext")?.get("purchaseState").safeString()
        ) {
            "PURCHASED" -> 0
            "CANCELLED" -> 1
            "PENDING" -> 2
            else -> null
        }

        return ServerOrderEntry(
            purchaseToken = token,
            cid = cid,
            sku = productId,
            mtime = mtime,
            kind = kind,
            isSubscription = false,
            orderId = orderId,
            startTimeMs = purchaseMs,
            expiryTimeMs = 0L,
            purchaseTimeMs = purchaseMs,
            subscriptionState = null,
            purchaseState = purchaseState,
            autoRenewEnabled = false,
            isTestPurchase = isTest,
            productId = productId,
            planId = planId
        )
    }

    private fun isoToMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (e: java.time.format.DateTimeParseException) {
            Logger.w(LOG_TAG_UI, "$TAG_TX isoToMillis: unparseable '$iso': ${e.message}", e)
            0L
        }
    }

    private fun JsonElement?.safeString(): String? {
        if (this == null || this is JsonNull) return null
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.content
    }

    private fun JsonElement?.safeBool(): Boolean? {
        if (this == null || this is JsonNull) return null
        val primitive = this as? JsonPrimitive ?: return null
        primitive.booleanOrNull?.let { return it }
        return primitive.content.toBooleanStrictOrNull()
    }

    private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

    private fun JsonObject?.firstArrayObj(key: String): JsonObject? {
        val arr = this?.get(key) as? JsonArray ?: return null
        return arr.firstOrNull() as? JsonObject
    }
}
