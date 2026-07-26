
package com.bernaferrari.bravedns.wireguard

import com.bernaferrari.bravedns.platform.currentTimeMillis

internal actual fun currentTimeMillisCompat(): Long = currentTimeMillis()

// DNS resolution on iOS can be wired via CFHost/NW later; numeric hosts already isResolved.
internal actual fun resolveHostToNumeric(host: String): String? =
    if (InetAddresses.isNumericAddress(host)) host else null
