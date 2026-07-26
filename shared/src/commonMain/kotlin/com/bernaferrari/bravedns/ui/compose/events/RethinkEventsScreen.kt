/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.events

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.ripple
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFilterChip
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape

enum class RethinkEventFilterMode { All, Severity, Source }

enum class RethinkEventSeverity { Low, Medium, High, Critical }

enum class RethinkEventSource { Ui, Vpn, Dns, Firewall, System, Service, Worker, Manager, Proxy }

data class RethinkEventFilters(
    val mode: RethinkEventFilterMode,
    val severity: RethinkEventSeverity? = null,
    val sources: Set<RethinkEventSource> = emptySet(),
)

data class RethinkEventRow(
    val id: String,
    val timestampLabel: String,
    val severity: RethinkEventSeverity,
    val sourceLabel: String,
    val eventTypeLabel: String,
    val message: String,
    val details: String? = null,
    val userAction: Boolean = false,
)

/** Kept as a source-compatible name while all grouped UI uses one shared shape policy. */
typealias RethinkEventCardPosition = CardPosition

data class RethinkEventsStrings(
    val title: String,
    val searchHint: String,
    val clearSearch: String,
    val refresh: String,
    val delete: String,
    val all: String,
    val severity: String,
    val source: String,
    val low: String,
    val medium: String,
    val high: String,
    val critical: String,
    val deleteDialogTitle: String,
    val deleteDialogDescription: String,
    val cancel: String,
    val noEventsTitle: String,
    val noEventsDescription: String,
    val copy: String,
)

/** Portable event-log shell. Hosts provide paging, filtering persistence and clipboard access. */
@Composable
fun RethinkEventsScreen(
    query: String,
    filters: RethinkEventFilters,
    strings: RethinkEventsStrings,
    isLoading: Boolean,
    isEmpty: Boolean,
    sourceLabel: (RethinkEventSource) -> String,
    onQueryChange: (String) -> Unit,
    onFiltersChange: (RethinkEventFilters) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    eventsContent: @Composable (Modifier, String) -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkTopBar(
                title = strings.title,
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = onRefresh) { Icon(MaterialSymbols.Filled.Refresh, strings.refresh) }
                    IconButton(onClick = { showDeleteDialog = true }) { Icon(MaterialSymbols.Filled.Delete, strings.delete) }
                },
            )
        },
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            Column(Modifier.fillMaxSize()) {
                RethinkEventControls(
                    query = query,
                    filters = filters,
                    strings = strings,
                    sourceLabel = sourceLabel,
                    onQueryChange = onQueryChange,
                    onFiltersChange = onFiltersChange,
                )
                eventsContent(Modifier.weight(1f), query)
            }
            if (isLoading) RethinkEventsLoadingState()
            if (isEmpty) RethinkEventsEmptyState(strings)
        }
    }
    if (showDeleteDialog) {
        RethinkConfirmDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = strings.deleteDialogTitle,
            message = strings.deleteDialogDescription,
            confirmText = strings.delete,
            dismissText = strings.cancel,
            isConfirmDestructive = true,
            onConfirm = { showDeleteDialog = false; onDelete() },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun RethinkEventControls(
    query: String,
    filters: RethinkEventFilters,
    strings: RethinkEventsStrings,
    sourceLabel: (RethinkEventSource) -> String,
    onQueryChange: (String) -> Unit,
    onFiltersChange: (RethinkEventFilters) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingSm,
        ),
    ) {
        RethinkSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = strings.searchHint,
            clearQueryContentDescription = strings.clearSearch,
            onClearQuery = { onQueryChange("") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SharedDimensions.cornerRadiusLg),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        Spacer(Modifier.height(SharedDimensions.spacingSm))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            RethinkEventFilterChip(strings.all, filters.mode == RethinkEventFilterMode.All) {
                onFiltersChange(RethinkEventFilters(RethinkEventFilterMode.All))
            }
            RethinkEventFilterChip(strings.severity, filters.mode == RethinkEventFilterMode.Severity) {
                onFiltersChange(filters.copy(mode = RethinkEventFilterMode.Severity, sources = emptySet()))
            }
            RethinkEventFilterChip(strings.source, filters.mode == RethinkEventFilterMode.Source) {
                onFiltersChange(filters.copy(mode = RethinkEventFilterMode.Source, severity = null))
            }
        }
        when (filters.mode) {
            RethinkEventFilterMode.All -> Unit
            RethinkEventFilterMode.Severity -> {
                Spacer(Modifier.height(SharedDimensions.spacingSm))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                ) {
                    listOf(
                        RethinkEventSeverity.Low to strings.low,
                        RethinkEventSeverity.Medium to strings.medium,
                        RethinkEventSeverity.High to strings.high,
                        RethinkEventSeverity.Critical to strings.critical,
                    ).forEach { (severity, label) ->
                        RethinkEventFilterChip(label, filters.severity == severity) {
                            onFiltersChange(RethinkEventFilters(RethinkEventFilterMode.Severity, severity = severity))
                        }
                    }
                }
            }
            RethinkEventFilterMode.Source -> {
                Spacer(Modifier.height(SharedDimensions.spacingSm))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                ) {
                    RethinkEventSource.entries.forEach { source ->
                        RethinkEventFilterChip(sourceLabel(source), source in filters.sources) {
                            val sources = if (source in filters.sources) filters.sources - source else filters.sources + source
                            onFiltersChange(RethinkEventFilters(RethinkEventFilterMode.Source, sources = sources))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RethinkEventFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    RethinkFilterChip(
        label = label,
        selected = selected,
        onClick = onClick,
        selectedLabelWeight = FontWeight.Medium,
        defaultLabelWeight = FontWeight.Medium,
    )
}

@Composable
fun RethinkEventCard(
    event: RethinkEventRow,
    query: String,
    position: RethinkEventCardPosition,
    copyDescription: String,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasDetails = !event.details.isNullOrBlank()
    var expanded by remember(event.id) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "event_card_scale",
    )
    val accent = event.severity.accentColor()
    val highlightColor = MaterialTheme.colorScheme.primary
    val highlightedMessage = remember(event.message, query, highlightColor) { event.message.highlight(query, highlightColor) }
    val highlightedDetails = remember(event.details, query, highlightColor) { event.details.orEmpty().highlight(query, highlightColor) }
    Surface(
        shape = rethinkGroupedListShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth().scale(scale).clip(rethinkGroupedListShape(position)).combinedClickable(
            interactionSource = interactionSource,
            indication = ripple(),
            onClick = { if (hasDetails) expanded = !expanded },
            onLongClick = { onCopy(event.clipboardText()) },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingMd, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            EventSeverityIcon(event.severity, accent, Modifier.size(40.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    Text(highlightedMessage, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(event.timestampLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                    CompactEventBadge(event.severity.name, accent.copy(alpha = 0.14f), accent)
                    CompactEventBadge(event.sourceLabel, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), MaterialTheme.colorScheme.onSurfaceVariant)
                    CompactEventBadge(event.eventTypeLabel, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                    if (event.userAction) CompactEventBadge("User", MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), MaterialTheme.colorScheme.primary)
                }
                AnimatedVisibility(visible = expanded && hasDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f), modifier = Modifier.padding(top = SharedDimensions.spacingXs))
                        Text(highlightedDetails, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 8, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            IconButton(
                onClick = { onCopy(event.clipboardText()) },
                modifier = Modifier.size(SharedDimensions.touchTargetSm),
            ) {
                Icon(MaterialSymbols.Filled.ContentCopy, copyDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun EventSeverityIcon(severity: RethinkEventSeverity, accent: Color, modifier: Modifier) {
    val icon = when (severity) {
        RethinkEventSeverity.Low -> MaterialSymbols.Filled.Settings
        RethinkEventSeverity.Medium -> MaterialSymbols.Filled.Info
        RethinkEventSeverity.High, RethinkEventSeverity.Critical -> MaterialSymbols.Filled.Warning
    }
    Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadiusMd), color = accent.copy(alpha = 0.12f), modifier = modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, severity.name, tint = accent, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun CompactEventBadge(text: String, containerColor: Color, contentColor: Color) {
    Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadiusSm), color = containerColor) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = contentColor, modifier = Modifier.padding(horizontal = SharedDimensions.spacingSm, vertical = 2.dp), maxLines = 1)
    }
}

@Composable
private fun RethinkEventsLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp) }
}

@Composable
private fun RethinkEventsEmptyState(strings: RethinkEventsStrings) {
    Column(Modifier.fillMaxSize().padding(horizontal = SharedDimensions.spacingXl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.size(96.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(MaterialSymbols.Filled.EventNote, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.height(SharedDimensions.spacingLg))
        Text(strings.noEventsTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(SharedDimensions.spacingSm))
        Text(strings.noEventsDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private fun RethinkEventSeverity.accentColor() = when (this) {
    RethinkEventSeverity.Low -> Color(0xFF2E7D32)
    RethinkEventSeverity.Medium -> Color(0xFFAD7F00)
    RethinkEventSeverity.High -> Color(0xFFB85C00)
    RethinkEventSeverity.Critical -> Color(0xFFB3261E)
}

private fun RethinkEventRow.clipboardText() = buildString {
    append(message)
    details?.takeIf { it.isNotBlank() }?.let { append("\n\n"); append(it) }
}

private fun String.highlight(query: String, color: Color): AnnotatedString {
    if (isBlank() || query.isBlank()) return AnnotatedString(this)
    val tokens = query.split(Regex("\\s+")).map(String::trim).filter(String::isNotBlank).distinct()
    val ranges = buildList {
        tokens.forEach { token ->
            var start = 0
            while (start < this@highlight.length) {
                val index = this@highlight.indexOf(token, start, ignoreCase = true)
                if (index < 0) break
                add(index to index + token.length)
                start = index + token.length
            }
        }
    }.sortedBy { it.first }
    if (ranges.isEmpty()) return AnnotatedString(this)
    val merged = ranges.fold(mutableListOf<Pair<Int, Int>>()) { accumulator, range ->
        val previous = accumulator.lastOrNull()
        if (previous == null || range.first > previous.second) accumulator += range
        else accumulator[accumulator.lastIndex] = previous.first to maxOf(previous.second, range.second)
        accumulator
    }
    return buildAnnotatedString {
        append(this@highlight)
        merged.forEach { (start, end) -> addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), start, end) }
    }
}
