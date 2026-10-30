package com.bernaferrari.bravedns.rules

import com.bernaferrari.bravedns.service.IpRuleNormalizer
import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString

internal data class ParsedIpRule(val address: IPAddress, val port: Int?) {
    val importKey: String get() = "[${address.toNormalizedString()}]:${port ?: 0}"
}

/** Parses numeric addresses only; the native trie must be able to represent the entire rule. */
internal object IpRuleInput {
    fun parse(value: String): ParsedIpRule? {
        val text = value.trim()
        val bracketed = text.startsWith('[')
        val split = when {
            bracketed -> {
                val closing = text.indexOf(']')
                if (closing < 0) return null
                val suffixStart = closing + 1
                if (suffixStart < text.length && text[suffixStart] != ':') return null
                suffixStart
            }
            text.count { it == ':' } == 1 -> text.lastIndexOf(':')
            else -> text.length
        }
        val addressText = if (bracketed) text.substring(1, split - 1) else text.substring(0, split)
        val port = if (split < text.length) {
            val digits = text.substring(split + 1)
            if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return null
            digits.toIntOrNull()?.takeIf { it in 0..65535 } ?: return null
        } else null
        val normalized = IpRuleNormalizer.trieKey(addressText) ?: return null
        val address = IPAddressString(normalized).address ?: return null
        return ParsedIpRule(address, port)
    }
}
