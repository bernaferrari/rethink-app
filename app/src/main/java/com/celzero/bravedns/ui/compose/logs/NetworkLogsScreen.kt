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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.ConnectionTrackerViewModel
import com.celzero.bravedns.viewmodel.DnsLogViewModel
import com.celzero.bravedns.viewmodel.RethinkLogViewModel
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class LogTab { CONNECTION, DNS }

data class TabSpec(val tab: LogTab, val label: String)

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
    val clearSearchContentDescription = stringResource(R.string.cd_clear_search)
    val deleteContentDescription = stringResource(R.string.lbl_delete)
    val selectedTab = remember { mutableIntStateOf(0) }
    var selectedConn by remember { mutableStateOf<ConnectionTracker?>(null) }
    var selectedDns by remember { mutableStateOf<DnsLog?>(null) }

    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            RethinkLargeTopBar(
                title = stringResource(R.string.lbl_logs),
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Integrated TabRow
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab.intValue,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    divider = {
                        HorizontalDivider(
                            thickness = Dimensions.dividerThickness,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, spec ->
                        val selected = selectedTab.intValue == index
                        Tab(
                            selected = selected,
                            onClick = { selectedTab.intValue = index },
                            text = {
                                Text(
                                    text = spec.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (tabs[selectedTab.intValue].tab) {
                    LogTab.CONNECTION -> ConnectionLogsContent(
                        viewModel = connectionTrackerViewModel,
                        repository = connectionTrackerRepository,
                        persistentState = persistentState,
                        onShowConnTracker = { selectedConn = it }
                    )

                    LogTab.DNS -> DnsLogsContent(
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

@OptIn(FlowPreview::class)
@Composable
private fun ConnectionLogsContent(
    viewModel: ConnectionTrackerViewModel,
    repository: ConnectionTrackerRepository,
    persistentState: PersistentState,
    onShowConnTracker: (ConnectionTracker) -> Unit
) {
    val context = LocalContext.current
    val clearSearchContentDescription = stringResource(R.string.cd_clear_search)
    val deleteContentDescription = stringResource(R.string.lbl_delete)
    val refreshCompleteMessage = stringResource(R.string.refresh_complete)
    val scope = rememberCoroutineScope()
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

    Column(modifier = Modifier.fillMaxSize()) {
        if (!persistentState.logsEnabled) {
            Box(Modifier.fillMaxSize().padding(Dimensions.spacing2xl), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.logs_disabled_summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            return
        }

        SectionHeader(
            title = stringResource(R.string.firewall_act_network_monitor_tab),
            modifier = Modifier.padding(horizontal = Dimensions.spacingXs)
        )

        // Search and filters
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.screenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Dimensions.spacingSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Dimensions.spacingSm).size(Dimensions.iconSizeMd)
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                stringResource(R.string.lbl_search),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    AnimatedVisibility(visible = query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = "" },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = clearSearchContentDescription,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Dimensions.iconSizeSm)
                            )
                        }
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = deleteContentDescription,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(Dimensions.iconSizeMd)
                        )
                    }
                }
            }

            // Tonal chips row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
            ) {
                items(filterOptions.size) { index ->
                    val (filter, label) = filterOptions[index]
                    val selected = parentFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = {
                            parentFilter = filter
                            childFilters = emptySet()
                        },
                        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Sub-category chips (Expressive secondary chips)
            val categories = when (parentFilter) {
                ConnectionTrackerViewModel.TopLevelFilter.BLOCKED -> FirewallRuleset.getBlockedRules()
                ConnectionTrackerViewModel.TopLevelFilter.ALLOWED -> FirewallRuleset.getAllowedRules()
                ConnectionTrackerViewModel.TopLevelFilter.ALL -> FirewallRuleset.getBlockedRules()
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)
            ) {
                items(categories.size) { index ->
                    val rule = categories[index]
                    val selected = childFilters.contains(rule.id)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            childFilters = if (selected) childFilters - rule.id else childFilters + rule.id
                        },
                        label = {
                            Text(
                                text = stringResource(rule.title),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = FirewallRuleset.getRulesIcon(rule.id)),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        }

        // Logs List
        if (items.itemCount == 0 && items.loadState.append.endOfPaginationReached) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(id = R.string.lbl_no_logs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Dimensions.screenPaddingHorizontal,
                    vertical = Dimensions.spacingSm
                ),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
            ) {
                items(items.itemCount) { index ->
                    val item = items[index] ?: return@items
                    ConnectionRow(item, onShowConnTracker)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = stringResource(R.string.conn_track_clear_logs_title)) },
            text = { Text(text = stringResource(R.string.conn_track_clear_logs_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch(Dispatchers.IO) { repository.clearAllData() }
                        Utilities.showToastUiCentered(
                            context,
                            refreshCompleteMessage,
                            Toast.LENGTH_SHORT
                        )
                    }
                ) {
                    Text(text = stringResource(R.string.hs_download_positive_default))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(R.string.lbl_cancel))
                }
            }
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun DnsLogsContent(
    viewModel: DnsLogViewModel,
    repository: DnsLogRepository,
    persistentState: PersistentState,
    onShowDnsLog: (DnsLog) -> Unit
) {
    val context = LocalContext.current
    val clearSearchContentDescription = stringResource(R.string.cd_clear_search)
    val deleteContentDescription = stringResource(R.string.lbl_delete)
    val refreshCompleteMessage = stringResource(R.string.refresh_complete)
    val scope = rememberCoroutineScope()
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

    Column(modifier = Modifier.fillMaxSize()) {
        if (!persistentState.logsEnabled) {
            Box(Modifier.fillMaxSize().padding(Dimensions.spacing2xl), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.logs_disabled_summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            return
        }

        SectionHeader(
            title = stringResource(R.string.dns_mode_info_title),
            modifier = Modifier.padding(horizontal = Dimensions.spacingXs)
        )
        // Search and filters
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.screenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Dimensions.spacingSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Dimensions.spacingSm).size(Dimensions.iconSizeMd)
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                stringResource(R.string.lbl_search),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    AnimatedVisibility(visible = query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = "" },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = clearSearchContentDescription,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Dimensions.iconSizeSm)
                            )
                        }
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = deleteContentDescription,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(Dimensions.iconSizeMd)
                        )
                    }
                }
            }

            // Tonal chips row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
            ) {
                items(filterOptions.size) { index ->
                    val (filter, label) = filterOptions[index]
                    val selected = filterType == filter
                    FilterChip(
                        selected = selected,
                        onClick = { filterType = filter },
                        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        if (items.itemCount == 0 && items.loadState.append.endOfPaginationReached) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(id = R.string.lbl_no_logs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Dimensions.screenPaddingHorizontal,
                    vertical = Dimensions.spacingSm
                ),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
            ) {
                items(items.itemCount) { index ->
                    val item = items[index] ?: return@items
                    DnsLogRow(
                        log = item,
                        loadFavIcon = persistentState.fetchFavIcon,
                        isRethinkDns = false,
                        onShowBlocklist = onShowDnsLog
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = stringResource(R.string.conn_track_clear_logs_title)) },
            text = { Text(text = stringResource(R.string.conn_track_clear_logs_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch(Dispatchers.IO) { repository.clearAllData() }
                        Utilities.showToastUiCentered(
                            context,
                            refreshCompleteMessage,
                            Toast.LENGTH_SHORT
                        )
                    }
                ) {
                    Text(text = stringResource(R.string.hs_download_positive_default))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(R.string.lbl_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnTrackerDetailsSheet(
    connection: ConnectionTracker,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.spacingLg),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.spacingLg)
            ) {
                Text(
                    text = connection.appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(Dimensions.spacingMd))
                DetailRow("IP Address", connection.ipAddress)
                DetailRow("Port", connection.port.toString())
                DetailRow("Protocol", connection.protocol.toString())
                val status = if (connection.isBlocked) "Blocked" else "Allowed"
                DetailRow("Status", status, isError = connection.isBlocked)

                Spacer(modifier = Modifier.height(Dimensions.spacingXl))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(text = stringResource(R.string.lbl_dismiss))
                }
            }
        }
        Spacer(modifier = Modifier.height(Dimensions.spacing2xl))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsLogDetailsSheet(
    log: DnsLog,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.spacingLg),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.spacingLg)
            ) {
                Text(
                    text = log.queryStr,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(Dimensions.spacingMd))
                DetailRow("App Name", log.appName)
                DetailRow("Response", log.responseIps.ifEmpty { "None" })
                DetailRow("Latency", "${log.latency}ms")
                val status = if (log.isBlocked) "Blocked" else "Allowed"
                DetailRow("Status", status, isError = log.isBlocked)

                Spacer(modifier = Modifier.height(Dimensions.spacingXl))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(text = stringResource(R.string.lbl_dismiss))
                }
            }
        }
        Spacer(modifier = Modifier.height(Dimensions.spacing2xl))
    }
}

@Composable
private fun DetailRow(label: String, value: String, isError: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.spacingXs),
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
