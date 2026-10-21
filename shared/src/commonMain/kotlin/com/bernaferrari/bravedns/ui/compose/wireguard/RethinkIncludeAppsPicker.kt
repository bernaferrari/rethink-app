/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.wireguard

import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.bernaferrari.bravedns.ui.compose.components.RethinkFastScrollerMark
import com.bernaferrari.bravedns.ui.compose.components.RethinkIndexedFastScroller
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

enum class RethinkIncludeAppsFilter { All, Selected }

data class RethinkIncludeAppsPickerItem(
    val id: String,
    val title: String,
    val sectionKey: String,
)

data class RethinkIncludeAppsPickerStrings(
    val search: String,
    val clearSearch: String,
    val all: String,
    val selected: String,
    val refresh: String,
    val loading: String,
    val selectAll: String,
    val unselectAll: String,
    val done: String,
    val empty: String,
    val more: String,
)

/** Shared full-screen app picker; hosts own filtering, database updates, and app artwork. */
@Composable
fun RethinkIncludeAppsPicker(
    title: String,
    items: List<RethinkIncludeAppsPickerItem>,
    query: String,
    selectedFilter: RethinkIncludeAppsFilter,
    allItemsSelected: Boolean,
    isRefreshing: Boolean,
    strings: RethinkIncludeAppsPickerStrings,
    onBackClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (RethinkIncludeAppsFilter) -> Unit,
    onRefresh: () -> Unit,
    onToggleAll: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (RethinkIncludeAppsPickerItem, CardPosition) -> Unit,
) {
    val listState = rememberLazyListState()
    val showFastScroller = items.size >= 8
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onDone,
                icon = { Icon(MaterialSymbols.Filled.Check, null) },
                text = { Text(strings.done) },
                elevation = FloatingActionButtonDefaults.loweredElevation(),
            )
        },
        topBar = {
            RethinkTopBar(
                title = title,
                onBackClick = onBackClick,
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
                actions = {
                    RethinkIncludeAppsFilterToggle(selectedFilter, strings, onFilterChange)
                    RethinkIncludeAppsOverflow(allItemsSelected, isRefreshing, strings, onRefresh, onToggleAll)
                },
            )
        },
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
                contentPadding = PaddingValues(
                    end = if (showFastScroller) SharedDimensions.spacing2xl else SharedDimensions.spacingNone,
                    bottom = SharedDimensions.buttonHeightLg + SharedDimensions.spacing3xl,
                ),
            ) {
                item {
                    RethinkSearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        placeholder = strings.search,
                        onClearQuery = { onQueryChange("") },
                        clearQueryContentDescription = strings.clearSearch,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        iconSize = SharedDimensions.iconSizeSm,
                        trailingIconSize = SharedDimensions.iconSizeXs,
                        trailingIconButtonSize = SharedDimensions.touchTargetMin,
                        modifier = Modifier.fillMaxWidth().padding(bottom = SharedDimensions.spacingSm),
                    )
                }
                if (items.isEmpty()) {
                    item { Text(strings.empty, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(SharedDimensions.cardPadding)) }
                }
                items.forEachIndexed { index, item ->
                    val previous = items.getOrNull(index - 1)
                    val next = items.getOrNull(index + 1)
                    val first = previous == null || previous.sectionKey != item.sectionKey
                    val last = next == null || next.sectionKey != item.sectionKey
                    if (first) {
                        stickyHeader(key = "include_apps_header_${item.sectionKey}") {
                            SectionHeader(
                                title = item.sectionKey,
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                            )
                        }
                    }
                    item(key = item.id) {
                        itemContent(item, when {
                            first && last -> CardPosition.Single
                            first -> CardPosition.First
                            last -> CardPosition.Last
                            else -> CardPosition.Middle
                        })
                    }
                }
            }
            if (showFastScroller) {
                RethinkIndexedFastScroller(
                    marks = remember(items) { rethinkIncludeAppsScrollerMarks(items) },
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = SharedDimensions.spacingSm).padding(end = SharedDimensions.spacingGridTile),
                )
            }
        }
    }
}

@Composable
private fun RethinkIncludeAppsFilterToggle(
    selected: RethinkIncludeAppsFilter,
    strings: RethinkIncludeAppsPickerStrings,
    onFilterChange: (RethinkIncludeAppsFilter) -> Unit,
) {
    val options = listOf(RethinkIncludeAppsFilter.All to strings.all, RethinkIncludeAppsFilter.Selected to strings.selected)
    RethinkConnectedChoiceGroup(
        options = options.map { (filter, label) -> RethinkChoiceOption(filter, label) },
        selected = selected,
        onSelect = onFilterChange,
        fillWidth = false,
    )
}

@Composable
private fun RethinkIncludeAppsOverflow(
    allItemsSelected: Boolean,
    isRefreshing: Boolean,
    strings: RethinkIncludeAppsPickerStrings,
    onRefresh: () -> Unit,
    onToggleAll: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(MaterialSymbols.Filled.MoreVert, strings.more) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                leadingIcon = { Icon(MaterialSymbols.Filled.Refresh, null) },
                text = { Text(if (isRefreshing) strings.loading else strings.refresh) },
                enabled = !isRefreshing,
                onClick = { expanded = false; onRefresh() },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(if (allItemsSelected) MaterialSymbols.Filled.Clear else MaterialSymbols.Filled.Check, null) },
                text = { Text(if (allItemsSelected) strings.unselectAll else strings.selectAll) },
                onClick = { expanded = false; onToggleAll() },
            )
        }
    }
}

internal fun rethinkIncludeAppsScrollerMarks(
    items: List<RethinkIncludeAppsPickerItem>,
    searchHeaderCount: Int = 1,
): List<RethinkFastScrollerMark> {
    val marks = mutableListOf<RethinkFastScrollerMark>()
    var listIndex = searchHeaderCount
    var previous: String? = null
    items.forEach { item ->
        val label = item.sectionKey.ifBlank { item.title }
        if (previous != item.sectionKey) {
            marks += RethinkFastScrollerMark(label, listIndex)
            listIndex++
            previous = item.sectionKey
        }
        listIndex++
    }
    return marks
}
