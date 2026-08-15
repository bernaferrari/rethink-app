/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package com.bernaferrari.bravedns.net.go

import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Constants.Companion.RETHINK_BASE_URL_MAX
import com.bernaferrari.bravedns.util.Constants.Companion.RETHINK_BASE_URL_SKY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoDnsTransportUrlsTest {

    @Test
    fun `dohTransportUrl keeps https when secure`() {
        val url = "https://dns.google/dns-query"
        assertEquals(url, dohTransportUrl(url, isSecure = true))
    }

    @Test
    fun `dohTransportUrl replaces https substring when insecure`() {
        assertEquals(
            "http://dns.google/dns-query",
            dohTransportUrl("https://dns.google/dns-query", isSecure = false)
        )
    }

    @Test
    fun `dohTransportUrl insecure replace is substring not scheme-only`() {
        // historical adapter behavior: replace("https", "http") hits every occurrence
        assertEquals(
            "http://example.com/http-path",
            dohTransportUrl("https://example.com/https-path", isSecure = false)
        )
    }

    @Test
    fun `dohTransportUrl leaves already-http insecure url unchanged`() {
        val url = "http://192.168.1.1/dns-query"
        assertEquals(url, dohTransportUrl(url, isSecure = false))
    }

    @Test
    fun `dotLookupHost strips tls scheme`() {
        assertEquals("one.one.one.one", dotLookupHost("tls://one.one.one.one"))
        assertEquals("one.one.one.one:853", dotLookupHost("tls://one.one.one.one:853"))
    }

    @Test
    fun `dotLookupHost leaves host without scheme`() {
        assertEquals("dns.quad9.net", dotLookupHost("dns.quad9.net"))
    }

    @Test
    fun `dotTransportUrl prefixes tls when secure and missing`() {
        assertEquals("tls://dns.quad9.net", dotTransportUrl("dns.quad9.net", isSecure = true))
    }

    @Test
    fun `dotTransportUrl does not double-prefix tls scheme`() {
        assertEquals("tls://dns.quad9.net", dotTransportUrl("tls://dns.quad9.net", isSecure = true))
    }

    @Test
    fun `dotTransportUrl treats tls substring start as already prefixed`() {
        // startsWith("tls") not startsWith("tls://")
        assertEquals("tlsdot.example", dotTransportUrl("tlsdot.example", isSecure = true))
    }

    @Test
    fun `dotTransportUrl leaves insecure url unchanged`() {
        assertEquals("10.0.0.1:853", dotTransportUrl("10.0.0.1:853", isSecure = false))
        assertEquals("tls://10.0.0.1", dotTransportUrl("tls://10.0.0.1", isSecure = false))
    }

    @Test
    fun `dnsProxyIpPortCsv appends port to single host`() {
        assertEquals("1.1.1.1:53", dnsProxyIpPortCsv("1.1.1.1", 53))
    }

    @Test
    fun `dnsProxyIpPortCsv appends same port to each csv host and trims`() {
        assertEquals(
            "1.1.1.1:5353,8.8.8.8:5353",
            dnsProxyIpPortCsv("1.1.1.1, 8.8.8.8", 5353)
        )
    }

    @Test
    fun `dnsProxyIpPortCsv keeps empty tokens`() {
        assertEquals(":53,1.1.1.1:53", dnsProxyIpPortCsv(",1.1.1.1", 53))
    }

    @Test
    fun `dnscryptRelayStamps drops blank entries and keeps raw tokens`() {
        assertEquals(
            listOf("sdns://relay-a", " sdns://relay-b"),
            dnscryptRelayStamps("sdns://relay-a,  , sdns://relay-b")
        )
        assertTrue(dnscryptRelayStamps("").isEmpty())
        assertTrue(dnscryptRelayStamps(",, ,").isEmpty())
    }

    @Test
    fun `isBlockFreeRdnsUrl matches only the three known endpoints`() {
        assertTrue(isBlockFreeRdnsUrl(Constants.BLOCK_FREE_DNS_SKY))
        assertTrue(isBlockFreeRdnsUrl(Constants.BLOCK_FREE_DNS_MAX))
        assertTrue(isBlockFreeRdnsUrl(Constants.BLOCK_FREE_DNS_ZERO))
        assertFalse(isBlockFreeRdnsUrl("https://sky.rethinkdns.com/1:abc"))
        assertFalse(isBlockFreeRdnsUrl(""))
    }

    @Test
    fun `extractRdnsPathStamp returns null without rethink domain`() {
        assertNull(extractRdnsPathStamp("https://cloudflare-dns.com/dns-query"))
        assertNull(extractRdnsPathStamp(""))
        assertNull(extractRdnsPathStamp("not-a-url"))
    }

    @Test
    fun `extractRdnsPathStamp strips leading slash after domain`() {
        assertEquals(
            "1:-AcHAL6d_5-Yxf7zMQAKAAAI",
            extractRdnsPathStamp("https://max.rethinkdns.com/1:-AcHAL6d_5-Yxf7zMQAKAAAI")
        )
        assertEquals(
            "dns-query",
            extractRdnsPathStamp("https://sky.rethinkdns.com/dns-query")
        )
    }

    @Test
    fun `extractRdnsPathStamp keeps path when slash is missing`() {
        assertEquals("1:abc", extractRdnsPathStamp("https://sky.rethinkdns.com1:abc"))
        assertEquals("", extractRdnsPathStamp("https://sky.rethinkdns.com"))
    }

    @Test
    fun `assembleRdnsUrl uses max base when url contains max`() {
        assertEquals(
            "${RETHINK_BASE_URL_MAX}stamp32",
            assembleRdnsUrl("https://max.rethinkdns.com/old", "stamp32")
        )
    }

    @Test
    fun `assembleRdnsUrl uses sky base otherwise`() {
        assertEquals(
            "${RETHINK_BASE_URL_SKY}stamp32",
            assembleRdnsUrl("https://sky.rethinkdns.com/old", "stamp32")
        )
    }

    @Test
    fun `resolveRdnsTransportUrl rejects urls without rethink domain substring`() {
        assertNull(resolveRdnsTransportUrl("https://dns.google/dns-query"))
        assertNull(resolveRdnsTransportUrl("https://example.com/rethink-dns.com.evil"))
    }

    @Test
    fun `resolveRdnsTransportUrl treats rethink domain as substring`() {
        assertEquals(
            "${RETHINK_BASE_URL_SKY}stamp",
            resolveRdnsTransportUrl("https://evil.example/rethinkdns.com/stamp")
        )
    }

    @Test
    fun `resolveRdnsTransportUrl returns block-free urls unchanged`() {
        assertEquals(
            Constants.BLOCK_FREE_DNS_SKY,
            resolveRdnsTransportUrl(Constants.BLOCK_FREE_DNS_SKY) { error("should not transform") }
        )
        assertEquals(
            Constants.BLOCK_FREE_DNS_MAX,
            resolveRdnsTransportUrl(Constants.BLOCK_FREE_DNS_MAX) { error("should not transform") }
        )
        assertEquals(
            Constants.BLOCK_FREE_DNS_ZERO,
            resolveRdnsTransportUrl(Constants.BLOCK_FREE_DNS_ZERO) { error("should not transform") }
        )
    }

    @Test
    fun `resolveRdnsTransportUrl applies stamp transform then assembles`() {
        val src = "https://max.rethinkdns.com/1:base64stamp"
        val out = resolveRdnsTransportUrl(src) { stamp ->
            assertEquals("1:base64stamp", stamp)
            "1-base32stamp"
        }
        assertEquals("${RETHINK_BASE_URL_MAX}1-base32stamp", out)
    }

    @Test
    fun `resolveRdnsTransportUrl identity transform rebuilds sky url`() {
        val src = "https://sky.rethinkdns.com/abc"
        assertEquals("${RETHINK_BASE_URL_SKY}abc", resolveRdnsTransportUrl(src))
    }

    @Test
    fun `hostWithDefaultPort appends when no port present`() {
        assertEquals("1.1.1.1:53", hostWithDefaultPort("1.1.1.1", DNS_PROXY_DEFAULT_PORT))
        assertEquals("dns.quad9.net:853", hostWithDefaultPort("dns.quad9.net", DOT_DEFAULT_PORT))
        assertEquals("dns.google:443", hostWithDefaultPort(" dns.google ", DOH_DEFAULT_PORT))
    }

    @Test
    fun `hostWithDefaultPort keeps existing host port`() {
        assertEquals("1.1.1.1:5353", hostWithDefaultPort("1.1.1.1:5353", DNS_PROXY_DEFAULT_PORT))
    }

    @Test
    fun `hostWithDefaultPort leaves bare ipv6 unchanged`() {
        assertEquals("2001:db8::1", hostWithDefaultPort("2001:db8::1", 53))
    }

    @Test
    fun `hostWithDefaultPort handles bracketed ipv6`() {
        assertEquals("[2001:db8::1]:53", hostWithDefaultPort("[2001:db8::1]", 53))
        assertEquals("[2001:db8::1]:853", hostWithDefaultPort("[2001:db8::1]:853", 53))
    }

    @Test
    fun `hostWithDefaultPort returns empty for blank input`() {
        assertEquals("", hostWithDefaultPort("   ", 53))
        assertEquals("", hostWithDefaultPort("", 53))
    }
}
