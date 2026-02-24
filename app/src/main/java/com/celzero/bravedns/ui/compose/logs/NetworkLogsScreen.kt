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
package com.celzero.bravedns.ui.compose.logs

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.ConnectionRow
import com.celzero.bravedns.adapter.DnsLogRow
import com.celzero.bravedns.database.ConnectionTracker
import com.celzero.bravedns.database.ConnectionTrackerRepository
import com.celzero.bravedns.database.DnsLog
import com.celzero.bravedns.database.DnsLogRepository
import com.celzero.bravedns.database.RethinkLogRepository
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallRuleset
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkBottomSheetCard
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkFilterChip
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.RethinkSearchField
import com.celzero.bravedns.ui.compose.theme.RethinkSegmentedChoiceRow
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.ConnectionTrackerViewModel
import com.celzero.bravedns.viewmodel.DnsLogViewModel
import com.celzero.bravedns.viewmodel.RethinkLogViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class LogTab { CONNECTION, DNS }

private data class TabSpec(val tab: LogTab, val label: String)

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
    val tabs = listOf(
        TabSpec(LogTab.CONNECTION, stringResource(R.string.firewall_act_network_monitor_tab)),
        TabSpec(LogTab.DNS, stringResource(R.string.dns_mode_info_title))
    )
    val selectedTab = remember { mutableIntStateOf(0) }

    var selectedConn by remember { mutableStateOf<ConnectionTracker?>(null) }
    var selectedDns by remember { mutableStateOf<DnsLog?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkLargeTopBar(
                title = stringResource(R.string.lbl_logs),
                subtitle = stringResource(R.string.logs_disabled_summary),
                onBackClick = onBackClick
            )
        }
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            LogsTabRow(
                tabs = tabs,
                selectedTab = selectedTab.intValue,
                onTabSelected = { selectedTab.intValue = it }
            )

            when (tabs[selectedTab.intValue].tab) {
                LogTab.CONNECTION -> {
                    ConnectionLogsContent(
                        viewModel = connectionTrackerViewModel,
                        repository = connectionTrackerRepository,
                        persistentState = persistentState,
                        onShowConnTracker = { selectedConn = it }
                    )
                }

                LogTab.DNS -> {
                    DnsLogsContent(
                        viewModel = dnsLogViewModel,
                        repository = dnsLogRepository,
                        persistentState = persistentState,
                        onShowDnsLog = { selectedDns = it }
                    )
                }
            }
        }
    }

    if (selectedConn != null) {
        ConnTrackerDetailsSheet(
            connection = selectedConn!!,
            onDismiss = { selectedConn = null }
        )
    }

    if (selectedDns != null) {
        DnsLogDetailsSheet(
            log = selectedDns!!,
            onDismiss = { selectedDns = null }
        )
    }
}

@Composable
private fun LogsTabRow(
    tabs: List<TabSpec>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.screenPaddingHorizontal)
                .padding(top = Dimensions.spacingSm, bottom = Dimensions.spacingXs)
    ) {
        RethinkSegmentedChoiceRow(
            options = tabs.indices.toList(),
            selectedOption = selectedTab,
            onOptionSelected = onTabSelected,
            modifier = Modifier.fillMaxWidth(),
            fillEqually = true,
            label = { index, isSelected ->
                Text(
                    text = tabs[index].label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun ConnectionLogsContent(
    viewModel: ConnectionTrackerViewModel,
    repository: ConnectionTrackerRepository,
    persistentState: PersistentState,
    onShowConnTracker: (ConnectionTracker) -> Unit
) {
    val items = viewModel.connectionTrackerList.asFlow().collectAsLazyPagingItems()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var parentFilter by remember { mutableStateOf(ConnectionTrackerViewModel.TopLevelFilter.ALL) }
    var childFilters by remember { mutableStateOf(setOf<String>()) }

    val filterOptions = listOf(
        ConnectionTrackerViewModel.TopLevelFilter.ALL to stringResource(R.string.lbl_all),
        ConnectionTrackerViewModel.TopLevelFilter.ALLOWED to stringResource(R.string.lbl_allowed),
        ConnectionTrackerViewModel.TopLevelFilter.BLOCKED to stringResource(R.string.lbl_blocked)
    )

    LaunchedEffect(Unit) {
        snapshotFlow { Triple(query, parentFilter, childFilters) }
            .debounce(300)
            .distinctUntilChanged()
            .collect { (q, type, filters) ->
                viewModel.setFilter(q, filters, type)
            }
    }

    if (!persistentState.logsEnabled) {
        LogsDisabledState()
        return
    }

    val ruleFilters = when (parentFilter) {
        ConnectionTrackerViewModel.TopLevelFilter.BLOCKED -> FirewallRuleset.getBlockedRules()
        ConnectionTrackerViewModel.TopLevelFilter.ALLOWED -> FirewallRuleset.getAllowedRules()
        ConnectionTrackerViewModel.TopLevelFilter.ALL -> emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LogsFilterCard {
            LogsControls(
                query = query,
                onQueryChange = { query = it },
                onClearQuery = { query = "" },
                onRefresh = { items.refresh() },
                onDelete = { showDeleteDialog = true },
                placeholder = stringResource(R.string.logs_search_network_hint)
            )

            Spacer(modifier = Modifier.height(Dimensions.spacingSm))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
            ) {
                items(filterOptions) { (filter, label) ->
                    RethinkFilterChip(
                        label = label,
                        selected = parentFilter == filter,
                        onClick = {
                            parentFilter = filter
                            childFilters = emptySet()
                        },
                        textStyle = MaterialTheme.typography.labelMedium,
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            if (ruleFilters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimensions.spacingSm))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)
                ) {
                    items(ruleFilters) { rule ->
                        val selected = childFilters.contains(rule.id)
                        RethinkFilterChip(
                            label = stringResource(rule.title),
                            selected = selected,
                            onClick = {
                                childFilters =
                                    if (selected) childFilters - rule.id
                                    else childFilters + rule.id
                            },
                            textStyle = MaterialTheme.typography.labelSmall,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = FirewallRuleset.getRulesIcon(rule.id)),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        LogsPagedListContent(
            items = items,
            modifier = Modifier.weight(1f)
        ) { item ->
            ConnectionRow(item, onShowConnTracker)
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
    onShowDnsLog: (DnsLog) -> Unit
) {
    val items = viewModel.dnsLogsList.asFlow().collectAsLazyPagingItems()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf(DnsLogViewModel.DnsLogFilter.ALL) }

    val filterOptions = listOf(
        DnsLogViewModel.DnsLogFilter.ALL to stringResource(R.string.lbl_all),
        DnsLogViewModel.DnsLogFilter.ALLOWED to stringResource(R.string.lbl_allowed),
        DnsLogViewModel.DnsLogFilter.BLOCKED to stringResource(R.string.lbl_blocked)
    )

    LaunchedEffect(Unit) {
        snapshotFlow { Pair(query, filterType) }
            .debounce(300)
            .distinctUntilChanged()
            .collect { (q, type) ->
                viewModel.setFilter(q, type)
            }
    }

    if (!persistentState.logsEnabled) {
        LogsDisabledState()
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LogsFilterCard {
            LogsControls(
                query = query,
                onQueryChange = { query = it },
                onClearQuery = { query = "" },
                onRefresh = { items.refresh() },
                onDelete = { showDeleteDialog = true },
                placeholder = stringResource(R.string.logs_search_dns_hint)
            )

            Spacer(modifier = Modifier.height(Dimensions.spacingSm))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
            ) {
                items(filterOptions) { (filter, label) ->
                    RethinkFilterChip(
                        label = label,
                        selected = filterType == filter,
                        onClick = { filterType = filter },
                        textStyle = MaterialTheme.typography.labelMedium,
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        LogsPagedListContent(
            items = items,
            modifier = Modifier.weight(1f)
        ) { item ->
            DnsLogRow(
                log = item,
                loadFavIcon = persistentState.fetchFavIcon,
                isRethinkDns = false,
                onShowBlocklist = onShowDnsLog
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

@Composable
private fun LogsFilterCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.screenPaddingHorizontal),
        shape = RoundedCornerShape(Dimensions.cornerRadius3xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Dimensions.spacingSm, vertical = Dimensions.spacingSm),
            content = content
        )
    }
}

@Composable
private fun LogsControls(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RethinkSearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
            onClearQuery = onClearQuery,
            clearQueryContentDescription = stringResource(R.string.cd_clear_search),
            shape = RoundedCornerShape(Dimensions.cornerRadiusMdLg),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )

        FilledTonalIconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.cd_refresh)
            )
        }

        FilledTonalIconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.lbl_delete),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun LogsDisabledState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.logs_disabled_summary),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(Dimensions.spacing2xl)
        )
    }
}

@Composable
private fun <T : Any> LogsPagedListContent(
    items: LazyPagingItems<T>,
    modifier: Modifier = Modifier,
    rowContent: @Composable (T) -> Unit
) {
    val isEmpty = items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading

    Box(
        modifier = modifier.fillMaxWidth().padding(top = Dimensions.spacingSm)
    ) {
        if (isEmpty) {
            LogsEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Dimensions.screenPaddingHorizontal,
                    end = Dimensions.screenPaddingHorizontal,
                    top = Dimensions.spacingSm,
                    bottom = Dimensions.spacingLg
                ),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
            ) {
                items(items.itemCount) { index ->
                    val item = items[index] ?: return@items
                    rowContent(item)
                }
            }
        }
    }
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
private fun LogsEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(id = R.string.lbl_no_logs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(Dimensions.spacingXl)
        )
    }
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
private fun ConnTrackerDetailsSheet(
    connection: ConnectionTracker,
    onDismiss: () -> Unit
) {
    val status = if (connection.isBlocked) stringResource(R.string.lbl_blocked) else stringResource(R.string.lbl_allowed)
    LogDetailsSheet(
        title = connection.appName,
        details = listOf(
            LogDetailEntry(stringResource(R.string.log_detail_ip_address), connection.ipAddress),
            LogDetailEntry(stringResource(R.string.log_detail_port), connection.port.toString()),
            LogDetailEntry(stringResource(R.string.log_detail_protocol), connection.protocol.toString()),
            LogDetailEntry(stringResource(R.string.lbl_status), status, isError = connection.isBlocked)
        ),
        onDismiss = onDismiss
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
    details: List<LogDetailEntry>,
    onDismiss: () -> Unit
) {
    RethinkModalBottomSheet(onDismissRequest = onDismiss, includeBottomSpacer = true) {
        RethinkBottomSheetCard(
            shape = RoundedCornerShape(Dimensions.cornerRadius4xl),
            contentPadding = PaddingValues(Dimensions.spacingLg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                details.forEach { entry ->
                    DetailRow(
                        label = entry.label,
                        value = entry.value,
                        isError = entry.isError
                    )
                }

                Spacer(modifier = Modifier.height(Dimensions.spacingXl))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(text = stringResource(R.string.lbl_dismiss))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, isError: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimensions.spacingXs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}
