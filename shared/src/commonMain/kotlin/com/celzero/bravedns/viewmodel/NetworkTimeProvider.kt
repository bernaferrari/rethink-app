package com.celzero.bravedns.viewmodel

/** The only platform input required by shared network-history view models. */
fun interface NetworkUptimeProvider {
    fun uptimeMs(): Long
}
