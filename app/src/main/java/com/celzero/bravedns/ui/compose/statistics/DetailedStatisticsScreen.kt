/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.celzero.bravedns.ui.compose.statistics

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConnection
import com.celzero.bravedns.data.SummaryStatisticsType
import com.celzero.bravedns.ui.compose.statistics.RethinkDetailedStatistic
import com.celzero.bravedns.ui.compose.statistics.RethinkDetailedStatisticsScreen
import com.celzero.bravedns.ui.compose.statistics.RethinkDetailedStatisticsStrings
import com.celzero.bravedns.util.UIUtils.formatBytes
import com.celzero.bravedns.viewmodel.DetailedStatisticsViewModel
import com.celzero.bravedns.viewmodel.SummaryStatisticsViewModel.TimeCategory

/** Android data, resources, and icon adapter for the common detailed-statistics screen. */
@Composable
fun DetailedStatisticsScreen(
    viewModel: DetailedStatisticsViewModel,
    type: SummaryStatisticsType,
    timeCategory: TimeCategory,
    onBackClick: () -> Unit,
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

    RethinkDetailedStatisticsScreen(
        title = stringResource(getTitleResId(type)),
        subtitle = if (type == SummaryStatisticsType.TOP_ACTIVE_CONNS) null else getTimeCategoryText(timeCategory),
        itemCount = pagingItems.itemCount,
        isRefreshing = pagingItems.loadState.refresh is LoadState.Loading,
        isAppending = pagingItems.loadState.append is LoadState.Loading,
        strings = RethinkDetailedStatisticsStrings(
            loading = stringResource(R.string.lbl_loading),
            empty = stringResource(R.string.blocklist_update_check_failure),
        ),
        onBackClick = onBackClick,
        itemAt = { index -> pagingItems[index]?.toRethinkDetailedStatistic(type) },
    )
}

@Composable
private fun AppConnection.toRethinkDetailedStatistic(type: SummaryStatisticsType): RethinkDetailedStatistic {
    val isCountryType = type == SummaryStatisticsType.MOST_CONTACTED_COUNTRIES
    val appIconPainter = if (type.supportsAppIcon()) rememberStatisticsAppIconPainter(uid) else null
    val countryName = if (isCountryType) countryNameFromFlag(flag) else null
    val fallbackName = stringResource(R.string.network_log_app_name_unknown)
    val title = appOrDnsName?.takeIf { it.isNotBlank() } ?: ipAddress.takeIf { it.isNotBlank() } ?: fallbackName
    val metric = buildString {
        append(stringResource(R.string.summary_connections_count, count))
        totalBytes?.takeIf { it > 0L }?.let { bytes ->
            append(" · ")
            append(formatBytes(bytes))
        }
    }
    return RethinkDetailedStatistic(
        id = "$type-$uid-$title-$count-$flag",
        headline = if (isCountryType) metric else title,
        supporting = if (isCountryType) countryName ?: title else metric,
        countryFlag = flag.takeIf { isCountryType && it.isNotBlank() },
        leadingContent = appIconPainter?.let { painter ->
            { Icon(painter, null, tint = Color.Unspecified, modifier = Modifier.size(24.dp)) }
        },
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

@Composable
private fun getTimeCategoryText(timeCategory: TimeCategory): String {
    val window = when (timeCategory) {
        TimeCategory.ONE_HOUR -> stringResource(R.string.time_window_one_hour_short)
        TimeCategory.TWENTY_FOUR_HOUR -> stringResource(R.string.time_window_twenty_four_hours_short)
        TimeCategory.SEVEN_DAYS -> stringResource(R.string.time_window_seven_days_short)
    }
    return "${stringResource(R.string.lbl_last)} $window"
}
