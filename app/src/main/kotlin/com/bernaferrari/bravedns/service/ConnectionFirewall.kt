package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.util.Constants.Companion.INVALID_UID
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.bernaferrari.bravedns.util.KnownPorts
import com.bernaferrari.bravedns.util.Protocol

data class FirewallVerdict(
    val blocked: Boolean,
    val rule: FirewallRuleset,
    val matchedQuery: String? = null,
    val closeTrackedOnDeviceLock: Boolean = false,
    val logs: List<String> = emptyList()
)

data class AppFirewallSnapshot(
    val uid: Int,
    val rethinkUid: Int,
    val appStatus: FirewallManager.FirewallStatus,
    val connectionStatus: FirewallManager.ConnectionStatus,
    val isTempAllowed: Boolean,
    val allowOrbot: Boolean,
    val isMissingOrInvalidUid: Boolean,
    val isUidSystemApp: Boolean,
    val newAppStillBlocked: Boolean,
    val backgroundBlocked: Boolean
)

data class FirewallFlags(
    val blockMeteredConnections: Boolean = false,
    val universalLockdown: Boolean = false,
    val blockHttpConnections: Boolean = false,
    val blockWhenDeviceLocked: Boolean = false,
    val deviceLocked: Boolean = false,
    val udpBlocked: Boolean = false,
    val blockUnknownConnections: Boolean = false,
    val blockNewlyInstalledApp: Boolean = false,
    val disallowDnsBypass: Boolean = false,
    val preventDnsLeaks: Boolean = false,
    val isDnsFirewallMode: Boolean = false,
    val isLockdown: Boolean = false,
    val isAppPaused: Boolean = false
)

data class NetworkSnapshot(
    val isActiveNetworkCellular: Boolean = false,
    val isActiveNetworkMetered: Boolean = false,
    val useActive: Boolean = true,
    val vpnLockdown: Boolean = false,
    val treatOnlyMobileNetworkAsMetered: Boolean = false,
    val destIfaceCellular: Boolean = false,
    val destIfaceMetered: Boolean = false
)

data class ConnectionRequest(
    val connId: String,
    val destIP: String,
    val destPort: Int,
    val protocol: Int,
    val query: String?,
    val domains: String?,
    val anyRealIpBlocked: Boolean,
    val isSplApp: Boolean,
    val rinr: Boolean,
    val debugConnInfo: String,
    val app: AppFirewallSnapshot,
    val flags: FirewallFlags,
    val network: NetworkSnapshot,
    val isMetered: (() -> Boolean)? = null,
    val isAlg: Boolean = false,
    val forDnsAnswer: Boolean = false,
)

class ConnectionFirewall(
    private val domainRule: (String?, Int) -> Pair<DomainRulesManager.Status, String?> = { _, _ ->
        Pair(DomainRulesManager.Status.NONE, null)
    },
    private val ipRule: (Int, String, Int) -> IpRulesManager.IpRuleStatus = { _, _, _ ->
        IpRulesManager.IpRuleStatus.NONE
    },
    private val allowOrbot: suspend (Int) -> Boolean = { false },
    private val onUntrackedApp: suspend (Int) -> Unit = {},
    private val resolveNewAppBlocked: suspend (Int) -> Boolean = { false },
    private val resolveBackgroundBlocked: suspend (Int) -> Boolean = { false },
    private val resolveIsUidSystemApp: suspend (Int) -> Boolean = { false },
    private val resolveDeviceLocked: () -> Boolean = { false }
) {

    suspend fun evaluate(request: ConnectionRequest): FirewallVerdict {
        val connId = request.connId
        val uid = request.app.uid
        var query = request.query

        if (uid == request.app.rethinkUid && !request.rinr) {
            return allow(
                FirewallRuleset.RULE0,
                log = "firewall($connId): rethink uid, ${request.app.rethinkUid}, not processing firewall rules"
            )
        }

        val logs = mutableListOf("firewall($connId): ${request.debugConnInfo}")

        if (request.app.allowOrbot || allowOrbot(uid)) {
            return allow(FirewallRuleset.RULE9B, logs = logs)
        }

        if (unknownAppBlocked(request)) {
            return block(FirewallRuleset.RULE5, logs, "firewall($connId): unknown app blocked, $uid")
        }

        if (request.app.appStatus.isUntracked() && uid != INVALID_UID) {
            onUntrackedApp(uid)
            if (newAppBlocked(request) || resolveNewAppStillBlocked(request)) {
                return block(FirewallRuleset.RULE1B, logs, "firewall($connId): new app blocked, $uid")
            }
        }

        if (request.app.isTempAllowed) {
            return allow(FirewallRuleset.RULE19, logs, "firewall($connId): temp allowed, $uid")
        }

        val appRuleset = appBlocked(request)
        if (appRuleset != null) {
            return block(appRuleset, logs, "firewall($connId): app blocked, $uid")
        }

        if (request.flags.isLockdown && request.flags.isAppPaused) {
            return block(FirewallRuleset.RULE16, logs, "firewall($connId): lockdown, app paused, $uid")
        }

        val dp = domainRule(request.domains, uid)
        if (!dp.second.isNullOrEmpty()) {
            query = dp.second
        }
        when (dp.first) {
            DomainRulesManager.Status.BLOCK -> {
                return block(FirewallRuleset.RULE2E, logs, "firewall($connId): domain blocked, $uid", query)
            }
            DomainRulesManager.Status.TRUST -> {
                return allow(FirewallRuleset.RULE2F, logs, "firewall($connId): domain trusted, $uid", query)
            }
            DomainRulesManager.Status.NONE -> {
                // fall-through
            }
        }

        when (ipRuleForRequest(uid, request)) {
            IpRulesManager.IpRuleStatus.BLOCK -> {
                return block(FirewallRuleset.RULE2, logs, "firewall($connId): ip blocked, $uid", query)
            }
            IpRulesManager.IpRuleStatus.TRUST -> {
                return allow(FirewallRuleset.RULE2B, logs, "firewall($connId): ip trusted, $uid", query)
            }
            IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL,
            IpRulesManager.IpRuleStatus.NONE -> {
                // no-op; pass-through
            }
        }

        if (request.app.appStatus.bypassDnsFirewall()) {
            return allow(FirewallRuleset.RULE1H, logs, "firewall($connId): bypass dns firewall, $uid", query)
        }

        if (request.app.appStatus.isolate()) {
            return block(FirewallRuleset.RULE1G, logs, "firewall($connId): isolate mode, $uid", query)
        }

        val globalDomainPair = domainRule(request.domains, UID_EVERYBODY)
        val globalDomainRule = globalDomainPair.first
        if (!globalDomainPair.second.isNullOrEmpty()) {
            query = globalDomainPair.second
        }

        if (request.app.appStatus.bypassUniversal()) {
            if (request.anyRealIpBlocked && globalDomainRule != DomainRulesManager.Status.TRUST) {
                return block(
                    FirewallRuleset.RULE2G,
                    logs,
                    "firewall($connId): bypass universal, dns blocked, $uid, $query",
                    query
                )
            }
            return if (dnsProxied(request)) {
                allow(
                    FirewallRuleset.RULE9,
                    logs,
                    "firewall($connId): bypass universal, dns proxied, $uid",
                    query
                )
            } else {
                allow(FirewallRuleset.RULE8, logs, "firewall($connId): bypass universal, $uid", query)
            }
        }

        when (globalDomainRule) {
            DomainRulesManager.Status.TRUST -> {
                return allow(
                    FirewallRuleset.RULE2I,
                    logs,
                    "firewall($connId): global domain trusted, $uid, $query",
                    query
                )
            }
            DomainRulesManager.Status.BLOCK -> {
                return block(
                    FirewallRuleset.RULE2H,
                    logs,
                    "firewall($connId): global domain blocked, $uid, $query",
                    query
                )
            }
            else -> {
                // fall through
            }
        }

        when (ipRuleForRequest(UID_EVERYBODY, request)) {
            IpRulesManager.IpRuleStatus.BLOCK -> {
                return block(
                    FirewallRuleset.RULE2D,
                    logs,
                    "firewall($connId): global ip blocked, $uid, ${request.destIP}",
                    query
                )
            }
            IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL -> {
                return allow(
                    FirewallRuleset.RULE2C,
                    logs,
                    "firewall($connId): global ip bypass universal, $uid, ${request.destIP}",
                    query
                )
            }
            IpRulesManager.IpRuleStatus.TRUST,
            IpRulesManager.IpRuleStatus.NONE -> {
                // no-op; pass-through
            }
        }

        if (request.anyRealIpBlocked) {
            return block(FirewallRuleset.RULE2G, logs, "firewall($connId): dns blocked, $uid, $query", query)
        }

        if (request.isSplApp) {
            return allow(FirewallRuleset.RULE0, logs, "firewall($connId): special app, $uid, $query", query)
        }

        if (request.flags.blockMeteredConnections && isMetered(request)) {
            return block(FirewallRuleset.RULE1F, logs, "firewall($connId): metered blocked, $uid", query)
        }

        if (request.flags.universalLockdown) {
            return block(FirewallRuleset.RULE11, logs, "firewall($connId): universal lockdown, $uid", query)
        }

        if (httpBlocked(request)) {
            return block(FirewallRuleset.RULE10, logs, "firewall($connId): http blocked, $uid", query)
        }

        if (deviceLocked(request.flags) || (request.flags.blockWhenDeviceLocked && resolveDeviceLocked())) {
            logs.add("firewall($connId): device locked, $uid")
            return FirewallVerdict(
                blocked = FirewallRuleset.ground(FirewallRuleset.RULE3),
                rule = FirewallRuleset.RULE3,
                matchedQuery = query,
                closeTrackedOnDeviceLock = true,
                logs = logs
            )
        }

        if (udpBlocked(request, request.app.isUidSystemApp || resolveIsUidSystemApp(uid))) {
            return block(FirewallRuleset.RULE6, logs, "firewall($connId): udp blocked, $uid", query)
        }

        if (request.app.backgroundBlocked || resolveBackgroundBlocked(uid)) {
            return block(
                FirewallRuleset.RULE4,
                logs,
                "firewall($connId): background data blocked, $uid",
                query
            )
        }

        if (dnsProxied(request)) {
            return allow(FirewallRuleset.RULE9, logs, "firewall($connId): dns proxied, $uid", query)
        }

        if (dnsBypassed(request, query)) {
            return block(FirewallRuleset.RULE7, logs, "firewall($connId): dns bypassed, $uid", query)
        }

        logs.add("no firewall rule($connId), uid=$uid")
        return allow(FirewallRuleset.RULE0, logs, matchedQuery = query)
    }

    private fun ipRuleForRequest(uid: Int, request: ConnectionRequest): IpRulesManager.IpRuleStatus {
        val addresses = request.destIP.split(',').map(String::trim).filter(String::isNotEmpty)
        val candidates = if (request.isAlg) addresses else addresses.take(1)
        val rules = candidates.map { ipRule(uid, it, request.destPort) }
        val trusted = if (uid == UID_EVERYBODY) IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL else IpRulesManager.IpRuleStatus.TRUST
        return when {
            trusted in rules -> trusted
            IpRulesManager.IpRuleStatus.BLOCK in rules -> IpRulesManager.IpRuleStatus.BLOCK
            else -> IpRulesManager.IpRuleStatus.NONE
        }
    }

    private fun unknownAppBlocked(request: ConnectionRequest): Boolean {
        return request.flags.blockUnknownConnections && request.app.isMissingOrInvalidUid
    }

    private fun newAppBlocked(request: ConnectionRequest): Boolean {
        return request.flags.blockNewlyInstalledApp &&
            !request.app.isMissingOrInvalidUid &&
            request.app.newAppStillBlocked
    }

    private suspend fun resolveNewAppStillBlocked(request: ConnectionRequest): Boolean {
        return request.flags.blockNewlyInstalledApp &&
            !request.app.isMissingOrInvalidUid &&
            resolveNewAppBlocked(request.app.uid)
    }

    private fun appBlocked(request: ConnectionRequest): FirewallRuleset? {
        val connectionStatus = request.app.connectionStatus
        if (connectionStatus.blocked()) {
            return FirewallRuleset.RULE1
        }
        val isMetered = isMetered(request)
        if (connectionStatus.wifi() && !isMetered) {
            return FirewallRuleset.RULE1D
        }
        if (connectionStatus.mobileData() && isMetered) {
            return FirewallRuleset.RULE1E
        }
        return null
    }

    private fun httpBlocked(request: ConnectionRequest): Boolean {
        return !request.forDnsAnswer && request.destPort == KnownPorts.HTTP_PORT && request.flags.blockHttpConnections
    }

    private fun deviceLocked(flags: FirewallFlags): Boolean {
        return flags.blockWhenDeviceLocked && flags.deviceLocked
    }

    private fun udpBlocked(request: ConnectionRequest, isUidSystemApp: Boolean): Boolean {
        if (request.forDnsAnswer || !request.flags.udpBlocked) return false
        if (request.protocol != Protocol.UDP.protocolType) return false
        if (KnownPorts.isDns(request.destPort)) return false
        val isNtpFromSystemApp = KnownPorts.isNtp(request.destPort) && isUidSystemApp
        return !isNtpFromSystemApp
    }

    private fun dnsProxied(request: ConnectionRequest): Boolean {
        return !request.forDnsAnswer && request.flags.isDnsFirewallMode &&
            request.flags.preventDnsLeaks &&
            KnownPorts.isDns(request.destPort)
    }

    private fun dnsBypassed(request: ConnectionRequest, query: String?): Boolean {
        return !request.forDnsAnswer && request.flags.disallowDnsBypass && query.isNullOrEmpty()
    }

    private fun allow(
        rule: FirewallRuleset,
        logs: List<String> = emptyList(),
        log: String? = null,
        matchedQuery: String? = null
    ): FirewallVerdict {
        return FirewallVerdict(
            blocked = FirewallRuleset.ground(rule),
            rule = rule,
            matchedQuery = matchedQuery,
            logs = if (log == null) logs else logs + log
        )
    }

    private fun block(
        rule: FirewallRuleset,
        logs: List<String>,
        log: String,
        matchedQuery: String? = null
    ): FirewallVerdict {
        return FirewallVerdict(
            blocked = FirewallRuleset.ground(rule),
            rule = rule,
            matchedQuery = matchedQuery,
            logs = logs + log
        )
    }

    private fun isMetered(request: ConnectionRequest): Boolean {
        return request.isMetered?.invoke() ?: isConnectionMetered(request.network)
    }

    companion object {
        fun isConnectionMetered(network: NetworkSnapshot): Boolean {
            val treatMobileAsMetered = network.treatOnlyMobileNetworkAsMetered
            if (!network.useActive || network.vpnLockdown) {
                return if (treatMobileAsMetered) {
                    network.destIfaceCellular
                } else {
                    network.destIfaceMetered
                }
            }
            return if (treatMobileAsMetered) {
                network.isActiveNetworkCellular
            } else {
                network.isActiveNetworkMetered
            }
        }
    }
}
