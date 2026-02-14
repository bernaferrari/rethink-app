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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.celzero.bravedns.ui.compose.theme.CompactEmptyState
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.SectionHeader
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
            AnimatedVisibility(
                visible = !uiState.loadMoreClicked,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.setLoadMoreClicked(true) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                Spacer(modifier = Modifier.height(Dimensions.spacingSm))
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
                    onSeeMoreClick = onSeeMoreClick
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
                    onSeeMoreClick = onSeeMoreClick
                )
            }

            if (uiState.loadMoreClicked) {
                item {
                    val data = viewModel.mcd.collectAsLazyPagingItems()
                    StatSection(
                        title = stringResource(id = R.string.ssv_most_contacted_domain_heading),
                        type = SummaryStatisticsType.MOST_CONTACTED_DOMAINS,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }

                item {
                    val data = viewModel.mbd.collectAsLazyPagingItems()
                    StatSection(
                        title = stringResource(id = R.string.ssv_most_blocked_domain_heading),
                        type = SummaryStatisticsType.MOST_BLOCKED_DOMAINS,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }

                item {
                    val data = viewModel.getMostContactedCountries.collectAsLazyPagingItems()
                    StatSection(
                        title = stringResource(id = R.string.ssv_most_contacted_countries_heading),
                        type = SummaryStatisticsType.MOST_CONTACTED_COUNTRIES,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }

                item {
                    val data = viewModel.getMostContactedIps.collectAsLazyPagingItems()
                    StatSection(
                        title = stringResource(id = R.string.ssv_most_contacted_ips_heading),
                        type = SummaryStatisticsType.MOST_CONTACTED_IPS,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick
                    )
                }

                item {
                    val data = viewModel.getMostBlockedIps.collectAsLazyPagingItems()
                    StatSection(
                        title = stringResource(id = R.string.ssv_most_blocked_ips_heading),
                        type = SummaryStatisticsType.MOST_BLOCKED_IPS,
                        pagingItems = data,
                        onSeeMoreClick = onSeeMoreClick
                    )
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
    ) {
        TimeCategory.entries.forEach { category ->
            val isSelected = category == selectedCategory
            
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.95f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "buttonScale"
            )

            val containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.surfaceVariant
            val contentColor = if (isSelected) 
                MaterialTheme.colorScheme.onPrimary 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant

            androidx.compose.material3.Surface(
                modifier = Modifier
                    .weight(1f)
                    .scale(scale)
                    .clip(RoundedCornerShape(Dimensions.buttonCornerRadius))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onCategorySelected(category) }
                    ),
                shape = RoundedCornerShape(Dimensions.buttonCornerRadius),
                color = containerColor,
                contentColor = contentColor
            ) {
                Text(
                    text = when (category) {
                        TimeCategory.ONE_HOUR -> "1h"
                        TimeCategory.TWENTY_FOUR_HOUR -> "24h"
                        TimeCategory.SEVEN_DAYS -> "7d"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(
                        vertical = Dimensions.spacingSm,
                        horizontal = Dimensions.spacingMd
                    )
                )
            }
        }
    }
}

@Composable
private fun UsageProgressHeader(dataUsage: DataUsageSummary) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimensions.cardCornerRadius),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.low)
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
                trackColor = MaterialTheme.colorScheme.surface,
            )

            Spacer(modifier = Modifier.height(Dimensions.spacingLg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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

            Spacer(modifier = Modifier.height(Dimensions.spacingSm))

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
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.low)
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
                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = Dimensions.cardPadding),
                                    thickness = Dimensions.dividerThickness,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "rowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(Dimensions.spacingSm))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { /* Handle click */ }
            )
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
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = Dimensions.Opacity.MEDIUM),
                    trackColor = MaterialTheme.colorScheme.surface,
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
                .alpha(Dimensions.Opacity.LOW)
        )
    }
}
