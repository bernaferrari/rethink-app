/*
Copyright 2020 RethinkDNS and its authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.collectAsLazyPagingItems
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.bumptech.glide.request.transition.Transition
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.ConnectionTrackerAdapter
import com.celzero.bravedns.adapter.DnsLogAdapter
import com.celzero.bravedns.adapter.RethinkLogAdapter
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.data.DataUsageSummary
import com.celzero.bravedns.database.ConnectionTrackerRepository
import com.celzero.bravedns.database.ConnectionTracker
import com.celzero.bravedns.database.DnsLog
import com.celzero.bravedns.database.DnsLogRepository
import com.celzero.bravedns.database.RethinkLogRepository
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.glide.FavIconDownloader
import com.celzero.bravedns.service.BraveVPNService
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallRuleset
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.activity.DomainConnectionsActivity
import com.celzero.bravedns.ui.bottomsheet.ConnTrackerSheet
import com.celzero.bravedns.ui.compose.statistics.StatisticsSummaryItem
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import com.celzero.bravedns.util.Utilities.getIcon
import com.celzero.bravedns.util.Themes.Companion.getCurrentTheme
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.ConnectionTrackerViewModel
import com.celzero.bravedns.viewmodel.ConnectionTrackerViewModel.TopLevelFilter
import com.celzero.bravedns.viewmodel.DomainConnectionsViewModel
import com.celzero.bravedns.viewmodel.DnsLogViewModel
import com.celzero.bravedns.viewmodel.RethinkLogViewModel
import com.celzero.bravedns.viewmodel.WgNwActivityViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class NetworkLogsActivity : AppCompatActivity() {
    private var fragmentIndex = 0
    private var searchParam = ""
    private var isUnivNavigated = false
    private var isWireGuardLogs = false
    private var wgId: String = ""

    private val persistentState by inject<PersistentState>()
    private val appConfig by inject<AppConfig>()
    private val connectionTrackerRepository by inject<ConnectionTrackerRepository>()
    private val dnsLogRepository by inject<DnsLogRepository>()
    private val rethinkLogRepository by inject<RethinkLogRepository>()
    private val eventLogger by inject<EventLogger>()

    private val connectionTrackerViewModel: ConnectionTrackerViewModel by viewModel()
    private val dnsLogViewModel: DnsLogViewModel by viewModel()
    private val rethinkLogViewModel: RethinkLogViewModel by viewModel()
    private val wgNwActivityViewModel: WgNwActivityViewModel by viewModel()

    private var tabs: List<TabSpec> = emptyList()

    enum class LogTab {
        CONNECTION,
        DNS,
        RETHINK,
        WG_STATS
    }

    data class TabSpec(val tab: LogTab, val label: String)

    enum class Tabs(val screen: Int) {
        NETWORK_LOGS(0),
        DNS_LOGS(1),
        RETHINK_LOGS(2),
        WIREGUARD_STATS(3)
    }

    companion object {
        const val RULES_SEARCH_ID_WIREGUARD = "W:"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        fragmentIndex = intent.getIntExtra(Constants.VIEW_PAGER_SCREEN_TO_LOAD, 0)
        searchParam = intent.getStringExtra(Constants.SEARCH_QUERY) ?: ""
        if (searchParam.contains(UniversalFirewallSettingsActivity.RULES_SEARCH_ID)) {
            isUnivNavigated = true
        } else if (searchParam.contains(RULES_SEARCH_ID_WIREGUARD)) {
            isWireGuardLogs = true
        }

        if (isWireGuardLogs) {
            wgId = searchParam.substringAfter(RULES_SEARCH_ID_WIREGUARD)
            dnsLogViewModel.setIsWireGuardLogs(true, wgId)
        }

        tabs = buildTabs()
        if (tabs.isEmpty()) {
            tabs = listOf(TabSpec(LogTab.CONNECTION, getString(R.string.firewall_act_network_monitor_tab)))
        }

        fragmentIndex = fragmentIndex.coerceIn(0, tabs.size - 1)

        setContent {
            RethinkTheme {
                NetworkLogsScreen(initialTab = fragmentIndex)
            }
        }

        observeAppState()
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun NetworkLogsScreen(initialTab: Int) {
        val selectedTab = remember { mutableIntStateOf(initialTab.coerceIn(0, tabs.size - 1)) }
        val selectedConn = remember { mutableStateOf<ConnectionTracker?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(title = { Text(text = getString(R.string.lbl_logs)) })
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    TabRow(
                        modifier = Modifier.fillMaxWidth().padding(end = 48.dp),
                        selectedTabIndex = selectedTab.intValue
                    ) {
                        tabs.forEachIndexed { index, spec ->
                            Tab(
                                selected = selectedTab.intValue == index,
                                onClick = { selectedTab.intValue = index },
                                text = { Text(text = spec.label) }
                            )
                        }
                    }
                    IconButton(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onClick = { openConsoleLogActivity() }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_android_icon),
                            contentDescription = null
                        )
                    }
                }

                when (tabs[selectedTab.intValue].tab) {
                    LogTab.CONNECTION -> ConnectionLogsContent { selectedConn.value = it }
                    LogTab.DNS -> DnsLogsContent()
                    LogTab.RETHINK -> RethinkLogsContent { selectedConn.value = it }
                    LogTab.WG_STATS -> WgStatsContent()
                }
            }
        }

        val activeConn = selectedConn.value
        if (activeConn != null) {
            ConnTrackerSheet(
                activity = this@NetworkLogsActivity,
                info = activeConn,
                persistentState = persistentState,
                eventLogger = eventLogger,
                onDismiss = { selectedConn.value = null }
            )
        }
    }

    @OptIn(FlowPreview::class)
    @Composable
    private fun ConnectionLogsContent(onShowConnTracker: (ConnectionTracker) -> Unit) {
        val adapter = remember { ConnectionTrackerAdapter(this, onShowConnTracker) }
        val items = connectionTrackerViewModel.connectionTrackerList.asFlow().collectAsLazyPagingItems()
        var showDeleteDialog by remember { mutableStateOf(false) }

        var query by remember { mutableStateOf("") }
        var parentFilter by remember { mutableStateOf(TopLevelFilter.ALL) }
        var childFilters by remember { mutableStateOf(setOf<String>()) }

        LaunchedEffect(Unit) {
            if (searchParam.isNotEmpty()) {
                if (isUnivNavigated) {
                    val rule = searchParam.split(UniversalFirewallSettingsActivity.RULES_SEARCH_ID)[1]
                    parentFilter = TopLevelFilter.BLOCKED
                    childFilters = setOf(rule)
                } else if (isWireGuardLogs) {
                    val rule = searchParam.split(RULES_SEARCH_ID_WIREGUARD)[1]
                    query = rule
                } else {
                    query = searchParam
                }
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { Triple(query, parentFilter, childFilters) }
                .debounce(300)
                .distinctUntilChanged()
                .collect { (q, type, filters) ->
                    connectionTrackerViewModel.setFilter(q, filters, type)
                }
        }

        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            if (!persistentState.logsEnabled) {
                Text(
                    text = getString(R.string.logs_disabled_summary),
                    style = MaterialTheme.typography.bodyMedium
                )
                return
            }

            if (!isUnivNavigated && !isWireGuardLogs) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(getString(R.string.lbl_search)) },
                        singleLine = true
                    )
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        TopLevelFilter.ALL to getString(R.string.lbl_all),
                        TopLevelFilter.ALLOWED to getString(R.string.lbl_allowed),
                        TopLevelFilter.BLOCKED to getString(R.string.lbl_blocked)
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

                val categories =
                    when (parentFilter) {
                        TopLevelFilter.BLOCKED -> FirewallRuleset.getBlockedRules()
                        TopLevelFilter.ALLOWED -> FirewallRuleset.getAllowedRules()
                        TopLevelFilter.ALL -> FirewallRuleset.getBlockedRules()
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
                                childFilters =
                                    if (selected) {
                                        childFilters - rule.id
                                    } else {
                                        childFilters + rule.id
                                    }
                            },
                            label = { Text(text = getString(rule.title)) },
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
            }

            if (items.itemCount == 0 && items.loadState.append.endOfPaginationReached) {
                Text(
                    text = if (isWireGuardLogs || isUnivNavigated) {
                        getString(R.string.ada_ip_no_connection)
                    } else {
                        getString(R.string.lbl_no_logs)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items.itemCount) { index ->
                        val item = items[index] ?: return@items
                        adapter.ConnectionRow(item)
                    }
                }
            }
        }

        if (showDeleteDialog) {
            val rule =
                searchParam.split(UniversalFirewallSettingsActivity.RULES_SEARCH_ID).getOrNull(1)
            val isRuleDelete = isUnivNavigated && rule != null
            val title =
                if (isRuleDelete) {
                    getString(R.string.conn_track_clear_rule_logs_title)
                } else {
                    getString(R.string.conn_track_clear_logs_title)
                }
            val message =
                if (isRuleDelete) {
                    getString(R.string.conn_track_clear_rule_logs_message)
                } else {
                    getString(R.string.conn_track_clear_logs_message)
                }
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(text = title) },
                text = { Text(text = message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            if (isRuleDelete) {
                                io { connectionTrackerRepository.clearLogsByRule(rule.orEmpty()) }
                            } else {
                                io { connectionTrackerRepository.clearAllData() }
                            }
                        }
                    ) {
                        Text(text = getString(R.string.dns_log_dialog_positive))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(text = getString(R.string.lbl_cancel))
                    }
                }
            )
        }
    }

    @OptIn(FlowPreview::class)
    @Composable
    private fun DnsLogsContent() {
        val favIcon = persistentState.fetchFavIcon
        val isRethinkDns = appConfig.isRethinkDnsConnected()
        var selectedLog by remember { mutableStateOf<DnsLog?>(null) }
        val adapter =
            remember {
                DnsLogAdapter(this, favIcon, isRethinkDns) { log ->
                    selectedLog = log
                }
            }
        val items = dnsLogViewModel.dnsLogsList.asFlow().collectAsLazyPagingItems()
        var showDeleteDialog by remember { mutableStateOf(false) }

        var query by remember { mutableStateOf("") }
        var filter by remember { mutableStateOf(DnsLogViewModel.DnsLogFilter.ALL) }

        LaunchedEffect(Unit) {
            if (searchParam.isNotEmpty() && !isWireGuardLogs) {
                query = searchParam
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { Pair(query, filter) }
                .debounce(300)
                .distinctUntilChanged()
                .collect { (q, type) ->
                    dnsLogViewModel.setFilter(q, type)
                }
        }

        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            if (!isWireGuardLogs) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(getString(R.string.lbl_search)) },
                        singleLine = true
                    )
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        DnsLogViewModel.DnsLogFilter.ALL to getString(R.string.lbl_all),
                        DnsLogViewModel.DnsLogFilter.ALLOWED to getString(R.string.lbl_allowed),
                        DnsLogViewModel.DnsLogFilter.MAYBE_BLOCKED to getString(R.string.lbl_maybe_blocked),
                        DnsLogViewModel.DnsLogFilter.BLOCKED to getString(R.string.lbl_blocked),
                        DnsLogViewModel.DnsLogFilter.UNKNOWN_RECORDS to getString(R.string.network_log_app_name_unknown)
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = filter == type,
                            onClick = { filter = type },
                            label = { Text(text = label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            if (items.itemCount == 0 && items.loadState.append.endOfPaginationReached) {
                Text(text = getString(R.string.lbl_no_logs), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items.itemCount) { index ->
                        val item = items[index] ?: return@items
                        adapter.DnsLogRow(item)
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(text = getString(R.string.dns_log_clear_logs_title)) },
                text = { Text(text = getString(R.string.dns_log_clear_logs_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            io { dnsLogRepository.clearAllData() }
                        }
                    ) {
                        Text(text = getString(R.string.dns_log_dialog_positive))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(text = getString(R.string.lbl_cancel))
                    }
                }
            )
        }

        selectedLog?.let { log ->
            DnsBlocklistSheet(
                log = log,
                onDismiss = { selectedLog = null }
            )
        }
    }

    @OptIn(FlowPreview::class)
    @Composable
    private fun RethinkLogsContent(onShowConnTracker: (ConnectionTracker) -> Unit) {
        val adapter = remember { RethinkLogAdapter(this, onShowConnTracker) }
        val items = rethinkLogViewModel.rlogList.asFlow().collectAsLazyPagingItems()
        var query by remember { mutableStateOf("") }
        var showDeleteDialog by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            snapshotFlow { query }
                .debounce(300)
                .distinctUntilChanged()
                .collect { q -> rethinkLogViewModel.setFilter(q) }
        }

        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(getString(R.string.lbl_search)) },
                    singleLine = true
                )
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (items.itemCount == 0 && items.loadState.append.endOfPaginationReached) {
                Text(text = getString(R.string.lbl_no_logs), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items.itemCount) { index ->
                        val item = items[index] ?: return@items
                        adapter.RethinkLogRow(item)
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(text = getString(R.string.dns_log_clear_logs_title)) },
                text = { Text(text = getString(R.string.dns_log_clear_logs_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            io { rethinkLogRepository.clearAllData() }
                        }
                    ) {
                        Text(text = getString(R.string.dns_log_dialog_positive))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(text = getString(R.string.lbl_cancel))
                    }
                }
            )
        }
    }

    @Composable
    private fun WgStatsContent() {
        if (wgId.isEmpty() || !wgId.startsWith(ProxyManager.ID_WG_BASE)) {
            var showWgError by remember { mutableStateOf(true) }
            if (showWgError) {
                AlertDialog(
                    onDismissRequest = {
                        showWgError = false
                        finish()
                    },
                    title = { Text(text = getString(R.string.lbl_wireguard)) },
                    text = { Text(text = getString(R.string.config_invalid_desc)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showWgError = false
                                finish()
                            }
                        ) {
                            Text(text = getString(R.string.fapps_info_dialog_positive_btn))
                        }
                    }
                )
            }
            return
        }

        var selectedTime by remember { mutableStateOf(WgNwActivityViewModel.TimeCategory.ONE_HOUR) }
        var dataUsage by remember { mutableStateOf(DataUsageSummary(0L, 0L, 0, 0L)) }
        val items = wgNwActivityViewModel.wgAppNwActivity.asFlow().collectAsLazyPagingItems()

        LaunchedEffect(Unit) {
            wgNwActivityViewModel.setWgId(wgId)
        }

        LaunchedEffect(selectedTime) {
            wgNwActivityViewModel.timeCategoryChanged(selectedTime)
            io {
                val totalUsage = wgNwActivityViewModel.totalUsage(wgId)
                uiCtx { dataUsage = totalUsage }
            }
        }

        val totalUsageText = formatTotalUsage(dataUsage)

        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options: List<Pair<WgNwActivityViewModel.TimeCategory, String>> =
                    listOf(
                        WgNwActivityViewModel.TimeCategory.ONE_HOUR to
                            getString(R.string.ci_desc, "1", getString(R.string.lbl_hour)),
                        WgNwActivityViewModel.TimeCategory.TWENTY_FOUR_HOUR to
                            getString(R.string.ci_desc, "24", getString(R.string.lbl_hour)),
                        WgNwActivityViewModel.TimeCategory.SEVEN_DAYS to
                            getString(R.string.ci_desc, "7", getString(R.string.lbl_day))
                    )
                options.forEach { (value, label) ->
                    FilterChip(
                        selected = selectedTime == value,
                        onClick = { selectedTime = value },
                        label = { Text(text = label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (totalUsageText.isNotEmpty()) {
                Text(text = totalUsageText, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (items.itemCount == 0 && items.loadState.append.endOfPaginationReached) {
                Text(text = getString(R.string.lbl_no_logs), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items.itemCount) { index ->
                        val item = items[index] ?: return@items
                        val usageText =
                            if (item.downloadBytes != null && item.uploadBytes != null) {
                                val download =
                                    getString(
                                        R.string.symbol_download,
                                        Utilities.humanReadableByteCount(item.downloadBytes, true)
                                    )
                                val upload =
                                    getString(
                                        R.string.symbol_upload,
                                        Utilities.humanReadableByteCount(item.uploadBytes, true)
                                    )
                                getString(R.string.two_argument, upload, download)
                            } else {
                                null
                            }

                        val total = (item.downloadBytes ?: 0L) + (item.uploadBytes ?: 0L)
                        val progress = if (total == 0L) 0f else 1f

                        StatisticsSummaryItem(
                            title = item.appOrDnsName ?: "",
                            subtitle = usageText,
                            countText = item.count.toString(),
                            iconDrawable = Utilities.getDefaultIcon(this@NetworkLogsActivity),
                            flagText = null,
                            showProgress = true,
                            progress = progress,
                            progressColor = MaterialTheme.colorScheme.primary,
                            showIndicator = false,
                            onClick = null
                        )
                    }
                }
            }
        }
    }

    private fun formatTotalUsage(dataUsage: DataUsageSummary): String {
        val unmeteredUsage = (dataUsage.totalDownload + dataUsage.totalUpload)
        val totalUsage = unmeteredUsage + dataUsage.meteredDataUsage
        val unmetered =
            getString(
                R.string.two_argument_colon,
                getString(R.string.ada_app_unmetered),
                Utilities.humanReadableByteCount(unmeteredUsage, true)
            )
        val metered =
            getString(
                R.string.two_argument_colon,
                getString(R.string.ada_app_metered),
                Utilities.humanReadableByteCount(dataUsage.meteredDataUsage, true)
            )
        val overall =
            getString(
                R.string.two_argument_colon,
                getString(R.string.lbl_overall),
                Utilities.humanReadableByteCount(totalUsage, true)
            )
        return listOf(unmetered, metered, overall).joinToString("\n")
    }

    private fun buildTabs(): List<TabSpec> {
        if (isUnivNavigated) {
            return listOf(TabSpec(LogTab.CONNECTION, getString(R.string.firewall_act_network_monitor_tab)))
        }
        if (isWireGuardLogs) {
            return listOf(
                TabSpec(LogTab.CONNECTION, getString(R.string.firewall_act_network_monitor_tab)),
                TabSpec(LogTab.DNS, getString(R.string.dns_mode_info_title)),
                TabSpec(LogTab.WG_STATS, getString(R.string.title_statistics))
            )
        }

        return listOf(
            TabSpec(LogTab.CONNECTION, getString(R.string.firewall_act_network_monitor_tab)),
            TabSpec(LogTab.DNS, getString(R.string.dns_mode_info_title)),
            TabSpec(LogTab.RETHINK, getString(R.string.lbl_rethink)),
            TabSpec(LogTab.WG_STATS, getString(R.string.title_statistics))
        )
    }


    private fun openConsoleLogActivity() {
        val intent = Intent(this, ConsoleLogActivity::class.java)
        startActivity(intent)
    }

    private fun observeAppState() {
        VpnController.connectionStatus.observe(this) {
            if (it == BraveVPNService.State.PAUSED) {
                startActivity(Intent().setClass(this, PauseActivity::class.java))
                finish()
            }
        }
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DnsBlocklistSheet(
        log: DnsLog,
        onDismiss: () -> Unit
    ) {
        val borderColor = Color(UIUtils.fetchColor(this, R.attr.border))
        val ruleLabels = remember { DomainRulesManager.Status.getLabel(this).toList() }
        val ruleOptions =
            remember {
                listOf(
                    DomainRulesManager.Status.NONE,
                    DomainRulesManager.Status.BLOCK,
                    DomainRulesManager.Status.TRUST
                )
            }
        var ruleExpanded by remember { mutableStateOf(false) }
        val currentRule = remember(log.uid) { getRuleUid(log) }
        var lastStatus by remember { mutableStateOf(DomainRulesManager.Status.NONE) }
        var showRuleInfo by remember { mutableStateOf(false) }
        var showIpDetails by remember { mutableStateOf(false) }
        var showAppInfo by remember { mutableStateOf(false) }
        var ipDetailsText by remember { mutableStateOf("") }
        var appInfoText by remember { mutableStateOf("") }
        val selectedIndex = ruleOptions.indexOf(lastStatus).coerceAtLeast(0)
        var selectedLabel by remember { mutableStateOf(ruleLabels[selectedIndex]) }

        LaunchedEffect(log) {
            val domain = log.queryStr
            if (domain.isNotEmpty()) {
                lastStatus = DomainRulesManager.getDomainRule(domain, currentRule)
                selectedLabel = ruleLabels[lastStatus.id]
            }
            ipDetailsText = getResponseIps(log)
            appInfoText = log.packageName
        }

        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier =
                        Modifier.align(Alignment.CenterHorizontally)
                            .width(60.dp)
                            .height(3.dp)
                            .background(borderColor, RoundedCornerShape(2.dp))
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (persistentState.fetchFavIcon) {
                        val favIcon = rememberFavIcon(log)
                        if (favIcon != null) {
                            val favPainter = rememberDrawablePainter(favIcon)
                            favPainter?.let { painter ->
                                Image(
                                    painter = painter,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = log.queryStr,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable { openDomainConnections(log) }
                        )
                        Text(
                            text = log.flag,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = getString(
                                R.string.dns_btm_latency_ms,
                                log.latency.toString()
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = getResponseIp(log),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable { showIpDetails = true }
                        )
                    }
                }

                HtmlText(
                    html = getString(R.string.bsdl_block_desc),
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) { showRuleInfo = true }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = getString(R.string.lbl_domain_rules))
                    Box(modifier = Modifier.weight(1f)) {
                        TextButton(
                            onClick = { ruleExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = selectedLabel)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "▼")
                        }
                        DropdownMenu(
                            expanded = ruleExpanded,
                            onDismissRequest = { ruleExpanded = false }
                        ) {
                            ruleOptions.forEachIndexed { index, option ->
                                DropdownMenuItem(
                                    text = { Text(ruleLabels[index]) },
                                    onClick = {
                                        ruleExpanded = false
                                        if (option == lastStatus) return@DropdownMenuItem
                                        val domain = log.queryStr
                                        if (domain.isNotEmpty()) {
                                            selectedLabel = ruleLabels[index]
                                            applyDomainRule(domain, currentRule, option) {
                                                lastStatus = it
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (log.msg.isNotEmpty()) {
                    Text(
                        text = log.msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }

                if (log.region.isNotEmpty()) {
                    Text(
                        text = log.region,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }

                if (log.blockedTarget.isNotEmpty()) {
                    Text(
                        text = log.blockedTarget,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }

                BlockedSummary(log)

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val blocklists = log.getBlocklists().filter { it.isNotBlank() }
                    if (blocklists.isNotEmpty()) {
                        val countText =
                            getString(R.string.rsv_blocklist_count_text, blocklists.size)
                        ChipText(countText)
                    }
                    val ips = log.responseIps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (ips.isNotEmpty()) {
                        val ipLabel = getString(R.string.lbl_ip)
                        val text =
                            getString(
                                R.string.two_argument_colon,
                                ipLabel,
                                ips.size.toString()
                            )
                        ChipText(text)
                    }
                    if (log.typeName.isNotEmpty()) {
                        ChipText(log.typeName)
                    }
                }

                AppInfoRow(log, onShow = { showAppInfo = true })
            }
        }

        if (showRuleInfo) {
            AlertDialog(
                onDismissRequest = { showRuleInfo = false },
                title = { Text(text = getString(R.string.lbl_domain_rules)) },
                text = { HtmlText(html = getString(R.string.bsdl_block_desc)) },
                confirmButton = {
                    TextButton(onClick = { showRuleInfo = false }) {
                        Text(text = getString(R.string.hs_download_positive_default))
                    }
                }
            )
        }

        if (showIpDetails) {
            AlertDialog(
                onDismissRequest = { showIpDetails = false },
                title = { Text(text = log.queryStr) },
                text = { Text(text = ipDetailsText) },
                confirmButton = {
                    TextButton(onClick = { showIpDetails = false }) {
                        Text(text = getString(R.string.hs_download_positive_default))
                    }
                }
            )
        }

        if (showAppInfo) {
            AlertDialog(
                onDismissRequest = { showAppInfo = false },
                title = { Text(text = log.appName) },
                text = { Text(text = appInfoText) },
                confirmButton = {
                    TextButton(onClick = { showAppInfo = false }) {
                        Text(text = getString(R.string.hs_download_positive_default))
                    }
                }
            )
        }
    }

    @Composable
    private fun HtmlText(html: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
        val textValue =
            remember(html) { HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() }
        val clickableModifier =
            if (onClick != null) {
                modifier.clickable { onClick() }
            } else {
                modifier
            }
        Text(
            text = textValue,
            modifier = clickableModifier,
            style =
                MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
        )
    }

    @Composable
    private fun ChipText(text: String) {
        Box(
            modifier =
                Modifier.background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    private fun BlockedSummary(log: DnsLog) {
        if (!log.isBlocked && !log.upstreamBlock && log.blockLists.isEmpty()) return

        val blockedBy =
            when {
                log.blockedTarget.isNotEmpty() -> log.blockedTarget
                log.blockLists.isNotEmpty() -> getString(R.string.lbl_rules)
                log.proxyId.isNotEmpty() -> log.proxyId
                log.resolver.isNotEmpty() -> log.resolver
                else -> getString(R.string.lbl_domain_rules)
            }
        Text(
            text = getString(R.string.bsdl_blocked_desc, log.queryStr, blockedBy),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }

    @Composable
    private fun AppInfoRow(log: DnsLog, onShow: () -> Unit) {
        if (log.appName.isEmpty() && log.packageName.isEmpty()) return

        Row(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = 10.dp).clickable {
                    onShow()
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon =
                remember(log.packageName, log.appName) {
                    getIcon(this@NetworkLogsActivity, log.packageName, log.appName)
                }
            icon?.let { drawable ->
                val painter = rememberDrawablePainter(drawable)
                painter?.let {
                    Image(
                        painter = it,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = log.appName, style = MaterialTheme.typography.bodyMedium)
        }
    }

    private fun openDomainConnections(log: DnsLog) {
        val domain = log.queryStr
        if (domain.isEmpty()) return
        val intent = Intent(this, DomainConnectionsActivity::class.java)
        intent.putExtra(
            DomainConnectionsActivity.INTENT_EXTRA_TYPE,
            DomainConnectionsActivity.InputType.DOMAIN.type
        )
        intent.putExtra(DomainConnectionsActivity.INTENT_EXTRA_DOMAIN, domain)
        intent.putExtra(DomainConnectionsActivity.INTENT_EXTRA_IS_BLOCKED, log.isBlocked)
        intent.putExtra(
            DomainConnectionsActivity.INTENT_EXTRA_TIME_CATEGORY,
            DomainConnectionsViewModel.TimeCategory.SEVEN_DAYS.value
        )
        startActivity(intent)
    }

    private fun getResponseIp(log: DnsLog): String {
        return log.responseIps.split(",").firstOrNull()?.trim().orEmpty()
    }

    private fun getResponseIps(log: DnsLog): String {
        val ips = log.responseIps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (ips.isEmpty()) {
            getString(
                R.string.two_argument_colon,
                getString(R.string.lbl_ip),
                "0"
            )
        } else {
            ips.joinToString(separator = "\n")
        }
    }

    @Composable
    private fun rememberFavIcon(log: DnsLog): Drawable? {
        var drawable by
            remember(log.queryStr, persistentState.fetchFavIcon) { mutableStateOf<Drawable?>(null) }
        DisposableEffect(log.queryStr, persistentState.fetchFavIcon) {
            drawable = null
            val target = loadFavIcon(log) { loaded ->
                drawable = loaded
            }
            onDispose {
                target?.let { Glide.with(applicationContext).clear(it) }
            }
        }
        return drawable
    }

    private fun loadFavIcon(log: DnsLog, onResult: (Drawable?) -> Unit): CustomTarget<Drawable>? {
        if (!persistentState.fetchFavIcon) {
            onResult(null)
            return null
        }

        val domain = log.queryStr
        if (domain.isEmpty()) {
            onResult(null)
            return null
        }

        val trim = domain.dropLastWhile { it == '.' }
        if (FavIconDownloader.isUrlAvailableInFailedCache(trim) != null) {
            onResult(null)
            return null
        }

        val factory = DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(true).build()
        val nextDnsUrl = FavIconDownloader.constructFavIcoUrlNextDns(trim)
        val duckduckGoUrl = FavIconDownloader.constructFavUrlDuckDuckGo(trim)
        val duckduckgoDomainUrl = FavIconDownloader.getDomainUrlFromFdqnDuckduckgo(trim)
        val target =
            object : CustomTarget<Drawable>() {
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    onResult(null)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    onResult(null)
                }

                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    onResult(resource)
                }
            }

        Glide.with(applicationContext)
            .load(nextDnsUrl)
            .onlyRetrieveFromCache(true)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .error(
                Glide.with(applicationContext)
                    .load(duckduckGoUrl)
                    .onlyRetrieveFromCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .error(
                        Glide.with(applicationContext)
                            .load(duckduckgoDomainUrl)
                            .onlyRetrieveFromCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    )
            )
            .transition(DrawableTransitionOptions.withCrossFade(factory))
            .into(target)
        return target
    }

    private fun applyDomainRule(
        domain: String,
        uid: Int,
        status: DomainRulesManager.Status,
        onUpdated: (DomainRulesManager.Status) -> Unit
    ) {
        io {
            DomainRulesManager.changeStatus(
                domain,
                uid,
                "",
                DomainRulesManager.DomainType.DOMAIN,
                status
            )
            onUpdated(status)
            logDnsRuleEvent(domain, status)
        }
    }

    private fun logDnsRuleEvent(domain: String, status: DomainRulesManager.Status) {
        eventLogger.log(
            EventType.FW_RULE_MODIFIED,
            Severity.LOW,
            "DNS log rule",
            EventSource.UI,
            false,
            "Domain rule updated for $domain: ${status.name}"
        )
    }

    private fun getRuleUid(log: DnsLog): Int {
        return when (log.uid) {
            Constants.INVALID_UID,
            Constants.MISSING_UID -> Constants.UID_EVERYBODY
            else -> log.uid
        }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}
