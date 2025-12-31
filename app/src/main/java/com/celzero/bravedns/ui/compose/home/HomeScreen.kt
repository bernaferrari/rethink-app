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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.RethinkTheme

data class HomeScreenUiState(
    val isVpnActive: Boolean = false,
    val dnsLatency: String = "-- ms",
    val dnsConnectedName: String = "Loading...",
    val firewallUniversalRules: Int = 0,
    val firewallIpRules: Int = 0,
    val firewallDomainRules: Int = 0,
    val proxyStatus: String = "Inactive",
    val networkLogsCount: Long = 0,
    val dnsLogsCount: Long = 0,
    val appsAllowed: Int = 0,
    val appsBlocked: Int = 0,
    val appsTotal: Int = 0,
    val appsBypassed: Int = 0,
    val appsIsolated: Int = 0,
    val appsExcluded: Int = 0,
    val protectionStatus: String = "Protected",
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
                    text = "rethink",
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
                    title = "DNS",
                    iconId = R.drawable.dns_home_screen,
                    modifier = Modifier.weight(1f),
                    onClick = onDnsClick
                ) {
                    Column {
                        StatItem(label = "Latency", value = uiState.dnsLatency)
                        Spacer(modifier = Modifier.height(8.dp))
                        StatItem(label = "Connected", value = uiState.dnsConnectedName)
                    }
                }
                
                DashboardCard(
                    title = "Firewall",
                    iconId = R.drawable.firewall_home_screen,
                    modifier = Modifier.weight(1f),
                    onClick = onFirewallClick
                ) {
                     Column {
                        StatItem(label = "Universal Rules", value = uiState.firewallUniversalRules.toString())
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                             StatItem(label = "IP", value = uiState.firewallIpRules.toString(), modifier = Modifier.weight(1f))
                             StatItem(label = "Domain", value = uiState.firewallDomainRules.toString(), modifier = Modifier.weight(1f))
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
                    title = "Proxy",
                    iconId = R.drawable.ic_vpn,
                    modifier = Modifier.weight(1f),
                    onClick = onProxyClick
                ) {
                    Text(
                        text = uiState.proxyStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                DashboardCard(
                    title = "Logs",
                    iconId = R.drawable.ic_logs_accent,
                    modifier = Modifier.weight(1f),
                    onClick = onLogsClick
                ) {
                   Column {
                        StatItem(label = "Network", value = uiState.networkLogsCount.toString())
                        Spacer(modifier = Modifier.height(8.dp))
                        StatItem(label = "DNS", value = uiState.dnsLogsCount.toString())
                    }
                }
            }
            
            // Apps Card
            DashboardCard(
                title = "Apps",
                iconId = R.drawable.ic_app_info_accent,
                onClick = onAppsClick
            ) {
                Row(
                   modifier = Modifier.fillMaxWidth(),
                   horizontalArrangement = Arrangement.SpaceBetween,
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(label = "Allowed", value = uiState.appsAllowed.toString(), isHighlighted = true)
                    Text("/", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatItem(label = "Total", value = uiState.appsTotal.toString())
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                     StatItem(label = "Blocked", value = uiState.appsBlocked.toString())
                     StatItem(label = "Bypassed", value = uiState.appsBypassed.toString())
                     StatItem(label = "Isolated", value = uiState.appsIsolated.toString())
                     StatItem(label = "Excluded", value = uiState.appsExcluded.toString())
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
