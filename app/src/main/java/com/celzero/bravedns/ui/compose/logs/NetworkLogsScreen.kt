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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class LogTab { CONNECTION, DNS }

data class TabSpec(val tab: LogTab, val label: String)

/**
 * Full Network Logs Screen for navigation integration.
 * Displays Connection logs and DNS logs with search and filtering.
 */
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
    val context = LocalContext.current
    
    val tabs = remember {
        listOf(
            TabSpec(LogTab.CONNECTION, context.getString(R.string.firewall_act_network_monitor_tab)),
            TabSpec(LogTab.DNS, context.getString(R.string.dns_mode_info_title))
        )
    }
    
    val selectedTab = remember { mutableIntStateOf(0) }
    var selectedConn by remember { mutableStateOf<ConnectionTracker?>(null) }
    var selectedDns by remember { mutableStateOf<DnsLog?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.lbl_logs)) },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_arrow_back_24),
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab.intValue,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, spec ->
                    Tab(
                        selected = selectedTab.intValue == index,
                        onClick = { selectedTab.intValue = index },
                        text = { Text(text = spec.label) }
                    )
                }
            }
            
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
    val scope = rememberCoroutineScope()
    val items = viewModel.connectionTrackerList.asFlow().collectAsLazyPagingItems()
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var parentFilter by remember { mutableStateOf(ConnectionTrackerViewModel.TopLevelFilter.ALL) }
    var childFilters by remember { mutableStateOf(setOf<String>()) }
    
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(query, parentFilter, childFilters) }
            .debounce(300)
            .distinctUntilChanged()
            .collect { (q, type, filters) ->
                viewModel.setFilter(q, filters, type)
            }
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (!persistentState.logsEnabled) {
            Text(
                text = stringResource(R.string.logs_disabled_summary),
                style = MaterialTheme.typography.bodyMedium
            )
            return
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.lbl_search)) },
                singleLine = true
            )
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ConnectionTrackerViewModel.TopLevelFilter.ALL to stringResource(R.string.lbl_all),
                ConnectionTrackerViewModel.TopLevelFilter.ALLOWED to stringResource(R.string.lbl_allowed),
                ConnectionTrackerViewModel.TopLevelFilter.BLOCKED to stringResource(R.string.lbl_blocked)
            ).forEach { (filter, label) ->
                FilterChip(
                    selected = parentFilter == filter,
                    onClick = {
                        parentFilter = filter
                        childFilters = emptySet()
                    },
                    label = { Text(text = label) }
                )
            }
        }
        
        val categories = when (parentFilter) {
            ConnectionTrackerViewModel.TopLevelFilter.BLOCKED -> FirewallRuleset.getBlockedRules()
            ConnectionTrackerViewModel.TopLevelFilter.ALLOWED -> FirewallRuleset.getAllowedRules()
            ConnectionTrackerViewModel.TopLevelFilter.ALL -> FirewallRuleset.getBlockedRules()
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(categories.size) { index ->
                val rule = categories[index]
                val selected = childFilters.contains(rule.id)
                FilterChip(
                    selected = selected,
                    onClick = {
                        childFilters = if (selected) childFilters - rule.id else childFilters + rule.id
                    },
                    label = { Text(text = stringResource(rule.title)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = FirewallRuleset.getRulesIcon(rule.id)),
                            contentDescription = null
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (items.itemCount == 0 && items.loadState.append.endOfPaginationReached) {
            Text(
                text = stringResource(R.string.lbl_no_logs),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                            context.getString(R.string.refresh_complete),
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
    val scope = rememberCoroutineScope()
    val items = viewModel.dnsLogsList.asFlow().collectAsLazyPagingItems()
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf(DnsLogViewModel.DnsLogFilter.ALL) }
    
    LaunchedEffect(Unit) {
        snapshotFlow { Pair(query, filterType) }
            .debounce(300)
            .distinctUntilChanged()
            .collect { (q, type) ->
                viewModel.setFilter(q, type)
            }
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (!persistentState.logsEnabled) {
            Text(
                text = stringResource(R.string.logs_disabled_summary),
                style = MaterialTheme.typography.bodyMedium
            )
            return
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.lbl_search)) },
                singleLine = true
            )
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                DnsLogViewModel.DnsLogFilter.ALL to stringResource(R.string.lbl_all),
                DnsLogViewModel.DnsLogFilter.ALLOWED to stringResource(R.string.lbl_allowed),
                DnsLogViewModel.DnsLogFilter.BLOCKED to stringResource(R.string.lbl_blocked)
            ).forEach { (filter, label) ->
                FilterChip(
                    selected = filterType == filter,
                    onClick = { filterType = filter },
                    label = { Text(text = label) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (items.itemCount == 0 && items.loadState.append.endOfPaginationReached) {
            Text(
                text = stringResource(R.string.lbl_no_logs),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                            context.getString(R.string.refresh_complete),
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = connection.appName,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "IP: ${connection.ipAddress}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Port: ${connection.port}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Protocol: ${connection.protocol}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Status: ${if (connection.isBlocked) "Blocked" else "Allowed"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(text = stringResource(R.string.lbl_dismiss))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsLogDetailsSheet(
    log: DnsLog,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = log.queryStr,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "App: ${log.appName}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Response: ${log.responseIps}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Latency: ${log.latency}ms",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Status: ${if (log.isBlocked) "Blocked" else "Allowed"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(text = stringResource(R.string.lbl_dismiss))
            }
        }
    }
}
