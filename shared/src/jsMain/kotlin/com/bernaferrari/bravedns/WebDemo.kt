/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Browser entry for a non-functional web demo: proves KMP compile + shared models/UI shells.
 * No VPN/DNS/Room DB runtime — entities/DAOs are type-checked only on JS.
 */
package com.bernaferrari.bravedns

import com.bernaferrari.bravedns.wireguard.Attribute
import com.bernaferrari.bravedns.wireguard.KeyPair
import com.bernaferrari.bravedns.wireguard.WgCrypto

fun webDemoBanner(): String {
    val kp = KeyPair(WgCrypto.generatePrivateKey())
    val attr = Attribute.parse("PrivateKey = demo")
    return "RethinkDNS shared web demo | key=${kp.getPublicKey().base64().take(12)}… | attr=${attr?.key}"
}

fun main() {
    println(webDemoBanner())
}
