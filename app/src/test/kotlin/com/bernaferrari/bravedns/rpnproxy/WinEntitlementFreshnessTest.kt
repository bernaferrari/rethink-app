package com.bernaferrari.bravedns.rpnproxy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WinEntitlementFreshnessTest {
    @Test fun `only a token with a future valid expiry is current`() {
        fun bytes(expiry: String, status: String = "valid") =
            """{"sessiontoken":"example","expiry":"$expiry","status":"$status"}""".toByteArray()
        assertTrue(WinEntitlementFreshness.isCurrent(bytes("2030-01-01T00:00:00Z"), 1))
        assertTrue(WinEntitlementFreshness.isCurrent(bytes("2030-01-01T01:00:00+01:00"), 1))
        assertFalse(WinEntitlementFreshness.isCurrent(bytes("2030-01-01T00:00:00Z", "expired"), 1))
        assertFalse(WinEntitlementFreshness.isCurrent(bytes("1970-01-01T00:00:00Z"), 1))
        assertFalse(WinEntitlementFreshness.isCurrent(bytes("invalid"), 1))
        assertFalse(WinEntitlementFreshness.isCurrent("{}".toByteArray(), 1))
        assertFalse(WinEntitlementFreshness.isCurrent(null, 1))
    }
    @Test fun `unknown expiry requires verification and explicit expiry wins over zero timestamp`() {
        assertTrue(WinEntitlementFreshness.requiresRefresh(null, null, 100))
        assertTrue(WinEntitlementFreshness.requiresRefresh(0, "active", 100))
        assertTrue(WinEntitlementFreshness.requiresRefresh(0, "expired", 100))
        assertTrue(WinEntitlementFreshness.requiresRefresh(1000, "expired", 100))
        assertTrue(WinEntitlementFreshness.requiresRefresh(99, "active", 100))
        assertFalse(WinEntitlementFreshness.requiresRefresh(1000, "active", 100))
    }
}
