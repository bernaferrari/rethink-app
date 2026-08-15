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

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

/**
 * Exact request shapes the billing backend used to send through Retrofit.
 *
 * Query encoding matches Retrofit `@Query` / OkHttp `HttpUrl` (not Ktor's default
 * `parameter()` encoder). Ack/entitlement/consume still pre-encode the purchase
 * token with [java.net.URLEncoder] and then apply `@Query` encoding again — that
 * double encoding is load-bearing and must not be "fixed".
 */
internal object BillingRequestCodec {

    const val JSON_MEDIA_TYPE = "application/json; charset=UTF-8"

    const val HEADER_CID = "x-rethink-app-cid"
    const val HEADER_DID = "x-rethink-app-did"
    const val HEADER_DID_TOKEN = "x-rethink-app-did-token"
    const val HEADER_SESSION = "x-rethink-db-rpn-session"
    const val HEADER_SESSION_TEST = "x-rethink-db-rpn-test-session"
    const val HEADER_SESSION_RESPONSE = "x-db-rpn-session"
    const val HEADER_SESSION_TEST_RESPONSE = "x-db-rpn-test-session"

    const val SESSION_PRIMARY = "first-primary"
    const val SESSION_UNCONSTRAINED = "first-unconstrained"

    const val PATH_REGISTER_CUSTOMER = "/d/acc"
    const val PATH_REGISTER_DEVICE = "/d/reg"
    const val PATH_CANCEL = "/g/stop"
    const val PATH_REVOKE = "/g/refund"
    const val PATH_ACK = "/g/ack"
    const val PATH_CONSUME = "/g/con"
    const val PATH_HISTORY = "/g/tx"

    data class RequestSpec(
        val method: String,
        val path: String,
        val query: LinkedHashMap<String, String>,
        val headers: LinkedHashMap<String, String>,
        val jsonBody: String?,
    ) {
        fun encodedQuery(): String = query.entries.joinToString("&") { (k, v) ->
            "${QueryEncoding.encodeQueryValue(k)}=${QueryEncoding.encodeQueryValue(v)}"
        }

        fun pathAndQuery(): String {
            val q = encodedQuery()
            return if (q.isEmpty()) path else "$path?$q"
        }
    }

    /**
     * Pre-encode used by ack / entitlement / consume before the value is placed
     * in the query (which encodes again). The double encoding is load-bearing.
     */
    private fun preEncodePurchaseToken(purchaseToken: String): String =
        URLEncoder.encode(purchaseToken, Charsets.UTF_8.name())

    fun registerCustomer(
        accountId: String?,
        deviceId: String?,
        vcode: String,
        meta: JsonObject,
        test: String? = null,
    ): RequestSpec = RequestSpec(
        method = "POST",
        path = PATH_REGISTER_CUSTOMER,
        query = queryOf("vcode" to vcode, test = test),
        headers = identityHeaders(
            accountId = accountId,
            deviceId = deviceId,
            omitBlankDid = false,
            write = true,
            test = test != null,
        ),
        jsonBody = BillingApiJson.encode(meta),
    )

    fun registerDevice(
        accountId: String,
        deviceId: String?,
        vcode: String,
        meta: JsonObject?,
        test: String? = null,
    ): RequestSpec = RequestSpec(
        method = "POST",
        path = PATH_REGISTER_DEVICE,
        query = queryOf("vcode" to vcode, test = test),
        headers = identityHeaders(
            accountId = accountId,
            deviceId = deviceId,
            omitBlankDid = true,
            write = true,
            test = test != null,
        ),
        jsonBody = meta?.let { BillingApiJson.encode(it) },
    )

    fun acknowledge(
        accountId: String,
        deviceId: String,
        sku: String,
        purchaseToken: String,
        vcode: String,
        test: String? = null,
    ): RequestSpec = noBody(
        method = "POST",
        path = PATH_ACK,
        query = queryOf(
            "sku" to sku,
            "purchaseToken" to preEncodePurchaseToken(purchaseToken),
            "vcode" to vcode,
            test = test,
        ),
        accountId = accountId,
        deviceId = deviceId,
        write = true,
        test = test != null,
    )

    fun queryEntitlement(
        accountId: String,
        deviceId: String,
        sku: String,
        purchaseToken: String,
        vcode: String,
        test: String? = null,
    ): RequestSpec = noBody(
        method = "GET",
        path = PATH_ACK,
        query = queryOf(
            "sku" to sku,
            "purchaseToken" to preEncodePurchaseToken(purchaseToken),
            "vcode" to vcode,
            test = test,
        ),
        accountId = accountId,
        deviceId = deviceId,
        write = false,
        test = test != null,
    )

    fun cancel(
        accountId: String,
        deviceId: String,
        sku: String,
        purchaseToken: String,
        vcode: String,
        test: String? = null,
    ): RequestSpec = noBody(
        method = "POST",
        path = PATH_CANCEL,
        query = queryOf(
            "sku" to sku,
            "purchaseToken" to purchaseToken,
            "vcode" to vcode,
            test = test,
        ),
        accountId = accountId,
        deviceId = deviceId,
        write = true,
        test = test != null,
    )

    fun revoke(
        accountId: String,
        deviceId: String,
        sku: String,
        purchaseToken: String,
        vcode: String,
        test: String? = null,
    ): RequestSpec = noBody(
        method = "POST",
        path = PATH_REVOKE,
        query = queryOf(
            "sku" to sku,
            "purchaseToken" to purchaseToken,
            "vcode" to vcode,
            test = test,
        ),
        accountId = accountId,
        deviceId = deviceId,
        write = true,
        test = test != null,
    )

    fun consume(
        accountId: String,
        deviceId: String,
        sku: String,
        purchaseToken: String,
        vcode: String,
        test: String? = null,
    ): RequestSpec = noBody(
        method = "POST",
        path = PATH_CONSUME,
        query = queryOf(
            "sku" to sku,
            "purchaseToken" to preEncodePurchaseToken(purchaseToken),
            "vcode" to vcode,
            test = test,
        ),
        accountId = accountId,
        deviceId = deviceId,
        write = true,
        test = test != null,
    )

    fun purchaseHistory(
        accountId: String,
        deviceId: String,
        purchaseToken: String,
        total: Int?,
        vcode: String,
        test: String? = null,
        active: String? = null,
    ): RequestSpec {
        val query = linkedMapOf<String, String>()
        query["purchaseToken"] = purchaseToken
        if (total != null) query["tot"] = total.toString()
        if (active != null) query["active"] = active
        if (test != null) query["test"] = test
        query["vcode"] = vcode
        return RequestSpec(
            method = "GET",
            path = PATH_HISTORY,
            query = query,
            headers = identityHeaders(
                accountId = accountId,
                deviceId = deviceId,
                omitBlankDid = false,
                write = false,
                test = test != null,
            ),
            jsonBody = null,
        )
    }

    private fun noBody(
        method: String,
        path: String,
        query: LinkedHashMap<String, String>,
        accountId: String,
        deviceId: String,
        write: Boolean,
        test: Boolean,
    ): RequestSpec = RequestSpec(
        method = method,
        path = path,
        query = query,
        headers = identityHeaders(
            accountId = accountId,
            deviceId = deviceId,
            omitBlankDid = false,
            write = write,
            test = test,
        ),
        jsonBody = null,
    )

    private fun queryOf(vararg pairs: Pair<String, String>, test: String?): LinkedHashMap<String, String> {
        val query = linkedMapOf<String, String>()
        for ((k, v) in pairs) query[k] = v
        if (test != null) query["test"] = test
        return query
    }

    private fun identityHeaders(
        accountId: String?,
        deviceId: String?,
        omitBlankDid: Boolean,
        write: Boolean,
        test: Boolean,
    ): LinkedHashMap<String, String> {
        val headers = linkedMapOf<String, String>()
        val sessionName = if (test) HEADER_SESSION_TEST else HEADER_SESSION
        headers[sessionName] = if (write) SESSION_PRIMARY else SESSION_UNCONSTRAINED
        if (accountId != null) headers[HEADER_CID] = accountId
        if (deviceId != null && !(omitBlankDid && deviceId.isBlank())) {
            headers[HEADER_DID] = deviceId
        }
        return headers
    }
}

/**
 * Characters OkHttp percent-encodes in a query *component* when
 * `alreadyEncoded = false`, plus `%` (always encoded in that mode).
 */
private val QUERY_COMPONENT_ENCODE_SET = " !\"#\$&'(),/:;<=>?@[]\\^`{|}~".toSet()

/** Percent-encodes query components exactly like OkHttp's `HttpUrl` builder. */
private object QueryEncoding {

    fun encodeQueryValue(value: String): String {
        val out = StringBuilder(value.length)
        for (ch in value) {
            when {
                needsEncoding(ch) -> out.appendPercentEncoded(ch)
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private fun needsEncoding(ch: Char): Boolean {
        if (ch in QUERY_COMPONENT_ENCODE_SET) return true
        val code = ch.code
        return ch == '%' || code < 0x20 || code == 0x7F || code >= 0x80
    }

    private fun StringBuilder.appendPercentEncoded(ch: Char) {
        val bytes = ch.toString().toByteArray(Charsets.UTF_8)
        for (b in bytes) {
            val u = b.toInt() and 0xFF
            append('%')
            append("0123456789ABCDEF"[u shr 4])
            append("0123456789ABCDEF"[u and 0x0F])
        }
    }
}
