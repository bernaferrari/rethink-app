/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Non-Android demo host: proves KMP common (Room types, WG, network models) without VPN.
 * Run: ./gradlew :shared:runJvmDemo  (or compileJvmMain + execute)
 */
package com.celzero.bravedns

import com.celzero.bravedns.wireguard.Attribute
import com.celzero.bravedns.wireguard.KeyPair
import com.celzero.bravedns.wireguard.WgCrypto

fun jvmDemoBanner(): String {
    val kp = KeyPair(WgCrypto.generatePrivateKey())
    val attr = Attribute.parse("DNS = 1.1.1.1")
    return buildString {
        appendLine("RethinkDNS KMP demo (JVM / future web host)")
        appendLine("  wg public key prefix: ${kp.getPublicKey().base64().take(16)}…")
        appendLine("  sample attr: ${attr?.key}=${attr?.value}")
        appendLine("  room: entities/DAOs in commonMain (runtime DB only on Android app)")
        appendLine("  (VPN/DNS not started — non-functional demo only)")
    }
}

fun main() {
    println(jvmDemoBanner())
}
