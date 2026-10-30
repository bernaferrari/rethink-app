package com.bernaferrari.bravedns.service

import org.junit.Assert.*
import org.junit.Test

class WireguardRoutePolicyTest {
    @Test fun `default route and two covering halves are both full tunnel`() {
        assertFalse(WireguardRoutePolicy.isSplit(listOf("0.0.0.0/0")))
        assertFalse(WireguardRoutePolicy.isSplit(listOf("0.0.0.0/1", "128.0.0.0/1")))
        assertFalse(WireguardRoutePolicy.isSplit(listOf("::/1", "8000::/1")))
    }
    @Test fun `routing unspecified alone does not make tunnel full`() {
        assertTrue(WireguardRoutePolicy.isSplit(listOf("0.0.0.0/1")))
        assertTrue(WireguardRoutePolicy.isSplit(listOf("::/1")))
    }
    @Test fun `missing config fails closed and editing full coverage removes split status`() {
        assertFalse(WireguardRoutePolicy.isSplit(emptyList()))
        assertTrue(WireguardRoutePolicy.isSplit(listOf("10.0.0.0/8")))
        assertFalse(WireguardRoutePolicy.isSplit(listOf("0.0.0.0/0", "::/0")))
    }
}
