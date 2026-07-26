package com.bernaferrari.bravedns.wireguard

private class JsWgKey(private val material: String) : WgKeyHandle {
    override fun base64(): String = material
    override fun hex(): String = material.encodeToByteArray().joinToString("") { b ->
        val v = b.toInt() and 0xff
        ((v shr 4).toString(16)) + ((v and 0xf).toString(16))
    }
    override fun mult(): WgKeyHandle = JsWgKey("pub:$material")
}

actual object WgCrypto {
    actual fun generatePrivateKey(): WgKeyHandle = JsWgKey("js-demo-private")
    actual fun parsePrivateKey(base64: String): WgKeyHandle = JsWgKey(base64)
    actual fun parsePublicKey(base64: String): WgKeyHandle = JsWgKey(base64)
}
