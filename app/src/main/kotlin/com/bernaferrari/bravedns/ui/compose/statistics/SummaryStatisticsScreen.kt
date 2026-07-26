/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.bernaferrari.bravedns.ui.compose.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Unspecified
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConnection
import com.bernaferrari.bravedns.data.DataUsageSummary
import com.bernaferrari.bravedns.data.SummaryStatisticsType
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.util.UIUtils.formatBytes
import com.bernaferrari.bravedns.viewmodel.SummaryStatisticsViewModel
import com.bernaferrari.bravedns.viewmodel.SummaryStatisticsViewModel.TimeCategory

/** Android paging, database, resources, and app-icon adapter for the shared statistics screen. */
@Composable
fun SummaryStatisticsScreen(
    viewModel: SummaryStatisticsViewModel,
    persistentState: PersistentState,
    onSeeMoreClick: (SummaryStatisticsType) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
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

    val sections = mutableListOf<RethinkSummaryStatisticsSection>()
    sections += summarySection(
            title = stringResource(R.string.ssv_app_network_activity_heading),
            type = SummaryStatisticsType.MOST_CONNECTED_APPS,
            pagingItems = mostConnectedApps,
            accentColor = MaterialTheme.colorScheme.primary,
            viewModel = viewModel,
            refreshToken = uiState.timeCategory,
            onSeeMoreClick = onSeeMoreClick,
    )
    sections += summarySection(
            title = stringResource(R.string.ssv_app_blocked_heading),
            type = SummaryStatisticsType.MOST_BLOCKED_APPS,
            pagingItems = mostBlockedApps,
            accentColor = MaterialTheme.colorScheme.error,
            viewModel = viewModel,
            refreshToken = uiState.timeCategory,
            onSeeMoreClick = onSeeMoreClick,
    )
    sections += summarySection(
            title = stringResource(R.string.ssv_most_contacted_countries_heading),
            type = SummaryStatisticsType.MOST_CONTACTED_COUNTRIES,
            pagingItems = mostContactedCountries,
            accentColor = MaterialTheme.colorScheme.tertiary,
            viewModel = viewModel,
            refreshToken = uiState.timeCategory,
            onSeeMoreClick = onSeeMoreClick,
    )
    if (persistentState.downloadIpInfo && shouldShowOptionalSection(mostConnectedAsn)) {
        sections += summarySection(stringResource(R.string.most_contacted_asn), SummaryStatisticsType.MOST_CONNECTED_ASN, mostConnectedAsn, MaterialTheme.colorScheme.secondary, viewModel, uiState.timeCategory, onSeeMoreClick)
    }
    if (persistentState.downloadIpInfo && shouldShowOptionalSection(mostBlockedAsn)) {
        sections += summarySection(stringResource(R.string.most_blocked_asn), SummaryStatisticsType.MOST_BLOCKED_ASN, mostBlockedAsn, MaterialTheme.colorScheme.error, viewModel, uiState.timeCategory, onSeeMoreClick)
    }
    sections += summarySection(stringResource(R.string.ssv_most_contacted_domain_heading), SummaryStatisticsType.MOST_CONTACTED_DOMAINS, mostContactedDomains, MaterialTheme.colorScheme.secondary, viewModel, uiState.timeCategory, onSeeMoreClick)
    sections += summarySection(stringResource(R.string.ssv_most_blocked_domain_heading), SummaryStatisticsType.MOST_BLOCKED_DOMAINS, mostBlockedDomains, MaterialTheme.colorScheme.error, viewModel, uiState.timeCategory, onSeeMoreClick)
    sections += summarySection(stringResource(R.string.ssv_most_contacted_ips_heading), SummaryStatisticsType.MOST_CONTACTED_IPS, mostContactedIps, MaterialTheme.colorScheme.secondary, viewModel, uiState.timeCategory, onSeeMoreClick)
    sections += summarySection(stringResource(R.string.ssv_most_blocked_ips_heading), SummaryStatisticsType.MOST_BLOCKED_IPS, mostBlockedIps, MaterialTheme.colorScheme.error, viewModel, uiState.timeCategory, onSeeMoreClick)
    if (shouldShowOptionalSection(topActiveConns)) {
        sections += summarySection(stringResource(R.string.top_active_conns), SummaryStatisticsType.TOP_ACTIVE_CONNS, topActiveConns, MaterialTheme.colorScheme.primary, viewModel, uiState.timeCategory, onSeeMoreClick)
    }

    RethinkSummaryStatisticsScreen(
        overview = uiState.dataUsage.toRethinkUsageOverview(),
        selectedWindow = uiState.timeCategory.toRethinkWindow(),
        sections = sections,
        strings = RethinkSummaryStatisticsStrings(
            title = stringResource(R.string.title_statistics),
            overall = stringResource(R.string.lbl_overall),
            download = stringResource(R.string.lbl_download),
            upload = stringResource(R.string.lbl_upload),
            connections = stringResource(R.string.lbl_connections),
            oneHour = stringResource(R.string.time_window_one_hour_short),
            twentyFourHours = stringResource(R.string.time_window_twenty_four_hours_short),
            sevenDays = stringResource(R.string.time_window_seven_days_short),
            noLogs = stringResource(R.string.lbl_no_logs),
            seeMore = stringResource(R.string.ssv_see_more),
            seeLess = stringResource(R.string.ssv_see_less),
        ),
        onWindowSelected = { viewModel.timeCategoryChanged(it.toAndroidTimeCategory()) },
    )
}

@Composable
private fun summarySection(
    title: String,
    type: SummaryStatisticsType,
    pagingItems: LazyPagingItems<AppConnection>,
    accentColor: Color,
    viewModel: SummaryStatisticsViewModel,
    refreshToken: TimeCategory,
    onSeeMoreClick: (SummaryStatisticsType) -> Unit,
): RethinkSummaryStatisticsSection {
    val rows = pagingItems.itemSnapshotList.items.filterNotNull().map { item ->
        item.toRethinkSummaryRow(type, accentColor, viewModel, refreshToken)
    }
    return RethinkSummaryStatisticsSection(
        id = type.name,
        title = title,
        accentColor = accentColor,
        rows = rows,
        isLoading = pagingItems.loadState.refresh is LoadState.Loading,
        canSeeMore = pagingItems.itemCount > 5,
        onSeeMore = { onSeeMoreClick(type) },
    )
}

@Composable
private fun AppConnection.toRethinkSummaryRow(
    type: SummaryStatisticsType,
    accentColor: Color,
    viewModel: SummaryStatisticsViewModel,
    refreshToken: TimeCategory,
): RethinkSummaryStatisticsRow {
    val isCountry = type == SummaryStatisticsType.MOST_CONTACTED_COUNTRIES
    val countryName = if (isCountry) countryNameFromFlag(flag) else null
    val fallbackName = stringResource(R.string.network_log_app_name_unknown)
    val headline = if (isCountry) {
        countryName ?: appOrDnsName?.takeIf { it.isNotBlank() } ?: flag.ifBlank { fallbackName }
    } else {
        appOrDnsName?.takeIf { it.isNotBlank() } ?: ipAddress.ifBlank { fallbackName }
    }
    val metric = totalBytes?.takeIf { it > 0L }?.let(::formatBytes) ?: count.toString()
    val supporting = if (isCountry) null else buildString {
        append(stringResource(R.string.summary_connections_count, count))
        totalBytes?.takeIf { it > 0L }?.let { append(" · ").append(formatBytes(it)) }
    }
    val appIconPainter = if (type.supportsAppIcon()) rememberStatisticsAppIconPainter(uid) else null
    return RethinkSummaryStatisticsRow(
        id = "$type-$uid-$ipAddress-$flag-$count",
        headline = headline,
        supporting = supporting,
        metric = metric,
        countryFlag = flag.takeIf { isCountry && it.isNotBlank() },
        leadingContent = if (!isCountry) {
            {
                if (appIconPainter != null) {
                    Icon(appIconPainter, contentDescription = null, tint = Unspecified, modifier = Modifier.size(24.dp))
                } else {
                    Icon(painterResource(R.drawable.ic_app_info), contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                }
            }
        } else null,
        expandedContent = if (isCountry && flag.isNotBlank()) {
            { CountryBreakdown(flag, accentColor, viewModel, refreshToken) }
        } else null,
    )
}

private fun DataUsageSummary.toRethinkUsageOverview() = RethinkUsageOverview(
    download = formatBytes(totalDownload),
    upload = formatBytes(totalUpload),
    connections = connectionsCount.toString(),
)

private fun TimeCategory.toRethinkWindow() = when (this) {
    TimeCategory.ONE_HOUR -> RethinkStatisticsWindow.OneHour
    TimeCategory.TWENTY_FOUR_HOUR -> RethinkStatisticsWindow.TwentyFourHours
    TimeCategory.SEVEN_DAYS -> RethinkStatisticsWindow.SevenDays
}

private fun RethinkStatisticsWindow.toAndroidTimeCategory() = when (this) {
    RethinkStatisticsWindow.OneHour -> TimeCategory.ONE_HOUR
    RethinkStatisticsWindow.TwentyFourHours -> TimeCategory.TWENTY_FOUR_HOUR
    RethinkStatisticsWindow.SevenDays -> TimeCategory.SEVEN_DAYS
}

private fun shouldShowOptionalSection(pagingItems: LazyPagingItems<AppConnection>): Boolean =
    pagingItems.loadState.refresh is LoadState.Loading || pagingItems.itemCount > 0

/** Android owns the database query; the surrounding expandable row is shared. */
@Composable
private fun CountryBreakdown(
    flag: String,
    accentColor: Color,
    viewModel: SummaryStatisticsViewModel,
    refreshToken: TimeCategory,
) {
    val apps by produceState<List<AppConnection>>(initialValue = emptyList(), flag, refreshToken) {
        value = viewModel.getTopAppsForCountry(flag, limit = 4)
    }
    RethinkCountryBreakdown(
        title = stringResource(R.string.ssv_app_network_activity_heading),
        emptyMessage = stringResource(R.string.lbl_no_logs),
        accentColor = accentColor,
        apps = apps.map { app ->
            val painter = rememberStatisticsAppIconPainter(app.uid)
            RethinkCountryBreakdownItem(
                id = "${app.uid}-${app.appOrDnsName}-${app.count}",
                headline = app.appOrDnsName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.network_log_app_name_unknown),
                metric = app.count.toString(),
                leadingContent = {
                    if (painter != null) Icon(painter, null, tint = Unspecified, modifier = Modifier.size(20.dp))
                    else Icon(painterResource(R.drawable.ic_app_info), null, modifier = Modifier.size(18.dp))
                },
            )
        },
    )
}
