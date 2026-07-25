/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.firewall

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
import com.celzero.bravedns.R
import com.celzero.bravedns.database.CustomDomain
import com.celzero.bravedns.database.CustomIp
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.util.Constants.Companion.UNSPECIFIED_PORT
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.celzero.bravedns.viewmodel.CustomDomainViewModel
import com.celzero.bravedns.viewmodel.CustomIpViewModel
import inet.ipaddr.IPAddressString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Compatibility names for Android navigation while the UI itself lives in commonMain. */
typealias RulesTab = RethinkRulesTab
typealias RulesMode = RethinkRulesMode

/** Android data/service adapter for the common custom-rules renderer. */
@Composable
fun CustomRulesScreen(
    uid: Int = UID_EVERYBODY,
    initialTab: RulesTab = RulesTab.IP,
    initialMode: RulesMode = RulesMode.APP_SPECIFIC,
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
    val effectiveMode = if (uid == UID_EVERYBODY) selectedMode else RulesMode.APP_SPECIFIC
    val query = if (selectedTab == RulesTab.IP) ipQuery else domainQuery

    LaunchedEffect(uid, effectiveMode) {
        val rulesUid = if (effectiveMode == RulesMode.APP_SPECIFIC) uid else UID_EVERYBODY
        ipViewModel.setUid(rulesUid)
        domainViewModel.setUid(rulesUid)
    }
    LaunchedEffect(selectedTab, effectiveMode, query) {
        delay(250)
        if (selectedTab == RulesTab.IP) ipViewModel.setFilter(query) else domainViewModel.setFilter(query)
    }

    val feed: RethinkFirewallRulesFeed = when (selectedTab) {
        RulesTab.IP -> {
            val items = when (effectiveMode) {
                RulesMode.APP_SPECIFIC -> ipViewModel.customIpDetails.collectAsLazyPagingItems()
                RulesMode.ALL_RULES -> ipViewModel.allIpRules.collectAsLazyPagingItems()
            }
            AndroidRulesFeed(items) { it.toRethinkRule(appNames[it.uid]) }
        }
        RulesTab.DOMAIN -> {
            val items = when (effectiveMode) {
                RulesMode.APP_SPECIFIC -> domainViewModel.customDomains.collectAsLazyPagingItems()
                RulesMode.ALL_RULES -> domainViewModel.allDomainRules.collectAsLazyPagingItems()
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
            if (selectedTab == RulesTab.IP) ipQuery = value else domainQuery = value
        },
        onAddRule = { tab, value ->
            when (tab) {
                RulesTab.IP -> {
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
                RulesTab.DOMAIN -> {
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
                    RulesTab.IP -> IpRulesManager.removeIpRule(rule.uid, rule.value, rule.port ?: UNSPECIFIED_PORT)
                    RulesTab.DOMAIN -> DomainRulesManager.deleteDomain(
                        CustomDomain().apply {
                            domain = rule.value
                            this.uid = rule.uid
                        },
                    )
                }
                eventLogger.log(
                    EventType.FW_RULE_MODIFIED,
                    Severity.LOW,
                    if (tab == RulesTab.IP) "Removed IP rule" else "Removed domain rule",
                    EventSource.UI,
                    false,
                    if (tab == RulesTab.IP) "IP: ${rule.value}" else "Domain: ${rule.value}",
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
private fun androidCustomRulesStrings() = RethinkCustomRulesStrings(
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
    noIpRules = stringResource(R.string.rules_load_failure_desc),
    noDomainRules = stringResource(R.string.cd_no_rules_text),
    add = stringResource(R.string.lbl_add),
    cancel = stringResource(R.string.lbl_cancel),
    delete = stringResource(R.string.lbl_delete),
    block = stringResource(R.string.ci_block),
    trust = stringResource(R.string.ci_trust_rule),
    bypass = stringResource(R.string.firewall_status_whitelisted),
    noRule = stringResource(R.string.ci_no_rule),
    uid = { value -> "UID $value" },
)
