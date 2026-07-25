/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.celzero.bravedns.ui.compose.firewall

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkSearchField
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import com.celzero.bravedns.ui.compose.theme.cardPositionFor

/** The portable custom-rules screen supports both universal and app-specific rule scopes. */
enum class RethinkRulesTab(val value: Int) {
    IP(0),
    DOMAIN(1);

    companion object {
        fun fromValue(value: Int): RethinkRulesTab = entries.firstOrNull { it.value == value } ?: IP
    }
}

enum class RethinkRulesMode(val value: Int) {
    ALL_RULES(0),
    APP_SPECIFIC(1);

    companion object {
        fun fromValue(value: Int): RethinkRulesMode = entries.firstOrNull { it.value == value } ?: APP_SPECIFIC
    }
}

enum class RethinkFirewallRuleStatus { Block, Trust, Bypass, None }

data class RethinkFirewallRule(
    val id: String,
    val uid: Int,
    val value: String,
    val status: RethinkFirewallRuleStatus,
    val tab: RethinkRulesTab = RethinkRulesTab.IP,
    val port: Int? = null,
    val ownerName: String? = null,
)

/**
 * Target adapters can preserve paging by exposing items lazily. The common renderer deliberately
 * has no dependency on Android Paging, Room, or a platform database cursor.
 */
interface RethinkFirewallRulesFeed {
    val itemCount: Int
    val isLoading: Boolean

    operator fun get(index: Int): RethinkFirewallRule?
}

data class RethinkInMemoryFirewallRulesFeed(
    val rules: List<RethinkFirewallRule>,
    override val isLoading: Boolean = false,
) : RethinkFirewallRulesFeed {
    override val itemCount: Int get() = rules.size

    override fun get(index: Int): RethinkFirewallRule? = rules.getOrNull(index)
}

data class RethinkRulesAddResult(
    val accepted: Boolean,
    val error: String? = null,
) {
    companion object {
        val Success = RethinkRulesAddResult(accepted = true)
    }
}

data class RethinkCustomRulesStrings(
    val universalTitle: String,
    val appWiseTitle: String,
    val appRulesTitle: String,
    val ipRules: String,
    val domainRules: String,
    val ip: String,
    val domain: String,
    val universal: String,
    val appWise: String,
    val search: String,
    val clearSearch: String,
    val loading: String,
    val noIpRules: String,
    val noDomainRules: String,
    val add: String,
    val cancel: String,
    val delete: String,
    val block: String,
    val trust: String,
    val bypass: String,
    val noRule: String,
    val uid: (Int) -> String,
)

/**
 * Complete, platform-neutral custom firewall-rules UI. Its host owns persistence and validation
 * policy; paging, list composition, grouping, inputs, dialogs, and selection controls are shared.
 */
@Composable
fun RethinkCustomRulesScreen(
    uid: Int,
    selectedTab: RethinkRulesTab,
    selectedMode: RethinkRulesMode,
    query: String,
    rules: RethinkFirewallRulesFeed,
    strings: RethinkCustomRulesStrings,
    onTabChange: (RethinkRulesTab) -> Unit,
    onModeChange: (RethinkRulesMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onAddRule: (RethinkRulesTab, String) -> RethinkRulesAddResult,
    onDeleteRule: (RethinkRulesTab, RethinkFirewallRule) -> Unit,
    onRuleOwnerVisible: (Int) -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val canSwitchScope = uid == RETHINK_UID_EVERYBODY
    val effectiveMode = if (canSwitchScope) selectedMode else RethinkRulesMode.APP_SPECIFIC
    val isUniversalRules = uid == RETHINK_UID_EVERYBODY && effectiveMode == RethinkRulesMode.APP_SPECIFIC
    var showAddDialog by rememberSaveable(uid) { mutableStateOf(false) }
    val title = when {
        isUniversalRules -> strings.universalTitle
        effectiveMode == RethinkRulesMode.ALL_RULES -> strings.appWiseTitle
        else -> strings.appRulesTitle
    }

    LaunchedEffect(effectiveMode) {
        if (effectiveMode == RethinkRulesMode.ALL_RULES) showAddDialog = false
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { RethinkTopBar(title = title, onBackClick = onBackClick) },
        floatingActionButton = {
            if (effectiveMode == RethinkRulesMode.APP_SPECIFIC) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(MaterialSymbols.Filled.Add, null) },
                    text = { Text(strings.add) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RethinkRulesControlDeck(
                selectedTab = selectedTab,
                selectedMode = effectiveMode,
                canSwitchScope = canSwitchScope,
                query = query,
                strings = strings,
                onTabChange = onTabChange,
                onModeChange = onModeChange,
                onQueryChange = onQueryChange,
            )
            RethinkRulesContent(
                modifier = Modifier.weight(1f),
                rules = rules,
                selectedTab = selectedTab,
                selectedMode = effectiveMode,
                strings = strings,
                onDeleteRule = { onDeleteRule(selectedTab, it) },
                onRuleOwnerVisible = onRuleOwnerVisible,
            )
        }
    }

    if (showAddDialog && effectiveMode == RethinkRulesMode.APP_SPECIFIC) {
        RethinkAddFirewallRuleDialog(
            tab = selectedTab,
            strings = strings,
            onDismiss = { showAddDialog = false },
            onAdd = { value ->
                onAddRule(selectedTab, value).also { result ->
                    if (result.accepted) showAddDialog = false
                }
            },
        )
    }
}

@Composable
private fun RethinkRulesControlDeck(
    selectedTab: RethinkRulesTab,
    selectedMode: RethinkRulesMode,
    canSwitchScope: Boolean,
    query: String,
    strings: RethinkCustomRulesStrings,
    onTabChange: (RethinkRulesTab) -> Unit,
    onModeChange: (RethinkRulesMode) -> Unit,
    onQueryChange: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingXs,
        ),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
    ) {
        if (canSwitchScope) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                RethinkRuleTabSelector(
                    selected = selectedTab,
                    strings = strings,
                    compact = true,
                    modifier = Modifier.weight(1f),
                    onSelected = onTabChange,
                )
                RethinkRuleScopeSelector(
                    selected = selectedMode,
                    strings = strings,
                    modifier = Modifier.weight(1f),
                    onSelected = onModeChange,
                )
            }
        } else {
            RethinkRuleTabSelector(selectedTab, strings, compact = false, onSelected = onTabChange)
        }
        RethinkSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = "${strings.search}: ${if (selectedTab == RethinkRulesTab.IP) strings.ipRules else strings.domainRules}",
            clearQueryContentDescription = strings.clearSearch,
            onClearQuery = { onQueryChange("") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RethinkRuleTabSelector(
    selected: RethinkRulesTab,
    strings: RethinkCustomRulesStrings,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onSelected: (RethinkRulesTab) -> Unit,
) {
    RethinkRulesConnectedChoiceButtonRow(
        options = listOf(RethinkRulesTab.IP, RethinkRulesTab.DOMAIN),
        selectedOption = selected,
        onOptionSelected = onSelected,
        modifier = modifier.fillMaxWidth(),
        buttonMinHeight = if (compact) 40.dp else 0.dp,
        label = { option, isSelected ->
            Text(
                text = when (option) {
                    RethinkRulesTab.IP -> if (compact) strings.ip else strings.ipRules
                    RethinkRulesTab.DOMAIN -> if (compact) strings.domain else strings.domainRules
                },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            )
        },
    )
}

@Composable
private fun RethinkRuleScopeSelector(
    selected: RethinkRulesMode,
    strings: RethinkCustomRulesStrings,
    modifier: Modifier = Modifier,
    onSelected: (RethinkRulesMode) -> Unit,
) {
    RethinkRulesConnectedChoiceButtonRow(
        options = listOf(RethinkRulesMode.APP_SPECIFIC, RethinkRulesMode.ALL_RULES),
        selectedOption = selected,
        onOptionSelected = onSelected,
        modifier = modifier.fillMaxWidth(),
        buttonMinHeight = 40.dp,
        label = { option, isSelected ->
            Text(
                text = if (option == RethinkRulesMode.APP_SPECIFIC) strings.universal else strings.appWise,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            )
        },
    )
}

@Composable
private fun RethinkRulesContent(
    rules: RethinkFirewallRulesFeed,
    selectedTab: RethinkRulesTab,
    selectedMode: RethinkRulesMode,
    strings: RethinkCustomRulesStrings,
    onDeleteRule: (RethinkFirewallRule) -> Unit,
    onRuleOwnerVisible: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        rules.isLoading && rules.itemCount == 0 -> RethinkRulesInfo(strings.loading, modifier)
        !rules.isLoading && rules.itemCount == 0 -> RethinkRulesEmptyState(selectedTab, strings, modifier)
        else -> androidx.compose.foundation.lazy.LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingSm,
                bottom = if (selectedMode == RethinkRulesMode.APP_SPECIFIC) 112.dp else SharedDimensions.spacing3xl,
            ),
        ) {
            items(rules.itemCount, key = { index -> rules[index]?.id ?: "rule-$index" }) { index ->
                val rule = rules[index] ?: return@items
                val previous = rules.getOrNull(index - 1)
                val next = rules.getOrNull(index + 1)
                val isGrouped = selectedMode == RethinkRulesMode.ALL_RULES
                if (isGrouped && (previous == null || previous.uid != rule.uid)) {
                    if (index > 0) Spacer(Modifier.height(SharedDimensions.spacingSm))
                    RethinkRuleOwnerHeader(rule, strings, onRuleOwnerVisible)
                    Spacer(Modifier.height(4.dp))
                }
                val position = if (!isGrouped) {
                    cardPositionFor(index, rules.itemCount - 1)
                } else {
                    when {
                        previous?.uid != rule.uid && next?.uid != rule.uid -> CardPosition.Single
                        previous?.uid != rule.uid -> CardPosition.First
                        next?.uid != rule.uid -> CardPosition.Last
                        else -> CardPosition.Middle
                    }
                }
                RethinkFirewallRuleRow(rule, position, strings, onDelete = { onDeleteRule(rule) })
            }
        }
    }
}

private fun RethinkFirewallRulesFeed.getOrNull(index: Int): RethinkFirewallRule? =
    if (index in 0 until itemCount) get(index) else null

@Composable
private fun RethinkRulesInfo(text: String, modifier: Modifier) {
    RethinkListGroup(modifier.padding(
        start = SharedDimensions.screenPaddingHorizontal,
        end = SharedDimensions.screenPaddingHorizontal,
        top = SharedDimensions.spacingSm,
    )) {
        RethinkListItem(headline = text, enabled = false, position = CardPosition.Single)
    }
}

@Composable
private fun RethinkRulesEmptyState(
    tab: RethinkRulesTab,
    strings: RethinkCustomRulesStrings,
    modifier: Modifier,
) {
    val isIp = tab == RethinkRulesTab.IP
    val accent = if (isIp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Box(
        modifier.fillMaxSize().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadius4xl),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = accent.copy(alpha = .14f),
                ) {
                    Icon(
                        if (isIp) MaterialSymbols.Filled.Public else MaterialSymbols.Filled.Language,
                        null,
                        Modifier.padding(10.dp),
                        tint = accent,
                    )
                }
                Text(
                    if (isIp) strings.noIpRules else strings.noDomainRules,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RethinkRuleOwnerHeader(
    rule: RethinkFirewallRule,
    strings: RethinkCustomRulesStrings,
    onRuleOwnerVisible: (Int) -> Unit,
) {
    LaunchedEffect(rule.uid) { onRuleOwnerVisible(rule.uid) }
    val uidLabel = strings.uid(rule.uid)
    val owner = rule.ownerName?.takeIf { it.isNotBlank() } ?: uidLabel
    RethinkListItem(
        headline = owner,
        supporting = uidLabel.takeIf { it != owner },
        leadingIcon = MaterialSymbols.Filled.Apps,
        position = CardPosition.Single,
        defaultContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun RethinkFirewallRuleRow(
    rule: RethinkFirewallRule,
    position: CardPosition,
    strings: RethinkCustomRulesStrings,
    onDelete: () -> Unit,
) {
    val statusLabel = when (rule.status) {
        RethinkFirewallRuleStatus.Block -> strings.block
        RethinkFirewallRuleStatus.Trust -> strings.trust
        RethinkFirewallRuleStatus.Bypass -> strings.bypass
        RethinkFirewallRuleStatus.None -> strings.noRule
    }
    val accent: Color = when (rule.status) {
        RethinkFirewallRuleStatus.Block -> MaterialTheme.colorScheme.error
        RethinkFirewallRuleStatus.Trust -> MaterialTheme.colorScheme.primary
        RethinkFirewallRuleStatus.Bypass, RethinkFirewallRuleStatus.None -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val headline = rule.port?.let { "${rule.value}:$it" } ?: rule.value
    RethinkListItem(
        headline = headline,
        leadingIcon = if (rule.tab == RethinkRulesTab.IP) MaterialSymbols.Filled.Public else MaterialSymbols.Filled.Language,
        leadingIconTint = accent,
        leadingIconContainerColor = accent.copy(alpha = .14f),
        position = position,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadiusPill),
                    color = accent.copy(alpha = .14f),
                ) {
                    Text(
                        statusLabel,
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                }
                IconButton(onClick = onDelete) { Icon(MaterialSymbols.Filled.Delete, strings.delete) }
            }
        },
    )
}

@Composable
private fun RethinkAddFirewallRuleDialog(
    tab: RethinkRulesTab,
    strings: RethinkCustomRulesStrings,
    onDismiss: () -> Unit,
    onAdd: (String) -> RethinkRulesAddResult,
) {
    var value by remember(tab) { mutableStateOf("") }
    var error by remember(tab) { mutableStateOf<String?>(null) }
    val title = if (tab == RethinkRulesTab.IP) strings.ipRules else strings.domainRules
    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = strings.add,
        dismissText = strings.cancel,
        confirmEnabled = value.isNotBlank(),
        onConfirm = { error = onAdd(value.trim()).error },
        onDismiss = onDismiss,
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; error = null },
                label = { Text(title) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun <T> RethinkRulesConnectedChoiceButtonRow(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    buttonMinHeight: androidx.compose.ui.unit.Dp = 0.dp,
    label: @Composable (option: T, selected: Boolean) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val selected = option == selectedOption
            ToggleButton(
                checked = selected,
                onCheckedChange = { checked -> if (checked && !selected) onOptionSelected(option) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = null,
                modifier = Modifier.weight(1f).heightIn(min = buttonMinHeight).semantics { role = Role.RadioButton },
            ) { label(option, selected) }
        }
    }
}

/** commonMain has no dependency on Android's constants module in the standalone WASM build. */
private const val RETHINK_UID_EVERYBODY = -1000
