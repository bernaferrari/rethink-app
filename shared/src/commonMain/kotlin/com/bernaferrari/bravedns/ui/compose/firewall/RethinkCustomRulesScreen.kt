/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSheetHeader
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import androidx.compose.material3.SwipeToDismissBoxValue
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import androidx.compose.ui.text.style.TextOverflow
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBox
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceLabels
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFirewallRuleStatus
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWellDefaults
import com.bernaferrari.bravedns.ui.compose.theme.accent
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedRowTopGap

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
    /** "Port %s"-style label for the port line under an IP rule. */
    val port: (Int) -> String = { "Port $it" },
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
    val deleteSelectedTitle: @Composable (Int) -> String = { "Delete $it rules?" },
    val selectedCount: @Composable (Int) -> String = { "$it selected" },
    val clearSelection: String = "Clear selection",
    val select: String = "Select",
    val deleteRule: String = "Delete rule",
    val addIpRuleTitle: String = "Add IP rule",
    val addDomainRuleTitle: String = "Add domain rule",
    val ipHint: String = "192.168.1.1 or 10.0.0.0/24",
    val domainHint: String = "example.com",
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
    var showAddDialog by rememberSaveable(uid) { mutableStateOf(false) }
    // The scope switch is a view of the same screen, so the title holds still when it flips.
    val title = if (canSwitchScope) strings.universalTitle else strings.appRulesTitle

    LaunchedEffect(effectiveMode) {
        if (effectiveMode == RethinkRulesMode.ALL_APPS) showAddDialog = false
    }
    // Long-press enters multi-select; tap opens a per-rule action sheet; swipe is a shortcut.
    // Selection resets when the list it refers to changes.
    var selection by remember(uid, selectedTab, effectiveMode) { mutableStateOf(emptyMap<String, RethinkFirewallRule>()) }
    var actionRule by remember { mutableStateOf<RethinkFirewallRule?>(null) }
    var pendingDelete by remember { mutableStateOf<List<RethinkFirewallRule>>(emptyList()) }
    val selecting = selection.isNotEmpty()
    fun toggle(rule: RethinkFirewallRule) {
        selection = if (rule.id in selection) selection - rule.id else selection + (rule.id to rule)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (selecting) {
                RethinkTopBar(
                    title = strings.selectedCount(selection.size),
                    onBackClick = { selection = emptyMap() },
                    backDescription = strings.clearSelection,
                    navigationIcon = MaterialSymbols.Filled.Close,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    actions = {
                        IconButton(onClick = { pendingDelete = selection.values.toList() }) {
                            Icon(MaterialSymbols.Filled.DeleteOutline, strings.delete)
                        }
                    },
                )
            } else {
                RethinkTopBar(title = title, onBackClick = onBackClick)
            }
        },
        floatingActionButton = {
            if (!selecting && effectiveMode == RethinkRulesMode.SELECTED_UID && (rules.isLoading || rules.itemCount > 0)) {
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
                selection = selection.keys,
                onRuleClick = { rule -> if (selecting) toggle(rule) else actionRule = rule },
                onRuleLongClick = ::toggle,
                onRequestDelete = { pendingDelete = listOf(it) },
                onRuleOwnerVisible = onRuleOwnerVisible,
                onAddRule = if (effectiveMode == RethinkRulesMode.SELECTED_UID) {
                    { showAddDialog = true }
                } else {
                    null
                },
            )
        }
    }

    actionRule?.let { rule ->
        RethinkRuleActionsSheet(
            rule = rule,
            strings = strings,
            onDismiss = { actionRule = null },
            onSelect = { toggle(rule) },
            onDelete = { pendingDelete = listOf(rule) },
        )
    }
    if (pendingDelete.isNotEmpty()) {
        val single = pendingDelete.singleOrNull()
        RethinkConfirmDialog(
            onDismissRequest = { pendingDelete = emptyList() },
            title = if (single != null) strings.deleteRuleTitle else strings.deleteSelectedTitle(pendingDelete.size),
            message = single?.let { listOfNotNull(it.value, it.port?.let(strings.port)).joinToString(" · ") } ?: "",
            confirmText = strings.delete,
            dismissText = strings.cancel,
            isConfirmDestructive = true,
            onConfirm = {
                pendingDelete.forEach { onDeleteRule(selectedTab, it) }
                selection = selection - pendingDelete.map { it.id }.toSet()
                pendingDelete = emptyList()
            },
        )
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
        // Scope and rule type share one row as two separate groups: every segment keeps its
        // label and only the selected one adds its icon, and the gap plus each group's own
        // rounded ends keep them from reading as one 4-segment control.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            val labels = if (canSwitchScope) RethinkChoiceLabels.SelectedIcon else RethinkChoiceLabels.Always
            if (canSwitchScope) {
                RethinkRuleScopeSelector(
                    selected = selectedMode,
                    strings = strings,
                    labels = labels,
                    onSelected = onModeChange,
                    modifier = Modifier.weight(1f),
                )
            }
            RethinkRuleTabSelector(
                selectedTab,
                strings,
                labels = labels,
                onSelected = onTabChange,
                modifier = Modifier.weight(1f),
            )
        }
        RethinkSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = "${strings.search}: ${if (selectedTab == RethinkRulesTab.IP) strings.ipRules else strings.domainRules}",
            clearQueryContentDescription = strings.clearSearch,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RethinkRuleTabSelector(
    selected: RethinkRulesTab,
    strings: RethinkCustomRulesStrings,
    labels: RethinkChoiceLabels,
    modifier: Modifier = Modifier,
    onSelected: (RethinkRulesTab) -> Unit,
) {
    RethinkConnectedChoiceGroup(
        options = listOf(
            // The screen title already says "rules"; the short type names fit every locale at
            // phone width without truncating.
            RethinkChoiceOption(RethinkRulesTab.IP, strings.ip, MaterialSymbols.Filled.Public),
            RethinkChoiceOption(RethinkRulesTab.DOMAIN, strings.domain, MaterialSymbols.Filled.Language),
        ),
        selected = selected,
        onSelect = onSelected,
        modifier = modifier,
        labels = labels,
    )
}

@Composable
private fun RethinkRuleScopeSelector(
    selected: RethinkRulesMode,
    strings: RethinkCustomRulesStrings,
    labels: RethinkChoiceLabels,
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
        labels = labels,
    )
}

@Composable
private fun RethinkRulesContent(
    rules: RethinkFirewallRulesFeed,
    selectedTab: RethinkRulesTab,
    selectedMode: RethinkRulesMode,
    strings: RethinkCustomRulesStrings,
    selection: Set<String>,
    onRuleClick: (RethinkFirewallRule) -> Unit,
    onRuleLongClick: (RethinkFirewallRule) -> Unit,
    onRequestDelete: (RethinkFirewallRule) -> Unit,
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
                bottom = if (selectedMode == RethinkRulesMode.SELECTED_UID) SharedDimensions.fabClearance else SharedDimensions.spacing3xl,
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
                RethinkFirewallRuleRow(
                    rule = rule,
                    position = position,
                    strings = strings,
                    selected = rule.id in selection,
                    selecting = selection.isNotEmpty(),
                    onClick = { onRuleClick(rule) },
                    onLongClick = { onRuleLongClick(rule) },
                    onRequestDelete = { onRequestDelete(rule) },
                )
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
    )
}

@Composable
private fun RethinkFirewallRuleRow(
    rule: RethinkFirewallRule,
    position: CardPosition,
    strings: RethinkCustomRulesStrings,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val accent = rule.status.accent()
    // Swipe (end → start) onto an error backdrop is a shortcut only; tap opens the action sheet
    // and long-press enters multi-select, so delete never depends on a hidden gesture.
    val swipeState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val shape = rethinkGroupedListShape(position)
    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = !selecting,
        modifier = Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(strings.delete) { onRequestDelete(); true })
        },
        onDismiss = {
            onRequestDelete()
            scope.launch { swipeState.reset() }
        },
        backgroundContent = {
            // Only while actually swiping: tonal (translucent) row states must not reveal it.
            if (swipeState.dismissDirection == SwipeToDismissBoxValue.EndToStart) Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = rethinkGroupedRowTopGap(position))
                    .background(MaterialTheme.colorScheme.errorContainer, shape)
                    .padding(horizontal = SharedDimensions.spacingXl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    MaterialSymbols.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        RethinkListItem(
            // The address keeps the headline (elided in the middle if needed); the port gets its
            // own line instead of being cut off at the end.
            headline = rule.value,
            supporting = rule.port?.let(strings.port),
            singleLineHeadline = true,
            leadingIcon = when {
                selected -> MaterialSymbols.Filled.CheckCircle
                rule.tab == RethinkRulesTab.IP -> MaterialSymbols.Filled.Public
                else -> MaterialSymbols.Filled.Language
            },
            leadingIconTint = if (selected) MaterialTheme.colorScheme.onPrimary else accent,
            leadingIconContainerColor = if (selected) MaterialTheme.colorScheme.primary else RethinkIconWellDefaults.tinted(accent),
            highlighted = selected,
            position = position,
            trailing = { RethinkPill(rule.status.label(strings), colors = RethinkPillDefaults.status(accent)) },
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}

private fun RethinkFirewallRuleStatus.label(strings: RethinkCustomRulesStrings): String = when (this) {
    RethinkFirewallRuleStatus.Block -> strings.block
    RethinkFirewallRuleStatus.Trust -> strings.trust
    RethinkFirewallRuleStatus.Bypass -> strings.bypass
    RethinkFirewallRuleStatus.None -> strings.noRule
}

/** Per-rule actions: the discoverable home of Delete (and the entry into multi-select). */
@Composable
private fun RethinkRuleActionsSheet(
    rule: RethinkFirewallRule,
    strings: RethinkCustomRulesStrings,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = rule.status.accent()
    RethinkModalBottomSheet(onDismissRequest = onDismiss) { dismiss ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    rule.value,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                rule.port?.let {
                    Text(strings.port(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            RethinkPill(rule.status.label(strings), colors = RethinkPillDefaults.status(accent))
        }
        RethinkListGroup {
            RethinkListItem(
                headline = strings.select,
                leadingIcon = MaterialSymbols.Filled.CheckCircle,
                position = CardPosition.First,
                onClick = { onSelect(); dismiss() },
            )
            RethinkListItem(
                headline = strings.deleteRule,
                leadingIcon = MaterialSymbols.Filled.DeleteOutline,
                destructive = true,
                position = CardPosition.Last,
                onClick = { onDelete(); dismiss() },
            )
        }
    }
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
    val isIp = tab == RethinkRulesTab.IP
    val title = if (isIp) strings.addIpRuleTitle else strings.addDomainRuleTitle
    RethinkModalBottomSheet(
        onDismissRequest = onDismiss,
        expandOnShow = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
            RethinkSheetHeader(title)
            RethinkFormTextField(
                value = value,
                onValueChange = { value = it; error = null },
                label = if (isIp) strings.ip else strings.domain,
                placeholder = if (isIp) strings.ipHint else strings.domainHint,
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
