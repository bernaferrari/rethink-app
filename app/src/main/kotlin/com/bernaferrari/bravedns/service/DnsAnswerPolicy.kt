package com.bernaferrari.bravedns.service

internal data class DnsAnswerContext(
    val addresses: String,
    val captivePortal: Boolean = false,
    val dnsOnly: Boolean = false,
    val fixedTransport: Boolean = false,
    val portSpecificRules: Boolean = false,
    val isolated: Boolean = false,
)

/** DNS has no destination port; decisions depending on a port must wait for flow. */
internal object DnsAnswerPolicy {
    sealed interface Decision {
        data object Keep : Decision
        data class Block(val rule: FirewallRuleset) : Decision
        data class Bypass(val rule: FirewallRuleset) : Decision
    }

    suspend fun decide(context: DnsAnswerContext, evaluate: suspend (List<String>) -> FirewallRuleset): Decision {
        val ips = context.addresses.split(',').map(String::trim).filter(String::isNotEmpty)
        if (context.captivePortal || context.dnsOnly || context.fixedTransport || context.portSpecificRules ||
            ips.isEmpty() || ips.any { IpRuleNormalizer.trieKey(it) == null }
        ) return Decision.Keep
        val rule = evaluate(ips)
        if (FirewallRuleset.ground(rule)) return Decision.Block(rule)
        val isolatedTrust = context.isolated && (rule == FirewallRuleset.RULE2B || rule == FirewallRuleset.RULE2F)
        return if (!isolatedTrust && FirewallRuleset.isBypassRule(rule)) Decision.Bypass(rule) else Decision.Keep
    }
}
