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
import com.bernaferrari.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.bernaferrari.bravedns.network.HttpClientManager
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.util.Constants
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import java.io.IOException

/**
 * Ktor transport for the Rethink billing backend. Executes the exact request
 * shapes built by [BillingRequestCodec], applies session-bookmark and DID-token
 * interception (the former OkHttp session interceptor), and — for production
 * calls only — transparently retries server errors and network failures against
 * [Constants.RPN_FALLBACK_URL] (the former OkHttp fallback interceptor).
 *
 * Test calls always target [Constants.RPN_FALLBACK_URL] directly, mirroring the
 * former `IBillingServerTestApi` Retrofit wiring.
 */
internal class BillingApiFactory(
    private val persistentState: PersistentState
) {

    companion object {
        private const val TAG = "BillingBackendClient"
    }

    /** Latest session bookmark received from the production server. */
    @Volatile private var dbSessionBookmark: String? = null

    /** Latest session bookmark received from the test server. */
    @Volatile private var dbSessionTestBookmark: String? = null

    /** Latest DID token received from the server; echoed on every subsequent request. */
    @Volatile private var didToken: String? = null

    /**
     * Billing environment for one logical call: `test` calls carry the `test`
     * query param and hit the fallback (test) server directly; production calls
     * hit [Constants.RPN_BASE_URL] with the transparent fallback hop.
     */
    data class BillingEnv(val test: Boolean) {
        val envLabel: String get() = if (test) "test" else "prod"
    }

    /**
     * Resolves which environment the current request must use.
     *
     * Calls [RpnProxyManager.getIsTestEntitlement] **once** and returns a test
     * env when the entitlement is a test one. On [IllegalStateException]
     * (server calls before any entitlement exists) it fails safe to the
     * persisted [PersistentState.appTestMode].
     */
    suspend fun resolveEnv(): BillingEnv {
        val testValue = try {
            RpnProxyManager.getIsTestEntitlement()
        } catch (e: IllegalStateException) {
            Logger.w(LOG_IAB, "$TAG resolveEnv: getIsTestEntitlement, ${e.message}", e)
            // happens when there are server calls before entitlement
            val test = persistentState.appTestMode
            Logger.w(LOG_IAB, "$TAG resolveEnv: using TEST interface (testValue=$test)")
            test
        }
        return if (testValue) {
            Logger.d(LOG_IAB, "$TAG resolveEnv: using TEST interface")
            BillingEnv(test = true)
        } else {
            Logger.d(LOG_IAB, "$TAG resolveEnv: using PROD interface")
            BillingEnv(test = false)
        }
    }

    /**
     * Executes [spec] against the billing backend.
     *
     * ### Fallback (production only)
     * - **5xx** responses: the request is re-issued against [Constants.RPN_FALLBACK_URL].
     * - **[IOException]** (covers [java.net.UnknownHostException],
     *   [java.net.ConnectException], timeouts and all other network-level failures):
     *   the request is re-issued against the fallback URL.
     * - **4xx** responses are returned as-is; the request cannot succeed on a
     *   different server.
     *
     * Test calls (and their failures) are returned/thrown without a fallback hop.
     */
    suspend fun execute(
        spec: BillingRequestCodec.RequestSpec,
        env: BillingEnv,
    ): BillingHttpResponse {
        // Test calls always target the fallback (test) server directly — no
        // primary/fallback split exists on the test path (former buildTestApi).
        val primaryUrl = if (env.test) Constants.RPN_FALLBACK_URL else Constants.RPN_BASE_URL
        return try {
            val primary = executeOnce(spec, env.test, primaryUrl)
            if (!env.test && primary.code in 500..599) {
                Logger.w(
                    LOG_IAB,
                    "$TAG fallback: server error ${primary.code} on ${primary.requestUrl}, " +
                        "retrying with fallback URL"
                )
                executeOnce(spec, env.test, Constants.RPN_FALLBACK_URL)
            } else {
                primary
            }
        } catch (e: IOException) {
            if (env.test) throw e
            Logger.w(
                LOG_IAB,
                "$TAG fallback: network error (${e.message}) on ${spec.pathAndQuery()}, " +
                    "retrying with fallback URL"
            )
            executeOnce(spec, env.test, Constants.RPN_FALLBACK_URL)
        }
    }

    /**
     * One request/response round-trip. [baseUrl] selects the primary or the
     * fallback server; the session-bookmark substitution and DID-token echo
     * apply to whichever hop runs, exactly like the former interceptor chain.
     */
    private suspend fun executeOnce(
        spec: BillingRequestCodec.RequestSpec,
        test: Boolean,
        baseUrl: String,
    ): BillingHttpResponse {
        val url = baseUrl.trimEnd('/') + spec.pathAndQuery()
        val sessionHeaderName = if (test) {
            BillingRequestCodec.HEADER_SESSION_TEST
        } else {
            BillingRequestCodec.HEADER_SESSION
        }
        val sessionResponseName = if (test) {
            BillingRequestCodec.HEADER_SESSION_TEST_RESPONSE
        } else {
            BillingRequestCodec.HEADER_SESSION_RESPONSE
        }

        val client = HttpClientManager.billingClient(persistentState.routeRethinkInRethink)
        val response: HttpResponse = client.request(url) {
            method = HttpMethod.parse(spec.method)
            spec.headers.forEach { (name, value) ->
                header(name, substituteSession(name, value, sessionHeaderName, test))
            }
            didToken?.let { header(BillingRequestCodec.HEADER_DID_TOKEN, it) }
            spec.jsonBody?.let { bodyText ->
                header(HttpHeaders.ContentType, BillingRequestCodec.JSON_MEDIA_TYPE)
                setBody(bodyText)
            }
        }

        response.headers[sessionResponseName]?.takeIf { it.isNotBlank() }?.let {
            setSessionBookmark(test, it)
            if (DEBUG) Logger.d(LOG_IAB, "$TAG interceptor(${envLabel(test)}): bookmark updated (len=${it.length})")
        }
        response.headers[BillingRequestCodec.HEADER_DID_TOKEN]?.takeIf { it.isNotBlank() }?.let {
            didToken = it
            if (DEBUG) Logger.d(LOG_IAB, "$TAG interceptor(${envLabel(test)}): didToken updated (len=${it.length})")
        }

        val rawBody = response.bodyAsText()
        val code = response.status.value
        val successful = code in 200..299
        return BillingHttpResponse(
            code = code,
            body = if (successful) BillingApiJson.parseObjectOrNull(rawBody) else null,
            errorBody = if (successful) null else rawBody,
            message = "",
            requestUrl = response.request.url.toString(),
        )
    }

    /**
     * Read endpoints carry the `first-unconstrained` session value; when a
     * server bookmark is cached it is substituted so the read is routed to the
     * replica the account was last seen on.
     */
    private fun substituteSession(
        name: String,
        value: String,
        sessionHeaderName: String,
        test: Boolean,
    ): String {
        if (name != sessionHeaderName) return value
        if (value != BillingRequestCodec.SESSION_UNCONSTRAINED) return value
        return sessionBookmark(test) ?: value
    }

    private fun sessionBookmark(test: Boolean): String? =
        if (test) dbSessionTestBookmark else dbSessionBookmark

    private fun setSessionBookmark(test: Boolean, value: String) {
        if (test) dbSessionTestBookmark = value else dbSessionBookmark = value
    }

    private fun envLabel(test: Boolean): String = if (test) "test" else "prod"
}
