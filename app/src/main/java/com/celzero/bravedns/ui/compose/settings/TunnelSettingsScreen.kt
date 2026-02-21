/*
 * Copyright 2023 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkScreenHeader
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.ui.dialog.CustomLanIpSheet
import com.celzero.bravedns.ui.dialog.NetworkReachabilitySheet
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.InternetProtocol
import com.celzero.bravedns.util.NewSettingsManager
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private const val POLICY_AUTO = 0
private const val POLICY_SENSITIVE = 1
private const val POLICY_RELAXED = 2
private const val POLICY_FIXED = 3
private const val IP_DIALOG_POS_IPV4 = 0
private const val IP_DIALOG_POS_IPV6 = 1
private const val IP_DIALOG_POS_ALWAYS_V46 = 2
private const val IP_DIALOG_POS_V46 = 3
private const val ALPHA_ENABLED = 1f
private const val ALPHA_DISABLED = 0.5f

private data class NetworkPolicyOption(val title: String, val description: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelSettingsScreen(
    persistentState: PersistentState,
    appConfig: AppConfig,
    eventLogger: EventLogger,
    onOpenVpnProfile: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLockdown by remember { mutableStateOf(VpnController.isVpnLockdown()) }
    var allowBypass by remember { mutableStateOf(persistentState.allowBypass) }
    var allowBypassLoading by remember { mutableStateOf(false) }
    var useMultipleNetworks by remember { mutableStateOf(persistentState.useMultipleNetworks) }
    var routeLan by remember { mutableStateOf(persistentState.privateIps) }
    var excludeApps by remember { mutableStateOf(!persistentState.excludeAppsInProxy) }
    var stallNoNetwork by remember { mutableStateOf(persistentState.stallOnNoNetwork) }
    var protocolTranslation by remember { mutableStateOf(persistentState.protocolTranslationType) }
    var meteredOnlyMobile by remember { mutableStateOf(persistentState.treatOnlyMobileNetworkAsMetered) }
    var listenPortFixed by remember { mutableStateOf(!persistentState.randomizeListenPort) }
    var wgLockdown by remember { mutableStateOf(persistentState.wgGlobalLockdown) }
    var endpointIndependence by remember { mutableStateOf(persistentState.endpointIndependence) }
    var allowIncoming by remember { mutableStateOf(persistentState.nwEngExperimentalFeatures) }
    var tcpKeepAlive by remember { mutableStateOf(persistentState.tcpKeepAlive) }
    var useMaxMtu by remember { mutableStateOf(persistentState.useMaxMtu) }
    var tunnelMetered by remember { mutableStateOf(persistentState.setVpnBuilderToMetered) }
    var dialTimeoutMin by remember { mutableIntStateOf(persistentState.dialTimeoutSec / SECONDS_PER_MINUTE) }
    var internetProtocol by remember { mutableIntStateOf(persistentState.internetProtocolType) }
    var vpnPolicy by remember { mutableIntStateOf(persistentState.vpnBuilderPolicy) }
    var connectivityChecks by remember { mutableStateOf(persistentState.connectivityChecks) }
    var showCustomLanIpSheet by remember { mutableStateOf(false) }
    var showReachabilitySheet by remember { mutableStateOf(false) }
    var showDefaultDnsDialog by remember { mutableStateOf(false) }
    var showVpnPolicyDialog by remember { mutableStateOf(false) }
    var showIpDialog by remember { mutableStateOf(false) }
    var showConnectivityChecksDialog by remember { mutableStateOf(false) }

    val canModify = !isLockdown
    val showPtrans = internetProtocol == InternetProtocol.IPv6.id
    val showConnectivityChecksOption = internetProtocol == InternetProtocol.IPv46.id
    val showPingIps = showConnectivityChecksOption && connectivityChecks

    fun logEvent(msg: String, details: String) {
        eventLogger.log(EventType.TUN_ESTABLISHED, Severity.LOW, msg, EventSource.UI, false, details)
    }

    fun formatTimeShort(totalSeconds: Int): String {
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (seconds > 0) parts.add("${seconds}s")
        return if (parts.isEmpty()) context.resources.getString(R.string.lbl_disabled) else parts.joinToString(" ")
    }

    val ipDesc = when (internetProtocol) {
        InternetProtocol.IPv4.id -> stringResource(R.string.settings_ip_text_ipv4)
        InternetProtocol.IPv6.id -> stringResource(R.string.settings_ip_text_ipv6)
        InternetProtocol.IPv46.id -> stringResource(R.string.settings_ip_text_ipv46)
        InternetProtocol.ALWAYSv46.id -> stringResource(R.string.settings_ip_text_ipv4) + " & " + stringResource(R.string.settings_ip_text_ipv6)
        else -> stringResource(R.string.settings_ip_text_ipv4)
    }

    val vpnPolicyDesc = when (vpnPolicy) {
        POLICY_AUTO -> stringResource(R.string.settings_ip_text_ipv46)
        POLICY_SENSITIVE -> stringResource(R.string.vpn_policy_sensitive)
        POLICY_RELAXED -> stringResource(R.string.vpn_policy_relaxed)
        POLICY_FIXED -> stringResource(R.string.vpn_policy_fixed)
        else -> stringResource(R.string.settings_ip_text_ipv46)
    }

    val dialTimeoutDesc = formatTimeShort(dialTimeoutMin * SECONDS_PER_MINUTE)

    // Default DNS Dialog
    if (showDefaultDnsDialog) {
        DefaultDnsDialog(
            persistentState = persistentState,
            onDismiss = { showDefaultDnsDialog = false },
            onConfirm = { logEvent("default dns changed", "Default DNS changed") }
        )
    }

    // VPN Policy Dialog
    if (showVpnPolicyDialog) {
        VpnPolicyDialog(
            persistentState = persistentState,
            context = context,
            onDismiss = { showVpnPolicyDialog = false },
            onConfirm = { selectedIndex ->
                if (selectedIndex == POLICY_FIXED) {
                    persistentState.enableStabilityDependentSettings(context)
                    persistentState.useMaxMtu = true
                    useMaxMtu = true
                    persistentState.internetProtocolType = InternetProtocol.ALWAYSv46.id
                    internetProtocol = InternetProtocol.ALWAYSv46.id
                }
                persistentState.vpnBuilderPolicy = selectedIndex
                vpnPolicy = selectedIndex
                logEvent("vpn policy changed", "VPN builder network policy changed to: $selectedIndex")
            }
        )
    }

    // IP Dialog
    if (showIpDialog) {
        IpProtocolDialog(
            persistentState = persistentState,
            context = context,
            onDismiss = { showIpDialog = false },
            onConfirm = { selectedProtocol ->
                internetProtocol = selectedProtocol
                logEvent("ip protocol changed", "Internet protocol changed to: $selectedProtocol")
            }
        )
    }

    // Connectivity Checks Dialog
    if (showConnectivityChecksDialog) {
        ConnectivityChecksDialog(
            persistentState = persistentState,
            onDismiss = { showConnectivityChecksDialog = false },
            onConfirm = { enabled ->
                connectivityChecks = enabled
                logEvent("connectivity checks", "Connectivity checks changed")
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title = stringResource(R.string.lbl_network),
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding =
                PaddingValues(
                    start = Dimensions.screenPaddingHorizontal,
                    end = Dimensions.screenPaddingHorizontal,
                    bottom = Dimensions.spacing3xl
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            item {
                TunnelOverviewCard(
                    vpnPolicyDesc = vpnPolicyDesc,
                    ipDesc = ipDesc,
                    dialTimeoutDesc = dialTimeoutDesc
                )
            }

            if (isLockdown) {
                item {
                    RethinkListGroup {
                        RethinkListItem(
                            headline = stringResource(R.string.settings_lock_down_mode_desc),
                            leadingIconPainter = painterResource(id = R.drawable.ic_firewall_lockdown_on),
                            position = CardPosition.Single,
                            onClick = { onOpenVpnProfile() }
                        )
                    }
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.lbl_network))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(R.string.settings_allow_bypass_heading),
                        supporting = stringResource(R.string.settings_allow_bypass_desc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_settings),
                        position = CardPosition.First,
                        onClick = {
                            if (Utilities.isPlayStoreFlavour()) return@RethinkListItem
                            val checked = !allowBypass
                            allowBypass = checked
                            persistentState.allowBypass = checked
                            allowBypassLoading = true
                            scope.launch {
                                delay(1000L)
                                allowBypassLoading = false
                            }
                            logEvent("allow bypass", "Allow bypass VPN: $checked")
                        },
                        trailing = {
                            Switch(
                                checked = allowBypass,
                                onCheckedChange = { checked ->
                                    if (Utilities.isPlayStoreFlavour()) return@Switch
                                    allowBypass = checked
                                    persistentState.allowBypass = checked
                                    allowBypassLoading = true
                                    scope.launch {
                                        delay(1000L)
                                        allowBypassLoading = false
                                    }
                                    logEvent("allow bypass", "Allow bypass VPN: $checked")
                                },
                                enabled = canModify && !Utilities.isPlayStoreFlavour()
                            )
                        }
                    )

                    RethinkListItem(
                        headline = stringResource(R.string.fail_open_network_title),
                        supporting = stringResource(R.string.fail_open_network_desc),
                        leadingIcon = Icons.Filled.Tune,
                        position = CardPosition.Middle,
                        onClick = {
                            val checked = !stallNoNetwork
                            stallNoNetwork = checked
                            persistentState.stallOnNoNetwork = checked
                            logEvent("stall on no network", "Stall on no network: $checked")
                        },
                        trailing = {
                            Switch(
                                checked = stallNoNetwork,
                                onCheckedChange = { checked ->
                                    stallNoNetwork = checked
                                    persistentState.stallOnNoNetwork = checked
                                    logEvent("stall on no network", "Stall on no network: $checked")
                                }
                            )
                        }
                    )

                    RethinkListItem(
                        headline = stringResource(R.string.settings_allow_lan_heading),
                        supporting = stringResource(R.string.settings_allow_lan_desc),
                        leadingIcon = Icons.Filled.Tune,
                        position = CardPosition.Middle,
                        onClick = {
                            if (canModify) {
                                val checked = !routeLan
                                routeLan = checked
                                persistentState.privateIps = checked
                                if (checked) persistentState.enableStabilityDependentSettings(context)
                                logEvent("route lan traffic", "Route LAN traffic: $checked")
                            }
                        },
                        trailing = {
                            Switch(
                                checked = routeLan,
                                onCheckedChange = { checked ->
                                    routeLan = checked
                                    persistentState.privateIps = checked
                                    if (checked) persistentState.enableStabilityDependentSettings(context)
                                    logEvent("route lan traffic", "Route LAN traffic: $checked")
                                },
                                enabled = canModify
                            )
                        }
                    )

                    RethinkListItem(
                        headline = stringResource(R.string.settings_network_all_networks),
                        supporting = stringResource(R.string.settings_network_all_networks_desc),
                        leadingIcon = Icons.Filled.Tune,
                        position = CardPosition.Middle,
                        onClick = {
                            if (canModify) {
                                val checked = !useMultipleNetworks
                                useMultipleNetworks = checked
                                persistentState.useMultipleNetworks = checked
                                if (checked) persistentState.enableStabilityDependentSettings(context)
                                if (!checked && persistentState.routeRethinkInRethink) {
                                    persistentState.routeRethinkInRethink = false
                                }
                                logEvent("use all networks", "Use all networks for VPN: $checked")
                            }
                        },
                        trailing = {
                            Switch(
                                checked = useMultipleNetworks,
                                onCheckedChange = { checked ->
                                    useMultipleNetworks = checked
                                    persistentState.useMultipleNetworks = checked
                                    if (checked) persistentState.enableStabilityDependentSettings(context)
                                    if (!checked && persistentState.routeRethinkInRethink) {
                                        persistentState.routeRethinkInRethink = false
                                    }
                                    logEvent("use all networks", "Use all networks for VPN: $checked")
                                },
                                enabled = canModify
                            )
                        }
                    )

                    RethinkListItem(
                        headline = stringResource(R.string.settings_exclude_apps_in_proxy),
                        supporting = stringResource(R.string.settings_exclude_apps_in_proxy_desc),
                        leadingIcon = Icons.Filled.Tune,
                        position = CardPosition.Middle,
                        onClick = {
                            if (canModify) {
                                val checked = !excludeApps
                                excludeApps = checked
                                persistentState.excludeAppsInProxy = !checked
                                logEvent("exclude apps in proxy", "Exclude apps in proxy: ${!checked}")
                            }
                        },
                        trailing = {
                            Switch(
                                checked = excludeApps,
                                onCheckedChange = { checked ->
                                    excludeApps = checked
                                    persistentState.excludeAppsInProxy = !checked
                                    logEvent("exclude apps in proxy", "Exclude apps in proxy: ${!checked}")
                                },
                                enabled = canModify
                            )
                        }
                    )

                    RethinkListItem(
                        headline = stringResource(R.string.settings_protocol_translation),
                        supporting = stringResource(R.string.settings_protocol_translation_desc),
                        leadingIcon = Icons.Filled.Tune,
                        position = CardPosition.Last,
                        onClick = {
                            if (showPtrans) {
                                val checked = !protocolTranslation
                                if (appConfig.getBraveMode().isDnsActive()) {
                                    protocolTranslation = checked
                                    persistentState.protocolTranslationType = checked
                                } else {
                                    protocolTranslation = false
                                    showToastUiCentered(
                                        context,
                                        context.resources.getString(R.string.settings_protocol_translation_dns_inactive),
                                        Toast.LENGTH_SHORT
                                    )
                                }
                                logEvent("protocol translation", "Protocol translation: $checked")
                            }
                        },
                        trailing = {
                            Switch(
                                checked = protocolTranslation,
                                onCheckedChange = { checked ->
                                    if (appConfig.getBraveMode().isDnsActive()) {
                                        protocolTranslation = checked
                                        persistentState.protocolTranslationType = checked
                                    } else {
                                        protocolTranslation = false
                                        showToastUiCentered(
                                            context,
                                            context.resources.getString(R.string.settings_protocol_translation_dns_inactive),
                                            Toast.LENGTH_SHORT
                                        )
                                    }
                                    logEvent("protocol translation", "Protocol translation: $checked")
                                },
                                enabled = showPtrans
                            )
                        }
                    )
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.lbl_advanced))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(R.string.settings_default_dns_heading),
                        supporting = stringResource(R.string.settings_default_dns_desc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_settings),
                        position = CardPosition.First,
                        onClick = { showDefaultDnsDialog = true }
                    )

                    RethinkListItem(
                        headline = stringResource(R.string.vpn_policy_title),
                        supporting = vpnPolicyDesc,
                        leadingIconPainter = painterResource(id = R.drawable.ic_settings),
                        position = CardPosition.Middle,
                        onClick = { showVpnPolicyDialog = true }
                    )

                    RethinkListItem(
                        headline = stringResource(R.string.settings_ip_dialog_title),
                        supporting = stringResource(R.string.settings_selected_ip_desc, ipDesc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_settings),
                        position = CardPosition.Middle,
                        onClick = { if (vpnPolicy != POLICY_FIXED) showIpDialog = true }
                    )

                    RethinkListItem(
                        headline = stringResource(R.string.settings_connectivity_checks),
                        supporting = stringResource(R.string.settings_connectivity_checks_desc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_settings),
                        position = CardPosition.Middle,
                        onClick = { if (showConnectivityChecksOption) showConnectivityChecksDialog = true }
                    )

                    if (showPingIps) {
                        RethinkListItem(
                            headline = stringResource(R.string.settings_ping_ips),
                            leadingIcon = Icons.Filled.NetworkCheck,
                            position = CardPosition.Middle,
                            onClick = {
                                if (!VpnController.hasTunnel()) {
                                    showToastUiCentered(
                                        context,
                                        context.resources.getString(R.string.settings_socks5_vpn_disabled_error),
                                        Toast.LENGTH_SHORT
                                    )
                                } else {
                                    showReachabilitySheet = true
                                }
                            }
                        )
                    }

                    RethinkListItem(
                        headline = stringResource(R.string.settings_treat_mobile_metered),
                        supporting = stringResource(R.string.settings_treat_mobile_metered_desc),
                        leadingIcon = Icons.Filled.Tune,
                        position = CardPosition.Middle,
                        onClick = {
                            val checked = !meteredOnlyMobile
                            meteredOnlyMobile = checked
                            persistentState.treatOnlyMobileNetworkAsMetered = checked
                            logEvent("mobile metered", "Treat mobile as metered: $checked")
                        },
                        trailing = {
                            Switch(
                                checked = meteredOnlyMobile,
                                onCheckedChange = { checked ->
                                    meteredOnlyMobile = checked
                                    persistentState.treatOnlyMobileNetworkAsMetered = checked
                                    logEvent("mobile metered", "Treat mobile as metered: $checked")
                                }
                            )
                        }
                    )

                    RethinkListItem(
                        headline = stringResource(R.string.settings_wg_listen_port),
                        supporting = stringResource(R.string.settings_wg_listen_port_desc),
                        leadingIcon = Icons.Filled.Tune,
                        position = CardPosition.Middle,
                        onClick = {
                            val checked = !listenPortFixed
                            listenPortFixed = checked
                            persistentState.randomizeListenPort = !checked
                            logEvent("listen port", "Randomize listen port: ${!checked}")
                        },
                        trailing = {
                            Switch(
                                checked = listenPortFixed,
                                onCheckedChange = { checked ->
                                    listenPortFixed = checked
                                    persistentState.randomizeListenPort = !checked
                                    logEvent("listen port", "Randomize listen port: ${!checked}")
                                }
                            )
                        }
                    )

                    RethinkListItem(
                        headline = stringResource(R.string.settings_wg_lockdown),
                        supporting = stringResource(R.string.settings_wg_lockdown_desc),
                        leadingIcon = Icons.Filled.Tune,
                        position = CardPosition.Middle,
                        onClick = {
                            val checked = !wgLockdown
                            wgLockdown = checked
                            persistentState.wgGlobalLockdown = checked
                            NewSettingsManager.markSettingSeen(NewSettingsManager.WG_GLOBAL_LOCKDOWN_MODE_SETTING)
                            logEvent("wg lockdown", "WG global lockdown: $checked")
                        },
                        trailing = {
                            Switch(
                                checked = wgLockdown,
                                onCheckedChange = { checked ->
                                    wgLockdown = checked
                                    persistentState.wgGlobalLockdown = checked
                                    NewSettingsManager.markSettingSeen(NewSettingsManager.WG_GLOBAL_LOCKDOWN_MODE_SETTING)
                                    logEvent("wg lockdown", "WG global lockdown: $checked")
                                }
                            )
                        }
                    )

                    RethinkListItem(
                        headline = stringResource(R.string.settings_endpoint_independence),
                        supporting = stringResource(R.string.settings_endpoint_independence_desc),
                        leadingIcon = Icons.Filled.Tune,
                        position = CardPosition.Middle,
                        onClick = {
                            val checked = !endpointIndependence
                            endpointIndependence = checked
                            persistentState.endpointIndependence = checked
                            if (!checked) {
                                allowIncoming = false
                                persistentState.nwEngExperimentalFeatures = false
                            } else {
                                allowIncoming = persistentState.nwEngExperimentalFeatures
                            }
                            logEvent("endpoint independence", "Endpoint independence: $checked")
                        },
                        trailing = {
                            Switch(
                                checked = endpointIndependence,
                                onCheckedChange = { checked ->
                                    endpointIndependence = checked
                                    persistentState.endpointIndependence = checked
                                    if (!checked) {
                                        allowIncoming = false
                                        persistentState.nwEngExperimentalFeatures = false
                                    } else {
                                        allowIncoming = persistentState.nwEngExperimentalFeatures
                                    }
                                    logEvent("endpoint independence", "Endpoint independence: $checked")
                                }
                            )
                        }
                    )

                    if (endpointIndependence) {
                        RethinkListItem(
                            headline = stringResource(R.string.settings_allow_incoming_wg_packets),
                            supporting = stringResource(R.string.settings_allow_incoming_wg_packets_desc),
                            leadingIcon = Icons.Filled.Tune,
                            position = CardPosition.Middle,
                            onClick = {
                                val checked = !allowIncoming
                                allowIncoming = checked
                                persistentState.nwEngExperimentalFeatures = checked
                                logEvent("allow incoming", "Allow incoming WG packets: $checked")
                            },
                            trailing = {
                                Switch(
                                    checked = allowIncoming,
                                    onCheckedChange = { checked ->
                                        allowIncoming = checked
                                        persistentState.nwEngExperimentalFeatures = checked
                                        logEvent("allow incoming", "Allow incoming WG packets: $checked")
                                    }
                                )
                            }
                        )
                    }

                    RethinkListItem(
                        headline = stringResource(R.string.settings_tcp_keep_alive),
                        supporting = stringResource(R.string.settings_tcp_keep_alive_desc),
                        leadingIcon = Icons.Filled.Tune,
                        position = CardPosition.Middle,
                        onClick = {
                            val checked = !tcpKeepAlive
                            tcpKeepAlive = checked
                            persistentState.tcpKeepAlive = checked
                            logEvent("tcp keep alive", "TCP keep alive: $checked")
                        },
                        trailing = {
                            Switch(
                                checked = tcpKeepAlive,
                                onCheckedChange = { checked ->
                                    tcpKeepAlive = checked
                                    persistentState.tcpKeepAlive = checked
                                    logEvent("tcp keep alive", "TCP keep alive: $checked")
                                }
                            )
                        }
                    )

                    RethinkListItem(
                        headline = stringResource(R.string.settings_jumbo_packets),
                        supporting = stringResource(R.string.settings_jumbo_packets_desc),
                        leadingIcon = Icons.Filled.Tune,
                        position = CardPosition.Middle,
                        onClick = {
                            if (vpnPolicy != POLICY_FIXED && !persistentState.routeRethinkInRethink) {
                                val checked = !useMaxMtu
                                useMaxMtu = checked
                                persistentState.useMaxMtu = checked
                                logEvent("jumbo packets", "Use jumbo packets: $checked")
                            }
                        },
                        trailing = {
                            Switch(
                                checked = useMaxMtu,
                                onCheckedChange = { checked ->
                                    useMaxMtu = checked
                                    persistentState.useMaxMtu = checked
                                    logEvent("jumbo packets", "Use jumbo packets: $checked")
                                },
                                enabled = vpnPolicy != POLICY_FIXED && !persistentState.routeRethinkInRethink
                            )
                        }
                    )

                    if (isAtleastQ()) {
                        RethinkListItem(
                            headline = stringResource(R.string.settings_vpn_builder_metered),
                            supporting = stringResource(R.string.settings_vpn_builder_metered_desc),
                            leadingIcon = Icons.Filled.Tune,
                            position = CardPosition.Middle,
                            onClick = {
                                val checked = !tunnelMetered
                                tunnelMetered = checked
                                persistentState.setVpnBuilderToMetered = checked
                                logEvent("vpn metered", "VPN builder metered: $checked")
                            },
                            trailing = {
                                Switch(
                                    checked = tunnelMetered,
                                    onCheckedChange = { checked ->
                                        tunnelMetered = checked
                                        persistentState.setVpnBuilderToMetered = checked
                                        logEvent("vpn metered", "VPN builder metered: $checked")
                                    }
                                )
                            }
                        )
                    }

                    RethinkListItem(
                        headline = stringResource(R.string.custom_lan_ip_title),
                        supporting = stringResource(R.string.custom_lan_ip_desc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_settings),
                        position = CardPosition.Last,
                        onClick = { showCustomLanIpSheet = true }
                    )
                }
            }

            // Dial Timeout Slider
            item {
                RethinkListGroup {
                    Column(modifier = Modifier.fillMaxWidth().padding(Dimensions.cardPadding)) {
                        Text(
                            text = stringResource(R.string.settings_dial_timeout),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dialTimeoutDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Dimensions.spacingSm))
                        Slider(
                            value = dialTimeoutMin.toFloat(),
                            onValueChange = { value ->
                                dialTimeoutMin = value.toInt()
                                persistentState.dialTimeoutSec = dialTimeoutMin * SECONDS_PER_MINUTE
                            },
                            valueRange = 0f..60f,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    }
                }
            }
        }
    }

    if (showCustomLanIpSheet) {
        CustomLanIpSheet(
            persistentState = persistentState,
            onDismiss = { showCustomLanIpSheet = false }
        )
    }
    if (showReachabilitySheet) {
        NetworkReachabilitySheet(
            persistentState = persistentState,
            onDismiss = { showReachabilitySheet = false }
        )
    }
}

@Composable
private fun TunnelOverviewCard(
    vpnPolicyDesc: String,
    ipDesc: String,
    dialTimeoutDesc: String
) {
    Surface(
        shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
            )
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.spacingXl),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
        ) {
            Text(
                text = stringResource(R.string.lbl_network),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.vpn_policy_title) + ": $vpnPolicyDesc",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.settings_ip_dialog_title) + ": $ipDesc",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.settings_dial_timeout) + ": $dialTimeoutDesc",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    alpha: Float = 1f,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(text = description, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = description, style = MaterialTheme.typography.bodySmall)
        }
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
    }
}

@Composable
private fun DefaultDnsDialog(
    persistentState: PersistentState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val options = Constants.DEFAULT_DNS_LIST
    val checkedItem = options.firstOrNull { it.url == persistentState.defaultDnsUrl }?.let { options.indexOf(it) } ?: 0
    var selectedIndex by remember { mutableIntStateOf(checkedItem) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_default_dns_heading)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = selectedIndex == index, onClick = { selectedIndex = index })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = item.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                persistentState.defaultDnsUrl = options[selectedIndex].url
                onConfirm()
                onDismiss()
            }) { Text(stringResource(R.string.fapps_info_dialog_positive_btn)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lbl_cancel)) } }
    )
}

@Composable
private fun VpnPolicyDialog(
    persistentState: PersistentState,
    context: Context,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val conservativeTxt = stringResource(R.string.vpn_policy_fixed) + " " + stringResource(R.string.lbl_experimental)
    val options = listOf(
        NetworkPolicyOption(
            stringResource(R.string.settings_ip_text_ipv46),
            stringResource(R.string.vpn_policy_auto_desc)
        ),
        NetworkPolicyOption(
            stringResource(R.string.vpn_policy_sensitive),
            stringResource(R.string.vpn_policy_sensitive_desc)
        ),
        NetworkPolicyOption(
            stringResource(R.string.vpn_policy_relaxed),
            stringResource(R.string.vpn_policy_relaxed_desc)
        ),
        NetworkPolicyOption(conservativeTxt, stringResource(R.string.vpn_policy_fixed_desc))
    )
    var selectedIndex by remember { mutableIntStateOf(persistentState.vpnBuilderPolicy) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vpn_policy_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEachIndexed { index, option ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = selectedIndex == index, onClick = { selectedIndex = index })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(option.title, style = MaterialTheme.typography.bodyMedium)
                            Text(option.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedIndex); onDismiss() }) { Text(stringResource(R.string.fapps_info_dialog_positive_btn)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lbl_cancel)) } }
    )
}

@Composable
private fun IpProtocolDialog(
    persistentState: PersistentState,
    context: Context,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val alwaysv46Txt =
        stringResource(R.string.settings_ip_text_ipv4) + " & " + stringResource(R.string.settings_ip_text_ipv6) + " " + stringResource(
            R.string.lbl_experimental
        )
    val items = listOf(
        stringResource(R.string.settings_ip_dialog_ipv4),
        stringResource(R.string.settings_ip_dialog_ipv6),
        alwaysv46Txt,
        stringResource(R.string.settings_ip_dialog_ipv46)
    )
    val chosenProtocol = persistentState.internetProtocolType
    val checkedItem = when (chosenProtocol) {
        InternetProtocol.ALWAYSv46.id -> IP_DIALOG_POS_ALWAYS_V46
        InternetProtocol.IPv46.id -> IP_DIALOG_POS_V46
        InternetProtocol.IPv4.id -> IP_DIALOG_POS_IPV4
        InternetProtocol.IPv6.id -> IP_DIALOG_POS_IPV6
        else -> IP_DIALOG_POS_IPV4
    }
    var selectedIndex by remember { mutableIntStateOf(checkedItem) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_ip_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEachIndexed { index, label ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = selectedIndex == index, onClick = { selectedIndex = index })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val selectedItem = when (selectedIndex) {
                    IP_DIALOG_POS_V46 -> InternetProtocol.IPv46.id
                    IP_DIALOG_POS_ALWAYS_V46 -> InternetProtocol.ALWAYSv46.id
                    else -> selectedIndex
                }
                if (persistentState.internetProtocolType != selectedItem) {
                    val protocolType = InternetProtocol.getInternetProtocol(selectedItem)
                    persistentState.internetProtocolType = protocolType.id
                    if (protocolType.id == InternetProtocol.IPv6.id ||
                        protocolType.id == InternetProtocol.IPv46.id ||
                        protocolType.id == InternetProtocol.ALWAYSv46.id
                    ) {
                        persistentState.enableStabilityDependentSettings(context)
                    }
                    onConfirm(protocolType.id)
                }
                onDismiss()
            }) { Text(stringResource(R.string.fapps_info_dialog_positive_btn)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lbl_cancel)) } }
    )
}

@Composable
private fun ConnectivityChecksDialog(
    persistentState: PersistentState,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    val items = listOf(
        stringResource(R.string.settings_app_list_default_app),
        stringResource(R.string.settings_ip_text_ipv46),
        stringResource(R.string.lbl_manual)
    )
    val type = persistentState.performAutoNetworkConnectivityChecks
    val enabled = persistentState.connectivityChecks
    val checkedItem = if (!enabled) 0 else if (type) 1 else 2
    var selectedIndex by remember { mutableIntStateOf(checkedItem) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_connectivity_checks)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEachIndexed { index, label ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = selectedIndex == index, onClick = { selectedIndex = index })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (selectedIndex) {
                    0 -> {
                        persistentState.performAutoNetworkConnectivityChecks = true
                        persistentState.connectivityChecks = false
                        onConfirm(false)
                    }

                    1 -> {
                        persistentState.performAutoNetworkConnectivityChecks = true
                        persistentState.connectivityChecks = true
                        onConfirm(true)
                    }

                    2 -> {
                        persistentState.performAutoNetworkConnectivityChecks = false
                        persistentState.connectivityChecks = true
                        onConfirm(true)
                    }
                }
                onDismiss()
            }) { Text(stringResource(R.string.fapps_info_dialog_positive_btn)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lbl_cancel)) } }
    )
}
