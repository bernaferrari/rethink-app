/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.ui.compose.firewall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
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
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkSegmentedChoiceRow
import com.celzero.bravedns.ui.compose.theme.cardPositionFor
import com.celzero.bravedns.util.Constants.Companion.UNSPECIFIED_PORT
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.celzero.bravedns.viewmodel.CustomDomainViewModel
import com.celzero.bravedns.viewmodel.CustomIpViewModel
import inet.ipaddr.IPAddressString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

enum class RulesTab(val value: Int) {
    IP(0),
    DOMAIN(1);

    companion object {
        fun fromValue(value: Int): RulesTab {
            return entries.firstOrNull { it.value == value } ?: IP
        }
    }
}

enum class RulesMode(val value: Int) {
    ALL_RULES(0),
    APP_SPECIFIC(1);

    companion object {
        fun fromValue(value: Int): RulesMode {
            return entries.firstOrNull { it.value == value } ?: APP_SPECIFIC
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRulesScreen(
    uid: Int = UID_EVERYBODY,
    initialTab: RulesTab = RulesTab.IP,
    initialMode: RulesMode = RulesMode.APP_SPECIFIC,
    domainViewModel: CustomDomainViewModel,
    ipViewModel: CustomIpViewModel,
    eventLogger: EventLogger,
    onBackClick: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable(uid, initialTab) { mutableStateOf(initialTab) }
    var selectedMode by rememberSaveable(uid, initialMode) { mutableStateOf(initialMode) }
    var showAddDialog by remember { mutableStateOf(false) }
    var ipQuery by rememberSaveable { mutableStateOf("") }
    var domainQuery by rememberSaveable { mutableStateOf("") }
    val canSwitchScope = uid == UID_EVERYBODY
    val effectiveMode = if (canSwitchScope) selectedMode else RulesMode.APP_SPECIFIC
    val isUniversalRules = uid == UID_EVERYBODY && effectiveMode == RulesMode.APP_SPECIFIC
    val showAddButton = effectiveMode == RulesMode.APP_SPECIFIC

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(effectiveMode) {
        if (effectiveMode != RulesMode.APP_SPECIFIC) {
            showAddDialog = false
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title =
                    if (isUniversalRules) {
                        stringResource(R.string.univ_view_blocked_ip)
                    } else if (effectiveMode == RulesMode.ALL_RULES) {
                        stringResource(R.string.lbl_app_wise)
                    } else {
                        stringResource(R.string.app_ip_domain_rules)
                    },
                subtitle =
                    if (isUniversalRules) {
                        stringResource(R.string.custom_rules_desc)
                    } else if (effectiveMode == RulesMode.ALL_RULES) {
                        stringResource(R.string.app_ip_domain_rules_desc)
                    } else {
                        stringResource(R.string.app_ip_domain_rules_desc)
                    },
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (showAddButton) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.lbl_add))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (selectedTab) {
            RulesTab.IP ->
                IpRulesContent(
                    modifier = Modifier.padding(padding),
                    uid = uid,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    rulesMode = effectiveMode,
                    canSwitchScope = canSwitchScope,
                    onRulesModeChange = { selectedMode = it },
                    query = ipQuery,
                    onQueryChange = { ipQuery = it },
                    viewModel = ipViewModel,
                    eventLogger = eventLogger
                )

            RulesTab.DOMAIN ->
                DomainRulesContent(
                    modifier = Modifier.padding(padding),
                    uid = uid,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    rulesMode = effectiveMode,
                    canSwitchScope = canSwitchScope,
                    onRulesModeChange = { selectedMode = it },
                    query = domainQuery,
                    onQueryChange = { domainQuery = it },
                    viewModel = domainViewModel,
                    eventLogger = eventLogger
                )
        }
    }

    if (showAddDialog && showAddButton) {
        AddRuleDialog(
            isIpRule = selectedTab == RulesTab.IP,
            onDismiss = { showAddDialog = false },
            onAddIpRule = { ip ->
                scope.launch(Dispatchers.IO) {
                    val ipAddress = IPAddressString(ip).address ?: return@launch
                    IpRulesManager.addIpRule(uid, ipAddress, null, IpRulesManager.IpRuleStatus.BLOCK, "", "")
                }
                eventLogger.log(
                    EventType.FW_RULE_MODIFIED,
                    Severity.LOW,
                    "Added IP rule",
                    EventSource.UI,
                    false,
                    "IP: $ip"
                )
                showAddDialog = false
            },
            onAddDomainRule = { domain ->
                scope.launch(Dispatchers.IO) {
                    DomainRulesManager.addDomainRule(
                        domain,
                        DomainRulesManager.Status.BLOCK,
                        DomainRulesManager.DomainType.DOMAIN,
                        uid = uid
                    )
                }
                eventLogger.log(
                    EventType.FW_RULE_MODIFIED,
                    Severity.LOW,
                    "Added domain rule",
                    EventSource.UI,
                    false,
                    "Domain: $domain"
                )
                showAddDialog = false
            }
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun IpRulesContent(
    modifier: Modifier = Modifier,
    uid: Int,
    selectedTab: RulesTab,
    onTabSelected: (RulesTab) -> Unit,
    rulesMode: RulesMode,
    canSwitchScope: Boolean,
    onRulesModeChange: (RulesMode) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    viewModel: CustomIpViewModel,
    eventLogger: EventLogger
) {
    val items =
        when (rulesMode) {
            RulesMode.APP_SPECIFIC -> viewModel.customIpDetails.asFlow().collectAsLazyPagingItems()
            RulesMode.ALL_RULES -> viewModel.allIpRules.asFlow().collectAsLazyPagingItems()
        }

    RulesContent(
        modifier = modifier,
        uid = uid,
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        rulesMode = rulesMode,
        canSwitchScope = canSwitchScope,
        onRulesModeChange = onRulesModeChange,
        query = query,
        onQueryChange = onQueryChange,
        hint = stringResource(R.string.lbl_ip_rules),
        emptyText = stringResource(R.string.rules_load_failure_desc),
        items = items,
        setUid = { modeUid -> viewModel.setUid(modeUid) },
        setFilter = { filter -> viewModel.setFilter(filter) },
        groupBy = { it.uid },
        onDeleteRule = { item -> IpRulesManager.removeIpRule(item.uid, item.ipAddress, item.port) },
        deleteEventMessage = "Removed IP rule",
        deleteEventDetails = { item -> "IP: ${item.ipAddress}" },
        eventLogger = eventLogger
    ) { item, position, onDelete ->
        IpRuleListItem(
            rule = item,
            position = position,
            onDelete = onDelete
        )
    }
}

@Composable
private fun IpRuleListItem(
    rule: CustomIp,
    position: CardPosition,
    onDelete: () -> Unit
) {
    val status = IpRulesManager.IpRuleStatus.getStatus(rule.status)
    val statusLabelRes =
        when (status) {
            IpRulesManager.IpRuleStatus.BLOCK -> R.string.ci_block
            IpRulesManager.IpRuleStatus.TRUST -> R.string.ci_trust_rule
            IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL -> R.string.firewall_status_whitelisted
            IpRulesManager.IpRuleStatus.NONE -> R.string.ci_no_rule
        }
    val statusColor =
        when (status) {
            IpRulesManager.IpRuleStatus.BLOCK -> MaterialTheme.colorScheme.error
            IpRulesManager.IpRuleStatus.TRUST -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val headline = if (rule.port == UNSPECIFIED_PORT) rule.ipAddress else "${rule.ipAddress}:${rule.port}"

    RuleListItem(
        headline = headline,
        supporting = stringResource(statusLabelRes),
        iconRes = R.drawable.ic_ip_address,
        accent = statusColor,
        position = position,
        onDelete = onDelete
    )
}

@OptIn(FlowPreview::class)
@Composable
private fun DomainRulesContent(
    modifier: Modifier = Modifier,
    uid: Int,
    selectedTab: RulesTab,
    onTabSelected: (RulesTab) -> Unit,
    rulesMode: RulesMode,
    canSwitchScope: Boolean,
    onRulesModeChange: (RulesMode) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    viewModel: CustomDomainViewModel,
    eventLogger: EventLogger
) {
    val items =
        when (rulesMode) {
            RulesMode.APP_SPECIFIC -> viewModel.customDomains.asFlow().collectAsLazyPagingItems()
            RulesMode.ALL_RULES -> viewModel.allDomainRules.asFlow().collectAsLazyPagingItems()
        }

    RulesContent(
        modifier = modifier,
        uid = uid,
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        rulesMode = rulesMode,
        canSwitchScope = canSwitchScope,
        onRulesModeChange = onRulesModeChange,
        query = query,
        onQueryChange = onQueryChange,
        hint = stringResource(R.string.lbl_domain_rules),
        emptyText = stringResource(R.string.cd_no_rules_text),
        items = items,
        setUid = { modeUid -> viewModel.setUid(modeUid) },
        setFilter = { filter -> viewModel.setFilter(filter) },
        groupBy = { it.uid },
        onDeleteRule = { item -> DomainRulesManager.deleteDomain(item) },
        deleteEventMessage = "Removed domain rule",
        deleteEventDetails = { item -> "Domain: ${item.domain}" },
        eventLogger = eventLogger
    ) { item, position, onDelete ->
        DomainRuleListItem(
            rule = item,
            position = position,
            onDelete = onDelete
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun <T : Any> RulesContent(
    modifier: Modifier,
    uid: Int,
    selectedTab: RulesTab,
    onTabSelected: (RulesTab) -> Unit,
    rulesMode: RulesMode,
    canSwitchScope: Boolean,
    onRulesModeChange: (RulesMode) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    emptyText: String,
    items: LazyPagingItems<T>,
    setUid: (Int) -> Unit,
    setFilter: (String) -> Unit,
    groupBy: (T) -> Int,
    onDeleteRule: suspend (T) -> Unit,
    deleteEventMessage: String,
    deleteEventDetails: (T) -> String,
    eventLogger: EventLogger,
    row: @Composable (item: T, position: CardPosition, onDelete: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(uid, rulesMode) {
        setUid(if (rulesMode == RulesMode.APP_SPECIFIC) uid else UID_EVERYBODY)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(250)
            .distinctUntilChanged()
            .collect { q -> setFilter(q) }
    }

    val isRefreshing = items.loadState.refresh is LoadState.Loading
    val isEmpty = !isRefreshing && items.itemCount == 0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = Dimensions.screenPaddingHorizontal,
                end = Dimensions.screenPaddingHorizontal,
                top = Dimensions.spacingSm,
                bottom = if (rulesMode == RulesMode.APP_SPECIFIC) 112.dp else Dimensions.spacing3xl
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            RuleTypeSelector(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected
            )
        }

        if (canSwitchScope) {
            item { Spacer(modifier = Modifier.height(Dimensions.spacingSm)) }
            item {
                RuleScopeSelector(
                    rulesMode = rulesMode,
                    onRulesModeChange = onRulesModeChange
                )
            }
        }

        item { Spacer(modifier = Modifier.height(Dimensions.spacingSm)) }

        item {
            RulesSearchField(
                query = query,
                onQueryChange = onQueryChange,
                hint = hint
            )
        }

        item { Spacer(modifier = Modifier.height(Dimensions.spacingMd)) }

        if (isRefreshing) {
            item {
                RulesInfoRow(text = stringResource(R.string.lbl_loading))
            }
        } else if (isEmpty) {
            item {
                RulesInfoRow(text = emptyText)
            }
        } else {
            items(items.itemCount) { index ->
                val item = items[index] ?: return@items
                val showHeader =
                    rulesMode == RulesMode.ALL_RULES &&
                        shouldShowGroupHeader(items, index, groupBy)
                if (showHeader) {
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(Dimensions.spacingSm))
                    }
                    RulesAppHeader(uid = groupBy(item))
                    Spacer(modifier = Modifier.height(4.dp))
                }
                val position =
                    if (rulesMode == RulesMode.ALL_RULES) {
                        groupedCardPosition(items, index, item, groupBy)
                    } else {
                        cardPositionFor(index, items.itemCount - 1)
                    }

                row(
                    item,
                    position,
                    {
                        scope.launch(Dispatchers.IO) {
                            onDeleteRule(item)
                        }
                        eventLogger.log(
                            EventType.FW_RULE_MODIFIED,
                            Severity.LOW,
                            deleteEventMessage,
                            EventSource.UI,
                            false,
                            deleteEventDetails(item)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DomainRuleListItem(
    rule: CustomDomain,
    position: CardPosition,
    onDelete: () -> Unit
) {
    val status = DomainRulesManager.Status.getStatus(rule.status)
    val statusLabelRes =
        when (status) {
            DomainRulesManager.Status.BLOCK -> R.string.ci_block
            DomainRulesManager.Status.TRUST -> R.string.ci_trust_rule
            DomainRulesManager.Status.NONE -> R.string.ci_no_rule
        }
    val statusColor =
        when (status) {
            DomainRulesManager.Status.BLOCK -> MaterialTheme.colorScheme.error
            DomainRulesManager.Status.TRUST -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    RuleListItem(
        headline = rule.domain,
        supporting = stringResource(statusLabelRes),
        iconRes = R.drawable.ic_undelegated_domain,
        accent = statusColor,
        position = position,
        onDelete = onDelete
    )
}

@Composable
private fun RuleListItem(
    headline: String,
    supporting: String,
    iconRes: Int,
    accent: Color,
    position: CardPosition,
    onDelete: () -> Unit
) {
    RethinkListItem(
        headline = headline,
        supporting = supporting,
        leadingIconPainter = painterResource(id = iconRes),
        leadingIconTint = accent,
        leadingIconContainerColor = accent.copy(alpha = 0.14f),
        position = position,
        trailing = {
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.lbl_delete))
            }
        }
    )
}

@Composable
private fun RulesAppHeader(uid: Int) {
    val label by
        produceState(initialValue = "UID $uid", key1 = uid) {
            value =
                withContext(Dispatchers.IO) {
                    val appName = FirewallManager.getAppNameByUid(uid).orEmpty().trim()
                    if (appName.isEmpty()) {
                        "UID $uid"
                    } else {
                        appName
                    }
                }
        }

    val supporting = if (label == "UID $uid") null else "UID $uid"
    RethinkListItem(
        headline = label,
        supporting = supporting,
        leadingIcon = Icons.Rounded.Apps,
        position = CardPosition.Single,
        defaultContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

private fun <T : Any> shouldShowGroupHeader(
    items: LazyPagingItems<T>,
    index: Int,
    groupBy: (T) -> Int
): Boolean {
    if (index == 0) return true
    val current = items[index] ?: return true
    val prev = items[index - 1] ?: return true
    return groupBy(prev) != groupBy(current)
}

private fun <T : Any> groupedCardPosition(
    items: LazyPagingItems<T>,
    index: Int,
    item: T,
    groupBy: (T) -> Int
): CardPosition {
    val itemGroup = groupBy(item)
    val hasPrevSame = index > 0 && items[index - 1]?.let(groupBy) == itemGroup
    val hasNextSame = index < items.itemCount - 1 && items[index + 1]?.let(groupBy) == itemGroup
    return when {
        !hasPrevSame && !hasNextSame -> CardPosition.Single
        !hasPrevSame -> CardPosition.First
        !hasNextSame -> CardPosition.Last
        else -> CardPosition.Middle
    }
}

@Composable
private fun RuleTypeSelector(
    selectedTab: RulesTab,
    onTabSelected: (RulesTab) -> Unit
) {
    val options = listOf(RulesTab.IP to R.string.lbl_ip_rules, RulesTab.DOMAIN to R.string.lbl_domain_rules)

    RethinkSegmentedChoiceRow(
        options = options,
        selectedOption = options.first { it.first == selectedTab },
        onOptionSelected = { (tab, _) -> onTabSelected(tab) },
        modifier = Modifier.fillMaxWidth(),
        fillEqually = true,
        icon = { option, selected ->
            SegmentedButtonDefaults.Icon(active = selected) {
                Icon(
                    imageVector =
                        if (selected) {
                            Icons.Rounded.RadioButtonChecked
                        } else {
                            Icons.Rounded.RadioButtonUnchecked
                        },
                    contentDescription = null
                )
            }
        },
        label = { option, _ ->
            Text(text = stringResource(option.second), maxLines = 1)
        }
    )
}

@Composable
private fun RuleScopeSelector(
    rulesMode: RulesMode,
    onRulesModeChange: (RulesMode) -> Unit
) {
    val universalLabel =
        stringResource(R.string.firewall_act_universal_tab).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
    val options =
        listOf(
            RulesMode.APP_SPECIFIC to universalLabel,
            RulesMode.ALL_RULES to stringResource(R.string.lbl_app_wise)
        )

    RethinkSegmentedChoiceRow(
        options = options,
        selectedOption = options.first { it.first == rulesMode },
        onOptionSelected = { (mode, _) -> onRulesModeChange(mode) },
        modifier = Modifier.fillMaxWidth(),
        fillEqually = true,
        label = { option, _ ->
            Text(text = option.second, maxLines = 1)
        }
    )
}

@Composable
private fun RulesSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.lbl_search)
            )
        },
        placeholder = { Text(text = stringResource(R.string.two_argument_colon, stringResource(R.string.lbl_search), hint)) },
        singleLine = true
    )
}

@Composable
private fun RulesInfoRow(text: String) {
    RethinkListGroup {
        RethinkListItem(
            headline = text,
            position = CardPosition.Single,
            enabled = false
        )
    }
}

@Composable
private fun AddRuleDialog(
    isIpRule: Boolean,
    onDismiss: () -> Unit,
    onAddIpRule: (String) -> Unit,
    onAddDomainRule: (String) -> Unit
) {
    var ruleValue by remember { mutableStateOf("") }
    val title =
        if (isIpRule) {
            stringResource(R.string.lbl_ip_rules)
        } else {
            stringResource(R.string.lbl_domain_rules)
        }

    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = title,
        text = {
            OutlinedTextField(
                value = ruleValue,
                onValueChange = { ruleValue = it },
                label = { Text(text = title) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmText = stringResource(R.string.lbl_add),
        dismissText = stringResource(R.string.lbl_cancel),
        confirmEnabled = ruleValue.isNotBlank(),
        onConfirm = {
            if (ruleValue.isNotBlank()) {
                if (isIpRule) onAddIpRule(ruleValue.trim())
                else onAddDomainRule(ruleValue.trim())
            }
        },
        onDismiss = onDismiss
    )
}
