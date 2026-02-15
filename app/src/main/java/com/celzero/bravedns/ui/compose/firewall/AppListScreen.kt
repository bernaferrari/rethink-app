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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
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
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.ui.compose.theme.SectionHeader
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
            INSTALLED -> context.resources.getString(R.string.fapps_filter_parent_installed)
            SYSTEM -> context.resources.getString(R.string.fapps_filter_parent_system)
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
            ALL -> context.resources.getString(R.string.lbl_all)
            ALLOWED -> context.resources.getString(R.string.lbl_allowed)
            BLOCKED_WIFI -> context.resources.getString(R.string.two_argument_colon, context.resources.getString(R.string.lbl_blocked), context.resources.getString(R.string.firewall_rule_block_unmetered))
            BLOCKED_MOBILE_DATA -> context.resources.getString(R.string.two_argument_colon, context.resources.getString(R.string.lbl_blocked), context.resources.getString(R.string.firewall_rule_block_metered))
            BLOCKED -> context.resources.getString(R.string.lbl_blocked)
            BYPASS -> context.resources.getString(R.string.fapps_firewall_filter_bypass_universal)
            EXCLUDED -> context.resources.getString(R.string.fapps_firewall_filter_excluded)
            LOCKDOWN -> context.resources.getString(R.string.fapps_firewall_filter_isolate)
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
                    onClick = { onBulkDialogConfirm(bulkDialogType) }
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

    Scaffold(
        topBar = {
            if (onBackClick != null) {
                RethinkTopBar(
                    title = stringResource(R.string.apps_info_title),
                    onBackClick = onBackClick
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar with gradient background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(
                        horizontal = Dimensions.screenPaddingHorizontal,
                        vertical = Dimensions.spacingMd
                    )
            ) {
                Column {
                    // Search field with action buttons
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = Dimensions.Elevation.none
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Dimensions.spacingSm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = Dimensions.spacingSm)
                                    .size(Dimensions.iconSizeMd)
                            )
                            OutlinedTextField(
                                value = queryText,
                                onValueChange = onQueryChange,
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.search_firewall_all_apps),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = Dimensions.Opacity.MEDIUM
                                        )
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                                )
                            )
                            if (queryText.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_clear_search),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(Dimensions.iconSizeSm)
                                    )
                                }
                            }
                            // Refresh with tinted background
                            IconButton(
                                onClick = onRefreshClick,
                                enabled = !isRefreshing,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh",
                                    modifier = Modifier
                                        .size(Dimensions.iconSizeMd)
                                        .rotate(if (isRefreshing) refreshRotation.value else 0f)
                                )
                            }
                            // Filter with tinted background when active
                            val hasActiveFilters = currentFilters?.let {
                                it.topLevelFilter != TopLevelFilter.ALL ||
                                    it.categoryFilters.isNotEmpty()
                            } ?: false
                            IconButton(
                                onClick = { showFilterSheet = true },
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = if (hasActiveFilters)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Box {
                                    Icon(
                                        imageVector = Icons.Rounded.FilterList,
                                        contentDescription = "Filter"
                                    )
                                    // Active indicator dot
                                    if (hasActiveFilters) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .align(Alignment.TopEnd)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.spacingMd))

                    // Filter chips with tonal coloring
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
                    ) {
                        items(FirewallFilter.entries.size) { index ->
                            val filter = FirewallFilter.entries[index]
                            val label = filter.getLabel(context)
                            if (label.isNotEmpty()) {
                                val selected = selectedFirewallFilter == filter
                                FilterChip(
                                    selected = selected,
                                    onClick = { onFirewallFilterClick(filter) },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.SemiBold
                                                else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    // Filter label
                    if (filterLabelText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Dimensions.spacingSm))
                        Text(
                            text = filterLabelText.toString(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Dimensions.spacingSm),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = Dimensions.Opacity.MEDIUM
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Bulk toggle toolbar — expressive with tinted icon backgrounds
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

            // App list
            AppListRecycler(viewModel = viewModel, eventLogger = eventLogger)
        }
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Dimensions.Elevation.none
    ) {
        Column {
            HorizontalDivider(
                thickness = Dimensions.dividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = Dimensions.Opacity.LOW
                )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Dimensions.spacingSm,
                        vertical = Dimensions.spacingSm
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BulkActionItem(
                    icon = R.drawable.ic_info_white,
                    label = stringResource(R.string.lbl_info),
                    isActive = false,
                    onClick = onShowInfoDialog
                )
                BulkActionItem(
                    icon = if (bulkLockdown) R.drawable.ic_firewall_lockdown_on else R.drawable.ic_firewall_lockdown_off,
                    label = stringResource(R.string.fapps_firewall_filter_isolate),
                    isActive = bulkLockdown,
                    onClick = { onShowBulkDialog(BlockType.LOCKDOWN) }
                )
                BulkActionItem(
                    icon = if (bulkExclude) R.drawable.ic_firewall_exclude_on else R.drawable.ic_firewall_exclude_off,
                    label = stringResource(R.string.fapps_firewall_filter_excluded),
                    isActive = bulkExclude,
                    onClick = { onShowBulkDialog(BlockType.EXCLUDE) }
                )
                BulkActionItem(
                    icon = if (bulkBypass) R.drawable.ic_firewall_bypass_on else R.drawable.ic_firewall_bypass_off,
                    label = stringResource(R.string.fapps_firewall_filter_bypass_universal),
                    isActive = bulkBypass,
                    onClick = { onShowBulkDialog(BlockType.BYPASS) }
                )
                BulkActionItem(
                    icon = if (bulkBypassDns) R.drawable.ic_bypass_dns_firewall_on else R.drawable.ic_bypass_dns_firewall_off,
                    label = "DNS",
                    isActive = bulkBypassDns,
                    onClick = {
                        if (showBypassToolTip) {
                            onBypassDnsTooltip()
                        } else {
                            onShowBulkDialog(BlockType.BYPASS_DNS_FIREWALL)
                        }
                    }
                )
                BulkActionItem(
                    icon = if (bulkWifi) R.drawable.ic_firewall_wifi_off else R.drawable.ic_firewall_wifi_on_grey,
                    label = "Wi-Fi",
                    isActive = bulkWifi,
                    onClick = { onShowBulkDialog(BlockType.UNMETER) }
                )
                BulkActionItem(
                    icon = if (bulkMobile) R.drawable.ic_firewall_data_off else R.drawable.ic_firewall_data_on_grey,
                    label = stringResource(R.string.lbl_mobile_data),
                    isActive = bulkMobile,
                    onClick = { onShowBulkDialog(BlockType.METER) }
                )
            }
            HorizontalDivider(
                thickness = Dimensions.dividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = Dimensions.Opacity.LOW
                )
            )
        }
    }
}

@Composable
private fun BulkActionItem(
    icon: Int,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
        modifier = Modifier.width(48.dp)
    ) {
        // Tinted circle background when active (like ConfigureScreen icons)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isActive)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                )
        ) {
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = label,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun AppListRecycler(viewModel: AppInfoViewModel, eventLogger: EventLogger) {
    val items = viewModel.appInfo.asFlow().collectAsLazyPagingItems()
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
            FirewallAppRow(item, eventLogger)
        }
    }
}

@Composable
private fun FirewallInfoDialogContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.spacingLg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
    ) {
        Text(
            text = stringResource(R.string.fapps_info_dialog_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        HorizontalDivider(
            thickness = Dimensions.dividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimensions.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                )
        ) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
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
        Column(
            modifier = Modifier.padding(
                horizontal = Dimensions.screenPaddingHorizontal,
                vertical = Dimensions.spacingSm
            )
        ) {
            SectionHeader(title = stringResource(R.string.fapps_filter_filter_heading))

            Spacer(modifier = Modifier.height(Dimensions.spacingSm))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm),
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

            Spacer(modifier = Modifier.height(Dimensions.spacingLg))

            SectionHeader(title = stringResource(R.string.fapps_filter_categories_heading))

            Spacer(modifier = Modifier.height(Dimensions.spacingSm))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm),
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
                        label = { Text(text = category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.spacingXl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMd, Alignment.End)
            ) {
                OutlinedButton(
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
                FilledTonalButton(
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

            Spacer(modifier = Modifier.height(Dimensions.spacing2xl))
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
                modifier = Modifier.padding(
                    horizontal = Dimensions.spacingMd,
                    vertical = Dimensions.spacingSm
                ),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.labelMedium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
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
