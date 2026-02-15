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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
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
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.ui.compose.theme.rememberReducedMotion
import com.celzero.bravedns.util.UIUtils.formatBytes
import com.celzero.bravedns.viewmodel.SummaryStatisticsViewModel
import com.celzero.bravedns.viewmodel.SummaryStatisticsViewModel.TimeCategory

@Composable
fun SummaryStatisticsScreen(
    viewModel: SummaryStatisticsViewModel,
    onSeeMoreClick: (SummaryStatisticsType) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val reducedMotion = rememberReducedMotion()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            AnimatedVisibility(
                visible = !uiState.loadMoreClicked,
                enter = if (reducedMotion) fadeIn() else fadeIn() + slideInVertically { it / 2 },
                exit = if (reducedMotion) fadeOut() else fadeOut() + slideOutVertically { it / 3 }
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.setLoadMoreClicked(true) },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(Dimensions.buttonCornerRadiusLarge),
                    icon = {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_down),
                            contentDescription = null
                        )
                    },
                    text = { Text(text = stringResource(id = R.string.load_more)) }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Dimensions.screenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            item {
                RethinkAnimatedSection(index = 0) {
                    Spacer(modifier = Modifier.height(Dimensions.spacingSm))
                    HeaderSection()
                }
            }

            item {
                RethinkAnimatedSection(index = 1) {
                    TimeCategorySelector(
                        selectedCategory = uiState.timeCategory,
                        onCategorySelected = { viewModel.timeCategoryChanged(it) }
                    )
                }
            }

            item {
                RethinkAnimatedSection(index = 2) {
                    UsageProgressHeader(dataUsage = uiState.dataUsage)
                }
            }

            // Stat Sections
            item {
                val data = viewModel.getTopActiveConns.collectAsLazyPagingItems()
                RethinkAnimatedSection(index = 3) {
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
                RethinkAnimatedSection(index = 4) {
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
                RethinkAnimatedSection(index = 5) {
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
                RethinkAnimatedSection(index = 6) {
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
                RethinkAnimatedSection(index = 7) {
                    StatSection(
                        title = stringResource(id = R.string.most_blocked_asn),
                        type = SummaryStatisticsType.MOST_BLOCKED_ASN,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }
            }

            if (uiState.loadMoreClicked) {
                item {
                    val data = viewModel.mcd.collectAsLazyPagingItems()
                    RethinkAnimatedSection(index = 8) {
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
                    RethinkAnimatedSection(index = 9) {
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
                    RethinkAnimatedSection(index = 10) {
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
                    RethinkAnimatedSection(index = 11) {
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
                    RethinkAnimatedSection(index = 12) {
                        StatSection(
                            title = stringResource(id = R.string.ssv_most_blocked_ips_heading),
                            type = SummaryStatisticsType.MOST_BLOCKED_IPS,
                            pagingItems = data,
                            onSeeMoreClick = onSeeMoreClick
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(Dimensions.spacing3xl))
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    Column {
        Text(
            text = stringResource(id = R.string.app_name_small_case),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.alpha(Dimensions.Opacity.LOW)
        )
        Spacer(modifier = Modifier.height(Dimensions.spacingXs))
        Text(
            text = stringResource(id = R.string.about_title_desc),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alpha(Dimensions.Opacity.MEDIUM)
        )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = Dimensions.dividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.none)
    ) {
        Column(modifier = Modifier.padding(Dimensions.cardPadding)) {
            val total = dataUsage.totalDownload + dataUsage.totalUpload
            val progress = remember(total, dataUsage.totalDownload) {
                if (total > 0) (dataUsage.totalDownload.toFloat() / total.toFloat()) else 0f
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
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
    Column {
        SectionHeader(
            title = title,
            actionLabel = stringResource(id = R.string.ssv_see_more).uppercase(),
            onAction = { onSeeMoreClick(type) }
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimensions.cardCornerRadius),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(
                width = Dimensions.dividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.none)
        ) {
            if (pagingItems.itemCount == 0) {
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
                                    modifier = Modifier.padding(horizontal = Dimensions.cardPadding),
                                    thickness = Dimensions.dividerThickness,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                                        alpha = Dimensions.Opacity.LOW
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
            .padding(Dimensions.cardPadding),
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
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_app_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimensions.iconSizeLg)
            )
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

        Spacer(modifier = Modifier.width(Dimensions.spacingSm))

        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_right_arrow_white),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(Dimensions.iconSizeSm)
                .alpha(Dimensions.Opacity.MEDIUM)
        )
    }
}
