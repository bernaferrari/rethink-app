/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.events

import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWellDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWell
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillSize
import com.bernaferrari.bravedns.ui.compose.theme.RethinkOverflowMenu
import com.bernaferrari.bravedns.ui.compose.theme.RethinkOverflowItem
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFilterChip
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkMenuChip

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
    /** Localised severity name; drives the badge text and the severity icon's content description. */
    val severityLabel: String,
)

data class RethinkEventsStrings(
    val title: String,
    val searchHint: String,
    val clearSearch: String,
    val refresh: String,
    val delete: String,
    val moreActions: String = "More options",
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
    val userAction: String,
    val noMatchesTitle: String,
    val noMatchesDescription: String,
    val clearFilters: String,
    /** Formats an active filter chip, e.g. "Severity: High". */
    val filterValue: (name: String, value: String) -> String = { name, value -> "$name: $value" },
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
                    // Same overflow as Logs and Console logs: secondary/destructive actions tucked away.
                    RethinkOverflowMenu(
                        moreDescription = strings.moreActions,
                        items = listOf(
                            RethinkOverflowItem(strings.refresh, MaterialSymbols.Filled.Refresh, onClick = onRefresh),
                            RethinkOverflowItem(strings.delete, MaterialSymbols.Filled.DeleteOutline) { showDeleteDialog = true },
                        ),
                    )
                },
            )
        },
    ) { paddingValues ->
        val hasActiveFilters = query.isNotBlank() || filters != RethinkEventFilters(RethinkEventFilterMode.All)
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            RethinkEventControls(
                query = query,
                filters = filters,
                strings = strings,
                sourceLabel = sourceLabel,
                onQueryChange = onQueryChange,
                onFiltersChange = onFiltersChange,
            )
            // Loading/empty states live below the search + filters so the user can always edit
            // or clear them; they never cover the controls.
            when {
                isLoading -> RethinkEventsLoadingState(Modifier.weight(1f))
                isEmpty && hasActiveFilters -> RethinkEmptyState(
                    title = strings.noMatchesTitle,
                    message = strings.noMatchesDescription,
                    icon = MaterialSymbols.Filled.FilterList,
                    ctaLabel = strings.clearFilters,
                    onCtaClick = {
                        onQueryChange("")
                        onFiltersChange(RethinkEventFilters(RethinkEventFilterMode.All))
                    },
                    modifier = Modifier.weight(1f),
                )
                isEmpty -> RethinkEventsEmptyState(strings, Modifier.weight(1f))
                else -> eventsContent(Modifier.weight(1f), query)
            }
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
            bottom = SharedDimensions.spacingXs,
        ),
    ) {
        RethinkSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = strings.searchHint,
            clearQueryContentDescription = strings.clearSearch,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SharedDimensions.spacingSm))
        // One filter row: "All" resets, and each dimension is a menu chip that shows its current
        // value. The host model filters by one dimension at a time, so picking one clears the other.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RethinkFilterChip(
                label = strings.all,
                selected = filters.mode == RethinkEventFilterMode.All,
                onClick = { onFiltersChange(RethinkEventFilters(RethinkEventFilterMode.All)) },
            )
            val severity = filters.severity.takeIf { filters.mode == RethinkEventFilterMode.Severity }
            RethinkMenuChip(
                label = severity?.let { strings.filterValue(strings.severity, it.label(strings)) } ?: strings.severity,
                selected = severity != null,
            ) { dismiss ->
                RethinkEventSeverity.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label(strings)) },
                        leadingIcon = { Icon(option.icon(), null, tint = option.accentColor()) },
                        trailingIcon = if (option == severity) {
                            { Icon(MaterialSymbols.Filled.Check, null) }
                        } else {
                            null
                        },
                        onClick = {
                            dismiss()
                            onFiltersChange(
                                if (option == severity) {
                                    RethinkEventFilters(RethinkEventFilterMode.All)
                                } else {
                                    RethinkEventFilters(RethinkEventFilterMode.Severity, severity = option)
                                },
                            )
                        },
                    )
                }
            }
            val sources = filters.sources.takeIf { filters.mode == RethinkEventFilterMode.Source }.orEmpty()
            val sourceValue = when (sources.size) {
                0 -> null
                1 -> sourceLabel(sources.first())
                else -> "${sourceLabel(sources.first())} +${sources.size - 1}"
            }
            RethinkMenuChip(
                label = sourceValue?.let { strings.filterValue(strings.source, it) } ?: strings.source,
                selected = sources.isNotEmpty(),
            ) { _ ->
                // Multi-select: the menu stays open so several sources can be toggled in a row.
                RethinkEventSource.entries.forEach { source ->
                    val checked = source in sources
                    DropdownMenuItem(
                        text = { Text(sourceLabel(source)) },
                        trailingIcon = if (checked) {
                            { Icon(MaterialSymbols.Filled.Check, null) }
                        } else {
                            null
                        },
                        modifier = Modifier.semantics { selected = checked },
                        onClick = {
                            val next = if (checked) sources - source else sources + source
                            onFiltersChange(
                                if (next.isEmpty()) {
                                    RethinkEventFilters(RethinkEventFilterMode.All)
                                } else {
                                    RethinkEventFilters(RethinkEventFilterMode.Source, sources = next)
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

/** A filter chip that opens a menu; the trailing caret marks it as a menu, not a toggle. */
/**
 * One event row. Tap expands to reveal details and the copy action; long-press copies directly.
 * Severity is carried once, by the tinted glyph (with its label as content description).
 */
@Composable
fun RethinkEventCard(
    event: RethinkEventRow,
    query: String,
    position: CardPosition,
    copyDescription: String,
    userActionLabel: String,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalRethinkMotion.current
    val hasDetails = !event.details.isNullOrBlank()
    var expanded by remember(event.id) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !motion.reducedMotion) 0.985f else 1f,
        animationSpec = if (motion.reducedMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "event_card_scale",
    )
    val accent = event.severity.accentColor()
    val highlightColor = MaterialTheme.colorScheme.primary
    val highlightedMessage = remember(event.message, query, highlightColor) { event.message.highlight(query, highlightColor) }
    val highlightedDetails = remember(event.details, query, highlightColor) { event.details.orEmpty().highlight(query, highlightColor) }
    val shape = rethinkGroupedListShape(position)
    Surface(
        shape = shape,
        color = if (expanded) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .scale(scale)
            .rethinkGroupedRow(position)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = { expanded = !expanded },
                onLongClick = { onCopy(event.clipboardText()) },
            )
            .semantics { stateDescription = event.severityLabel },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingSmMd),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            EventSeverityIcon(event.severity, event.severityLabel, accent, Modifier)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                // Timestamp sits on the message's first baseline, also when the message expands.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    Text(highlightedMessage, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).alignByBaseline())
                    Text(event.timestampLabel, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.alignByBaseline())
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                    RethinkPill(event.sourceLabel, colors = RethinkPillDefaults.secondary(), size = RethinkPillSize.Small)
                    RethinkPill(event.eventTypeLabel, size = RethinkPillSize.Small)
                    if (event.userAction) RethinkPill(userActionLabel, colors = RethinkPillDefaults.status(MaterialTheme.colorScheme.primary), size = RethinkPillSize.Small)
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = if (motion.reducedMotion) EnterTransition.None else expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = if (motion.reducedMotion) ExitTransition.None else shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                        if (hasDetails) {
                            HorizontalDivider(thickness = SharedDimensions.dividerThickness, color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = SharedDimensions.spacingXs))
                            Text(highlightedDetails, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 8, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(
                            onClick = { onCopy(event.clipboardText()) },
                            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                        ) {
                            Icon(MaterialSymbols.Filled.ContentCopy, null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(copyDescription)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventSeverityIcon(severity: RethinkEventSeverity, severityLabel: String, accent: Color, modifier: Modifier) {
    RethinkIconWell(modifier = modifier, containerColor = RethinkIconWellDefaults.tinted(accent), contentColor = accent) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (severity == RethinkEventSeverity.Low) {
                // Low is routine: a neutral dot reads as "nothing to act on" without a glyph.
                Box(
                    Modifier
                        .size(SharedDimensions.spacingSmMd)
                        .clip(CircleShape)
                        .background(accent)
                        .semantics { contentDescription = severityLabel },
                )
            } else {
                Icon(severity.icon(), severityLabel, tint = accent, modifier = Modifier.size(SharedDimensions.iconSizeSm))
            }
        }
    }
}

@Composable
private fun RethinkEventsLoadingState(modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        RethinkLoadingState(label = "")
    }
}

@Composable
private fun RethinkEventsEmptyState(strings: RethinkEventsStrings, modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        RethinkEmptyState(
            title = strings.noEventsTitle,
            message = strings.noEventsDescription,
            icon = MaterialSymbols.Filled.EventNote,
        )
    }
}

private fun RethinkEventSeverity.label(strings: RethinkEventsStrings) = when (this) {
    RethinkEventSeverity.Low -> strings.low
    RethinkEventSeverity.Medium -> strings.medium
    RethinkEventSeverity.High -> strings.high
    RethinkEventSeverity.Critical -> strings.critical
}

private fun RethinkEventSeverity.icon(): ImageVector = when (this) {
    RethinkEventSeverity.Low -> MaterialSymbols.Filled.Info
    RethinkEventSeverity.Medium -> MaterialSymbols.Filled.Info
    RethinkEventSeverity.High -> MaterialSymbols.Filled.Warning
    RethinkEventSeverity.Critical -> MaterialSymbols.Filled.GppBad
}

@Composable
private fun RethinkEventSeverity.accentColor(): Color = when (this) {
    RethinkEventSeverity.Low -> RethinkStatusColors.idle
    RethinkEventSeverity.Medium -> RethinkStatusColors.allowed
    RethinkEventSeverity.High -> RethinkStatusColors.mixed
    RethinkEventSeverity.Critical -> RethinkStatusColors.blocked
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
