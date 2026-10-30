package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.wireguard.Config

/** Simultaneous sessions with the same local and remote keys compete at the peer. */
internal object WireguardIdentity {
    fun conflicts(candidate: Config, active: Config): Boolean {
        val candidateKey = candidate.getInterface()?.getKeyPair()?.getPrivateKey()?.base64()
        val activeKey = active.getInterface()?.getKeyPair()?.getPrivateKey()?.base64()
        if (candidateKey == null || candidateKey != activeKey) return false
        val peers = active.getPeers().orEmpty().map { it.getPublicKey().base64() }.toSet()
        return candidate.getPeers().orEmpty().any { it.getPublicKey().base64() in peers }
    }
}
