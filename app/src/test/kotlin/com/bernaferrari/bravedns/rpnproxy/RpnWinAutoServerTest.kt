/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.rpnproxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RpnWinAutoServerTest {

    @Test
    fun `create builds the synthetic AUTO row`() {
        val auto = RpnWinAutoServer.create("AUTO", "AUTO", now = 42L)
        assertEquals("AUTO", auto.id)
        assertEquals("AUTO", auto.cc)
        assertEquals("AUTO", auto.key)
        assertTrue(auto.catchAll)
        assertTrue(auto.premium)
        assertTrue(auto.isActive)
        assertFalse(auto.isEnabled)
        assertFalse(auto.lockdown)
        assertFalse(auto.mobileOnly)
        assertFalse(auto.ssidBased)
        assertEquals(999, auto.priority)
        assertEquals(42L, auto.lastModified)
    }
}
