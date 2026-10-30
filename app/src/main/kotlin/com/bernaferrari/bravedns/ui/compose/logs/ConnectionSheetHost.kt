/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.logs

import Logger
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.ConnectionTracker
import com.bernaferrari.bravedns.database.CustomDomain
import com.bernaferrari.bravedns.database.CustomIp
import com.bernaferrari.bravedns.database.DnsLog
import com.bernaferrari.bravedns.rules.IpRuleInput
import com.bernaferrari.bravedns.service.DomainRulesManager
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.service.FirewallManager.ConnectionStatus
import com.bernaferrari.bravedns.service.FirewallRuleset
import com.bernaferrari.bravedns.service.IpRuleNormalizer
import com.bernaferrari.bravedns.service.IpRulesManager
import com.bernaferrari.bravedns.service.IpRulesManager.IpRuleStatus
import com.bernaferrari.bravedns.ui.compose.common.AppIcon
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleAction
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRulesTab
import com.bernaferrari.bravedns.ui.compose.firewall.launchReversibleRuleMutation
import com.bernaferrari.bravedns.ui.compose.firewall.logFirewallRuleChange
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.bernaferrari.bravedns.util.UIUtils
import inet.ipaddr.IPAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One log row as the Connection sheet needs it, from either log. Build it with
 * [ConnectionTracker.toConnectionSheetRequest] or [DnsLog.toConnectionSheetRequest]; Stats and
 * App detail rows can fill it directly. Labels arrive formatted, as the rows already format them.
 */
@Immutable
data class ConnectionSheetRequest(
    /** The app's uid; negative (unknown app) hides every app-scoped action. */
    val uid: Int,
    val appName: String,
    val packageName: String? = null,
    /** The domain looked up, when known. Only an exact name becomes a domain rule. */
    val domain: String? = null,
    val ipAddress: String? = null,
    val port: Int = 0,
    val timeLabel: String,
    /** "HTTPS" for connections, the record type ("A") for lookups. */
    val protocolLabel: String? = null,
    val isBlocked: Boolean,
    /** Connections: `ConnectionTracker.blockedByRule`, a [FirewallRuleset] id. Null for lookups. */
    val ruleId: String? = null,
    val isDnsLookup: Boolean = false,
    /** Names of the DNS blocklists that stopped it ([parseBlocklists]). */
    val blocklists: List<String> = emptyList(),
    /** The key/value table behind "Details" (the rows' existing details lists fit as is). */
    val details: List<RethinkLogDetail> = emptyList(),
)

fun ConnectionTracker.toConnectionSheetRequest(
    timeLabel: String,
    protocolLabel: String?,
    details: List<RethinkLogDetail> = emptyList(),
) = ConnectionSheetRequest(
    uid = uid,
    appName = appName,
    packageName = packageName.takeIf { it.isNotBlank() },
    domain = dnsQuery?.takeIf { it.isNotBlank() },
    ipAddress = ipAddress.takeIf { it.isNotBlank() },
    port = port,
    timeLabel = timeLabel,
    protocolLabel = protocolLabel,
    isBlocked = isBlocked,
    ruleId = blockedByRule.takeIf { it.isNotBlank() },
    blocklists = parseBlocklists(blocklists),
    details = details,
)

fun DnsLog.toConnectionSheetRequest(
    timeLabel: String,
    typeLabel: String?,
    details: List<RethinkLogDetail> = emptyList(),
) = ConnectionSheetRequest(
    uid = uid,
    appName = appName,
    packageName = packageName.takeIf { it.isNotBlank() },
    domain = queryStr.takeIf { it.isNotBlank() },
    timeLabel = timeLabel,
    protocolLabel = typeLabel,
    isBlocked = isBlocked,
    isDnsLookup = true,
    blocklists = parseBlocklists(blockLists),
    details = details,
)

/**
 * Where the sheet's links lead. Every one is optional: a null destination hides its action, so
 * the sheet works before navigation is wired (WS1 wires these; the sheet dismisses itself first).
 */
@Immutable
data class ConnectionSheetNavigation(
    /** Universal rules with the row for a [FirewallRuleset] id highlighted. */
    val onOpenUniversalRules: ((focusKey: String) -> Unit)? = null,
    val onOpenProxySettings: (() -> Unit)? = null,
    /** The rules list for [uid] (UID_EVERYBODY for all apps) on [tab], searching [query]. */
    val onOpenRules: ((uid: Int, tab: RethinkRulesTab, query: String) -> Unit)? = null,
    /** "Open Clock": the app's page in Keepout (App detail). */
    val onOpenApp: ((uid: Int) -> Unit)? = null,
)

/**
 * Android adapter for [RethinkConnectionSheet]: reads the app's flags and the domain/IP rules,
 * maps [BlockReason] fixes to buttons, and performs every write through [FirewallManager],
 * [DomainRulesManager] and [IpRulesManager] with an Undo snackbar.
 *
 * Fixes change only the rule that decided [request] ([BlockReason]); the rule group edits the
 * *exact* rule for the target and scope, preserving an existing rule's proxy settings, and an
 * Undo of a rule the sheet created deletes it again rather than leaving a "No rule" row.
 * [onRulesChanged] runs after every write and undo, for hosts that cache rule state.
 */
@Composable
fun ConnectionSheetHost(
    request: ConnectionSheetRequest,
    eventLogger: EventLogger,
    onDismiss: () -> Unit,
    navigation: ConnectionSheetNavigation = ConnectionSheetNavigation(),
    onRulesChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val appUid = request.uid.takeIf { it >= 0 }
    val domain = remember(request.domain) { request.domain?.let { DnsQueryRuleEditor.exactQueryDomain(it) } }
    val ip = remember(request.ipAddress) { request.ipAddress?.let(IpRuleInput::parse)?.address }
    val reason = remember(request, domain) {
        if (request.isDnsLookup) {
            dnsBlockReasonFor(request.isBlocked, request.blocklists)
        } else {
            blockReasonFor(request.ruleId?.let { FirewallRuleset.getFirewallRule(it) }, hasDomain = domain != null, request.blocklists)
        }
    }
    // The group targets the domain when there is an exact one, else the IP.
    val targetKind = when {
        domain != null -> RethinkConnectionTargetKind.Domain
        ip != null -> RethinkConnectionTargetKind.Ip
        else -> null
    }

    var ruleScope by remember { mutableStateOf(if (appUid != null) RethinkConnectionRuleScope.App else RethinkConnectionRuleScope.AllApps) }
    var refresh by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<HostState?>(null) }
    var applied by remember { mutableStateOf(emptySet<QuickFix>()) }
    var busy by remember { mutableStateOf(false) }
    var epoch by remember { mutableIntStateOf(0) }

    fun ruleUid(scope: RethinkConnectionRuleScope) = if (scope == RethinkConnectionRuleScope.App && appUid != null) appUid else UID_EVERYBODY

    LaunchedEffect(request, ruleScope, refresh) {
        state = withContext(Dispatchers.IO) { loadState(request, appUid, domain, ip, ruleUid(ruleScope), ruleScope) }
    }

    val undoLabel = stringResource(R.string.lbl_undo)
    val failedMessage = stringResource(R.string.connection_save_failed)
    val appName = request.appName.ifBlank { stringResource(R.string.network_log_app_name_unknown) }

    /** Write, refresh, then offer Undo. A newer write retires an older snackbar's Undo. */
    fun mutate(message: String, fix: QuickFix?, log: String, write: suspend () -> (suspend () -> Unit)) {
        if (busy) return
        val next = epoch + 1
        epoch = next
        snackbar.currentSnackbarData?.dismiss()
        var undo: (suspend () -> Unit)? = null
        scope.launchReversibleRuleMutation(
            snackbarHostState = snackbar,
            editedMessage = message,
            undoLabel = undoLabel,
            epoch = next,
            currentEpoch = { epoch },
            setApplying = { busy = it },
            apply = {
                val result = runCatching { withContext(Dispatchers.IO + NonCancellable) { sheetWrites.withLock { write() } } }
                result.exceptionOrNull()?.let { error ->
                    Logger.e(Logger.LOG_TAG_UI, "connection sheet: write failed, $log", error as? Exception)
                    scope.launch { snackbar.showSnackbar(failedMessage) }
                }
                undo = result.getOrNull()
                if (undo != null) {
                    logFirewallRuleChange(eventLogger, "Connection sheet", log)
                    fix?.let { applied = applied + it }
                    refresh++
                    onRulesChanged()
                }
                undo != null
            },
            undo = {
                runCatching { withContext(Dispatchers.IO + NonCancellable) { sheetWrites.withLock { undo?.invoke() } } }
                    .onFailure { Logger.e(Logger.LOG_TAG_UI, "connection sheet: undo failed, $log", it as? Exception) }
                logFirewallRuleChange(eventLogger, "Connection sheet", "undo: $log")
                fix?.let { applied = applied - it }
                refresh++
                onRulesChanged()
            },
        )
    }

    fun navigate(action: () -> Unit) {
        onDismiss()
        action()
    }

    @Composable
    fun actionFor(fix: QuickFix?): RethinkConnectionAction? {
        val current = state ?: return null
        if (fix == null || fix in applied) return null
        return when (fix) {
            is QuickFix.AllowApp -> {
                val uid = appUid?.takeIf { current.hasApp } ?: return null
                val status = current.connectionStatus ?: return null
                val next = status.allowing(fix.network)
                // The flag is already clear (changed since): nothing left to fix here.
                if (next == status) return null
                val (label, done) = when (fix.network) {
                    BlockedNetwork.Wifi -> R.string.connection_fix_allow_app_wifi to R.string.connection_done_allowed_wifi
                    BlockedNetwork.MobileData -> R.string.connection_fix_allow_app_mobile to R.string.connection_done_allowed_mobile
                    BlockedNetwork.Everywhere -> R.string.connection_fix_allow_app_everywhere to R.string.connection_done_allowed
                }
                val message = stringResource(done, appName)
                RethinkConnectionAction(stringResource(label, appName), icon = networkIcon(fix.network), onClick = {
                    mutate(message, fix, "allow app $uid: $status -> $next") {
                        val appStatus = FirewallManager.appStatus(uid).takeUnless { it.isUntracked() } ?: FirewallManager.FirewallStatus.NONE
                        val before = FirewallManager.connectionStatus(uid)
                        FirewallManager.updateFirewallStatus(uid, appStatus, before.allowing(fix.network))
                        return@mutate { FirewallManager.updateFirewallStatus(uid, appStatus, before) }
                    }
                })
            }
            QuickFix.AllowTemporarily -> {
                val uid = appUid?.takeIf { current.hasApp && !current.tempAllowed } ?: return null
                val message = stringResource(R.string.connection_done_allowed_temporarily, appName)
                RethinkConnectionAction(stringResource(R.string.temp_allow_label), icon = MaterialSymbols.Filled.Timer, onClick = {
                    mutate(message, fix, "temp allow $uid") {
                        FirewallManager.updateTempAllow(uid, true)
                        return@mutate { FirewallManager.updateTempAllow(uid, false) }
                    }
                })
            }
            is QuickFix.RemoveDomainRule -> {
                val ruleUid = if (fix.universal) UID_EVERYBODY else appUid ?: return null
                val name = domain
                if (name == null || !current.blockingDomainRule(fix.universal)) {
                    // Only a wildcard matched (or no exact name): the rule lives in the list.
                    return openRulesAction(navigation, ruleUid, RethinkRulesTab.DOMAIN, request.domain.orEmpty(), ::navigate)
                }
                val message = stringResource(R.string.connection_done_rule_removed)
                RethinkConnectionAction(stringResource(R.string.connection_fix_remove_rule), icon = MaterialSymbols.Filled.DeleteOutline, onClick = {
                    mutate(message, fix, "remove domain rule $name, $ruleUid") { writeDomainRule(name, ruleUid, "", DomainRulesManager.Status.NONE) }
                })
            }
            is QuickFix.RemoveIpRule -> {
                val ruleUid = if (fix.universal) UID_EVERYBODY else appUid ?: return null
                val address = ip
                val port = current.blockingIpRulePort(fix.universal)
                if (address == null || port == null) {
                    return openRulesAction(navigation, ruleUid, RethinkRulesTab.IP, request.ipAddress.orEmpty(), ::navigate)
                }
                val message = stringResource(R.string.connection_done_rule_removed)
                RethinkConnectionAction(stringResource(R.string.connection_fix_remove_rule), icon = MaterialSymbols.Filled.DeleteOutline, onClick = {
                    mutate(message, fix, "remove ip rule $address:$port, $ruleUid") { writeIpRule(ruleUid, address, IpRuleStatus.NONE, port) }
                })
            }
            QuickFix.TrustDomain -> {
                val uid = appUid ?: return null
                val name = domain?.takeIf { current.appDomainRule != DomainRulesManager.Status.TRUST } ?: return null
                val message = stringResource(R.string.connection_done_domain_trusted, appName)
                RethinkConnectionAction(stringResource(R.string.connection_fix_allow_domain), icon = MaterialSymbols.Filled.Verified, onClick = {
                    mutate(message, fix, "trust domain $name, $uid") {
                        writeDomainRule(name, uid, request.ipAddress.orEmpty(), DomainRulesManager.Status.TRUST)
                    }
                })
            }
            QuickFix.TrustIp -> {
                val uid = appUid ?: return null
                val address = ip?.takeIf { current.appIpRule != IpRuleStatus.TRUST } ?: return null
                val message = stringResource(R.string.connection_done_ip_trusted, appName)
                RethinkConnectionAction(stringResource(R.string.connection_fix_trust_ip), icon = MaterialSymbols.Filled.Verified, onClick = {
                    mutate(message, fix, "trust ip $address, $uid") { writeIpRule(uid, address, IpRuleStatus.TRUST) }
                })
            }
            is QuickFix.OpenUniversalRules -> navigation.onOpenUniversalRules?.let { open ->
                RethinkConnectionAction(stringResource(R.string.connection_fix_open_universal), icon = MaterialSymbols.Filled.Tune, onClick = { navigate { open(fix.focusKey) } })
            }
            QuickFix.OpenProxySettings -> navigation.onOpenProxySettings?.let { open ->
                RethinkConnectionAction(stringResource(R.string.connection_fix_open_proxy), icon = MaterialSymbols.Filled.ConversionPath, onClick = { navigate(open) })
            }
        }
    }

    val primary = actionFor(reason.primaryFix)
    // Allowing the app outright makes temporary access moot.
    val secondary = actionFor(reason.secondaryFix).takeUnless { reason.primaryFix != null && reason.primaryFix in applied }
    val savedMessage = stringResource(R.string.connection_done_rule_saved)
    val current = state
    val rule = if (targetKind != null && current != null) {
        RethinkConnectionRule(
            kind = targetKind,
            value = if (targetKind == RethinkConnectionTargetKind.Domain) domain.orEmpty() else request.ipAddress.orEmpty(),
            action = current.groupAction,
            scope = ruleScope,
            note = if (current.groupInherited) stringResource(R.string.connection_wider_rule) else null,
            enabled = !busy,
        )
    } else {
        null
    }
    val title = domain ?: request.domain ?: request.ipAddress ?: appName
    val copiedMessage = stringResource(R.string.copied_clipboard)

    RethinkConnectionSheet(
        model = RethinkConnectionSheetModel(
            title = title,
            subtitle = listOfNotNull(appName, request.timeLabel, request.protocolLabel).filter { it.isNotBlank() }.joinToString(" · "),
            isBlocked = request.isBlocked,
            verdict = stringResource(reason.sentence),
            verdictCaption = reason.blocklists.takeIf { it.isNotEmpty() }?.let {
                stringResource(R.string.connection_blocked_by_lists, it.joinToString(", "))
            },
            primaryAction = primary,
            secondaryAction = secondary,
            rule = rule,
            details = request.details,
        ),
        strings = RethinkConnectionSheetStrings(
            thisDomain = stringResource(R.string.connection_this_domain),
            thisIp = stringResource(R.string.connection_this_ip),
            block = stringResource(R.string.ci_block),
            trust = stringResource(R.string.ci_trust_rule),
            noRule = stringResource(R.string.ci_no_rule),
            onlyApp = stringResource(R.string.connection_only_app, appName),
            allApps = stringResource(R.string.lbl_all_apps),
            details = stringResource(R.string.log_pane_details),
            copyAddress = stringResource(R.string.connection_copy_address),
            openApp = stringResource(R.string.connection_open_app, appName),
        ),
        onDismiss = onDismiss,
        onRuleActionChange = { action ->
            val uid = ruleUid(ruleScope)
            val universal = uid == UID_EVERYBODY
            when (targetKind) {
                RethinkConnectionTargetKind.Domain -> domain?.let { name ->
                    mutate(savedMessage, null, "domain rule $name, $uid -> $action") {
                        writeDomainRule(name, uid, request.ipAddress.orEmpty(), action.toDomainStatus())
                    }
                }
                RethinkConnectionTargetKind.Ip -> ip?.let { address ->
                    mutate(savedMessage, null, "ip rule $address, $uid -> $action") {
                        writeIpRule(uid, address, action.toIpStatus(universal))
                    }
                }
                null -> Unit
            }
        },
        onRuleScopeChange = { if (appUid != null || it == RethinkConnectionRuleScope.AllApps) ruleScope = it },
        appIcon = {
            request.packageName?.let { AppIcon(it, appName = request.appName) } ?: appUid?.let { AppIcon(it) }
        },
        onCopyAddress = {
            UIUtils.clipboardCopy(context, title, context.getString(R.string.connection_copy_address))
            scope.launch { snackbar.showSnackbar(copiedMessage) }
        },
        onOpenApp = appUid?.let { uid -> navigation.onOpenApp?.let { open -> { navigate { open(uid) } } } },
        snackbarHostState = snackbar,
    )
}

/** Serialises sheet writes: the managers' DB rows and native tries must change together. */
private val sheetWrites = Mutex()

/** What the sheet read for its target, app and scope; reloaded after every write and undo. */
private data class HostState(
    val hasApp: Boolean,
    val connectionStatus: ConnectionStatus?,
    val tempAllowed: Boolean,
    /** The app's and the universal exact domain rule, for "Remove this rule" and "Allow this domain". */
    val appDomainRule: DomainRulesManager.Status,
    val universalDomainRule: DomainRulesManager.Status,
    /** The exact IP rule port (connection port, else any port) that blocks it, per scope. */
    val appIpBlockPort: Int?,
    val universalIpBlockPort: Int?,
    val appIpRule: IpRuleStatus,
    /** The rule group: the exact rule in the selected scope, and whether another rule decides. */
    val groupAction: RethinkRuleAction,
    val groupInherited: Boolean,
) {
    fun blockingDomainRule(universal: Boolean) =
        (if (universal) universalDomainRule else appDomainRule) == DomainRulesManager.Status.BLOCK

    fun blockingIpRulePort(universal: Boolean) = if (universal) universalIpBlockPort else appIpBlockPort
}

private suspend fun loadState(
    request: ConnectionSheetRequest,
    appUid: Int?,
    domain: String?,
    ip: IPAddress?,
    groupUid: Int,
    groupScope: RethinkConnectionRuleScope,
): HostState {
    val hasApp = appUid != null && FirewallManager.hasUid(appUid)
    val storage = ip?.let(IpRuleNormalizer::storageText)
    suspend fun domainRule(uid: Int?) = if (uid == null || domain == null) {
        DomainRulesManager.Status.NONE
    } else {
        DomainRulesManager.Status.getStatus(DomainRulesManager.getObj(uid, domain)?.status)
    }
    suspend fun ipRule(uid: Int?, port: Int) = if (uid == null || storage == null) {
        IpRuleStatus.NONE
    } else {
        IpRuleStatus.getStatus(IpRulesManager.getObj(uid, storage, port)?.status)
    }
    suspend fun ipBlockPort(uid: Int?): Int? = listOf(request.port, 0).distinct().firstOrNull { ipRule(uid, it) == IpRuleStatus.BLOCK }

    val (groupAction, inherited) = when {
        domain != null -> {
            val exact = domainRule(groupUid)
            exact.toRuleAction() to (exact == DomainRulesManager.Status.NONE && DomainRulesManager.status(domain, groupUid) != DomainRulesManager.Status.NONE)
        }
        ip != null -> {
            val exact = ipRule(groupUid, 0)
            val effective = IpRulesManager.hasRule(groupUid, request.ipAddress.orEmpty(), request.port)
            exact.toRuleAction(groupScope) to (exact == IpRuleStatus.NONE && effective != IpRuleStatus.NONE)
        }
        else -> RethinkRuleAction.None to false
    }
    return HostState(
        hasApp = hasApp,
        connectionStatus = if (hasApp) FirewallManager.connectionStatus(appUid!!) else null,
        tempAllowed = hasApp && FirewallManager.isTempAllowed(appUid!!),
        appDomainRule = domainRule(appUid),
        universalDomainRule = domainRule(UID_EVERYBODY),
        appIpBlockPort = ipBlockPort(appUid),
        universalIpBlockPort = ipBlockPort(UID_EVERYBODY),
        appIpRule = ipRule(appUid, 0),
        groupAction = groupAction,
        groupInherited = inherited,
    )
}

/**
 * Set the exact domain rule for [uid] to [status] and return its undo. An existing rule changes in
 * place (keeping its proxy settings); a rule created here is deleted again on undo.
 */
private suspend fun writeDomainRule(domain: String, uid: Int, ips: String, status: DomainRulesManager.Status): suspend () -> Unit {
    val existing = DomainRulesManager.getObj(uid, domain)
    if (existing == null) {
        DomainRulesManager.changeStatus(domain, uid, ips, DomainRulesManager.DomainType.DOMAIN, status)
        return { DomainRulesManager.getObj(uid, domain)?.let { DomainRulesManager.deleteDomain(it) } }
    }
    val previous = DomainRulesManager.Status.getStatus(existing.status)
    existing.applyStatus(status)
    return { DomainRulesManager.getObj(uid, domain)?.applyStatus(previous) }
}

private suspend fun CustomDomain.applyStatus(status: DomainRulesManager.Status) = when (status) {
    DomainRulesManager.Status.TRUST -> DomainRulesManager.trust(this)
    DomainRulesManager.Status.BLOCK -> DomainRulesManager.block(this)
    DomainRulesManager.Status.NONE -> DomainRulesManager.noRule(this)
}

/** As [writeDomainRule], for the IP rule at [port] (0: any port). */
private suspend fun writeIpRule(uid: Int, address: IPAddress, status: IpRuleStatus, port: Int = 0): suspend () -> Unit {
    val storage = IpRuleNormalizer.storageText(address)
    val existing = IpRulesManager.getObj(uid, storage, port)
    if (existing == null) {
        IpRulesManager.addIpRule(uid, address, port.takeIf { it != 0 }, status, proxyId = "", proxyCC = "")
        return { IpRulesManager.removeIpRule(uid, storage, port) }
    }
    val previous = IpRuleStatus.getStatus(existing.status)
    existing.applyStatus(status)
    return { IpRulesManager.getObj(uid, storage, port)?.applyStatus(previous) }
}

private suspend fun CustomIp.applyStatus(status: IpRuleStatus) = when (status) {
    IpRuleStatus.BLOCK -> IpRulesManager.updateBlock(this)
    IpRuleStatus.TRUST -> IpRulesManager.updateTrust(this)
    IpRuleStatus.BYPASS_UNIVERSAL -> IpRulesManager.updateBypass(this)
    IpRuleStatus.NONE -> IpRulesManager.updateNoRule(this)
}

private fun DomainRulesManager.Status.toRuleAction() = when (this) {
    DomainRulesManager.Status.TRUST -> RethinkRuleAction.Trust
    DomainRulesManager.Status.BLOCK -> RethinkRuleAction.Block
    DomainRulesManager.Status.NONE -> RethinkRuleAction.None
}

private fun RethinkRuleAction.toDomainStatus() = when (this) {
    RethinkRuleAction.Trust -> DomainRulesManager.Status.TRUST
    RethinkRuleAction.Block -> DomainRulesManager.Status.BLOCK
    RethinkRuleAction.None, RethinkRuleAction.Bypass -> DomainRulesManager.Status.NONE
}

/**
 * The firewall honours an all-apps IP "trust" only as BYPASS_UNIVERSAL (a universal TRUST row is
 * ignored), so for all apps Trust reads and writes that status; per app it is TRUST.
 */
private fun IpRuleStatus.toRuleAction(scope: RethinkConnectionRuleScope = RethinkConnectionRuleScope.App) = when (this) {
    IpRuleStatus.BLOCK -> RethinkRuleAction.Block
    IpRuleStatus.TRUST -> if (scope == RethinkConnectionRuleScope.App) RethinkRuleAction.Trust else RethinkRuleAction.None
    IpRuleStatus.BYPASS_UNIVERSAL -> if (scope == RethinkConnectionRuleScope.AllApps) RethinkRuleAction.Trust else RethinkRuleAction.None
    IpRuleStatus.NONE -> RethinkRuleAction.None
}

private fun RethinkRuleAction.toIpStatus(universal: Boolean) = when (this) {
    RethinkRuleAction.Block -> IpRuleStatus.BLOCK
    RethinkRuleAction.Trust, RethinkRuleAction.Bypass -> if (universal) IpRuleStatus.BYPASS_UNIVERSAL else IpRuleStatus.TRUST
    RethinkRuleAction.None -> IpRuleStatus.NONE
}

private fun networkIcon(network: BlockedNetwork) = when (network) {
    BlockedNetwork.Wifi -> MaterialSymbols.Filled.Wifi
    BlockedNetwork.MobileData -> MaterialSymbols.Filled.SignalCellularAlt
    BlockedNetwork.Everywhere -> MaterialSymbols.Filled.CheckCircle
}

@Composable
private fun openRulesAction(
    navigation: ConnectionSheetNavigation,
    uid: Int,
    tab: RethinkRulesTab,
    query: String,
    navigate: (() -> Unit) -> Unit,
): RethinkConnectionAction? = navigation.onOpenRules?.let { open ->
    RethinkConnectionAction(stringResource(R.string.connection_fix_open_rules), icon = MaterialSymbols.Filled.Rule, onClick = { navigate { open(uid, tab, query) } })
}
