/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.ui.compose.theme.RethinkSheetHeader
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWell
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWellContainerColor
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListSelection

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
            modifier = Modifier.fillMaxWidth().heightIn(max = SharedDimensions.sheetContentMaxHeight),
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
                    RethinkLoadingState(strings.loading, contained = true)
                } else {
                    // "All" leads one grouped list with the apps below it.
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).selectableGroup(),
                    ) {
                        item("all_apps") {
                            RethinkLogAppFilterItem(
                                option = null,
                                label = strings.all,
                                count = totalCount,
                                selected = selectedId == null,
                                position = cardPositionFor(0, filteredOptions.size),
                                onClick = { onSelect(null) },
                            )
                        }
                        itemsIndexed(filteredOptions, key = { _, option -> option.id }) { index, option ->
                            RethinkLogAppFilterItem(
                                option = option,
                                label = option.label,
                                count = option.count,
                                selected = selectedId == option.id,
                                position = cardPositionFor(index + 1, filteredOptions.size),
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
    position: CardPosition,
    onClick: () -> Unit,
    appIcon: (@Composable (RethinkLogAppOption) -> Unit)? = null,
) {
    RethinkListItem(
        headline = label,
        leadingIcon = if (option == null) MaterialSymbols.Filled.Public else null,
        // App icons sit centred in a well-sized box so their labels line up with "All".
        leadingContent = option?.let {
            {
                Box(Modifier.size(SharedDimensions.iconContainerMd), contentAlignment = Alignment.Center) {
                    if (appIcon != null) {
                        appIcon(option)
                    } else {
                        Surface(
                            shape = RoundedCornerShape(SharedDimensions.cornerRadiusSm),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(SharedDimensions.iconSizeMd),
                        ) {}
                    }
                }
            }
        },
        trailing = { RethinkPill(count.toString(), colors = if (selected) RethinkPillDefaults.secondary() else RethinkPillDefaults.neutral()) },
        position = position,
        highlighted = selected,
        selection = RethinkListSelection(checked = selected, role = Role.RadioButton) { onClick() },
    )
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
        Column(Modifier.fillMaxWidth().heightIn(max = SharedDimensions.sheetContentMaxHeight)) {
                RethinkSheetHeader(
                    title = strings.title,
                    modifier = Modifier.padding(
                        start = SharedDimensions.screenPaddingHorizontal,
                        end = SharedDimensions.spacingXs,
                        top = SharedDimensions.spacingMd,
                        bottom = SharedDimensions.spacingSm,
                    ),
                    leading = {
                        Icon(MaterialSymbols.Filled.FilterList, null, modifier = Modifier.size(SharedDimensions.iconSizeSm), tint = MaterialTheme.colorScheme.primary)
                    },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedCount > 0) {
                                RethinkPill(selectedCount.toString(), colors = RethinkPillDefaults.primary(), size = RethinkPillSize.Small)
                                TextButton(onClick = onClear) { Text(strings.clear, fontWeight = FontWeight.SemiBold) }
                            }
                            IconButton(
                                onClick = dismissSheet,
                                modifier = Modifier.size(SharedDimensions.touchTargetMin),
                            ) {
                                Icon(MaterialSymbols.Filled.Close, strings.dismissDescription, modifier = Modifier.size(SharedDimensions.iconSizeXs))
                            }
                        }
                    },
                )
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
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    RethinkIconWell(
        containerColor = if (selected) RethinkIconWellContainerColor else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = tint,
        size = SharedDimensions.iconContainerSm,
    ) {
        if (icon == null) {
            Icon(MaterialSymbols.Filled.FilterList, null, modifier = Modifier.size(SharedDimensions.iconSizeSm), tint = tint)
        } else {
            icon(tint)
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
