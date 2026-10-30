package com.bernaferrari.bravedns.ui.compose.firewall

import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import org.junit.Assert.*
import org.junit.Test

class IpRuleAddActionTest {
    @Test fun addCallbackEnqueuesTheEnteredPortInsteadOfAnAllPortRule() {
        val writes = mutableListOf<Pair<IPAddress, Int?>>()
        val result = ipRuleAddResult("10.0.2.2:19862", "invalid") { address, port -> writes += address to port }
        assertTrue(result.accepted)
        assertEquals(listOf(IPAddressString("10.0.2.2").address to 19862), writes)
        val ipv6 = ipRuleAddResult("[2001:db8::1]:19862", "invalid") { address, port -> writes += address to port }
        assertTrue(ipv6.accepted)
        assertEquals(IPAddressString("2001:db8::1").address to 19862, writes.last())
    }

    @Test fun invalidPortReturnsTheDialogErrorWithoutSchedulingAnyWrite() {
        var writes = 0
        val result = ipRuleAddResult("10.0.2.2:65536", "invalid input") { _, _ -> writes++ }
        assertFalse(result.accepted)
        assertEquals("invalid input", result.error)
        assertEquals(0, writes)
    }
}
