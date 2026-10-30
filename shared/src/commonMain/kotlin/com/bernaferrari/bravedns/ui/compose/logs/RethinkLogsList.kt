/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.foundation.layout.padding
import com.bernaferrari.bravedns.ui.compose.common.RethinkErrorState
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

enum class RethinkLogFilter { All, Allowed, Blocked }

sealed interface RethinkLogListState {
    data object Loading : RethinkLogListState
    data object Empty : RethinkLogListState
    data class Error(val onRetry: () -> Unit) : RethinkLogListState
    data class Content(val rows: List<RethinkLogRowModel>) : RethinkLogListState
}

/** State supplied by a target's paged log source; the list treatment stays shared. */
sealed interface RethinkPagedLogListState {
    data object Content : RethinkPagedLogListState
    data object Loading : RethinkPagedLogListState
    data object Empty : RethinkPagedLogListState
    data class Error(val onRetry: () -> Unit) : RethinkPagedLogListState
}

data class RethinkLogListStrings(
    val all: String,
    val allowed: String,
    val blocked: String,
    val loading: String,
    val empty: String,
    val error: String,
    val retry: String,
)

@Composable
fun RethinkLogFilterRow(
    selected: RethinkLogFilter,
    strings: RethinkLogListStrings,
    onSelected: (RethinkLogFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Every filter shows its icon and label, so the row never shifts as the selection moves.
    val options = listOf(
        RethinkChoiceOption(RethinkLogFilter.All, strings.all, MaterialSymbols.Filled.FormatListBulleted),
        RethinkChoiceOption(RethinkLogFilter.Allowed, strings.allowed, MaterialSymbols.Filled.CheckCircle),
        RethinkChoiceOption(RethinkLogFilter.Blocked, strings.blocked, MaterialSymbols.Filled.DoNotDisturbOn),
    )
    RethinkConnectedChoiceGroup(
        options = options,
        selected = selected,
        onSelect = onSelected,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun RethinkLogList(
    state: RethinkLogListState,
    strings: RethinkLogListStrings,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingXs,
            bottom = SharedDimensions.screenPaddingHorizontal,
        ),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
    ) {
        when (state) {
            RethinkLogListState.Loading -> item { RethinkLoadingState(strings.loading, LogStateModifier, contained = true) }
            RethinkLogListState.Empty -> item { RethinkLogEmpty(strings.empty) }
            is RethinkLogListState.Error -> item { RethinkErrorState(strings.error, LogStateModifier, retryLabel = strings.retry, onRetry = state.onRetry, contained = true) }
            is RethinkLogListState.Content -> itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                RethinkLogRow(row, index, state.rows.size)
            }
        }
    }
}

/** Shared paging-list geometry and empty/loading/error states for target-owned paged data. */
@Composable
fun RethinkPagedLogList(
    itemCount: Int,
    state: RethinkPagedLogListState,
    strings: RethinkLogListStrings,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = SharedDimensions.screenPaddingHorizontal,
    itemContent: @Composable (index: Int, itemCount: Int) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingXs,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
    ) {
        when (state) {
            RethinkPagedLogListState.Loading -> item("logs_loading") { RethinkLoadingState(strings.loading, LogStateModifier, contained = true) }
            RethinkPagedLogListState.Empty -> item("logs_empty") { RethinkLogEmpty(strings.empty) }
            is RethinkPagedLogListState.Error -> item("logs_load_error") {
                RethinkErrorState(strings.error, LogStateModifier, retryLabel = strings.retry, onRetry = state.onRetry, contained = true)
            }
            RethinkPagedLogListState.Content -> items(itemCount) { index -> itemContent(index, itemCount) }
        }
    }
}

/** In-list states sit one small gap below the filter row, in the grouped-list container. */
private val LogStateModifier = Modifier.padding(top = SharedDimensions.spacingSm)

@Composable
private fun RethinkLogEmpty(label: String) =
    RethinkEmptyState(title = label, modifier = LogStateModifier, icon = MaterialSymbols.Filled.Info, contained = true)
