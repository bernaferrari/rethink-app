package com.bernaferrari.bravedns.service

import inet.ipaddr.AddressValueException
import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import inet.ipaddr.IncompatibleAddressException

/** Conversion boundary for the native trie, which accepts single addresses and CIDRs only. */
internal object IpRuleNormalizer {
    fun storageText(address: IPAddress): String {
        val text = if (address.isIPv4) padIpv4Cidr(address.toNormalizedString()) else address.toNormalizedString()
        return trieKey(text) ?: text
    }

    private fun padIpv4Cidr(cidr: String): String {
        // remove port number from the IP address
        // [192.x.y/24]:80
        // ip => [192.x.y/24]
        val ip = cidr.split(":")[0]
        // plaincidr => 192.x.y/24
        val hasbraces = ip.contains("[") and ip.contains("]")
        val plaincidr = ip.replace("[", "").replace("]", "")
        // parts => [192.x.y, 24]
        val parts = plaincidr.split("/")
        // ipparts => [192, x, y]
        val ipParts = parts[0].split(".").toMutableList()
        if (ipParts.size == 4) {
            return cidr
        }
        // Pad the IP address with zeros if not fully specified
        while (ipParts.size < 4) {
            // ipparts => [192, x, y, *]
            ipParts.add("*")
        }
        // Remove the last part of the IP address if it is 0
        // 192.x.y.0 => 192.x.y.*; 192.x.0.* => 192.x.*.*
        for (i in (ipParts.size - 1) downTo 0) {
            if (ipParts[i] != "*") {
                if (ipParts[i] != "0") break
                ipParts[i] = "*"
            }
        }
        // Reassemble the IP address; paddedIp => 192.x.y.*
        val paddedIp = ipParts.joinToString(".")
        // Reassemble the CIDR string
        if (parts.size == 1) return paddedIp
        return if (hasbraces) {
            "[$paddedIp/${parts[1]}]"
        } else {
            "$paddedIp/${parts[1]}"
        }
    }

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
