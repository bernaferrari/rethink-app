/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.network

import com.bernaferrari.bravedns.platform.platformHttpEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.path

/** Resolves hostnames via public DoH resolvers. Returns textual IP addresses. */
internal object DohDnsResolver {
    private data class DohProvider(
        val host: String,
        val path: String,
        val bootstrapIps: List<String>,
    )

    private val providers =
        listOf(
            DohProvider("dns.quad9.net", "/dns-query", listOf("9.9.9.9", "149.112.112.112")),
            DohProvider("cloudflare-dns.com", "/dns-query", listOf("1.1.1.1", "1.0.0.1")),
            DohProvider("dns.google", "/dns-query", listOf("8.8.8.8", "8.8.4.4")),
        )

    private val bootstrapClient by lazy {
        HttpClient(platformHttpEngine()) {
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                requestTimeoutMillis = 30_000
            }
        }
    }

    suspend fun resolve(hostname: String): List<String> {
        if (isIpLiteral(hostname)) return listOf(hostname)
        for (provider in providers) {
            try {
                val addresses = resolveViaDoh(hostname, provider)
                if (addresses.isNotEmpty()) return addresses
            } catch (_: Exception) {
                // try next provider
            }
        }
        return emptyList()
    }

    private suspend fun resolveViaDoh(hostname: String, provider: DohProvider): List<String> {
        val query = DnsWireFormat.buildQuery(hostname)
        for (ip in provider.bootstrapIps) {
            try {
                val response =
                    bootstrapClient.post {
                        url {
                            protocol = URLProtocol.HTTPS
                            host = ip
                            path(provider.path)
                        }
                        headers.append(HttpHeaders.Host, provider.host)
                        headers.append(HttpHeaders.Accept, "application/dns-message")
                        contentType(ContentType.Application.OctetStream)
                        setBody(query)
                    }.bodyAsBytes()
                val addresses = DnsWireFormat.parseAddresses(response)
                if (addresses.isNotEmpty()) return addresses
            } catch (_: Exception) {
                continue
            }
        }
        return emptyList()
    }

    private fun isIpLiteral(host: String): Boolean {
        if (host.contains(':')) return true
        val parts = host.split('.')
        return parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
    }
}
