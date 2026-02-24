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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Unspecified
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkSegmentedChoiceRow
import com.celzero.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.ui.compose.theme.cardPositionFor
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
    val listState = rememberLazyListState()

    val topActiveConns = viewModel.getTopActiveConns.collectAsLazyPagingItems()
    val mostConnectedApps = viewModel.getAllowedAppNetworkActivity.collectAsLazyPagingItems()
    val mostBlockedApps = viewModel.getBlockedAppNetworkActivity.collectAsLazyPagingItems()
    val mostConnectedAsn = viewModel.getMostConnectedASN.collectAsLazyPagingItems()
    val mostBlockedAsn = viewModel.getMostBlockedASN.collectAsLazyPagingItems()
    val mostContactedDomains = viewModel.mcd.collectAsLazyPagingItems()
    val mostBlockedDomains = viewModel.mbd.collectAsLazyPagingItems()
    val mostContactedCountries = viewModel.getMostContactedCountries.collectAsLazyPagingItems()
    val mostContactedIps = viewModel.getMostContactedIps.collectAsLazyPagingItems()
    val mostBlockedIps = viewModel.getMostBlockedIps.collectAsLazyPagingItems()

    RethinkTopBarLazyColumnScreen(
        title = stringResource(id = R.string.title_statistics),
        containerColor = MaterialTheme.colorScheme.surface,
        topBarTitleTextStyle = MaterialTheme.typography.headlineMedium,
        listState = listState,
        contentPadding = PaddingValues(
            start = Dimensions.screenPaddingHorizontal,
            end = Dimensions.screenPaddingHorizontal,
            top = Dimensions.spacingMd,
            bottom = Dimensions.spacing3xl
        )
    ) {
            item {
                RethinkAnimatedSection(index = 0) {
                    UsageOverviewCard(dataUsage = uiState.dataUsage)
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
                    SummaryStatSection(
                        title = stringResource(id = R.string.top_active_conns),
                        type = SummaryStatisticsType.TOP_ACTIVE_CONNS,
                        pagingItems = topActiveConns,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }
            }

            item {
                RethinkAnimatedSection(index = 3) {
                    SummaryStatSection(
                        title = stringResource(id = R.string.ssv_app_network_activity_heading),
                        type = SummaryStatisticsType.MOST_CONNECTED_APPS,
                        pagingItems = mostConnectedApps,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }
            }

            item {
                RethinkAnimatedSection(index = 4) {
                    SummaryStatSection(
                        title = stringResource(id = R.string.ssv_app_blocked_heading),
                        type = SummaryStatisticsType.MOST_BLOCKED_APPS,
                        pagingItems = mostBlockedApps,
                        accentColor = MaterialTheme.colorScheme.error,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }
            }

            item {
                RethinkAnimatedSection(index = 5) {
                    SummaryStatSection(
                        title = stringResource(id = R.string.most_contacted_asn),
                        type = SummaryStatisticsType.MOST_CONNECTED_ASN,
                        pagingItems = mostConnectedAsn,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }
            }

            item {
                RethinkAnimatedSection(index = 6) {
                    SummaryStatSection(
                        title = stringResource(id = R.string.most_blocked_asn),
                        type = SummaryStatisticsType.MOST_BLOCKED_ASN,
                        pagingItems = mostBlockedAsn,
                        accentColor = MaterialTheme.colorScheme.error,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }
            }

            if (!uiState.loadMoreClicked) {
                item {
                    FilledTonalButton(
                        onClick = { viewModel.setLoadMoreClicked(true) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimensions.cornerRadius2xl)
                    ) {
                        Text(text = stringResource(id = R.string.load_more))
                    }
                }
            }

            if (uiState.loadMoreClicked) {
                item {
                    SummaryStatSection(
                        title = stringResource(id = R.string.ssv_most_contacted_domain_heading),
                        type = SummaryStatisticsType.MOST_CONTACTED_DOMAINS,
                        pagingItems = mostContactedDomains,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }

                item {
                    SummaryStatSection(
                        title = stringResource(id = R.string.ssv_most_blocked_domain_heading),
                        type = SummaryStatisticsType.MOST_BLOCKED_DOMAINS,
                        pagingItems = mostBlockedDomains,
                        accentColor = MaterialTheme.colorScheme.error,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }

                item {
                    SummaryStatSection(
                        title = stringResource(id = R.string.ssv_most_contacted_countries_heading),
                        type = SummaryStatisticsType.MOST_CONTACTED_COUNTRIES,
                        pagingItems = mostContactedCountries,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }

                item {
                    SummaryStatSection(
                        title = stringResource(id = R.string.ssv_most_contacted_ips_heading),
                        type = SummaryStatisticsType.MOST_CONTACTED_IPS,
                        pagingItems = mostContactedIps,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }

                item {
                    SummaryStatSection(
                        title = stringResource(id = R.string.ssv_most_blocked_ips_heading),
                        type = SummaryStatisticsType.MOST_BLOCKED_IPS,
                        pagingItems = mostBlockedIps,
                        accentColor = MaterialTheme.colorScheme.error,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }
            }
        }
}

@Composable
private fun UsageOverviewCard(dataUsage: DataUsageSummary) {
    val total = dataUsage.totalDownload + dataUsage.totalUpload
    val downloadShare = remember(total, dataUsage.totalDownload) {
        if (total > 0) dataUsage.totalDownload.toFloat() / total.toFloat() else 0f
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cornerRadius4xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.lbl_overall),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            LinearProgressIndicator(
                progress = { downloadShare },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(Dimensions.cornerRadiusXs)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UsageStatPill(
                    label = stringResource(id = R.string.lbl_download),
                    value = formatBytes(dataUsage.totalDownload),
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.44f),
                    valueColor = MaterialTheme.colorScheme.primary
                )
                UsageStatPill(
                    label = stringResource(id = R.string.lbl_upload),
                    value = formatBytes(dataUsage.totalUpload),
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f),
                    valueColor = MaterialTheme.colorScheme.tertiary
                )
                UsageStatPill(
                    label = stringResource(id = R.string.lbl_connections),
                    value = dataUsage.connectionsCount.toString(),
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun UsageStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: Color,
    valueColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Dimensions.cornerRadiusLg),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimeCategorySelector(
    selectedCategory: TimeCategory,
    onCategorySelected: (TimeCategory) -> Unit
) {
    RethinkSegmentedChoiceRow(
        options = TimeCategory.entries,
        selectedOption = selectedCategory,
        onOptionSelected = onCategorySelected,
        modifier = Modifier.fillMaxWidth(),
        fillEqually = true,
        label = { category, selected ->
            Text(
                text = when (category) {
                    TimeCategory.ONE_HOUR -> stringResource(id = R.string.time_window_one_hour_short)
                    TimeCategory.TWENTY_FOUR_HOUR -> stringResource(id = R.string.time_window_twenty_four_hours_short)
                    TimeCategory.SEVEN_DAYS -> stringResource(id = R.string.time_window_seven_days_short)
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    )
}

@Composable
private fun SummaryStatSection(
    title: String,
    type: SummaryStatisticsType,
    pagingItems: LazyPagingItems<AppConnection>,
    accentColor: Color,
    onSeeMoreClick: (SummaryStatisticsType) -> Unit
) {
    val visibleCount = minOf(pagingItems.itemCount, 5)
    val hasData = visibleCount > 0

    Column {
        SectionHeader(
            title = title,
            color = accentColor,
            actionLabel = if (hasData) stringResource(id = R.string.ssv_see_more) else null,
            onAction = if (hasData) {
                { onSeeMoreClick(type) }
            } else {
                null
            }
        )

        if (!hasData) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimensions.cornerRadius4xl),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
            ) {
                CompactEmptyState(
                    message = stringResource(id = R.string.lbl_no_logs),
                    modifier = Modifier.padding(vertical = Dimensions.spacingSm)
                )
            }
        } else {
            Column {
                for (index in 0 until visibleCount) {
                    val item = pagingItems[index] ?: continue
                    val metricText = item.totalBytes?.takeIf { it > 0L }?.let { formatBytes(it) } ?: item.count.toString()
                    val headline = if (type == SummaryStatisticsType.MOST_CONTACTED_COUNTRIES) {
                        "${item.flag} ${item.appOrDnsName.orEmpty()}".trim()
                    } else {
                        item.appOrDnsName?.takeIf { it.isNotBlank() } ?: item.ipAddress
                    }
                    val supporting = buildString {
                        append(stringResource(id = R.string.summary_connections_count, item.count))
                        item.totalBytes?.takeIf { it > 0L }?.let {
                            append(" \u00b7 ")
                            append(formatBytes(it))
                        }
                    }
                    val appIconPainter =
                        if (type.supportsAppIcon()) {
                            rememberStatisticsAppIconPainter(item.uid)
                        } else {
                            null
                        }
                    val hasTrueAppIcon = appIconPainter != null
                    val fallbackPainter =
                        if (type == SummaryStatisticsType.MOST_CONTACTED_COUNTRIES) {
                            null
                        } else {
                            painterResource(id = R.drawable.ic_app_info)
                        }

                    RethinkListItem(
                        headline = headline.ifBlank { "-" },
                        supporting = supporting,
                        leadingIconPainter = appIconPainter ?: fallbackPainter,
                        leadingIconTint = if (hasTrueAppIcon) Unspecified else accentColor,
                        leadingIconContainerColor = if (hasTrueAppIcon) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            accentColor.copy(alpha = 0.14f)
                        },
                        position = cardPositionFor(index = index, lastIndex = visibleCount - 1),
                        showTrailingChevron = false,
                        trailing = {
                            Text(
                                text = metricText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor
                            )
                        }
                    )
                }
            }
        }
    }
}
