/*
 * Copyright 2023 RethinkDNS and its authors
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.celzero.bravedns.wireguard

/**
 * External endpoint (host + port). DNS resolution is a platform expect/actual.
 */
class InetEndpoint private constructor(
    val host: String,
    private val isResolved: Boolean,
    val port: Int,
) {
    private val lock = Any()
    private var lastResolutionMs: Long = 0L
    private var resolved: InetEndpoint? = null

    override fun equals(obj: Any?): Boolean {
        if (obj !is InetEndpoint) return false
        return host == obj.host && port == obj.port
    }

    fun getResolved(): InetEndpoint? {
        if (isResolved) return this
        val now = currentTimeMillisCompat()
        if (now - lastResolutionMs > 60_000L) {
            val numeric = resolveHostToNumeric(host)
            resolved = numeric?.let { InetEndpoint(it, true, port) }
            lastResolutionMs = now
        }
        return resolved
    }

    override fun hashCode(): Int = host.hashCode() xor port

    override fun toString(): String {
        val isBareIpv6 = isResolved && host.contains(":") && !host.startsWith("[")
        return (if (isBareIpv6) "[$host]" else host) + ":" + port
    }

    companion object {
        private val FORBIDDEN = Regex("[/?#]")

        @Throws(ParseException::class)
        fun parse(endpoint: String): InetEndpoint {
            if (FORBIDDEN.containsMatchIn(endpoint))
                throw ParseException("InetEndpoint", endpoint, "Forbidden characters")
            // Minimal host:port / [ipv6]:port parse without java.net.URI
            val host: String
            val portStr: String
            if (endpoint.startsWith("[")) {
                val close = endpoint.indexOf(']')
                if (close < 0) throw ParseException("InetEndpoint", endpoint, "Bad IPv6 bracket")
                host = endpoint.substring(1, close)
                if (close + 1 >= endpoint.length || endpoint[close + 1] != ':')
                    throw ParseException("InetEndpoint", endpoint, "Missing/invalid port number")
                portStr = endpoint.substring(close + 2)
            } else {
                val colon = endpoint.lastIndexOf(':')
                if (colon < 0) throw ParseException("InetEndpoint", endpoint, "Missing/invalid port number")
                host = endpoint.substring(0, colon)
                portStr = endpoint.substring(colon + 1)
            }
            val port = portStr.toIntOrNull()
                ?: throw ParseException("InetEndpoint", endpoint, "Missing/invalid port number")
            if (port < 0 || port > 65535)
                throw ParseException("InetEndpoint", endpoint, "Missing/invalid port number")
            return try {
                InetAddresses.parse(host)
                InetEndpoint(host, true, port)
            } catch (_: ParseException) {
                InetEndpoint(host, false, port)
            }
        }
    }
}

internal expect fun currentTimeMillisCompat(): Long
internal expect fun resolveHostToNumeric(host: String): String?
