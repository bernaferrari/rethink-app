/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.celzero.bravedns.ui.compose.settings

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.celzero.bravedns.ui.compose.theme.RethinkRadioChoiceList
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import kotlinx.coroutines.delay

/** Copy and platform callbacks used by the portable console-log UI. */
data class RethinkConsoleLogStrings(
    val title: String,
    val searchHint: String,
    val filterDescription: String,
    val shareDescription: String,
    val clearDescription: String,
    val reportLabel: String,
    val confirmLabel: String,
    val cancelLabel: String,
    val filterOptions: List<String>,
)

/**
 * Shared console-log chrome: header, debounced search, level picker, actions and report FAB.
 * Hosts own storage and paging; no Android database or VPN type reaches this renderer.
 */
@Composable
fun RethinkConsoleLogsScreen(
    strings: RethinkConsoleLogStrings,
    initialLogLevel: Int,
    onFilterChange: (String) -> Unit,
    onLogLevelSelected: (Int) -> Unit,
    onShareClick: () -> Unit,
    onClearClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filterVisible by remember { mutableStateOf(false) }
    var selectedLogLevel by remember(initialLogLevel) { mutableIntStateOf(initialLogLevel) }

    LaunchedEffect(query) {
        delay(1_000)
        onFilterChange(query)
    }

    if (filterVisible) {
        RethinkModalBottomSheet(
            onDismissRequest = { filterVisible = false },
        ) {
            Text(strings.filterDescription, style = MaterialTheme.typography.titleLarge)
            RethinkRadioChoiceList(
                options = strings.filterOptions.indices.toList(),
                selected = { selectedLogLevel == it },
                label = { strings.filterOptions[it] },
                onSelected = {
                    selectedLogLevel = it
                    onLogLevelSelected(it)
                    filterVisible = false
                },
            )
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(strings.reportLabel) },
                icon = { Icon(MaterialSymbols.Filled.Share, null) },
                onClick = onShareClick,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            RethinkConsoleLogSearchRow(
                query = query,
                strings = strings,
                onQueryChange = { query = it },
                onFilterClick = {
                    selectedLogLevel = initialLogLevel
                    filterVisible = true
                },
                onShareClick = onShareClick,
                onClearClick = onClearClick,
            )
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun RethinkConsoleLogSearchRow(
    query: String,
    strings: RethinkConsoleLogStrings,
    onQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    onShareClick: () -> Unit,
    onClearClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = SharedDimensions.screenPaddingHorizontal,
            vertical = SharedDimensions.spacingMd,
        ),
        shape = RoundedCornerShape(SharedDimensions.cardCornerRadiusLarge),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SharedDimensions.spacingSm, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                MaterialSymbols.Filled.Search,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SharedDimensions.spacingSm).size(SharedDimensions.iconSizeMd),
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(strings.searchHint, style = MaterialTheme.typography.bodyMedium) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
            Row {
                IconButton(onClick = onFilterClick) {
                    Icon(MaterialSymbols.Filled.FilterList, strings.filterDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onShareClick) {
                    Icon(MaterialSymbols.Filled.Share, strings.shareDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClearClick) {
                    Icon(MaterialSymbols.Filled.Clear, strings.clearDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
