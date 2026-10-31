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
 * distributed under the License is distributed on an AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bernaferrari.bravedns.ui.compose.logs

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import com.bernaferrari.bravedns.ui.compose.common.RethinkAdaptiveWidth
import com.bernaferrari.bravedns.ui.compose.common.isExpanded
import com.bernaferrari.bravedns.ui.compose.dns.dnsLogModel
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkToolbarRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkToolbarIconToggle
import com.bernaferrari.bravedns.ui.compose.dns.DnsLogRow
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.listSaver
import com.bernaferrari.bravedns.database.ActivityAppCount
import com.bernaferrari.bravedns.database.ActivityLogEntry
import com.bernaferrari.bravedns.database.ActivityLogKinds
import com.bernaferrari.bravedns.database.ActivityLogQuery
import com.bernaferrari.bravedns.database.ActivityVerdict
import com.bernaferrari.bravedns.database.ConnectionTracker
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.database.DnsLog
import com.bernaferrari.bravedns.database.DnsLogRepository
import com.bernaferrari.bravedns.service.FirewallRuleset
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkOverflowItem
import com.bernaferrari.bravedns.ui.compose.common.AppIcon
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.icons.keepout.KeepoutIcons
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.viewmodel.ActivityLogViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The log: connections and DNS lookups as one timeline (All), or either alone, filtered by verdict,
 * reason, app and search. One control row: the verdict group and a Filters button, whose sheet
 * holds Type, App and Reason; active ones show as removable chips under the row. Every row opens the Connection sheet (why it was allowed or blocked, and
 * the fix); at Expanded width rows select into a pane that opens the same sheet.
 *
 * As the Activity tab's Log view the host passes [viewSwitch] (Log · Insights) and a null
 * [onBackClick]. Pushed with a [query] (e.g. from Insights) it lists only matching entries, titled
 * by the query; pushed with an [appUid] (App detail's "See all activity") it lists that app's
 * entries only, titled by [appName].
 */
@Composable
fun NetworkLogsScreen(
    connectionTrackerRepository: ConnectionTrackerRepository,
    dnsLogRepository: DnsLogRepository,
    persistentState: PersistentState,
    eventLogger: EventLogger,
    onAppLogsClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    sheetNavigation: ConnectionSheetNavigation = ConnectionSheetNavigation(),
    query: String = "",
    appUid: Int? = null,
    appName: String = "",
    title: String? = null,
    viewSwitch: (@Composable () -> Unit)? = null,
    viewModel: ActivityLogViewModel = rememberActivityLogViewModel(),
) {
    var sheetTarget by remember { mutableStateOf<LogSheetTarget?>(null) }
    // Filters survive switching types and views (saveable), not only recomposition.
    val filters = rememberSaveable(saver = LogFilters.Saver) { LogFilters() }
    val scope = LogScope(query = query, appUid = appUid, appName = appName)
    val content: @Composable ((RethinkLogToolbarActions) -> Unit) -> Unit = { onToolbarActionsChange ->
        ActivityLogContent(
            filters = filters,
            scope = scope,
            viewModel = viewModel,
            connections = connectionTrackerRepository,
            lookups = dnsLogRepository,
            persistentState = persistentState,
            onOpenSheet = { sheetTarget = it },
            onToolbarActionsChange = onToolbarActionsChange,
        )
    }
    RethinkLogsScreenShell(
        strings = RethinkLogsStrings(
            title = title ?: appName.ifBlank { query }.ifBlank { stringResource(R.string.lbl_logs) },
            network = stringResource(R.string.firewall_act_network_monitor_tab),
            dns = stringResource(R.string.dns_mode_info_title),
            moreActions = stringResource(R.string.wireguard_fab_more_actions),
            refresh = stringResource(R.string.cd_refresh),
            clear = stringResource(R.string.fapps_filter_clear_btn),
            connections = stringResource(R.string.activity_type_connections),
            lookups = stringResource(R.string.activity_type_lookups),
        ),
        onBackClick = onBackClick,
        viewSwitch = viewSwitch,
        additionalOverflowItems = listOf(
            RethinkOverflowItem(stringResource(R.string.console_log_title), MaterialSymbols.Filled.Terminal, onClick = onAppLogsClick),
        ),
        content = content,
    )

    sheetTarget?.let { target ->
        LogConnectionSheet(
            target = target,
            eventLogger = eventLogger,
            navigation = sheetNavigation,
            loadFavIcon = persistentState.fetchFavIcon,
            onDismiss = { sheetTarget = null },
        )
    }
}

/** What the screen was opened for: a search or one app. Fixed for its lifetime, unlike [LogFilters]. */
@Immutable
private data class LogScope(val query: String, val appUid: Int?, val appName: String)

/** The filters the user sets; they survive switching views and tabs. */
@Stable
private class LogFilters(
    type: RethinkLogType = RethinkLogType.All,
    verdict: RethinkLogFilter = RethinkLogFilter.All,
    rules: List<String> = emptyList(),
    appUid: Int? = null,
    appName: String = "",
) {
    var type by mutableStateOf(type)
    var verdict by mutableStateOf(verdict)
    var rules by mutableStateOf(rules)
    /** The app picked in the app filter (by uid: names collide), and its name for the empty state. */
    var appUid by mutableStateOf(appUid)
    var appName by mutableStateOf(appName)

    companion object {
        val Saver = listSaver<LogFilters, Any?>(
            save = { listOf(it.type.name, it.verdict.name, ArrayList(it.rules), it.appUid, it.appName) },
            restore = { saved ->
                LogFilters(
                    type = RethinkLogType.valueOf(saved[0] as String),
                    verdict = RethinkLogFilter.valueOf(saved[1] as String),
                    rules = (saved[2] as List<*>).filterIsInstance<String>(),
                    appUid = saved[3] as Int?,
                    appName = saved[4] as String,
                )
            },
        )
    }
}

@Composable
private fun ActivityLogContent(
    filters: LogFilters,
    scope: LogScope,
    viewModel: ActivityLogViewModel,
    connections: ConnectionTrackerRepository,
    lookups: DnsLogRepository,
    persistentState: PersistentState,
    onOpenSheet: (LogSheetTarget) -> Unit,
    onToolbarActionsChange: (RethinkLogToolbarActions) -> Unit,
) {
    // Local mirror of the pref so the inline "Enable logs" action recomposes immediately.
    var logsEnabled by remember { mutableStateOf(persistentState.logsEnabled) }
    val items = viewModel.entries.collectAsLazyPagingItems()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showAppFilterDialog by remember { mutableStateOf(false) }
    var showFiltersSheet by remember { mutableStateOf(false) }
    var appPickerQuery by remember { mutableStateOf("") }
    var appFilterOptions by remember { mutableStateOf<List<ActivityAppCount>>(emptyList()) }
    var appFilterOptionsLoading by remember { mutableStateOf(false) }

    val logQuery = ActivityLogQuery(
        kinds = filters.type.toKinds(),
        verdict = filters.verdict.toVerdict(),
        rules = filters.rules.toSet(),
        search = scope.query,
        uid = scope.appUid ?: filters.appUid,
    )
    LaunchedEffect(logQuery) { viewModel.setQuery(logQuery) }

    val refreshAction = remember(items) { { items.refresh() } }
    val clearAction = remember { { showDeleteDialog = true } }
    LaunchedEffect(refreshAction, clearAction, logsEnabled, items.itemCount) {
        onToolbarActionsChange(
            RethinkLogToolbarActions(
                onRefresh = refreshAction.takeIf { logsEnabled },
                onClear = clearAction.takeIf { logsEnabled && items.itemCount > 0 },
            )
        )
    }

    // A picked app is a filter: back clears it first. An app the screen was opened for is not.
    BackHandler(enabled = filters.appUid != null && !showDeleteDialog && !showRulesDialog && !showAppFilterDialog && !showFiltersSheet) {
        filters.appUid = null
    }

    if (!logsEnabled) {
        LogsDisabled {
            persistentState.logsEnabled = true
            logsEnabled = true
        }
        return
    }

    // Reasons are the firewall's: lookups have none, so the filter is offered wherever connections are.
    val ruleFilters = if (filters.type == RethinkLogType.Lookups) emptyList() else when (filters.verdict) {
        RethinkLogFilter.Blocked -> FirewallRuleset.getBlockedRules()
        RethinkLogFilter.Allowed -> FirewallRuleset.getAllowedRules()
        RethinkLogFilter.All -> FirewallRuleset.entries.toList()
    }

    LaunchedEffect(showAppFilterDialog, logQuery.kinds, logQuery.verdict, logQuery.rules) {
        if (!showAppFilterDialog) return@LaunchedEffect
        appFilterOptionsLoading = true
        appFilterOptions = viewModel.loggedApps(logQuery)
        appFilterOptionsLoading = false
    }

    val listState = rememberLazyListState()
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val loadFavIcon = persistentState.fetchFavIcon
    val search = scope.appName.ifBlank { filters.appName.takeIf { filters.appUid != null }.orEmpty() }.ifBlank { scope.query }

    LogsAdaptiveLayout(
        controls = {
            val active = activeFilters(filters)
            LogControls(
                verdict = filters.verdict,
                onVerdict = {
                    filters.verdict = it
                    filters.rules = emptyList()
                },
                filtersActive = active.isNotEmpty(),
                onFilters = { showFiltersSheet = true },
                active = active,
            )
        },
        list = { listModifier, twoPane ->
            LogsPagedListContent(
                items = items,
                listState = listState,
                emptyMessage = emptyLogMessage(filters.verdict, search),
                modifier = listModifier
            ) { entry, index, itemCount ->
                val selected = twoPane && entry.key == (selectedKey ?: items.firstLoadedId { it.key })
                val onSelect = if (twoPane) ({ selectedKey = entry.key }) else ({ onOpenSheet(entry.sheetTarget()) })
                entry.connection?.let { ct ->
                    ConnectionRow(ct = ct, index = index, itemCount = itemCount, selected = selected, onSelect = onSelect)
                }
                entry.lookup?.let { log ->
                    DnsLogRow(
                        log = log,
                        loadFavIcon = loadFavIcon,
                        isRethinkDns = false,
                        onShowBlocklist = { onOpenSheet(LogSheetTarget.Lookup(it)) },
                        index = index,
                        itemCount = itemCount,
                        selected = selected,
                        onSelect = onSelect,
                    )
                }
            }
        },
        pane = { paneModifier ->
            val selected = items.loadedOrFirst(selectedKey) { it.key }
            val lookup = selected?.lookup
            if (lookup != null) {
                DnsLogPane(lookup, lookups, loadFavIcon, { onOpenSheet(LogSheetTarget.Lookup(it)) }, paneModifier)
            } else {
                ConnectionLogPane(selected?.connection, connections, { onOpenSheet(LogSheetTarget.Connection(it)) }, paneModifier)
            }
        },
    )

    if (showFiltersSheet) {
        val anyReason = stringResource(R.string.activity_filter_any_reason)
        val reasonTitles = filters.rules.mapNotNull { id -> FirewallRuleset.getFirewallRule(id)?.title }.map { stringResource(it) }
        RethinkLogFiltersSheet(
            type = filters.type,
            onType = { type ->
                filters.type = type
                // Lookups have no firewall reason: a reason filter would only hide them.
                if (type == RethinkLogType.Lookups) filters.rules = emptyList()
            },
            appSummary = if (scope.appUid != null) null else filters.appName.takeIf { filters.appUid != null }
                ?: stringResource(R.string.activity_filter_all_apps),
            onApp = { showAppFilterDialog = true },
            reasonSummary = if (ruleFilters.isEmpty()) null else reasonTitles.joinToString().ifEmpty { anyReason },
            onReason = { showRulesDialog = true },
            strings = RethinkLogFiltersStrings(
                title = stringResource(R.string.activity_filters),
                type = stringResource(R.string.activity_filter_type),
                all = stringResource(R.string.activity_type_all),
                connections = stringResource(R.string.activity_type_connections),
                lookups = stringResource(R.string.activity_type_lookups),
                app = stringResource(R.string.log_filter_app),
                reason = stringResource(R.string.log_filter_reason),
            ),
            onDismiss = { showFiltersSheet = false },
        )
    }

    if (showRulesDialog && ruleFilters.isNotEmpty()) {
        RethinkLogRulesDialog(
            rules = ruleFilters.map { rule ->
                RethinkLogRuleOption(
                    id = rule.id,
                    title = stringResource(rule.title),
                    supporting = rethinkHtmlToAnnotatedString(stringResource(rule.desc)),
                    leadingIcon = { tint -> PlatformLogRuleIcon(rule.id, tint) },
                )
            },
            selectedIds = filters.rules.toSet(),
            strings = RethinkLogRulesStrings(
                title = stringResource(R.string.log_filter_reason),
                clear = stringResource(R.string.fapps_filter_clear_btn),
                dismissDescription = stringResource(R.string.lbl_dismiss),
            ),
            onToggle = { ruleId ->
                filters.rules = if (ruleId in filters.rules) filters.rules - ruleId else filters.rules + ruleId
            },
            onClear = { filters.rules = emptyList() },
            onDismiss = { showRulesDialog = false }
        )
    }

    if (showAppFilterDialog) {
        RethinkLogAppFilterDialog(
            options = appFilterOptions.toRethinkLogAppOptions(),
            selectedId = filters.appUid?.toString(),
            searchQuery = appPickerQuery,
            isLoading = appFilterOptionsLoading,
            strings = logAppFilterStrings(appFilterOptions.size),
            onSearchQueryChange = { appPickerQuery = it },
            onSelect = { picked ->
                val app = appFilterOptions.firstOrNull { it.uid.toString() == picked }
                filters.appUid = app?.uid
                filters.appName = app?.appName.orEmpty()
                showAppFilterDialog = false
            },
            onClearSelection = { filters.appUid = null },
            onDismiss = { showAppFilterDialog = false },
            appIcon = { option -> PlatformLogAppIcon(option) },
        )
    }

    LogsDeleteDialog(
        show = showDeleteDialog,
        title = stringResource(
            when (filters.type) {
                RethinkLogType.All -> R.string.activity_clear_all_title
                RethinkLogType.Connections -> R.string.conn_track_clear_logs_title
                RethinkLogType.Lookups -> R.string.activity_clear_lookups_title
            }
        ),
        onDismiss = { showDeleteDialog = false },
        onDelete = {
            if (filters.type != RethinkLogType.Lookups) connections.clearAllData()
            if (filters.type != RethinkLogType.Connections) lookups.clearAllData()
        },
        onRefresh = { items.refresh() }
    )
}

private fun RethinkLogType.toKinds() = when (this) {
    RethinkLogType.All -> ActivityLogKinds.All
    RethinkLogType.Connections -> ActivityLogKinds.Connections
    RethinkLogType.Lookups -> ActivityLogKinds.Lookups
}

/** The non-default filters the user set, each a chip that removes itself; the screen's own app is not one. */
@Composable
private fun activeFilters(filters: LogFilters): List<RethinkActiveLogFilter> = buildList {
    when (filters.type) {
        RethinkLogType.All -> Unit
        RethinkLogType.Connections ->
            add(RethinkActiveLogFilter("type", stringResource(R.string.activity_type_connections)) { filters.type = RethinkLogType.All })
        RethinkLogType.Lookups ->
            add(RethinkActiveLogFilter("type", stringResource(R.string.activity_type_lookups)) { filters.type = RethinkLogType.All })
    }
    if (filters.appUid != null) {
        add(RethinkActiveLogFilter("app", filters.appName) { filters.appUid = null })
    }
    for (id in filters.rules) {
        val title = FirewallRuleset.getFirewallRule(id)?.title?.let { stringResource(it) } ?: id
        add(RethinkActiveLogFilter("rule:$id", stringResource(R.string.activity_filter_reason_chip, title)) { filters.rules = filters.rules - id })
    }
}

private fun RethinkLogFilter.toVerdict() = when (this) {
    RethinkLogFilter.All -> ActivityVerdict.All
    RethinkLogFilter.Allowed -> ActivityVerdict.Allowed
    RethinkLogFilter.Blocked -> ActivityVerdict.Blocked
}

private fun ActivityLogEntry.sheetTarget(): LogSheetTarget =
    connection?.let { LogSheetTarget.Connection(it) } ?: LogSheetTarget.Lookup(checkNotNull(lookup))

/**
 * One control row: the verdict group (icons, the selected one labelled) and the Filters button,
 * dotted while any filter is set; under it, the active filters as removable chips.
 */
@Composable
private fun LogControls(
    verdict: RethinkLogFilter,
    onVerdict: (RethinkLogFilter) -> Unit,
    filtersActive: Boolean,
    onFilters: () -> Unit,
    active: List<RethinkActiveLogFilter>,
) {
    RethinkLogsControlsDeck {
        RethinkToolbarRow {
            RethinkLogFilterRow(
                selected = verdict,
                strings = logListStrings(),
                onSelected = onVerdict,
                modifier = Modifier.weight(1f)
            )
            RethinkToolbarIconToggle(
                icon = MaterialSymbols.Filled.FilterList,
                contentDescription = stringResource(R.string.activity_filters),
                selected = filtersActive,
                showSelectedDot = true,
                onClick = onFilters,
            )
        }
    }
    val remove = stringResource(R.string.activity_filter_remove)
    RethinkActiveLogFilters(filters = active, removeDescription = { remove.format(it) })
}

/** The empty list says what the filters ruled out, instead of a generic "no logs". */
@Composable
private fun emptyLogMessage(filter: RethinkLogFilter, search: String): String = when {
    search.isNotBlank() -> stringResource(R.string.log_empty_search, search)
    filter == RethinkLogFilter.Blocked -> stringResource(R.string.log_empty_blocked)
    filter == RethinkLogFilter.Allowed -> stringResource(R.string.log_empty_allowed)
    else -> stringResource(R.string.lbl_no_logs)
}

@Composable
private fun <T : Any> LogsPagedListContent(
    items: LazyPagingItems<T>,
    listState: LazyListState,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    rowContent: @Composable (item: T, index: Int, itemCount: Int) -> Unit
) {
    val refreshState = items.loadState.refresh
    val itemCount = items.itemCount
    val isLoading = refreshState is LoadState.Loading && itemCount == 0
    val isEmpty = refreshState is LoadState.NotLoading && itemCount == 0
    val hasLoadError = refreshState is LoadState.Error && itemCount == 0
    val density = LocalDensity.current
    val navBarBottomInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }

    val listContentState = when {
        isLoading -> RethinkPagedLogListState.Loading
        isEmpty -> RethinkPagedLogListState.Empty
        hasLoadError -> RethinkPagedLogListState.Error { items.retry() }
        else -> RethinkPagedLogListState.Content
    }
    RethinkPagedLogList(
        itemCount = itemCount,
        state = listContentState,
        strings = logListStrings().copy(empty = emptyMessage),
        listState = listState,
        modifier = modifier,
        bottomPadding = SharedDimensions.screenPaddingHorizontal + navBarBottomInset,
    ) { index, total ->
        val item = items[index] ?: return@RethinkPagedLogList
        rowContent(item, index, total)
    }
}

/**
 * Compact and medium widths: filters over one list whose rows expand in place. Expanded width:
 * the list on the left, the selected entry's details in a pane on the right.
 */
@Composable
private fun LogsAdaptiveLayout(
    controls: @Composable () -> Unit,
    list: @Composable (modifier: Modifier, twoPane: Boolean) -> Unit,
    pane: @Composable (modifier: Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val navBarBottomInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    RethinkAdaptiveWidth(Modifier.fillMaxSize()) { widthClass ->
        if (widthClass.isExpanded) {
            Row(Modifier.fillMaxSize().padding(end = SharedDimensions.screenPaddingHorizontal)) {
                Column(Modifier.weight(LIST_PANE_WEIGHT).fillMaxHeight()) {
                    controls()
                    list(Modifier.weight(1f), true)
                }
                pane(
                    Modifier
                        .weight(1f)
                        .padding(
                            top = SharedDimensions.spacingXs,
                            bottom = SharedDimensions.screenPaddingHorizontal + navBarBottomInset,
                        )
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                controls()
                list(Modifier.weight(1f), false)
            }
        }
    }
}

private const val LIST_PANE_WEIGHT = 1.15f

/** Wide layout: the selected connection, and where its app connects. Its fixes open in the sheet. */
@Composable
private fun ConnectionLogPane(
    selected: ConnectionTracker?,
    repository: ConnectionTrackerRepository,
    onOpenSheet: (ConnectionTracker) -> Unit,
    modifier: Modifier,
) {
    var groups by remember { mutableStateOf<List<RethinkLogGroup>>(emptyList()) }
    LaunchedEffect(selected?.uid) {
        val uid = selected?.uid ?: return@LaunchedEffect
        groups = withContext(Dispatchers.IO) { repository.getDestinationsForUid(uid) }
            .map { RethinkLogGroup(it.label, it.count, it.blocked, monospace = true) }
    }
    RethinkLogDetailPane(
        model = selected?.let { connectionLogModel(it) },
        groups = groups,
        strings = logPaneStrings(R.string.log_pane_network_hosts),
        modifier = modifier,
        onOpenSheet = selected?.let { ct -> { onOpenSheet(ct) } },
    )
}

/** Wide layout: the selected query, and the apps that looked its domain up. */
@Composable
private fun DnsLogPane(
    selected: DnsLog?,
    repository: DnsLogRepository,
    loadFavIcon: Boolean,
    onOpenSheet: (DnsLog) -> Unit,
    modifier: Modifier,
) {
    var groups by remember { mutableStateOf<List<RethinkLogGroup>>(emptyList()) }
    LaunchedEffect(selected?.queryStr) {
        val domain = selected?.queryStr ?: return@LaunchedEffect
        groups = withContext(Dispatchers.IO) { repository.getAppsQueryingDomain(domain) }
            .map { group -> RethinkLogGroup(group.label, group.count, group.blocked, icon = { AppIcon(group.packageName, appName = group.label, size = Dp.Unspecified) }) }
    }
    RethinkLogDetailPane(
        model = selected?.let { dnsLogModel(it, loadFavIcon, false, onOpenSheet) },
        groups = groups,
        strings = logPaneStrings(R.string.log_pane_dns_apps),
        modifier = modifier,
        onOpenSheet = selected?.let { log -> { onOpenSheet(log) } },
    )
}

/** The selected entry if it is loaded, else the newest one, so the pane is never empty by default. */
private fun <T : Any, K> LazyPagingItems<T>.loadedOrFirst(selectedId: K?, id: (T) -> K): T? {
    val loaded = itemSnapshotList.items
    return loaded.firstOrNull { selectedId != null && id(it) == selectedId } ?: loaded.firstOrNull()
}

private fun <T : Any, K> LazyPagingItems<T>.firstLoadedId(id: (T) -> K): K? =
    itemSnapshotList.items.firstOrNull()?.let(id)

@Composable
private fun logPaneStrings(groupsTitle: Int) = RethinkLogPaneStrings(
    groupsTitle = stringResource(groupsTitle),
    emptySelection = stringResource(R.string.log_pane_empty),
    detailsTitle = stringResource(R.string.log_pane_details),
    openSheet = stringResource(R.string.log_pane_fix),
)

/** The one log-list string set: filter chips and paging states, shared by every log screen. */
@Composable
internal fun logListStrings() = RethinkLogListStrings(
    all = stringResource(R.string.lbl_all),
    allowed = stringResource(R.string.lbl_allowed),
    blocked = stringResource(R.string.lbl_blocked),
    loading = stringResource(R.string.lbl_loading),
    empty = stringResource(R.string.lbl_no_logs),
    error = stringResource(R.string.error_loading_log_file),
    retry = stringResource(R.string.cd_refresh),
)

@Composable
private fun logAppFilterStrings(appCount: Int) = RethinkLogAppFilterStrings(
    all = stringResource(R.string.lbl_all),
    searchPlaceholder = stringResource(R.string.search_apps_count_placeholder, appCount),
    clear = stringResource(R.string.fapps_filter_clear_btn),
    clearSearchDescription = stringResource(R.string.cd_clear_search),
    dismissDescription = stringResource(R.string.lbl_dismiss),
    loading = stringResource(R.string.lbl_loading),
)

private fun List<ActivityAppCount>.toRethinkLogAppOptions() = map { app ->
    RethinkLogAppOption(
        id = app.uid.toString(),
        label = app.appName,
        count = app.count,
        iconKey = app.packageName,
    )
}

/** Filter options pair the app with a 24dp glyph-sized icon, like the "All" globe beside it. */
@Composable
private fun PlatformLogAppIcon(option: RethinkLogAppOption) {
    AppIcon(option.iconKey, appName = option.label, size = SharedDimensions.iconSizeMd)
}

@Composable
private fun PlatformLogRuleIcon(ruleId: String, tint: Color) {
    Icon(
        imageVector = firewallRuleIcon(ruleId),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(SharedDimensions.iconSizeSm),
    )
}

/** Why a connection was allowed or blocked: the rule's subject, in the same glyphs the settings use. */
private val firewallRuleIcons: Map<String, () -> ImageVector> by lazy {
    // Keep each vector lazy: initializing the dispatch table must not inflate every glyph.
    mapOf(
        FirewallRuleset.RULE0.id to { MaterialSymbols.Filled.CheckCircle },
        FirewallRuleset.RULE1.id to { MaterialSymbols.Filled.DoNotDisturbOn },
        FirewallRuleset.RULE1C.id to { MaterialSymbols.Filled.DoNotDisturbOn },
        FirewallRuleset.RULE1B.id to { MaterialSymbols.Filled.NewReleases },
        FirewallRuleset.RULE1D.id to { MaterialSymbols.Filled.WifiOff },
        FirewallRuleset.RULE1E.id to { MaterialSymbols.Filled.MobiledataOff },
        FirewallRuleset.RULE1F.id to { MaterialSymbols.Filled.CellTower },
        FirewallRuleset.RULE1G.id to { MaterialSymbols.Filled.Verified },
        FirewallRuleset.RULE2B.id to { MaterialSymbols.Filled.Verified },
        FirewallRuleset.RULE2F.id to { MaterialSymbols.Filled.Verified },
        FirewallRuleset.RULE2I.id to { MaterialSymbols.Filled.Verified },
        FirewallRuleset.RULE1H.id to { KeepoutIcons.FirewallOff },
        FirewallRuleset.RULE2.id to { MaterialSymbols.Filled.Pin },
        FirewallRuleset.RULE2C.id to { MaterialSymbols.Filled.PublicOff },
        FirewallRuleset.RULE8.id to { MaterialSymbols.Filled.PublicOff },
        FirewallRuleset.RULE2D.id to { MaterialSymbols.Filled.Public },
        FirewallRuleset.RULE2H.id to { MaterialSymbols.Filled.Public },
        FirewallRuleset.RULE2E.id to { MaterialSymbols.Filled.Link },
        FirewallRuleset.RULE3.id to { MaterialSymbols.Filled.ScreenLockPortrait },
        FirewallRuleset.RULE4.id to { MaterialSymbols.Filled.VisibilityOff },
        FirewallRuleset.RULE5.id to { MaterialSymbols.AutoMirrored.Filled.HelpOutline },
        FirewallRuleset.RULE6.id to { MaterialSymbols.Filled.SettingsEthernet },
        FirewallRuleset.RULE7.id to { MaterialSymbols.Filled.Shield },
        FirewallRuleset.RULE9B.id to { MaterialSymbols.Filled.ConversionPath },
        FirewallRuleset.RULE12.id to { MaterialSymbols.Filled.ConversionPath },
        FirewallRuleset.RULE15.id to { MaterialSymbols.Filled.ConversionPath },
        FirewallRuleset.RULE10.id to { MaterialSymbols.Filled.Http },
        FirewallRuleset.RULE11.id to { MaterialSymbols.Filled.Lock },
        FirewallRuleset.RULE17.id to { MaterialSymbols.Filled.Lock },
        FirewallRuleset.RULE13.id to { MaterialSymbols.Filled.Warning },
        FirewallRuleset.RULE18.id to { MaterialSymbols.Filled.Warning },
        FirewallRuleset.RULE16.id to { MaterialSymbols.Filled.PauseCircle },
        FirewallRuleset.RULE19.id to { MaterialSymbols.Filled.Timer },
    )
}

private fun firewallRuleIcon(ruleId: String?): ImageVector =
    firewallRuleIcons[ruleId]?.invoke() ?: MaterialSymbols.Filled.Dns

@Composable
private fun LogsDeleteDialog(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onDelete: suspend () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refreshCompleteText = stringResource(R.string.refresh_complete)
    val failureText = stringResource(R.string.blocklist_update_check_failure)
    if (!show) return
    ConfirmClearLogsDialog(
        title = title,
        onDismiss = onDismiss,
        onConfirm = {
            onDismiss()
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { onDelete() }
                    onRefresh()
                    Utilities.showToastUiCentered(context, refreshCompleteText, Toast.LENGTH_SHORT)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: android.database.SQLException) {
                    Logger.e(Logger.LOG_TAG_UI, "Failed to clear logs", error)
                    Utilities.showToastUiCentered(context, failureText, Toast.LENGTH_LONG)
                } catch (error: IllegalStateException) {
                    Logger.e(Logger.LOG_TAG_UI, "Log database unavailable", error)
                    Utilities.showToastUiCentered(context, failureText, Toast.LENGTH_LONG)
                }
            }
        }
    )
}

@Composable
private fun ConfirmClearLogsDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = title,
        message = stringResource(R.string.conn_track_clear_logs_message),
        confirmText = stringResource(R.string.lbl_delete),
        dismissText = stringResource(R.string.lbl_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isConfirmDestructive = true
    )
}

/** Logging is off: explain why the list is empty and offer to turn it back on in place. */
@Composable
private fun LogsDisabled(onEnable: () -> Unit) {
    RethinkLogsDisabledState(
        message = stringResource(R.string.logs_disabled_message),
        title = stringResource(R.string.logs_disabled_title),
        actionLabel = stringResource(R.string.logs_enable_action),
        onEnable = onEnable,
    )
}
