package com.bernaferrari.bravedns.net.go

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager
import com.celzero.firestack.backend.*
import com.celzero.firestack.intra.Tunnel
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GoProxyRecoveryTest {
    @get:Rule val instantTasks = InstantTaskExecutorRule()
    private val fixture = GoVpnAdapterTestFixture()
    @Before fun setUp() = fixture.setUp()
    @After fun tearDown() = fixture.tearDown()
    private fun newAdapter(tunnel: Tunnel, dns: (Tunnel, Proxy) -> Unit = { _, _ -> }) = fixture.newAdapter(tunnel, dns)

    @Test fun `reconcile restores missing active WG once and keeps native hostname config`() = runTest {
        io.mockk.mockkObject(com.bernaferrari.bravedns.service.WireguardManager, RpnProxyManager, com.bernaferrari.bravedns.wireguard.WgHopManager)
        io.mockk.coEvery { com.bernaferrari.bravedns.wireguard.WgHopManager.getHop(any<String>()) } returns ""
        val tunnel = mockk<Tunnel>(relaxed = true)
        val proxies = mockk<Proxies>(relaxed = true)
        val proxy = mockk<Proxy>(relaxed = true)
        every { tunnel.isConnected } returns true
        every { tunnel.proxies } returns proxies
        val config = mockk<com.bernaferrari.bravedns.wireguard.Config>()
        every { config.getId() } returns 1
        every { config.toWgUserspaceString(any(), any(), any()) } returns "reviewed-userspace-config"
        val mapping = com.bernaferrari.bravedns.database.WgConfigFilesImmutable(
            1, "VPN", "", "", true, false, false, false, false, true, true, "")
        every { com.bernaferrari.bravedns.service.WireguardManager.getActiveConfigs() } returns listOf(config)
        every { com.bernaferrari.bravedns.service.WireguardManager.getConfigFilesById(1) } returns mapping
        every { com.bernaferrari.bravedns.service.WireguardManager.getConfigById(1) } returns config
        every { RpnProxyManager.isRpnActive() } returns false
        val id = com.bernaferrari.bravedns.service.ProxyManager.ID_WG_BASE + 1
        var present = false
        every { proxies.hasProxy(id) } answers { present }
        every { proxies.addProxy(id, any()) } answers { present = true; proxy }
        every { proxies.getProxy(id) } returns proxy
        val dnsReferences = mutableListOf<Proxy>()
        val adapter = newAdapter(tunnel) { _, p -> dnsReferences.add(p) }
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        verify(exactly = 1) { proxies.addProxy(id, "reviewed-userspace-config") }
        assertEquals(listOf(proxy), dnsReferences)
        verify(exactly = 0) { proxy.pause() }
    }

    @Test fun `reconcile rechecks disabled WG after the native presence query`() = runTest {
        io.mockk.mockkObject(com.bernaferrari.bravedns.service.WireguardManager, RpnProxyManager, com.bernaferrari.bravedns.wireguard.WgHopManager)
        io.mockk.coEvery { com.bernaferrari.bravedns.wireguard.WgHopManager.getHop(any<String>()) } returns ""
        val tunnel = mockk<Tunnel>(relaxed = true)
        val proxies = mockk<Proxies>(relaxed = true)
        every { tunnel.isConnected } returns true
        every { tunnel.proxies } returns proxies
        val config = mockk<com.bernaferrari.bravedns.wireguard.Config>()
        every { config.getId() } returns 1
        var active = true
        val mapping = com.bernaferrari.bravedns.database.WgConfigFilesImmutable(
            1, "VPN", "", "", true, false, false, false, false, true, false, "")
        every { com.bernaferrari.bravedns.service.WireguardManager.getActiveConfigs() } returns listOf(config)
        every { com.bernaferrari.bravedns.service.WireguardManager.getConfigFilesById(1) } answers { mapping.copy(isActive = active) }
        every { proxies.hasProxy(any()) } answers { active = false; false }
        every { RpnProxyManager.isRpnActive() } returns false
        newAdapter(tunnel).refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        verify(exactly = 0) { proxies.addProxy(any(), any()) }
    }

    @Test fun `one native proxy failure cannot starve recovery of another active WG`() = runTest {
        io.mockk.mockkObject(com.bernaferrari.bravedns.service.WireguardManager, RpnProxyManager, com.bernaferrari.bravedns.wireguard.WgHopManager)
        io.mockk.coEvery { com.bernaferrari.bravedns.wireguard.WgHopManager.getHop(any<String>()) } returns ""
        val tunnel = mockk<Tunnel>(relaxed = true)
        val proxies = mockk<Proxies>(relaxed = true)
        every { tunnel.isConnected } returns true
        every { tunnel.proxies } returns proxies
        val configs = (1..2).map { id -> mockk<com.bernaferrari.bravedns.wireguard.Config>().also {
            every { it.getId() } returns id
            every { it.toWgUserspaceString(any(), any(), any()) } returns "config-$id"
            every { com.bernaferrari.bravedns.service.WireguardManager.getConfigById(id) } returns it
            every { com.bernaferrari.bravedns.service.WireguardManager.getConfigFilesById(id) } returns
                com.bernaferrari.bravedns.database.WgConfigFilesImmutable(id, "VPN", "", "", true, false, false, false, false, true, false, "")
        } }
        every { com.bernaferrari.bravedns.service.WireguardManager.getActiveConfigs() } returns configs
        every { RpnProxyManager.isRpnActive() } returns false
        every { proxies.hasProxy(any()) } returns false
        val prefix = com.bernaferrari.bravedns.service.ProxyManager.ID_WG_BASE
        every { proxies.addProxy(prefix + 1, any()) } throws IllegalStateException("native failure")
        newAdapter(tunnel).refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        verify(exactly = 1) { proxies.addProxy(prefix + 2, "config-2") }
    }
    @Test fun `recovered WG refreshes DNS and restores hop only after both active proxies exist`() = runTest {
        val wg = com.bernaferrari.bravedns.service.WireguardManager
        val hops = com.bernaferrari.bravedns.wireguard.WgHopManager
        io.mockk.mockkObject(wg, RpnProxyManager, hops)
        val tunnel = mockk<Tunnel>(relaxed = true)
        val proxies = mockk<Proxies>(relaxed = true)
        every { tunnel.isConnected } returns true
        every { tunnel.proxies } returns proxies
        val prefix = com.bernaferrari.bravedns.service.ProxyManager.ID_WG_BASE
        var hopActive = true
        val native = (1..2).associateWith { mockk<Proxy>(relaxed = true) }
        val present = mutableSetOf<String>()
        val statuses = mutableMapOf<Int, Int>()
        var via: Proxy? = null
        every { native.getValue(1).router().via() } answers { via }
        every { proxies.hop(prefix + 2, prefix + 1) } answers { via = native.getValue(2) }
        val configs = (1..2).map { id -> mockk<com.bernaferrari.bravedns.wireguard.Config>().also { config ->
            every { config.getId() } returns id
            every { config.toWgUserspaceString(any(), any(), any()) } returns "config-$id"
            every { wg.getConfigById(id) } returns config
            every { wg.getConfigFilesById(id) } answers {
                com.bernaferrari.bravedns.database.WgConfigFilesImmutable(id, "VPN", "", "", id == 1 || hopActive,
                    false, false, false, false, true, false, "")
            }
            every { proxies.hasProxy(prefix + id) } answers { prefix + id in present }
            every { native.getValue(id).id() } returns prefix + id
            every { native.getValue(id).status() } answers { statuses[id] ?: com.celzero.firestack.backend.Backend.TUP }
            every { native.getValue(id).pause() } answers { statuses[id] = com.celzero.firestack.backend.Backend.TPU; true }
            every { native.getValue(id).resume() } answers { statuses[id] = com.celzero.firestack.backend.Backend.TUP; true }
            every { proxies.addProxy(prefix + id, any()) } answers {
                present.add(prefix + id); statuses[id] = com.celzero.firestack.backend.Backend.TUP
                if (id == 1) via = null
                native.getValue(id)
            }
            every { proxies.getProxy(prefix + id) } returns native.getValue(id)
        } }
        every { wg.getActiveConfigs() } returns configs
        every { RpnProxyManager.isRpnActive() } returns false
        io.mockk.coEvery { hops.getHop(prefix + 1) } returns prefix + 2
        io.mockk.coEvery { hops.getHop(prefix + 2) } returns ""
        val dns = mutableListOf<Proxy>()
        val adapter = newAdapter(tunnel) { _, proxy -> dns.add(proxy) }
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        io.mockk.verifyOrder {
            proxies.addProxy(prefix + 2, "config-2")
            proxies.addProxy(prefix + 1, "config-1")
            proxies.hop(prefix + 2, prefix + 1)
        }
        assertEquals(listOf(native.getValue(2), native.getValue(1)), dns)
        hopActive = false
        present.remove(prefix + 2); via = null
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        verify(exactly = 1) { proxies.addProxy(prefix + 2, any()) }
        assertEquals(com.celzero.firestack.backend.Backend.TPU, statuses[1])
        hopActive = true
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        assertEquals(com.celzero.firestack.backend.Backend.TUP, statuses[1])
        assertEquals(native.getValue(2), via)
        hopActive = false
        present.remove(prefix + 1); present.remove(prefix + 2); via = null
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        assertFalse(present.contains(prefix + 1))
        verify(exactly = 1) { proxies.addProxy(prefix + 1, any()) }
        verify(exactly = 2) { proxies.addProxy(prefix + 2, any()) }
        hopActive = true
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        verify(exactly = 2) { proxies.addProxy(prefix + 1, any()) }
        assertEquals(com.celzero.firestack.backend.Backend.TUP, statuses[1])
        assertEquals(native.getValue(2), via)
    }

    @Test fun `RPN recovery inherits AUTO mobile policy refreshes DNS and pauses when AUTO disappears`() = runTest {
        val wg = com.bernaferrari.bravedns.service.WireguardManager
        io.mockk.mockkObject(wg, RpnProxyManager)
        every { wg.getActiveConfigs() } returns emptyList()
        every { RpnProxyManager.isRpnActive() } returns true
        val tunnel = mockk<Tunnel>(relaxed = true)
        val proxies = mockk<Proxies>(relaxed = true)
        val rpn = mockk<com.celzero.firestack.backend.Rpn>(relaxed = true)
        val root = mockk<com.celzero.firestack.backend.RpnProxy>(relaxed = true)
        val child = mockk<Proxy>(relaxed = true)
        val country = com.bernaferrari.bravedns.database.CountryConfig("US-country", "US", key = "country", isEnabled = true, hopEnabled = true)
        var auto: com.bernaferrari.bravedns.database.CountryConfig? = com.bernaferrari.bravedns.database.CountryConfig("AUTO", "", key = "AUTO", mobileOnly = true)
        io.mockk.coEvery { RpnProxyManager.getEnabledConfigs() } returns setOf(country)
        io.mockk.coEvery { RpnProxyManager.getCountryConfigByKey("AUTO") } answers { auto }
        every { tunnel.isConnected } returns true
        every { tunnel.proxies } returns proxies
        every { proxies.rpn() } returns rpn
        every { rpn.win() } returns root
        every { root.id() } returns com.celzero.firestack.backend.Backend.RpnWin
        every { child.id() } returns com.celzero.firestack.backend.Backend.RpnWin + "country"
        var present = false
        var via: Proxy? = null
        every { root.router().via() } answers { via }
        every { root.has("country") } answers { present }
        every { root.get("country") } answers { if (present) child else throw IllegalStateException("missing") }
        every { root.fork("country") } answers { present = true; child }
        every { proxies.hop(com.celzero.firestack.backend.Backend.RpnWin + "country", com.celzero.firestack.backend.Backend.RpnWin) } answers { via = child }
        val dns = mutableListOf<Proxy>()
        val adapter = newAdapter(tunnel) { _, proxy -> dns.add(proxy) }
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "Home", true)
        verify(exactly = 0) { root.fork(any()) }
        adapter.refreshOrPauseOrResumeOrReAddProxies(true, "Home", false)
        adapter.refreshOrPauseOrResumeOrReAddProxies(true, "Home", false)
        verify(exactly = 1) { root.fork("country") }
        verify(exactly = 1) { proxies.hop(com.celzero.firestack.backend.Backend.RpnWin + "country", com.celzero.firestack.backend.Backend.RpnWin) }
        assertEquals(listOf(child), dns)
        auto = null
        adapter.refreshOrPauseOrResumeOrReAddProxies(true, "", false)
        verify(exactly = 1) { child.pause() }
        verify(exactly = 1) { root.fork("country") }
    }

    @Test fun `required WG DNS fails closed while optional aliases retry without blocking data`() = runTest {
        val wg = com.bernaferrari.bravedns.service.WireguardManager
        val hops = com.bernaferrari.bravedns.wireguard.WgHopManager
        mockkObject(wg, RpnProxyManager, hops)
        every { RpnProxyManager.isRpnActive() } returns false
        var mode = com.bernaferrari.bravedns.data.AppConfig.BraveMode.DNS_FIREWALL
        var splitDns = true
        var advancedActive = true
        var oneWg: Int? = null
        every { fixture.appConfig.getBraveMode() } answers { mode }
        every { fixture.persistentState.splitDns } answers { splitDns }
        every { wg.isAdvancedWgActive() } answers { advancedActive }
        every { wg.getOneWireGuardProxyId() } answers { oneWg }
        coEvery { hops.getHop(any<String>()) } returns ""
        val config = mockk<com.bernaferrari.bravedns.wireguard.Config>()
        every { config.getId() } returns 1
        every { config.toWgUserspaceString(any(), any(), any()) } returns "config"
        every { wg.getActiveConfigs() } returns listOf(config)
        every { wg.getConfigById(1) } returns config
        every { wg.getConfigFilesById(1) } returns com.bernaferrari.bravedns.database.WgConfigFilesImmutable(
            1, "VPN", "", "", true, false, false, false, false, true, false, "")
        val tunnel = mockk<Tunnel>(relaxed = true)
        val proxies = mockk<Proxies>(relaxed = true)
        val resolver = mockk<DNSResolver>(relaxed = true)
        val transport = mockk<DNSTransport>()
        val proxy = mockk<Proxy>(relaxed = true)
        val id = com.bernaferrari.bravedns.service.ProxyManager.ID_WG_BASE + 1
        var present = false
        var alias = false
        var failing = true
        var status = Backend.TUP
        var dnsAttempts = 0
        every { tunnel.isConnected } returns true
        every { tunnel.proxies } returns proxies
        every { tunnel.resolver } returns resolver
        every { proxies.hasProxy(id) } answers { present }
        every { proxies.getProxy(id) } returns proxy
        every { proxies.addProxy(id, any()) } answers { present = true; proxy }
        every { proxies.removeProxy(id) } answers { present = false; true }
        every { resolver.remove(id) } answers { alias = false; true }
        every { resolver.get(id) } answers { if (alias) transport else throw IllegalStateException("missing alias") }
        every { proxy.status() } answers { status }
        every { proxy.pause() } answers { status = Backend.TPU; true }
        every { proxy.resume() } answers { status = Backend.TUP; true }
        val adapter = newAdapter(tunnel) { _, _ -> dnsAttempts++; if (failing) error("DNS registration failed") else alias = true }
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        assertFalse(present)
        failing = false
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        assertTrue(present && alias)
        alias = false
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        assertTrue(alias)
        verify(exactly = 2) { proxies.addProxy(id, any()) }
        assertEquals(3, dnsAttempts)
        alias = false; failing = true
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        assertEquals(Backend.TPU, status)
        failing = false
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        assertEquals(Backend.TUP, status)
        val beforeOptional = dnsAttempts
        splitDns = false; failing = true; alias = false; present = false
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        assertTrue("Unused DNS alias must not prevent data-only recovery", present)
        assertTrue("Optional aliases must still be retried", dnsAttempts > beforeOptional)
        assertEquals(Backend.TUP, status)
        oneWg = 1; present = false
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        assertFalse("Selected one-WG DNS is required even when split DNS is off", present)
        oneWg = 2; splitDns = true
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        assertTrue("Another proxy's one-WG selection must not require this alias", present)
        mode = com.bernaferrari.bravedns.data.AppConfig.BraveMode.FIREWALL
        oneWg = 1; present = false
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        assertTrue("Firewall-only mode does not select WG DNS aliases", present)
        mode = com.bernaferrari.bravedns.data.AppConfig.BraveMode.DNS_FIREWALL
        oneWg = null; splitDns = true; advancedActive = false; present = false
        adapter.refreshOrPauseOrResumeOrReAddProxies(false, "", true)
        assertTrue("A saved split preference without active advanced WG does not select aliases", present)
    }

}
