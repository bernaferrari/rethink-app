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
package com.celzero.bravedns.ui.compose.logs

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.components.ConnectionRow
import com.celzero.bravedns.ui.components.DnsLogRow
import com.celzero.bravedns.database.ConnectionTracker
import com.celzero.bravedns.database.ConnectionTrackerRepository
import com.celzero.bravedns.database.DnsLog
import com.celzero.bravedns.database.DnsLogRepository
import com.celzero.bravedns.database.LogAppCount
import com.celzero.bravedns.database.RethinkLogRepository
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallRuleset
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkBottomSheetCard
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.cardPositionFor
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.celzero.bravedns.ui.compose.theme.RethinkSearchField
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.ConnectionTrackerViewModel
import com.celzero.bravedns.viewmodel.DnsLogViewModel
import com.celzero.bravedns.viewmodel.RethinkLogViewModel
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
    rethinkLogViewModel: RethinkLogViewModel,
    connectionTrackerRepository: ConnectionTrackerRepository,
    dnsLogRepository: DnsLogRepository,
    rethinkLogRepository: RethinkLogRepository,
    persistentState: PersistentState,
    eventLogger: EventLogger,
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

    LaunchedEffect(refreshAction, clearAction, persistentState.logsEnabled, items.itemCount) {
        onTopBarActionsChange(
            refreshAction.takeIf { persistentState.logsEnabled },
            clearAction.takeIf { persistentState.logsEnabled && items.itemCount > 0 }
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

    if (!persistentState.logsEnabled) {
        RethinkLogsDisabledState(stringResource(R.string.logs_disabled_summary))
        return
    }

    val ruleFilters = when (parentFilter) {
        ConnectionTrackerViewModel.TopLevelFilter.BLOCKED -> FirewallRuleset.getBlockedRules()
        ConnectionTrackerViewModel.TopLevelFilter.ALLOWED -> FirewallRuleset.getAllowedRules()
        ConnectionTrackerViewModel.TopLevelFilter.ALL -> FirewallRuleset.entries.toList()
    }
    val hasRulesFilter = ruleFilters.isNotEmpty()

    LaunchedEffect(showAppFilterDialog, parentFilter, childFilters, persistentState.logsEnabled) {
        if (!showAppFilterDialog || !persistentState.logsEnabled) return@LaunchedEffect
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            RethinkLogsControlsDeck {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RethinkLogFilterRow(
                        selected = parentFilter.toRethinkLogFilter(),
                        strings = logFilterStrings(),
                        onSelected = { filterType ->
                            parentFilter = filterType.toConnectionFilter()
                            childFilters = emptySet()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    RethinkLogCompactIconAction(
                        icon = Icons.Filled.FilterList,
                        contentDescription = stringResource(R.string.lbl_rules),
                        selected = childFilters.isNotEmpty(),
                        enabled = hasRulesFilter,
                        count = childFilters.size,
                        onClick = { showRulesDialog = true }
                    )

                    RethinkLogCompactIconAction(
                        icon = Icons.Filled.Apps,
                        contentDescription = stringResource(R.string.lbl_apps),
                        selected = selectedAppFilter != null,
                        enabled = true,
                        onClick = { openAppFilterAction() }
                    )
                }
            }

            LogsPagedListContent(
                items = items,
                listState = listState,
                modifier = Modifier.weight(1f)
            ) { item, index, itemCount ->
                ConnectionRow(
                    ct = item,
                    index = index,
                    itemCount = itemCount
                )
            }
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

    LaunchedEffect(refreshAction, clearAction, persistentState.logsEnabled, items.itemCount) {
        onTopBarActionsChange(
            refreshAction.takeIf { persistentState.logsEnabled },
            clearAction.takeIf { persistentState.logsEnabled && items.itemCount > 0 }
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

    if (!persistentState.logsEnabled) {
        RethinkLogsDisabledState(stringResource(R.string.logs_disabled_summary))
        return
    }

    LaunchedEffect(showAppFilterDialog, filterType, persistentState.logsEnabled) {
        if (!showAppFilterDialog || !persistentState.logsEnabled) return@LaunchedEffect
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

    Column(modifier = Modifier.fillMaxSize()) {
        RethinkLogsControlsDeck {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RethinkLogFilterRow(
                    selected = filterType.toRethinkLogFilter(),
                    strings = logFilterStrings(),
                    onSelected = { selectedFilter ->
                        filterType = selectedFilter.toDnsLogFilter()
                    },
                    modifier = Modifier.weight(1f)
                )

                RethinkLogCompactIconAction(
                    icon = Icons.Filled.Apps,
                    contentDescription = stringResource(R.string.lbl_apps),
                    selected = selectedAppFilter != null,
                    enabled = true,
                    onClick = { showAppFilterDialog = true }
                )
            }
        }

        LogsPagedListContent(
            items = items,
            listState = listState,
            modifier = Modifier.weight(1f)
        ) { item, index, itemCount ->
            DnsLogRow(
                log = item,
                loadFavIcon = persistentState.fetchFavIcon,
                isRethinkDns = false,
                onShowBlocklist = onShowDnsLog,
                index = index,
                itemCount = itemCount,
            )
        }
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
        strings = logPagingStrings(),
        listState = listState,
        modifier = modifier,
        bottomPadding = Dimensions.screenPaddingHorizontal + navBarBottomInset,
    ) { index, total ->
        val item = items[index] ?: return@RethinkPagedLogList
        rowContent(item, index, total)
    }
}

@Composable
private fun logFilterStrings() = RethinkLogListStrings(
    all = stringResource(R.string.lbl_all),
    allowed = stringResource(R.string.lbl_allowed),
    blocked = stringResource(R.string.lbl_blocked),
    loading = "",
    empty = "",
    error = "",
    retry = "",
)

@Composable
private fun logPagingStrings() = RethinkLogListStrings(
    all = "",
    allowed = "",
    blocked = "",
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
            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)),
        )
    }
}

@Composable
private fun PlatformLogRuleIcon(ruleId: String, tint: Color) {
    Icon(
        painter = painterResource(FirewallRuleset.getRulesIcon(ruleId)),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(18.dp),
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
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp)),
                )
            }
        },
    )
}
