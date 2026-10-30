package com.bernaferrari.bravedns.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DnsAnswerPolicyTest {
    @Test fun `port rules and non-address answers always defer without evaluating`() = runTest {
        val contexts = listOf(
            DnsAnswerContext("192.0.2.1", portSpecificRules = true),
            DnsAnswerContext(""), DnsAnswerContext("mail.example"),
            DnsAnswerContext("192.0.2.1", captivePortal = true),
            DnsAnswerContext("192.0.2.1", dnsOnly = true),
            DnsAnswerContext("192.0.2.1", fixedTransport = true),
        )
        contexts.forEach { context ->
            assertEquals(DnsAnswerPolicy.Decision.Keep, DnsAnswerPolicy.decide(context) { error("must defer") })
        }
    }
    @Test fun `firewall denial blocks while trust bypasses blocklists`() = runTest {
        val context = DnsAnswerContext("192.0.2.1,2001:db8::1")
        assertEquals(DnsAnswerPolicy.Decision.Block(FirewallRuleset.RULE2),
            DnsAnswerPolicy.decide(context) { assertEquals(2, it.size); FirewallRuleset.RULE2 })
        assertEquals(DnsAnswerPolicy.Decision.Bypass(FirewallRuleset.RULE2F),
            DnsAnswerPolicy.decide(context) { FirewallRuleset.RULE2F })
    }
    @Test fun `universal firewall exemption does not bypass DNS blocklists`() = runTest {
        assertEquals(DnsAnswerPolicy.Decision.Keep,
            DnsAnswerPolicy.decide(DnsAnswerContext("192.0.2.1")) { FirewallRuleset.RULE8 })
    }
    @Test fun `trusted isolate rule allows answer without changing resolver`() = runTest {
        val context = DnsAnswerContext("192.0.2.1", isolated = true)
        assertEquals(DnsAnswerPolicy.Decision.Keep, DnsAnswerPolicy.decide(context) { FirewallRuleset.RULE2B })
        assertEquals(DnsAnswerPolicy.Decision.Keep, DnsAnswerPolicy.decide(context) { FirewallRuleset.RULE0 })
    }
}
