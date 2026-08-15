/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.network

import com.bernaferrari.bravedns.platform.platformHttpEngine
import com.bernaferrari.bravedns.util.Constants
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object HttpClientManager {
    private val clients = mutableMapOf<String, HttpClient>()

    fun blocklistClient(isRinRActive: Boolean): HttpClient =
        client(Constants.DOWNLOAD_BASE_URL, isRinRActive, longTimeouts = true)

    fun tcpProxyClient(isRinRActive: Boolean): HttpClient =
        client(Constants.TCP_PROXY_BASE_URL, isRinRActive, longTimeouts = true)

    fun ipInfoClient(isRinRActive: Boolean): HttpClient =
        client(Constants.IP_INFO_BASE_URL, isRinRActive, longTimeouts = true)

    fun stripeClient(): HttpClient = client("https://api.stripe.com/", isRinRActive = false, longTimeouts = false)

    /**
     * Billing backend client. DoH polarity is faithful to the former RetrofitManager
     * wiring the billing stack shipped with for years: app-side DoH is installed only
     * when Rethink-in-Rethink is OFF — outside the tunnel the system resolver can be
     * poisoned, while inside the loop the tunnel's own resolver handles DNS.
     *
     * NOTE: this is deliberately the opposite of [blocklistClient] and siblings, which
     * install DoH when RinR is ON. Unify only with a deliberate behavior change, not as
     * a drive-by of this migration.
     *
     * Deliberately NOT configured with [HttpRequestRetry]: billing POSTs are writes
     * (ack / consume / cancel); a blind exception-retry could re-issue a delivered
     * write. The old OkHttp client had no such retry either — the transport-level
     * fallback hop in BillingApiFactory is the only retry path.
     */
    fun billingClient(routeRethinkInRethink: Boolean): HttpClient {
        val key = "billing-$routeRethinkInRethink"
        return clients.getOrPut(key) {
            HttpClient(platformHttpEngine()) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 1.minutes.inWholeMilliseconds
                    socketTimeoutMillis = 20.minutes.inWholeMilliseconds
                    requestTimeoutMillis = 20.minutes.inWholeMilliseconds
                }
                if (!routeRethinkInRethink) {
                    install(DohDnsPlugin)
                }
            }
        }
    }

    fun imageClient(): HttpClient =
        client("https://placeholder.local/", isRinRActive = false, longTimeouts = false, shortTimeouts = true)

    fun genericClient(isRinRActive: Boolean): HttpClient =
        client("https://placeholder.local/", isRinRActive, longTimeouts = true)

    private fun client(
        baseUrl: String,
        isRinRActive: Boolean,
        longTimeouts: Boolean,
        shortTimeouts: Boolean = false,
    ): HttpClient {
        val key = "$baseUrl-$isRinRActive-$longTimeouts-$shortTimeouts"
        return clients.getOrPut(key) {
            HttpClient(platformHttpEngine()) {
                install(HttpTimeout) {
                    if (shortTimeouts) {
                        connectTimeoutMillis = 3.seconds.inWholeMilliseconds
                        socketTimeoutMillis = 5.seconds.inWholeMilliseconds
                        requestTimeoutMillis = 5.seconds.inWholeMilliseconds
                    } else if (longTimeouts) {
                        connectTimeoutMillis = 1.minutes.inWholeMilliseconds
                        socketTimeoutMillis = 20.minutes.inWholeMilliseconds
                        requestTimeoutMillis = 20.minutes.inWholeMilliseconds
                    }
                }
                install(HttpRequestRetry) {
                    retryOnException(maxRetries = 2)
                }
                if (isRinRActive) {
                    install(DohDnsPlugin)
                }
            }
        }
    }
}
