package com.bernaferrari.bravedns.wireguard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class WireguardSerializationTest {
    private fun config(dns: String = "", endpoint: String = "does-not-resolve.invalid:51820"): Config = Config.parse("""
        [Interface]
        PrivateKey = test-private
        Address = 10.0.0.2/32
        $dns
        [Peer]
        PublicKey = test-public
        AllowedIPs = 0.0.0.0/0
        Endpoint = $endpoint
    """.trimIndent())

    @Test fun endpointIsSerializedOnceWithoutPlatformResolution() {
        val config = config()
        assertEquals(listOf("endpoint=does-not-resolve.invalid:51820"),
            config.toWgUserspaceString().lineSequence().filter { it.startsWith("endpoint=") }.toList())
        assertEquals(1, config.toWgQuickString().lineSequence().count { it.startsWith("Endpoint =") })
    }
    @Test fun emptyDnsGetsUserspaceFallbackWithoutChangingExport() {
        val config = config()
        assertTrue(config.toWgUserspaceString().contains("dns=1.1.1.1\n"))
        assertFalse(config.toWgQuickString().contains("DNS ="))
    }
    @Test fun configuredDnsAndIpv6EndpointRemainIntact() {
        val config = config("DNS = 9.9.9.9", "[2001:db8::1]:51820")
        assertTrue(config.toWgUserspaceString().contains("dns=9.9.9.9\n"))
        assertEquals(1, config.toWgUserspaceString().lineSequence().count { it.startsWith("endpoint=") })
        assertTrue(config.toWgUserspaceString().contains("endpoint=[2001:db8::1]:51820"))
    }
    @Test fun unknownInterfaceOptionIsExplicitlyRejected() {
        assertFailsWith<BadConfigException> { config("UnsupportedOption = true") }
    }
}
