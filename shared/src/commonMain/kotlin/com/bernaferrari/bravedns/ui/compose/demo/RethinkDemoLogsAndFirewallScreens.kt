/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.bernaferrari.bravedns.ui.components.*
import com.bernaferrari.bravedns.ui.compose.apps.*
import com.bernaferrari.bravedns.ui.compose.common.*
import com.bernaferrari.bravedns.ui.compose.configure.*
import com.bernaferrari.bravedns.ui.compose.database.*
import com.bernaferrari.bravedns.ui.compose.dns.*
import com.bernaferrari.bravedns.ui.compose.events.*
import com.bernaferrari.bravedns.ui.compose.firewall.*
import com.bernaferrari.bravedns.ui.compose.home.*
import com.bernaferrari.bravedns.ui.compose.logs.*
import com.bernaferrari.bravedns.ui.compose.rpn.*
import com.bernaferrari.bravedns.ui.compose.settings.*
import com.bernaferrari.bravedns.ui.compose.statistics.*
import com.bernaferrari.bravedns.ui.compose.theme.*
import com.bernaferrari.bravedns.ui.compose.wireguard.*
import kotlinx.coroutines.launch

@Composable
internal fun DemoLogsScreen(
    modifier: Modifier,
    onBack: (() -> Unit)?,
    viewSwitch: (@Composable () -> Unit)? = null,
    title: String? = null,
) {
    var networkFilter by remember { mutableStateOf(RethinkLogFilter.All) }
    var dnsFilter by remember { mutableStateOf(RethinkLogFilter.All) }
    var selectedNetworkApp by remember { mutableStateOf<String?>(null) }
    var selectedDnsApp by remember { mutableStateOf<String?>(null) }
    var selectedRules by remember { mutableStateOf(emptySet<String>()) }
    var showNetworkAppPicker by remember { mutableStateOf(false) }
    var showDnsAppPicker by remember { mutableStateOf(false) }
    var showRulesPicker by remember { mutableStateOf(false) }
    var confirmClearNetworkLogs by remember { mutableStateOf(false) }
    var confirmClearDnsLogs by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }
    var networkLogs by remember { mutableStateOf(demoNetworkLogs) }
    var dnsLogs by remember { mutableStateOf(demoDnsLogs) }
    val networkApps = remember(networkLogs) { networkLogs.toDemoLogAppOptions() }
    val dnsApps = remember(dnsLogs) { dnsLogs.toDemoLogAppOptions() }
    val visibleNetworkLogs = networkLogs.filterFor(networkFilter, selectedNetworkApp)
    val visibleDnsLogs = dnsLogs.filterFor(dnsFilter, selectedDnsApp)

    RethinkLogsScreenShell(
        strings = if (title != null) demoLogsStrings.copy(title = title) else demoLogsStrings,
        onBackClick = onBack,
        modifier = modifier,
        viewSwitch = viewSwitch,
        networkContent = { onToolbarActionsChange ->
            LaunchedEffect(networkLogs) {
                onToolbarActionsChange(
                    RethinkLogToolbarActions(
                        onRefresh = { networkLogs = demoNetworkLogs },
                        onClear = networkLogs.takeIf { it.isNotEmpty() }?.let { { confirmClearNetworkLogs = true } },
                    ),
                )
            }
            Column(Modifier.fillMaxSize()) {
                RethinkLogsControlsDeck {
                    RethinkToolbarRow {
                        RethinkLogFilterRow(
                            selected = networkFilter,
                            strings = demoLogListStrings,
                            onSelected = { networkFilter = it },
                            modifier = Modifier.weight(1f),
                        )
                        RethinkToolbarIconToggle(
                            icon = MaterialSymbols.Filled.FilterList,
                            contentDescription = "Reason",
                            selected = selectedRules.isNotEmpty(),
                            count = selectedRules.size,
                            onClick = { showRulesPicker = true },
                        )
                        RethinkToolbarIconToggle(
                            icon = MaterialSymbols.Filled.Apps,
                            contentDescription = "Filter apps",
                            selected = selectedNetworkApp != null,
                            onClick = { showNetworkAppPicker = true },
                        )
                    }
                }
                RethinkLogList(
                    state = visibleNetworkLogs.toDemoLogListState(),
                    strings = demoLogListStrings,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        dnsContent = { onToolbarActionsChange ->
            LaunchedEffect(dnsLogs) {
                onToolbarActionsChange(
                    RethinkLogToolbarActions(
                        onRefresh = { dnsLogs = demoDnsLogs },
                        onClear = dnsLogs.takeIf { it.isNotEmpty() }?.let { { confirmClearDnsLogs = true } },
                    ),
                )
            }
            Column(Modifier.fillMaxSize()) {
                RethinkLogsControlsDeck {
                    RethinkToolbarRow {
                        RethinkLogFilterRow(
                            selected = dnsFilter,
                            strings = demoLogListStrings,
                            onSelected = { dnsFilter = it },
                            modifier = Modifier.weight(1f),
                        )
                        RethinkToolbarIconToggle(
                            icon = MaterialSymbols.Filled.Apps,
                            contentDescription = "Filter apps",
                            selected = selectedDnsApp != null,
                            onClick = { showDnsAppPicker = true },
                        )
                    }
                }
                RethinkLogList(
                    state = visibleDnsLogs.toDemoLogListState(),
                    strings = demoLogListStrings,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )

    if (showNetworkAppPicker) {
        RethinkLogAppFilterDialog(
            options = networkApps,
            selectedId = selectedNetworkApp,
            searchQuery = appSearchQuery,
            isLoading = false,
            strings = demoLogAppFilterStrings(networkApps.size),
            onSearchQueryChange = { appSearchQuery = it },
            onSelect = { selectedNetworkApp = it; showNetworkAppPicker = false },
            onClearSelection = { selectedNetworkApp = null },
            onDismiss = { showNetworkAppPicker = false },
            appIcon = { option -> RethinkWebDemoAppIcon(RethinkWebDemoAppPresentations.packageNameFor(option.label), option.label, Modifier.size(34.dp)) },
        )
    }
    if (showDnsAppPicker) {
        RethinkLogAppFilterDialog(
            options = dnsApps,
            selectedId = selectedDnsApp,
            searchQuery = appSearchQuery,
            isLoading = false,
            strings = demoLogAppFilterStrings(dnsApps.size),
            onSearchQueryChange = { appSearchQuery = it },
            onSelect = { selectedDnsApp = it; showDnsAppPicker = false },
            onClearSelection = { selectedDnsApp = null },
            onDismiss = { showDnsAppPicker = false },
            appIcon = { option -> RethinkWebDemoAppIcon(RethinkWebDemoAppPresentations.packageNameFor(option.label), option.label, Modifier.size(34.dp)) },
        )
    }
    if (showRulesPicker) {
        RethinkLogRulesDialog(
            rules = demoLogRules,
            selectedIds = selectedRules,
            strings = RethinkLogRulesStrings("Rules", "Clear", "Dismiss"),
            onToggle = { id -> selectedRules = if (id in selectedRules) selectedRules - id else selectedRules + id },
            onClear = { selectedRules = emptySet() },
            onDismiss = { showRulesPicker = false },
        )
    }
    if (confirmClearNetworkLogs) {
        DemoClearLogsDialog(
            title = "Clear network logs?",
            message = "This removes the sample network log history. You can restore it with Refresh.",
            onDismiss = { confirmClearNetworkLogs = false },
            onConfirm = { networkLogs = emptyList(); confirmClearNetworkLogs = false },
        )
    }
    if (confirmClearDnsLogs) {
        DemoClearLogsDialog(
            title = "Clear DNS logs?",
            message = "This removes the sample DNS log history. You can restore it with Refresh.",
            onDismiss = { confirmClearDnsLogs = false },
            onConfirm = { dnsLogs = emptyList(); confirmClearDnsLogs = false },
        )
    }
}
private fun List<RethinkLogRowModel>.filterFor(filter: RethinkLogFilter, appId: String?) = filter { row ->
    (filter == RethinkLogFilter.All || (filter == RethinkLogFilter.Blocked) == row.isBlocked) &&
        (appId == null || row.appLabel == appId)
}

private fun List<RethinkLogRowModel>.toDemoLogListState(): RethinkLogListState =
    if (isEmpty()) RethinkLogListState.Empty else RethinkLogListState.Content(this)

@Composable
internal fun DemoClearLogsDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = title,
        message = message,
        confirmText = "Clear logs",
        dismissText = "Cancel",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isConfirmDestructive = true,
    )
}

@Composable
internal fun DemoFirewallSettingsScreen(
    modifier: Modifier,
    onBack: () -> Unit,
    onCustomRules: () -> Unit,
    initialFocusKey: String? = null,
) {
    var settings by remember { mutableStateOf(demoUniversalFirewallSettings) }
    RethinkFirewallSettingsScreen(
        strings = RethinkFirewallSettingsStrings(
            title = "Firewall",
            explanation = "Rules that apply to every app.",
            rulesTitle = "Rules",
            rulesDescription = "Block or trust domains and IPs, for one app or all.",
            rulesWithCount = { "Rules · $it" },
            everydaySection = "Everyday",
            strictSection = "Strict",
            lockdownSection = "Lockdown",
            blocked = { "Blocked: $it" },
            lockdownConfirmTitle = "Turn on lockdown?",
            lockdownConfirmMessage = "Every app loses internet access unless it skips filtering or universal rules, or connects to a domain or IP you trust. To undo it, turn Lockdown off here.",
            lockdownConfirm = "Turn on lockdown",
            cancel = "Cancel",
            accessibilityTitle = "Accessibility permission",
            accessibilityDescription = "Enable accessibility to block apps while they are in the background.",
            accessibilityConfirm = "Open settings",
            accessibilityDismiss = "Cancel",
        ),
        everyday = settings.take(3),
        strict = settings.drop(3).dropLast(1),
        lockdown = settings.last(),
        rulesCount = demoFirewallRules.size,
        isLoadingStats = false,
        onSettingChange = { id, checked ->
            settings = settings.map { if (it.id == id) it.copy(checked = checked) else it }
            RethinkUniversalFirewallChange.Applied
        },
        onRulesClick = onCustomRules,
        onLogsClick = {},
        onOpenAccessibilitySettings = {},
        initialFocusKey = initialFocusKey,
        onBackClick = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun DemoCustomRulesScreen(modifier: Modifier, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(RethinkRulesTab.DOMAIN) }
    var scope by remember { mutableStateOf<RethinkRulesScope>(RethinkRulesScope.All) }
    var ipQuery by remember { mutableStateOf("") }
    var domainQuery by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf(demoFirewallRules) }
    var adding by remember { mutableStateOf(false) }
    val query = if (tab == RethinkRulesTab.IP) ipQuery else domainQuery
    fun inScope(rule: RethinkFirewallRule) = when (val s = scope) {
        RethinkRulesScope.All -> true
        RethinkRulesScope.AllApps -> rule.uid == -1000
        is RethinkRulesScope.App -> rule.uid == s.uid
    }
    val visible = rules.filter { rule ->
        rule.tab == tab && inScope(rule) && (query.isBlank() || rule.value.contains(query, ignoreCase = true))
    }.sortedWith(compareBy({ it.uid != -1000 }, { it.uid }))
    RethinkCustomRulesScreen(
        title = "Rules",
        selectedTab = tab,
        scope = scope,
        query = query,
        rules = RethinkInMemoryFirewallRulesFeed(visible),
        strings = demoCustomRulesStrings,
        domainCount = rules.count { it.tab == RethinkRulesTab.DOMAIN && inScope(it) },
        ipCount = rules.count { it.tab == RethinkRulesTab.IP && inScope(it) },
        onTabChange = { tab = it },
        onScopeChange = { scope = it },
        onQueryChange = { if (tab == RethinkRulesTab.IP) ipQuery = it else domainQuery = it },
        onAddRule = { adding = true },
        onDeleteRule = { _, rule -> rules = rules.filterNot { it.id == rule.id } },
        onBackClick = onBack,
        modifier = modifier,
    )
    if (adding) {
        var ruleScope by remember { mutableStateOf<RethinkAddRuleScope>(RethinkAddRuleScope.AllApps) }
        RethinkAddRuleSheet(
            target = tab,
            scope = ruleScope,
            strings = demoAddRuleStrings,
            onScopeChange = { ruleScope = it },
            onDismiss = { adding = false },
            onAdd = { value, action, added ->
                val isIp = tab == RethinkRulesTab.IP
                if (isIp && value.none { it.isDigit() }) {
                    RethinkRulesAddResult(false, "Enter a valid IP address.")
                } else {
                    rules = rules + RethinkFirewallRule(
                        id = "demo-rule-${rules.size}",
                        uid = (added as? RethinkAddRuleScope.App)?.uid ?: -1000,
                        value = value,
                        tab = tab,
                        status = if (action == RethinkRuleAction.Trust) RethinkFirewallRuleStatus.Trust else RethinkFirewallRuleStatus.Block,
                    )
                    RethinkRulesAddResult.Success
                }
            },
        )
    }
}

@Composable
internal fun DemoFirewallAppListScreen(
    modifier: Modifier,
    onBack: () -> Unit,
    onAppInfo: (RethinkFirewallApp) -> Unit,
) {
    var apps by remember { mutableStateOf(demoFirewallApps) }
    var isRefreshing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(RethinkFirewallFilters()) }
    var activeBulkActions by remember { mutableStateOf(emptySet<RethinkFirewallBulkAction>()) }
    val refreshScope = androidx.compose.runtime.rememberCoroutineScope()
    val catalogByPackage = remember {
        RethinkWebDemoAppCatalog.entries.associateBy { it.packageName }
    }
    val visibleApps = apps.filter { app ->
        val catalogEntry = catalogByPackage[app.packageName]
        val queryMatches = query.isBlank() || app.appName.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)
        val topLevelMatches = when (filters.topLevel) {
            RethinkFirewallTopLevelFilter.Installed -> catalogEntry?.isSystemApp != true
            RethinkFirewallTopLevelFilter.System -> catalogEntry?.isSystemApp == true
            RethinkFirewallTopLevelFilter.All -> true
        }
        val categoryMatches = filters.categories.isEmpty() || catalogEntry?.category in filters.categories
        val statusMatches = when (filters.status) {
            RethinkFirewallFilter.All -> true
            RethinkFirewallFilter.Allowed -> app.status == RethinkFirewallAppStatus.Allowed
            RethinkFirewallFilter.Blocked -> app.status == RethinkFirewallAppStatus.Blocked
            RethinkFirewallFilter.Bypass -> app.status == RethinkFirewallAppStatus.Bypass
            RethinkFirewallFilter.Excluded -> app.status == RethinkFirewallAppStatus.Excluded
            RethinkFirewallFilter.Lockdown -> app.status == RethinkFirewallAppStatus.Lockdown
            RethinkFirewallFilter.BlockedWifi -> app.wifiBlocked
            RethinkFirewallFilter.BlockedMobile -> app.mobileBlocked
        }
        queryMatches && topLevelMatches && categoryMatches && statusMatches
    }
    fun updateApp(updated: RethinkFirewallApp) {
        apps = apps.map { if (it.id == updated.id) updated else it }
    }
    RethinkFirewallAppListScreen(
        filterState = com.bernaferrari.bravedns.ui.compose.firewall.RethinkAppListFilterState(query, filters.status, filters),
        apps = visibleApps,
        activeBulkActions = activeBulkActions,
        strings = demoFirewallAppListStrings,
        isRefreshing = isRefreshing,
        onQueryChange = { query = it; filters = filters.copy(query = it) },
        onRefresh = {
            if (!isRefreshing) {
                refreshScope.launch {
                    isRefreshing = true
                    kotlinx.coroutines.delay(450)
                    apps = demoFirewallApps
                    isRefreshing = false
                }
            }
        },
        onFiltersChange = { filters = it; query = it.query },
        onQuickFilterChange = { filters = filters.copy(status = it) },
        onBulkAction = { action ->
            activeBulkActions = if (action in activeBulkActions) activeBulkActions - action else activeBulkActions + action
        },
        loadCategories = { topLevel ->
            RethinkWebDemoAppCatalog.entries
                .filter { entry ->
                    when (topLevel) {
                        RethinkFirewallTopLevelFilter.Installed -> !entry.isSystemApp
                        RethinkFirewallTopLevelFilter.System -> entry.isSystemApp
                        RethinkFirewallTopLevelFilter.All -> true
                    }
                }
                .map { it.category }
                .filter(String::isNotBlank)
                .distinct()
        },
        onAppClick = onAppInfo,
        onWifiToggle = { app -> updateApp(app.copy(wifiBlocked = !app.wifiBlocked, status = if (!app.wifiBlocked || app.mobileBlocked) RethinkFirewallAppStatus.Blocked else RethinkFirewallAppStatus.Allowed)) },
        onMobileToggle = { app -> updateApp(app.copy(mobileBlocked = !app.mobileBlocked, status = if (!app.mobileBlocked || app.wifiBlocked) RethinkFirewallAppStatus.Blocked else RethinkFirewallAppStatus.Allowed)) },
        appIcon = { app -> RethinkWebDemoAppIcon(app.packageName, app.appName) },
        onBackClick = onBack,
        modifier = modifier,
    )
}

/** Web preview for the same shared per-app detail UI used by Android. */
@Composable
internal fun DemoAppInfoScreen(modifier: Modifier, app: RethinkFirewallApp?, onBack: () -> Unit) {
    val current = app ?: demoFirewallApps.first()
    var wifiBlocked by remember(current.id) { mutableStateOf(current.wifiBlocked) }
    var mobileBlocked by remember(current.id) { mutableStateOf(current.mobileBlocked) }
    var isolated by remember(current.id) { mutableStateOf(current.status == RethinkFirewallAppStatus.Lockdown) }
    var bypassDns by remember(current.id) { mutableStateOf(false) }
    var bypassUniversal by remember(current.id) { mutableStateOf(current.status == RethinkFirewallAppStatus.Bypass) }
    var excluded by remember(current.id) { mutableStateOf(current.status == RethinkFirewallAppStatus.Excluded) }
    var proxyExcluded by remember(current.id) { mutableStateOf(false) }
    var tempAllowed by remember(current.id) { mutableStateOf(false) }
    val rows = listOf(
        RethinkAppInfoLogItem("0", "1.1.1.1", "cloudflare-dns.com"),
        RethinkAppInfoLogItem("1", "142.250.184.14", "www.google.com"),
    )
    val status = when {
        isolated -> "Isolated"
        excluded -> "Excluded"
        bypassUniversal -> "Bypasses universal firewall"
        bypassDns -> "Bypasses DNS firewall"
        wifiBlocked && mobileBlocked -> "Blocked"
        wifiBlocked -> "Wi‑Fi blocked"
        mobileBlocked -> "Mobile data blocked"
        else -> "Allowed"
    }
    RethinkAppInfoScreen(
        modifier = modifier,
        state = RethinkAppInfoState(
            appAvailable = true,
            title = current.appName,
            subtitle = current.packageName,
            status = status,
            temporaryAllowed = tempAllowed,
            proxyDetails = if (proxyExcluded) "Excluded from proxy routing" else "Uses default proxy routing",
            wifiBlocked = wifiBlocked,
            mobileBlocked = mobileBlocked,
            isolated = isolated,
            bypassDnsFirewall = bypassDns,
            bypassUniversalFirewall = bypassUniversal,
            excluded = excluded,
            proxyExcluded = proxyExcluded,
            tempAllowed = tempAllowed,
            activeConnections = RethinkAppInfoLogSection("Top active connections", 2, loading = false, empty = false, entries = rows),
            domainLogs = RethinkAppInfoLogSection("Most contacted domains", 2, loading = false, empty = false, entries = rows.map { it.copy(title = it.subtitle.orEmpty(), subtitle = it.title) }),
            ipLogs = RethinkAppInfoLogSection("Most contacted IPs", 2, loading = false, empty = false, entries = rows),
        ),
        strings = RethinkAppInfoStrings(
            unavailable = "This app is no longer available.", status = "Status", temporaryAllow = "Temporarily allowed",
            firewall = "Firewall", wifi = "Wi‑Fi", mobile = "Mobile data",
            allowed = "Allowed", blocked = "Blocked",
            modeTitle = "How Keepout treats this app", normal = "Normal", normalDescription = "Follows your firewall and DNS rules",
            isolate = "Allow only trusted", isolateDescription = "Blocks everything except the domains and IPs you trust",
            bypassDns = "Bypass DNS and firewall", bypassDnsDescription = "Skips DNS blocking and firewall rules",
            bypassUniversal = "Bypass universal rules", bypassUniversalDescription = "Ignores the rules that apply to every app",
            exclude = "Exclude from Keepout", excludeDescription = "Its traffic goes around Keepout entirely",
            advanced = "Advanced", proxyExclude = "Exclude from proxy", proxyExcludeDescription = "Keep this app outside proxy routing.",
            temporaryAllowDescription = "Allow traffic temporarily.", rules = "Rules", systemAppInfo = "System app info", ipRules = "IP rules", domainRules = "Domain rules", loading = "Loading", empty = "No activity yet.",
        ),
        onBackClick = onBack,
        onWifiClick = { wifiBlocked = !wifiBlocked },
        onMobileClick = { mobileBlocked = !mobileBlocked },
        onIsolateClick = {
            val next = !isolated
            isolated = next
            if (next) { bypassDns = false; bypassUniversal = false; excluded = false }
        },
        onBypassDnsClick = {
            val next = !bypassDns
            bypassDns = next
            if (next) { isolated = false; bypassUniversal = false; excluded = false }
        },
        onBypassUniversalClick = {
            val next = !bypassUniversal
            bypassUniversal = next
            if (next) { isolated = false; bypassDns = false; excluded = false }
        },
        onExcludeClick = {
            val next = !excluded
            excluded = next
            if (next) { isolated = false; bypassDns = false; bypassUniversal = false }
        },
        onProxyExcludedChange = { proxyExcluded = it },
        onTempAllowChange = { tempAllowed = it },
        onSystemAppInfo = {}, onIpRules = {}, onDomainRules = {}, logActions = com.bernaferrari.bravedns.ui.compose.apps.RethinkAppInfoLogsActions(com.bernaferrari.bravedns.ui.compose.apps.RethinkAppInfoLogActions(), com.bernaferrari.bravedns.ui.compose.apps.RethinkAppInfoLogActions(), com.bernaferrari.bravedns.ui.compose.apps.RethinkAppInfoLogActions()),
        titleLeading = {
            RethinkWebDemoAppIcon(
                current.packageName,
                current.appName,
                Modifier.size(36.dp),
            )
        },
    )
}
