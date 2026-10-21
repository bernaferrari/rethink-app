/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormActionRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormTextField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/** The portable custom-rules screen supports both universal and app-specific rule scopes. */
enum class RethinkRulesTab(val value: Int) {
    IP(0),
    DOMAIN(1);

    companion object {
        fun fromValue(value: Int): RethinkRulesTab = entries.firstOrNull { it.value == value } ?: IP
    }
}

/**
 * Which rules the screen lists. Values are the persisted/navigation ints and must stay stable.
 */
enum class RethinkRulesMode(val value: Int) {
    /** Every app's rules, grouped by owning app ("App-wise"). Only offered on the universal screen. */
    ALL_APPS(0),

    /**
     * Rules owned by the screen's own uid: the universal rules on the universal screen
     * ("Universal"), or one app's rules on an app screen.
     */
    SELECTED_UID(1);

    companion object {
        fun fromValue(value: Int): RethinkRulesMode = entries.firstOrNull { it.value == value } ?: SELECTED_UID
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
    /** Title of the per-rule delete confirmation; the message is the rule itself. */
    val deleteRuleTitle: String = "Delete rule?",
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
    val effectiveMode = if (canSwitchScope) selectedMode else RethinkRulesMode.SELECTED_UID
    val isUniversalRules = uid == RETHINK_UID_EVERYBODY && effectiveMode == RethinkRulesMode.SELECTED_UID
    var showAddDialog by rememberSaveable(uid) { mutableStateOf(false) }
    val title = when {
        isUniversalRules -> strings.universalTitle
        effectiveMode == RethinkRulesMode.ALL_APPS -> strings.appWiseTitle
        else -> strings.appRulesTitle
    }

    LaunchedEffect(effectiveMode) {
        if (effectiveMode == RethinkRulesMode.ALL_APPS) showAddDialog = false
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { RethinkTopBar(title = title, onBackClick = onBackClick) },
        floatingActionButton = {
            if (effectiveMode == RethinkRulesMode.SELECTED_UID && (rules.isLoading || rules.itemCount > 0)) {
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
                onAddRule = if (effectiveMode == RethinkRulesMode.SELECTED_UID) {
                    { showAddDialog = true }
                } else {
                    null
                },
            )
        }
    }

    if (showAddDialog && effectiveMode == RethinkRulesMode.SELECTED_UID) {
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
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        // Scope and rule type are two separate questions, so they stack as two full-width
        // groups rather than sitting side by side where they read as one 4-segment control.
        if (canSwitchScope) {
            RethinkRuleScopeSelector(selected = selectedMode, strings = strings, onSelected = onModeChange)
        }
        RethinkRuleTabSelector(selectedTab, strings, onSelected = onTabChange)
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
    modifier: Modifier = Modifier,
    onSelected: (RethinkRulesTab) -> Unit,
) {
    RethinkConnectedChoiceGroup(
        options = listOf(
            RethinkChoiceOption(RethinkRulesTab.IP, strings.ipRules, MaterialSymbols.Filled.Public),
            RethinkChoiceOption(RethinkRulesTab.DOMAIN, strings.domainRules, MaterialSymbols.Filled.Language),
        ),
        selected = selected,
        onSelect = onSelected,
        modifier = modifier,
    )
}

@Composable
private fun RethinkRuleScopeSelector(
    selected: RethinkRulesMode,
    strings: RethinkCustomRulesStrings,
    modifier: Modifier = Modifier,
    onSelected: (RethinkRulesMode) -> Unit,
) {
    RethinkConnectedChoiceGroup(
        options = listOf(
            RethinkChoiceOption(RethinkRulesMode.SELECTED_UID, strings.universal, MaterialSymbols.Filled.Security),
            RethinkChoiceOption(RethinkRulesMode.ALL_APPS, strings.appWise, MaterialSymbols.Filled.Apps),
        ),
        selected = selected,
        onSelect = onSelected,
        modifier = modifier,
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
    onAddRule: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when {
        rules.isLoading && rules.itemCount == 0 -> RethinkRulesInfo(strings.loading, modifier)
        !rules.isLoading && rules.itemCount == 0 -> RethinkRulesEmptyState(selectedTab, strings, onAddRule, modifier)
        else -> androidx.compose.foundation.lazy.LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingSm,
                bottom = if (selectedMode == RethinkRulesMode.SELECTED_UID) 112.dp else SharedDimensions.spacing3xl,
            ),
        ) {
            items(rules.itemCount, key = { index -> rules[index]?.id ?: "rule-$index" }) { index ->
                val rule = rules[index] ?: return@items
                val previous = rules.getOrNull(index - 1)
                val next = rules.getOrNull(index + 1)
                val isGrouped = selectedMode == RethinkRulesMode.ALL_APPS
                if (isGrouped && (previous == null || previous.uid != rule.uid)) {
                    if (index > 0) Spacer(Modifier.height(SharedDimensions.spacingSm))
                    RethinkRuleOwnerHeader(rule, strings, onRuleOwnerVisible)
                    Spacer(Modifier.height(SharedDimensions.spacingXs))
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
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RethinkLoadingState(label = text)
    }
}

@Composable
private fun RethinkRulesEmptyState(
    tab: RethinkRulesTab,
    strings: RethinkCustomRulesStrings,
    onAddRule: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RethinkEmptyState(
            title = if (tab == RethinkRulesTab.IP) strings.noIpRules else strings.noDomainRules,
            message = "",
            icon = if (tab == RethinkRulesTab.IP) MaterialSymbols.Filled.Public else MaterialSymbols.Filled.Language,
            ctaLabel = strings.add,
            onCtaClick = onAddRule,
        )
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
        RethinkFirewallRuleStatus.Block -> RethinkStatusColors.blocked
        RethinkFirewallRuleStatus.Trust -> RethinkStatusColors.allowed
        RethinkFirewallRuleStatus.Bypass -> RethinkStatusColors.mixed
        RethinkFirewallRuleStatus.None -> RethinkStatusColors.idle
    }
    val headline = rule.port?.let { "${rule.value}:$it" } ?: rule.value
    var confirmDelete by remember(rule.id) { mutableStateOf(false) }
    if (confirmDelete) {
        RethinkConfirmDialog(
            onDismissRequest = { confirmDelete = false },
            title = strings.deleteRuleTitle,
            message = headline,
            confirmText = strings.delete,
            dismissText = strings.cancel,
            isConfirmDestructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
    RethinkListItem(
        headline = headline,
        leadingIcon = if (rule.tab == RethinkRulesTab.IP) MaterialSymbols.Filled.Public else MaterialSymbols.Filled.Language,
        leadingIconTint = accent,
        leadingIconContainerColor = accent.copy(alpha = .14f),
        position = position,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadiusPill),
                    color = accent.copy(alpha = .14f),
                ) {
                    Text(
                        statusLabel,
                        Modifier.padding(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingGridTile),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(MaterialSymbols.Filled.DeleteOutline, strings.delete) }
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
    RethinkModalBottomSheet(
        onDismissRequest = onDismiss,
        expandOnShow = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            RethinkFormTextField(
                value = value,
                onValueChange = { value = it; error = null },
                label = title,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                singleLine = true,
            )
            RethinkFormActionRow(
                confirmLabel = strings.add,
                onConfirm = { error = onAdd(value.trim()).error },
                dismissLabel = strings.cancel,
                onDismiss = onDismiss,
                confirmEnabled = value.isNotBlank(),
            )
        }
    }
}

/** commonMain has no dependency on Android's constants module in the standalone WASM build. */
private const val RETHINK_UID_EVERYBODY = -1000
