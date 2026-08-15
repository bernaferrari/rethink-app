/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.wireguard.BadConfigException
import com.bernaferrari.bravedns.wireguard.Peer
import com.bernaferrari.bravedns.wireguard.WgInterface
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WgConfigParserTest {

    @Test
    fun `withIdentity attaches id and name`() {
        val iface = mockk<WgInterface>(relaxed = true)
        val parsed = WgConfigParser.rebuild(-1, "", iface, emptyList())
        val identified = WgConfigParser.withIdentity(parsed, id = 7, name = "home")
        assertEquals(7, identified.getId())
        assertEquals("home", identified.getName())
        assertSame(iface, identified.getInterface())
    }

    @Test
    fun `rebuild keeps peers from source`() {
        val iface = mockk<WgInterface>(relaxed = true)
        val peers = listOf(mockk<Peer>(relaxed = true))
        val rebuilt = WgConfigParser.rebuild(3, "office", iface, peers)
        assertEquals(3, rebuilt.getId())
        assertEquals("office", rebuilt.getName())
        assertEquals(peers, rebuilt.getPeers())
    }

    @Test(expected = BadConfigException::class)
    fun `parseText rejects missing interface section`() {
        WgConfigParser.parseText("PrivateKey = aaa\n")
    }

    @Test(expected = BadConfigException::class)
    fun `parseText rejects unknown section`() {
        WgConfigParser.parseText("[Nope]\nFoo = bar\n")
    }
}
