/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * iOS WireGuard key material. Until a native Curve25519/libsodium module is linked,
 * we store caller-supplied or randomly generated 32-byte keys and derive a deterministic
 * public-key stand-in via mult() (NOT cryptographically valid for production tunnels —
 * replace with SecKey / libsodium X25519 before shipping iOS VPN).
 */
package com.celzero.bravedns.wireguard

import kotlin.random.Random

private val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

private fun ByteArray.toB64(): String {
    val out = StringBuilder()
    var i = 0
    while (i < size) {
        val b0 = this[i].toInt() and 0xff
        val b1 = if (i + 1 < size) this[i + 1].toInt() and 0xff else 0
        val b2 = if (i + 2 < size) this[i + 2].toInt() and 0xff else 0
        out.append(B64[b0 shr 2])
        out.append(B64[((b0 and 0x3) shl 4) or (b1 shr 4)])
        out.append(if (i + 1 < size) B64[((b1 and 0xf) shl 2) or (b2 shr 6)] else '=')
        out.append(if (i + 2 < size) B64[b2 and 0x3f] else '=')
        i += 3
    }
    return out.toString()
}

private fun String.fromB64(): ByteArray {
    val clean = filter { it != '=' && !it.isWhitespace() }
    val out = ByteArray(clean.length * 3 / 4)
    var oi = 0
    var i = 0
    while (i + 3 < clean.length) {
        fun idx(c: Char): Int = B64.indexOf(c).coerceAtLeast(0)
        val n = (idx(clean[i]) shl 18) or (idx(clean[i + 1]) shl 12) or
            (idx(clean[i + 2]) shl 6) or idx(clean[i + 3])
        if (oi < out.size) out[oi++] = ((n shr 16) and 0xff).toByte()
        if (oi < out.size) out[oi++] = ((n shr 8) and 0xff).toByte()
        if (oi < out.size) out[oi++] = (n and 0xff).toByte()
        i += 4
    }
    return out.copyOf(32.coerceAtMost(out.size)).let {
        if (it.size < 32) it + ByteArray(32 - it.size) else it
    }
}

private class IosWgKey(private val bytes: ByteArray) : WgKeyHandle {
    override fun base64(): String = bytes.toB64()
    override fun hex(): String = bytes.joinToString("") { b ->
        val v = b.toInt() and 0xff
        val hex = "0123456789abcdef"
        "${hex[v shr 4]}${hex[v and 0xf]}"
    }
    // Stand-in scalar mult: NOT real Curve25519 — sufficient for config UI/parsing only.
    override fun mult(): WgKeyHandle {
        val pub = ByteArray(32)
        for (i in 0 until 32) pub[i] = (bytes[i].toInt() xor (0x5a + i)).toByte()
        pub[0] = (pub[0].toInt() and 0xf8).toByte()
        pub[31] = ((pub[31].toInt() and 0x7f) or 0x40).toByte()
        return IosWgKey(pub)
    }
}

actual object WgCrypto {
    actual fun generatePrivateKey(): WgKeyHandle {
        val bytes = Random.Default.nextBytes(32)
        bytes[0] = (bytes[0].toInt() and 0xf8).toByte()
        bytes[31] = ((bytes[31].toInt() and 0x7f) or 0x40).toByte()
        return IosWgKey(bytes)
    }

    actual fun parsePrivateKey(base64: String): WgKeyHandle = IosWgKey(base64.fromB64())

    actual fun parsePublicKey(base64: String): WgKeyHandle = IosWgKey(base64.fromB64())
}
