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
package com.celzero.bravedns.ui.compose.apps

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.celzero.bravedns.R
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.RefreshDatabase
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.ui.compose.firewall.AppListScreen as FirewallAppListScreen
import com.celzero.bravedns.ui.compose.firewall.BlockType
import com.celzero.bravedns.ui.compose.firewall.Filters
import com.celzero.bravedns.ui.compose.firewall.FirewallFilter
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.AppInfoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect

private const val REFRESH_TIMEOUT: Long = 4000
private const val QUERY_TEXT_DELAY: Long = 1000

/**
 * Full App List Screen for navigation integration.
 * Manages all state internally and delegates UI to firewall/AppListScreen.
 */
@OptIn(FlowPreview::class)
@Composable
fun AppListScreen(
    viewModel: AppInfoViewModel,
    eventLogger: EventLogger,
    refreshDatabase: RefreshDatabase,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State
    var queryText by remember { mutableStateOf("") }
    var filterLabelText by remember { mutableStateOf<CharSequence>("") }
    var selectedFirewallFilter by remember { mutableStateOf(FirewallFilter.ALL) }
    var isRefreshing by remember { mutableStateOf(false) }
    var currentFilters by remember { mutableStateOf(Filters()) }
    
    // Bulk action states
    var bulkWifi by remember { mutableStateOf(false) }
    var bulkMobile by remember { mutableStateOf(false) }
    var bulkBypass by remember { mutableStateOf(false) }
    var bulkBypassDns by remember { mutableStateOf(false) }
    var bulkExclude by remember { mutableStateOf(false) }
    var bulkLockdown by remember { mutableStateOf(false) }
    var showBulkUpdateDialog by remember { mutableStateOf(false) }
    var bulkDialogTitle by remember { mutableStateOf("") }
    var bulkDialogMessage by remember { mutableStateOf("") }
    var bulkDialogType by remember { mutableStateOf<BlockType?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showBypassToolTip by remember { mutableStateOf(true) }
    
    val searchQuery = remember { MutableStateFlow("") }
    
    // Filter label update
    fun updateFilterText(filter: Filters) {
        val filterLabel = filter.topLevelFilter.getLabel(context)
        val firewallLabel = filter.firewallFilter.getLabel(context)
        filterLabelText = if (filter.categoryFilters.isEmpty()) {
            UIUtils.htmlToSpannedText(
                context.getString(
                    R.string.fapps_firewall_filter_desc,
                    firewallLabel.lowercase(),
                    filterLabel
                )
            )
        } else {
            UIUtils.htmlToSpannedText(
                context.getString(
                    R.string.fapps_firewall_filter_desc_category,
                    firewallLabel.lowercase(),
                    filterLabel,
                    filter.categoryFilters
                )
            )
        }
    }
    
    // Apply filters
    fun applyFilters(filters: Filters) {
        currentFilters = filters
        viewModel.setFilter(filters)
        updateFilterText(filters)
        selectedFirewallFilter = filters.firewallFilter
        queryText = filters.searchString
    }
    
    // Query filter with debounce
    LaunchedEffect(Unit) {
        searchQuery
            .debounce(QUERY_TEXT_DELAY)
            .distinctUntilChanged()
            .collect { query ->
                val updated = currentFilters.copy().apply { searchString = query }
                applyFilters(updated)
            }
    }
    
    // Initialize
    LaunchedEffect(Unit) {
        applyFilters(Filters())
    }
    
    // Bulk action helpers
    fun getBulkActionDialogTitle(type: BlockType): String {
        return when (type) {
            BlockType.UNMETER -> {
                if (!bulkWifi) context.getString(R.string.fapps_unmetered_block_dialog_title)
                else context.getString(R.string.fapps_unmetered_unblock_dialog_title)
            }
            BlockType.METER -> {
                if (!bulkMobile) context.getString(R.string.fapps_metered_block_dialog_title)
                else context.getString(R.string.fapps_metered_unblock_dialog_title)
            }
            BlockType.LOCKDOWN -> {
                if (!bulkLockdown) context.getString(R.string.fapps_isolate_block_dialog_title)
                else context.getString(R.string.fapps_unblock_dialog_title)
            }
            BlockType.BYPASS -> {
                if (!bulkBypass) context.getString(R.string.fapps_bypass_block_dialog_title)
                else context.getString(R.string.fapps_unblock_dialog_title)
            }
            BlockType.EXCLUDE -> {
                if (!bulkExclude) context.getString(R.string.fapps_exclude_block_dialog_title)
                else context.getString(R.string.fapps_unblock_dialog_title)
            }
            BlockType.BYPASS_DNS_FIREWALL -> {
                if (!bulkBypassDns) context.getString(R.string.fapps_bypass_dns_firewall_dialog_title)
                else context.getString(R.string.fapps_unblock_dialog_title)
            }
        }
    }
    
    fun getBulkActionDialogMessage(type: BlockType): String {
        return when (type) {
            BlockType.UNMETER -> {
                if (!bulkWifi) context.getString(R.string.fapps_unmetered_block_dialog_message)
                else context.getString(R.string.fapps_unmetered_unblock_dialog_message)
            }
            BlockType.METER -> {
                if (!bulkMobile) context.getString(R.string.fapps_metered_block_dialog_message)
                else context.getString(R.string.fapps_metered_unblock_dialog_message)
            }
            BlockType.LOCKDOWN -> {
                if (!bulkLockdown) context.getString(R.string.fapps_isolate_block_dialog_message)
                else context.getString(R.string.fapps_unblock_dialog_message)
            }
            BlockType.BYPASS -> {
                if (!bulkBypass) context.getString(R.string.fapps_bypass_block_dialog_message)
                else context.getString(R.string.fapps_unblock_dialog_message)
            }
            BlockType.BYPASS_DNS_FIREWALL -> {
                if (!bulkBypassDns) context.getString(R.string.fapps_bypass_dns_firewall_dialog_message)
                else context.getString(R.string.fapps_unblock_dialog_message)
            }
            BlockType.EXCLUDE -> {
                if (!bulkExclude) context.getString(R.string.fapps_exclude_block_dialog_message)
                else context.getString(R.string.fapps_unblock_dialog_message)
            }
        }
    }
    
    fun resetBulkStates(type: BlockType) {
        when (type) {
            BlockType.UNMETER -> {
                bulkMobile = false; bulkBypass = false; bulkBypassDns = false
                bulkExclude = false; bulkLockdown = false
            }
            BlockType.METER -> {
                bulkWifi = false; bulkBypass = false; bulkBypassDns = false
                bulkExclude = false; bulkLockdown = false
            }
            BlockType.LOCKDOWN -> {
                bulkWifi = false; bulkMobile = false; bulkBypass = false
                bulkBypassDns = false; bulkExclude = false
            }
            BlockType.BYPASS -> {
                bulkWifi = false; bulkMobile = false; bulkBypassDns = false
                bulkExclude = false; bulkLockdown = false
            }
            BlockType.BYPASS_DNS_FIREWALL -> {
                bulkWifi = false; bulkMobile = false; bulkBypass = false
                bulkExclude = false; bulkLockdown = false
            }
            BlockType.EXCLUDE -> {
                bulkWifi = false; bulkMobile = false; bulkBypass = false
                bulkBypassDns = false; bulkLockdown = false
            }
        }
    }
    
    fun logEvent(details: String) {
        eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, "App list, bulk change", EventSource.UI, false, details)
    }
    
    fun updateBulkRules(type: BlockType) {
        scope.launch(Dispatchers.IO) {
            when (type) {
                BlockType.UNMETER -> {
                    val unmeter = !bulkWifi
                    viewModel.updateUnmeteredStatus(unmeter)
                    withContext(Dispatchers.Main) {
                        bulkWifi = unmeter
                        resetBulkStates(BlockType.UNMETER)
                    }
                    logEvent("Bulk unmetered rule update, isUnmetered: $unmeter")
                }
                BlockType.METER -> {
                    val metered = !bulkMobile
                    viewModel.updateMeteredStatus(metered)
                    withContext(Dispatchers.Main) {
                        bulkMobile = metered
                        resetBulkStates(BlockType.METER)
                    }
                    logEvent("Bulk metered rule update, isMetered: $metered")
                }
                BlockType.LOCKDOWN -> {
                    val lockdown = !bulkLockdown
                    viewModel.updateLockdownStatus(lockdown)
                    withContext(Dispatchers.Main) {
                        bulkLockdown = lockdown
                        resetBulkStates(BlockType.LOCKDOWN)
                    }
                    logEvent("Bulk lockdown rule update, isLockdown: $lockdown")
                }
                BlockType.BYPASS -> {
                    val bypass = !bulkBypass
                    viewModel.updateBypassStatus(bypass)
                    withContext(Dispatchers.Main) {
                        bulkBypass = bypass
                        resetBulkStates(BlockType.BYPASS)
                    }
                    logEvent("Bulk bypass rule update, isBypass: $bypass")
                }
                BlockType.BYPASS_DNS_FIREWALL -> {
                    val bypassDns = !bulkBypassDns
                    viewModel.updateBypassDnsFirewall(bypassDns)
                    withContext(Dispatchers.Main) {
                        bulkBypassDns = bypassDns
                        resetBulkStates(BlockType.BYPASS_DNS_FIREWALL)
                    }
                    logEvent("Bulk bypass DNS firewall rule update, isBypassDnsFirewall: $bypassDns")
                }
                BlockType.EXCLUDE -> {
                    val exclude = !bulkExclude
                    viewModel.updateExcludeStatus(exclude)
                    withContext(Dispatchers.Main) {
                        bulkExclude = exclude
                        resetBulkStates(BlockType.EXCLUDE)
                    }
                    logEvent("Bulk exclude rule update, isExclude: $exclude")
                }
            }
        }
    }
    
    fun refreshAppList() {
        isRefreshing = true
        scope.launch(Dispatchers.IO) {
            refreshDatabase.refresh(RefreshDatabase.ACTION_REFRESH_INTERACTIVE)
        }
        scope.launch {
            delay(REFRESH_TIMEOUT)
            isRefreshing = false
            Utilities.showToastUiCentered(context, context.getString(R.string.refresh_complete), Toast.LENGTH_SHORT)
        }
    }
    
    // Delegate to the firewall AppListScreen with all parameters
    FirewallAppListScreen(
        viewModel = viewModel,
        eventLogger = eventLogger,
        queryText = queryText,
        filterLabelText = filterLabelText,
        selectedFirewallFilter = selectedFirewallFilter,
        isRefreshing = isRefreshing,
        bulkWifi = bulkWifi,
        bulkMobile = bulkMobile,
        bulkBypass = bulkBypass,
        bulkBypassDns = bulkBypassDns,
        bulkExclude = bulkExclude,
        bulkLockdown = bulkLockdown,
        showBulkUpdateDialog = showBulkUpdateDialog,
        bulkDialogTitle = bulkDialogTitle,
        bulkDialogMessage = bulkDialogMessage,
        bulkDialogType = bulkDialogType,
        showInfoDialog = showInfoDialog,
        currentFilters = currentFilters,
        onQueryChange = { query ->
            queryText = query
            searchQuery.value = query
        },
        onRefreshClick = { refreshAppList() },
        onFilterApply = { applied -> applyFilters(applied) },
        onFilterClear = { cleared -> applyFilters(cleared) },
        onFirewallFilterClick = { filter ->
            val updated = currentFilters.copy().apply { firewallFilter = filter }
            applyFilters(updated)
        },
        onBulkDialogConfirm = { type ->
            showBulkUpdateDialog = false
            bulkDialogType = null
            updateBulkRules(type)
        },
        onBulkDialogDismiss = {
            showBulkUpdateDialog = false
            bulkDialogType = null
        },
        onInfoDialogDismiss = { showInfoDialog = false },
        onShowInfoDialog = { showInfoDialog = true },
        onShowBulkDialog = { type ->
            bulkDialogTitle = getBulkActionDialogTitle(type)
            bulkDialogMessage = getBulkActionDialogMessage(type)
            bulkDialogType = type
            showBulkUpdateDialog = true
        },
        onBypassDnsTooltip = {
            showBypassToolTip = false
            Utilities.showToastUiCentered(
                context,
                context.getString(R.string.bypass_dns_firewall_tooltip, context.getString(R.string.bypass_dns_firewall)),
                Toast.LENGTH_SHORT
            )
        },
        showBypassToolTip = showBypassToolTip,
        onBackClick = onBackClick
    )
}

// Extension to copy a Filters object
private fun Filters.copy(): Filters {
    return Filters().also {
        it.categoryFilters = this.categoryFilters.toMutableSet()
        it.topLevelFilter = this.topLevelFilter
        it.firewallFilter = this.firewallFilter
        it.searchString = this.searchString
    }
}
