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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.FirewallAppRow
import com.celzero.bravedns.adapter.FirewallRowPosition
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkBottomSheetActionRow
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.theme.RethinkFilterChip
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.celzero.bravedns.ui.compose.theme.RethinkSearchField
import com.celzero.bravedns.ui.compose.theme.RethinkSecondaryActionStyle
import com.celzero.bravedns.ui.compose.theme.RethinkSegmentedChoiceRow
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.viewmodel.AppInfoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.foundation.ExperimentalFoundationApi
import com.celzero.bravedns.database.AppInfo

private const val ANIMATION_DURATION = 750
private val FAST_SCROLLER_LIST_END_PADDING = 32.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppInfoViewModel,
    eventLogger: EventLogger,
    queryText: String,
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
    onAppClick: ((Int) -> Unit)? = null,
    onBackClick: (() -> Unit)? = null
) {
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
    var isSearchOpen by rememberSaveable { mutableStateOf(queryText.isNotBlank()) }

    val hasActiveFilters = currentFilters?.let {
        it.topLevelFilter != TopLevelFilter.INSTALLED ||
                it.categoryFilters.isNotEmpty() ||
                selectedFirewallFilter != FirewallFilter.ALL
    } ?: (selectedFirewallFilter != FirewallFilter.ALL)

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
        RethinkConfirmDialog(
            onDismissRequest = onBulkDialogDismiss,
            title = bulkDialogTitle,
            message = bulkDialogMessage,
            confirmText = stringResource(R.string.lbl_apply),
            dismissText = stringResource(R.string.lbl_cancel),
            onConfirm = { onBulkDialogConfirm(bulkDialogType) },
            onDismiss = onBulkDialogDismiss
        )
    }

    if (showInfoDialog) {
        RethinkConfirmDialog(
            onDismissRequest = onInfoDialogDismiss,
            text = { FirewallInfoDialogContent() },
            confirmText = stringResource(R.string.fapps_info_dialog_positive_btn),
            onConfirm = onInfoDialogDismiss
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RethinkTopBar(
                title = stringResource(R.string.apps_info_title),
                onBackClick = onBackClick,
                actions = {
                    IconButton(
                        onClick = {
                            if (isSearchOpen && queryText.isNotBlank()) {
                                onQueryChange("")
                                isSearchOpen = false
                            } else {
                                isSearchOpen = !isSearchOpen
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSearchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription =
                                if (isSearchOpen) {
                                    stringResource(R.string.cd_clear_search)
                                } else {
                                    stringResource(R.string.lbl_search)
                                }
                        )
                    }
                    IconButton(
                        onClick = onRefreshClick,
                        enabled = !isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh),
                            modifier = Modifier.rotate(if (isRefreshing) refreshRotation.value else 0f)
                        )
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Box {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.cd_filter)
                            )
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
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            AppListControlsCompact(
                queryText = queryText,
                isSearchOpen = isSearchOpen,
                selectedFirewallFilter = selectedFirewallFilter,
                bulkWifi = bulkWifi,
                bulkMobile = bulkMobile,
                bulkBypass = bulkBypass,
                bulkBypassDns = bulkBypassDns,
                bulkExclude = bulkExclude,
                bulkLockdown = bulkLockdown,
                showBypassToolTip = showBypassToolTip,
                onQueryChange = onQueryChange,
                onSearchToggle = { isSearchOpen = it },
                onFirewallFilterClick = onFirewallFilterClick,
                onShowInfoDialog = onShowInfoDialog,
                onShowBulkDialog = onShowBulkDialog,
                onBypassDnsTooltip = onBypassDnsTooltip
            )

            AppListRecycler(
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                eventLogger = eventLogger,
                searchQuery = queryText,
                onAppClick = onAppClick
            )
        }
    }
}

@Composable
private fun AppListControlsCompact(
    queryText: String,
    isSearchOpen: Boolean,
    selectedFirewallFilter: FirewallFilter,
    bulkWifi: Boolean,
    bulkMobile: Boolean,
    bulkBypass: Boolean,
    bulkBypassDns: Boolean,
    bulkExclude: Boolean,
    bulkLockdown: Boolean,
    showBypassToolTip: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onFirewallFilterClick: (FirewallFilter) -> Unit,
    onShowInfoDialog: () -> Unit,
    onShowBulkDialog: (BlockType) -> Unit,
    onBypassDnsTooltip: () -> Unit
) {
    val context = LocalContext.current
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val quickFilters = listOf(FirewallFilter.ALL, FirewallFilter.ALLOWED, FirewallFilter.BLOCKED)

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            delay(120)
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.screenPaddingHorizontal)
            .padding(bottom = Dimensions.spacingXs),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isSearchOpen) {
            RethinkSearchField(
                query = queryText,
                onQueryChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester),
                placeholder = stringResource(R.string.lbl_search),
                onClearQuery = { onQueryChange("") },
                onCloseWhenEmpty = { onSearchToggle(false) },
                clearQueryContentDescription = stringResource(R.string.cd_clear_search),
                closeWhenEmptyContentDescription = stringResource(R.string.configure_search_close),
                shape = RoundedCornerShape(Dimensions.cornerRadiusMdLg),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                iconSize = 20.dp,
                trailingIconSize = 16.dp,
                trailingIconButtonSize = 32.dp
            )
        }

        RethinkSegmentedChoiceRow(
            options = quickFilters,
            selectedOption = selectedFirewallFilter,
            onOptionSelected = onFirewallFilterClick,
            modifier = Modifier.fillMaxWidth(),
            fillEqually = true,
            icon = { filter, _ ->
                val imageVector =
                    when (filter) {
                        FirewallFilter.ALL -> Icons.Rounded.Tune
                        FirewallFilter.ALLOWED -> Icons.Rounded.CheckCircle
                        FirewallFilter.BLOCKED -> Icons.Rounded.Block
                        else -> Icons.Rounded.Tune
                    }
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { filter, _ ->
                Text(
                    text = filter.getLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BulkChip(
                    icon = if (bulkLockdown) R.drawable.ic_firewall_lockdown_on else R.drawable.ic_firewall_lockdown_off,
                    label = stringResource(R.string.fapps_firewall_filter_isolate),
                    isActive = bulkLockdown,
                    onClick = { onShowBulkDialog(BlockType.LOCKDOWN) }
                )
                BulkChip(
                    icon = if (bulkExclude) R.drawable.ic_firewall_exclude_on else R.drawable.ic_firewall_exclude_off,
                    label = stringResource(R.string.fapps_firewall_filter_excluded),
                    isActive = bulkExclude,
                    onClick = { onShowBulkDialog(BlockType.EXCLUDE) }
                )
                BulkChip(
                    icon = if (bulkBypass) R.drawable.ic_firewall_bypass_on else R.drawable.ic_firewall_bypass_off,
                    label = stringResource(R.string.fapps_firewall_filter_bypass_universal),
                    isActive = bulkBypass,
                    onClick = { onShowBulkDialog(BlockType.BYPASS) }
                )
                BulkChip(
                    icon = if (bulkBypassDns) R.drawable.ic_bypass_dns_firewall_on else R.drawable.ic_bypass_dns_firewall_off,
                    label = stringResource(R.string.lbl_dns),
                    isActive = bulkBypassDns,
                    onClick = {
                        if (showBypassToolTip) onBypassDnsTooltip()
                        else onShowBulkDialog(BlockType.BYPASS_DNS_FIREWALL)
                    }
                )
                BulkChip(
                    icon = if (bulkWifi) R.drawable.ic_firewall_wifi_off else R.drawable.ic_firewall_wifi_on_grey,
                    label = stringResource(R.string.ada_app_unmetered),
                    isActive = bulkWifi,
                    onClick = { onShowBulkDialog(BlockType.UNMETER) }
                )
                BulkChip(
                    icon = if (bulkMobile) R.drawable.ic_firewall_data_off else R.drawable.ic_firewall_data_on_grey,
                    label = stringResource(R.string.lbl_mobile_data),
                    isActive = bulkMobile,
                    onClick = { onShowBulkDialog(BlockType.METER) }
                )
            }

            IconButton(
                onClick = onShowInfoDialog,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = stringResource(R.string.lbl_info),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BulkChip(
    icon: Int,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    RethinkFilterChip(
        label = label,
        selected = isActive,
        onClick = onClick,
        textStyle = MaterialTheme.typography.labelMedium,
        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(Dimensions.cornerRadiusMd),
        border = BorderStroke(
            1.dp,
            if (isActive) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
        ),
        leadingIcon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        modifier = Modifier
    )
}

@Composable
private fun AppListRecycler(
    modifier: Modifier = Modifier,
    viewModel: AppInfoViewModel,
    eventLogger: EventLogger,
    searchQuery: String,
    onAppClick: ((Int) -> Unit)? = null
) {
    val items by viewModel.appInfo.collectAsState()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = Dimensions.screenPaddingHorizontal),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(Dimensions.cornerRadius2xl),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.fapps_empty_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.fapps_empty_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    val showFastScroller = items.size >= 8
    val fastScrollerKeys = remember(items) { buildFastScrollerIndexKeys(items) }

    Box(modifier = modifier.fillMaxSize()) {
        AppListContent(
            loadedItems = items,
            listState = listState,
            eventLogger = eventLogger,
            searchQuery = searchQuery,
            showFastScroller = showFastScroller,
            onAppClick = onAppClick
        )

        if (showFastScroller) {
            IndexedFastScroller(
                items = fastScrollerKeys,
                listState = listState,
                getIndexKey = { it },
                scrollItemOffset = 2,
                minItemCount = 8,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(vertical = Dimensions.spacingSm)
                    .padding(end = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListContent(
    loadedItems: List<AppInfo>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    eventLogger: EventLogger,
    searchQuery: String,
    showFastScroller: Boolean,
    onAppClick: ((Int) -> Unit)? = null
) {
    val density = LocalDensity.current
    val navBarBottomInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dimensions.screenPaddingHorizontal,
            end = Dimensions.screenPaddingHorizontal + if (showFastScroller) FAST_SCROLLER_LIST_END_PADDING else 8.dp,
            top = Dimensions.spacingXs,
            bottom = Dimensions.screenPaddingHorizontal + navBarBottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        loadedItems.forEachIndexed { index, item ->
            val currentInitial = appInitial(item.appName, item.packageName)
            val previousItem = loadedItems.getOrNull(index - 1)
            val previousInitial =
                previousItem?.let { appInitial(it.appName, it.packageName) }
            val nextItem = loadedItems.getOrNull(index + 1)
            val nextInitial =
                nextItem?.let { appInitial(it.appName, it.packageName) }
            val isFirstInGroup = previousInitial == null || currentInitial != previousInitial
            val isLastInGroup = nextInitial == null || currentInitial != nextInitial

            val rowPosition =
                when {
                    isFirstInGroup && isLastInGroup -> FirewallRowPosition.Single
                    isFirstInGroup -> FirewallRowPosition.First
                    isLastInGroup -> FirewallRowPosition.Last
                    else -> FirewallRowPosition.Middle
                }

            if (index == 0 || isFirstInGroup) {
                stickyHeader(key = "header_$currentInitial") {
                    AppListLetterHeader(letter = currentInitial)
                }
            }

            item(key = "app_${item.uid}_${item.packageName}") {
                FirewallAppRow(
                    appInfo = item,
                    eventLogger = eventLogger,
                    searchQuery = searchQuery,
                    rowPosition = rowPosition,
                    onAppClick = onAppClick
                )
            }
        }
    }
}

@Composable
private fun AppListLetterHeader(letter: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun appInitial(appName: String, packageName: String): String {
    val source = appName.ifBlank { packageName }.trim()
    if (source.isEmpty()) return "#"
    val first = source.first()
    return if (first.isLetter()) {
        first.uppercaseChar().toString()
    } else {
        source.first().toString().uppercase(Locale.getDefault())
    }
}

private fun buildFastScrollerIndexKeys(loadedItems: List<AppInfo>): List<String> {
    val indexKeys = mutableListOf<String>()
    var previousInitial: String? = null

    loadedItems.forEach { item ->
        val initial = appInitial(item.appName, item.packageName)
        if (initial != previousInitial) {
            indexKeys.add(initial) // sticky header index
            previousInitial = initial
        }
        indexKeys.add(item.appName.ifBlank { item.packageName }) // app row index
    }

    return indexKeys
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
        InfoRow(R.drawable.ic_firewall_wifi_on_grey, stringResource(R.string.fapps_info_unmetered_msg))
        InfoRow(R.drawable.ic_firewall_data_on_grey, stringResource(R.string.fapps_info_metered_msg))
        InfoRow(R.drawable.ic_firewall_bypass_off, stringResource(R.string.fapps_info_bypass_msg))
        InfoRow(R.drawable.ic_bypass_dns_firewall_off, stringResource(R.string.fapps_info_bypass_dns_firewall_msg))
        InfoRow(R.drawable.ic_firewall_exclude_off, stringResource(R.string.fapps_info_exclude_msg))
        InfoRow(R.drawable.ic_firewall_lockdown_off, stringResource(R.string.fapps_info_isolate_msg))
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
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
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
    var topFilter by remember {
        mutableStateOf(initialFilters?.topLevelFilter ?: TopLevelFilter.INSTALLED)
    }
    val selectedCategories = remember {
        mutableStateListOf<String>().apply {
            if (initialFilters != null) addAll(initialFilters.categoryFilters)
        }
    }
    val categories = remember { mutableStateListOf<String>() }

    LaunchedEffect(topFilter) {
        val result = fetchCategories(topFilter)
        categories.clear()
        categories.addAll(result)
        selectedCategories.retainAll(result.toSet())
    }

    RethinkModalBottomSheet(
        onDismissRequest = onDismiss,
        contentPadding = PaddingValues(0.dp),
        verticalSpacing = 0.dp,
        includeBottomSpacer = true
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Dimensions.screenPaddingHorizontal,
                vertical = Dimensions.spacingSm
            )
        ) {
            SectionHeader(title = stringResource(R.string.fapps_filter_filter_heading))

            RethinkListGroup {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimensions.spacingMd, vertical = Dimensions.spacingMd)
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
            }

            Spacer(modifier = Modifier.height(Dimensions.spacingLg))

            SectionHeader(title = stringResource(R.string.fapps_filter_categories_heading))

            RethinkListGroup {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimensions.spacingMd, vertical = Dimensions.spacingMd)
                ) {
                    categories.forEach { category ->
                        RethinkFilterChip(
                            label = category,
                            selected = selectedCategories.contains(category),
                            onClick = {
                                if (selectedCategories.contains(category)) selectedCategories.remove(category)
                                else selectedCategories.add(category)
                            },
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.spacingXl))

            RethinkBottomSheetActionRow(
                primaryText = stringResource(R.string.lbl_apply),
                onPrimaryClick = {
                    onApply(
                        Filters(
                            categoryFilters = selectedCategories.toSet(),
                            topLevelFilter = topFilter,
                            firewallFilter = firewallFilter,
                            searchString = initialFilters?.searchString.orEmpty()
                        )
                    )
                },
                secondaryText = stringResource(R.string.fapps_filter_clear_btn),
                onSecondaryClick = {
                    selectedCategories.clear()
                    topFilter = TopLevelFilter.INSTALLED
                    onClear(
                        Filters(
                            topLevelFilter = TopLevelFilter.INSTALLED,
                            firewallFilter = firewallFilter,
                            searchString = initialFilters?.searchString.orEmpty()
                        )
                    )
                },
                secondaryStyle = RethinkSecondaryActionStyle.TEXT
            )
        }
    }
}

@Composable
private fun TopFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    RethinkFilterChip(
        label = label,
        selected = selected,
        onClick = onClick,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(Dimensions.cornerRadiusMdLg),
        selectedLabelWeight = FontWeight.SemiBold,
        defaultLabelWeight = FontWeight.Normal
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
