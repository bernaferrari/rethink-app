package com.celzero.bravedns.service

import com.celzero.firestack.backend.Backend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRoutingEngineTest {

    @Test
    fun `special app should be false outside dns firewall mode`() {
        val request =
            specialAppRequest(
                isDnsFirewallMode = false,
                isCustomSocks5Enabled = true,
                packageName = "com.test.app",
                socks5ProxyAppName = "com.test.app"
            )

        assertFalse(ProxyRoutingEngine.isSpecialApp(request))
    }

    @Test
    fun `special app should be true when socks5 proxy app matches`() {
        val request =
            specialAppRequest(
                isCustomSocks5Enabled = true,
                packageName = "com.test.app",
                socks5ProxyAppName = "com.test.app"
            )

        assertTrue(ProxyRoutingEngine.isSpecialApp(request))
    }

    @Test
    fun `resolve base or exit should return base for dns proxy rule`() {
        val result =
            ProxyRoutingEngine.resolveBaseOrExitProxyId(
                doubleLoopback = false,
                blockedByRule = FirewallRuleset.RULE9.id,
                rinr = false,
                uid = 1000,
                rethinkUid = 2000,
                autoProxyEnabled = true
            )

        assertEquals(Backend.Base, result)
    }

    @Test
    fun `resolve base or exit should force auto or exit for rethink in rinr`() {
        val result =
            ProxyRoutingEngine.resolveBaseOrExitProxyId(
                doubleLoopback = true,
                blockedByRule = FirewallRuleset.RULE9.id,
                rinr = true,
                uid = 2000,
                rethinkUid = 2000,
                autoProxyEnabled = true
            )

        assertEquals(Backend.Auto, result)
    }

    @Test
    fun `determine route should return rethink direct route`() {
        val decision =
            ProxyRoutingEngine.determineRoute(
                routingRequest(
                    uid = 1000,
                    rethinkUid = 1000,
                    rinr = false
                )
            )

        assertEquals(Backend.Exit, decision.proxyIds)
        assertEquals(ProxyRoutingEngine.Reason.RETHINK_DIRECT, decision.reason)
    }

    @Test
    fun `determine route should apply rule15 for excluded app from rule0`() {
        val decision =
            ProxyRoutingEngine.determineRoute(
                routingRequest(
                    appExcludedFromProxy = true,
                    blockedByRule = FirewallRuleset.RULE0.id,
                    baseOrExitProxyId = Backend.Base
                )
            )

        assertEquals(Backend.Base, decision.proxyIds)
        assertEquals(FirewallRuleset.RULE15.id, decision.blockedByRuleOverride)
        assertEquals(ProxyRoutingEngine.Reason.EXCLUDED_APP, decision.reason)
    }

    @Test
    fun `determine route should mark block and rule17 for wireguard block candidate`() {
        val decision =
            ProxyRoutingEngine.determineRoute(
                routingRequest(
                    wireguardProxyIds = listOf(ProxyManager.ID_WG_BASE + "10", Backend.Block)
                )
            )

        assertTrue(decision.markBlocked)
        assertEquals(FirewallRuleset.RULE17.id, decision.blockedByRuleOverride)
        assertEquals(ProxyRoutingEngine.Reason.WIREGUARD, decision.reason)
    }

    @Test
    fun `determine route should use orbot proxy id when app assigned`() {
        val decision =
            ProxyRoutingEngine.determineRoute(
                routingRequest(
                    isOrbotProxyEnabled = true,
                    orbotProxyAssignedToApp = true
                )
            )

        assertEquals(ProxyManager.ID_ORBOT_BASE, decision.proxyIds)
        assertEquals(ProxyRoutingEngine.Reason.ORBOT_PROXY, decision.reason)
    }

    @Test
    fun `determine route should prefer socks5 over http`() {
        val decision =
            ProxyRoutingEngine.determineRoute(
                routingRequest(
                    isCustomSocks5Enabled = true,
                    isCustomHttpProxyEnabled = true
                )
            )

        assertEquals(ProxyManager.ID_S5_BASE, decision.proxyIds)
        assertEquals(ProxyRoutingEngine.Reason.SOCKS5_PROXY, decision.reason)
    }

    @Test
    fun `determine route should use dns direct app when only dns proxy app matches`() {
        val decision =
            ProxyRoutingEngine.determineRoute(
                routingRequest(
                    isProxyEnabled = false,
                    isDnsProxyActive = true,
                    packageName = "com.test.app",
                    dnsProxyAppName = "com.test.app"
                )
            )

        assertEquals(Backend.Exit, decision.proxyIds)
        assertEquals(ProxyRoutingEngine.Reason.DNS_PROXY_DIRECT_APP, decision.reason)
    }

    private fun specialAppRequest(
        isDnsFirewallMode: Boolean = true,
        isOrbotProxyEnabled: Boolean = false,
        isCustomSocks5Enabled: Boolean = false,
        isCustomHttpProxyEnabled: Boolean = false,
        isDnsProxyActive: Boolean = false,
        packageName: String? = null,
        orbotProxyAppName: String? = null,
        socks5ProxyAppName: String? = null,
        httpProxyAppName: String? = null,
        dnsProxyAppName: String? = null
    ): ProxyRoutingEngine.SpecialAppRequest {
        return ProxyRoutingEngine.SpecialAppRequest(
            isDnsFirewallMode = isDnsFirewallMode,
            isOrbotProxyEnabled = isOrbotProxyEnabled,
            isCustomSocks5Enabled = isCustomSocks5Enabled,
            isCustomHttpProxyEnabled = isCustomHttpProxyEnabled,
            isDnsProxyActive = isDnsProxyActive,
            packageName = packageName,
            orbotProxyAppName = orbotProxyAppName,
            socks5ProxyAppName = socks5ProxyAppName,
            httpProxyAppName = httpProxyAppName,
            dnsProxyAppName = dnsProxyAppName
        )
    }

    private fun routingRequest(
        uid: Int = 2001,
        rethinkUid: Int = 1000,
        rinr: Boolean = false,
        autoProxyEnabled: Boolean = false,
        blockedByRule: String = FirewallRuleset.RULE8.id,
        appExcludedFromProxy: Boolean = false,
        baseOrExitProxyId: String = Backend.Exit,
        wireguardProxyIds: List<String> = emptyList(),
        isProxyEnabled: Boolean = true,
        isDnsProxyActive: Boolean = false,
        isOrbotProxyEnabled: Boolean = false,
        isCustomSocks5Enabled: Boolean = false,
        isCustomHttpProxyEnabled: Boolean = false,
        packageName: String? = null,
        orbotProxyAppName: String? = null,
        orbotProxyAssignedToApp: Boolean = false,
        socks5ProxyAppName: String? = null,
        httpProxyAppName: String? = null,
        dnsProxyAppName: String? = null
    ): ProxyRoutingEngine.RoutingRequest {
        return ProxyRoutingEngine.RoutingRequest(
            uid = uid,
            rethinkUid = rethinkUid,
            rinr = rinr,
            autoProxyEnabled = autoProxyEnabled,
            blockedByRule = blockedByRule,
            appExcludedFromProxy = appExcludedFromProxy,
            baseOrExitProxyId = baseOrExitProxyId,
            wireguardProxyIds = wireguardProxyIds,
            isProxyEnabled = isProxyEnabled,
            isDnsProxyActive = isDnsProxyActive,
            isOrbotProxyEnabled = isOrbotProxyEnabled,
            isCustomSocks5Enabled = isCustomSocks5Enabled,
            isCustomHttpProxyEnabled = isCustomHttpProxyEnabled,
            packageName = packageName,
            orbotProxyAppName = orbotProxyAppName,
            orbotProxyAssignedToApp = orbotProxyAssignedToApp,
            socks5ProxyAppName = socks5ProxyAppName,
            httpProxyAppName = httpProxyAppName,
            dnsProxyAppName = dnsProxyAppName
        )
    }
}
