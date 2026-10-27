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

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import com.bernaferrari.bravedns.ui.compose.common.RethinkAdaptiveWidth
import com.bernaferrari.bravedns.ui.compose.common.isExpanded
import com.bernaferrari.bravedns.ui.compose.dns.dnsLogModel
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.compose.logs.ConnectionRow
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkToolbarRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkToolbarIconToggle
import com.bernaferrari.bravedns.ui.compose.dns.DnsLogRow
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.database.DnsLog
import com.bernaferrari.bravedns.database.DnsLogRepository
import com.bernaferrari.bravedns.database.LogAppCount
import com.bernaferrari.bravedns.service.FirewallRuleset
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.rememberDrawablePainter
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
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

private data class LogsTabSpec(
    val tab: LogTab,
    val title: Int
)

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NetworkLogsScreen(
    connectionTrackerViewModel: ConnectionTrackerViewModel,
    dnsLogViewModel: DnsLogViewModel,
    connectionTrackerRepository: ConnectionTrackerRepository,
    dnsLogRepository: DnsLogRepository,
    persistentState: PersistentState,
    onBackClick: (() -> Unit)? = null
) {
    var selectedDns by remember { mutableStateOf<DnsLog?>(null) }
    RethinkLogsScreenShell(
        strings = RethinkLogsStrings(
            title = stringResource(R.string.lbl_logs),
            network = stringResource(R.string.firewall_act_network_monitor_tab),
            dns = stringResource(R.string.dns_mode_info_title),
            moreActions = stringResource(R.string.wireguard_fab_more_actions),
            refresh = stringResource(R.string.cd_refresh),
            clear = stringResource(R.string.fapps_filter_clear_btn),
        ),
        onBackClick = onBackClick,
        networkContent = { onToolbarActionsChange ->
            ConnectionLogsContent(
                viewModel = connectionTrackerViewModel,
                repository = connectionTrackerRepository,
                persistentState = persistentState,
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
                onTopBarActionsChange = { refresh, clear ->
                    onToolbarActionsChange(RethinkLogToolbarActions(refresh, clear))
                },
                onShowDnsLog = { selectedDns = it },
            )
        },
    )

    if (selectedDns != null) {
        DnsLogDetailsSheet(
            log = selectedDns!!,
            onDismiss = { selectedDns = null }
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun ConnectionLogsContent(
    viewModel: ConnectionTrackerViewModel,
    repository: ConnectionTrackerRepository,
    persistentState: PersistentState,
    onTopBarActionsChange: (refreshAction: (() -> Unit)?, clearAction: (() -> Unit)?) -> Unit
) {
    // Local mirror of the pref so the inline "Enable logs" action recomposes immediately.
    var logsEnabled by remember { mutableStateOf(persistentState.logsEnabled) }
    val logsFlow = remember(viewModel) { viewModel.connectionTrackerList }
    val items = logsFlow.collectAsLazyPagingItems()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showAppFilterDialog by remember { mutableStateOf(false) }
    var selectedAppFilter by remember { mutableStateOf<String?>(null) }
    var appPickerQuery by remember { mutableStateOf("") }
    var appFilterOptions by remember { mutableStateOf<List<LogAppCount>>(emptyList()) }
    var appFilterOptionsLoading by remember { mutableStateOf(false) }
    var parentFilter by remember { mutableStateOf(ConnectionTrackerViewModel.TopLevelFilter.ALL) }
    var childFilters by remember { mutableStateOf(setOf<String>()) }

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
        // Reset stale filters when re-entering the screen to avoid empty/hidden results.
        viewModel.setFilter("", emptySet(), ConnectionTrackerViewModel.TopLevelFilter.ALL)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { Triple(selectedAppFilter.orEmpty(), parentFilter, childFilters) }
            .debounce(300)
            .distinctUntilChanged()
            .collect { (q, type, filters) ->
                viewModel.setFilter(q, filters, type)
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
        appFilterOptions =
            withContext(Dispatchers.IO) {
                when (parentFilter) {
                    ConnectionTrackerViewModel.TopLevelFilter.ALL ->
                        repository.getAllLoggedAppsWithCount(childFilters)
                    ConnectionTrackerViewModel.TopLevelFilter.ALLOWED ->
                        repository.getAllowedLoggedAppsWithCount(childFilters)
                    ConnectionTrackerViewModel.TopLevelFilter.BLOCKED ->
                        repository.getBlockedLoggedAppsWithCount(childFilters)
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
                                childFilters = emptySet()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        RethinkToolbarIconToggle(
                            icon = MaterialSymbols.Filled.FilterList,
                            contentDescription = stringResource(R.string.lbl_rules),
                            selected = childFilters.isNotEmpty(),
                            enabled = hasRulesFilter,
                            count = childFilters.size,
                            onClick = { showRulesDialog = true }
                        )

                        RethinkToolbarIconToggle(
                            icon = MaterialSymbols.Filled.Apps,
                            contentDescription = stringResource(R.string.lbl_apps),
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
                    modifier = listModifier
                ) { item, index, itemCount ->
                    val selected = twoPane && item.id.toLong() == (selectedId ?: items.firstLoadedId { it.id.toLong() })
                    ConnectionRow(
                        ct = item,
                        index = index,
                        itemCount = itemCount,
                        selected = selected,
                        onSelect = if (twoPane) ({ selectedId = item.id.toLong() }) else null,
                    )
                }
            },
            pane = { paneModifier ->
                val selected = items.loadedOrFirst(selectedId) { it.id.toLong() }
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
                    modifier = paneModifier,
                )
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
                selectedIds = childFilters,
                strings = RethinkLogRulesStrings(
                    title = stringResource(R.string.lbl_rules),
                    clear = stringResource(R.string.fapps_filter_clear_btn),
                    dismissDescription = stringResource(R.string.lbl_dismiss),
                ),
                onToggle = { ruleId ->
                    childFilters =
                        if (childFilters.contains(ruleId)) childFilters - ruleId
                        else childFilters + ruleId
                },
                onClear = { childFilters = emptySet() },
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
    onTopBarActionsChange: (refreshAction: (() -> Unit)?, clearAction: (() -> Unit)?) -> Unit,
    onShowDnsLog: (DnsLog) -> Unit
) {
    // Local mirror of the pref so the inline "Enable logs" action recomposes immediately.
    var logsEnabled by remember { mutableStateOf(persistentState.logsEnabled) }
    val logsFlow = remember(viewModel) { viewModel.dnsLogsList }
    val items = logsFlow.collectAsLazyPagingItems()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAppFilterDialog by remember { mutableStateOf(false) }
    var selectedAppFilter by remember { mutableStateOf<String?>(null) }
    var appPickerQuery by remember { mutableStateOf("") }
    var appFilterOptions by remember { mutableStateOf<List<LogAppCount>>(emptyList()) }
    var appFilterOptionsLoading by remember { mutableStateOf(false) }
    var filterType by remember { mutableStateOf(DnsLogViewModel.DnsLogFilter.ALL) }

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
        // Reset stale filters when re-entering the screen to avoid empty/hidden results.
        viewModel.setFilter("", DnsLogViewModel.DnsLogFilter.ALL)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { Pair(selectedAppFilter.orEmpty(), filterType) }
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
            withContext(Dispatchers.IO) {
                when (filterType) {
                    DnsLogViewModel.DnsLogFilter.ALL -> repository.getAllLoggedAppsWithCount()
                    DnsLogViewModel.DnsLogFilter.ALLOWED -> repository.getAllowedLoggedAppsWithCount()
                    DnsLogViewModel.DnsLogFilter.BLOCKED -> repository.getBlockedLoggedAppsWithCount()
                    else -> repository.getAllLoggedAppsWithCount()
                }
            }
        appFilterOptionsLoading = false
    }

    val listState = rememberLazyListState()

    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    LogsAdaptiveLayout(
        controls = {
            RethinkLogsControlsDeck {
                RethinkToolbarRow {
                    RethinkLogFilterRow(
                        selected = filterType.toRethinkLogFilter(),
                        strings = logListStrings(),
                        onSelected = { selectedFilter ->
                            filterType = selectedFilter.toDnsLogFilter()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    RethinkToolbarIconToggle(
                        icon = MaterialSymbols.Filled.Apps,
                        contentDescription = stringResource(R.string.lbl_apps),
                        selected = selectedAppFilter != null,
                        enabled = true,
                        onClick = { showAppFilterDialog = true }
                    )
                }
            }
        },
        list = { listModifier, twoPane ->
            LogsPagedListContent(
                items = items,
                listState = listState,
                modifier = listModifier
            ) { item, index, itemCount ->
                DnsLogRow(
                    log = item,
                    loadFavIcon = persistentState.fetchFavIcon,
                    isRethinkDns = false,
                    onShowBlocklist = onShowDnsLog,
                    index = index,
                    itemCount = itemCount,
                    selected = twoPane && item.id.toLong() == (selectedId ?: items.firstLoadedId { it.id.toLong() }),
                    onSelect = if (twoPane) ({ selectedId = item.id.toLong() }) else null,
                )
            }
        },
        pane = { paneModifier ->
            val selected = items.loadedOrFirst(selectedId) { it.id.toLong() }
            var groups by remember { mutableStateOf<List<RethinkLogGroup>>(emptyList()) }
            LaunchedEffect(selected?.queryStr) {
                val domain = selected?.queryStr ?: return@LaunchedEffect
                groups = withContext(Dispatchers.IO) { repository.getAppsQueryingDomain(domain) }
                    .map { group -> RethinkLogGroup(group.label, group.count, group.blocked, icon = { PackageIcon(group.packageName, group.label) }) }
            }
            RethinkLogDetailPane(
                model = selected?.let { dnsLogModel(it, persistentState.fetchFavIcon, false, onShowDnsLog) },
                groups = groups,
                strings = logPaneStrings(R.string.log_pane_dns_apps),
                modifier = paneModifier,
            )
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

@Composable
private fun <T : Any> LogsPagedListContent(
    items: LazyPagingItems<T>,
    listState: LazyListState,
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
        strings = logListStrings(),
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
)

@Composable
private fun PackageIcon(packageName: String, label: String) {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(packageName) {
        icon = withContext(Dispatchers.IO) {
            if (packageName.isBlank()) Utilities.getDefaultIcon(context) else Utilities.getIcon(context, packageName, label)
        }
    }
    icon?.let { drawable ->
        rememberDrawablePainter(drawable)?.let { painter ->
            Image(painter = painter, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

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

@Composable
private fun PlatformLogAppIcon(option: RethinkLogAppOption) {
    val context = LocalContext.current
    var appIcon by remember(option.id, option.iconKey) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(option.id, option.iconKey) {
        appIcon = withContext(Dispatchers.IO) {
            if (option.iconKey.isBlank()) Utilities.getDefaultIcon(context)
            else Utilities.getIcon(context, option.iconKey, option.label)
        }
    }
    rememberDrawablePainter(appIcon ?: Utilities.getDefaultIcon(context))?.let { painter ->
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(SharedDimensions.iconSizeMd).clip(RoundedCornerShape(SharedDimensions.cornerRadiusSm)),
        )
    }
}

@Composable
private fun PlatformLogRuleIcon(ruleId: String, tint: Color) {
    Icon(
        painter = painterResource(FirewallRuleset.getRulesIcon(ruleId)),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(SharedDimensions.iconSizeSm),
    )
}

@Composable
private fun LogsDeleteDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onDelete: suspend () -> Unit,
    onRefresh: () -> Unit
) {
    if (!show) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refreshCompleteText = stringResource(R.string.refresh_complete)
    ConfirmClearLogsDialog(
        onDismiss = onDismiss,
        onConfirm = {
            onDismiss()
            scope.launch(Dispatchers.IO) { onDelete() }
            Utilities.showToastUiCentered(
                context,
                refreshCompleteText,
                Toast.LENGTH_SHORT
            )
            onRefresh()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsLogDetailsSheet(
    log: DnsLog,
    onDismiss: () -> Unit
) {
    val status = if (log.isBlocked) stringResource(R.string.lbl_blocked) else stringResource(R.string.lbl_allowed)
    val response = log.responseIps.ifEmpty { stringResource(R.string.settings_app_list_default_app) }
    LogDetailsSheet(
        title = log.queryStr,
        appPackageName = log.packageName,
        appDisplayName = log.appName,
        details = listOf(
            LogDetailEntry(stringResource(R.string.log_detail_app_name), log.appName),
            LogDetailEntry(stringResource(R.string.log_detail_response), response),
            LogDetailEntry(stringResource(R.string.dns_detail_latency), "${log.latency}ms"),
            LogDetailEntry(stringResource(R.string.lbl_status), status, isError = log.isBlocked)
        ),
        onDismiss = onDismiss
    )
}

private data class LogDetailEntry(
    val label: String,
    val value: String,
    val isError: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogDetailsSheet(
    title: String,
    appPackageName: String,
    appDisplayName: String,
    details: List<LogDetailEntry>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var appIcon by remember(appPackageName, appDisplayName) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(appPackageName, appDisplayName) {
        appIcon = kotlinx.coroutines.withContext(Dispatchers.IO) {
            if (appPackageName.isBlank()) {
                Utilities.getDefaultIcon(context)
            } else {
                Utilities.getIcon(context, appPackageName, appDisplayName)
            }
        }
    }

    RethinkLogDetailsSheet(
        title = title,
        details = details.map { RethinkLogDetailEntry(it.label, it.value, it.isError) },
        dismissLabel = stringResource(R.string.lbl_dismiss),
        onDismiss = onDismiss,
        appIcon = {
            val iconPainter = rememberDrawablePainter(appIcon ?: Utilities.getDefaultIcon(context))
            iconPainter?.let { painter ->
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier
                        .size(SharedDimensions.iconContainerSm)
                        .clip(RoundedCornerShape(SharedDimensions.cornerRadiusSmMd)),
                )
            }
        },
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
