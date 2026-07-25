/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.celzero.bravedns.ui.compose.statistics

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import com.celzero.bravedns.ui.compose.theme.cardPositionFor

data class RethinkDetailedStatisticsStrings(
    val loading: String,
    val empty: String,
)

/** A display-ready statistic. Android injects the real app icon, while WASM can use the fallback. */
data class RethinkDetailedStatistic(
    val id: String,
    val headline: String,
    val supporting: String,
    val countryFlag: String? = null,
    val leadingContent: (@Composable () -> Unit)? = null,
)

/**
 * Target-neutral detail-statistics renderer. The item source remains lazy through [itemAt], so
 * Android Paging does not have to materialize every database result to share this screen.
 */
@Composable
fun RethinkDetailedStatisticsScreen(
    title: String,
    subtitle: String?,
    itemCount: Int,
    isRefreshing: Boolean,
    isAppending: Boolean,
    strings: RethinkDetailedStatisticsStrings,
    onBackClick: () -> Unit,
    itemAt: @Composable (Int) -> RethinkDetailedStatistic?,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title = title,
                subtitle = subtitle,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
                titleTextStyle = MaterialTheme.typography.headlineMedium,
                actions = {
                    if (itemCount > 0) StatisticsCountPill(itemCount)
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isRefreshing -> StatisticsLoading(strings.loading, Modifier.align(Alignment.Center))
                itemCount == 0 -> StatisticsEmpty(strings.empty, Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = SharedDimensions.screenPaddingHorizontal,
                        end = SharedDimensions.screenPaddingHorizontal,
                        top = SharedDimensions.spacingSm,
                        bottom = SharedDimensions.spacing3xl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(itemCount, key = { index -> index }) { index ->
                        itemAt(index)?.let { item ->
                            RethinkDetailedStatisticRow(
                                item = item,
                                position = cardPositionFor(index, itemCount - 1),
                            )
                        }
                    }
                    if (isAppending) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(SharedDimensions.spacingLg),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(SharedDimensions.iconSizeMd))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsCountPill(itemCount: Int) {
    Surface(
        shape = RoundedCornerShape(SharedDimensions.cornerRadiusFull),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(end = SharedDimensions.spacingSm),
    ) {
        Text(
            itemCount.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StatisticsLoading(label: String, modifier: Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SharedDimensions.spacingLg, vertical = SharedDimensions.spacingMd),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(SharedDimensions.iconSizeSm), strokeWidth = 2.dp)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatisticsEmpty(label: String, modifier: Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(SharedDimensions.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MaterialSymbols.Filled.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RethinkDetailedStatisticRow(
    item: RethinkDetailedStatistic,
    position: CardPosition,
) {
    val isCountry = !item.countryFlag.isNullOrBlank()
    RethinkListItem(
        headline = item.headline,
        supporting = item.supporting,
        position = position,
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(SharedDimensions.iconContainerRadius),
                color = when {
                    item.leadingContent != null -> Color.Transparent
                    isCountry -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.32f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                modifier = Modifier.size(SharedDimensions.iconContainerMd),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        item.leadingContent != null -> item.leadingContent.invoke()
                        isCountry -> Text(item.countryFlag.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        else -> Icon(MaterialSymbols.Filled.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    )
}
