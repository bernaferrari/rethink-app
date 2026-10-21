/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

data class RethinkLogAppOption(
    val id: String,
    val label: String,
    val count: Int,
    val iconKey: String = id,
)

data class RethinkLogAppFilterStrings(
    val all: String,
    val searchPlaceholder: String,
    val clear: String,
    val clearSearchDescription: String,
    val dismissDescription: String,
    val loading: String,
)

data class RethinkLogRuleOption(
    val id: String,
    val title: String,
    val supporting: AnnotatedString,
    val leadingIcon: (@Composable (tint: androidx.compose.ui.graphics.Color) -> Unit)? = null,
)

data class RethinkLogRulesStrings(
    val title: String,
    val clear: String,
    val dismissDescription: String,
)

/** Common chrome around the live log query controls. */
@Composable
fun RethinkLogsControlsDeck(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Symmetric vertical inset: the strip sits centered between the top bar and the list.
            .padding(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingXs),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
        content = { content() },
    )
}

/**
 * One toolbar strip (connected group or search field + [RethinkLogCompactIconAction]s). Fixes
 * the gap between controls and their vertical centering so every log-like screen (network, DNS,
 * console, events, database) lines up the same way.
 */
@Composable
fun RethinkLogToolbarRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Toolbar action beside the log filter group. Same height and tonal family as the connected
 * segments so the row reads as one control strip; [selected] (an active filter) uses the checked
 * container, and [count] shows as a badge. Toggle semantics, since it reflects filter state.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RethinkLogCompactIconAction(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    count: Int = 0,
    modifier: Modifier = Modifier,
    /** Match the neighbouring control's height (48dp groups; 56dp text fields). */
    size: Dp = SharedDimensions.touchTargetMin,
    /** Shows a dot badge while [selected] when there is no [count] to show. */
    showSelectedDot: Boolean = false,
    onClick: () -> Unit,
) {
    BadgedBox(
        modifier = modifier,
        badge = {
            when {
                count > 0 -> Badge { Text(count.toString()) }
                showSelectedDot && selected -> Badge()
            }
        },
    ) {
        FilledTonalIconToggleButton(
            checked = selected,
            onCheckedChange = { onClick() },
            enabled = enabled,
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier.size(size),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(SharedDimensions.iconSizeSm),
            )
        }
    }
}

/** Common app picker; a host may supply platform app artwork for each real app option. */
@Composable
fun RethinkLogAppFilterDialog(
    options: List<RethinkLogAppOption>,
    selectedId: String?,
    searchQuery: String,
    isLoading: Boolean,
    strings: RethinkLogAppFilterStrings,
    onSearchQueryChange: (String) -> Unit,
    onSelect: (String?) -> Unit,
    onClearSelection: () -> Unit,
    onDismiss: () -> Unit,
    appIcon: (@Composable (RethinkLogAppOption) -> Unit)? = null,
) {
    val filteredOptions = remember(options, searchQuery) {
        if (searchQuery.isBlank()) options else options.filter { it.label.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val totalCount = remember(options) { options.sumOf { it.count } }

    RethinkModalBottomSheet(
        onDismissRequest = onDismiss,
        contentPadding = PaddingValues(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingSm),
        verticalSpacing = SharedDimensions.spacingGridTile,
        includeBottomSpacer = false,
    ) { dismissSheet ->
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
        ) {
                RethinkSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = strings.searchPlaceholder,
                    onClearQuery = { onSearchQueryChange("") },
                    clearQueryContentDescription = strings.clearSearchDescription,
                    closeWhenEmptyContentDescription = strings.dismissDescription,
                    onCloseWhenEmpty = dismissSheet,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    iconSize = SharedDimensions.iconSizeXs,
                    trailingIconSize = SharedDimensions.iconSizeXs,
                    trailingIconButtonSize = SharedDimensions.touchTargetMin,
                )
                if (selectedId != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onClearSelection) {
                            Text(strings.clear, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (isLoading) {
                    RethinkLogLoadingState(strings.loading)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
                    ) {
                        item("all_apps") {
                            RethinkLogAppFilterItem(
                                option = null,
                                label = strings.all,
                                count = totalCount,
                                selected = selectedId == null,
                                onClick = { onSelect(null) },
                            )
                        }
                        items(filteredOptions, key = { it.id }) { option ->
                            RethinkLogAppFilterItem(
                                option = option,
                                label = option.label,
                                count = option.count,
                                selected = selectedId == option.id,
                                onClick = { onSelect(option.id) },
                                appIcon = appIcon,
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun RethinkLogAppFilterItem(
    option: RethinkLogAppOption?,
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    appIcon: (@Composable (RethinkLogAppOption) -> Unit)? = null,
) {
    val itemShape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg)
    Surface(
        onClick = onClick,
        shape = itemShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f) else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.clip(itemShape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (option == null) {
                Icon(
                    MaterialSymbols.Filled.Public,
                    contentDescription = null,
                    modifier = Modifier.size(SharedDimensions.iconSizeMd),
                    tint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (appIcon != null) {
                appIcon(option)
            } else {
                Surface(
                    shape = RoundedCornerShape(SharedDimensions.cornerRadiusSm),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(SharedDimensions.iconSizeMd),
                ) {}
            }
            Spacer(modifier = Modifier.size(SharedDimensions.spacingMd))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                shape = RoundedCornerShape(SharedDimensions.cornerRadiusFull),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.84f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingGridTile),
                )
            }
        }
    }
}

@Composable
fun RethinkLogRulesDialog(
    rules: List<RethinkLogRuleOption>,
    selectedIds: Set<String>,
    strings: RethinkLogRulesStrings,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedCount = selectedIds.size
    RethinkModalBottomSheet(
        onDismissRequest = onDismiss,
        contentPadding = PaddingValues(SharedDimensions.spacingNone),
        verticalSpacing = SharedDimensions.spacingNone,
        includeBottomSpacer = false,
    ) { dismissSheet ->
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        start = SharedDimensions.screenPaddingHorizontal,
                        end = SharedDimensions.spacingXs,
                        top = SharedDimensions.spacingMd,
                        bottom = SharedDimensions.spacingSm,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(MaterialSymbols.Filled.FilterList, null, modifier = Modifier.size(SharedDimensions.iconSizeSm), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(SharedDimensions.spacingSm))
                    Text(strings.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (selectedCount > 0) {
                        Spacer(Modifier.size(SharedDimensions.spacingSm))
                        Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadiusFull), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(selectedCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingGridTile))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (selectedCount > 0) TextButton(onClick = onClear) { Text(strings.clear, fontWeight = FontWeight.SemiBold) }
                    IconButton(
                        onClick = dismissSheet,
                        modifier = Modifier.size(SharedDimensions.touchTargetMin),
                    ) {
                        Icon(MaterialSymbols.Filled.Close, strings.dismissDescription, modifier = Modifier.size(SharedDimensions.iconSizeXs))
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(start = SharedDimensions.spacingSm, end = SharedDimensions.spacingSm, bottom = SharedDimensions.spacingSm),
                ) {
                    itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                        val selected = selectedIds.contains(rule.id)
                        RethinkListItem(
                            headline = rule.title,
                            supportingAnnotated = rule.supporting,
                            leadingContent = { RethinkLogRuleIconSlot(rule.leadingIcon, selected) },
                            position = cardPositionFor(index, rules.lastIndex),
                            highlighted = selected,
                            highlightContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                            trailing = if (selected) {
                                { Icon(MaterialSymbols.Filled.CheckCircle, null, modifier = Modifier.size(SharedDimensions.iconSizeSm), tint = MaterialTheme.colorScheme.primary) }
                            } else null,
                            onClick = { onToggle(rule.id) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun RethinkLogRuleIconSlot(
    icon: (@Composable (tint: androidx.compose.ui.graphics.Color) -> Unit)?,
    selected: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(SharedDimensions.iconContainerRadius),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(SharedDimensions.iconContainerSm),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            if (icon == null) {
                Icon(MaterialSymbols.Filled.FilterList, null, modifier = Modifier.size(SharedDimensions.iconSizeSm), tint = tint)
            } else {
                icon(tint)
            }
        }
    }
}

/**
 * Shown when on-device logging is off. With [actionLabel] + [onEnable] the host offers a direct
 * "Enable logs" action instead of sending the user to Settings.
 */
@Composable
fun RethinkLogsDisabledState(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onEnable: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RethinkEmptyState(
            icon = MaterialSymbols.AutoMirrored.Filled.Subject,
            title = title ?: message,
            message = if (title != null) message else "",
            ctaLabel = actionLabel,
            onCtaClick = onEnable,
        )
    }
}

fun rethinkHtmlToAnnotatedString(input: String): AnnotatedString {
    val sanitized = input.replace(Regex("(?i)</?u>"), "")
    val tokenRegex = Regex("(?i)<br\\s*/?>|</?i>")
    val builder = AnnotatedString.Builder()
    var cursor = 0
    var italicDepth = 0
    tokenRegex.findAll(sanitized).forEach { match ->
        if (match.range.first > cursor) builder.append(sanitized.substring(cursor, match.range.first))
        when {
            match.value.matches(Regex("(?i)<br\\s*/?>")) -> builder.append("\n")
            match.value.equals("<i>", ignoreCase = true) -> { builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); italicDepth++ }
            match.value.equals("</i>", ignoreCase = true) && italicDepth > 0 -> { builder.pop(); italicDepth-- }
        }
        cursor = match.range.last + 1
    }
    if (cursor < sanitized.length) builder.append(sanitized.substring(cursor))
    repeat(italicDepth) { builder.pop() }
    return builder.toAnnotatedString()
}
