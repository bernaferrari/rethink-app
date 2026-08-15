/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.network

import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins Ktor's URL handling of pre-encoded billing query strings.
 *
 * BillingRequestCodec emits OkHttp-compatible already-encoded queries
 * (including the load-bearing double-encoded purchaseToken). The Ktor
 * transport sends `baseUrl + pathAndQuery()` as one string; these tests
 * prove the URL builder round-trips those bytes unchanged — if Ktor ever
 * re-encodes `%` to `%25` (or `+` to `%2B`), the wire contract breaks and
 * these pins fail.
 */
class BillingUrlEncodingTest {

    private val base = "https://svc.rethinkdns.com"

    @Test
    fun `double-encoded purchase token round-trips unchanged`() {
        val pathAndQuery = "/g/ack?sku=standard.tier&purchaseToken=abc%252Bdef+ghi%252Fx%253Fy%253D%2526z&vcode=42"
        val builder = URLBuilder()
        builder.takeFrom(base + pathAndQuery)
        assertEquals(
            "https://svc.rethinkdns.com$pathAndQuery",
            builder.buildString(),
        )
    }

    @Test
    fun `single-encoded purchase token round-trips unchanged`() {
        val pathAndQuery = "/g/stop?sku=standard.tier&purchaseToken=abc+def%20ghi%2Fx%3Fy%3D%26z&vcode=42"
        val builder = URLBuilder()
        builder.takeFrom(base + pathAndQuery)
        assertEquals(
            "https://svc.rethinkdns.com$pathAndQuery",
            builder.buildString(),
        )
    }

    @Test
    fun `empty test query param round-trips unchanged`() {
        val pathAndQuery = "/d/acc?vcode=42&test="
        val builder = URLBuilder()
        builder.takeFrom(base + pathAndQuery)
        assertEquals(
            "https://svc.rethinkdns.com$pathAndQuery",
            builder.buildString(),
        )
    }

    @Test
    fun `method parse covers GET and POST`() {
        assertEquals(HttpMethod.Get, HttpMethod.parse("GET"))
        assertEquals(HttpMethod.Post, HttpMethod.parse("POST"))
    }
}
