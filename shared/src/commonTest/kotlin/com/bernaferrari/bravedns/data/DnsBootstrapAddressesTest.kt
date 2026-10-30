package com.bernaferrari.bravedns.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DnsBootstrapAddressesTest {
    @Test fun mixedNumericAddressesAreBoundedDeduplicatedAndNeverResolveNames() {
        assertEquals("192.0.2.53,2001:db8::53", DnsBootstrapAddresses.parse(" 192.0.2.53, 2001:DB8::53\n192.0.2.53 "))
        assertEquals("", DnsBootstrapAddresses.parse(" \n "))
        assertEquals("::ffff:192.0.2.1", DnsBootstrapAddresses.parse("::ffff:192.0.2.1"))
    }
    @Test fun hostnamesPortsNetworksAndMalformedLiteralsCannotBecomeBootstrapDestinations() {
        for (value in listOf("resolver.example", "https://192.0.2.1", "192.0.2.1:53", "[2001:db8::1]:53",
            "192.0.2.1/24", "192.0.2.*", "fe80::1%wlan0", "2001:::1", "999.1.1.1", "01.2.3.4", ",", "192.0.2.1,,192.0.2.2")) {
            assertNull(DnsBootstrapAddresses.parse(value), value)
        }
    }
    @Test fun inputAndAddressCountsAreCheckedBeforeNativeInstallation() {
        assertNull(DnsBootstrapAddresses.parse("1".repeat(2049)))
        assertNull(DnsBootstrapAddresses.parse((1..17).joinToString(",") { "192.0.2.$it" }))
        assertEquals(16, DnsBootstrapAddresses.parse((1..16).joinToString(",") { "192.0.2.$it" })!!.split(',').size)
    }
    @Test fun unknownSmartProfileKeepsExistingUnfilteredDefault() {
        assertEquals(SmartDnsProfile.None, SmartDnsProfile.fromValue(-1))
        SmartDnsProfile.entries.forEach { assertEquals(it, SmartDnsProfile.fromValue(it.value)) }
    }
    @Test fun staleProfileStatusCannotAdvertiseNewChoiceAsActive() {
        assertEquals(SmartDnsApplyStatus.Queued(SmartDnsProfile.Family),
            SmartDnsApplyStatus.Active(SmartDnsProfile.None).forSelection(SmartDnsProfile.Family))
        assertEquals(SmartDnsApplyStatus.Active(SmartDnsProfile.Family),
            SmartDnsApplyStatus.Active(SmartDnsProfile.Family).forSelection(SmartDnsProfile.Family))
        assertEquals(SmartDnsApplyStatus.Saved, SmartDnsApplyStatus.Saved.forSelection(SmartDnsProfile.Family))
    }

    @Test fun endpointTransportIdentityIncludesItsBootstrapDestinations() {
        fun endpoint(bootstrap: String) = com.bernaferrari.bravedns.database.DoHEndpoint(
            1, "Resolver", "https://resolver.example/dns-query", "", false, true, true, 0, 0, bootstrap)
        assertEquals(endpoint("192.0.2.1"), endpoint("192.0.2.1"))
        kotlin.test.assertNotEquals(endpoint("192.0.2.1"), endpoint("192.0.2.2"))
    }

}
