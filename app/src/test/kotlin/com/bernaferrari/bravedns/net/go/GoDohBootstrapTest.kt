package com.bernaferrari.bravedns.net.go

import android.content.Context
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.DoHEndpoint
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.intra.Tunnel
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GoDohBootstrapTest {
    private fun endpoint(ips: String) = DoHEndpoint(0, "Custom", "https://resolver.example/dns-query", "", true, true, true, 0, 0, ips)
    private suspend fun install(ips: String, received: MutableList<Pair<String, String>>,
        callbacks: GoDnsTransports.Callbacks): GoDnsTransports.Callbacks {
        val tunnel = mockk<Tunnel>(relaxed = true)
        every { tunnel.isConnected } returns true
        val config = mockk<AppConfig>(relaxed = true)
        every { config.getDnsType() } returns AppConfig.DnsType.DOH
        coEvery { config.getDOHDetails() } returns endpoint(ips)
        GoDnsTransports({ tunnel }, mockk<Context>(relaxed = true), config, callbacks,
            installDoh = { _, id, url, bootstrap ->
                assertEquals(Backend.Preferred, id)
                received += url to bootstrap
            }).addTransport()
        return callbacks
    }
    @Test fun customAddressesReachNativeWithoutReplacingAuthenticatedHostname() = runTest {
        val callbacks = mockk<GoDnsTransports.Callbacks>(relaxed = true)
        val received = mutableListOf<Pair<String, String>>()
        install("192.0.2.53,2001:db8::53", received, callbacks)
        assertEquals(listOf("https://resolver.example/dns-query" to "192.0.2.53,2001:db8::53"), received)
        verify(exactly = 0) { callbacks.ipString(any()) }
    }
    @Test fun emptyAddressesKeepExistingBootstrapPolicy() = runTest {
        val callbacks = mockk<GoDnsTransports.Callbacks>(relaxed = true)
        every { callbacks.ipString("https://resolver.example/dns-query") } returns "192.0.2.54"
        val received = mutableListOf<Pair<String, String>>()
        install("", received, callbacks)
        assertEquals("192.0.2.54", received.single().second)
    }
    @Test fun invalidPersistedAddressesFailClearlyWithoutResolvingOrInstalling() = runTest {
        val callbacks = mockk<GoDnsTransports.Callbacks>(relaxed = true)
        val received = mutableListOf<Pair<String, String>>()
        install("resolver.other.example", received, callbacks)
        assertTrue(received.isEmpty())
        verify(exactly = 0) { callbacks.ipString(any()) }
        verify { callbacks.onDnsFailure(any(), match { "bootstrap" in it }) }
    }
}
