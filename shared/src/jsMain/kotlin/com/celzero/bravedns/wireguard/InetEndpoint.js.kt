package com.celzero.bravedns.wireguard

import com.celzero.bravedns.platform.currentTimeMillis

internal actual fun currentTimeMillisCompat(): Long = currentTimeMillis()
internal actual fun resolveHostToNumeric(host: String): String? =
    if (InetAddresses.isNumericAddress(host)) host else null
