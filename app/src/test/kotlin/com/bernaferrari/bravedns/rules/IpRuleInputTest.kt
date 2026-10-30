package com.bernaferrari.bravedns.rules

import inet.ipaddr.IPAddressString
import org.junit.Assert.*
import org.junit.Test

class IpRuleInputTest {
    @Test fun directIpv4AndBracketedIpv6PreserveTheirPort() {
        val ipv4 = requireNotNull(IpRuleInput.parse("10.0.2.2:19862"))
        assertEquals(IPAddressString("10.0.2.2").address, ipv4.address)
        assertEquals(19862, ipv4.port)
        val ipv6 = requireNotNull(IpRuleInput.parse("[2001:db8::1]:19862"))
        assertEquals(IPAddressString("2001:db8::1").address, ipv6.address)
        assertEquals(19862, ipv6.port)
    }

    @Test fun plainAddressesCidrsAndRepresentableWildcardsKeepUnspecifiedPorts() {
        for (input in listOf("10.0.2.2", "10.0.2.0/24", "2001:db8::/32", "[2001:db8::1]", "10.0.*.*")) {
            assertNull(input, requireNotNull(IpRuleInput.parse(input)).port)
        }
        assertEquals(IPAddressString("10.0.0.0/16").address, IpRuleInput.parse("10.0.*.*")?.address)
        assertEquals(IPAddressString("10.0.2.0/24").address, IpRuleInput.parse("10.0.2.0/24:19862")?.address)
        assertEquals(19862, IpRuleInput.parse("10.0.2.0/24:19862")?.port)
        assertEquals(IPAddressString("2001:db8::1986").address, IpRuleInput.parse("2001:db8::1986")?.address)
    }

    @Test fun invalidPortsBracketsNonCidrRangesAndHostnamesAreRejected() {
        for (input in listOf("10.0.2.2:", "10.0.2.2:-1", "10.0.2.2:65536", "10.0.2.2:+80",
            "10.0.2.2:999999999999", "10.0.2.2:http", "[::1]:53:80", "[::1]garbage:80", "[::1",
            "10.0.2.1-7:19862", "10.0.2.1-7", "example.com:443", "[::1]: 80")) {
            assertNull(input, IpRuleInput.parse(input))
        }
    }
}
