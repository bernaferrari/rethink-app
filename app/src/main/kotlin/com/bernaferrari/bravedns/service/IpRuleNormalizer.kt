package com.bernaferrari.bravedns.service

import inet.ipaddr.AddressValueException
import inet.ipaddr.IPAddressString
import inet.ipaddr.IncompatibleAddressException

/** Conversion boundary for the native trie, which accepts single addresses and CIDRs only. */
internal object IpRuleNormalizer {
    fun trieKey(text: String): String? {
        if (text.isBlank()) return null
        return try {
            val address = IPAddressString(text).address ?: return null
            if (address.isMultiple || text.contains('*')) {
                address.assignPrefixForSingleBlock()?.toCanonicalString()
            } else {
                address.toNormalizedString()
            }
        } catch (_: IncompatibleAddressException) {
            null
        } catch (_: AddressValueException) {
            null
        }
    }
}
