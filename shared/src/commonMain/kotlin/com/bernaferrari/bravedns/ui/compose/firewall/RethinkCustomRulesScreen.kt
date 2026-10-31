/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.firewall

import com.bernaferrari.bravedns.ui.compose.theme.rethinkListItemColors

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSheetHeader
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.Immutable
import androidx.compose.material3.DropdownMenuItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkMenuChip
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
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

/** The Rules screen's tabs. Values are navigation ints and must stay stable. */
enum class RethinkRulesTab(val value: Int) {
    IP(0),
    DOMAIN(1);

    companion object {
        fun fromValue(value: Int): RethinkRulesTab = entries.firstOrNull { it.value == value } ?: IP
    }
}

/**
 * The scope a Rules route opens with. Values are navigation ints and must stay stable.
 * On the all-apps screen (uid "everybody") [ALL_APPS] lists every rule and [SELECTED_UID] the
 * all-apps (universal) rules; with an app uid, [SELECTED_UID] is that app's rules.
 */
enum class RethinkRulesMode(val value: Int) {
    ALL_APPS(0),
    SELECTED_UID(1);

    companion object {
        fun fromValue(value: Int): RethinkRulesMode = entries.firstOrNull { it.value == value } ?: SELECTED_UID
    }
}

/** Which rules the Rules list shows: every rule, the all-apps ones, or one app's. */
@Immutable
sealed interface RethinkRulesScope {
    /** Every rule: all-apps rules first, then each app's under its name. */
    data object All : RethinkRulesScope

    /** Rules that apply to every app (the universal uid). */
    data object AllApps : RethinkRulesScope

    data class App(val uid: Int, val label: String) : RethinkRulesScope
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
    /** "Domains" / "IPs", and with a count: "Domains · 3". */
    val domains: String,
    val ips: String,
    val withCount: (String, Int) -> String = { label, count -> "$label · $count" },
    val searchDomains: String,
    val searchIps: String,
    val clearSearch: String,
    val loading: String,
    val noIpRules: String,
    val noDomainRules: String,
    /** "Add rule" (the FAB and the empty state). */
    val add: String,
    val cancel: String,
    val delete: String,
    val block: String,
    val trust: String,
    val bypass: String,
    val noRule: String,
    val uid: (Int) -> String,
    /** Scope chip: "All rules", "For all apps", "Choose app…", and the all-apps group header. */
    val scopeAll: String = "All rules",
    val scopeAllApps: String = "For all apps",
    val scopeChooseApp: String = "Choose app…",
    val allAppsHeader: String = "All apps",
    /** Title of the per-rule delete confirmation; the message is the rule itself. */
    val deleteRuleTitle: String = "Delete rule?",
    val deleteSelectedTitle: @Composable (Int) -> String = { "Delete $it rules?" },
    val selectedCount: @Composable (Int) -> String = { "$it selected" },
    val clearSelection: String = "Clear selection",
    val select: String = "Select",
    val deleteRule: String = "Delete rule",
    /** The tap sheet: its "Action" section title, and who the rule applies to ("Only Chrome"). */
    val action: String = "Action",
    val onlyApp: (String) -> String = { "Only $it" },
)

/**
 * The one Rules screen: a rule is a target (domain or IP), an action and a scope.
 *
 * Labelled **Domains · IPs** tabs with counts, a scope chip (All rules · For all apps · one app) when
 * [onScopeChange] is set (a screen opened for one app keeps that app and names it in [title]),
 * search, and an **Add rule** FAB. The host owns the Add sheet ([onAddRule]) and the app picker
 * ([onChooseApp]), plus persistence; paging, grouping, selection and delete are shared.
 */
@Composable
fun RethinkCustomRulesScreen(
    title: String,
    selectedTab: RethinkRulesTab,
    scope: RethinkRulesScope,
    query: String,
    rules: RethinkFirewallRulesFeed,
    strings: RethinkCustomRulesStrings,
    onTabChange: (RethinkRulesTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onAddRule: () -> Unit,
    onDeleteRule: (RethinkRulesTab, RethinkFirewallRule) -> Unit,
    modifier: Modifier = Modifier,
    domainCount: Int? = null,
    ipCount: Int? = null,
    onScopeChange: ((RethinkRulesScope) -> Unit)? = null,
    onChooseApp: (() -> Unit)? = null,
    onRuleOwnerVisible: (Int) -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    importAction: (@Composable () -> Unit)? = null,
    onEditRule: ((RethinkFirewallRule, RethinkRuleEdit) -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
) {
    // Long-press enters multi-select; tap opens the rule's sheet (Block · Trust, Delete); swipe is
    // a shortcut to delete.
    // Selection resets when the list it refers to changes.
    var selection by remember(selectedTab, scope) { mutableStateOf(emptyMap<String, RethinkFirewallRule>()) }
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
            RethinkRulesTopBar(selection.size, title, strings, onBackClick,
                { selection = emptyMap() }, { pendingDelete = selection.values.toList() },
                importAction)
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        floatingActionButton = {
            RethinkRulesAddButton(selecting, rules.isLoading, rules.itemCount, strings.add, onAddRule)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RethinkRulesControlDeck(
                selectedTab = selectedTab,
                scope = scope,
                query = query,
                strings = strings,
                domainCount = domainCount,
                ipCount = ipCount,
                onTabChange = onTabChange,
                onScopeChange = onScopeChange,
                onChooseApp = onChooseApp,
                onQueryChange = onQueryChange,
            )
            RethinkRulesContent(
                modifier = Modifier.weight(1f),
                rules = rules,
                selectedTab = selectedTab,
                grouped = scope == RethinkRulesScope.All,
                strings = strings,
                selection = selection.keys,
                onRuleClick = { rule -> if (selecting) toggle(rule) else actionRule = rule },
                onRuleLongClick = ::toggle,
                onRequestDelete = { pendingDelete = listOf(it) },
                onRuleOwnerVisible = onRuleOwnerVisible,
                onAddRule = onAddRule,
            )
        }
    }

    actionRule?.let { rule ->
        val app = scope as? RethinkRulesScope.App
        val owner = rule.ownerName?.takeIf { it.isNotBlank() }
            ?: app?.takeIf { it.uid == rule.uid }?.label?.takeIf { it.isNotBlank() }
            ?: strings.uid(rule.uid)
        RethinkRuleEditSheet(
            rule = rule,
            scopeLabel = if (rule.uid == RETHINK_RULES_UID_EVERYBODY) strings.allAppsHeader else strings.onlyApp(owner),
            strings = strings,
            onDismiss = { actionRule = null },
            onEdit = onEditRule?.let { edit -> { change -> edit(rule, change) } },
            onDelete = { pendingDelete = listOf(rule) },
        )
    }
    RethinkDeleteRulesDialog(
        rules = pendingDelete, strings = strings,
        onDismiss = { pendingDelete = emptyList() },
        onConfirm = {
            pendingDelete.forEach { onDeleteRule(selectedTab, it) }
            selection = selection - pendingDelete.map { it.id }.toSet()
            pendingDelete = emptyList()
        },
    )
}

@Composable
private fun RethinkRulesControlDeck(
    selectedTab: RethinkRulesTab,
    scope: RethinkRulesScope,
    query: String,
    strings: RethinkCustomRulesStrings,
    domainCount: Int?,
    ipCount: Int?,
    onTabChange: (RethinkRulesTab) -> Unit,
    onScopeChange: ((RethinkRulesScope) -> Unit)?,
    onChooseApp: (() -> Unit)?,
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
        // Domains lead: they're what people block and trust most.
        RethinkConnectedChoiceGroup(
            options = listOf(
                RethinkChoiceOption(RethinkRulesTab.DOMAIN, domainCount?.let { strings.withCount(strings.domains, it) } ?: strings.domains, MaterialSymbols.Filled.Link),
                RethinkChoiceOption(RethinkRulesTab.IP, ipCount?.let { strings.withCount(strings.ips, it) } ?: strings.ips, MaterialSymbols.Filled.Pin),
            ),
            selected = selectedTab,
            onSelect = onTabChange,
            modifier = Modifier.fillMaxWidth(),
        )
        RethinkSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = if (selectedTab == RethinkRulesTab.IP) strings.searchIps else strings.searchDomains,
            clearQueryContentDescription = strings.clearSearch,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onScopeChange != null) {
            RethinkMenuChip(
                label = when (scope) {
                    RethinkRulesScope.All -> strings.scopeAll
                    RethinkRulesScope.AllApps -> strings.scopeAllApps
                    is RethinkRulesScope.App -> scope.label
                },
                selected = scope != RethinkRulesScope.All,
            ) { dismiss ->
                DropdownMenuItem(
                    text = { Text(strings.scopeAll) },
                    leadingIcon = { Icon(MaterialSymbols.Filled.Rule, null) },
                    onClick = { dismiss(); onScopeChange(RethinkRulesScope.All) },
                )
                DropdownMenuItem(
                    text = { Text(strings.scopeAllApps) },
                    leadingIcon = { Icon(MaterialSymbols.Filled.Public, null) },
                    onClick = { dismiss(); onScopeChange(RethinkRulesScope.AllApps) },
                )
                if (onChooseApp != null) {
                    DropdownMenuItem(
                        text = { Text(strings.scopeChooseApp) },
                        leadingIcon = { Icon(MaterialSymbols.Filled.Android, null) },
                        onClick = { dismiss(); onChooseApp() },
                    )
                }
            }
        }
    }
}

@Composable
private fun RethinkRulesContent(
    rules: RethinkFirewallRulesFeed,
    selectedTab: RethinkRulesTab,
    grouped: Boolean,
    strings: RethinkCustomRulesStrings,
    selection: Set<String>,
    onRuleClick: (RethinkFirewallRule) -> Unit,
    onRuleLongClick: (RethinkFirewallRule) -> Unit,
    onRequestDelete: (RethinkFirewallRule) -> Unit,
    onRuleOwnerVisible: (Int) -> Unit,
    onAddRule: () -> Unit,
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
                bottom = SharedDimensions.fabClearance,
            ),
        ) {
            items(rules.itemCount, key = { index -> rules[index]?.id ?: "rule-$index" }) { index ->
                val rule = rules[index] ?: return@items
                val previous = rules.getOrNull(index - 1)
                val next = rules.getOrNull(index + 1)
                val isGrouped = grouped
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
    onAddRule: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RethinkEmptyState(
            title = if (tab == RethinkRulesTab.IP) strings.noIpRules else strings.noDomainRules,
            icon = if (tab == RethinkRulesTab.IP) MaterialSymbols.Filled.Pin else MaterialSymbols.Filled.Link,
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
    if (rule.uid == RETHINK_RULES_UID_EVERYBODY) {
        SectionHeader(strings.allAppsHeader)
        return
    }
    LaunchedEffect(rule.uid) { onRuleOwnerVisible(rule.uid) }
    val uidLabel = strings.uid(rule.uid)
    val owner = rule.ownerName?.takeIf { it.isNotBlank() } ?: uidLabel
    SectionHeader(owner)
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
                rule.tab == RethinkRulesTab.IP -> MaterialSymbols.Filled.Pin
                else -> MaterialSymbols.Filled.Link
            },
            colors = rethinkListItemColors(leadingIconTint = if (selected) MaterialTheme.colorScheme.onPrimary else accent, leadingIconContainerColor = if (selected) MaterialTheme.colorScheme.primary else RethinkIconWellDefaults.tinted(accent)),
            highlighted = selected,
            position = position,
            trailing = { RethinkPill(rule.status.label(strings), colors = RethinkPillDefaults.status(accent)) },
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}
