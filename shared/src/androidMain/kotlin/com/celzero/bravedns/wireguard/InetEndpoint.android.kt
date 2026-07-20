
package com.celzero.bravedns.wireguard

import java.net.Inet4Address
import java.net.InetAddress

internal actual fun currentTimeMillisCompat(): Long = System.currentTimeMillis()

internal actual fun resolveHostToNumeric(host: String): String? = try {
    val candidates = InetAddress.getAllByName(host)
    var address = candidates[0]
    for (candidate in candidates) {
        if (candidate is Inet4Address) { address = candidate; break }
    }
    address.hostAddress
} catch (_: Exception) { null }
