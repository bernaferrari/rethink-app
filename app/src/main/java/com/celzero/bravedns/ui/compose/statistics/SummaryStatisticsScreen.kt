/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.ui.compose.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConnection
import com.celzero.bravedns.data.DataUsageSummary
import com.celzero.bravedns.data.SummaryStatisticsType
import com.celzero.bravedns.ui.compose.theme.CompactEmptyState
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkAnimatedSection
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.util.UIUtils.formatBytes
import com.celzero.bravedns.viewmodel.SummaryStatisticsViewModel
import com.celzero.bravedns.viewmodel.SummaryStatisticsViewModel.TimeCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryStatisticsScreen(
    viewModel: SummaryStatisticsViewModel,
    onSeeMoreClick: (SummaryStatisticsType) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RethinkLargeTopBar(
                title = stringResource(id = R.string.title_statistics),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = Dimensions.screenPaddingHorizontal,
                end = Dimensions.screenPaddingHorizontal,
                top = Dimensions.spacingMd,
                bottom = Dimensions.spacing3xl
            ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            item {
                RethinkAnimatedSection(index = 0) {
                    TimeCategorySelector(
                        selectedCategory = uiState.timeCategory,
                        onCategorySelected = { viewModel.timeCategoryChanged(it) }
                    )
                }
            }

            item {
                RethinkAnimatedSection(index = 1) {
                    UsageProgressHeader(dataUsage = uiState.dataUsage)
                }
            }

            // Stat Sections
            item {
                val data = viewModel.getTopActiveConns.collectAsLazyPagingItems()
                RethinkAnimatedSection(index = 2) {
                    StatSection(
                        title = stringResource(id = R.string.top_active_conns),
                        type = SummaryStatisticsType.TOP_ACTIVE_CONNS,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }
            }

            item {
                val data = viewModel.getAllowedAppNetworkActivity.collectAsLazyPagingItems()
                RethinkAnimatedSection(index = 3) {
                    StatSection(
                        title = stringResource(id = R.string.ssv_app_network_activity_heading),
                        type = SummaryStatisticsType.MOST_CONNECTED_APPS,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }
            }

            item {
                val data = viewModel.getBlockedAppNetworkActivity.collectAsLazyPagingItems()
                RethinkAnimatedSection(index = 4) {
                    StatSection(
                        title = stringResource(id = R.string.ssv_app_blocked_heading),
                        type = SummaryStatisticsType.MOST_BLOCKED_APPS,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }
            }

            item {
                val data = viewModel.getMostConnectedASN.collectAsLazyPagingItems()
                RethinkAnimatedSection(index = 5) {
                    StatSection(
                        title = stringResource(id = R.string.most_contacted_asn),
                        type = SummaryStatisticsType.MOST_CONNECTED_ASN,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }
            }

            item {
                val data = viewModel.getMostBlockedASN.collectAsLazyPagingItems()
                RethinkAnimatedSection(index = 6) {
                    StatSection(
                        title = stringResource(id = R.string.most_blocked_asn),
                        type = SummaryStatisticsType.MOST_BLOCKED_ASN,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }
            }

            if (!uiState.loadMoreClicked) {
                item {
                    FilledTonalButton(
                        onClick = { viewModel.setLoadMoreClicked(true) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimensions.cardCornerRadius)
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_down),
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(Dimensions.spacingSm))
                        Text(text = stringResource(id = R.string.load_more))
                    }
                }
            }

            if (uiState.loadMoreClicked) {
                item {
                    val data = viewModel.mcd.collectAsLazyPagingItems()
                    RethinkAnimatedSection(index = 7) {
                        StatSection(
                            title = stringResource(id = R.string.ssv_most_contacted_domain_heading),
                            type = SummaryStatisticsType.MOST_CONTACTED_DOMAINS,
                            pagingItems = data,
                            onSeeMoreClick = onSeeMoreClick
                        )
                    }
                }

                item {
                    val data = viewModel.mbd.collectAsLazyPagingItems()
                    RethinkAnimatedSection(index = 8) {
                        StatSection(
                            title = stringResource(id = R.string.ssv_most_blocked_domain_heading),
                            type = SummaryStatisticsType.MOST_BLOCKED_DOMAINS,
                            pagingItems = data,
                            onSeeMoreClick = onSeeMoreClick
                        )
                    }
                }

                item {
                    val data = viewModel.getMostContactedCountries.collectAsLazyPagingItems()
                    RethinkAnimatedSection(index = 9) {
                        StatSection(
                            title = stringResource(id = R.string.ssv_most_contacted_countries_heading),
                            type = SummaryStatisticsType.MOST_CONTACTED_COUNTRIES,
                            pagingItems = data,
                            onSeeMoreClick = onSeeMoreClick
                        )
                    }
                }

                item {
                    val data = viewModel.getMostContactedIps.collectAsLazyPagingItems()
                    RethinkAnimatedSection(index = 10) {
                        StatSection(
                            title = stringResource(id = R.string.ssv_most_contacted_ips_heading),
                            type = SummaryStatisticsType.MOST_CONTACTED_IPS,
                            pagingItems = data,
                            onSeeMoreClick = onSeeMoreClick
                        )
                    }
                }

                item {
                    val data = viewModel.getMostBlockedIps.collectAsLazyPagingItems()
                    RethinkAnimatedSection(index = 11) {
                        StatSection(
                            title = stringResource(id = R.string.ssv_most_blocked_ips_heading),
                            type = SummaryStatisticsType.MOST_BLOCKED_IPS,
                            pagingItems = data,
                            onSeeMoreClick = onSeeMoreClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeCategorySelector(
    selectedCategory: TimeCategory,
    onCategorySelected: (TimeCategory) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        TimeCategory.entries.forEachIndexed { index, category ->
            SegmentedButton(
                selected = category == selectedCategory,
                onClick = { onCategorySelected(category) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = TimeCategory.entries.size
                ),
                label = {
                    Text(
                        text = when (category) {
                            TimeCategory.ONE_HOUR -> "1h"
                            TimeCategory.TWENTY_FOUR_HOUR -> "24h"
                            TimeCategory.SEVEN_DAYS -> "7d"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (category == selectedCategory)
                            FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
        }
    }
}

@Composable
private fun UsageProgressHeader(dataUsage: DataUsageSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(Dimensions.spacingXl)) {
            val total = dataUsage.totalDownload + dataUsage.totalUpload
            val progress = remember(total, dataUsage.totalDownload) {
                if (total > 0) (dataUsage.totalDownload.toFloat() / total.toFloat()) else 0f
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            Spacer(modifier = Modifier.height(Dimensions.spacingLg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Download column
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.iconSizeSm)
                    )
                    Spacer(modifier = Modifier.width(Dimensions.spacingXs))
                    Column {
                        Text(
                            text = formatBytes(dataUsage.totalDownload),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.symbol_download).format(""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Upload column
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(Dimensions.iconSizeSm)
                    )
                    Spacer(modifier = Modifier.width(Dimensions.spacingXs))
                    Column {
                        Text(
                            text = formatBytes(dataUsage.totalUpload),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = stringResource(R.string.lbl_upload),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Total column
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatBytes(total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.lbl_overall),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatSection(
    title: String,
    type: SummaryStatisticsType,
    pagingItems: LazyPagingItems<AppConnection>,
    onSeeMoreClick: (SummaryStatisticsType) -> Unit
) {
    val hasData = pagingItems.itemCount > 0
    Column {
        SectionHeader(
            title = title,
            actionLabel = if (hasData) stringResource(id = R.string.ssv_see_more) else null,
            onAction = if (hasData) {
                { onSeeMoreClick(type) }
            } else {
                null
            }
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
            elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.none)
        ) {
            if (!hasData) {
                CompactEmptyState(
                    message = stringResource(R.string.lbl_no_logs),
                    icon = Icons.Default.Info
                )
            } else {
                Column(modifier = Modifier.padding(vertical = Dimensions.spacingSm)) {
                    for (i in 0 until minOf(pagingItems.itemCount, 5)) {
                        pagingItems[i]?.let { item ->
                            StatItemRow(item = item, type = type)
                            if (i < minOf(pagingItems.itemCount, 5) - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = Dimensions.spacingXl),
                                    thickness = Dimensions.dividerThickness,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                                        alpha = 0.3f
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItemRow(
    item: AppConnection,
    type: SummaryStatisticsType
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.spacingXl, vertical = Dimensions.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon/Flag
        if (type == SummaryStatisticsType.MOST_CONTACTED_COUNTRIES) {
            Text(
                text = item.flag,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.size(Dimensions.iconSizeLg)
            )
        } else {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_app_info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(Dimensions.spacingLg))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.appOrDnsName ?: "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimensions.spacingXs))
            Text(
                text = "Connections: ${item.count}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val totalBytes = item.totalBytes ?: 0L
            val downloadBytes = item.downloadBytes ?: 0L
            if (totalBytes > 0) {
                Spacer(modifier = Modifier.height(Dimensions.spacingXs))
                val progress = remember(downloadBytes, totalBytes) {
                    if (totalBytes > 0) (downloadBytes.toFloat() / totalBytes.toFloat()) else 0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimensions.spacingXs)
                        .clip(RoundedCornerShape(Dimensions.spacingXs)),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = Dimensions.Opacity.MEDIUM),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text(
                    text = formatBytes(totalBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(Dimensions.Opacity.MEDIUM)
                )
            }
        }

    }
}
