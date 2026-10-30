/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.logs

import androidx.annotation.StringRes
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.FirewallManager.ConnectionStatus
import com.bernaferrari.bravedns.service.FirewallRuleset

/**
 * Why a connection or lookup got its verdict, and the one-tap fixes that undo exactly the rule
 * responsible. Pure: callers map it to copy and to manager writes ([ConnectionSheetHost] does both).
 *
 * The contract every fix keeps (the plan's risk #2): a fix changes only the rule that decided this
 * connection. Universal settings (device locked, UDP, port 80, lockdown, unknown apps…) never get a
 * silent rule write; they route to their settings row instead.
 */
data class BlockReason(
    /** The plain sentence for the verdict card ("Blocked because this app is blocked on Wi‑Fi."). */
    @StringRes val sentence: Int,
    /** The filled action, or null when nothing in Keepout can (or should) change this verdict. */
    val primaryFix: QuickFix? = null,
    /** The tonal action under [primaryFix]; today only "Allow for 15 minutes" on app blocks. */
    val secondaryFix: QuickFix? = null,
    /** DNS blocklists that stopped the lookup, shown as a caption under [sentence]. */
    val blocklists: List<String> = emptyList(),
)

/** The networks an app-level block can cover. Metered Wi‑Fi counts as [MobileData] (stored flags). */
enum class BlockedNetwork { Wifi, MobileData, Everywhere }

/** One action a [BlockReason] offers. Hosts decide the copy and perform the write with Undo. */
sealed interface QuickFix {
    /** Clear the app's own block on [network] only, keeping any block on the other network. */
    data class AllowApp(val network: BlockedNetwork) : QuickFix

    /** Temporary allow through `FirewallManager.updateTempAllowStatus` (stored expiry). */
    data object AllowTemporarily : QuickFix

    /**
     * Remove the domain rule that blocked it: the app's rule, or the all-apps rule when
     * [universal]. When only a wildcard matched, hosts open the rules list instead.
     */
    data class RemoveDomainRule(val universal: Boolean) : QuickFix

    /** As [RemoveDomainRule], for IP rules; a range or port rule falls back to the rules list. */
    data class RemoveIpRule(val universal: Boolean) : QuickFix

    /** Add an app-scoped Trust rule for the domain (DNS blocklists, Trusted only apps). */
    data object TrustDomain : QuickFix

    /** Add an app-scoped Trust rule for the IP (Trusted only apps, when no domain is known). */
    data object TrustIp : QuickFix

    /** Open the universal rules with the row for [focusKey] (a [FirewallRuleset] id) highlighted. */
    data class OpenUniversalRules(val focusKey: String) : QuickFix

    /** Open Proxy & VPN: WireGuard lockdown and proxy errors are fixed there, not with a rule. */
    data object OpenProxySettings : QuickFix
}

/**
 * The reason for a network log row. [rule] is `ConnectionTracker.blockedByRule` parsed with
 * [FirewallRuleset.getFirewallRule]; [hasDomain] says whether the row knows the domain it looked up
 * (only then can Trusted only be fixed with a domain rule).
 */
fun blockReasonFor(rule: FirewallRuleset?, hasDomain: Boolean, blocklists: List<String> = emptyList()): BlockReason {
    if (rule == null) return BlockReason(R.string.firewall_rule_no_rule_desc)
    val fixes: Pair<QuickFix?, QuickFix?> = when (rule) {
        // The app's own Wi‑Fi / mobile data flags. RULE1 is the "both" flag, and new apps are
        // stored with it when "Block new apps" is on, so allowing the app is the rule's own undo.
        FirewallRuleset.RULE1 -> QuickFix.AllowApp(BlockedNetwork.Everywhere) to QuickFix.AllowTemporarily
        FirewallRuleset.RULE1D -> QuickFix.AllowApp(BlockedNetwork.Wifi) to QuickFix.AllowTemporarily
        FirewallRuleset.RULE1E -> QuickFix.AllowApp(BlockedNetwork.MobileData) to QuickFix.AllowTemporarily
        // Temporary allow is checked after the new-app gate, so it can't help here.
        FirewallRuleset.RULE1B -> QuickFix.AllowApp(BlockedNetwork.Everywhere) to null
        FirewallRuleset.RULE1G -> (if (hasDomain) QuickFix.TrustDomain else QuickFix.TrustIp) to null
        FirewallRuleset.RULE2 -> QuickFix.RemoveIpRule(universal = false) to null
        FirewallRuleset.RULE2D -> QuickFix.RemoveIpRule(universal = true) to null
        FirewallRuleset.RULE2E -> QuickFix.RemoveDomainRule(universal = false) to null
        FirewallRuleset.RULE2H -> QuickFix.RemoveDomainRule(universal = true) to null
        FirewallRuleset.RULE2G -> (if (hasDomain) QuickFix.TrustDomain else null) to null
        // Universal settings: route to the row, never write a rule.
        FirewallRuleset.RULE1F, FirewallRuleset.RULE3, FirewallRuleset.RULE4, FirewallRuleset.RULE5,
        FirewallRuleset.RULE6, FirewallRuleset.RULE7, FirewallRuleset.RULE10, FirewallRuleset.RULE11,
        -> QuickFix.OpenUniversalRules(rule.id) to null
        FirewallRuleset.RULE17, FirewallRuleset.RULE18 -> QuickFix.OpenProxySettings to null
        // Allowed verdicts, and blocks Keepout can't undo from here: an internal error (1C), no
        // network route (13), a lookup for a Private DNS server that doesn't exist (14), and a
        // paused app under lockdown (16, ends with the pause).
        FirewallRuleset.RULE0, FirewallRuleset.RULE1C, FirewallRuleset.RULE1H, FirewallRuleset.RULE2B,
        FirewallRuleset.RULE2C, FirewallRuleset.RULE2F, FirewallRuleset.RULE2I, FirewallRuleset.RULE8,
        FirewallRuleset.RULE9, FirewallRuleset.RULE9B, FirewallRuleset.RULE12, FirewallRuleset.RULE13,
        FirewallRuleset.RULE14, FirewallRuleset.RULE15, FirewallRuleset.RULE16, FirewallRuleset.RULE19,
        -> null to null
    }
    return BlockReason(
        sentence = rule.desc,
        primaryFix = fixes.first,
        secondaryFix = fixes.second,
        blocklists = if (rule == FirewallRuleset.RULE2G) blocklists else emptyList(),
    )
}

/**
 * The reason for a DNS log row. A blocked lookup offers an app-scoped Trust rule for the domain
 * and names the [blocklists] that stopped it; an allowed one has no fix.
 */
fun dnsBlockReasonFor(blocked: Boolean, blocklists: List<String>): BlockReason =
    if (blocked) {
        BlockReason(R.string.firewall_rule_dns_blocked_desc, primaryFix = QuickFix.TrustDomain, blocklists = blocklists)
    } else {
        BlockReason(R.string.connection_dns_allowed)
    }

/**
 * The app's connection status after [QuickFix.AllowApp] on [network]: only that network's flag
 * clears, so "Allow on Wi‑Fi" on an app blocked everywhere leaves it blocked on mobile data.
 */
fun ConnectionStatus.allowing(network: BlockedNetwork): ConnectionStatus = when (network) {
    BlockedNetwork.Everywhere -> ConnectionStatus.ALLOW
    BlockedNetwork.Wifi -> when (this) {
        ConnectionStatus.UNMETERED -> ConnectionStatus.ALLOW
        ConnectionStatus.BOTH -> ConnectionStatus.METERED
        ConnectionStatus.METERED, ConnectionStatus.ALLOW -> this
    }
    BlockedNetwork.MobileData -> when (this) {
        ConnectionStatus.METERED -> ConnectionStatus.ALLOW
        ConnectionStatus.BOTH -> ConnectionStatus.UNMETERED
        ConnectionStatus.UNMETERED, ConnectionStatus.ALLOW -> this
    }
}

/** `DnsLog.blockLists` / `ConnectionTracker.blocklists` as names: comma separated, blanks dropped. */
fun parseBlocklists(raw: String?): List<String> =
    raw.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).distinct()
