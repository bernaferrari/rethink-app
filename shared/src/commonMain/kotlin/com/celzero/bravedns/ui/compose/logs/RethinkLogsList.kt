/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.celzero.bravedns.ui.compose.logs

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

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
    val options = listOf(
        Triple(RethinkLogFilter.All, strings.all, MaterialSymbols.Filled.Public),
        Triple(RethinkLogFilter.Allowed, strings.allowed, MaterialSymbols.Filled.CheckCircle),
        Triple(RethinkLogFilter.Blocked, strings.blocked, MaterialSymbols.Filled.Block),
    )
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < 420.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { index, (filter, label, icon) ->
                val isSelected = filter == selected
                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { if (it && !isSelected) onSelected(filter) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = null,
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                ) {
                    Icon(icon, contentDescription = if (compact) label else null)
                    if (!compact) {
                        Text(
                            label,
                            modifier = Modifier.padding(start = SharedDimensions.spacingXs),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
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
        verticalArrangement = Arrangement.spacedBy(2.dp),
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
        verticalArrangement = Arrangement.spacedBy(2.dp),
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
