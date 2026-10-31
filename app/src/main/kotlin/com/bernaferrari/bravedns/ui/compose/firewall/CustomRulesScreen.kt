/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFirewallRuleStatus
import com.bernaferrari.bravedns.database.CustomDomain
import com.bernaferrari.bravedns.database.CustomIp
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.DomainRulesManager
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.service.IpRulesManager
import Logger
import com.bernaferrari.bravedns.util.Constants.Companion.UNSPECIFIED_PORT
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.bernaferrari.bravedns.viewmodel.CustomDomainViewModel
import com.bernaferrari.bravedns.viewmodel.CustomIpViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android adapter for the Rules screen. [uid] "everybody" opens the full screen with its scope chip
 * ([initialMode]: every rule, or the all-apps ones); an app uid keeps the list to that app and names
 * it in the title. Adds go through [AddRuleSheetHost] (Block or Trust, for all apps or one), deletes
 * through [IpRulesManager] / [DomainRulesManager]. [initialQuery] prefills the opened tab's search.
 */
@Composable
fun CustomRulesScreen(
    uid: Int = UID_EVERYBODY,
    initialTab: RethinkRulesTab = RethinkRulesTab.IP,
    initialMode: RethinkRulesMode = RethinkRulesMode.SELECTED_UID,
    domainViewModel: CustomDomainViewModel,
    ipViewModel: CustomIpViewModel,
    eventLogger: EventLogger,
    initialQuery: String = "",
    onBackClick: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val lockedToApp = uid != UID_EVERYBODY
    var selectedTab by rememberSaveable(uid, initialTab) { mutableStateOf(initialTab) }
    var ipQuery by rememberSaveable(uid) { mutableStateOf(if (initialTab == RethinkRulesTab.IP) initialQuery else "") }
    var domainQuery by rememberSaveable(uid) { mutableStateOf(if (initialTab == RethinkRulesTab.DOMAIN) initialQuery else "") }
    var lockedAppName by remember(uid) { mutableStateOf("") }
    var chosenScope by remember(uid, initialMode) {
        mutableStateOf(
            when {
                lockedToApp -> RethinkRulesScope.App(uid, "")
                initialMode == RethinkRulesMode.ALL_APPS -> RethinkRulesScope.All
                else -> RethinkRulesScope.AllApps
            },
        )
    }
    val rulesScope = (chosenScope as? RethinkRulesScope.App)?.takeIf { lockedToApp }?.copy(label = lockedAppName) ?: chosenScope
    val appNames = remember { mutableStateMapOf<Int, String>() }
    val query = if (selectedTab == RethinkRulesTab.IP) ipQuery else domainQuery
    val picker = rememberRuleAppPicker()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val editRule = rememberRuleEditAction(snackbar, eventLogger)

    LaunchedEffect(uid) {
        if (lockedToApp) lockedAppName = withContext(Dispatchers.IO) { FirewallManager.getAppNameByUid(uid).orEmpty() }
    }
    val listUid = (rulesScope as? RethinkRulesScope.App)?.uid ?: UID_EVERYBODY
    LaunchedEffect(listUid) {
        ipViewModel.setUid(listUid)
        domainViewModel.setUid(listUid)
    }
    LaunchedEffect(selectedTab, rulesScope, query) {
        delay(250)
        if (selectedTab == RethinkRulesTab.IP) ipViewModel.setFilter(query) else domainViewModel.setFilter(query)
    }

    val feed: RethinkFirewallRulesFeed = when (selectedTab) {
        RethinkRulesTab.IP -> {
            val items = when (rulesScope) {
                RethinkRulesScope.All -> ipViewModel.everyIpRule.collectAsLazyPagingItems()
                else -> ipViewModel.customIpDetails.collectAsLazyPagingItems()
            }
            AndroidRulesFeed(items) { it.toRethinkRule(appNames[it.uid]) }
        }
        RethinkRulesTab.DOMAIN -> {
            val items = when (rulesScope) {
                RethinkRulesScope.All -> domainViewModel.everyDomainRule.collectAsLazyPagingItems()
                else -> domainViewModel.customDomains.collectAsLazyPagingItems()
            }
            AndroidRulesFeed(items) { it.toRethinkRule(appNames[it.uid]) }
        }
    }
    val domainCount by remember(rulesScope is RethinkRulesScope.All, listUid) {
        if (rulesScope is RethinkRulesScope.All) domainViewModel.everyDomainRuleCount() else domainViewModel.domainRulesCount(listUid)
    }.collectAsState(initial = null)
    val ipCount by remember(rulesScope is RethinkRulesScope.All, listUid) {
        if (rulesScope is RethinkRulesScope.All) ipViewModel.everyIpRuleCount() else ipViewModel.ipRulesCount(listUid)
    }.collectAsState(initial = null)

    fun loadOwnerName(ruleUid: Int) {
        if (ruleUid == UID_EVERYBODY || appNames.containsKey(ruleUid)) return
        scope.launch {
            val name = withContext(Dispatchers.IO) { FirewallManager.getAppNameByUid(ruleUid).orEmpty() }
            appNames[ruleUid] = name
        }
    }

    val rulesTitle = stringResource(R.string.lbl_rules)
    RethinkCustomRulesScreen(
        title = if (lockedToApp && lockedAppName.isNotBlank()) stringResource(R.string.rules_title_app, lockedAppName) else rulesTitle,
        selectedTab = selectedTab,
        scope = rulesScope,
        query = query,
        rules = feed,
        strings = androidCustomRulesStrings(),
        domainCount = domainCount,
        ipCount = ipCount,
        // Import writes to one owner: the app on its own screen, otherwise the all-apps rules.
        importAction = { RulesImportAction(listUid, selectedTab) },
        onTabChange = { selectedTab = it },
        onScopeChange = if (lockedToApp) null else ({ chosenScope = it }),
        onChooseApp = if (lockedToApp) null else ({ picker.open { app -> chosenScope = RethinkRulesScope.App(app.uid, app.label) } }),
        onQueryChange = { value ->
            if (selectedTab == RethinkRulesTab.IP) ipQuery = value else domainQuery = value
        },
        onAddRule = { showAddSheet = true },
        onDeleteRule = { tab, rule ->
            scope.launch(Dispatchers.IO) {
                when (tab) {
                    RethinkRulesTab.IP -> IpRulesManager.removeIpRule(rule.uid, rule.value, rule.port ?: UNSPECIFIED_PORT)
                    RethinkRulesTab.DOMAIN -> DomainRulesManager.deleteDomain(
                        CustomDomain().apply {
                            domain = rule.value
                            this.uid = rule.uid
                        },
                    )
                }
                eventLogger.log(
                    EventType.FW_RULE_MODIFIED,
                    Severity.LOW,
                    if (tab == RethinkRulesTab.IP) "Removed IP rule" else "Removed domain rule",
                    EventSource.UI,
                    false,
                    if (tab == RethinkRulesTab.IP) "IP: ${rule.value}" else "Domain: ${rule.value}",
                )
            }
        },
        onRuleOwnerVisible = ::loadOwnerName,
        onBackClick = onBackClick,
        onEditRule = editRule,
        snackbarHostState = snackbar,
    )

    if (showAddSheet) {
        val app = rulesScope as? RethinkRulesScope.App
        AddRuleSheetHost(
            target = selectedTab,
            // The sheet starts on the scope being looked at; All rules starts on all apps.
            initialScope = app?.let { RethinkAddRuleScope.App(it.uid, it.label.ifBlank { stringResource(R.string.rules_this_app) }) }
                ?: RethinkAddRuleScope.AllApps,
            eventLogger = eventLogger,
            onDismiss = { showAddSheet = false },
            onChooseApp = if (lockedToApp) null else picker::open,
        )
    }
    RuleAppPickerHost(picker, selectedUid = (rulesScope as? RethinkRulesScope.App)?.uid)
}

/**
 * A tapped rule's Block · Trust · Delete, written through the managers by [applyRuleEdit], then an
 * Undo snackbar that restores the exact previous rule. A newer edit retires an older Undo.
 */
@Composable
private fun rememberRuleEditAction(
    snackbar: SnackbarHostState,
    eventLogger: EventLogger,
): (RethinkFirewallRule, RethinkRuleEdit) -> Unit {
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.lbl_undo)
    val failed = stringResource(R.string.rules_edit_failed)
    val blocked = stringResource(R.string.rules_edit_blocked)
    val trusted = stringResource(R.string.rules_edit_trusted)
    val deleted = stringResource(R.string.rules_edit_deleted)
    var epoch by remember { mutableIntStateOf(0) }
    return { rule, edit ->
        val next = ++epoch
        snackbar.currentSnackbarData?.dismiss()
        val message = when (edit) {
            RethinkRuleEdit.Delete -> deleted
            is RethinkRuleEdit.SetAction -> if (edit.action == RethinkRuleAction.Trust) trusted else blocked
        }.format(rule.value)
        val log = "${rule.tab.name} ${rule.value}, ${rule.uid}: $edit"
        var undo: (suspend () -> Unit)? = null
        scope.launchReversibleRuleMutation(
            snackbarHostState = snackbar,
            editedMessage = message,
            undoLabel = undoLabel,
            epoch = next,
            currentEpoch = { epoch },
            setApplying = {},
            apply = {
                val result = runCatching {
                    withContext(Dispatchers.IO + NonCancellable) { applyRuleEdit(ManagerRuleEditStore, rule, edit) }
                }
                result.exceptionOrNull()?.let { error ->
                    Logger.e(Logger.LOG_TAG_UI, "rules: edit failed, $log", error as? Exception)
                    scope.launch { snackbar.showSnackbar(failed) }
                }
                undo = result.getOrNull()
                if (undo != null) logFirewallRuleChange(eventLogger, "Changed rule", log)
                undo != null
            },
            undo = {
                runCatching { withContext(Dispatchers.IO + NonCancellable) { undo?.invoke() } }
                    .onFailure { Logger.e(Logger.LOG_TAG_UI, "rules: undo failed, $log", it as? Exception) }
                logFirewallRuleChange(eventLogger, "Changed rule", "undo: $log")
            },
        )
    }
}

private class AndroidRulesFeed<T : Any>(
    private val items: LazyPagingItems<T>,
    private val mapper: (T) -> RethinkFirewallRule,
) : RethinkFirewallRulesFeed {
    override val itemCount: Int get() = items.itemCount
    override val isLoading: Boolean get() = items.loadState.refresh is LoadState.Loading

    override fun get(index: Int): RethinkFirewallRule? = items[index]?.let(mapper)
}

private fun CustomIp.toRethinkRule(ownerName: String?): RethinkFirewallRule = RethinkFirewallRule(
    id = "ip:$uid:$ipAddress:$port:$protocol",
    uid = uid,
    value = ipAddress,
    port = port.takeUnless { it == UNSPECIFIED_PORT },
    tab = RethinkRulesTab.IP,
    status = when (IpRulesManager.IpRuleStatus.getStatus(status)) {
        IpRulesManager.IpRuleStatus.BLOCK -> RethinkFirewallRuleStatus.Block
        IpRulesManager.IpRuleStatus.TRUST -> RethinkFirewallRuleStatus.Trust
        IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL -> RethinkFirewallRuleStatus.Bypass
        IpRulesManager.IpRuleStatus.NONE -> RethinkFirewallRuleStatus.None
    },
    ownerName = ownerName,
)

private fun CustomDomain.toRethinkRule(ownerName: String?): RethinkFirewallRule = RethinkFirewallRule(
    id = "domain:$uid:$domain",
    uid = uid,
    value = domain,
    tab = RethinkRulesTab.DOMAIN,
    status = when (DomainRulesManager.Status.getStatus(status)) {
        DomainRulesManager.Status.BLOCK -> RethinkFirewallRuleStatus.Block
        DomainRulesManager.Status.TRUST -> RethinkFirewallRuleStatus.Trust
        DomainRulesManager.Status.NONE -> RethinkFirewallRuleStatus.None
    },
    ownerName = ownerName,
)

@Composable
private fun androidCustomRulesStrings(): RethinkCustomRulesStrings {
    val portFormat = stringResource(R.string.custom_rule_port_format)
    return androidCustomRulesStrings(port = { portFormat.format(it) })
}

@Composable
private fun androidCustomRulesStrings(port: (Int) -> String): RethinkCustomRulesStrings {
    val countFormat = stringResource(R.string.rules_tab_count)
    val onlyApp = stringResource(R.string.rules_edit_only_app)
    return RethinkCustomRulesStrings(
        port = port,
        domains = stringResource(R.string.rules_tab_domains),
        ips = stringResource(R.string.rules_tab_ips),
        withCount = { label, count -> countFormat.format(label, count) },
        searchDomains = stringResource(R.string.rules_search_domains),
        searchIps = stringResource(R.string.rules_search_ips),
        clearSearch = stringResource(R.string.cd_clear_search),
        loading = stringResource(R.string.lbl_loading),
        noIpRules = stringResource(R.string.ci_no_rules_text),
        noDomainRules = stringResource(R.string.cd_no_rules_text),
        add = stringResource(R.string.add_rule_title),
        cancel = stringResource(R.string.lbl_cancel),
        delete = stringResource(R.string.lbl_delete),
        block = stringResource(R.string.ci_block),
        trust = stringResource(R.string.ci_trust_rule),
        bypass = stringResource(R.string.firewall_status_whitelisted),
        noRule = stringResource(R.string.ci_no_rule),
        uid = { value -> "UID $value" },
        scopeAll = stringResource(R.string.rules_scope_all),
        scopeAllApps = stringResource(R.string.rules_scope_all_apps),
        scopeChooseApp = stringResource(R.string.add_rule_choose_app),
        allAppsHeader = stringResource(R.string.lbl_all_apps),
        deleteRuleTitle = stringResource(R.string.custom_rule_delete_title),
        deleteSelectedTitle = { count -> pluralStringResource(R.plurals.custom_rules_delete_selected_title, count, count) },
        selectedCount = { count -> pluralStringResource(R.plurals.custom_rules_selected_count, count, count) },
        clearSelection = stringResource(R.string.custom_rules_clear_selection),
        select = stringResource(R.string.custom_rules_select),
        deleteRule = stringResource(R.string.custom_rules_delete_rule),
        action = stringResource(R.string.lbl_action),
        onlyApp = { app -> onlyApp.format(app) },
    )
}
