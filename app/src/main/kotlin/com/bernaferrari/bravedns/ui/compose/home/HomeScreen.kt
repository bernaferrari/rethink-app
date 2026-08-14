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

enum class HomeProtectionStatus {
    Protected,
    UltraSecure,
    Failing,
    Waiting,
    Exposed,
    NoNetwork,
}

data class HomeScreenUiState(
    val isVpnActive: Boolean = false,
    val networkLogsCount: Long = 0,
    val dnsLogsCount: Long = 0,
    val protectionStatus: HomeProtectionStatus? = null,
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

@Composable
private fun HomeScreenUiState.toCommonUiState() = RethinkHomeUiState(
    isVpnActive = isVpnActive,
    networkLogsCount = networkLogsCount,
    dnsLogsCount = dnsLogsCount,
    protectionStatus = protectionStatusLabel(protectionStatus),
    isProtectionFailing = isProtectionFailing,
)

@Composable
private fun protectionStatusLabel(status: HomeProtectionStatus?): String {
    if (status == null) return ""
    return stringResource(
        when (status) {
            HomeProtectionStatus.Protected -> R.string.home_protected
            HomeProtectionStatus.UltraSecure -> R.string.lbl_ultra_secure
            HomeProtectionStatus.Failing -> R.string.status_failing
            HomeProtectionStatus.Waiting -> R.string.status_waiting
            HomeProtectionStatus.Exposed -> R.string.status_exposed
            HomeProtectionStatus.NoNetwork -> R.string.status_no_network
        }
    )
}

@Composable
private fun androidHomeStrings() = RethinkHomeStrings(
    productName = stringResource(R.string.app_name),
    protected = stringResource(R.string.home_protected),
    notActive = stringResource(R.string.home_not_active),
    start = stringResource(R.string.lbl_start),
    stop = stringResource(R.string.lbl_stop),
    protectedSubtitle = stringResource(R.string.home_protected_subtitle),
    inactiveSubtitle = stringResource(R.string.home_inactive_subtitle),
    failingSubtitle = stringResource(R.string.home_failing_subtitle),
    connections = stringResource(R.string.home_connections),
    dnsQueries = stringResource(R.string.home_dns_queries),
    networkProtection = stringResource(R.string.home_network_protection),
    live = stringResource(R.string.home_live),
    offline = stringResource(R.string.home_offline),
)
