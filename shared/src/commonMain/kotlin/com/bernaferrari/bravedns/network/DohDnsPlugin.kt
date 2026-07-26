/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders

internal val DohDnsPlugin =
    createClientPlugin("DohDns") {
        onRequest { request, _ ->
            val originalHost = request.url.host
            val addresses = DohDnsResolver.resolve(originalHost)
            if (addresses.isEmpty()) return@onRequest
            request.url.host = addresses.first()
            request.headers.appendIfNameAbsent(HttpHeaders.Host, originalHost)
        }
    }

private fun io.ktor.http.HeadersBuilder.appendIfNameAbsent(name: String, value: String) {
    if (!contains(name)) append(name, value)
}
