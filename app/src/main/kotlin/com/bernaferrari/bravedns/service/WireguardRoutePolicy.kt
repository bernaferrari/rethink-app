package com.bernaferrari.bravedns.service

import inet.ipaddr.IPAddressString

/** Checks configured coverage, rather than just whether the unspecified address is routed. */
internal object WireguardRoutePolicy {
    fun isSplit(allowedIps: Collection<String>): Boolean {
        val addresses = allowedIps.mapNotNull { IPAddressString(it).address?.toPrefixBlock() }
        if (addresses.isEmpty()) return false
        return addresses.groupBy { it.isIPv4 }.values.any { family ->
            val merged = family.first().mergeToPrefixBlocks(*family.drop(1).toTypedArray())
            merged.none { it.networkPrefixLength == 0 }
        }
    }
}
