package com.bernaferrari.bravedns.service

import android.net.NetworkCapabilities

/** Only cached underlay capabilities establish Wi-Fi; neither an absent SSID nor noncellular implies it. */
internal fun ConnectionMonitor.UnderlyingNetworks?.isKnownWifi(ipv6: Boolean? = null): Boolean {
    val networks = this ?: return false
    val preferred = when (ipv6) {
        null -> networks.ipv4Net.firstOrNull() ?: networks.ipv6Net.firstOrNull()
        true -> networks.ipv6Net.firstOrNull()
        false -> networks.ipv4Net.firstOrNull()
    }
    val capabilities = preferred?.capabilities ?: networks.ipv4Net.firstOrNull()?.capabilities
        ?: networks.ipv6Net.firstOrNull()?.capabilities
    return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
}
