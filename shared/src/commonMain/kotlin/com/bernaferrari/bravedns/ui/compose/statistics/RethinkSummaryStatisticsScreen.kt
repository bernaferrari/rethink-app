/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.statistics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.bernaferrari.bravedns.ui.compose.common.RethinkAdaptiveWidth
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.isExpanded
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf

private val ShareBarWidth = 48.dp
private val ShareBarHeight = 4.dp

enum class RethinkStatisticsWindow { OneHour, TwentyFourHours, SevenDays }

data class RethinkSummaryStatisticsRow(
    val id: String,
    val headline: String,
    val supporting: String? = null,
    val metric: String,
    val countryFlag: String? = null,
    val leadingContent: (@Composable () -> Unit)? = null,
    val expandedContent: (@Composable () -> Unit)? = null,
    /** This row's value relative to the section's largest (0..1); drives the ranking bar. */
    val share: Float? = null,
)

/**
 * Relative shares for a ranked section: each value divided by the section maximum, so the top
 * row reads as a full bar. Returns nulls when there is nothing to compare.
 */
fun rethinkRelativeShares(values: List<Long>): List<Float?> {
    val max = values.maxOrNull()?.takeIf { it > 0L } ?: return values.map { null }
    return values.map { (it.toFloat() / max).coerceIn(0f, 1f) }
}

data class RethinkSummaryStatisticsSection(
    val id: String,
    val title: String,
    /** Metric and ranking-bar colour: allowed green, blocked red, otherwise neutral. */
    val accentColor: Color,
    val rows: List<RethinkSummaryStatisticsRow>,
    val isLoading: Boolean = false,
    val canSeeMore: Boolean = false,
    val onSeeMore: (() -> Unit)? = null,
)

data class RethinkCountryBreakdownItem(
    val id: String,
    val headline: String,
    val metric: String,
    val leadingContent: (@Composable () -> Unit)? = null,
)

data class RethinkSummaryStatisticsStrings(
    val title: String,
    val overall: String,
    val download: String,
    val upload: String,
    val connections: String,
    val oneHour: String,
    val twentyFourHours: String,
    val sevenDays: String,
    val noLogs: String,
    val seeMore: String,
    val seeLess: String,
    /** Accessibility state for expandable country rows. */
    val expanded: String = "Expanded",
    val collapsed: String = "Collapsed",
)

/**
 * The shared overview for the Statistics destination. Paging, database formatting, resource
 * lookup, and app artwork stay in the host; hierarchy, density, and interaction visuals do not.
 */
@Composable
fun RethinkSummaryStatisticsScreen(
    overview: RethinkUsageOverview,
    selectedWindow: RethinkStatisticsWindow,
    sections: List<RethinkSummaryStatisticsSection>,
    strings: RethinkSummaryStatisticsStrings,
    onWindowSelected: (RethinkStatisticsWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Once the window selector scrolls away, the window rides in the top bar so it stays visible and switchable.
    val listState = rememberLazyListState()
    val selectorHidden by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val reducedMotion = LocalRethinkMotion.current.reducedMotion
    RethinkAdaptiveWidth(modifier) { widthClass ->
        RethinkTopBarLazyColumnScreen(
            title = strings.title,
            modifier = Modifier.fillMaxWidth(),
            listState = listState,
            topBarActions = {
                AnimatedVisibility(
                    visible = selectorHidden,
                    enter = if (reducedMotion) EnterTransition.None else fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = if (reducedMotion) ExitTransition.None else fadeOut() + scaleOut(targetScale = 0.9f),
                ) {
                    RethinkStatisticsWindowSelector(
                        selectedWindow,
                        strings,
                        onWindowSelected,
                        Modifier.padding(end = SharedDimensions.spacingSm),
                        inTopBar = true,
                    )
                }
            },
        ) {
            // The window scopes everything below it, including the usage totals, so it leads.
            item {
                RethinkStatisticsWindowSelector(selectedWindow, strings, onWindowSelected)
            }
            item {
                RethinkUsageOverviewCard(
                    overview = overview,
                    overallLabel = strings.overall,
                    downloadLabel = strings.download,
                    uploadLabel = strings.upload,
                    connectionsLabel = strings.connections,
                )
            }
            if (widthClass.isExpanded) {
                sections.chunked(2).forEach { pair ->
                    item(key = pair.joinToString(separator = "|") { it.id }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
                            verticalAlignment = Alignment.Top,
                        ) {
                            pair.forEach { section ->
                                Box(Modifier.weight(1f)) {
                                    RethinkSummaryStatisticsSectionCard(section, strings)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                sections.forEach { section ->
                    item(key = section.id) { RethinkSummaryStatisticsSectionCard(section, strings) }
                }
            }
        }
    }
}

private fun RethinkSummaryStatisticsStrings.windowOptions() = listOf(
    RethinkStatisticsWindow.OneHour to oneHour,
    RethinkStatisticsWindow.TwentyFourHours to twentyFourHours,
    RethinkStatisticsWindow.SevenDays to sevenDays,
)

@Composable
private fun RethinkStatisticsWindowSelector(
    selectedWindow: RethinkStatisticsWindow,
    strings: RethinkSummaryStatisticsStrings,
    onWindowSelected: (RethinkStatisticsWindow) -> Unit,
    modifier: Modifier = Modifier,
    /** The same tabs, compact, for the collapsed top bar once the full-width row scrolls away. */
    inTopBar: Boolean = false,
) {
    val options = strings.windowOptions()
    RethinkConnectedChoiceGroup(
        options = options.map { (window, label) -> RethinkChoiceOption(window, label) },
        selected = selectedWindow,
        onSelect = onWindowSelected,
        modifier = modifier,
        fillWidth = !inTopBar,
        compact = inTopBar,
        unselectedContainerColor = if (inTopBar) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow,
    )
}

@Composable
private fun RethinkSummaryStatisticsSectionCard(
    section: RethinkSummaryStatisticsSection,
    strings: RethinkSummaryStatisticsStrings,
) {
    val isCountrySection = section.rows.any { !it.countryFlag.isNullOrBlank() }
    var showAllCountries by remember(section.id) { mutableStateOf(false) }
    var expandedCountryId by remember(section.id) { mutableStateOf<String?>(null) }
    val displayedRows = if (isCountrySection && !showAllCountries) section.rows.take(5) else section.rows

    val canToggleCountries = isCountrySection && section.rows.size > 5
    val canSeeMore = !isCountrySection && section.canSeeMore && section.onSeeMore != null
    Column {
        // One header colour for every section; the verdict colour lives in the metric alone.
        // "Show all" is a quiet link on the header row, not a filled pill under the list.
        SectionHeader(
            title = section.title,
            actionLabel = when {
                canToggleCountries -> if (showAllCountries) strings.seeLess else strings.seeMore
                canSeeMore -> strings.seeMore
                else -> null
            },
            onAction = {
                if (canToggleCountries) {
                    showAllCountries = !showAllCountries
                    if (!showAllCountries && section.rows.take(5).none { it.id == expandedCountryId }) expandedCountryId = null
                } else {
                    section.onSeeMore?.invoke()
                }
            },
        )
        when {
            section.isLoading && section.rows.isEmpty() -> RethinkLoadingState(label = "", contained = true)
            section.rows.isEmpty() -> RethinkEmptyState(icon = MaterialSymbols.Filled.Info, title = strings.noLogs, contained = true)
            else -> RethinkListGroup {
                displayedRows.forEachIndexed { index, row ->
                    val isExpanded = expandedCountryId == row.id
                    val isExpandable = !row.countryFlag.isNullOrBlank()
                    RethinkListItem(
                        modifier = if (isExpandable) {
                            Modifier.semantics {
                                stateDescription = if (isExpanded) strings.expanded else strings.collapsed
                            }
                        } else {
                            Modifier
                        },
                        headline = row.headline,
                        supporting = row.supporting,
                        // Domains and IPs never wrap mid-label; the middle elides so the TLD stays.
                        singleLineHeadline = true,
                        // ListItem's text column begins at 16dp without a leading slot.
                        // Text-only statistics read best with a deliberate 20dp inset.
                        contentOffset = if (row.countryFlag.isNullOrBlank() && row.leadingContent == null) {
                            Modifier.padding(start = SharedDimensions.spacingXs)
                        } else {
                            Modifier
                        },
                        leadingContent = when {
                            !row.countryFlag.isNullOrBlank() -> {
                                { Text(row.countryFlag, style = MaterialTheme.typography.headlineMedium) }
                            }
                            else -> row.leadingContent
                        },
                        position = cardPositionFor(index, displayedRows.lastIndex),
                        showTrailingChevron = false,
                        onClick = if (!row.countryFlag.isNullOrBlank()) {
                            { expandedCountryId = if (isExpanded) null else row.id }
                        } else null,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RethinkStatisticsMetric(row.metric, row.share, section.accentColor)
                                if (!row.countryFlag.isNullOrBlank()) {
                                    Spacer(Modifier.size(SharedDimensions.spacingXs))
                                    val chevronRotation by animateFloatAsState(
                                        targetValue = if (isExpanded) 180f else 0f,
                                        animationSpec = if (LocalRethinkMotion.current.reducedMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
                                        label = "country_chevron",
                                    )
                                    Icon(
                                        MaterialSymbols.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(SharedDimensions.iconSizeSm).rotate(chevronRotation),
                                    )
                                }
                            }
                        },
                    )
                    if (isExpanded) row.expandedContent?.invoke()
                }
            }
        }
    }
}

/**
 * Trailing metric with a thin proportional bar beneath it, so a section's ranking reads at a
 * glance. Purely visual: the metric text already carries the value for accessibility.
 */
@Composable
private fun RethinkStatisticsMetric(metric: String, share: Float?, accentColor: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            metric,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = accentColor,
            maxLines = 1,
        )
        if (share != null) {
            val motion = LocalRethinkMotion.current
            val animatedShare by animateFloatAsState(
                targetValue = share.coerceIn(0f, 1f),
                animationSpec = if (motion.reducedMotion) snap() else MaterialTheme.motionScheme.slowEffectsSpec(),
                label = "statistic_share",
            )
            Box(
                Modifier
                    .padding(top = SharedDimensions.spacingXs)
                    .width(ShareBarWidth)
                    .height(ShareBarHeight)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = SHARE_TRACK_ALPHA)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                // Grows from the trailing edge, aligned with the right-aligned metric above it.
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedShare.coerceAtLeast(MIN_VISIBLE_SHARE))
                        .clip(CircleShape)
                        .background(accentColor),
                )
            }
        }
    }
}

private const val SHARE_TRACK_ALPHA = 0.24f

/** Keeps a tiny-but-nonzero share visible as a dot instead of vanishing. */
private const val MIN_VISIBLE_SHARE = 0.08f


/** Shared expanded country detail. Hosts own the query and app artwork, not its visual hierarchy. */
@Composable
fun RethinkCountryBreakdown(
    title: String,
    emptyMessage: String,
    accentColor: Color,
    apps: List<RethinkCountryBreakdownItem>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(top = SharedDimensions.spacingXs, bottom = SharedDimensions.spacingXs),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadiusLg),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingSm),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            SectionHeader(title = title)
            if (apps.isEmpty()) {
                Text(emptyMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                apps.forEach { app ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(SharedDimensions.iconSizeSm), contentAlignment = Alignment.Center) { app.leadingContent?.invoke() }
                            Text(app.headline, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            app.metric,
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
