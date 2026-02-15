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
package com.celzero.bravedns.ui.compose.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.Dimensions
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
            ) {
                // Header with gradient background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .padding(bottom = Dimensions.spacingLg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimensions.spacing2xl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.app_name_small_case),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.alpha(Dimensions.Opacity.LOW),
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.height(Dimensions.spacingLg))

                        // Protection status pill
                        val statusColor = if (uiState.isProtectionFailing)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary

                        val statusContainerColor = if (uiState.isProtectionFailing)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)

                        Surface(
                            shape = CircleShape,
                            color = statusContainerColor,
                            modifier = Modifier.padding(horizontal = Dimensions.screenPaddingHorizontal)
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = Dimensions.spacing2xl,
                                    vertical = Dimensions.spacingMd
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = uiState.protectionStatus.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = statusColor,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }

                // Main Content
                Column(
                    modifier = Modifier
                        .padding(horizontal = Dimensions.screenPaddingHorizontal),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
                ) {
                    // DNS & Firewall Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
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
                                Spacer(modifier = Modifier.height(Dimensions.spacingSm))
                                StatItem(
                                    label = stringResource(R.string.lbl_connected),
                                    value = uiState.dnsConnectedName.ifEmpty {
                                        stringResource(R.string.lbl_inactive)
                                    }
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
                                Spacer(modifier = Modifier.height(Dimensions.spacingSm))
                                Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)) {
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
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
                    ) {
                        DashboardCard(
                            title = stringResource(R.string.lbl_proxy),
                            iconId = R.drawable.ic_vpn,
                            modifier = Modifier.weight(1f),
                            onClick = onProxyClick
                        ) {
                            Column {
                                Text(
                                    text = uiState.proxyStatus.ifEmpty {
                                        stringResource(R.string.lbl_inactive)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (uiState.proxyStatus.isNotEmpty())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                                Spacer(modifier = Modifier.height(Dimensions.spacingSm))
                                StatItem(
                                    label = stringResource(R.string.lbl_dns),
                                    value = uiState.dnsLogsCount.toString()
                                )
                            }
                        }
                    }

                    // Apps Card - Full width
                    DashboardCard(
                        title = stringResource(R.string.lbl_apps),
                        iconId = R.drawable.ic_app_info_accent,
                        onClick = onAppsClick
                    ) {
                        // Progress indicator for allowed/total
                        val appsProgress = remember(uiState.appsAllowed, uiState.appsTotal) {
                            if (uiState.appsTotal > 0)
                                uiState.appsAllowed.toFloat() / uiState.appsTotal.toFloat()
                            else 0f
                        }
                        LinearProgressIndicator(
                            progress = { appsProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Dimensions.spacingXs)
                                .clip(RoundedCornerShape(Dimensions.spacingXs)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )

                        Spacer(modifier = Modifier.height(Dimensions.spacingMd))

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
                                text = "/",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alpha(Dimensions.Opacity.LOW)
                            )
                            StatItem(
                                label = stringResource(R.string.lbl_total),
                                value = uiState.appsTotal.toString()
                            )
                        }

                        Spacer(modifier = Modifier.height(Dimensions.spacingLg))

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
                }

                // Start/Stop Button pinned at bottom
                StartStopButton(
                    isPlaying = uiState.isVpnActive,
                    onClick = onStartStopClick,
                    modifier = Modifier
                        .padding(horizontal = Dimensions.screenPaddingHorizontal)
                        .padding(
                            top = Dimensions.spacingXl,
                            bottom = Dimensions.spacing2xl
                        )
                )
            }
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
