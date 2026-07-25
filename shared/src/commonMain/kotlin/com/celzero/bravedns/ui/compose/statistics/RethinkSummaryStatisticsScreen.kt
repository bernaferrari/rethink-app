/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.celzero.bravedns.ui.compose.statistics

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import com.celzero.bravedns.ui.compose.theme.cardPositionFor

enum class RethinkStatisticsWindow { OneHour, TwentyFourHours, SevenDays }

data class RethinkSummaryStatisticsRow(
    val id: String,
    val headline: String,
    val supporting: String? = null,
    val metric: String,
    val countryFlag: String? = null,
    val leadingContent: (@Composable () -> Unit)? = null,
    val expandedContent: (@Composable () -> Unit)? = null,
)

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
    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBarTitleTextStyle = MaterialTheme.typography.headlineMedium,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingMd,
            bottom = SharedDimensions.spacing3xl,
        ),
        topBarActions = {
            RethinkStatisticsWindowSelector(selectedWindow, strings, onWindowSelected)
        },
    ) {
        item {
            RethinkUsageOverviewCard(
                overview = overview,
                overallLabel = strings.overall,
                downloadLabel = strings.download,
                uploadLabel = strings.upload,
                connectionsLabel = strings.connections,
            )
        }
        sections.forEach { section ->
            item(key = section.id) { RethinkSummaryStatisticsSectionCard(section, strings) }
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
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, (window, label) ->
            val selected = window == selectedWindow
            ToggleButton(
                checked = selected,
                onCheckedChange = { if (it && !selected) onWindowSelected(window) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.semantics { role = Role.RadioButton },
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
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
                    RethinkListItem(
                        headline = row.headline,
                        supporting = row.supporting,
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
                                Text(row.metric, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = section.accentColor)
                                if (!row.countryFlag.isNullOrBlank()) {
                                    Spacer(Modifier.size(SharedDimensions.spacingXs))
                                    Icon(
                                        MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp).rotate(if (isExpanded) 90f else 0f),
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

@Composable
private fun RethinkSummaryLoadingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadius4xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.fillMaxWidth().padding(SharedDimensions.spacingLg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(SharedDimensions.iconSizeMd))
        }
    }
}

@Composable
private fun RethinkSummaryEmptyCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadius4xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(SharedDimensions.cardPadding),
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
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = accentColor)
            if (apps.isEmpty()) {
                Text(emptyMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                apps.forEach { app ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { app.leadingContent?.invoke() }
                            Text(app.headline, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(app.metric, style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
