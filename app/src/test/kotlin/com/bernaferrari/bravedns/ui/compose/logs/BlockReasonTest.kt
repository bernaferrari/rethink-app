/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.FirewallManager.ConnectionStatus
import com.bernaferrari.bravedns.service.FirewallRuleset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockReasonTest {

    private val universalSettings = setOf(
        FirewallRuleset.RULE1F, FirewallRuleset.RULE3, FirewallRuleset.RULE4, FirewallRuleset.RULE5,
        FirewallRuleset.RULE6, FirewallRuleset.RULE7, FirewallRuleset.RULE10, FirewallRuleset.RULE11,
    )

    /** Fixes that write a rule or flag; everything else navigates or does nothing. */
    private fun QuickFix?.writes(): Boolean = when (this) {
        null, is QuickFix.OpenUniversalRules, QuickFix.OpenProxySettings -> false
        is QuickFix.AllowApp, QuickFix.AllowTemporarily, is QuickFix.RemoveDomainRule,
        is QuickFix.RemoveIpRule, QuickFix.TrustDomain, QuickFix.TrustIp -> true
    }

    @Test
    fun everyRuleUsesItsOwnSentence() {
        FirewallRuleset.entries.forEach { rule ->
            assertEquals(rule.name, rule.desc, blockReasonFor(rule, hasDomain = true).sentence)
        }
    }

    @Test
    fun allowedVerdictsOfferNoFix() {
        FirewallRuleset.getAllowedRules().forEach { rule ->
            val reason = blockReasonFor(rule, hasDomain = true)
            assertNull(rule.name, reason.primaryFix)
            assertNull(rule.name, reason.secondaryFix)
        }
    }

    @Test
    fun universalSettingsRouteToTheirRowAndNeverWriteARule() {
        universalSettings.forEach { rule ->
            val reason = blockReasonFor(rule, hasDomain = true)
            assertEquals(rule.name, QuickFix.OpenUniversalRules(rule.id), reason.primaryFix)
            assertNull(rule.name, reason.secondaryFix)
        }
    }

    @Test
    fun proxyBlocksRouteToProxySettings() {
        listOf(FirewallRuleset.RULE17, FirewallRuleset.RULE18).forEach { rule ->
            assertEquals(rule.name, QuickFix.OpenProxySettings, blockReasonFor(rule, hasDomain = true).primaryFix)
        }
    }

    @Test
    fun blocksKeepoutCannotUndoOfferNothing() {
        listOf(FirewallRuleset.RULE1C, FirewallRuleset.RULE13, FirewallRuleset.RULE14, FirewallRuleset.RULE16).forEach { rule ->
            val reason = blockReasonFor(rule, hasDomain = true)
            assertNull(rule.name, reason.primaryFix)
            assertNull(rule.name, reason.secondaryFix)
        }
    }

    @Test
    fun appBlocksClearOnlyTheirOwnNetworkAndOfferTemporaryAccess() {
        assertEquals(QuickFix.AllowApp(BlockedNetwork.Wifi), blockReasonFor(FirewallRuleset.RULE1D, true).primaryFix)
        assertEquals(QuickFix.AllowApp(BlockedNetwork.MobileData), blockReasonFor(FirewallRuleset.RULE1E, true).primaryFix)
        assertEquals(QuickFix.AllowApp(BlockedNetwork.Everywhere), blockReasonFor(FirewallRuleset.RULE1, true).primaryFix)
        listOf(FirewallRuleset.RULE1, FirewallRuleset.RULE1D, FirewallRuleset.RULE1E).forEach { rule ->
            assertEquals(rule.name, QuickFix.AllowTemporarily, blockReasonFor(rule, true).secondaryFix)
        }
    }

    @Test
    fun newAppIsAllowedButNotTemporarily() {
        val reason = blockReasonFor(FirewallRuleset.RULE1B, hasDomain = true)
        assertEquals(QuickFix.AllowApp(BlockedNetwork.Everywhere), reason.primaryFix)
        assertNull(reason.secondaryFix)
    }

    @Test
    fun ruleBlocksRemoveTheRuleInItsOwnScope() {
        assertEquals(QuickFix.RemoveIpRule(universal = false), blockReasonFor(FirewallRuleset.RULE2, true).primaryFix)
        assertEquals(QuickFix.RemoveIpRule(universal = true), blockReasonFor(FirewallRuleset.RULE2D, true).primaryFix)
        assertEquals(QuickFix.RemoveDomainRule(universal = false), blockReasonFor(FirewallRuleset.RULE2E, true).primaryFix)
        assertEquals(QuickFix.RemoveDomainRule(universal = true), blockReasonFor(FirewallRuleset.RULE2H, true).primaryFix)
    }

    @Test
    fun trustedOnlyAndDnsBlocksTrustForThisApp() {
        assertEquals(QuickFix.TrustDomain, blockReasonFor(FirewallRuleset.RULE1G, hasDomain = true).primaryFix)
        assertEquals(QuickFix.TrustIp, blockReasonFor(FirewallRuleset.RULE1G, hasDomain = false).primaryFix)
        assertEquals(QuickFix.TrustDomain, blockReasonFor(FirewallRuleset.RULE2G, hasDomain = true).primaryFix)
        assertNull(blockReasonFor(FirewallRuleset.RULE2G, hasDomain = false).primaryFix)
    }

    @Test
    fun everyBlockedRuleIsClassified() {
        val classified = universalSettings + setOf(
            FirewallRuleset.RULE1, FirewallRuleset.RULE1B, FirewallRuleset.RULE1C, FirewallRuleset.RULE1D,
            FirewallRuleset.RULE1E, FirewallRuleset.RULE1G, FirewallRuleset.RULE2, FirewallRuleset.RULE2D,
            FirewallRuleset.RULE2E, FirewallRuleset.RULE2G, FirewallRuleset.RULE2H, FirewallRuleset.RULE13,
            FirewallRuleset.RULE14, FirewallRuleset.RULE16, FirewallRuleset.RULE17, FirewallRuleset.RULE18,
        )
        assertEquals(FirewallRuleset.getBlockedRules().toSet() - classified, emptySet<FirewallRuleset>())
        FirewallRuleset.getBlockedRules().filter { it in universalSettings }.forEach { rule ->
            assertTrue(rule.name, !blockReasonFor(rule, true).primaryFix.writes())
        }
    }

    @Test
    fun blocklistsOnlyCaptionDnsBlocks() {
        val lists = listOf("Hagezi Multi Pro", "1Hosts Pro")
        assertEquals(lists, blockReasonFor(FirewallRuleset.RULE2G, true, lists).blocklists)
        assertEquals(emptyList<String>(), blockReasonFor(FirewallRuleset.RULE1, true, lists).blocklists)
    }

    @Test
    fun unknownRuleSaysNoRuleMatched() {
        val reason = blockReasonFor(null, hasDomain = true)
        assertEquals(R.string.firewall_rule_no_rule_desc, reason.sentence)
        assertNull(reason.primaryFix)
    }

    @Test
    fun dnsLookups() {
        val blocked = dnsBlockReasonFor(blocked = true, blocklists = listOf("OISD"))
        assertEquals(R.string.firewall_rule_dns_blocked_desc, blocked.sentence)
        assertEquals(QuickFix.TrustDomain, blocked.primaryFix)
        assertEquals(listOf("OISD"), blocked.blocklists)
        val allowed = dnsBlockReasonFor(blocked = false, blocklists = emptyList())
        assertEquals(R.string.connection_dns_allowed, allowed.sentence)
        assertNull(allowed.primaryFix)
    }

    @Test
    fun allowingClearsOnlyTheChosenNetwork() {
        assertEquals(ConnectionStatus.ALLOW, ConnectionStatus.UNMETERED.allowing(BlockedNetwork.Wifi))
        assertEquals(ConnectionStatus.METERED, ConnectionStatus.BOTH.allowing(BlockedNetwork.Wifi))
        assertEquals(ConnectionStatus.METERED, ConnectionStatus.METERED.allowing(BlockedNetwork.Wifi))
        assertEquals(ConnectionStatus.ALLOW, ConnectionStatus.METERED.allowing(BlockedNetwork.MobileData))
        assertEquals(ConnectionStatus.UNMETERED, ConnectionStatus.BOTH.allowing(BlockedNetwork.MobileData))
        assertEquals(ConnectionStatus.UNMETERED, ConnectionStatus.UNMETERED.allowing(BlockedNetwork.MobileData))
        ConnectionStatus.entries.forEach { assertEquals(ConnectionStatus.ALLOW, it.allowing(BlockedNetwork.Everywhere)) }
    }

    @Test
    fun parsesBlocklists() {
        assertEquals(listOf("A", "B"), parseBlocklists(" A, ,B,A "))
        assertEquals(emptyList<String>(), parseBlocklists(null))
    }
}
