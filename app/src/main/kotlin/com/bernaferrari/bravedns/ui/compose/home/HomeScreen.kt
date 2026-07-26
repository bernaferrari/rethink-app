/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Android adapter for the common Home renderer. Keep platform resources and live VPN state here;
 * the actual Compose screen lives in :shared/commonMain.
 */
package com.bernaferrari.bravedns.ui.compose.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R

data class HomeScreenUiState(
    val isVpnActive: Boolean = false,
    val dnsLatency: String = "-- ms",
    val dnsConnectedName: String = "",
    val firewallUniversalRules: Int = 0,
    val firewallIpRules: Int = 0,
    val firewallDomainRules: Int = 0,
    val proxyStatus: String = "",
    val networkLogsCount: Long = 0,
    val dnsLogsCount: Long = 0,
    val appsAllowed: Int = 0,
    val appsBlocked: Int = 0,
    val appsTotal: Int = 0,
    val appsBypassed: Int = 0,
    val appsIsolated: Int = 0,
    val appsExcluded: Int = 0,
    val protectionStatus: String = "",
    val isProtectionFailing: Boolean = false,
)

@Composable
fun HomeScreen(
    uiState: HomeScreenUiState,
    onStartStopClick: () -> Unit,
) {
    RethinkHomeScreen(
        uiState = uiState.toCommonUiState(),
        strings = androidHomeStrings(),
        onStartStopClick = onStartStopClick,
    )
}

private fun HomeScreenUiState.toCommonUiState() = RethinkHomeUiState(
    isVpnActive = isVpnActive,
    networkLogsCount = networkLogsCount,
    dnsLogsCount = dnsLogsCount,
    protectionStatus = protectionStatus,
    isProtectionFailing = isProtectionFailing,
)

@Composable
private fun androidHomeStrings() = RethinkHomeStrings(
    productName = stringResource(R.string.app_name),
    protection = "Protection",
    protected = "Protected",
    notActive = "Not active",
    start = stringResource(R.string.lbl_start),
    stop = stringResource(R.string.lbl_stop),
    protectedSubtitle = stringResource(R.string.home_protected_subtitle),
    inactiveSubtitle = stringResource(R.string.home_inactive_subtitle),
    failingSubtitle = stringResource(R.string.home_failing_subtitle),
    activity = stringResource(R.string.home_activity_title),
    activitySubtitle = stringResource(R.string.home_activity_subtitle),
    connections = stringResource(R.string.home_connections),
    dnsQueries = stringResource(R.string.home_dns_queries),
)
