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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConnection
import com.celzero.bravedns.data.SummaryStatisticsType
import com.celzero.bravedns.ui.compose.theme.CompactEmptyState
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.util.UIUtils.formatBytes
import com.celzero.bravedns.viewmodel.DetailedStatisticsViewModel
import com.celzero.bravedns.viewmodel.SummaryStatisticsViewModel.TimeCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedStatisticsScreen(
    viewModel: DetailedStatisticsViewModel,
    type: SummaryStatisticsType,
    timeCategory: TimeCategory,
    onBackClick: () -> Unit
) {
    val pagingItems = when (type) {
        SummaryStatisticsType.TOP_ACTIVE_CONNS -> viewModel.getAllActiveConns
        SummaryStatisticsType.MOST_CONNECTED_APPS -> viewModel.getAllAllowedAppNetworkActivity
        SummaryStatisticsType.MOST_BLOCKED_APPS -> viewModel.getAllBlockedAppNetworkActivity
        SummaryStatisticsType.MOST_CONNECTED_ASN -> viewModel.getAllAllowedAsn
        SummaryStatisticsType.MOST_BLOCKED_ASN -> viewModel.getAllBlockedAsn
        SummaryStatisticsType.MOST_CONTACTED_DOMAINS -> viewModel.getAllContactedDomains
        SummaryStatisticsType.MOST_BLOCKED_DOMAINS -> viewModel.getAllBlockedDomains
        SummaryStatisticsType.MOST_CONTACTED_IPS -> viewModel.getAllContactedIps
        SummaryStatisticsType.MOST_BLOCKED_IPS -> viewModel.getAllBlockedIps
        SummaryStatisticsType.MOST_CONTACTED_COUNTRIES -> viewModel.getAllContactedCountries
    }.collectAsLazyPagingItems()

    LaunchedEffect(type, timeCategory) {
        viewModel.setData(type)
        viewModel.timeCategoryChanged(timeCategory)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DetailedStatisticsTopBar(type, scrollBehavior, onBackClick)
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (pagingItems.loadState.refresh is LoadState.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (pagingItems.itemCount == 0) {
                CompactEmptyState(
                    message = stringResource(R.string.blocklist_update_check_failure),
                    icon = Icons.Default.Info,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = Dimensions.screenPaddingHorizontal,
                                vertical = Dimensions.spacingSm
                            ),
                        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = Dimensions.spacingLg,
                                    vertical = Dimensions.spacingMd
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(id = getTitleResId(type)),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (type != SummaryStatisticsType.TOP_ACTIVE_CONNS) {
                                    Text(
                                        text = getTimeCategoryText(timeCategory),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(Dimensions.cornerRadiusFull),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = pagingItems.itemCount.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(
                                        horizontal = Dimensions.spacingMd,
                                        vertical = 4.dp
                                    )
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = Dimensions.screenPaddingHorizontal,
                            vertical = Dimensions.spacingSm
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
                    ) {
                        items(pagingItems.itemCount) { index ->
                            pagingItems[index]?.let { item ->
                                DetailedStatItemCard(item, type)
                            }
                        }

                        if (pagingItems.loadState.append is LoadState.Loading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Dimensions.spacingLg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(Dimensions.iconSizeMd)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailedStatisticsTopBar(
    type: SummaryStatisticsType,
    scrollBehavior: TopAppBarScrollBehavior,
    onBackClick: () -> Unit
) {
    RethinkLargeTopBar(
        title = stringResource(id = getTitleResId(type)),
        onBackClick = onBackClick,
        scrollBehavior = scrollBehavior,
        titleTextStyle = MaterialTheme.typography.headlineMedium
    )
}

@Composable
private fun DetailedStatItemCard(item: AppConnection, type: SummaryStatisticsType) {
    val appIconPainter =
        if (type.supportsAppIcon()) {
            rememberStatisticsAppIconPainter(item.uid)
        } else {
            null
        }
    val hasTrueAppIcon = appIconPainter != null

    Card(
        shape = RoundedCornerShape(Dimensions.cornerRadiusXl),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimensions.spacingLg, vertical = Dimensions.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (type == SummaryStatisticsType.MOST_CONTACTED_COUNTRIES) {
                Text(
                    text = item.flag,
                    style = MaterialTheme.typography.headlineMedium
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(Dimensions.cornerRadiusMd),
                    color = if (hasTrueAppIcon) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Box(
                        modifier = Modifier.size(38.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (appIconPainter != null) {
                            Image(
                                painter = appIconPainter,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_app_info),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(Dimensions.spacingMd))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.appOrDnsName ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(Dimensions.spacingXs))
                Text(
                    text = "Connections: ${item.count}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val totalBytes = item.totalBytes ?: 0L
                val downloadBytes = item.downloadBytes ?: 0L
                if (totalBytes > 0) {
                    Spacer(modifier = Modifier.height(Dimensions.spacingSm))
                    val progress = remember(downloadBytes, totalBytes) {
                        if (totalBytes > 0) (downloadBytes.toFloat() / totalBytes.toFloat()) else 0f
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(Dimensions.cornerRadius2xs)),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Spacer(modifier = Modifier.height(Dimensions.spacingXs))
                    Text(
                        text = "Data: ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun getTitleResId(type: SummaryStatisticsType): Int {
    return when (type) {
        SummaryStatisticsType.TOP_ACTIVE_CONNS -> R.string.top_active_conns
        SummaryStatisticsType.MOST_CONNECTED_APPS -> R.string.ssv_app_network_activity_heading
        SummaryStatisticsType.MOST_BLOCKED_APPS -> R.string.ssv_app_blocked_heading
        SummaryStatisticsType.MOST_CONNECTED_ASN -> R.string.most_contacted_asn
        SummaryStatisticsType.MOST_BLOCKED_ASN -> R.string.most_blocked_asn
        SummaryStatisticsType.MOST_CONTACTED_DOMAINS -> R.string.ssv_most_contacted_domain_heading
        SummaryStatisticsType.MOST_BLOCKED_DOMAINS -> R.string.ssv_most_blocked_domain_heading
        SummaryStatisticsType.MOST_CONTACTED_IPS -> R.string.ssv_most_contacted_ips_heading
        SummaryStatisticsType.MOST_BLOCKED_IPS -> R.string.ssv_most_blocked_ips_heading
        SummaryStatisticsType.MOST_CONTACTED_COUNTRIES -> R.string.ssv_most_contacted_countries_heading
    }
}

@Composable
private fun getTimeCategoryText(timeCategory: TimeCategory): String {
    return when (timeCategory) {
        TimeCategory.ONE_HOUR -> stringResource(
            id = R.string.three_argument,
            stringResource(id = R.string.lbl_last),
            stringResource(id = R.string.numeric_one),
            stringResource(id = R.string.lbl_hour)
        )

        TimeCategory.TWENTY_FOUR_HOUR -> stringResource(
            id = R.string.three_argument,
            stringResource(id = R.string.lbl_last),
            stringResource(id = R.string.numeric_twenty_four),
            stringResource(id = R.string.lbl_hour)
        )

        TimeCategory.SEVEN_DAYS -> stringResource(
            id = R.string.three_argument,
            stringResource(id = R.string.lbl_last),
            stringResource(id = R.string.numeric_seven),
            stringResource(id = R.string.lbl_day)
        )
    }
}
