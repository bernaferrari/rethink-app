/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Labels are always visible (M3 Expressive: never drop labels); on narrow widths the
        // decorative glyphs go first so the text keeps its room.
        val showIcons = maxWidth >= COMPACT_FILTER_WIDTH
        val options = listOf(
            RethinkChoiceOption(RethinkLogFilter.All, strings.all, MaterialSymbols.Filled.Public.takeIf { showIcons }),
            RethinkChoiceOption(RethinkLogFilter.Allowed, strings.allowed, MaterialSymbols.Filled.CheckCircle.takeIf { showIcons }),
            RethinkChoiceOption(RethinkLogFilter.Blocked, strings.blocked, MaterialSymbols.Filled.Block.takeIf { showIcons }),
        )
        RethinkConnectedChoiceGroup(
            options = options,
            selected = selected,
            onSelect = onSelected,
        )
    }
}

private val COMPACT_FILTER_WIDTH = 420.dp

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
            RethinkLogListState.Loading -> item { RethinkLogLoadingState(strings.loading) }
            RethinkLogListState.Empty -> item { RethinkLogEmptyState(strings.empty) }
            is RethinkLogListState.Error -> item { RethinkLogErrorState(strings.error, strings.retry, state.onRetry) }
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
            RethinkPagedLogListState.Loading -> item("logs_loading") { RethinkLogLoadingState(strings.loading) }
            RethinkPagedLogListState.Empty -> item("logs_empty") { RethinkLogEmptyState(strings.empty) }
            is RethinkPagedLogListState.Error -> item("logs_load_error") {
                RethinkLogErrorState(strings.error, strings.retry, state.onRetry)
            }
            RethinkPagedLogListState.Content -> items(itemCount) { index -> itemContent(index, itemCount) }
        }
    }
}
