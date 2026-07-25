/* Copyright 2026 RethinkDNS and its authors */

package com.celzero.bravedns.ui.compose.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.database.Event
import com.celzero.bravedns.database.EventDao
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.ui.components.copyEventToClipboard
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import com.celzero.bravedns.viewmodel.EventsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Android paging, database deletion and clipboard bridge for shared Event Logs UI. */
@OptIn(FlowPreview::class)
@Composable
fun EventsScreen(
    viewModel: EventsViewModel,
    eventDao: EventDao,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(viewModel.getCurrentQuery()) }
    var filters by remember {
        mutableStateOf(
            RethinkEventFilters(
                mode = viewModel.getFilterType().toShared(),
                severity = viewModel.getCurrentSeverity()?.toShared(),
                sources = viewModel.getCurrentSources().map { it.toShared() }.toSet(),
            ),
        )
    }
    val currentFilters by rememberUpdatedState(filters)
    val items = viewModel.eventsFlow.collectAsLazyPagingItems()
    val isLoading = items.loadState.refresh is LoadState.Loading && items.itemCount == 0
    val isEmpty = items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading

    fun applyFilters(next: RethinkEventFilters) {
        filters = next
        viewModel.setFilterType(next.mode.toPlatform())
        viewModel.setFilter(query, next.sources.map { it.toPlatform() }.toSet(), next.severity?.toPlatform())
    }
    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(350)
            .distinctUntilChanged()
            .collect { value ->
                viewModel.setFilter(
                    value,
                    currentFilters.sources.map { it.toPlatform() }.toSet(),
                    currentFilters.severity?.toPlatform(),
                )
            }
    }
    RethinkEventsScreen(
        query = query,
        filters = filters,
        strings = RethinkEventsStrings(
            title = stringResource(R.string.event_logs_title), searchHint = stringResource(R.string.search_event_logs), clearSearch = stringResource(R.string.cd_clear_search),
            refresh = stringResource(R.string.cd_refresh), delete = stringResource(R.string.lbl_delete), all = stringResource(R.string.lbl_all), severity = "Severity", source = stringResource(R.string.events_filter_source),
            low = stringResource(R.string.events_severity_low), medium = stringResource(R.string.events_severity_medium), high = stringResource(R.string.events_severity_high), critical = stringResource(R.string.events_severity_critical),
            deleteDialogTitle = stringResource(R.string.ada_delete_logs_dialog_title), deleteDialogDescription = stringResource(R.string.ada_delete_logs_dialog_desc), cancel = stringResource(R.string.lbl_cancel),
            noEventsTitle = stringResource(R.string.no_events_recorded), noEventsDescription = stringResource(R.string.no_events_desc), copy = "Copy",
        ),
        isLoading = isLoading,
        isEmpty = isEmpty,
        sourceLabel = { it.toPlatform().displayLabel() },
        onQueryChange = { query = it },
        onFiltersChange = ::applyFilters,
        onRefresh = items::refresh,
        onDelete = {
            scope.launch(Dispatchers.IO) { eventDao.deleteAll() }
            items.refresh()
        },
        onBackClick = onBackClick,
    ) { modifier, activeQuery ->
        AndroidEventsList(
            modifier = modifier,
            items = items,
            query = activeQuery,
            onCopy = { copyEventToClipboard(context, it) },
        )
    }
}

@Composable
private fun AndroidEventsList(
    modifier: Modifier,
    items: androidx.paging.compose.LazyPagingItems<Event>,
    query: String,
    onCopy: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingSm,
            bottom = SharedDimensions.spacing3xl,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(count = items.itemCount, key = { index -> items[index]?.id ?: index }) { index ->
            val event = items[index] ?: return@items
            RethinkEventCard(
                event = event.toSharedRow(),
                query = query,
                position = eventPosition(index, items.itemCount),
                copyDescription = "Copy",
                onCopy = onCopy,
            )
        }
    }
}

private fun EventsViewModel.TopLevelFilter.toShared() = when (this) {
    EventsViewModel.TopLevelFilter.ALL -> RethinkEventFilterMode.All
    EventsViewModel.TopLevelFilter.SEVERITY -> RethinkEventFilterMode.Severity
    EventsViewModel.TopLevelFilter.SOURCE -> RethinkEventFilterMode.Source
}

private fun RethinkEventFilterMode.toPlatform() = when (this) {
    RethinkEventFilterMode.All -> EventsViewModel.TopLevelFilter.ALL
    RethinkEventFilterMode.Severity -> EventsViewModel.TopLevelFilter.SEVERITY
    RethinkEventFilterMode.Source -> EventsViewModel.TopLevelFilter.SOURCE
}

private fun Severity.toShared() = when (this) {
    Severity.LOW -> RethinkEventSeverity.Low
    Severity.MEDIUM -> RethinkEventSeverity.Medium
    Severity.HIGH -> RethinkEventSeverity.High
    Severity.CRITICAL -> RethinkEventSeverity.Critical
}

private fun RethinkEventSeverity.toPlatform() = when (this) {
    RethinkEventSeverity.Low -> Severity.LOW
    RethinkEventSeverity.Medium -> Severity.MEDIUM
    RethinkEventSeverity.High -> Severity.HIGH
    RethinkEventSeverity.Critical -> Severity.CRITICAL
}

private fun EventSource.toShared() = when (this) {
    EventSource.UI -> RethinkEventSource.Ui
    EventSource.VPN -> RethinkEventSource.Vpn
    EventSource.DNS -> RethinkEventSource.Dns
    EventSource.FIREWALL -> RethinkEventSource.Firewall
    EventSource.SYSTEM -> RethinkEventSource.System
    EventSource.SERVICE -> RethinkEventSource.Service
    EventSource.WORKER -> RethinkEventSource.Worker
    EventSource.MANAGER -> RethinkEventSource.Manager
    EventSource.PROXY -> RethinkEventSource.Proxy
}

private fun RethinkEventSource.toPlatform() = when (this) {
    RethinkEventSource.Ui -> EventSource.UI
    RethinkEventSource.Vpn -> EventSource.VPN
    RethinkEventSource.Dns -> EventSource.DNS
    RethinkEventSource.Firewall -> EventSource.FIREWALL
    RethinkEventSource.System -> EventSource.SYSTEM
    RethinkEventSource.Service -> EventSource.SERVICE
    RethinkEventSource.Worker -> EventSource.WORKER
    RethinkEventSource.Manager -> EventSource.MANAGER
    RethinkEventSource.Proxy -> EventSource.PROXY
}

private fun Event.toSharedRow() = RethinkEventRow(
    id = id.toString(),
    timestampLabel = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault()).format(Date(timestamp)),
    severity = severity.toShared(),
    sourceLabel = source.displayLabel(),
    eventTypeLabel = eventType.name.displayLabel(),
    message = message,
    details = details,
    userAction = userAction,
)

private fun EventSource.displayLabel() = name.displayLabel()

private fun String.displayLabel() = lowercase(Locale.getDefault())
    .replace('_', ' ')
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

private fun eventPosition(index: Int, itemCount: Int) = when {
    itemCount <= 1 -> RethinkEventCardPosition.Single
    index == 0 -> RethinkEventCardPosition.First
    index == itemCount - 1 -> RethinkEventCardPosition.Last
    else -> RethinkEventCardPosition.Middle
}
