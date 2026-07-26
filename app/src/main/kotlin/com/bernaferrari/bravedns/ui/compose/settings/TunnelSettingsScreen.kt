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
package com.bernaferrari.bravedns.ui.compose.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.Dimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.dialog.CustomLanIpSheet
import com.bernaferrari.bravedns.ui.dialog.NetworkReachabilitySheet
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.InternetProtocol
import com.bernaferrari.bravedns.util.NewSettingsManager
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.Utilities.isAtleastQ
import com.bernaferrari.bravedns.util.Utilities.showToastUiCentered
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

private data class NetworkPolicyOption(val title: String, val description: String)

private fun tunnelFocusTarget(
    focusKey: String,
    isLockdown: Boolean,
    showConnectivityChecksOption: Boolean,
    showPingIps: Boolean,
    showAllowIncoming: Boolean,
    showVpnMetered: Boolean
): Pair<Int, Int>? {
    val networkIndex = if (isLockdown) 1 else 0
    val advancedIndex = networkIndex + 1
    val timeoutIndex = advancedIndex + 1
    val rowHeight = 82
    val groupStart = 62

    fun groupOffset(row: Int): Int = groupStart + (rowHeight * row)

    val networkRow =
        when (focusKey) {
            "network_allow_bypass" -> 0
            "network_fail_open" -> 1
            "network_allow_lan" -> 2
            "network_all_networks" -> 3
            "network_exclude_apps_proxy" -> 4
            "network_protocol_translation" -> 5
            else -> null
        }

    val advancedRows = mutableMapOf<String, Int>()
    var row = 0
    fun addAdvancedRow(key: String, visible: Boolean = true) {
        if (!visible) return
        advancedRows[key] = row
        row++
    }

    addAdvancedRow("network_default_dns")
    addAdvancedRow("network_vpn_policy")
    addAdvancedRow("network_ip_protocol")
    addAdvancedRow("network_connectivity_checks", showConnectivityChecksOption)
    addAdvancedRow("network_ping_ips", showPingIps)
    addAdvancedRow("network_mobile_metered")
    addAdvancedRow("network_wg_listen_port")
    addAdvancedRow("network_wg_lockdown")
    addAdvancedRow("network_endpoint_independence")
    addAdvancedRow("network_allow_incoming_wg", showAllowIncoming)
    addAdvancedRow("network_tcp_keep_alive")
    addAdvancedRow("network_jumbo_packets")
    addAdvancedRow("network_vpn_metered", showVpnMetered)
    addAdvancedRow("network_custom_lan_ip")
    val advancedRow = advancedRows[focusKey]

    return when (focusKey) {
        "network_core" -> networkIndex to 0
        "network_advanced" -> advancedIndex to 0
        "network_dial_timeout" -> timeoutIndex to 0
        else -> {
            when {
                networkRow != null -> networkIndex to groupOffset(networkRow)
                advancedRow != null -> advancedIndex to groupOffset(advancedRow)
                else -> null
            }
        }
    }
}

private fun tunnelFocusIndex(focusKey: String, isLockdown: Boolean): Int? {
    val networkIndex = if (isLockdown) 1 else 0
    val advancedIndex = networkIndex + 1
    val timeoutIndex = advancedIndex + 1
    return when (focusKey) {
        "network_core",
        "network_allow_bypass",
        "network_fail_open",
        "network_allow_lan",
        "network_all_networks",
        "network_exclude_apps_proxy",
        "network_protocol_translation" -> networkIndex
        "network_advanced",
        "network_default_dns",
        "network_vpn_policy",
        "network_ip_protocol",
        "network_connectivity_checks",
        "network_ping_ips",
        "network_mobile_metered",
        "network_wg_listen_port",
        "network_wg_lockdown",
        "network_endpoint_independence",
        "network_allow_incoming_wg",
        "network_tcp_keep_alive",
        "network_jumbo_packets",
        "network_vpn_metered",
        "network_custom_lan_ip" -> advancedIndex
        "network_dial_timeout" -> timeoutIndex
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelSettingsScreen(
    persistentState: PersistentState,
    appConfig: AppConfig,
    eventLogger: EventLogger,
    onOpenVpnProfile: () -> Unit,
    initialFocusKey: String? = null,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val disabledText = stringResource(R.string.lbl_disabled)
    val protocolTranslationInactiveText = stringResource(R.string.settings_protocol_translation_dns_inactive)
    val socks5VpnDisabledErrorText = stringResource(R.string.settings_socks5_vpn_disabled_error)

    var isLockdown by remember { mutableStateOf(VpnController.isVpnLockdown()) }
    var allowBypass by remember { mutableStateOf(persistentState.allowBypass) }
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
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val initialFocus = initialFocusKey?.trim().orEmpty()
    var pendingFocusKey by rememberSaveable(initialFocus) { mutableStateOf(initialFocus) }
    var activeFocusKey by rememberSaveable(initialFocus) {
        mutableStateOf(initialFocus.ifBlank { null })
    }

    val canModify = !isLockdown
    val showPtrans = internetProtocol == InternetProtocol.IPv6.id
    val showConnectivityChecksOption = internetProtocol == InternetProtocol.IPv46.id
    val showPingIps = showConnectivityChecksOption && connectivityChecks

    fun logEvent(msg: String, details: String) {
        eventLogger.log(EventType.TUN_ESTABLISHED, Severity.LOW, msg, EventSource.UI, false, details)
    }

    fun formatTimeShort(totalSeconds: Int, disabledText: String): String {
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (seconds > 0) parts.add("${seconds}s")
        return if (parts.isEmpty()) disabledText else parts.joinToString(" ")
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

    val dialTimeoutDesc = formatTimeShort(dialTimeoutMin * SECONDS_PER_MINUTE, disabledText)
    val topBarSubtitle =
        stringResource(
            R.string.two_argument_colon,
            stringResource(R.string.vpn_policy_title),
            vpnPolicyDesc
        )

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

    LaunchedEffect(
        pendingFocusKey,
        isLockdown,
        showConnectivityChecksOption,
        showPingIps,
        endpointIndependence,
    ) {
        val key = pendingFocusKey.trim()
        if (key.isBlank()) return@LaunchedEffect
        activeFocusKey = key
        val target = tunnelFocusTarget(
            focusKey = key,
            isLockdown = isLockdown,
            showConnectivityChecksOption = showConnectivityChecksOption,
            showPingIps = showPingIps,
            showAllowIncoming = endpointIndependence,
            showVpnMetered = isAtleastQ(),
        )
        if (target != null) {
            val (index, offsetDp) = target
            listState.animateScrollToItem(index, with(density) { offsetDp.dp.toPx().roundToInt() })
            delay(900)
        } else {
            tunnelFocusIndex(key, isLockdown)?.let { listState.animateScrollToItem(it) }
            delay(750)
        }
        if (activeFocusKey == key) activeFocusKey = null
        pendingFocusKey = ""
    }

    val coreRows = listOf(
        RethinkTunnelSettingRow(
            id = "network_allow_bypass",
            title = stringResource(R.string.settings_allow_bypass_heading),
            description = stringResource(R.string.settings_allow_bypass_desc),
            kind = RethinkTunnelSettingKind.Toggle,
            checked = allowBypass,
            enabled = canModify && !Utilities.isPlayStoreFlavour(),
        ),
        RethinkTunnelSettingRow(
            id = "network_fail_open",
            title = stringResource(R.string.fail_open_network_title),
            description = stringResource(R.string.fail_open_network_desc),
            kind = RethinkTunnelSettingKind.Toggle,
            checked = stallNoNetwork,
            icon = RethinkTunnelSettingIcon.Tune,
        ),
        RethinkTunnelSettingRow(
            id = "network_allow_lan",
            title = stringResource(R.string.settings_allow_lan_heading),
            description = stringResource(R.string.settings_allow_lan_desc),
            kind = RethinkTunnelSettingKind.Toggle,
            checked = routeLan,
            enabled = canModify,
            icon = RethinkTunnelSettingIcon.Tune,
        ),
        RethinkTunnelSettingRow(
            id = "network_all_networks",
            title = stringResource(R.string.settings_network_all_networks),
            description = stringResource(R.string.settings_network_all_networks_desc),
            kind = RethinkTunnelSettingKind.Toggle,
            checked = useMultipleNetworks,
            enabled = canModify,
            icon = RethinkTunnelSettingIcon.Tune,
        ),
        RethinkTunnelSettingRow(
            id = "network_exclude_apps_proxy",
            title = stringResource(R.string.settings_exclude_apps_in_proxy),
            description = stringResource(R.string.settings_exclude_apps_in_proxy_desc),
            kind = RethinkTunnelSettingKind.Toggle,
            checked = excludeApps,
            enabled = canModify,
            icon = RethinkTunnelSettingIcon.Tune,
        ),
        RethinkTunnelSettingRow(
            id = "network_protocol_translation",
            title = stringResource(R.string.settings_protocol_translation),
            description = stringResource(R.string.settings_protocol_translation_desc),
            kind = RethinkTunnelSettingKind.Toggle,
            checked = protocolTranslation,
            enabled = showPtrans,
            icon = RethinkTunnelSettingIcon.Tune,
        ),
    )
    val advancedRows = buildList {
        add(RethinkTunnelSettingRow("network_default_dns", stringResource(R.string.settings_default_dns_heading), stringResource(R.string.settings_default_dns_desc), RethinkTunnelSettingKind.Action))
        add(RethinkTunnelSettingRow("network_vpn_policy", stringResource(R.string.vpn_policy_title), vpnPolicyDesc, RethinkTunnelSettingKind.Action))
        add(RethinkTunnelSettingRow("network_ip_protocol", stringResource(R.string.settings_ip_dialog_title), stringResource(R.string.settings_selected_ip_desc, ipDesc), RethinkTunnelSettingKind.Action, enabled = vpnPolicy != POLICY_FIXED))
        if (showConnectivityChecksOption) {
            add(RethinkTunnelSettingRow("network_connectivity_checks", stringResource(R.string.settings_connectivity_checks), stringResource(R.string.settings_connectivity_checks_desc), RethinkTunnelSettingKind.Action))
        }
        if (showPingIps) {
            add(RethinkTunnelSettingRow("network_ping_ips", stringResource(R.string.settings_ping_ips), kind = RethinkTunnelSettingKind.Action, icon = RethinkTunnelSettingIcon.NetworkCheck))
        }
        add(RethinkTunnelSettingRow("network_mobile_metered", stringResource(R.string.settings_treat_mobile_metered), stringResource(R.string.settings_treat_mobile_metered_desc), RethinkTunnelSettingKind.Toggle, meteredOnlyMobile, icon = RethinkTunnelSettingIcon.Tune))
        add(RethinkTunnelSettingRow("network_wg_listen_port", stringResource(R.string.settings_wg_listen_port), stringResource(R.string.settings_wg_listen_port_desc), RethinkTunnelSettingKind.Toggle, listenPortFixed, icon = RethinkTunnelSettingIcon.Tune))
        add(RethinkTunnelSettingRow("network_wg_lockdown", stringResource(R.string.settings_wg_lockdown), stringResource(R.string.settings_wg_lockdown_desc), RethinkTunnelSettingKind.Toggle, wgLockdown, icon = RethinkTunnelSettingIcon.Tune))
        add(RethinkTunnelSettingRow("network_endpoint_independence", stringResource(R.string.settings_endpoint_independence), stringResource(R.string.settings_endpoint_independence_desc), RethinkTunnelSettingKind.Toggle, endpointIndependence, icon = RethinkTunnelSettingIcon.Tune))
        if (endpointIndependence) {
            add(RethinkTunnelSettingRow("network_allow_incoming_wg", stringResource(R.string.settings_allow_incoming_wg_packets), stringResource(R.string.settings_allow_incoming_wg_packets_desc), RethinkTunnelSettingKind.Toggle, allowIncoming, icon = RethinkTunnelSettingIcon.Tune))
        }
        add(RethinkTunnelSettingRow("network_tcp_keep_alive", stringResource(R.string.settings_tcp_keep_alive), stringResource(R.string.settings_tcp_keep_alive_desc), RethinkTunnelSettingKind.Toggle, tcpKeepAlive, icon = RethinkTunnelSettingIcon.Tune))
        add(RethinkTunnelSettingRow("network_jumbo_packets", stringResource(R.string.settings_jumbo_packets), stringResource(R.string.settings_jumbo_packets_desc), RethinkTunnelSettingKind.Toggle, useMaxMtu, enabled = vpnPolicy != POLICY_FIXED && !persistentState.routeRethinkInRethink, icon = RethinkTunnelSettingIcon.Tune))
        if (isAtleastQ()) {
            add(RethinkTunnelSettingRow("network_vpn_metered", stringResource(R.string.settings_vpn_builder_metered), stringResource(R.string.settings_vpn_builder_metered_desc), RethinkTunnelSettingKind.Toggle, tunnelMetered, icon = RethinkTunnelSettingIcon.Tune))
        }
        add(RethinkTunnelSettingRow("network_custom_lan_ip", stringResource(R.string.custom_lan_ip_title), stringResource(R.string.custom_lan_ip_desc), RethinkTunnelSettingKind.Action))
    }

    RethinkTunnelSettingsScreen(
        listState = listState,
        strings = RethinkTunnelSettingsStrings(
            title = stringResource(R.string.lbl_network),
            subtitle = topBarSubtitle,
            lockdownDescription = stringResource(R.string.settings_lock_down_mode_desc),
            advanced = stringResource(R.string.lbl_advanced),
            dialTimeout = stringResource(R.string.settings_dial_timeout),
        ),
        showLockdown = isLockdown,
        coreRows = coreRows,
        advancedRows = advancedRows,
        dialTimeoutMinutes = dialTimeoutMin,
        dialTimeoutDescription = dialTimeoutDesc,
        focusedRowId = activeFocusKey,
        onBackClick = onBackClick,
        onLockdownClick = onOpenVpnProfile,
        onActionClick = { id ->
            when (id) {
                "network_default_dns" -> showDefaultDnsDialog = true
                "network_vpn_policy" -> showVpnPolicyDialog = true
                "network_ip_protocol" -> if (vpnPolicy != POLICY_FIXED) showIpDialog = true
                "network_connectivity_checks" -> showConnectivityChecksDialog = true
                "network_ping_ips" -> {
                    if (VpnController.hasTunnel()) showReachabilitySheet = true
                    else showToastUiCentered(context, socks5VpnDisabledErrorText, Toast.LENGTH_SHORT)
                }
                "network_custom_lan_ip" -> showCustomLanIpSheet = true
            }
        },
        onToggleChange = { id, checked ->
            when (id) {
                "network_allow_bypass" -> {
                    if (!Utilities.isPlayStoreFlavour()) {
                        allowBypass = checked
                        persistentState.allowBypass = checked
                        logEvent("allow bypass", "Allow bypass VPN: $checked")
                    }
                }
                "network_fail_open" -> {
                    stallNoNetwork = checked
                    persistentState.stallOnNoNetwork = checked
                    logEvent("stall on no network", "Stall on no network: $checked")
                }
                "network_allow_lan" -> {
                    routeLan = checked
                    persistentState.privateIps = checked
                    if (checked) persistentState.enableStabilityDependentSettings(context)
                    logEvent("route lan traffic", "Route LAN traffic: $checked")
                }
                "network_all_networks" -> {
                    useMultipleNetworks = checked
                    persistentState.useMultipleNetworks = checked
                    if (checked) persistentState.enableStabilityDependentSettings(context)
                    if (!checked && persistentState.routeRethinkInRethink) persistentState.routeRethinkInRethink = false
                    logEvent("use all networks", "Use all networks for VPN: $checked")
                }
                "network_exclude_apps_proxy" -> {
                    excludeApps = checked
                    persistentState.excludeAppsInProxy = !checked
                    logEvent("exclude apps in proxy", "Exclude apps in proxy: ${!checked}")
                }
                "network_protocol_translation" -> {
                    if (appConfig.getBraveMode().isDnsActive()) {
                        protocolTranslation = checked
                        persistentState.protocolTranslationType = checked
                    } else {
                        protocolTranslation = false
                        showToastUiCentered(context, protocolTranslationInactiveText, Toast.LENGTH_SHORT)
                    }
                    logEvent("protocol translation", "Protocol translation: $checked")
                }
                "network_mobile_metered" -> {
                    meteredOnlyMobile = checked
                    persistentState.treatOnlyMobileNetworkAsMetered = checked
                    logEvent("mobile metered", "Treat mobile as metered: $checked")
                }
                "network_wg_listen_port" -> {
                    listenPortFixed = checked
                    persistentState.randomizeListenPort = !checked
                    logEvent("listen port", "Randomize listen port: ${!checked}")
                }
                "network_wg_lockdown" -> {
                    wgLockdown = checked
                    persistentState.wgGlobalLockdown = checked
                    NewSettingsManager.markSettingSeen(NewSettingsManager.WG_GLOBAL_LOCKDOWN_MODE_SETTING)
                    logEvent("wg lockdown", "WG global lockdown: $checked")
                }
                "network_endpoint_independence" -> {
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
                "network_allow_incoming_wg" -> {
                    allowIncoming = checked
                    persistentState.nwEngExperimentalFeatures = checked
                    logEvent("allow incoming", "Allow incoming WG packets: $checked")
                }
                "network_tcp_keep_alive" -> {
                    tcpKeepAlive = checked
                    persistentState.tcpKeepAlive = checked
                    logEvent("tcp keep alive", "TCP keep alive: $checked")
                }
                "network_jumbo_packets" -> {
                    useMaxMtu = checked
                    persistentState.useMaxMtu = checked
                    logEvent("jumbo packets", "Use jumbo packets: $checked")
                }
                "network_vpn_metered" -> {
                    tunnelMetered = checked
                    persistentState.setVpnBuilderToMetered = checked
                    logEvent("vpn metered", "VPN builder metered: $checked")
                }
            }
        },
        onDialTimeoutChange = { minutes ->
            dialTimeoutMin = minutes
            persistentState.dialTimeoutSec = minutes * SECONDS_PER_MINUTE
        },
    )
    if (showCustomLanIpSheet) {
        CustomLanIpSheet(persistentState = persistentState, onDismiss = { showCustomLanIpSheet = false })
    }
    if (showReachabilitySheet) {
        NetworkReachabilitySheet(persistentState = persistentState, onDismiss = { showReachabilitySheet = false })
    }
}

@Composable
private fun DefaultDnsDialog(
    persistentState: PersistentState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val options = Constants.DEFAULT_DNS_LIST
    RethinkSelectionDialog(
        title = stringResource(R.string.settings_default_dns_heading),
        options = options.map { RethinkSelectionOption(it.url, it.name) },
        initialSelectedId = options.firstOrNull { it.url == persistentState.defaultDnsUrl }?.url ?: options.firstOrNull()?.url.orEmpty(),
        confirm = stringResource(R.string.fapps_info_dialog_positive_btn),
        cancel = stringResource(R.string.lbl_cancel),
        onDismiss = onDismiss,
        onConfirm = { option ->
            persistentState.defaultDnsUrl = option.id
            onConfirm()
            onDismiss()
        },
    )
}

@Composable
private fun VpnPolicyDialog(
    persistentState: PersistentState,
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
    RethinkSelectionDialog(
        title = stringResource(R.string.vpn_policy_title),
        options = options.mapIndexed { index, option -> RethinkSelectionOption(index.toString(), option.title, option.description) },
        initialSelectedId = persistentState.vpnBuilderPolicy.toString(),
        confirm = stringResource(R.string.fapps_info_dialog_positive_btn),
        cancel = stringResource(R.string.lbl_cancel),
        onDismiss = onDismiss,
        onConfirm = { option ->
            onConfirm(option.id.toInt())
            onDismiss()
        },
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
    RethinkSelectionDialog(
        title = stringResource(R.string.settings_ip_dialog_title),
        options = items.mapIndexed { index, label -> RethinkSelectionOption(index.toString(), label) },
        initialSelectedId = checkedItem.toString(),
        confirm = stringResource(R.string.fapps_info_dialog_positive_btn),
        cancel = stringResource(R.string.lbl_cancel),
        onDismiss = onDismiss,
        onConfirm = { option ->
            val selectedIndex = option.id.toInt()
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
        },
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
    RethinkSelectionDialog(
        title = stringResource(R.string.settings_connectivity_checks),
        options = items.mapIndexed { index, label -> RethinkSelectionOption(index.toString(), label) },
        initialSelectedId = checkedItem.toString(),
        confirm = stringResource(R.string.fapps_info_dialog_positive_btn),
        cancel = stringResource(R.string.lbl_cancel),
        onDismiss = onDismiss,
        onConfirm = { option ->
            val selectedIndex = option.id.toInt()
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
        },
    )
}
