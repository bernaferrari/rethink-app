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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.bernaferrari.bravedns.ui.compose.logs.ConnectionRow
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkToolbarRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkToolbarIconToggle
import com.bernaferrari.bravedns.ui.compose.dns.DnsLogRow
import com.bernaferrari.bravedns.database.ConnectionTracker
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.database.DnsLog
import com.bernaferrari.bravedns.database.DnsLogRepository
import com.bernaferrari.bravedns.database.LogAppCount
import com.bernaferrari.bravedns.service.FirewallRuleset
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkOverflowItem
import com.bernaferrari.bravedns.ui.compose.common.AppIcon
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.icons.keepout.KeepoutIcons
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.viewmodel.ConnectionTrackerViewModel
import com.bernaferrari.bravedns.viewmodel.DnsLogViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LogTab { CONNECTION, DNS }

/**
 * The log: connections or DNS lookups, filtered by verdict, reason and app. Every row opens the
 * Connection sheet (why it was allowed or blocked, and the fix); at Expanded width rows select into
 * a pane that opens the same sheet.
 *
 * As the Activity tab's Log view the host passes [viewSwitch] (Log · Insights) and a null
 * [onBackClick]. Pushed with a [query] (e.g. from App detail or Insights) it lists only matching
 * entries, titled by the query.
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NetworkLogsScreen(
    connectionTrackerViewModel: ConnectionTrackerViewModel,
    dnsLogViewModel: DnsLogViewModel,
    connectionTrackerRepository: ConnectionTrackerRepository,
    dnsLogRepository: DnsLogRepository,
    persistentState: PersistentState,
    eventLogger: EventLogger,
    onAppLogsClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    sheetNavigation: ConnectionSheetNavigation = ConnectionSheetNavigation(),
    query: String = "",
    title: String? = null,
    viewSwitch: (@Composable () -> Unit)? = null,
) {
    var sheetTarget by remember { mutableStateOf<LogSheetTarget?>(null) }
    val openSheet: (LogSheetTarget) -> Unit = { sheetTarget = it }
    RethinkLogsScreenShell(
        strings = RethinkLogsStrings(
            title = title ?: query.ifBlank { stringResource(R.string.lbl_logs) },
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
        networkContent = { onToolbarActionsChange ->
            ConnectionLogsContent(
                viewModel = connectionTrackerViewModel,
                repository = connectionTrackerRepository,
                persistentState = persistentState,
                query = query,
                onOpenSheet = { openSheet(LogSheetTarget.Connection(it)) },
                onTopBarActionsChange = { refresh, clear ->
                    onToolbarActionsChange(RethinkLogToolbarActions(refresh, clear))
                },
            )
        },
        dnsContent = { onToolbarActionsChange ->
            DnsLogsContent(
                viewModel = dnsLogViewModel,
                repository = dnsLogRepository,
                persistentState = persistentState,
                query = query,
                onTopBarActionsChange = { refresh, clear ->
                    onToolbarActionsChange(RethinkLogToolbarActions(refresh, clear))
                },
                onOpenSheet = { openSheet(LogSheetTarget.Lookup(it)) },
            )
        },
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

@OptIn(FlowPreview::class)
@Composable
private fun ConnectionLogsContent(
    viewModel: ConnectionTrackerViewModel,
    repository: ConnectionTrackerRepository,
    persistentState: PersistentState,
    query: String,
    onOpenSheet: (ConnectionTracker) -> Unit,
    onTopBarActionsChange: (refreshAction: (() -> Unit)?, clearAction: (() -> Unit)?) -> Unit
) {
    // Local mirror of the pref so the inline "Enable logs" action recomposes immediately.
    var logsEnabled by remember { mutableStateOf(persistentState.logsEnabled) }
    val logsFlow = remember(viewModel) { viewModel.connectionTrackerList }
    val items = logsFlow.collectAsLazyPagingItems()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showAppFilterDialog by remember { mutableStateOf(false) }
    // Filters survive switching tabs and views (saveable), not only recomposition.
    var selectedAppFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var appPickerQuery by remember { mutableStateOf("") }
    var appFilterOptions by remember { mutableStateOf<List<LogAppCount>>(emptyList()) }
    var appFilterOptionsLoading by remember { mutableStateOf(false) }
    var parentFilter by rememberSaveable { mutableStateOf(ConnectionTrackerViewModel.TopLevelFilter.ALL) }
    var childFilters by rememberSaveable { mutableStateOf(emptyList<String>()) }

    val refreshAction = remember(items) { { items.refresh() } }
    val clearAction = remember { { showDeleteDialog = true } }
    val openAppFilterAction = remember { { showAppFilterDialog = true } }

    BackHandler(enabled = selectedAppFilter != null && !showDeleteDialog && !showRulesDialog && !showAppFilterDialog) {
        selectedAppFilter = null
    }

    LaunchedEffect(refreshAction, clearAction, logsEnabled, items.itemCount) {
        onTopBarActionsChange(
            refreshAction.takeIf { logsEnabled },
            clearAction.takeIf { logsEnabled && items.itemCount > 0 }
        )
    }

    LaunchedEffect(Unit) {
        // The view model is shared by every log screen: re-apply this screen's filters on entry.
        viewModel.setFilter(selectedAppFilter ?: query, childFilters.toSet(), parentFilter)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { Triple(selectedAppFilter ?: query, parentFilter, childFilters) }
            .debounce(300)
            .distinctUntilChanged()
            .collect { (q, type, filters) ->
                viewModel.setFilter(q, filters.toSet(), type)
            }
    }

    if (!logsEnabled) {
        LogsDisabled {
            persistentState.logsEnabled = true
            logsEnabled = true
        }
        return
    }

    val ruleFilters = when (parentFilter) {
        ConnectionTrackerViewModel.TopLevelFilter.BLOCKED -> FirewallRuleset.getBlockedRules()
        ConnectionTrackerViewModel.TopLevelFilter.ALLOWED -> FirewallRuleset.getAllowedRules()
        ConnectionTrackerViewModel.TopLevelFilter.ALL -> FirewallRuleset.entries.toList()
    }
    val hasRulesFilter = ruleFilters.isNotEmpty()

    LaunchedEffect(showAppFilterDialog, parentFilter, childFilters, logsEnabled) {
        if (!showAppFilterDialog || !logsEnabled) return@LaunchedEffect
        appFilterOptionsLoading = true
        val rules = childFilters.toSet()
        appFilterOptions =
            withContext(Dispatchers.IO) {
                when (parentFilter) {
                    ConnectionTrackerViewModel.TopLevelFilter.ALL ->
                        repository.getAllLoggedAppsWithCount(rules)
                    ConnectionTrackerViewModel.TopLevelFilter.ALLOWED ->
                        repository.getAllowedLoggedAppsWithCount(rules)
                    ConnectionTrackerViewModel.TopLevelFilter.BLOCKED ->
                        repository.getBlockedLoggedAppsWithCount(rules)
                }
            }
        appFilterOptionsLoading = false
    }

    val listState = rememberLazyListState()

    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LogsAdaptiveLayout(
            controls = {
                RethinkLogsControlsDeck {
                    RethinkToolbarRow {
                        RethinkLogFilterRow(
                            selected = parentFilter.toRethinkLogFilter(),
                            strings = logListStrings(),
                            onSelected = { filterType ->
                                parentFilter = filterType.toConnectionFilter()
                                childFilters = emptyList()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        RethinkToolbarIconToggle(
                            icon = MaterialSymbols.Filled.FilterList,
                            contentDescription = stringResource(R.string.log_filter_reason),
                            selected = childFilters.isNotEmpty(),
                            enabled = hasRulesFilter,
                            count = childFilters.size,
                            onClick = { showRulesDialog = true }
                        )

                        RethinkToolbarIconToggle(
                            icon = MaterialSymbols.Filled.Apps,
                            contentDescription = stringResource(R.string.log_filter_app),
                            selected = selectedAppFilter != null,
                            enabled = true,
                            onClick = { openAppFilterAction() }
                        )
                    }
                }
            },
            list = { listModifier, twoPane ->
                LogsPagedListContent(
                    items = items,
                    listState = listState,
                    emptyMessage = emptyLogMessage(parentFilter.toRethinkLogFilter(), selectedAppFilter ?: query),
                    modifier = listModifier
                ) { item, index, itemCount ->
                    val selected = twoPane && item.id.toLong() == (selectedId ?: items.firstLoadedId { it.id.toLong() })
                    ConnectionRow(
                        ct = item,
                        index = index,
                        itemCount = itemCount,
                        selected = selected,
                        onSelect = if (twoPane) ({ selectedId = item.id.toLong() }) else ({ onOpenSheet(item) }),
                    )
                }
            },
            pane = { paneModifier ->
                ConnectionLogPane(items.loadedOrFirst(selectedId) { it.id.toLong() }, repository, onOpenSheet, paneModifier)
            },
        )

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
                selectedIds = childFilters.toSet(),
                strings = RethinkLogRulesStrings(
                    title = stringResource(R.string.log_filter_reason),
                    clear = stringResource(R.string.fapps_filter_clear_btn),
                    dismissDescription = stringResource(R.string.lbl_dismiss),
                ),
                onToggle = { ruleId ->
                    childFilters =
                        if (childFilters.contains(ruleId)) childFilters - ruleId
                        else childFilters + ruleId
                },
                onClear = { childFilters = emptyList() },
                onDismiss = { showRulesDialog = false }
            )
        }

        if (showAppFilterDialog) {
            RethinkLogAppFilterDialog(
                options = appFilterOptions.toRethinkLogAppOptions(),
                selectedId = selectedAppFilter,
                searchQuery = appPickerQuery,
                isLoading = appFilterOptionsLoading,
                strings = logAppFilterStrings(appFilterOptions.size),
                onSearchQueryChange = { appPickerQuery = it },
                onSelect = { selectedApp ->
                    selectedAppFilter = selectedApp
                    showAppFilterDialog = false
                },
                onClearSelection = { selectedAppFilter = null },
                onDismiss = { showAppFilterDialog = false },
                appIcon = { option -> PlatformLogAppIcon(option) },
            )
        }
    }

    LogsDeleteDialog(
        show = showDeleteDialog,
        onDismiss = { showDeleteDialog = false },
        onDelete = { repository.clearAllData() },
        onRefresh = { items.refresh() }
    )
}

@OptIn(FlowPreview::class)
@Composable
private fun DnsLogsContent(
    viewModel: DnsLogViewModel,
    repository: DnsLogRepository,
    persistentState: PersistentState,
    query: String,
    onTopBarActionsChange: (refreshAction: (() -> Unit)?, clearAction: (() -> Unit)?) -> Unit,
    onOpenSheet: (DnsLog) -> Unit
) {
    // Local mirror of the pref so the inline "Enable logs" action recomposes immediately.
    var logsEnabled by remember { mutableStateOf(persistentState.logsEnabled) }
    val logsFlow = remember(viewModel) { viewModel.dnsLogsList }
    val items = logsFlow.collectAsLazyPagingItems()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAppFilterDialog by remember { mutableStateOf(false) }
    var selectedAppFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var appPickerQuery by remember { mutableStateOf("") }
    var appFilterOptions by remember { mutableStateOf<List<LogAppCount>>(emptyList()) }
    var appFilterOptionsLoading by remember { mutableStateOf(false) }
    var filterType by rememberSaveable { mutableStateOf(DnsLogViewModel.DnsLogFilter.ALL) }

    val refreshAction = remember(items) { { items.refresh() } }
    val clearAction = remember { { showDeleteDialog = true } }

    BackHandler(enabled = selectedAppFilter != null && !showDeleteDialog && !showAppFilterDialog) {
        selectedAppFilter = null
    }

    LaunchedEffect(refreshAction, clearAction, logsEnabled, items.itemCount) {
        onTopBarActionsChange(
            refreshAction.takeIf { logsEnabled },
            clearAction.takeIf { logsEnabled && items.itemCount > 0 }
        )
    }

    LaunchedEffect(Unit) {
        // The view model is shared by every log screen: re-apply this screen's filters on entry.
        viewModel.setFilter(selectedAppFilter ?: query, filterType)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { Pair(selectedAppFilter ?: query, filterType) }
            .debounce(300)
            .distinctUntilChanged()
            .collect { (q, type) ->
                viewModel.setFilter(q, type)
            }
    }

    if (!logsEnabled) {
        LogsDisabled {
            persistentState.logsEnabled = true
            logsEnabled = true
        }
        return
    }

    LaunchedEffect(showAppFilterDialog, filterType, logsEnabled) {
        if (!showAppFilterDialog || !logsEnabled) return@LaunchedEffect
        appFilterOptionsLoading = true
        appFilterOptions =
            withContext(Dispatchers.IO) { repository.loggedAppsFor(filterType) }
        appFilterOptionsLoading = false
    }

    val listState = rememberLazyListState()

    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    LogsAdaptiveLayout(
        controls = {
            DnsLogControls(
                filter = filterType.toRethinkLogFilter(),
                onFilter = { filterType = it.toDnsLogFilter() },
                appFiltered = selectedAppFilter != null,
                onApps = { showAppFilterDialog = true },
            )
        },
        list = { listModifier, twoPane ->
            LogsPagedListContent(
                items = items,
                listState = listState,
                emptyMessage = emptyLogMessage(filterType.toRethinkLogFilter(), selectedAppFilter ?: query),
                modifier = listModifier
            ) { item, index, itemCount ->
                DnsLogRow(
                    log = item,
                    loadFavIcon = persistentState.fetchFavIcon,
                    isRethinkDns = false,
                    onShowBlocklist = onOpenSheet,
                    index = index,
                    itemCount = itemCount,
                    selected = twoPane && item.id.toLong() == (selectedId ?: items.firstLoadedId { it.id.toLong() }),
                    onSelect = if (twoPane) ({ selectedId = item.id.toLong() }) else ({ onOpenSheet(item) }),
                )
            }
        },
        pane = { paneModifier ->
            DnsLogPane(items.loadedOrFirst(selectedId) { it.id.toLong() }, repository, persistentState.fetchFavIcon, onOpenSheet, paneModifier)
        },
    )

    if (showAppFilterDialog) {
        RethinkLogAppFilterDialog(
            options = appFilterOptions.toRethinkLogAppOptions(),
            selectedId = selectedAppFilter,
            searchQuery = appPickerQuery,
            isLoading = appFilterOptionsLoading,
            strings = logAppFilterStrings(appFilterOptions.size),
            onSearchQueryChange = { appPickerQuery = it },
            onSelect = { selectedApp ->
                selectedAppFilter = selectedApp
                showAppFilterDialog = false
            },
            onClearSelection = { selectedAppFilter = null },
            onDismiss = { showAppFilterDialog = false },
            appIcon = { option -> PlatformLogAppIcon(option) },
        )
    }

    LogsDeleteDialog(
        show = showDeleteDialog,
        onDismiss = { showDeleteDialog = false },
        onDelete = { repository.clearAllData() },
        onRefresh = { items.refresh() }
    )
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

private suspend fun DnsLogRepository.loggedAppsFor(filter: DnsLogViewModel.DnsLogFilter): List<LogAppCount> =
    when (filter) {
        DnsLogViewModel.DnsLogFilter.ALLOWED -> getAllowedLoggedAppsWithCount()
        DnsLogViewModel.DnsLogFilter.BLOCKED -> getBlockedLoggedAppsWithCount()
        else -> getAllLoggedAppsWithCount()
    }

@Composable
private fun DnsLogControls(filter: RethinkLogFilter, onFilter: (RethinkLogFilter) -> Unit, appFiltered: Boolean, onApps: () -> Unit) {
    RethinkLogsControlsDeck {
        RethinkToolbarRow {
            RethinkLogFilterRow(
                selected = filter,
                strings = logListStrings(),
                onSelected = onFilter,
                modifier = Modifier.weight(1f)
            )
            RethinkToolbarIconToggle(
                icon = MaterialSymbols.Filled.Apps,
                contentDescription = stringResource(R.string.log_filter_app),
                selected = appFiltered,
                enabled = true,
                onClick = onApps,
            )
        }
    }
}

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

private fun List<LogAppCount>.toRethinkLogAppOptions() = map { app ->
    RethinkLogAppOption(
        id = app.appName,
        label = app.appName,
        count = app.count,
        iconKey = app.packageName,
    )
}

private fun ConnectionTrackerViewModel.TopLevelFilter.toRethinkLogFilter() = when (this) {
    ConnectionTrackerViewModel.TopLevelFilter.ALL -> RethinkLogFilter.All
    ConnectionTrackerViewModel.TopLevelFilter.ALLOWED -> RethinkLogFilter.Allowed
    ConnectionTrackerViewModel.TopLevelFilter.BLOCKED -> RethinkLogFilter.Blocked
}

private fun RethinkLogFilter.toConnectionFilter() = when (this) {
    RethinkLogFilter.All -> ConnectionTrackerViewModel.TopLevelFilter.ALL
    RethinkLogFilter.Allowed -> ConnectionTrackerViewModel.TopLevelFilter.ALLOWED
    RethinkLogFilter.Blocked -> ConnectionTrackerViewModel.TopLevelFilter.BLOCKED
}

private fun DnsLogViewModel.DnsLogFilter.toRethinkLogFilter() = when (this) {
    DnsLogViewModel.DnsLogFilter.ALL -> RethinkLogFilter.All
    DnsLogViewModel.DnsLogFilter.ALLOWED -> RethinkLogFilter.Allowed
    DnsLogViewModel.DnsLogFilter.BLOCKED -> RethinkLogFilter.Blocked
    else -> RethinkLogFilter.All
}

private fun RethinkLogFilter.toDnsLogFilter() = when (this) {
    RethinkLogFilter.All -> DnsLogViewModel.DnsLogFilter.ALL
    RethinkLogFilter.Allowed -> DnsLogViewModel.DnsLogFilter.ALLOWED
    RethinkLogFilter.Blocked -> DnsLogViewModel.DnsLogFilter.BLOCKED
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
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.conn_track_clear_logs_title),
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
