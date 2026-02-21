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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.ui.compose.theme.SectionHeader

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

private data class HomeStatusVisuals(
    val icon: ImageVector,
    val accentColor: Color
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RethinkTopBar(
                title = stringResource(R.string.txt_home),
                actions = {
                    IconButton(onClick = onSponsorClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_heart_accent),
                            contentDescription = stringResource(R.string.about_sponsor_link_text)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val dnsSummary =
            if (uiState.dnsConnectedName.isNotBlank()) {
                "${uiState.dnsConnectedName} • ${uiState.dnsLatency}"
            } else {
                stringResource(R.string.lbl_inactive)
            }

        val firewallSummary =
            "${stringResource(R.string.lbl_universal_rules)} ${uiState.firewallUniversalRules} • " +
                "${stringResource(R.string.lbl_ip)} ${uiState.firewallIpRules} • " +
                "${stringResource(R.string.lbl_domain)} ${uiState.firewallDomainRules}"

        val proxySummary = uiState.proxyStatus.ifEmpty { stringResource(R.string.lbl_inactive) }

        val logsSummary =
            "${stringResource(R.string.lbl_network)} ${uiState.networkLogsCount} • " +
                "${stringResource(R.string.lbl_dns)} ${uiState.dnsLogsCount}"

        val appsSummary =
            "${stringResource(R.string.lbl_allowed)} ${uiState.appsAllowed}/${uiState.appsTotal} • " +
                "${stringResource(R.string.lbl_blocked)} ${uiState.appsBlocked}"

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues),
            contentPadding =
                PaddingValues(
                    start = Dimensions.screenPaddingHorizontal,
                    end = Dimensions.screenPaddingHorizontal,
                    bottom = Dimensions.spacing3xl
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            item {
                HomeProtectionCard(
                    uiState = uiState,
                    onStartStopClick = onStartStopClick
                )
            }

            item {
                SectionHeader(title = stringResource(R.string.lbl_status))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(R.string.lbl_dns),
                        supporting = dnsSummary,
                        leadingIconPainter = painterResource(id = R.drawable.dns_home_screen),
                        onClick = onDnsClick
                    )
                    RethinkListItem(
                        headline = stringResource(R.string.lbl_firewall),
                        supporting = firewallSummary,
                        leadingIconPainter = painterResource(id = R.drawable.firewall_home_screen),
                        onClick = onFirewallClick
                    )
                    RethinkListItem(
                        headline = stringResource(R.string.lbl_proxy),
                        supporting = proxySummary,
                        leadingIconPainter = painterResource(id = R.drawable.ic_vpn),
                        onClick = onProxyClick
                    )
                    RethinkListItem(
                        headline = stringResource(R.string.lbl_logs),
                        supporting = logsSummary,
                        leadingIconPainter = painterResource(id = R.drawable.ic_logs_accent),
                        onClick = onLogsClick
                    )
                    RethinkListItem(
                        headline = stringResource(R.string.lbl_apps),
                        supporting = appsSummary,
                        leadingIconPainter = painterResource(id = R.drawable.ic_app_info_accent),
                        onClick = onAppsClick,
                        showDivider = false
                    )
                }
            }

            item {
                AppsHealthCard(uiState)
            }
        }
    }
}

@Composable
private fun HomeProtectionCard(
    uiState: HomeScreenUiState,
    onStartStopClick: () -> Unit
) {
    val visuals =
        when {
            uiState.isProtectionFailing -> HomeStatusVisuals(Icons.Rounded.WarningAmber, MaterialTheme.colorScheme.error)
            uiState.isVpnActive -> HomeStatusVisuals(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary)
            else -> HomeStatusVisuals(Icons.Rounded.WarningAmber, MaterialTheme.colorScheme.onSurfaceVariant)
        }

    Surface(
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = visuals.accentColor.copy(alpha = 0.12f)
                ) {
                    Icon(
                        imageVector = visuals.icon,
                        contentDescription = null,
                        tint = visuals.accentColor,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lbl_status),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiState.protectionStatus,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Button(
                    onClick = onStartStopClick,
                    shape = RoundedCornerShape(14.dp),
                    colors =
                        if (uiState.isVpnActive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        },
                    modifier = Modifier.height(42.dp)
                ) {
                    Text(
                        text = if (uiState.isVpnActive) stringResource(R.string.lbl_stop) else stringResource(R.string.lbl_start),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
                thickness = 1.dp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeStatusMetric(
                    label = stringResource(R.string.dns_detail_latency),
                    value = uiState.dnsLatency,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HomeStatusMetric(
                    label = stringResource(R.string.lbl_network),
                    value = uiState.networkLogsCount.toString(),
                    color = MaterialTheme.colorScheme.onSurface
                )
                HomeStatusMetric(
                    label = stringResource(R.string.lbl_blocked),
                    value = uiState.appsBlocked.toString(),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun AppsHealthCard(uiState: HomeScreenUiState) {
    val appsProgress =
        remember(uiState.appsAllowed, uiState.appsTotal) {
            if (uiState.appsTotal > 0) {
                uiState.appsAllowed.toFloat() / uiState.appsTotal.toFloat()
            } else {
                0f
            }
        }

    Surface(
        shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.spacingXl),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
        ) {
            Text(
                text = stringResource(R.string.lbl_apps),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            LinearProgressIndicator(
                progress = { appsProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeStatusMetric(
                    label = stringResource(R.string.lbl_allowed),
                    value = uiState.appsAllowed.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                HomeStatusMetric(
                    label = stringResource(R.string.lbl_total),
                    value = uiState.appsTotal.toString(),
                    color = MaterialTheme.colorScheme.onSurface
                )
                HomeStatusMetric(
                    label = stringResource(R.string.lbl_bypassed),
                    value = uiState.appsBypassed.toString(),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun HomeStatusMetric(label: String, value: String, color: Color) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    RethinkTheme {
        HomeScreen(
            uiState =
                HomeScreenUiState(
                    isVpnActive = true,
                    dnsLatency = "24ms",
                    dnsConnectedName = "Cloudflare",
                    firewallUniversalRules = 12,
                    appsTotal = 120,
                    appsAllowed = 115,
                    appsBlocked = 5,
                    protectionStatus = "Protected"
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
