package com.bernaferrari.bravedns.service

import com.celzero.firestack.backend.Backend

/** Firestack d3d530ea3e protocol: comma-separated transports, each followed by colon-separated proxies. */
internal object DnsTransportRouting {
    fun encode(transports: String, proxies: String): String {
        val proxyIds = proxies.split(',').filter(String::isNotBlank)
        return transports.split(',').filter(String::isNotBlank).joinToString(",") { transport ->
            val id = transport.removePrefix(Backend.CT)
            val selected = if (ProxyManager.isAnyUserSetProxy(id)) {
                proxyIds.filter { it in setOf(Backend.Base, Backend.Block, Backend.Exit, Backend.Auto, Backend.Ingress) }
            } else proxyIds
            (listOf(transport) + selected).joinToString(":")
        }
    }

    fun sameTransports(left: String, right: String): Boolean =
        normalizedTransports(left) == normalizedTransports(right)

    private fun normalizedTransports(encoded: String): List<String> = transportIds(encoded)
        .split(',').filter(String::isNotBlank).map { it.removePrefix(Backend.CT) }.distinct()

    fun transportIds(encoded: String): String =
        encoded.split(',').filter(String::isNotBlank).joinToString(",") { it.substringBefore(':') }
}
