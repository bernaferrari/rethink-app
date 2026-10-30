package com.bernaferrari.bravedns.ui.compose.dns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DnsHeroLatencyTest {
    @Test
    fun emptyWindowDoesNotInventLatency() {
        val display = dnsHeroLatency(emptyList(), "()", 12)
        assertEquals(emptyList(), display.samples)
        assertNull(display.label)
    }

    @Test
    fun resolverReportedLatencyOverridesWindowFallback() {
        assertEquals("45 ms", dnsHeroLatency(listOf(10, 20), " (45 ms) ", 12).label)
    }

    @Test
    fun fallbackUsesOnlyRecentWindowWithoutReorderingChartSamples() {
        val display = dnsHeroLatency(listOf(20, 5, 10, 500), "", 3)
        assertEquals(listOf(20L, 5L, 10L), display.samples)
        assertEquals("10 ms", display.label)
    }

    @Test
    fun evenFallbackRetainsUpperMiddleConvention() {
        assertEquals("20 ms", dnsHeroLatency(listOf(20, 10), "", 12).label)
    }
}
