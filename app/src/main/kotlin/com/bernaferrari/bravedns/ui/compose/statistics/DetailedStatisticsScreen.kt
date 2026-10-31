/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.bernaferrari.bravedns.ui.compose.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConnection
import com.bernaferrari.bravedns.data.SummaryStatisticsType
import com.bernaferrari.bravedns.ui.compose.statistics.RethinkDetailedStatistic
import com.bernaferrari.bravedns.ui.compose.statistics.RethinkDetailedStatisticsScreen
import com.bernaferrari.bravedns.ui.compose.statistics.RethinkDetailedStatisticsStrings
import com.bernaferrari.bravedns.util.formatByteCount
import com.bernaferrari.bravedns.viewmodel.DetailedStatisticsViewModel
import com.bernaferrari.bravedns.ui.compose.common.AppIcon
import com.bernaferrari.bravedns.ui.compose.common.lastTimeWindowLabel
import com.bernaferrari.bravedns.viewmodel.SummaryStatisticsViewModel.TimeCategory

/** Android data, resources, and icon adapter for the common detailed-statistics screen. */
@Composable
fun DetailedStatisticsScreen(
    viewModel: DetailedStatisticsViewModel,
    type: SummaryStatisticsType,
    timeCategory: TimeCategory,
    onBackClick: () -> Unit,
    rowActions: InsightsRowActions? = null,
) {
    val rows = rememberInsightsRowHandler(rowActions)
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

    RethinkDetailedStatisticsScreen(
        title = stringResource(getTitleResId(type)),
        subtitle = if (type == SummaryStatisticsType.TOP_ACTIVE_CONNS) null else lastTimeWindowLabel(timeCategory.value),
        itemCount = pagingItems.itemCount,
        isRefreshing = pagingItems.loadState.refresh is LoadState.Loading,
        isAppending = pagingItems.loadState.append is LoadState.Loading,
        strings = RethinkDetailedStatisticsStrings(
            loading = stringResource(R.string.lbl_loading),
            empty = stringResource(R.string.lbl_no_logs),
        ),
        onBackClick = onBackClick,
        itemAt = { index ->
            pagingItems[index]?.let { item ->
                item.toRethinkDetailedStatistic(type).copy(onClick = rows.onClick(type, item, timeCategory))
            }
        },
    )
    rows.Sheet()
}

@Composable
private fun AppConnection.toRethinkDetailedStatistic(type: SummaryStatisticsType): RethinkDetailedStatistic {
    val isCountryType = type == SummaryStatisticsType.MOST_CONTACTED_COUNTRIES
    val countryName = if (isCountryType) countryNameFromFlag(flag) else null
    val fallbackName = stringResource(R.string.network_log_app_name_unknown)
    val title = appOrDnsName?.takeIf { it.isNotBlank() } ?: ipAddress.takeIf { it.isNotBlank() } ?: fallbackName
    val metric = buildString {
        append(pluralStringResource(R.plurals.summary_connections_count, count, count))
        totalBytes?.takeIf { it > 0L }?.let { bytes ->
            append(" · ")
            append(formatByteCount(bytes))
        }
    }
    return RethinkDetailedStatistic(
        id = "$type-$uid-$title-$count-$flag",
        headline = if (isCountryType) countryName ?: title else title,
        supporting = metric,
        countryFlag = flag.takeIf { isCountryType && it.isNotBlank() },
        leadingContent = if (type.supportsAppIcon()) ({ AppIcon(uid) }) else null,
    )
}

private fun getTitleResId(type: SummaryStatisticsType): Int = when (type) {
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
