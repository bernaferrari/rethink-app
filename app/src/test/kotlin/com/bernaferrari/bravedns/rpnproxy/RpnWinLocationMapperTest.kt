/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.rpnproxy

import com.bernaferrari.bravedns.database.CountryConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RpnWinLocationMapperTest {

    @Test
    fun `serverId joins country name and key`() {
        assertEquals("US-nyc-sid1", RpnWinLocationMapper.serverId("US", "nyc", "sid1"))
    }

    @Test
    fun `essential fields reject blanks`() {
        assertTrue(RpnWinLocationMapper.hasEssentialFields("US", "nyc", "k"))
        assertFalse(RpnWinLocationMapper.hasEssentialFields(null, "nyc", "k"))
        assertFalse(RpnWinLocationMapper.hasEssentialFields("US", "", "k"))
        assertFalse(RpnWinLocationMapper.hasEssentialFields("US", "nyc", null))
    }

    @Test
    fun `removedIds is set difference`() {
        assertEquals(
            setOf("old"),
            RpnWinLocationMapper.removedIds(setOf("keep", "old"), setOf("keep", "new")),
        )
    }

    @Test
    fun `removedSelected only includes ids currently in the tunnel`() {
        val existing = listOf(
            CountryConfig(id = "US-a-k1", cc = "US", name = "a", key = "k1"),
            CountryConfig(id = "DE-b-k2", cc = "DE", name = "b", key = "k2"),
            CountryConfig(id = "AUTO", cc = "AUTO", name = "AUTO", key = "AUTO"),
        )
        val result = RpnWinLocationMapper.removedSelected(
            existing = existing,
            removedIds = setOf("US-a-k1", "DE-b-k2", "missing"),
            isInTunnel = { it == "k1" },
        )
        assertEquals(listOf("US-a-k1"), result.selectedIds)
        assertEquals(setOf("k1"), result.keysToRemove)
    }

    @Test
    fun `staleServers skips AUTO and keys still present`() {
        val before = listOf(
            CountryConfig(id = "AUTO", cc = "AUTO", key = "AUTO"),
            CountryConfig(id = "US-a-k1", cc = "US", key = "k1"),
            CountryConfig(id = "DE-b-k2", cc = "DE", key = "k2"),
        )
        val stale = RpnWinLocationMapper.staleServers(before, setOf("k2"), "AUTO")
        assertEquals(listOf("k1"), stale.map { it.key })
    }
}
