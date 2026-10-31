/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.statistics

import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.bernaferrari.bravedns.ui.compose.common.RethinkAdaptiveWidth
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import com.bernaferrari.bravedns.ui.compose.common.rethinkContentWidth
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

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
    /** As in the summary: apps open App detail, domains and IPs their Connection sheet. */
    val onClick: (() -> Unit)? = null,
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
    /** Stable per-row key from the model (not the position) so rows keep state across reloads. */
    itemKey: ((Int) -> Any)? = null,
) {
    RethinkAdaptiveWidth(modifier) { widthClass ->
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                RethinkLargeTopBar(
                    title = title,
                    subtitle = subtitle,
                    onBackClick = onBackClick,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (itemCount > 0) {
                            RethinkPill(
                                itemCount.toString(),
                                Modifier.padding(end = SharedDimensions.spacingSm),
                                colors = RethinkPillDefaults.primary(),
                                size = RethinkPillSize.Large,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                when {
                    isRefreshing -> RethinkLoadingState(label = strings.loading, modifier = Modifier.align(Alignment.Center))
                    itemCount == 0 -> RethinkEmptyState(
                        icon = MaterialSymbols.Filled.Info,
                        title = strings.empty,
                        contained = true,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = SharedDimensions.screenPaddingHorizontal),
                    )
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxHeight()
                            .rethinkContentWidth(widthClass),
                        contentPadding = PaddingValues(
                            start = SharedDimensions.screenPaddingHorizontal,
                            end = SharedDimensions.screenPaddingHorizontal,
                            top = SharedDimensions.spacingSm,
                            bottom = SharedDimensions.spacing3xl,
                        ),
                    ) {
                        items(itemCount, key = itemKey) { index ->
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
                                    LoadingIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RethinkDetailedStatisticRow(
    item: RethinkDetailedStatistic,
    position: CardPosition,
) {
    // Apps lead with their icon and countries with their flag; domains, IPs and ASNs are
    // text-only rows (a generic glyph on every row says nothing).
    RethinkListItem(
        headline = item.headline,
        supporting = item.supporting,
        position = position,
        singleLineHeadline = true,
        onClick = item.onClick,
        showTrailingChevron = false,
        leadingContent = when {
            item.leadingContent != null -> item.leadingContent
            !item.countryFlag.isNullOrBlank() -> {
                {
                    Box(Modifier.size(SharedDimensions.iconContainerMd), contentAlignment = Alignment.Center) {
                        Text(item.countryFlag, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            else -> null
        },
    )
}
