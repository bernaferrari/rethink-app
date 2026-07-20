
package com.celzero.bravedns.wireguard

import com.celzero.bravedns.platform.currentTimeMillis

internal actual fun currentTimeMillisCompat(): Long = currentTimeMillis()

// DNS resolution on iOS can be wired via CFHost/NW later; numeric hosts already isResolved.
internal actual fun resolveHostToNumeric(host: String): String? =
    if (InetAddresses.isNumericAddress(host)) host else null
