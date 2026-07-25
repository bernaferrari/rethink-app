package com.celzero.bravedns.wireguard

private class WasmWgKey(private val material: String) : WgKeyHandle {
    override fun base64(): String = material

    override fun hex(): String = material.encodeToByteArray().joinToString("") { byte ->
        val value = byte.toInt() and 0xff
        "${(value shr 4).toString(16)}${(value and 0xf).toString(16)}"
    }

    override fun mult(): WgKeyHandle = WasmWgKey("web-demo-public:$material")
}

/** Deterministic demo keys keep configuration editing available without exposing browser crypto as VPN crypto. */
actual object WgCrypto {
    actual fun generatePrivateKey(): WgKeyHandle = WasmWgKey("web-demo-private")
    actual fun parsePrivateKey(base64: String): WgKeyHandle = WasmWgKey(base64)
    actual fun parsePublicKey(base64: String): WgKeyHandle = WasmWgKey(base64)
}
