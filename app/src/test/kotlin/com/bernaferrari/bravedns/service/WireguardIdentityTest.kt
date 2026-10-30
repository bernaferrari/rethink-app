package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.wireguard.Config
import com.bernaferrari.bravedns.wireguard.KeyPair
import com.bernaferrari.bravedns.wireguard.Peer
import com.bernaferrari.bravedns.wireguard.WgInterface
import com.bernaferrari.bravedns.wireguard.WgKeyHandle
import org.junit.Assert.*
import org.junit.Test

class WireguardIdentityTest {
    private data class Key(private val value: String) : WgKeyHandle {
        override fun base64() = value
        override fun hex() = value
        override fun mult(): WgKeyHandle = this
    }
    private fun config(privateKey: String, peer: String): Config = Config.Builder()
        .setInterface(WgInterface.Builder().setKeyPair(KeyPair(Key(privateKey))).build())
        .addPeer(Peer.Builder().setPublicKey(Key(peer)).build()).build()

    @Test fun `same local and remote identity conflicts`() {
        assertTrue(WireguardIdentity.conflicts(config("a", "peer"), config("a", "peer")))
    }
    @Test fun `different remote peers may reuse a private key`() {
        assertFalse(WireguardIdentity.conflicts(config("a", "peer1"), config("a", "peer2")))
        assertFalse(WireguardIdentity.conflicts(config("a", "peer"), config("b", "peer")))
    }
}
