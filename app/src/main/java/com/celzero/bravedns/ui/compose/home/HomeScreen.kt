/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Android adapter for the common Home renderer. Keep platform resources and live VPN state here;
 * the actual Compose screen lives in :shared/commonMain.
 */
package com.celzero.bravedns.ui.compose.home

import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.celzero.bravedns.R

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
    onDnsClick: () -> Unit,
    onFirewallClick: () -> Unit,
    onProxyClick: () -> Unit,
    onLogsClick: () -> Unit,
    onAppsClick: () -> Unit,
    onSponsorClick: () -> Unit,
) {
    RethinkHomeScreen(
        uiState = uiState.toCommonUiState(),
        strings = androidHomeStrings(),
        onStartStopClick = onStartStopClick,
        onDnsClick = onDnsClick,
        onFirewallClick = onFirewallClick,
        onProxyClick = onProxyClick,
        onLogsClick = onLogsClick,
        onAppsClick = onAppsClick,
        icons = androidHomeIcons(),
    )
}

@Composable
private fun androidHomeIcons() = RethinkHomeIcons(
    dns = { Icon(painterResource(R.drawable.dns_home_screen), null) },
    firewall = { Icon(painterResource(R.drawable.firewall_home_screen), null) },
    proxy = { Icon(painterResource(R.drawable.ic_vpn), null) },
    logs = { Icon(painterResource(R.drawable.ic_logs_accent), null) },
    apps = { Icon(painterResource(R.drawable.ic_app_info_accent), null) },
)

private fun HomeScreenUiState.toCommonUiState() = RethinkHomeUiState(
    isVpnActive = isVpnActive,
    dnsLatency = dnsLatency,
    dnsConnectedName = dnsConnectedName,
    firewallUniversalRules = firewallUniversalRules,
    firewallIpRules = firewallIpRules,
    firewallDomainRules = firewallDomainRules,
    proxyStatus = proxyStatus,
    networkLogsCount = networkLogsCount,
    dnsLogsCount = dnsLogsCount,
    appsAllowed = appsAllowed,
    appsBlocked = appsBlocked,
    appsTotal = appsTotal,
    appsBypassed = appsBypassed,
    protectionStatus = protectionStatus,
    isProtectionFailing = isProtectionFailing,
)

@Composable
private fun androidHomeStrings() = RethinkHomeStrings(
    home = stringResource(R.string.txt_home),
    status = stringResource(R.string.lbl_status),
    protection = "Protection",
    protected = "Protected",
    notActive = "Not active",
    inactive = stringResource(R.string.lbl_inactive),
    latency = "Latency",
    dns = stringResource(R.string.lbl_dns),
    firewall = stringResource(R.string.lbl_firewall),
    proxy = stringResource(R.string.lbl_proxy),
    logs = stringResource(R.string.lbl_logs),
    network = stringResource(R.string.lbl_network),
    blocked = stringResource(R.string.lbl_blocked),
    apps = stringResource(R.string.lbl_apps),
    allowed = stringResource(R.string.lbl_allowed),
    bypassed = stringResource(R.string.lbl_bypassed),
    universalRules = stringResource(R.string.lbl_universal_rules),
)
