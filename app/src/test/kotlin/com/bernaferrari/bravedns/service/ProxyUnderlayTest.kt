package com.bernaferrari.bravedns.service

import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.mockk
import io.mockk.every
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProxyUnderlayTest {
    private fun network(transport: Int): ConnectionMonitor.NetworkProperties {
        val capabilities = mockk<NetworkCapabilities>()
        every { capabilities.hasTransport(any()) } returns false
        every { capabilities.hasTransport(transport) } returns true
        return ConnectionMonitor.NetworkProperties(mockk<Network>(), capabilities, null, "cached", null)
    }

    private fun snapshot(v4: List<ConnectionMonitor.NetworkProperties>, v6: List<ConnectionMonitor.NetworkProperties> = emptyList()) =
        ConnectionMonitor.UnderlyingNetworks(v4, v6, null, false, 1500, false, false, null, 0, emptyMap())

    @Test fun knownWifiDoesNotDependOnDiscoveringItsName() {
        assertTrue(snapshot(listOf(network(NetworkCapabilities.TRANSPORT_WIFI))).isKnownWifi())
        assertFalse(snapshot(listOf(network(NetworkCapabilities.TRANSPORT_ETHERNET))).isKnownWifi())
        assertFalse(snapshot(emptyList()).isKnownWifi())
        assertFalse((null as ConnectionMonitor.UnderlyingNetworks?).isKnownWifi())
    }

    @Test fun destinationFamilySelectsItsCachedUnderlayAndMissingFamilyUsesKnownFallback() {
        val networks = snapshot(listOf(network(NetworkCapabilities.TRANSPORT_WIFI)), listOf(network(NetworkCapabilities.TRANSPORT_CELLULAR)))
        assertTrue(networks.isKnownWifi(ipv6 = false))
        assertFalse(networks.isKnownWifi(ipv6 = true))
        assertTrue(snapshot(emptyList(), listOf(network(NetworkCapabilities.TRANSPORT_WIFI))).isKnownWifi(ipv6 = false))
    }
}
