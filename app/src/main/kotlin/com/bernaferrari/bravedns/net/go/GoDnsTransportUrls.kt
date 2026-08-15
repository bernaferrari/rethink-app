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
import com.bernaferrari.bravedns.util.Constants.Companion.MAX_ENDPOINT
import com.bernaferrari.bravedns.util.Constants.Companion.RETHINKDNS_DOMAIN
import com.bernaferrari.bravedns.util.Constants.Companion.RETHINK_BASE_URL_MAX
import com.bernaferrari.bravedns.util.Constants.Companion.RETHINK_BASE_URL_SKY

/** Default DoT port when a host is given without one. Not applied unless the caller asks. */
internal const val DOT_DEFAULT_PORT = 853

/** Default DoH port when a host is given without one. Not applied unless the caller asks. */
internal const val DOH_DEFAULT_PORT = 443

/** Default plaintext DNS / DNS-proxy port when a host is given without one. */
internal const val DNS_PROXY_DEFAULT_PORT = 53

/**
 * DoH URL as passed to Intra. Insecure endpoints keep the historical
 * `https` → `http` substring replace (not scheme-only).
 */
internal fun dohTransportUrl(url: String, isSecure: Boolean): String {
    return if (!isSecure) url.replace("https", "http") else url
}

/** Host used for DoT IP bootstrap: strip a `tls://` prefix if present. */
internal fun dotLookupHost(url: String): String = url.replace("tls://", "")

/**
 * DoT URL as passed to Intra. Secure endpoints that do not already start
 * with `tls` (not `tls://`) get a `tls://` prefix — same predicate as before.
 */
internal fun dotTransportUrl(url: String, isSecure: Boolean): String {
    return if (isSecure && !url.startsWith("tls")) "tls://$url" else url
}

/**
 * Append [port] to each comma-separated proxy host. Empty / blank tokens are
 * kept (trimmed) so Intra sees the same CSV the adapter used to build inline.
 */
internal fun dnsProxyIpPortCsv(proxyIps: String, port: Int): String {
    return proxyIps.split(",").joinToString(",") { "${it.trim()}:$port" }
}

/** Non-blank DNSCrypt relay stamps from a comma-separated config string. */
internal fun dnscryptRelayStamps(routesCsv: String): List<String> {
    return routesCsv.split(",").filter { it.isNotBlank() }
}

internal fun isBlockFreeRdnsUrl(url: String): Boolean {
    return url == Constants.BLOCK_FREE_DNS_SKY ||
        url == Constants.BLOCK_FREE_DNS_MAX ||
        url == Constants.BLOCK_FREE_DNS_ZERO
}

/**
 * Path after the first `rethinkdns.com` occurrence, without a leading `/`.
 * Returns null when the domain is absent (malformed / non-Rethink URL).
 */
internal fun extractRdnsPathStamp(url: String): String? {
    if (!url.contains(RETHINKDNS_DOMAIN)) return null
    val after = url.split(RETHINKDNS_DOMAIN).getOrNull(1) ?: return null
    return if (after.startsWith("/")) after.substring(1) else after
}

/** Rebuild a Rethink DoH URL from a (possibly re-encoded) stamp. */
internal fun assembleRdnsUrl(url: String, stamp: String): String {
    return if (url.contains(MAX_ENDPOINT)) {
        "$RETHINK_BASE_URL_MAX$stamp"
    } else {
        "$RETHINK_BASE_URL_SKY$stamp"
    }
}

/**
 * Convert a configured Rethink URL into the DoH URL Intra expects.
 * [stampTransform] is the tunnel-backed base32 conversion; identity is fine
 * for tests and for block-free URLs (which never reach it).
 */
internal fun resolveRdnsTransportUrl(
    url: String,
    stampTransform: (String) -> String = { it }
): String? {
    if (!url.contains(RETHINKDNS_DOMAIN)) return null
    if (isBlockFreeRdnsUrl(url)) return url
    val stamp = extractRdnsPathStamp(url) ?: return null
    return assembleRdnsUrl(url, stampTransform(stamp))
}

/**
 * Host:port for a single endpoint. If [host] already has a port (or is a
 * bracketed IPv6 with port), it is returned unchanged; otherwise [defaultPort]
 * is appended.
 */
internal fun hostWithDefaultPort(host: String, defaultPort: Int): String {
    val trimmed = host.trim()
    if (trimmed.isEmpty()) return trimmed
    if (trimmed.startsWith("[")) {
        // [2001:db8::1] or [2001:db8::1]:853
        if (trimmed.contains("]:")) return trimmed
        return "$trimmed:$defaultPort"
    }
    // IPv6 without brackets has multiple ':' and no port we can detect safely
    val colonCount = trimmed.count { it == ':' }
    if (colonCount == 1) return trimmed
    if (colonCount > 1) return trimmed
    return "$trimmed:$defaultPort"
}
