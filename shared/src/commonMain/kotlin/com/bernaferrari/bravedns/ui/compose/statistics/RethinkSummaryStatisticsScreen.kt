/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.statistics

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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    RethinkAdaptiveWidth(modifier) { widthClass ->
        RethinkTopBarLazyColumnScreen(
            title = strings.title,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingMd,
                bottom = SharedDimensions.spacing3xl,
            ),
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

@Composable
private fun RethinkStatisticsWindowSelector(
    selectedWindow: RethinkStatisticsWindow,
    strings: RethinkSummaryStatisticsStrings,
    onWindowSelected: (RethinkStatisticsWindow) -> Unit,
) {
    val options = listOf(
        RethinkStatisticsWindow.OneHour to strings.oneHour,
        RethinkStatisticsWindow.TwentyFourHours to strings.twentyFourHours,
        RethinkStatisticsWindow.SevenDays to strings.sevenDays,
    )
    RethinkConnectedChoiceGroup(
        options = options.map { (window, label) -> RethinkChoiceOption(window, label) },
        selected = selectedWindow,
        onSelect = onWindowSelected,
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

    Column {
        SectionHeader(title = section.title, color = section.accentColor)
        when {
            section.isLoading && section.rows.isEmpty() -> RethinkSummaryLoadingCard()
            section.rows.isEmpty() -> RethinkSummaryEmptyCard(strings.noLogs)
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
                        leadingIconContainerColor = if (!row.countryFlag.isNullOrBlank()) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.32f)
                        } else {
                            section.accentColor.copy(alpha = 0.14f)
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
                                        targetValue = if (isExpanded) 90f else 0f,
                                        animationSpec = if (LocalRethinkMotion.current.reducedMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
                                        label = "country_chevron",
                                    )
                                    Icon(
                                        MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight,
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

        val canToggleCountries = isCountrySection && section.rows.size > 5
        if (canToggleCountries || (!isCountrySection && section.canSeeMore && section.onSeeMore != null)) {
            Row(Modifier.fillMaxWidth().padding(top = SharedDimensions.spacingSm), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(
                    onClick = {
                        if (canToggleCountries) {
                            showAllCountries = !showAllCountries
                            if (!showAllCountries && displayedRows.none { it.id == expandedCountryId }) expandedCountryId = null
                        } else {
                            section.onSeeMore?.invoke()
                        }
                    },
                ) {
                    Text(
                        if (canToggleCountries && showAllCountries) strings.seeLess else strings.seeMore,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
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
                    .width(SharedDimensions.iconContainerLg)
                    .height(SharedDimensions.tabIndicatorHeight)
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

@Composable
private fun RethinkSummaryLoadingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.groupCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.fillMaxWidth().padding(SharedDimensions.spacingLg), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
    }
}

@Composable
private fun RethinkSummaryEmptyCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.groupCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        RethinkEmptyState(
            icon = MaterialSymbols.Filled.Info,
            title = message,
            message = "",
        )
    }
}

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
            SectionHeader(title = title, color = accentColor)
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
