package com.bernaferrari.bravedns.service

import org.junit.Assert.*
import org.junit.Test

class IpRuleNormalizerTest {
    @Test fun `aligned wildcard and range become native CIDR`() {
        assertEquals("1.2.0.0/16", IpRuleNormalizer.trieKey("1.2.*.*"))
        assertEquals("1.2.252.0/22", IpRuleNormalizer.trieKey("1.2.252-255.*"))
    }
    @Test fun `unrepresentable ranges and invalid input never reach native trie`() {
        assertNull(IpRuleNormalizer.trieKey("0.0.6.178-228"))
        assertNull(IpRuleNormalizer.trieKey("*.255.255.255"))
        assertNull(IpRuleNormalizer.trieKey("not-an-address"))
        assertNull(IpRuleNormalizer.trieKey(""))
    }
    @Test fun `storage never turns an unsupported range into a port-only rule`() {
        val input = inet.ipaddr.IPAddressString("*.255.255.255").address!!
        val stored = IpRuleNormalizer.storageText(input)
        assertTrue(stored.isNotBlank())
        assertNull(IpRuleNormalizer.trieKey(stored))
    }
    @Test fun `individual addresses and v6 prefixes remain usable`() {
        assertNotNull(IpRuleNormalizer.trieKey("192.0.2.1"))
        assertTrue(IpRuleNormalizer.trieKey("2001:db8::/32")!!.endsWith("/32"))
    }
}
