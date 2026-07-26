/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.wireguard

/**
 * Platform-neutral WireGuard key abstraction.
 * Android/JVM actuals delegate to firestack; iOS can plug in a native Curve25519 impl later.
 */
interface WgKeyHandle {
    fun base64(): String
    fun hex(): String
    fun mult(): WgKeyHandle
}

expect object WgCrypto {
    fun generatePrivateKey(): WgKeyHandle
    fun parsePrivateKey(base64: String): WgKeyHandle
    fun parsePublicKey(base64: String): WgKeyHandle
}

/** Curve25519 key pair for WireGuard — fully commonMain. */
class KeyPair(privateKey: WgKeyHandle = WgCrypto.generatePrivateKey()) {
    private val privateKey: WgKeyHandle = privateKey
    private val publicKey: WgKeyHandle = privateKey.mult()

    fun getPrivateKey(): WgKeyHandle = privateKey
    fun getPublicKey(): WgKeyHandle = publicKey
}
