package com.bernaferrari.bravedns.service

import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import java.math.BigInteger

/** Checks configured coverage, rather than just whether the unspecified address is routed. */
internal object WireguardRoutePolicy {
    fun isSplit(allowedIps: Collection<String>): Boolean {
        val addresses = allowedIps.mapNotNull { IPAddressString(it).address?.toPrefixBlock() }
        if (addresses.isEmpty()) return false
        return addresses.groupBy { it.isIPv4 }.values.any { !coversFamily(it) }
    }

    private fun coversFamily(addresses: List<IPAddress>): Boolean {
        var next = BigInteger.ZERO
        val limit = BigInteger.ONE.shiftLeft(if (addresses.first().isIPv4) 32 else 128)
        addresses.sortedBy { it.lower.value }.forEach { address ->
            if (address.lower.value > next) return false
            next = next.max(address.upper.value + BigInteger.ONE)
        }
        return next == limit
    }
}
