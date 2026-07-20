/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.celzero.bravedns.wireguard

/** JVM fallback: stub keys for unit tests / tooling (no firestack on pure JVM). */
private class StubWgKey(private val material: String) : WgKeyHandle {
    override fun base64(): String = material
    override fun hex(): String = material.encodeToByteArray().joinToString("") { b -> "%02x".format(b) }
    override fun mult(): WgKeyHandle = StubWgKey("pub:$material")
}

actual object WgCrypto {
    actual fun generatePrivateKey(): WgKeyHandle = StubWgKey("stub-private-key")
    actual fun parsePrivateKey(base64: String): WgKeyHandle = StubWgKey(base64)
    actual fun parsePublicKey(base64: String): WgKeyHandle = StubWgKey(base64)
}
