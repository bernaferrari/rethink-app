/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor

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
            .padding(horizontal = SharedDimensions.screenPaddingHorizontal)
            .padding(top = SharedDimensions.spacingXs),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
        content = { content() },
    )
}

@Composable
fun RethinkLogCompactIconAction(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    count: Int = 0,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.size(SharedDimensions.touchTargetSm)) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.matchParentSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (selected) {
            Surface(
                shape = RoundedCornerShape(SharedDimensions.cornerRadiusPill),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp).size(6.dp),
            ) {}
        }
        if (count > 0) {
            Surface(
                shape = RoundedCornerShape(SharedDimensions.cornerRadiusPill),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp),
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
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
        verticalSpacing = 2.dp,
        includeBottomSpacer = false,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
                RethinkSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = strings.searchPlaceholder,
                    onClearQuery = { onSearchQueryChange("") },
                    clearQueryContentDescription = strings.clearSearchDescription,
                    closeWhenEmptyContentDescription = strings.dismissDescription,
                    onCloseWhenEmpty = onDismiss,
                    shape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    iconSize = 18.dp,
                    trailingIconSize = 16.dp,
                    trailingIconButtonSize = SharedDimensions.touchTargetSm,
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
                        verticalArrangement = Arrangement.spacedBy(2.dp),
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
                    modifier = Modifier.size(24.dp),
                    tint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (appIcon != null) {
                appIcon(option)
            } else {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(24.dp),
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
                shape = RoundedCornerShape(SharedDimensions.cornerRadiusPill),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.84f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
        contentPadding = PaddingValues(0.dp),
        verticalSpacing = 0.dp,
        includeBottomSpacer = false,
    ) {
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
                    Icon(MaterialSymbols.Filled.FilterList, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(SharedDimensions.spacingSm))
                    Text(strings.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (selectedCount > 0) {
                        Spacer(Modifier.size(SharedDimensions.spacingSm))
                        Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadiusPill), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(selectedCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (selectedCount > 0) TextButton(onClick = onClear) { Text(strings.clear, fontWeight = FontWeight.SemiBold) }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(SharedDimensions.touchTargetSm),
                    ) {
                        Icon(MaterialSymbols.Filled.Close, strings.dismissDescription, modifier = Modifier.size(18.dp))
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
                                { Icon(MaterialSymbols.Filled.CheckCircle, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
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

@Composable
fun RethinkLogsDisabledState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(SharedDimensions.cornerRadius2xl),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.padding(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingXl),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingLg, vertical = SharedDimensions.spacingMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            ) {
                Icon(MaterialSymbols.AutoMirrored.Filled.Subject, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
