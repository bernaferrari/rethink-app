/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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
import com.bernaferrari.bravedns.util.Constants.Companion.UNSPECIFIED_PORT
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.bernaferrari.bravedns.viewmodel.CustomDomainViewModel
import com.bernaferrari.bravedns.viewmodel.CustomIpViewModel
import inet.ipaddr.IPAddressString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android data/service adapter for the common custom-rules renderer. */
@Composable
fun CustomRulesScreen(
    uid: Int = UID_EVERYBODY,
    initialTab: RethinkRulesTab = RethinkRulesTab.IP,
    initialMode: RethinkRulesMode = RethinkRulesMode.SELECTED_UID,
    domainViewModel: CustomDomainViewModel,
    ipViewModel: CustomIpViewModel,
    eventLogger: EventLogger,
    onBackClick: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val invalidIpMessage = stringResource(R.string.ci_dialog_error_invalid_ip)
    var selectedTab by rememberSaveable(uid, initialTab) { mutableStateOf(initialTab) }
    var selectedMode by rememberSaveable(uid, initialMode) { mutableStateOf(initialMode) }
    var ipQuery by rememberSaveable(uid) { mutableStateOf("") }
    var domainQuery by rememberSaveable(uid) { mutableStateOf("") }
    val appNames = remember { mutableStateMapOf<Int, String>() }
    val effectiveMode = if (uid == UID_EVERYBODY) selectedMode else RethinkRulesMode.SELECTED_UID
    val query = if (selectedTab == RethinkRulesTab.IP) ipQuery else domainQuery

    LaunchedEffect(uid, effectiveMode) {
        val rulesUid = if (effectiveMode == RethinkRulesMode.SELECTED_UID) uid else UID_EVERYBODY
        ipViewModel.setUid(rulesUid)
        domainViewModel.setUid(rulesUid)
    }
    LaunchedEffect(selectedTab, effectiveMode, query) {
        delay(250)
        if (selectedTab == RethinkRulesTab.IP) ipViewModel.setFilter(query) else domainViewModel.setFilter(query)
    }

    val feed: RethinkFirewallRulesFeed = when (selectedTab) {
        RethinkRulesTab.IP -> {
            val items = when (effectiveMode) {
                RethinkRulesMode.SELECTED_UID -> ipViewModel.customIpDetails.collectAsLazyPagingItems()
                RethinkRulesMode.ALL_APPS -> ipViewModel.allIpRules.collectAsLazyPagingItems()
            }
            AndroidRulesFeed(items) { it.toRethinkRule(appNames[it.uid]) }
        }
        RethinkRulesTab.DOMAIN -> {
            val items = when (effectiveMode) {
                RethinkRulesMode.SELECTED_UID -> domainViewModel.customDomains.collectAsLazyPagingItems()
                RethinkRulesMode.ALL_APPS -> domainViewModel.allDomainRules.collectAsLazyPagingItems()
            }
            AndroidRulesFeed(items) { it.toRethinkRule(appNames[it.uid]) }
        }
    }

    fun loadOwnerName(ruleUid: Int) {
        if (ruleUid == UID_EVERYBODY || appNames.containsKey(ruleUid)) return
        scope.launch {
            val name = withContext(Dispatchers.IO) { FirewallManager.getAppNameByUid(ruleUid).orEmpty() }
            appNames[ruleUid] = name
        }
    }

    RethinkCustomRulesScreen(
        uid = uid,
        selectedTab = selectedTab,
        selectedMode = effectiveMode,
        query = query,
        rules = feed,
        strings = androidCustomRulesStrings(),
        onTabChange = { selectedTab = it },
        onModeChange = { selectedMode = it },
        onQueryChange = { value ->
            if (selectedTab == RethinkRulesTab.IP) ipQuery = value else domainQuery = value
        },
        onAddRule = { tab, value ->
            when (tab) {
                RethinkRulesTab.IP -> {
                    val address = IPAddressString(value).address
                        ?: return@RethinkCustomRulesScreen RethinkRulesAddResult(
                            accepted = false,
                            error = invalidIpMessage,
                        )
                    scope.launch(Dispatchers.IO) {
                        IpRulesManager.addIpRule(uid, address, null, IpRulesManager.IpRuleStatus.BLOCK, "", "")
                        eventLogger.log(
                            EventType.FW_RULE_MODIFIED,
                            Severity.LOW,
                            "Added IP rule",
                            EventSource.UI,
                            false,
                            "IP: $value",
                        )
                    }
                    RethinkRulesAddResult.Success
                }
                RethinkRulesTab.DOMAIN -> {
                    scope.launch(Dispatchers.IO) {
                        DomainRulesManager.addDomainRule(
                            value,
                            DomainRulesManager.Status.BLOCK,
                            DomainRulesManager.DomainType.DOMAIN,
                            uid = uid,
                        )
                        eventLogger.log(
                            EventType.FW_RULE_MODIFIED,
                            Severity.LOW,
                            "Added domain rule",
                            EventSource.UI,
                            false,
                            "Domain: $value",
                        )
                    }
                    RethinkRulesAddResult.Success
                }
            }
        },
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
    )
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
private fun androidCustomRulesStrings(port: (Int) -> String) = RethinkCustomRulesStrings(
    port = port,
    universalTitle = stringResource(R.string.univ_view_blocked_ip),
    appWiseTitle = stringResource(R.string.lbl_app_wise),
    appRulesTitle = stringResource(R.string.app_ip_domain_rules),
    ipRules = stringResource(R.string.lbl_ip_rules),
    domainRules = stringResource(R.string.lbl_domain_rules),
    ip = stringResource(R.string.lbl_ip),
    domain = stringResource(R.string.lbl_domain),
    universal = stringResource(R.string.firewall_act_universal_tab),
    appWise = stringResource(R.string.lbl_app_wise),
    search = stringResource(R.string.lbl_search),
    clearSearch = stringResource(R.string.cd_clear_search),
    loading = stringResource(R.string.lbl_loading),
    noIpRules = stringResource(R.string.ci_no_rules_text),
    noDomainRules = stringResource(R.string.cd_no_rules_text),
    add = stringResource(R.string.lbl_add),
    cancel = stringResource(R.string.lbl_cancel),
    delete = stringResource(R.string.lbl_delete),
    block = stringResource(R.string.ci_block),
    trust = stringResource(R.string.ci_trust_rule),
    bypass = stringResource(R.string.firewall_status_whitelisted),
    noRule = stringResource(R.string.ci_no_rule),
    uid = { value -> "UID $value" },
    deleteRuleTitle = stringResource(R.string.custom_rule_delete_title),
    deleteSelectedTitle = { count -> pluralStringResource(R.plurals.custom_rules_delete_selected_title, count, count) },
    selectedCount = { count -> pluralStringResource(R.plurals.custom_rules_selected_count, count, count) },
    clearSelection = stringResource(R.string.custom_rules_clear_selection),
    select = stringResource(R.string.custom_rules_select),
    deleteRule = stringResource(R.string.custom_rules_delete_rule),
    addIpRuleTitle = stringResource(R.string.custom_rules_add_ip_title),
    addDomainRuleTitle = stringResource(R.string.custom_rules_add_domain_title),
    ipHint = stringResource(R.string.custom_rules_ip_hint),
    domainHint = stringResource(R.string.custom_rules_domain_hint),
)
