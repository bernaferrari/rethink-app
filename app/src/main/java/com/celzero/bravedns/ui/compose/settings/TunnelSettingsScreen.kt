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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
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
        return if (parts.isEmpty()) context.getString(R.string.lbl_disabled) else parts.joinToString(" ")
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.lbl_network)) },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_arrow_back_24),
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLockdown) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenVpnProfile() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.NetworkCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.settings_lock_down_mode_desc))
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingToggleRow(
                        icon = Icons.Filled.Settings,
                        title = stringResource(R.string.settings_allow_bypass_heading),
                        description = stringResource(R.string.settings_allow_bypass_desc),
                        checked = allowBypass,
                        enabled = canModify && !Utilities.isPlayStoreFlavour(),
                        alpha = if (canModify) ALPHA_ENABLED else ALPHA_DISABLED,
                        onCheckedChange = { checked ->
                            if (Utilities.isPlayStoreFlavour()) return@SettingToggleRow
                            allowBypass = checked
                            persistentState.allowBypass = checked
                            allowBypassLoading = true
                            scope.launch {
                                delay(1000L)
                                allowBypassLoading = false
                            }
                            logEvent("allow bypass", "Allow bypass VPN: $checked")
                        }
                    )
                    if (allowBypassLoading) {
                        Text(text = stringResource(R.string.lbl_loading), style = MaterialTheme.typography.bodySmall)
                    }

                    SettingToggleRow(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.fail_open_network_title),
                        description = stringResource(R.string.fail_open_network_desc),
                        checked = stallNoNetwork,
                        enabled = true,
                        onCheckedChange = {
                            stallNoNetwork = it
                            persistentState.stallOnNoNetwork = it
                            logEvent("stall on no network", "Stall on no network: $it")
                        }
                    )

                    SettingToggleRow(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.settings_allow_lan_heading),
                        description = stringResource(R.string.settings_allow_lan_desc),
                        checked = routeLan,
                        enabled = canModify,
                        alpha = if (canModify) ALPHA_ENABLED else ALPHA_DISABLED,
                        onCheckedChange = { checked ->
                            routeLan = checked
                            persistentState.privateIps = checked
                            if (checked) persistentState.enableStabilityDependentSettings(context)
                            logEvent("route lan traffic", "Route LAN traffic: $checked")
                        }
                    )

                    SettingToggleRow(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.settings_network_all_networks),
                        description = stringResource(R.string.settings_network_all_networks_desc),
                        checked = useMultipleNetworks,
                        enabled = canModify,
                        alpha = if (canModify) ALPHA_ENABLED else ALPHA_DISABLED,
                        onCheckedChange = { checked ->
                            useMultipleNetworks = checked
                            persistentState.useMultipleNetworks = checked
                            if (checked) persistentState.enableStabilityDependentSettings(context)
                            if (!checked && persistentState.routeRethinkInRethink) {
                                persistentState.routeRethinkInRethink = false
                            }
                            logEvent("use all networks", "Use all networks for VPN: $checked")
                        }
                    )

                    SettingToggleRow(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.settings_exclude_apps_in_proxy),
                        description = stringResource(R.string.settings_exclude_apps_in_proxy_desc),
                        checked = excludeApps,
                        enabled = canModify,
                        alpha = if (canModify) ALPHA_ENABLED else ALPHA_DISABLED,
                        onCheckedChange = { checked ->
                            excludeApps = checked
                            persistentState.excludeAppsInProxy = !checked
                            logEvent("exclude apps in proxy", "Exclude apps in proxy: ${!checked}")
                        }
                    )

                    SettingToggleRow(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.settings_protocol_translation),
                        description = stringResource(R.string.settings_protocol_translation_desc),
                        checked = protocolTranslation,
                        enabled = showPtrans,
                        alpha = if (showPtrans) ALPHA_ENABLED else ALPHA_DISABLED,
                        onCheckedChange = { checked ->
                            if (appConfig.getBraveMode().isDnsActive()) {
                                protocolTranslation = checked
                                persistentState.protocolTranslationType = checked
                            } else {
                                protocolTranslation = false
                                showToastUiCentered(context, context.getString(R.string.settings_protocol_translation_dns_inactive), Toast.LENGTH_SHORT)
                            }
                            logEvent("protocol translation", "Protocol translation: $checked")
                        }
                    )

                    SettingActionRow(
                        icon = Icons.Filled.Settings,
                        title = stringResource(R.string.settings_default_dns_heading),
                        description = stringResource(R.string.settings_default_dns_desc),
                        onClick = { showDefaultDnsDialog = true }
                    )

                    SettingActionRow(
                        icon = Icons.Filled.Settings,
                        title = stringResource(R.string.vpn_policy_title),
                        description = vpnPolicyDesc,
                        onClick = { showVpnPolicyDialog = true }
                    )

                    SettingActionRow(
                        icon = Icons.Filled.Settings,
                        title = stringResource(R.string.settings_ip_dialog_title),
                        description = stringResource(R.string.settings_selected_ip_desc, ipDesc),
                        enabled = vpnPolicy != POLICY_FIXED,
                        onClick = { showIpDialog = true }
                    )

                    SettingActionRow(
                        icon = Icons.Filled.Settings,
                        title = stringResource(R.string.settings_connectivity_checks),
                        description = stringResource(R.string.settings_connectivity_checks_desc),
                        enabled = showConnectivityChecksOption,
                        onClick = { showConnectivityChecksDialog = true }
                    )

                    if (showPingIps) {
                        Button(
                            onClick = {
                                if (!VpnController.hasTunnel()) {
                                    showToastUiCentered(context, context.getString(R.string.settings_socks5_vpn_disabled_error), Toast.LENGTH_SHORT)
                                } else {
                                    showReachabilitySheet = true
                                }
                            }
                        ) {
                            Text(text = stringResource(R.string.settings_ping_ips))
                        }
                    }

                    SettingToggleRow(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.settings_treat_mobile_metered),
                        description = stringResource(R.string.settings_treat_mobile_metered_desc),
                        checked = meteredOnlyMobile,
                        enabled = true,
                        onCheckedChange = { checked ->
                            meteredOnlyMobile = checked
                            persistentState.treatOnlyMobileNetworkAsMetered = checked
                            logEvent("mobile metered", "Treat mobile as metered: $checked")
                        }
                    )

                    SettingToggleRow(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.settings_wg_listen_port),
                        description = stringResource(R.string.settings_wg_listen_port_desc),
                        checked = listenPortFixed,
                        enabled = true,
                        onCheckedChange = { checked ->
                            listenPortFixed = checked
                            persistentState.randomizeListenPort = !checked
                            logEvent("listen port", "Randomize listen port: ${!checked}")
                        }
                    )

                    SettingToggleRow(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.settings_wg_lockdown),
                        description = stringResource(R.string.settings_wg_lockdown_desc),
                        checked = wgLockdown,
                        enabled = true,
                        onCheckedChange = { checked ->
                            wgLockdown = checked
                            persistentState.wgGlobalLockdown = checked
                            NewSettingsManager.markSettingSeen(NewSettingsManager.WG_GLOBAL_LOCKDOWN_MODE_SETTING)
                            logEvent("wg lockdown", "WG global lockdown: $checked")
                        }
                    )

                    SettingToggleRow(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.settings_endpoint_independence),
                        description = stringResource(R.string.settings_endpoint_independence_desc),
                        checked = endpointIndependence,
                        enabled = true,
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

                    if (endpointIndependence) {
                        SettingToggleRow(
                            icon = Icons.Filled.Tune,
                            title = stringResource(R.string.settings_allow_incoming_wg_packets),
                            description = stringResource(R.string.settings_allow_incoming_wg_packets_desc),
                            checked = allowIncoming,
                            enabled = true,
                            onCheckedChange = { checked ->
                                allowIncoming = checked
                                persistentState.nwEngExperimentalFeatures = checked
                                logEvent("allow incoming", "Allow incoming WG packets: $checked")
                            }
                        )
                    }

                    SettingToggleRow(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.settings_tcp_keep_alive),
                        description = stringResource(R.string.settings_tcp_keep_alive_desc),
                        checked = tcpKeepAlive,
                        enabled = true,
                        onCheckedChange = { checked ->
                            tcpKeepAlive = checked
                            persistentState.tcpKeepAlive = checked
                            logEvent("tcp keep alive", "TCP keep alive: $checked")
                        }
                    )

                    SettingToggleRow(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.settings_jumbo_packets),
                        description = stringResource(R.string.settings_jumbo_packets_desc),
                        checked = useMaxMtu,
                        enabled = vpnPolicy != POLICY_FIXED && !persistentState.routeRethinkInRethink,
                        alpha = if (vpnPolicy != POLICY_FIXED && !persistentState.routeRethinkInRethink) ALPHA_ENABLED else ALPHA_DISABLED,
                        onCheckedChange = { checked ->
                            useMaxMtu = checked
                            persistentState.useMaxMtu = checked
                            logEvent("jumbo packets", "Use jumbo packets: $checked")
                        }
                    )

                    if (isAtleastQ()) {
                        SettingToggleRow(
                            icon = Icons.Filled.Tune,
                            title = stringResource(R.string.settings_vpn_builder_metered),
                            description = stringResource(R.string.settings_vpn_builder_metered_desc),
                            checked = tunnelMetered,
                            enabled = true,
                            onCheckedChange = { checked ->
                                tunnelMetered = checked
                                persistentState.setVpnBuilderToMetered = checked
                                logEvent("vpn metered", "VPN builder metered: $checked")
                            }
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.settings_dial_timeout), style = MaterialTheme.typography.titleSmall)
                        Text(text = dialTimeoutDesc, style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = dialTimeoutMin.toFloat(),
                            onValueChange = { value ->
                                dialTimeoutMin = value.toInt()
                                persistentState.dialTimeoutSec = dialTimeoutMin * SECONDS_PER_MINUTE
                            },
                            valueRange = 0f..60f
                        )
                    }

                    SettingActionRow(
                        icon = Icons.Filled.Settings,
                        title = stringResource(R.string.custom_lan_ip_title),
                        description = stringResource(R.string.custom_lan_ip_desc),
                        onClick = { showCustomLanIpSheet = true }
                    )
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
        Icon(imageVector = Icons.Filled.ArrowForward, contentDescription = null)
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
        NetworkPolicyOption(stringResource(R.string.settings_ip_text_ipv46), stringResource(R.string.vpn_policy_auto_desc)),
        NetworkPolicyOption(stringResource(R.string.vpn_policy_sensitive), stringResource(R.string.vpn_policy_sensitive_desc)),
        NetworkPolicyOption(stringResource(R.string.vpn_policy_relaxed), stringResource(R.string.vpn_policy_relaxed_desc)),
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
    val alwaysv46Txt = stringResource(R.string.settings_ip_text_ipv4) + " & " + stringResource(R.string.settings_ip_text_ipv6) + " " + stringResource(R.string.lbl_experimental)
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
                        protocolType.id == InternetProtocol.ALWAYSv46.id) {
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
