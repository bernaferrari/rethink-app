/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.celzero.bravedns.wireguard

import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.WgKey

private class FirestackWgKey(val key: WgKey) : WgKeyHandle {
    override fun base64(): String = key.base64().toString()
    override fun hex(): String = key.hex().toString()
    override fun mult(): WgKeyHandle = FirestackWgKey(key.mult())
}

actual object WgCrypto {
    actual fun generatePrivateKey(): WgKeyHandle = FirestackWgKey(Backend.newWgPrivateKey())
    actual fun parsePrivateKey(base64: String): WgKeyHandle = FirestackWgKey(Backend.newWgPrivateKeyOf(base64))
    // firestack accepts private-key wire format for both; public keys use the same constructor in Peer.kt
    actual fun parsePublicKey(base64: String): WgKeyHandle = FirestackWgKey(Backend.newWgPrivateKeyOf(base64))
}

/** Bridge for app code that still needs the underlying firestack [WgKey]. */
fun WgKeyHandle.toFirestackKey(): WgKey =
    (this as? FirestackWgKey)?.key ?: error("WgKeyHandle is not a firestack-backed key")
