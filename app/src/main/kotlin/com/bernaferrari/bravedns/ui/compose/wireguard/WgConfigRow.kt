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
package com.bernaferrari.bravedns.ui.compose.wireguard

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.database.WgConfigFiles
import com.bernaferrari.bravedns.net.doh.Transaction
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.ProxyManager
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.service.WireguardManager
import com.bernaferrari.bravedns.service.WireguardManager.ERR_CODE_OTHER_WG_ACTIVE
import com.bernaferrari.bravedns.service.WireguardManager.ERR_CODE_VPN_NOT_ACTIVE
import com.bernaferrari.bravedns.service.WireguardManager.ERR_CODE_VPN_NOT_FULL
import com.bernaferrari.bravedns.service.WireguardManager.ERR_CODE_WG_INVALID
import com.bernaferrari.bravedns.service.WireguardManager.WG_UPTIME_THRESHOLD
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.util.UIUtils.formatTemplate
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.formatByteCount
import com.bernaferrari.bravedns.wireguard.WgHopManager
import com.celzero.firestack.backend.RouterStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DELAY_MS = 1500L

/**
 * One WireGuard config row. [WgType.DEFAULT] is the multi-config list (switch, full chip set,
 * app count); [WgType.ONE_WG] is the single "route everything" mode (checkbox, protocol chips
 * only while active, one-WG enable/disable bookkeeping).
 */
@Composable
fun WgConfigRow(
    config: WgConfigFiles,
    eventLogger: EventLogger,
    onDnsStatusChanged: () -> Unit,
    onConfigDetailClick: (Int, WgType) -> Unit
) = WireguardConfigRow(WgType.DEFAULT, config, eventLogger, onDnsStatusChanged, onConfigDetailClick)

@Composable
fun OneWgConfigRow(
    config: WgConfigFiles,
    eventLogger: EventLogger,
    onDnsStatusChanged: () -> Unit,
    onConfigDetailClick: (Int, WgType) -> Unit
) = WireguardConfigRow(WgType.ONE_WG, config, eventLogger, onDnsStatusChanged, onConfigDetailClick)

private data class WgChips(
    val ipv4: Boolean = false,
    val ipv6: Boolean = false,
    val splitTunnel: Boolean = false,
    val amnezia: Boolean = false,
    val hopSrc: Boolean = false,
    val hopping: Boolean = false,
    val properties: String = ""
)

private data class WgChipLabels(
    val ipv4: String,
    val ipv6: String,
    val split: String,
    val amnezia: String,
    val hopping: String,
    val relay: String,
)

private fun WgChips.labels(l: WgChipLabels): List<String> = buildList {
    if (ipv4) add(l.ipv4)
    if (ipv6) add(l.ipv6)
    if (splitTunnel) add(l.split)
    if (amnezia) add(l.amnezia)
    if (hopSrc) add(l.hopping)
    if (hopping) add(l.relay)
    if (properties.isNotEmpty()) add(properties)
}

private data class WgUiState(
    val isActive: Boolean,
    val statusText: String,
    val appsText: String?,
    val uptimeText: String?,
    val rxtxText: String?,
    val strokeColor: Color,
    val strokeWidth: Dp
)

private data class WgStatusColors(val error: Color, val neutral: Color, val ok: Color)

private data class WgStrings(
    val duplicateConfig: String,
    val disabled: String,
    val failing: String,
    val waiting: String,
    val active: String,
    val routingRemainingApps: String,
    val addRemoveApps: String,
    val oneWgAppsAdded: String,
    val logsCardDuration: String,
    val twoArgumentSpace: String,
    val versionInstallSource: String,
    val symbolDownload: String,
    val symbolUpload: String,
    val symbolLightening: String,
    val symbolMobile: String,
    val symbolId: String,
    val proxyStatusByRes: Map<Int, String>,
    val vpnDisabledError: String,
    val wgEnabledFailure: String,
    val disableFailureRelay: String,
    val disableFailure: String,
    val startRethinkToast: String
)

@Composable
private fun WireguardConfigRow(
    type: WgType,
    config: WgConfigFiles,
    eventLogger: EventLogger,
    onDnsStatusChanged: () -> Unit,
    onConfigDetailClick: (Int, WgType) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val strings = rememberWgStrings()
    val identifierText = stringResource(R.string.single_argument_parenthesis, config.id.toString())
    val chipLabels =
        WgChipLabels(
            ipv4 = stringResource(R.string.settings_ip_text_ipv4),
            ipv6 = stringResource(R.string.settings_ip_text_ipv6),
            split = stringResource(R.string.lbl_split),
            amnezia = stringResource(R.string.lbl_amnezia),
            hopping = stringResource(R.string.lbl_hopping),
            relay = stringResource(R.string.cd_dns_crypt_relay_heading),
        )
    val colors =
        WgStatusColors(
            error = MaterialTheme.colorScheme.error,
            neutral = MaterialTheme.colorScheme.onSurfaceVariant,
            ok = MaterialTheme.colorScheme.tertiary
        )
    var isChecked by remember(config.id, config.isActive) {
        mutableStateOf(config.isActive && VpnController.hasTunnel())
    }
    var uiState by remember(config.id, colors) { mutableStateOf(inactiveUiState(strings, colors)) }
    var chips by remember(config.id) { mutableStateOf(WgChips()) }
    var inProgress by remember(config.id) { mutableStateOf(false) }

    LaunchedEffect(
        config.id,
        config.isActive,
        config.isCatchAll,
        config.useOnlyOnMetered,
        config.ssidEnabled,
        strings,
        colors
    ) {
        chips = withContext(Dispatchers.IO) { computeChips(type, strings, config) }
        while (isActive) {
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                delay(DELAY_MS)
                continue
            }
            val next = withContext(Dispatchers.IO) { computeStatusUi(type, strings, config, colors) }
            isChecked = next.isActive
            uiState = next
            delay(DELAY_MS)
        }
    }

    // One-WG only shows its protocol chips while it is the active route.
    val showChips = type == WgType.DEFAULT || isChecked
    RethinkWireguardConfigCard(
        state = RethinkWireguardConfigCardState(
            name = config.name,
            identifier = identifierText,
            chips = if (showChips) chips.labels(chipLabels) else emptyList(),
            isChecked = isChecked,
            statusText = uiState.statusText,
            appsText = uiState.appsText,
            uptimeText = uiState.uptimeText,
            rxTxText = uiState.rxtxText,
            accentBorderColor = uiState.strokeColor.takeIf { uiState.strokeWidth > 0.dp },
            accentBorderWidth = uiState.strokeWidth,
        ),
        control =
            if (type == WgType.ONE_WG) RethinkWireguardConfigControl.Checkbox
            else RethinkWireguardConfigControl.Switch,
        onOpen = { launchConfigDetail(context, strings.startRethinkToast, config.id, type, onConfigDetailClick) },
        onCheckedChange = { checked ->
            if (inProgress) return@RethinkWireguardConfigCard
            inProgress = true
            scope.launch(Dispatchers.IO) {
                val success =
                    if (checked) enableWgIfPossible(type, context, strings, config, onDnsStatusChanged, eventLogger)
                    else disableWgIfPossible(type, context, strings, config, onDnsStatusChanged, eventLogger)
                withContext(Dispatchers.Main) {
                    isChecked = if (checked) success else !success
                    inProgress = false
                }
            }
        },
    )
}

private suspend fun computeChips(type: WgType, strings: WgStrings, config: WgConfigFiles): WgChips {
    val id = ProxyManager.ID_WG_BASE + config.id
    val pair = VpnController.getSupportedIpVersion(id)
    val cfg = WireguardManager.getConfigById(config.id)
    val splitTunnel =
        if (cfg?.getPeers()?.isNotEmpty() == true) {
            VpnController.isSplitTunnelProxy(id, pair)
        } else {
            false
        }
    // One-WG only surfaces the protocol chips.
    if (type == WgType.ONE_WG) {
        return WgChips(ipv4 = pair.first, ipv6 = pair.second, splitTunnel = splitTunnel)
    }
    val properties = buildString {
        if (config.isCatchAll) append(strings.symbolLightening)
        if (config.useOnlyOnMetered) append(strings.symbolMobile)
        if (config.ssidEnabled) append(strings.symbolId)
    }
    return WgChips(
        ipv4 = pair.first,
        ipv6 = pair.second,
        splitTunnel = splitTunnel,
        amnezia = cfg?.getInterface()?.isAmnezia() == true,
        hopSrc = WgHopManager.getMapBySrc(id).isNotEmpty(),
        hopping = WgHopManager.isAlreadyHop(id),
        properties = properties
    )
}

private suspend fun appsText(type: WgType, strings: WgStrings, config: WgConfigFiles, active: Boolean): String? =
    when {
        type == WgType.ONE_WG -> strings.oneWgAppsAdded.takeIf { active }
        config.isCatchAll -> strings.routingRemainingApps
        else -> {
            val appCount = ProxyManager.getAppsCountForProxy(ProxyManager.ID_WG_BASE + config.id)
            formatTemplate(strings.addRemoveApps, appCount.toString())
        }
    }

private fun inactiveUiState(
    strings: WgStrings,
    colors: WgStatusColors,
    appsText: String? = null,
): WgUiState =
    WgUiState(
        isActive = false,
        statusText = strings.disabled.replaceFirstChar(Char::titlecase),
        appsText = appsText,
        uptimeText = null,
        rxtxText = null,
        strokeColor = colors.error,
        strokeWidth = 0.dp
    )

private suspend fun computeStatusUi(
    type: WgType,
    strings: WgStrings,
    config: WgConfigFiles,
    colors: WgStatusColors,
): WgUiState {
    if (!config.isActive || !VpnController.hasTunnel()) {
        return inactiveUiState(strings, colors, appsText(type, strings, config, active = false))
    }

    val proxyId = ProxyManager.ID_WG_BASE + config.id
    val statusPair = VpnController.getProxyStatusById(proxyId)
    val stats = VpnController.getProxyStats(proxyId)
    val dnsStatusId = VpnController.getDnsStatus(proxyId)
    val status = UIUtils.ProxyStatus.entries.find { it.id == statusPair.first }
    val dnsFailing = dnsStatusId != null && isDnsError(dnsStatusId)
    val statusText =
        if (dnsFailing) {
            strings.failing.replaceFirstChar(Char::titlecase)
        } else {
            getStatusText(strings, status, getHandshakeTime(stats).toString(), stats, statusPair.second)
        }
    val strokeColor = if (dnsFailing) colors.error else getStrokeColorForStatus(status, stats, colors)

    val time = getUpTime(stats)
    val uptimeText =
        if (time.isNotEmpty()) {
            val t = formatTemplate(strings.logsCardDuration, time)
            formatTemplate(strings.twoArgumentSpace, strings.active, t)
        } else {
            strings.active
        }

    return WgUiState(
        isActive = true,
        statusText = statusText,
        appsText = appsText(type, strings, config, active = true),
        uptimeText = uptimeText,
        rxtxText = getRxTx(strings, stats),
        strokeColor = strokeColor,
        strokeWidth = 2.dp
    )
}

private fun isDnsError(statusId: Int?): Boolean {
    if (statusId == null) return true
    val s = Transaction.Status.fromId(statusId)
    return s == Transaction.Status.BAD_QUERY ||
        s == Transaction.Status.BAD_RESPONSE ||
        s == Transaction.Status.NO_RESPONSE ||
        s == Transaction.Status.SEND_FAIL ||
        s == Transaction.Status.CLIENT_ERROR ||
        s == Transaction.Status.INTERNAL_ERROR ||
        s == Transaction.Status.TRANSPORT_ERROR
}

private fun isHandshakeFailing(stats: RouterStats?): Boolean {
    val lastOk = stats?.lastOK ?: 0L
    val since = stats?.since ?: 0L
    return System.currentTimeMillis() - since > WG_UPTIME_THRESHOLD && lastOk == 0L
}

private fun getStrokeColorForStatus(
    status: UIUtils.ProxyStatus?,
    stats: RouterStats?,
    colors: WgStatusColors,
): Color =
    when (status) {
        UIUtils.ProxyStatus.TOK -> if (isHandshakeFailing(stats)) colors.neutral else colors.ok
        UIUtils.ProxyStatus.TUP,
        UIUtils.ProxyStatus.TZZ,
        UIUtils.ProxyStatus.TNT -> colors.neutral
        else -> colors.error
    }

private fun getStatusText(
    strings: WgStrings,
    status: UIUtils.ProxyStatus?,
    handshakeTime: String? = null,
    stats: RouterStats?,
    errMsg: String? = null
): String {
    if (status == null) {
        val txt = if (errMsg != null) strings.waiting + " ($errMsg)" else strings.waiting
        return txt.replaceFirstChar(Char::titlecase)
    }

    if (isHandshakeFailing(stats)) {
        return strings.failing.replaceFirstChar(Char::titlecase)
    }

    val statusRes = UIUtils.getProxyStatusStringRes(status.id)
    val baseText =
        (strings.proxyStatusByRes[statusRes] ?: strings.proxyStatusByRes.getValue(R.string.rt_filter_parent_selected))
            .replaceFirstChar(Char::titlecase)
    return if (stats?.lastOK != 0L && handshakeTime != null) {
        formatTemplate(strings.versionInstallSource, baseText, handshakeTime)
    } else {
        baseText
    }
}

private fun getUpTime(stats: RouterStats?): String = UIUtils.formatToRelativeTime(stats?.since ?: 0L)

fun getHandshakeTime(stats: RouterStats?): String = UIUtils.formatToRelativeTime(stats?.lastOK ?: 0L)

private fun getRxTx(strings: WgStrings, stats: RouterStats?): String {
    if (stats == null) return ""
    val rx = formatTemplate(strings.symbolDownload, formatByteCount(stats.rx))
    val tx = formatTemplate(strings.symbolUpload, formatByteCount(stats.tx))
    return formatTemplate(strings.twoArgumentSpace, tx, rx)
}

private suspend fun showLongToast(context: Context, msg: String) {
    withContext(Dispatchers.Main) { Utilities.showToastUiCentered(context, msg, Toast.LENGTH_LONG) }
}

@Suppress("ReturnCount")
private suspend fun enableWgIfPossible(
    type: WgType,
    context: Context,
    strings: WgStrings,
    config: WgConfigFiles,
    onDnsStatusChanged: () -> Unit,
    eventLogger: EventLogger
): Boolean {
    if (!VpnController.hasTunnel()) {
        showLongToast(context, ERR_CODE_VPN_NOT_ACTIVE + strings.vpnDisabledError)
        return false
    }
    if (!WireguardManager.canEnableProxy()) {
        showLongToast(context, ERR_CODE_VPN_NOT_FULL + strings.wgEnabledFailure)
        return false
    }
    val otherActive =
        if (type == WgType.ONE_WG) WireguardManager.isAnyOtherOneWgEnabled(config.id)
        else WireguardManager.oneWireGuardEnabled()
    if (otherActive) {
        showLongToast(context, ERR_CODE_OTHER_WG_ACTIVE + strings.wgEnabledFailure)
        return false
    }
    if (!WireguardManager.isValidConfig(config.id)) {
        showLongToast(context, ERR_CODE_WG_INVALID + strings.wgEnabledFailure)
        return false
    }

    if (WireguardManager.getConflictingActiveConfigId(config.id) != null) {
        showLongToast(context, strings.duplicateConfig)
        return false
    }
    if (type == WgType.ONE_WG) {
        WireguardManager.updateOneWireGuardConfig(config.id, owg = true)
        config.oneWireGuard = true
    }
    WireguardManager.enableConfig(config.toImmutable())
    withContext(Dispatchers.Main) { onDnsStatusChanged() }
    logEvent(eventLogger, if (type == WgType.ONE_WG) "One-WireGuard enabled" else "WireGuard enabled", config.id)
    return true
}

private suspend fun disableWgIfPossible(
    type: WgType,
    context: Context,
    strings: WgStrings,
    config: WgConfigFiles,
    onDnsStatusChanged: () -> Unit,
    eventLogger: EventLogger
): Boolean {
    if (type == WgType.ONE_WG) {
        if (!VpnController.hasTunnel()) {
            showLongToast(context, ERR_CODE_VPN_NOT_ACTIVE + strings.vpnDisabledError)
            return false
        }
        WireguardManager.updateOneWireGuardConfig(config.id, owg = false)
        config.oneWireGuard = false
    } else if (!WireguardManager.canDisableConfig(config.toImmutable())) {
        val msg =
            if (WgHopManager.isWgEitherHopOrSrc(config.id)) strings.disableFailureRelay
            else strings.disableFailure
        showLongToast(context, msg)
        return false
    }

    WireguardManager.disableConfig(config.toImmutable())
    withContext(Dispatchers.Main) { onDnsStatusChanged() }
    logEvent(eventLogger, if (type == WgType.ONE_WG) "One-WireGuard disabled" else "WireGuard disabled", config.id)
    return true
}

private fun launchConfigDetail(
    context: Context,
    startRethinkToast: String,
    id: Int,
    type: WgType,
    onConfigDetailClick: (Int, WgType) -> Unit
) {
    if (!VpnController.hasTunnel()) {
        Utilities.showToastUiCentered(context, startRethinkToast, Toast.LENGTH_SHORT)
        return
    }
    onConfigDetailClick(id, type)
}

private fun logEvent(eventLogger: EventLogger, msg: String, configId: Int) {
    eventLogger.log(EventType.PROXY_SWITCH, Severity.LOW, msg, EventSource.UI, false, "WG ID: $configId")
}

@Composable
private fun rememberWgStrings(): WgStrings {
    val duplicateConfig = stringResource(R.string.port_wg_duplicate)
    val disabled = stringResource(R.string.lbl_disabled)
    val failing = stringResource(R.string.status_failing)
    val waiting = stringResource(R.string.status_waiting)
    val active = stringResource(R.string.lbl_active)
    val routingRemainingApps = stringResource(R.string.routing_remaining_apps)
    val addRemoveApps = stringResource(R.string.add_remove_apps)
    val oneWgAppsAdded = stringResource(R.string.one_wg_apps_added)
    val logsCardDuration = stringResource(R.string.logs_card_duration)
    val twoArgumentSpace = stringResource(R.string.two_argument_space)
    val versionInstallSource = stringResource(R.string.about_version_install_source)
    val symbolDownload = stringResource(R.string.symbol_download)
    val symbolUpload = stringResource(R.string.symbol_upload)
    val symbolLightening = stringResource(R.string.symbol_lightening)
    val symbolMobile = stringResource(R.string.symbol_mobile)
    val symbolId = stringResource(R.string.symbol_id)
    val starting = stringResource(R.string.lbl_starting)
    val connected = stringResource(R.string.dns_connected)
    val idle = stringResource(R.string.lbl_idle)
    val stopped = stringResource(R.string.lbl_stopped)
    val paused = stringResource(R.string.pause_text)
    val selected = stringResource(R.string.rt_filter_parent_selected)
    val vpnDisabledError = stringResource(R.string.settings_socks5_vpn_disabled_error)
    val wgEnabledFailure = stringResource(R.string.wireguard_enabled_failure)
    val disableFailureRelay = stringResource(R.string.wireguard_disable_failure_relay)
    val disableFailure = stringResource(R.string.wireguard_disable_failure)
    val startRethinkToast = stringResource(R.string.ssv_toast_start_rethink)
    return remember(
        disabled,
        failing,
        waiting,
        active,
        routingRemainingApps,
        addRemoveApps,
        oneWgAppsAdded,
        logsCardDuration,
        twoArgumentSpace,
        versionInstallSource,
        symbolDownload,
        symbolUpload,
        symbolLightening,
        symbolMobile,
        symbolId,
        starting,
        connected,
        idle,
        stopped,
        paused,
        selected,
        vpnDisabledError,
        wgEnabledFailure,
        disableFailureRelay,
        disableFailure,
        startRethinkToast, duplicateConfig
    ) {
        WgStrings(
            duplicateConfig = duplicateConfig,
            disabled = disabled,
            failing = failing,
            waiting = waiting,
            active = active,
            routingRemainingApps = routingRemainingApps,
            addRemoveApps = addRemoveApps,
            oneWgAppsAdded = oneWgAppsAdded,
            logsCardDuration = logsCardDuration,
            twoArgumentSpace = twoArgumentSpace,
            versionInstallSource = versionInstallSource,
            symbolDownload = symbolDownload,
            symbolUpload = symbolUpload,
            symbolLightening = symbolLightening,
            symbolMobile = symbolMobile,
            symbolId = symbolId,
            proxyStatusByRes =
                mapOf(
                    R.string.lbl_starting to starting,
                    R.string.dns_connected to connected,
                    R.string.lbl_idle to idle,
                    R.string.status_failing to failing,
                    R.string.lbl_stopped to stopped,
                    R.string.status_waiting to waiting,
                    R.string.pause_text to paused,
                    R.string.rt_filter_parent_selected to selected
                ),
            vpnDisabledError = vpnDisabledError,
            wgEnabledFailure = wgEnabledFailure,
            disableFailureRelay = disableFailureRelay,
            disableFailure = disableFailure,
            startRethinkToast = startRethinkToast
        )
    }
}
