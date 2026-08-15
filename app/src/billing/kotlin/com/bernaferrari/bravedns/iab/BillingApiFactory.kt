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
import com.bernaferrari.bravedns.customdownloader.IBillingServerApi
import com.bernaferrari.bravedns.customdownloader.IBillingServerTestApi
import com.bernaferrari.bravedns.customdownloader.RetrofitManager
import com.bernaferrari.bravedns.customdownloader.SafeResponseConverterFactory
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.JsonHelper
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

/**
 * Retrofit wiring for the Rethink billing backend: test vs production interfaces,
 * session-bookmark interceptors, and the production fallback hop.
 */
internal class BillingApiFactory(
    private val persistentState: PersistentState
) {

    companion object {
        private const val TAG = "BillingBackendClient"

        private const val DB_SESSION_HEADER = "x-rethink-db-rpn-session"
        private const val DB_SESSION_TEST_HEADER = "x-rethink-db-rpn-test-session"
        private const val DB_SESSION_RESPONSE_HEADER = "x-db-rpn-session"
        private const val DB_SESSION_TEST_RESPONSE_HEADER = "x-db-rpn-test-session"
        private const val DID_TOKEN_HEADER = "x-rethink-app-did-token"
        private const val DB_SESSION_UNCONSTRAINED = "first-unconstrained"

        private val jsonMediaType = "application/json; charset=UTF-8".toMediaType()
        private val retrofitJson = Json(JsonHelper.json) {
            coerceInputValues = true
        }
    }

    /** Latest session bookmark received from the production server. */
    @Volatile private var dbSessionBookmark: String? = null

    /** Latest session bookmark received from the test server. */
    @Volatile private var dbSessionTestBookmark: String? = null

    /** Latest DID token received from the server; echoed on every subsequent request. */
    @Volatile private var didToken: String? = null

    /**
     * Discriminated union returned by [resolveApi].
     *
     * [Production] carries the standard [IBillingServerApi] instance; the `test`
     * query param must be passed as `null` for every call to ensure it is omitted
     * from the URL entirely.
     *
     * [Test] carries the [IBillingServerTestApi] instance plus the non-null test
     * string that must be forwarded as the `test` query param.
     */
    sealed class ApiHandle {
        /** Human-readable label for log messages: "prod" or "test". */
        abstract val envLabel: String
        /** Use for production entitlements. Pass `test = null` to every endpoint. */
        data class Production(val api: IBillingServerApi) : ApiHandle() {
            override val envLabel = "prod"
        }
        /** Use for test entitlements. Pass `test = testValue` to every endpoint. */
        data class Test(val api: IBillingServerTestApi, val testValue: Boolean) : ApiHandle() {
            override val envLabel = "test"
        }
    }

    /**
     * Resolves which Retrofit interface to use for the current request.
     *
     * Calls [RpnProxyManager.getIsTestEntitlement] **once** and returns:
     * - [ApiHandle.Test]       when the entitlement is a test one (non-null value).
     * - [ApiHandle.Production] when the entitlement is production (null value) or when
     *   the lookup throws (fail-safe: always prefer production on error).
     *
     * The Retrofit instances are created fresh per-call, Retrofit interface creation
     * is lightweight (no network I/O) and this avoids threading concerns around caching
     * a potentially stale test/production split.
     */
    suspend fun resolveApi(): ApiHandle {
        val testValue = try {
            RpnProxyManager.getIsTestEntitlement()
        } catch (e: IllegalStateException) {
            Logger.w(LOG_IAB, "$TAG resolveApi: getIsTestEntitlement, ${e.message}", e)
            // happens when there is server calls before entitlement
            val test = persistentState.appTestMode
            Logger.w(LOG_IAB, "$TAG resolveApi: using TEST interface (testValue=$test)")
            test
        }
        return if (testValue) {
            Logger.d(LOG_IAB, "$TAG resolveApi: using TEST interface")
            ApiHandle.Test(buildTestApi(), true)
        } else {
            Logger.d(LOG_IAB, "$TAG resolveApi: using PROD interface")
            ApiHandle.Production(buildProductionApi())
        }
    }

    /** Builds a Retrofit instance for the production billing API. */
    fun buildProductionApi(): IBillingServerApi {
        val client = RetrofitManager
            .okHttpClient(persistentState.routeRethinkInRethink)
            .newBuilder()
            // Outermost: on 5xx or network IOException, transparently retries against
            // RPN_FALLBACK_URL before the response surfaces to Retrofit. Must be added
            // before the session interceptor so that the session interceptor still runs
            // (and correctly attaches headers) for the fallback request as well.
            .addInterceptor(buildFallbackInterceptor())
            .addInterceptor(buildSessionInterceptor(
                DB_SESSION_HEADER, DB_SESSION_RESPONSE_HEADER,
                { dbSessionBookmark }, { dbSessionBookmark = it }, "prod"
            ))
            .build()
        return Retrofit.Builder()
            .baseUrl(Constants.RPN_BASE_URL)
            .client(client)
            .addConverterFactory(SafeResponseConverterFactory())
            .addConverterFactory(retrofitJson.asConverterFactory(jsonMediaType))
            .build()
            .create(IBillingServerApi::class.java)
    }

    /**
     * Builds a Retrofit instance for the test billing API.
     *
     * Test calls always target [Constants.RPN_FALLBACK_URL] directly — no primary/fallback
     * split is needed for the test path.
     */
    fun buildTestApi(): IBillingServerTestApi {
        val client = RetrofitManager
            .okHttpClient(persistentState.routeRethinkInRethink)
            .newBuilder()
            .addInterceptor(buildSessionInterceptor(
                DB_SESSION_TEST_HEADER, DB_SESSION_TEST_RESPONSE_HEADER,
                { dbSessionTestBookmark }, { dbSessionTestBookmark = it }, "test"
            ))
            .build()
        return Retrofit.Builder()
            .baseUrl(Constants.RPN_FALLBACK_URL)
            .client(client)
            .addConverterFactory(SafeResponseConverterFactory())
            .addConverterFactory(retrofitJson.asConverterFactory(jsonMediaType))
            .build()
            .create(IBillingServerTestApi::class.java)
    }

    /**
     * OkHttp Application Interceptor that transparently retries a failed production request
     * against [Constants.RPN_FALLBACK_URL] when [Constants.RPN_BASE_URL] is unreachable or
     * returns a server error.
     *
     * ### Fallback triggers
     * - **5xx** HTTP responses (server-side errors): the primary response is closed and the
     *   request is re-issued against the fallback URL.
     * - **[IOException]** (covers [java.net.UnknownHostException], [java.net.ConnectException],
     *   [java.net.SocketTimeoutException], and all other network-level failures): the request
     *   is re-issued against the fallback URL.
     *
     * ### Non-fallback cases
     * - **4xx** client errors are returned as-is; the request cannot succeed on a different
     *   server (the error is in the request itself, not the server).
     * - **2xx / 3xx** responses are returned as-is.
     *
     * ### Interceptor ordering
     * This interceptor is added as the **outermost** application interceptor (before the
     * session interceptor). When the fallback request is issued via `chain.proceed()`, it
     * passes through the session interceptor again, so session headers (bookmarks, DID token)
     * are attached correctly to the fallback request too.
     *
     * Only used for [IBillingServerApi] (production). [IBillingServerTestApi] already
     * targets [Constants.RPN_FALLBACK_URL] directly and does not need this interceptor.
     */
    private fun buildFallbackInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        try {
            val response = chain.proceed(request)
            if (response.code in 500..599) {
                Logger.w(LOG_IAB, "$TAG fallback: server error ${response.code} on ${request.url}, retrying with fallback URL")
                response.close()
                val fallbackRequest = request.newBuilder()
                    .url(request.url.toString().replace(Constants.RPN_BASE_URL, Constants.RPN_FALLBACK_URL))
                    .build()
                chain.proceed(fallbackRequest)
            } else {
                response
            }
        } catch (e: IOException) {
            Logger.w(LOG_IAB, "$TAG fallback: network error (${e.message}) on ${request.url}, retrying with fallback URL")
            val fallbackRequest = request.newBuilder()
                .url(request.url.toString().replace(Constants.RPN_BASE_URL, Constants.RPN_FALLBACK_URL))
                .build()
            chain.proceed(fallbackRequest)
        }
    }

    /**
     * OkHttp interceptor that manages session bookmarks and the DID token header.
     *
     * Requests without the session header are passed through unmodified.
     * On read endpoints (session header == [DB_SESSION_UNCONSTRAINED]), the cached
     * bookmark is substituted if one is available. The DID token, if present, is
     * added to every session-bearing request. Bookmark and DID token are updated
     * from each server response.
     */
    private fun buildSessionInterceptor(
        reqHeader: String,
        respHeader: String,
        getBookmark: () -> String?,
        setBookmark: (String) -> Unit,
        tag: String
    ): Interceptor = Interceptor { chain ->
        val original = chain.request()
        val sessionHdr = original.header(reqHeader) ?: return@Interceptor chain.proceed(original)

        val reqBuilder = original.newBuilder()
        if (sessionHdr == DB_SESSION_UNCONSTRAINED) {
            getBookmark()?.let { reqBuilder.header(reqHeader, it) }
        }
        didToken?.let { reqBuilder.header(DID_TOKEN_HEADER, it) }

        val response = chain.proceed(reqBuilder.build())

        response.header(respHeader)?.takeIf { it.isNotBlank() }?.let {
            setBookmark(it)
            if (DEBUG) Logger.d(LOG_IAB, "$TAG interceptor($tag): bookmark updated (len=${it.length})")
        }
        response.header(DID_TOKEN_HEADER)?.takeIf { it.isNotBlank() }?.let {
            didToken = it
            if (DEBUG) Logger.d(LOG_IAB, "$TAG interceptor($tag): didToken updated (len=${it.length})")
        }
        response
    }
}
