package com.bernaferrari.bravedns.ui.compose.firewall

import com.bernaferrari.bravedns.ui.compose.theme.RethinkFirewallRuleStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RethinkRuleEditTest {
    private fun rule(tab: RethinkRulesTab, uid: Int, status: RethinkFirewallRuleStatus) =
        RethinkFirewallRule(id = "x", uid = uid, value = "v", status = status, tab = tab)

    @Test
    fun domainRulesReadAsTheirOwnAction() {
        for (uid in listOf(RETHINK_RULES_UID_EVERYBODY, 10123)) {
            assertEquals(RethinkRuleAction.Block, rule(RethinkRulesTab.DOMAIN, uid, RethinkFirewallRuleStatus.Block).editAction())
            assertEquals(RethinkRuleAction.Trust, rule(RethinkRulesTab.DOMAIN, uid, RethinkFirewallRuleStatus.Trust).editAction())
            assertNull(rule(RethinkRulesTab.DOMAIN, uid, RethinkFirewallRuleStatus.None).editAction())
        }
    }

    @Test
    fun allAppsIpTrustIsTheBypassStatusTheFirewallHonours() {
        val all = RETHINK_RULES_UID_EVERYBODY
        assertEquals(RethinkRuleAction.Trust, rule(RethinkRulesTab.IP, all, RethinkFirewallRuleStatus.Bypass).editAction())
        // An all-apps TRUST row is ignored by the firewall, so it isn't shown as trusted.
        assertNull(rule(RethinkRulesTab.IP, all, RethinkFirewallRuleStatus.Trust).editAction())
        assertEquals(RethinkRuleAction.Block, rule(RethinkRulesTab.IP, all, RethinkFirewallRuleStatus.Block).editAction())
    }

    @Test
    fun perAppIpTrustIsTrustAndBypassIsNeither() {
        assertEquals(RethinkRuleAction.Trust, rule(RethinkRulesTab.IP, 10123, RethinkFirewallRuleStatus.Trust).editAction())
        assertNull(rule(RethinkRulesTab.IP, 10123, RethinkFirewallRuleStatus.Bypass).editAction())
        assertNull(rule(RethinkRulesTab.IP, 10123, RethinkFirewallRuleStatus.None).editAction())
    }
}
