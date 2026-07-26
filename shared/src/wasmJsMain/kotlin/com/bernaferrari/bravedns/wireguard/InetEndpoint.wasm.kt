package com.bernaferrari.bravedns.wireguard

import com.bernaferrari.bravedns.platform.currentTimeMillis

internal actual fun currentTimeMillisCompat(): Long = currentTimeMillis()

// Browser demos do not resolve hostnames; numeric endpoints remain fully usable.
internal actual fun resolveHostToNumeric(host: String): String? =
    if (InetAddresses.isNumericAddress(host)) host else null
