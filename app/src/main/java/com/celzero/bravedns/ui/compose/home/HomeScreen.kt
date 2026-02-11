package com.celzero.bravedns.ui.compose.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.RethinkTheme

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
    val isProtectionFailing: Boolean = false
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
    onSponsorClick: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name_small_case),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { onSponsorClick() }
                )
            }

            // Protection Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = uiState.protectionStatus,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (uiState.isProtectionFailing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            // DNS & Firewall Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    title = stringResource(R.string.lbl_dns),
                    iconId = R.drawable.dns_home_screen,
                    modifier = Modifier.weight(1f),
                    onClick = onDnsClick
                ) {
                    Column {
                        StatItem(
                            label = stringResource(R.string.dns_detail_latency),
                            value = uiState.dnsLatency
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        StatItem(
                            label = stringResource(R.string.lbl_connected),
                            value = uiState.dnsConnectedName
                        )
                    }
                }
                
                DashboardCard(
                    title = stringResource(R.string.lbl_firewall),
                    iconId = R.drawable.firewall_home_screen,
                    modifier = Modifier.weight(1f),
                    onClick = onFirewallClick
                ) {
                     Column {
                        StatItem(
                            label = stringResource(R.string.lbl_universal_rules),
                            value = uiState.firewallUniversalRules.toString()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                             StatItem(
                                 label = stringResource(R.string.lbl_ip),
                                 value = uiState.firewallIpRules.toString(),
                                 modifier = Modifier.weight(1f)
                             )
                             StatItem(
                                 label = stringResource(R.string.lbl_domain),
                                 value = uiState.firewallDomainRules.toString(),
                                 modifier = Modifier.weight(1f)
                             )
                        }
                    }
                }
            }

            // Proxy & Logs Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    title = stringResource(R.string.lbl_proxy),
                    iconId = R.drawable.ic_vpn,
                    modifier = Modifier.weight(1f),
                    onClick = onProxyClick
                ) {
                    Text(
                        text = uiState.proxyStatus.ifEmpty { stringResource(R.string.lbl_inactive) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                DashboardCard(
                    title = stringResource(R.string.lbl_logs),
                    iconId = R.drawable.ic_logs_accent,
                    modifier = Modifier.weight(1f),
                    onClick = onLogsClick
                ) {
                   Column {
                        StatItem(
                            label = stringResource(R.string.lbl_network),
                            value = uiState.networkLogsCount.toString()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        StatItem(
                            label = stringResource(R.string.lbl_dns),
                            value = uiState.dnsLogsCount.toString()
                        )
                    }
                }
            }
            
            // Apps Card
            DashboardCard(
                title = stringResource(R.string.lbl_apps),
                iconId = R.drawable.ic_app_info_accent,
                onClick = onAppsClick
            ) {
                Row(
                   modifier = Modifier.fillMaxWidth(),
                   horizontalArrangement = Arrangement.SpaceBetween,
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(
                        label = stringResource(R.string.lbl_allowed),
                        value = uiState.appsAllowed.toString(),
                        isHighlighted = true
                    )
                    Text(
                        "/",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatItem(
                        label = stringResource(R.string.lbl_total),
                        value = uiState.appsTotal.toString()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                     StatItem(
                         label = stringResource(R.string.lbl_blocked),
                         value = uiState.appsBlocked.toString()
                     )
                     StatItem(
                         label = stringResource(R.string.lbl_bypassed),
                         value = uiState.appsBypassed.toString()
                     )
                     StatItem(
                         label = stringResource(R.string.lbl_isolated),
                         value = uiState.appsIsolated.toString()
                     )
                     StatItem(
                         label = stringResource(R.string.lbl_excluded),
                         value = uiState.appsExcluded.toString()
                     )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            
            // Start/Stop Button
            StartStopButton(
                isPlaying = uiState.isVpnActive,
                onClick = onStartStopClick,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    RethinkTheme {
        HomeScreen(
            uiState = HomeScreenUiState(
                isVpnActive = true,
                dnsLatency = "24ms",
                dnsConnectedName = "Cloudflare",
                firewallUniversalRules = 12,
                appsTotal = 120,
                appsAllowed = 115,
                appsBlocked = 5
            ),
            onStartStopClick = {},
            onDnsClick = {},
            onFirewallClick = {},
            onProxyClick = {},
            onLogsClick = {},
            onAppsClick = {},
            onSponsorClick = {}
        )
    }
}
