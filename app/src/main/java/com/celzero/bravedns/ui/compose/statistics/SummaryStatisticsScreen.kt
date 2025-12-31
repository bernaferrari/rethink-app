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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConnection
import com.celzero.bravedns.data.DataUsageSummary
import com.celzero.bravedns.data.SummaryStatisticsType
import com.celzero.bravedns.util.UIUtils.formatBytes
import com.celzero.bravedns.viewmodel.SummaryStatisticsViewModel
import com.celzero.bravedns.viewmodel.SummaryStatisticsViewModel.TimeCategory

@Composable
fun SummaryStatisticsScreen(
    viewModel: SummaryStatisticsViewModel,
    onSeeMoreClick: (SummaryStatisticsType) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (!uiState.loadMoreClicked) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.setLoadMoreClicked(true) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(text = stringResource(id = R.string.load_more))
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HeaderSection()
            }

            item {
                TimeCategorySelector(
                    selectedCategory = uiState.timeCategory,
                    onCategorySelected = { viewModel.timeCategoryChanged(it) }
                )
            }

            item {
                UsageProgressHeader(dataUsage = uiState.dataUsage)
            }

            // Stat Sections
            item {
                val data = viewModel.getTopActiveConns.collectAsLazyPagingItems()
                StatSection(
                    title = stringResource(id = R.string.top_active_conns),
                    type = SummaryStatisticsType.TOP_ACTIVE_CONNS,
                    pagingItems = data,
                    onSeeMoreClick = onSeeMoreClick
                )
            }

            item {
                val data = viewModel.getAllowedAppNetworkActivity.collectAsLazyPagingItems()
                StatSection(
                    title = stringResource(id = R.string.ssv_app_network_activity_heading),
                    type = SummaryStatisticsType.MOST_CONNECTED_APPS,
                    pagingItems = data,
                    onSeeMoreClick = onSeeMoreClick
                )
            }

            item {
                val data = viewModel.getBlockedAppNetworkActivity.collectAsLazyPagingItems()
                StatSection(
                    title = stringResource(id = R.string.ssv_app_blocked_heading),
                    type = SummaryStatisticsType.MOST_BLOCKED_APPS,
                    pagingItems = data,
                    onSeeMoreClick = onSeeMoreClick,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                val data = viewModel.getMostConnectedASN.collectAsLazyPagingItems()
                StatSection(
                    title = stringResource(id = R.string.most_contacted_asn),
                    type = SummaryStatisticsType.MOST_CONNECTED_ASN,
                    pagingItems = data,
                    onSeeMoreClick = onSeeMoreClick
                )
            }

            item {
                val data = viewModel.getMostBlockedASN.collectAsLazyPagingItems()
                StatSection(
                    title = stringResource(id = R.string.most_blocked_asn),
                    type = SummaryStatisticsType.MOST_BLOCKED_ASN,
                    pagingItems = data,
                    onSeeMoreClick = onSeeMoreClick,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            if (uiState.loadMoreClicked) {
                item {
                    val data = viewModel.mcd.collectAsLazyPagingItems()
                    StatSection(
                        title = stringResource(id = R.string.ssv_most_contacted_domain_heading),
                        type = SummaryStatisticsType.MOST_CONTACTED_DOMAINS,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                item {
                    val data = viewModel.mbd.collectAsLazyPagingItems()
                    StatSection(
                        title = stringResource(id = R.string.ssv_most_blocked_domain_heading),
                        type = SummaryStatisticsType.MOST_BLOCKED_DOMAINS,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                item {
                    val data = viewModel.getMostContactedCountries.collectAsLazyPagingItems()
                    StatSection(
                        title = stringResource(id = R.string.ssv_most_contacted_countries_heading),
                        type = SummaryStatisticsType.MOST_CONTACTED_COUNTRIES,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                item {
                    val data = viewModel.getMostContactedIps.collectAsLazyPagingItems()
                    StatSection(
                        title = stringResource(id = R.string.ssv_most_contacted_ips_heading),
                        type = SummaryStatisticsType.MOST_CONTACTED_IPS,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                item {
                    val data = viewModel.getMostBlockedIps.collectAsLazyPagingItems()
                    StatSection(
                        title = stringResource(id = R.string.ssv_most_blocked_ips_heading),
                        type = SummaryStatisticsType.MOST_BLOCKED_IPS,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
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
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.alpha(0.5f)
        )
        Text(
            text = stringResource(id = R.string.about_title_desc),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alpha(0.5f)
        )
    }
}

@Composable
private fun TimeCategorySelector(
    selectedCategory: TimeCategory,
    onCategorySelected: (TimeCategory) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimeCategory.entries.forEach { category ->
            val isSelected = category == selectedCategory
            Button(
                onClick = { onCategorySelected(category) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(
                    text = when (category) {
                        TimeCategory.ONE_HOUR -> "1h"
                        TimeCategory.TWENTY_FOUR_HOUR -> "24h"
                        TimeCategory.SEVEN_DAYS -> "7d"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun UsageProgressHeader(dataUsage: DataUsageSummary) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val total = dataUsage.totalDownload + dataUsage.totalUpload
            val progress = remember(total, dataUsage.totalDownload) {
                if (total > 0) (dataUsage.totalDownload.toFloat() / total.toFloat()) else 0f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Download: ${formatBytes(dataUsage.totalDownload)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Total: ${formatBytes(total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Upload: ${formatBytes(dataUsage.totalUpload)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatSection(
    title: String,
    type: SummaryStatisticsType,
    pagingItems: LazyPagingItems<AppConnection>,
    onSeeMoreClick: (SummaryStatisticsType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )
            TextButton(onClick = { onSeeMoreClick(type) }) {
                Text(text = stringResource(id = R.string.ssv_see_more).uppercase())
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column {
                if (pagingItems.itemCount == 0) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(text = "No data available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                } else {
                    for (i in 0 until pagingItems.itemCount) {
                        pagingItems[i]?.let { item ->
                            StatItemRow(item = item, type = type)
                            if (i < pagingItems.itemCount - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
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
            .clickable { /* Handle click if needed */ }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon/Flag
        if (type == SummaryStatisticsType.MOST_CONTACTED_COUNTRIES) {
            Text(
                text = item.flag,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.size(32.dp)
            )
        } else {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_app_info), // Placeholder
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.appOrDnsName ?: "",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Connections: ${item.count}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val totalBytes = item.totalBytes ?: 0L
            val downloadBytes = item.downloadBytes ?: 0L
            if (totalBytes > 0) {
                val progress = remember(downloadBytes, totalBytes) {
                    if (totalBytes > 0) (downloadBytes.toFloat() / totalBytes.toFloat()) else 0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    trackColor = MaterialTheme.colorScheme.surface
                )
                Text(
                    text = formatBytes(totalBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_right_arrow_white),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}
