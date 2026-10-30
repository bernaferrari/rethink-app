package com.bernaferrari.bravedns.data

import com.bernaferrari.bravedns.wireguard.InetAddresses

/** Numeric bootstrap destinations only. Never consults a resolver or accepts a URL/port/range. */
object DnsBootstrapAddresses {
    const val MAX_ADDRESSES = 16
    const val MAX_INPUT_LENGTH = 2048

    /** Empty text selects the existing resolver defaults; null means invalid configuration. */
    fun parse(input: String): String? {
        if (input.length > MAX_INPUT_LENGTH) return null
        val text = input.trim()
        if (text.isEmpty()) return ""
        if (text.startsWith(',') || text.endsWith(',') || Regex(",\\s*,").containsMatchIn(text)) return null
        val addresses = text.split(Regex("[,\\s]+"))
        if (addresses.size > MAX_ADDRESSES || addresses.any { !isLiteral(it) }) return null
        return addresses.map { it.lowercase() }.distinct().joinToString(",")
    }

    private fun isLiteral(address: String): Boolean {
        if (address.length > 45) return false
        if (address.any { it in "%[]" }) return false
        if (!InetAddresses.isNumericAddress(address)) return false
        val v4 = address.substringAfterLast(':')
        return '.' !in v4 || v4.split('.').all { it == "0" || !it.startsWith('0') }
    }
}
