/*
 * Copyright 2025 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.events

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.EventCard
import com.celzero.bravedns.adapter.copyEventToClipboard
import com.celzero.bravedns.database.Event
import com.celzero.bravedns.database.EventDao
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.viewmodel.EventsViewModel
import com.celzero.bravedns.viewmodel.EventsViewModel.TopLevelFilter
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun EventsScreen(
    viewModel: EventsViewModel,
    eventDao: EventDao,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filterQuery by remember { mutableStateOf("") }
    var filterSources by remember { mutableStateOf(setOf<EventSource>()) }
    var filterSeverity by remember { mutableStateOf<Severity?>(null) }
    var filterType by remember { mutableStateOf(TopLevelFilter.ALL) }
    var showSeverityChips by remember { mutableStateOf(false) }
    var showSourceChips by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val items = viewModel.eventsFlow.collectAsLazyPagingItems()

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(QUERY_TEXT_DELAY)
            .distinctUntilChanged()
            .collect { value ->
                filterQuery = value
                viewModel.setFilter(value, filterSources, filterSeverity)
            }
    }

    Scaffold(
        topBar = {
            RethinkTopBar(
                title = stringResource(id = R.string.event_logs_title),
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchRow(
                    query = query,
                    onQueryChange = { query = it },
                    onFilterClick = {
                        showSeverityChips = !showSeverityChips
                        if (!showSeverityChips) {
                            showSourceChips = false
                        }
                    },
                    onRefreshClick = {
                        viewModel.setFilter(filterQuery, filterSources, filterSeverity)
                    },
                    onDeleteClick = { showDeleteDialog = true }
                )

                if (showSeverityChips) {
                    SeverityChips(
                        filterType = filterType,
                        filterSeverity = filterSeverity,
                        showSourceChips = showSourceChips,
                        onAllClick = {
                            showSourceChips = false
                            filterType = TopLevelFilter.ALL
                            filterSeverity = null
                            filterSources = emptySet()
                            viewModel.setFilter(filterQuery, emptySet(), null)
                        },
                        onSeverityClick = { severity ->
                            showSourceChips = false
                            filterType = TopLevelFilter.SEVERITY
                            filterSeverity = severity
                            filterSources = emptySet()
                            viewModel.setFilter(filterQuery, emptySet(), severity)
                        },
                        onSourceClick = {
                            showSourceChips = true
                            filterType = TopLevelFilter.SOURCE
                            filterSeverity = null
                            if (filterSources.isEmpty()) {
                                viewModel.setFilter(filterQuery, emptySet(), null)
                            } else {
                                viewModel.setFilter(filterQuery, filterSources, null)
                            }
                        }
                    )
                    if (showSourceChips) {
                        SourceChips(
                            filterSources = filterSources,
                            onToggle = { source ->
                                filterSources =
                                    if (filterSources.contains(source)) {
                                        filterSources - source
                                    } else {
                                        filterSources + source
                                    }
                                if (filterSources.isEmpty()) {
                                    filterType = TopLevelFilter.ALL
                                    filterSeverity = null
                                    viewModel.setFilter(filterQuery, emptySet(), null)
                                } else {
                                    filterType = TopLevelFilter.SOURCE
                                    filterSeverity = null
                                    viewModel.setFilter(filterQuery, filterSources, null)
                                }
                            }
                        )
                    }
                }

                EventsList(items = items, onCopy = { copyEventToClipboard(context, it) })
            }

            val showEmpty = items.itemCount == 0 && items.loadState.append.endOfPaginationReached
            if (showEmpty) {
                EmptyState()
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = stringResource(id = R.string.ada_delete_logs_dialog_title)) },
            text = { Text(text = stringResource(id = R.string.ada_delete_logs_dialog_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch(Dispatchers.IO) { eventDao.deleteAll() }
                        viewModel.setFilter(filterQuery, filterSources, filterSeverity)
                    }
                ) {
                    Text(text = stringResource(id = R.string.lbl_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(id = R.string.lbl_cancel))
                }
            }
        )
    }
}

@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(text = stringResource(id = R.string.search_event_logs)) }
        )

        IconButton(onClick = onFilterClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_filter),
                contentDescription = null
            )
        }

        IconButton(onClick = onRefreshClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_refresh_white),
                contentDescription = null
            )
        }

        IconButton(onClick = onDeleteClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = null
            )
        }
    }
}

@Composable
private fun SeverityChips(
    filterType: TopLevelFilter,
    filterSeverity: Severity?,
    showSourceChips: Boolean,
    onAllClick: () -> Unit,
    onSeverityClick: (Severity) -> Unit,
    onSourceClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            label = stringResource(id = R.string.lbl_all),
            selected = filterType == TopLevelFilter.ALL,
            onClick = onAllClick
        )
        FilterChip(
            label = stringResource(id = R.string.events_severity_low),
            selected = filterSeverity == Severity.LOW,
            onClick = { onSeverityClick(Severity.LOW) }
        )
        FilterChip(
            label = stringResource(id = R.string.events_severity_medium),
            selected = filterSeverity == Severity.MEDIUM,
            onClick = { onSeverityClick(Severity.MEDIUM) }
        )
        FilterChip(
            label = stringResource(id = R.string.events_severity_high),
            selected = filterSeverity == Severity.HIGH,
            onClick = { onSeverityClick(Severity.HIGH) }
        )
        FilterChip(
            label = stringResource(id = R.string.events_severity_critical),
            selected = filterSeverity == Severity.CRITICAL,
            onClick = { onSeverityClick(Severity.CRITICAL) }
        )
        FilterChip(
            label = stringResource(id = R.string.events_filter_source),
            selected = showSourceChips && filterType == TopLevelFilter.SOURCE,
            onClick = onSourceClick
        )
    }
}

@Composable
private fun SourceChips(
    filterSources: Set<EventSource>,
    onToggle: (EventSource) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EventSource.entries.forEach { source ->
            FilterChip(
                label = source.name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() },
                selected = filterSources.contains(source),
                onClick = { onToggle(source) }
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label) },
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun EventsList(items: androidx.paging.compose.LazyPagingItems<Event>, onCopy: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(count = items.itemCount) { index ->
            val item = items[index] ?: return@items
            EventCard(event = item, onCopy = onCopy)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_event_note),
            contentDescription = null,
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.no_events_recorded),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.no_events_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

private const val QUERY_TEXT_DELAY: Long = 1000
