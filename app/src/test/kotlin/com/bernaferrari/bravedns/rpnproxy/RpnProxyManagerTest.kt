/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.rpnproxy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Focused tests for the manager's side-effect-free routing helpers. */
class RpnProxyManagerTest {
    @Test
    fun `matchesSsidList accepts exact configured network`() {
        assertTrue(RpnProxyManager.matchesSsidList("Home,Office,Guest", "Office"))
    }

    @Test
    fun `matchesSsidList trims entries and ignores case`() {
        assertTrue(RpnProxyManager.matchesSsidList(" Home WiFi , OFFICE ", "office"))
    }

    @Test
    fun `matchesSsidList supports wildcard-style entries and fail-open empty policy`() {
        assertTrue(RpnProxyManager.matchesSsidList("Home,Office", "Off"))
        assertTrue(RpnProxyManager.matchesSsidList("", "Home"))
        assertFalse(RpnProxyManager.matchesSsidList("Home", ""))
    }
}
