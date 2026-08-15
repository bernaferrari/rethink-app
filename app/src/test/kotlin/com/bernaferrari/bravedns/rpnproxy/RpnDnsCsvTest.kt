/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.rpnproxy

import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager.DnsMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RpnDnsCsvTest {

    @Test
    fun `blank csv yields default`() {
        assertEquals(setOf(DnsMode.DEFAULT), DnsMode.setFromCsv(""))
        assertEquals(setOf(DnsMode.DEFAULT), DnsMode.setFromCsv("   "))
        assertEquals(
            setOf("default"),
            RpnDnsCsv.setFromCsv("", listOf("default", "privacy"), { it }, "default"),
        )
    }

    @Test
    fun `unknown tokens are ignored and fall back to default when none remain`() {
        assertEquals(setOf(DnsMode.DEFAULT), DnsMode.setFromCsv("social,unknown"))
    }

    @Test
    fun `known tokens map to modes and ignore junk`() {
        assertEquals(
            setOf(DnsMode.PRIVACY, DnsMode.PARENTAL),
            DnsMode.setFromCsv("privacy, family ,social"),
        )
    }

    @Test
    fun `tunTypesFromSet is deterministic by id`() {
        assertEquals("privacy,family", DnsMode.tunTypesFromSet(setOf(DnsMode.PARENTAL, DnsMode.PRIVACY)))
        assertEquals("default", DnsMode.tunTypesFromSet(emptySet()))
        assertEquals("default,privacy,family,security", DnsMode.tunTypesFromSet(DnsMode.entries.toSet()))
    }

    @Test
    fun `parseTokens trims and drops empties`() {
        assertEquals(emptyList<String>(), RpnDnsCsv.parseTokens(""))
        assertEquals(listOf("privacy", "family"), RpnDnsCsv.parseTokens(" privacy, ,family , "))
    }

    @Test
    fun `round trip csv through DnsMode`() {
        val original = setOf(DnsMode.SECURITY, DnsMode.PRIVACY)
        val csv = DnsMode.tunTypesFromSet(original)
        assertEquals("privacy,security", csv)
        assertEquals(original, DnsMode.setFromCsv(csv))
    }
}
