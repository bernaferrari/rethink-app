/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

internal data class DnsHeroLatency(val samples: List<Long>, val label: String?)

/** Keep the recent window in arrival order; prefer the resolver's reported latency. */
internal fun dnsHeroLatency(recent: List<Long>, reported: String, sampleLimit: Int): DnsHeroLatency {
    val samples = recent.take(sampleLimit)
    // Preserve the existing upper-middle sample convention for an even fallback window.
    val median = samples.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
    val label = reported.trim('(', ')', ' ').takeIf { it.isNotBlank() } ?: median?.let { "$it ms" }
    return DnsHeroLatency(samples, label)
}
