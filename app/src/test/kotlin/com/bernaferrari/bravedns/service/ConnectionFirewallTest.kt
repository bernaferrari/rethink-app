package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.util.Constants.Companion.INVALID_UID
import com.bernaferrari.bravedns.util.Constants.Companion.MISSING_UID
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.bernaferrari.bravedns.util.KnownPorts
import com.bernaferrari.bravedns.util.Protocol
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionFirewallTest {

    private val uid = 10001
    private val rethinkUid = 10000

    @Test
    fun `app blocked when connection status is BOTH`() {
        val verdict =
            evaluate(
                app = appSnapshot(connectionStatus = FirewallManager.ConnectionStatus.BOTH)
            )

        assertEquals(FirewallRuleset.RULE1, verdict.rule)
        assertTrue(verdict.blocked)
        assertEquals("Rule #1", verdict.rule.id)
    }

    @Test
    fun `app unblocked when connection status is ALLOW`() {
        val verdict = evaluate()

        assertEquals(FirewallRuleset.RULE0, verdict.rule)
        assertFalse(verdict.blocked)
        assertEquals("No Rule", verdict.rule.id)
    }

    @Test
    fun `wifi blocked for uid on unmetered network`() {
        val verdict =
            evaluate(
                app = appSnapshot(connectionStatus = FirewallManager.ConnectionStatus.UNMETERED),
                network = wifiUnmetered()
            )

        assertEquals(FirewallRuleset.RULE1D, verdict.rule)
        assertTrue(verdict.blocked)
        assertEquals("Rule #1D", verdict.rule.id)
    }

    @Test
    fun `wifi blocked for uid does not apply on metered cellular`() {
        val verdict =
            evaluate(
                app = appSnapshot(connectionStatus = FirewallManager.ConnectionStatus.UNMETERED),
                network = cellularMetered()
            )

        assertEquals(FirewallRuleset.RULE0, verdict.rule)
        assertFalse(verdict.blocked)
    }

    @Test
    fun `mobile data blocked for uid on metered network`() {
        val verdict =
            evaluate(
                app = appSnapshot(connectionStatus = FirewallManager.ConnectionStatus.METERED),
                network = cellularMetered()
            )

        assertEquals(FirewallRuleset.RULE1E, verdict.rule)
        assertTrue(verdict.blocked)
        assertEquals("Rule #1E", verdict.rule.id)
    }

    @Test
    fun `mobile data blocked for uid does not apply on wifi`() {
        val verdict =
            evaluate(
                app = appSnapshot(connectionStatus = FirewallManager.ConnectionStatus.METERED),
                network = wifiUnmetered()
            )

        assertEquals(FirewallRuleset.RULE0, verdict.rule)
        assertFalse(verdict.blocked)
    }

    @Test
    fun `bypass dns firewall returns RULE1H`() {
        val verdict =
            evaluate(app = appSnapshot(appStatus = FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL))

        assertEquals(FirewallRuleset.RULE1H, verdict.rule)
        assertFalse(verdict.blocked)
        assertEquals("Rule #1H", verdict.rule.id)
    }

    @Test
    fun `isolate mode returns RULE1G`() {
        val verdict = evaluate(app = appSnapshot(appStatus = FirewallManager.FirewallStatus.ISOLATE))

        assertEquals(FirewallRuleset.RULE1G, verdict.rule)
        assertTrue(verdict.blocked)
        assertEquals("Rule #1G", verdict.rule.id)
    }

    @Test
    fun `bypass universal returns RULE8`() {
        val verdict =
            evaluate(app = appSnapshot(appStatus = FirewallManager.FirewallStatus.BYPASS_UNIVERSAL))

        assertEquals(FirewallRuleset.RULE8, verdict.rule)
        assertFalse(verdict.blocked)
        assertEquals("Whitelist", verdict.rule.id)
    }

    @Test
    fun `block metered on cellular when treatOnlyMobileAsMetered`() {
        val verdict =
            evaluate(
                flags = FirewallFlags(blockMeteredConnections = true),
                network =
                    NetworkSnapshot(
                        isActiveNetworkCellular = true,
                        isActiveNetworkMetered = false,
                        treatOnlyMobileNetworkAsMetered = true
                    )
            )

        assertEquals(FirewallRuleset.RULE1F, verdict.rule)
        assertTrue(verdict.blocked)
        assertEquals("Rule #1F", verdict.rule.id)
    }

    @Test
    fun `wifi is not metered when treatOnlyMobileAsMetered even if iface reports metered`() {
        val verdict =
            evaluate(
                flags = FirewallFlags(blockMeteredConnections = true),
                network =
                    NetworkSnapshot(
                        isActiveNetworkCellular = false,
                        isActiveNetworkMetered = true,
                        treatOnlyMobileNetworkAsMetered = true
                    )
            )

        assertEquals(FirewallRuleset.RULE0, verdict.rule)
        assertFalse(verdict.blocked)
    }

    @Test
    fun `block metered uses iface metered when treatOnlyMobileAsMetered is false`() {
        val verdict =
            evaluate(
                flags = FirewallFlags(blockMeteredConnections = true),
                network =
                    NetworkSnapshot(
                        isActiveNetworkCellular = false,
                        isActiveNetworkMetered = true,
                        treatOnlyMobileNetworkAsMetered = false
                    )
            )

        assertEquals(FirewallRuleset.RULE1F, verdict.rule)
        assertTrue(verdict.blocked)
    }

    @Test
    fun `unmetered wifi is allowed when treatOnlyMobileAsMetered is false`() {
        val verdict =
            evaluate(
                flags = FirewallFlags(blockMeteredConnections = true),
                network = wifiUnmetered()
            )

        assertEquals(FirewallRuleset.RULE0, verdict.rule)
        assertFalse(verdict.blocked)
    }

    @Test
    fun `isConnectionMetered uses active cellular when treatOnlyMobile and useActive`() {
        assertTrue(
            ConnectionFirewall.isConnectionMetered(
                NetworkSnapshot(
                    isActiveNetworkCellular = true,
                    isActiveNetworkMetered = false,
                    useActive = true,
                    treatOnlyMobileNetworkAsMetered = true
                )
            )
        )
        assertFalse(
            ConnectionFirewall.isConnectionMetered(
                NetworkSnapshot(
                    isActiveNetworkCellular = false,
                    isActiveNetworkMetered = true,
                    useActive = true,
                    treatOnlyMobileNetworkAsMetered = true
                )
            )
        )
    }

    @Test
    fun `isConnectionMetered uses dest iface when not useActive or lockdown`() {
        assertTrue(
            ConnectionFirewall.isConnectionMetered(
                NetworkSnapshot(
                    isActiveNetworkCellular = false,
                    useActive = false,
                    treatOnlyMobileNetworkAsMetered = true,
                    destIfaceCellular = true
                )
            )
        )
        assertTrue(
            ConnectionFirewall.isConnectionMetered(
                NetworkSnapshot(
                    isActiveNetworkMetered = false,
                    useActive = true,
                    vpnLockdown = true,
                    treatOnlyMobileNetworkAsMetered = false,
                    destIfaceMetered = true
                )
            )
        )
    }

    @Test
    fun `udp blocked for non-dns non-ntp`() {
        val verdict =
            evaluate(
                destPort = 443,
                protocol = Protocol.UDP.protocolType,
                flags = FirewallFlags(udpBlocked = true)
            )

        assertEquals(FirewallRuleset.RULE6, verdict.rule)
        assertTrue(verdict.blocked)
        assertEquals("Rule #6", verdict.rule.id)
    }

    @Test
    fun `udp dns port is not blocked by udp rule`() {
        val verdict =
            evaluate(
                destPort = KnownPorts.DNS_PORT,
                protocol = Protocol.UDP.protocolType,
                flags = FirewallFlags(udpBlocked = true)
            )

        assertEquals(FirewallRuleset.RULE0, verdict.rule)
        assertFalse(verdict.blocked)
    }

    @Test
    fun `unknown connection blocked for missing uid`() {
        val verdict =
            evaluate(
                app =
                    appSnapshot(
                        uid = MISSING_UID,
                        isMissingOrInvalidUid = true
                    ),
                flags = FirewallFlags(blockUnknownConnections = true)
            )

        assertEquals(FirewallRuleset.RULE5, verdict.rule)
        assertTrue(verdict.blocked)
        assertEquals("Rule #5", verdict.rule.id)
    }

    @Test
    fun `unknown connection allowed when flag is off`() {
        val verdict =
            evaluate(
                app = appSnapshot(uid = MISSING_UID, isMissingOrInvalidUid = true),
                flags = FirewallFlags(blockUnknownConnections = false)
            )

        assertEquals(FirewallRuleset.RULE0, verdict.rule)
        assertFalse(verdict.blocked)
    }

    @Test
    fun `dns bypass allowed when disallowDnsBypass is false`() {
        val verdict =
            evaluate(
                query = null,
                flags = FirewallFlags(disallowDnsBypass = false)
            )

        assertEquals(FirewallRuleset.RULE0, verdict.rule)
        assertFalse(verdict.blocked)
    }

    @Test
    fun `dns bypass blocked when disallowDnsBypass and query empty`() {
        val verdict =
            evaluate(
                query = "",
                flags = FirewallFlags(disallowDnsBypass = true)
            )

        assertEquals(FirewallRuleset.RULE7, verdict.rule)
        assertTrue(verdict.blocked)
        assertEquals("Rule #7", verdict.rule.id)
    }

    @Test
    fun `new app blocked when untracked and still firewalled`() {
        val verdict =
            evaluate(
                app =
                    appSnapshot(
                        appStatus = FirewallManager.FirewallStatus.UNTRACKED,
                        newAppStillBlocked = true
                    ),
                flags = FirewallFlags(blockNewlyInstalledApp = true)
            )

        assertEquals(FirewallRuleset.RULE1B, verdict.rule)
        assertTrue(verdict.blocked)
        assertEquals("Rule #1B", verdict.rule.id)
    }

    @Test
    fun `new app not blocked when flag is off`() {
        val verdict =
            evaluate(
                app =
                    appSnapshot(
                        appStatus = FirewallManager.FirewallStatus.UNTRACKED,
                        newAppStillBlocked = true
                    ),
                flags = FirewallFlags(blockNewlyInstalledApp = false)
            )

        assertEquals(FirewallRuleset.RULE0, verdict.rule)
        assertFalse(verdict.blocked)
    }

    @Test
    fun `proxy mapped special app skips universal metered and udp rules`() {
        val verdict =
            evaluate(
                isSplApp = true,
                destPort = 443,
                protocol = Protocol.UDP.protocolType,
                flags =
                    FirewallFlags(
                        blockMeteredConnections = true,
                        udpBlocked = true
                    ),
                network = cellularMetered(treatOnlyMobile = true)
            )

        assertEquals(FirewallRuleset.RULE0, verdict.rule)
        assertFalse(verdict.blocked)
    }

    @Test
    fun `rethink uid skips rules when not rinr`() {
        val verdict =
            evaluate(
                app = appSnapshot(uid = rethinkUid),
                rinr = false,
                flags = FirewallFlags(blockMeteredConnections = true, udpBlocked = true),
                network = cellularMetered()
            )

        assertEquals(FirewallRuleset.RULE0, verdict.rule)
        assertFalse(verdict.blocked)
    }

    @Test
    fun `temp allowed returns RULE19 before app block`() {
        val verdict =
            evaluate(
                app =
                    appSnapshot(
                        isTempAllowed = true,
                        connectionStatus = FirewallManager.ConnectionStatus.BOTH
                    )
            )

        assertEquals(FirewallRuleset.RULE19, verdict.rule)
        assertFalse(verdict.blocked)
    }

    @Test
    fun `uid domain block precedes ip and universal rules`() {
        val firewall =
            ConnectionFirewall(
                domainRule = { _, lookupUid ->
                    if (lookupUid == uid) {
                        Pair(DomainRulesManager.Status.BLOCK, "ads.example")
                    } else {
                        Pair(DomainRulesManager.Status.NONE, null)
                    }
                }
            )

        val verdict = runBlocking { firewall.evaluate(request()) }

        assertEquals(FirewallRuleset.RULE2E, verdict.rule)
        assertEquals("ads.example", verdict.matchedQuery)
        assertTrue(verdict.blocked)
    }

    @Test
    fun `uid ip trust precedes bypass dns firewall`() {
        val firewall =
            ConnectionFirewall(
                ipRule = { lookupUid, _, _ ->
                    if (lookupUid == uid) {
                        IpRulesManager.IpRuleStatus.TRUST
                    } else {
                        IpRulesManager.IpRuleStatus.NONE
                    }
                }
            )

        val verdict =
            runBlocking {
                firewall.evaluate(
                    request(app = appSnapshot(appStatus = FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL))
                )
            }

        assertEquals(FirewallRuleset.RULE2B, verdict.rule)
        assertFalse(verdict.blocked)
    }

    @Test
    fun `global ip block after bypass-universal check`() {
        val firewall =
            ConnectionFirewall(
                ipRule = { lookupUid, _, _ ->
                    if (lookupUid == UID_EVERYBODY) {
                        IpRulesManager.IpRuleStatus.BLOCK
                    } else {
                        IpRulesManager.IpRuleStatus.NONE
                    }
                }
            )

        val verdict = runBlocking { firewall.evaluate(request()) }

        assertEquals(FirewallRuleset.RULE2D, verdict.rule)
        assertTrue(verdict.blocked)
    }

    private fun evaluate(
        destPort: Int = 443,
        protocol: Int = Protocol.TCP.protocolType,
        query: String? = "example.com",
        anyRealIpBlocked: Boolean = false,
        isSplApp: Boolean = false,
        rinr: Boolean = false,
        app: AppFirewallSnapshot = appSnapshot(),
        flags: FirewallFlags = FirewallFlags(),
        network: NetworkSnapshot = wifiUnmetered()
    ): FirewallVerdict {
        return runBlocking {
            ConnectionFirewall().evaluate(
                request(
                    destPort = destPort,
                    protocol = protocol,
                    query = query,
                    anyRealIpBlocked = anyRealIpBlocked,
                    isSplApp = isSplApp,
                    rinr = rinr,
                    app = app,
                    flags = flags,
                    network = network
                )
            )
        }
    }

    private fun request(
        destPort: Int = 443,
        protocol: Int = Protocol.TCP.protocolType,
        query: String? = "example.com",
        anyRealIpBlocked: Boolean = false,
        isSplApp: Boolean = false,
        rinr: Boolean = false,
        app: AppFirewallSnapshot = appSnapshot(),
        flags: FirewallFlags = FirewallFlags(),
        network: NetworkSnapshot = wifiUnmetered()
    ): ConnectionRequest {
        return ConnectionRequest(
            connId = "abcd1234",
            destIP = "1.2.3.4",
            destPort = destPort,
            protocol = protocol,
            query = query,
            domains = query,
            anyRealIpBlocked = anyRealIpBlocked,
            isSplApp = isSplApp,
            rinr = rinr,
            debugConnInfo = "conn",
            app = app,
            flags = flags,
            network = network
        )
    }

    private fun appSnapshot(
        uid: Int = this.uid,
        appStatus: FirewallManager.FirewallStatus = FirewallManager.FirewallStatus.NONE,
        connectionStatus: FirewallManager.ConnectionStatus = FirewallManager.ConnectionStatus.ALLOW,
        isTempAllowed: Boolean = false,
        allowOrbot: Boolean = false,
        isMissingOrInvalidUid: Boolean = uid == INVALID_UID || uid == MISSING_UID,
        isUidSystemApp: Boolean = false,
        newAppStillBlocked: Boolean = false,
        backgroundBlocked: Boolean = false
    ): AppFirewallSnapshot {
        return AppFirewallSnapshot(
            uid = uid,
            rethinkUid = rethinkUid,
            appStatus = appStatus,
            connectionStatus = connectionStatus,
            isTempAllowed = isTempAllowed,
            allowOrbot = allowOrbot,
            isMissingOrInvalidUid = isMissingOrInvalidUid,
            isUidSystemApp = isUidSystemApp,
            newAppStillBlocked = newAppStillBlocked,
            backgroundBlocked = backgroundBlocked
        )
    }

    private fun wifiUnmetered(): NetworkSnapshot {
        return NetworkSnapshot(
            isActiveNetworkCellular = false,
            isActiveNetworkMetered = false,
            treatOnlyMobileNetworkAsMetered = false
        )
    }

    private fun cellularMetered(treatOnlyMobile: Boolean = false): NetworkSnapshot {
        return NetworkSnapshot(
            isActiveNetworkCellular = true,
            isActiveNetworkMetered = true,
            treatOnlyMobileNetworkAsMetered = treatOnlyMobile
        )
    }
}
