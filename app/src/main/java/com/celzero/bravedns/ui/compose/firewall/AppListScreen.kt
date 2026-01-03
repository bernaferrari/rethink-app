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
package com.celzero.bravedns.ui.compose.firewall

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.FirewallAppRow
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.viewmodel.AppInfoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ANIMATION_DURATION = 750

enum class BlockType {
    UNMETER,
    METER,
    BYPASS,
    LOCKDOWN,
    EXCLUDE,
    BYPASS_DNS_FIREWALL
}

enum class TopLevelFilter(val id: Int) {
    ALL(0),
    INSTALLED(1),
    SYSTEM(2);

    fun getLabel(context: Context): String {
        return when (this) {
            ALL -> ""
            INSTALLED -> context.getString(R.string.fapps_filter_parent_installed)
            SYSTEM -> context.getString(R.string.fapps_filter_parent_system)
        }
    }
}

@Suppress("MagicNumber")
enum class FirewallFilter(val id: Int) {
    ALL(0),
    ALLOWED(1),
    BLOCKED(2),
    BLOCKED_WIFI(3),
    BLOCKED_MOBILE_DATA(4),
    BYPASS(5),
    EXCLUDED(6),
    LOCKDOWN(7);

    fun getFilter(): Set<Int> {
        return when (this) {
            ALL -> setOf(0, 1, 2, 3, 4, 5, 7)
            ALLOWED -> setOf(5)
            BLOCKED_WIFI -> setOf(5)
            BLOCKED_MOBILE_DATA -> setOf(5)
            BLOCKED -> setOf(5)
            BYPASS -> setOf(2, 7)
            EXCLUDED -> setOf(3)
            LOCKDOWN -> setOf(4)
        }
    }

    fun getConnectionStatusFilter(): Set<Int> {
        return when (this) {
            ALL -> setOf(0, 1, 2, 3)
            ALLOWED -> setOf(3)
            BLOCKED_WIFI -> setOf(1)
            BLOCKED_MOBILE_DATA -> setOf(2)
            BLOCKED -> setOf(0)
            BYPASS -> setOf(0, 1, 2, 3)
            EXCLUDED -> setOf(0, 1, 2, 3)
            LOCKDOWN -> setOf(0, 1, 2, 3)
        }
    }

    fun getLabel(context: Context): String {
        return when (this) {
            ALL -> context.getString(R.string.lbl_all)
            ALLOWED -> context.getString(R.string.lbl_allowed)
            BLOCKED_WIFI -> context.getString(R.string.two_argument_colon, context.getString(R.string.lbl_blocked), context.getString(R.string.firewall_rule_block_unmetered))
            BLOCKED_MOBILE_DATA -> context.getString(R.string.two_argument_colon, context.getString(R.string.lbl_blocked), context.getString(R.string.firewall_rule_block_metered))
            BLOCKED -> context.getString(R.string.lbl_blocked)
            BYPASS -> context.getString(R.string.fapps_firewall_filter_bypass_universal)
            EXCLUDED -> context.getString(R.string.fapps_firewall_filter_excluded)
            LOCKDOWN -> context.getString(R.string.fapps_firewall_filter_isolate)
        }
    }

    companion object {
        fun filter(id: Int): FirewallFilter {
            return when (id) {
                ALL.id -> ALL
                ALLOWED.id -> ALLOWED
                BLOCKED_WIFI.id -> BLOCKED_WIFI
                BLOCKED_MOBILE_DATA.id -> BLOCKED_MOBILE_DATA
                BLOCKED.id -> BLOCKED
                BYPASS.id -> BYPASS
                EXCLUDED.id -> EXCLUDED
                LOCKDOWN.id -> LOCKDOWN
                else -> ALL
            }
        }
    }
}

class Filters {
    var categoryFilters: MutableSet<String> = mutableSetOf()
    var topLevelFilter = TopLevelFilter.ALL
    var firewallFilter = FirewallFilter.ALL
    var searchString: String = ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppInfoViewModel,
    eventLogger: EventLogger,
    queryText: String,
    filterLabelText: CharSequence,
    selectedFirewallFilter: FirewallFilter,
    isRefreshing: Boolean,
    bulkWifi: Boolean,
    bulkMobile: Boolean,
    bulkBypass: Boolean,
    bulkBypassDns: Boolean,
    bulkExclude: Boolean,
    bulkLockdown: Boolean,
    showBulkUpdateDialog: Boolean,
    bulkDialogTitle: String,
    bulkDialogMessage: String,
    bulkDialogType: BlockType?,
    showInfoDialog: Boolean,
    currentFilters: Filters?,
    onQueryChange: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onFilterApply: (Filters) -> Unit,
    onFilterClear: (Filters) -> Unit,
    onFirewallFilterClick: (FirewallFilter) -> Unit,
    onBulkDialogConfirm: (BlockType) -> Unit,
    onBulkDialogDismiss: () -> Unit,
    onInfoDialogDismiss: () -> Unit,
    onShowInfoDialog: () -> Unit,
    onShowBulkDialog: (BlockType) -> Unit,
    onBypassDnsTooltip: () -> Unit,
    showBypassToolTip: Boolean,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val refreshRotation = rememberInfiniteTransition(label = "refresh").animateFloat(
        initialValue = 0f,
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ANIMATION_DURATION, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refreshRotation"
    )

    var showFilterSheet by remember { mutableStateOf(false) }

    if (showFilterSheet) {
        FirewallAppFilterSheet(
            initialFilters = currentFilters,
            firewallFilter = selectedFirewallFilter,
            onDismiss = { showFilterSheet = false },
            onApply = { applied ->
                onFilterApply(applied)
                showFilterSheet = false
            },
            onClear = { cleared ->
                onFilterClear(cleared)
                showFilterSheet = false
            }
        )
    }

    if (showBulkUpdateDialog && bulkDialogType != null) {
        AlertDialog(
            onDismissRequest = onBulkDialogDismiss,
            title = { Text(text = bulkDialogTitle) },
            text = { Text(text = bulkDialogMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        bulkDialogType?.let { onBulkDialogConfirm(it) }
                    }
                ) {
                    Text(text = stringResource(R.string.lbl_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = onBulkDialogDismiss) {
                    Text(text = stringResource(R.string.lbl_cancel))
                }
            }
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = onInfoDialogDismiss,
            text = { FirewallInfoDialogContent() },
            confirmButton = {
                TextButton(onClick = onInfoDialogDismiss) {
                    Text(text = stringResource(R.string.fapps_info_dialog_positive_btn))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(text = stringResource(R.string.search_firewall_all_apps)) }
                    )
                    IconButton(
                        onClick = onRefreshClick,
                        enabled = !isRefreshing
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_refresh_white),
                            contentDescription = null,
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(if (isRefreshing) refreshRotation.value else 0f)
                        )
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Image(
                            painter = painterResource(R.drawable.ic_filter),
                            contentDescription = null
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    items(FirewallFilter.entries.size) { index ->
                        val filter = FirewallFilter.entries[index]
                        val label = filter.getLabel(context)
                        if (label.isNotEmpty()) {
                            FilterChip(
                                selected = selectedFirewallFilter == filter,
                                onClick = { onFirewallFilterClick(filter) },
                                label = { Text(text = label) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = filterLabelText.toString(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(fetchColor(context, R.attr.primaryTextColor)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        BulkToggleRow(
            bulkWifi = bulkWifi,
            bulkMobile = bulkMobile,
            bulkBypass = bulkBypass,
            bulkBypassDns = bulkBypassDns,
            bulkExclude = bulkExclude,
            bulkLockdown = bulkLockdown,
            showBypassToolTip = showBypassToolTip,
            onShowInfoDialog = onShowInfoDialog,
            onShowBulkDialog = onShowBulkDialog,
            onBypassDnsTooltip = onBypassDnsTooltip
        )
        AppListRecycler(viewModel = viewModel, eventLogger = eventLogger)
    }
}

@Composable
private fun BulkToggleRow(
    bulkWifi: Boolean,
    bulkMobile: Boolean,
    bulkBypass: Boolean,
    bulkBypassDns: Boolean,
    bulkExclude: Boolean,
    bulkLockdown: Boolean,
    showBypassToolTip: Boolean,
    onShowInfoDialog: () -> Unit,
    onShowBulkDialog: (BlockType) -> Unit,
    onBypassDnsTooltip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onShowInfoDialog) {
            Image(
                painter = painterResource(R.drawable.ic_info_white),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        BulkIconButton(
            icon = if (bulkLockdown) R.drawable.ic_firewall_lockdown_on else R.drawable.ic_firewall_lockdown_off,
            onClick = { onShowBulkDialog(BlockType.LOCKDOWN) }
        )
        BulkIconButton(
            icon = if (bulkExclude) R.drawable.ic_firewall_exclude_on else R.drawable.ic_firewall_exclude_off,
            onClick = { onShowBulkDialog(BlockType.EXCLUDE) }
        )
        BulkIconButton(
            icon = if (bulkBypass) R.drawable.ic_firewall_bypass_on else R.drawable.ic_firewall_bypass_off,
            onClick = { onShowBulkDialog(BlockType.BYPASS) }
        )
        BulkIconButton(
            icon = if (bulkBypassDns) R.drawable.ic_bypass_dns_firewall_on else R.drawable.ic_bypass_dns_firewall_off,
            onClick = {
                if (showBypassToolTip) {
                    onBypassDnsTooltip()
                } else {
                    onShowBulkDialog(BlockType.BYPASS_DNS_FIREWALL)
                }
            }
        )
        BulkIconButton(
            icon = if (bulkWifi) R.drawable.ic_firewall_wifi_off else R.drawable.ic_firewall_wifi_on_grey,
            onClick = { onShowBulkDialog(BlockType.UNMETER) }
        )
        BulkIconButton(
            icon = if (bulkMobile) R.drawable.ic_firewall_data_off else R.drawable.ic_firewall_data_on_grey,
            onClick = { onShowBulkDialog(BlockType.METER) }
        )
    }
}

@Composable
private fun BulkIconButton(icon: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun AppListRecycler(viewModel: AppInfoViewModel, eventLogger: EventLogger) {
    val items = viewModel.appInfo.asFlow().collectAsLazyPagingItems()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items.itemCount) { index ->
            val item = items[index] ?: return@items
            FirewallAppRow(item, eventLogger)
        }
    }
}

@Composable
private fun FirewallInfoDialogContent() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.fapps_info_dialog_message),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(fetchColor(context, R.attr.primaryTextColor))
        )

        Spacer(modifier = Modifier.height(2.dp))

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(fetchColor(context, R.attr.primaryLightColorText)))
        )

        InfoRow(
            icon = R.drawable.ic_firewall_wifi_on_grey,
            text = stringResource(R.string.fapps_info_unmetered_msg)
        )
        InfoRow(
            icon = R.drawable.ic_firewall_data_on_grey,
            text = stringResource(R.string.fapps_info_metered_msg)
        )
        InfoRow(
            icon = R.drawable.ic_firewall_bypass_off,
            text = stringResource(R.string.fapps_info_bypass_msg)
        )
        InfoRow(
            icon = R.drawable.ic_bypass_dns_firewall_off,
            text = stringResource(R.string.fapps_info_bypass_dns_firewall_msg)
        )
        InfoRow(
            icon = R.drawable.ic_firewall_exclude_off,
            text = stringResource(R.string.fapps_info_exclude_msg)
        )
        InfoRow(
            icon = R.drawable.ic_firewall_lockdown_off,
            text = stringResource(R.string.fapps_info_isolate_msg)
        )
    }
}

@Composable
private fun InfoRow(icon: Int, text: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(fetchColor(context, R.attr.primaryTextColor))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallAppFilterSheet(
    initialFilters: Filters?,
    firewallFilter: FirewallFilter,
    onDismiss: () -> Unit,
    onApply: (Filters) -> Unit,
    onClear: (Filters) -> Unit
) {
    val context = LocalContext.current
    var topFilter by remember {
        mutableStateOf(initialFilters?.topLevelFilter ?: TopLevelFilter.ALL)
    }
    val selectedCategories = remember {
        mutableStateListOf<String>().apply {
            if (initialFilters != null) {
                addAll(initialFilters.categoryFilters)
            }
        }
    }
    val categories = remember { mutableStateListOf<String>() }

    LaunchedEffect(topFilter) {
        val result = fetchCategories(topFilter)
        categories.clear()
        categories.addAll(result)
        selectedCategories.retainAll(result.toSet())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(3.dp)
                    .background(Color(fetchColor(context, R.attr.border)), MaterialTheme.shapes.small)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.fapps_filter_filter_heading),
                style = MaterialTheme.typography.titleMedium,
                color = Color(fetchColor(context, R.attr.secondaryTextColor)),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TopFilterChip(
                    label = stringResource(R.string.lbl_all),
                    selected = topFilter == TopLevelFilter.ALL,
                    onClick = { topFilter = TopLevelFilter.ALL }
                )
                TopFilterChip(
                    label = stringResource(R.string.fapps_filter_parent_installed),
                    selected = topFilter == TopLevelFilter.INSTALLED,
                    onClick = { topFilter = TopLevelFilter.INSTALLED }
                )
                TopFilterChip(
                    label = stringResource(R.string.fapps_filter_parent_system),
                    selected = topFilter == TopLevelFilter.SYSTEM,
                    onClick = { topFilter = TopLevelFilter.SYSTEM }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.fapps_filter_categories_heading),
                style = MaterialTheme.typography.titleMedium,
                color = Color(fetchColor(context, R.attr.secondaryTextColor)),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategories.contains(category),
                        onClick = {
                            if (selectedCategories.contains(category)) {
                                selectedCategories.remove(category)
                            } else {
                                selectedCategories.add(category)
                            }
                        },
                        label = { Text(text = category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = {
                        selectedCategories.clear()
                        topFilter = TopLevelFilter.ALL
                        val cleared = Filters().apply {
                            topLevelFilter = TopLevelFilter.ALL
                            this.firewallFilter = firewallFilter
                        }
                        onClear(cleared)
                    }
                ) {
                    Text(text = stringResource(R.string.fapps_filter_clear_btn))
                }
                TextButton(
                    onClick = {
                        val applied = Filters().apply {
                            topLevelFilter = topFilter
                            this.firewallFilter = firewallFilter
                            categoryFilters = selectedCategories.toMutableSet()
                        }
                        onApply(applied)
                    }
                ) {
                    Text(text = stringResource(R.string.lbl_apply))
                }
            }
        }
    }
}

@Composable
private fun TopFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            )
        }
    )
}

private suspend fun fetchCategories(filter: TopLevelFilter): List<String> {
    return withContext(Dispatchers.IO) {
        when (filter) {
            TopLevelFilter.ALL -> FirewallManager.getAllCategories()
            TopLevelFilter.INSTALLED -> FirewallManager.getCategoriesForInstalledApps()
            TopLevelFilter.SYSTEM -> FirewallManager.getCategoriesForSystemApps()
        }
    }
}
