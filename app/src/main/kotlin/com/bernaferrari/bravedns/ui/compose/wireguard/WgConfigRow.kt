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

import Logger
import Logger.LOG_TAG_UI

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.compose.theme.Dimensions
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
import com.bernaferrari.bravedns.ui.compose.wireguard.WgType
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardConfigCard
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardConfigCardState
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardConfigControl
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.wireguard.WgHopManager
import com.celzero.firestack.backend.RouterStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


private const val DELAY_MS = 1500L

data class WgChips(
    val ipv4: Boolean = false,
    val ipv6: Boolean = false,
    val splitTunnel: Boolean = false,
    val amnezia: Boolean = false,
    val hopSrc: Boolean = false,
    val hopping: Boolean = false,
    val properties: String = ""
) {
    fun hasAny(): Boolean {
        return ipv4 ||
            ipv6 ||
            splitTunnel ||
            amnezia ||
            hopSrc ||
            hopping ||
            properties.isNotEmpty()
    }
}

data class WgUiState(
    val isActive: Boolean,
    val statusText: String,
    val appsText: String,
    val showActiveLayout: Boolean,
    val uptimeText: String,
    val rxtxText: String,
    val strokeColor: Color,
    val strokeWidth: Dp
)

private data class WgStrings(
    val disabled: String,
    val failing: String,
    val waiting: String,
    val active: String,
    val routingRemainingApps: String,
    val addRemoveApps: String,
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
fun WgConfigRow(
    config: WgConfigFiles,
    eventLogger: EventLogger,
    onDnsStatusChanged: () -> Unit,
    onConfigDetailClick: (Int, WgType) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val strings = rememberWgStrings()
    val disabledStatus = strings.disabled.replaceFirstChar(Char::titlecase)
    val identifierText = stringResource(R.string.single_argument_parenthesis, config.id.toString())
    val ipv4Label = stringResource(R.string.settings_ip_text_ipv4)
    val ipv6Label = stringResource(R.string.settings_ip_text_ipv6)
    val splitLabel = stringResource(R.string.lbl_split)
    val amneziaLabel = stringResource(R.string.lbl_amnezia)
    val hoppingLabel = stringResource(R.string.lbl_hopping)
    val relayLabel = stringResource(R.string.cd_dns_crypt_relay_heading)
    var isChecked by remember(config.id, config.isActive) {
        mutableStateOf(config.isActive && VpnController.hasTunnel())
    }
    var statusText by remember(config.id) {
        mutableStateOf(disabledStatus)
    }
    var appsText by remember(config.id) { mutableStateOf("") }
    var showActiveLayout by remember(config.id) { mutableStateOf(false) }
    var uptimeText by remember(config.id) { mutableStateOf("") }
    var rxtxText by remember(config.id) { mutableStateOf("") }
    val errorColor = MaterialTheme.colorScheme.error
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    var strokeColor by remember(config.id, errorColor) { mutableStateOf(errorColor) }
    var strokeWidth by remember(config.id) { mutableStateOf(0.dp) }
    var chips by remember(config.id) { mutableStateOf(WgChips()) }
    var inProgress by remember(config.id) { mutableStateOf(false) }

    LaunchedEffect(
        config.id,
        config.isActive,
        config.isCatchAll,
        config.useOnlyOnMetered,
        config.ssidEnabled,
        strings
    ) {
        chips = withContext(Dispatchers.IO) { computeChips(strings, config) }
        while (isActive) {
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                delay(DELAY_MS)
                continue
            }
            val uiState =
                withContext(Dispatchers.IO) {
                    computeStatusUi(
                        strings = strings,
                        config = config,
                        errorColor = errorColor,
                        onSurfaceVariantColor = onSurfaceVariantColor,
                        tertiaryColor = tertiaryColor
                    )
                }
            isChecked = uiState.isActive
            statusText = uiState.statusText
            appsText = uiState.appsText
            showActiveLayout = uiState.showActiveLayout
            uptimeText = uiState.uptimeText
            rxtxText = uiState.rxtxText
            strokeColor = uiState.strokeColor
            strokeWidth = uiState.strokeWidth
            delay(DELAY_MS)
        }
    }

    RethinkWireguardConfigCard(
        state = RethinkWireguardConfigCardState(
            name = config.name,
            identifier = identifierText,
            chips = buildList {
                if (chips.ipv4) add(ipv4Label)
                if (chips.ipv6) add(ipv6Label)
                if (chips.splitTunnel) add(splitLabel)
                if (chips.amnezia) add(amneziaLabel)
                if (chips.hopSrc) add(hoppingLabel)
                if (chips.hopping) add(relayLabel)
                if (chips.properties.isNotEmpty()) add(chips.properties)
            },
            isChecked = isChecked,
            statusText = statusText,
            appsText = appsText,
            uptimeText = uptimeText.takeIf { showActiveLayout },
            rxTxText = rxtxText.takeIf { showActiveLayout },
            accentBorderColor = strokeColor.takeIf { strokeWidth > 0.dp },
            accentBorderWidth = strokeWidth,
        ),
        control = RethinkWireguardConfigControl.Switch,
        onOpen = { launchConfigDetail(context, strings.startRethinkToast, config.id, onConfigDetailClick) },
        onCheckedChange = { checked ->
            if (inProgress) return@RethinkWireguardConfigCard
            inProgress = true
            scope.launch(Dispatchers.IO) {
                val success =
                    if (checked) enableWgIfPossible(context, strings, config, onDnsStatusChanged, eventLogger)
                    else disableWgIfPossible(context, strings, config, onDnsStatusChanged, eventLogger)
                withContext(Dispatchers.Main) {
                    isChecked = if (checked) success else !success
                    inProgress = false
                }
            }
        },
    )
}

private suspend fun computeChips(strings: WgStrings, config: WgConfigFiles): WgChips {
    val id = ProxyManager.ID_WG_BASE + config.id
    val pair = VpnController.getSupportedIpVersion(id)
    val cfg = WireguardManager.getConfigById(config.id)
    val splitTunnel =
        if (cfg?.getPeers()?.isNotEmpty() == true) {
            VpnController.isSplitTunnelProxy(id, pair)
        } else {
            false
        }
    val hopSrc = WgHopManager.getMapBySrc(id).isNotEmpty()
    val hopping = WgHopManager.isAlreadyHop(id)
    val properties = buildString {
        if (config.isCatchAll) append(strings.symbolLightening)
        if (config.useOnlyOnMetered) append(strings.symbolMobile)
        if (config.ssidEnabled) append(strings.symbolId)
    }
    val amnezia = cfg?.getInterface()?.isAmnezia() == true
    return WgChips(
        ipv4 = pair.first,
        ipv6 = pair.second,
        splitTunnel = splitTunnel,
        amnezia = amnezia,
        hopSrc = hopSrc,
        hopping = hopping,
        properties = properties
    )
}

private suspend fun computeStatusUi(
    strings: WgStrings,
    config: WgConfigFiles,
    errorColor: Color,
    onSurfaceVariantColor: Color,
    tertiaryColor: Color
): WgUiState {
    val proxyId = ProxyManager.ID_WG_BASE + config.id
    val appCount = ProxyManager.getAppsCountForProxy(proxyId)
    val appsText =
        if (config.isCatchAll) {
            strings.routingRemainingApps
        } else {
            formatTemplate(strings.addRemoveApps, appCount.toString())
        }

    if (config.isActive && !VpnController.hasTunnel()) {
        return WgUiState(
            isActive = false,
            statusText = strings.disabled.replaceFirstChar(Char::titlecase),
            appsText = appsText,
            showActiveLayout = false,
            uptimeText = "",
            rxtxText = "",
            strokeColor = errorColor,
            strokeWidth = 0.dp
        )
    }

    if (!config.isActive || !VpnController.hasTunnel()) {
        return WgUiState(
            isActive = false,
            statusText = strings.disabled.replaceFirstChar(Char::titlecase),
            appsText = appsText,
            showActiveLayout = false,
            uptimeText = "",
            rxtxText = "",
            strokeColor = errorColor,
            strokeWidth = 0.dp
        )
    }

    val statusPair = VpnController.getProxyStatusById(proxyId)
    val stats = VpnController.getProxyStats(proxyId)
    val dnsStatusId = VpnController.getDnsStatus(proxyId)
    val statusText =
        if (dnsStatusId != null && isDnsError(dnsStatusId)) {
            strings.failing.replaceFirstChar(Char::titlecase)
        } else {
            getStatusText(
                strings,
                UIUtils.ProxyStatus.entries.find { it.id == statusPair.first },
                getHandshakeTime(stats).toString(),
                stats,
                statusPair.second
            )
        }

    val strokeColor =
        if (dnsStatusId != null && isDnsError(dnsStatusId)) {
            errorColor
        } else {
            val status =
                UIUtils.ProxyStatus.entries.find { it.id == statusPair.first }
            getStrokeColorForStatus(
                status = status,
                stats = stats,
                errorColor = errorColor,
                onSurfaceVariantColor = onSurfaceVariantColor,
                tertiaryColor = tertiaryColor
            )
        }

    val rxtx = getRxTx(strings, stats)
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
        appsText = appsText,
        showActiveLayout = true,
        uptimeText = uptimeText,
        rxtxText = rxtx,
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

private fun getStrokeColorForStatus(
    status: UIUtils.ProxyStatus?,
    stats: RouterStats?,
    errorColor: Color,
    onSurfaceVariantColor: Color,
    tertiaryColor: Color
): Color {
    val now = System.currentTimeMillis()
    val lastOk = stats?.lastOK ?: 0L
    val since = stats?.since ?: 0L
    val isFailing = now - since > WG_UPTIME_THRESHOLD && lastOk == 0L
    return when (status) {
        UIUtils.ProxyStatus.TOK ->
            if (isFailing) {
                onSurfaceVariantColor
            } else {
                tertiaryColor
            }
        UIUtils.ProxyStatus.TUP,
        UIUtils.ProxyStatus.TZZ,
        UIUtils.ProxyStatus.TNT -> onSurfaceVariantColor
        else -> errorColor
    }
}

private fun getStatusText(
    strings: WgStrings,
    status: UIUtils.ProxyStatus?,
    handshakeTime: String? = null,
    stats: RouterStats?,
    errMsg: String? = null
): String {
    if (status == null) {
        val txt =
            if (errMsg != null) {
                strings.waiting + " ($errMsg)"
            } else {
                strings.waiting
            }
        return txt.replaceFirstChar(Char::titlecase)
    }

    val now = System.currentTimeMillis()
    val lastOk = stats?.lastOK ?: 0L
    val since = stats?.since ?: 0L
    if (now - since > WG_UPTIME_THRESHOLD && lastOk == 0L) {
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

fun getUpTime(stats: RouterStats?): CharSequence {
    if (stats == null) return ""
    if (stats.since <= 0L) return ""
    val now = System.currentTimeMillis()
    return DateUtils.getRelativeTimeSpanString(
        stats.since,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    )
}

private fun getRxTx(strings: WgStrings, stats: RouterStats?): String {
    if (stats == null) return ""
    val rx =
        formatTemplate(
            strings.symbolDownload,
            Utilities.humanReadableByteCount(stats.rx, true)
        )
    val tx =
        formatTemplate(
            strings.symbolUpload,
            Utilities.humanReadableByteCount(stats.tx, true)
        )
    return formatTemplate(strings.twoArgumentSpace, tx, rx)
}

fun getHandshakeTime(stats: RouterStats?): CharSequence {
    if (stats == null) return ""
    if (stats.lastOK == 0L) return ""
    val now = System.currentTimeMillis()
    return DateUtils.getRelativeTimeSpanString(
        stats.lastOK,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    )
}

private suspend fun enableWgIfPossible(
    context: Context,
    strings: WgStrings,
    config: WgConfigFiles,
    onDnsStatusChanged: () -> Unit,
    eventLogger: EventLogger
): Boolean {
    if (!VpnController.hasTunnel()) {
        withContext(Dispatchers.Main) {
            Utilities.showToastUiCentered(
                context,
                ERR_CODE_VPN_NOT_ACTIVE + strings.vpnDisabledError,
                Toast.LENGTH_LONG
            )
        }
        return false
    }

    if (!WireguardManager.canEnableProxy()) {
        withContext(Dispatchers.Main) {
            Utilities.showToastUiCentered(
                context,
                ERR_CODE_VPN_NOT_FULL + strings.wgEnabledFailure,
                Toast.LENGTH_LONG
            )
        }
        return false
    }

    if (WireguardManager.oneWireGuardEnabled()) {
        withContext(Dispatchers.Main) {
            Utilities.showToastUiCentered(
                context,
                ERR_CODE_OTHER_WG_ACTIVE + strings.wgEnabledFailure,
                Toast.LENGTH_LONG
            )
        }
        return false
    }

    if (!WireguardManager.isValidConfig(config.id)) {
        withContext(Dispatchers.Main) {
            Utilities.showToastUiCentered(
                context,
                ERR_CODE_WG_INVALID + strings.wgEnabledFailure,
                Toast.LENGTH_LONG
            )
        }
        return false
    }

    WireguardManager.enableConfig(config.toImmutable())
    withContext(Dispatchers.Main) { onDnsStatusChanged() }
    logEvent(eventLogger, "WireGuard enabled", "WG ID: ${config.id}")
    return true
}

private suspend fun disableWgIfPossible(
    context: Context,
    strings: WgStrings,
    config: WgConfigFiles,
    onDnsStatusChanged: () -> Unit,
    eventLogger: EventLogger
): Boolean {
    val canDisable = WireguardManager.canDisableConfig(config.toImmutable())
    if (!canDisable) {
        val msg =
            if (WgHopManager.isWgEitherHopOrSrc(config.id)) {
                strings.disableFailureRelay
            } else {
                strings.disableFailure
            }
        withContext(Dispatchers.Main) {
            Utilities.showToastUiCentered(context, msg, Toast.LENGTH_LONG)
        }
        return false
    }

    WireguardManager.disableConfig(config.toImmutable())
    withContext(Dispatchers.Main) { onDnsStatusChanged() }
    logEvent(eventLogger, "WireGuard disabled", "WG ID: ${config.id}")
    return true
}

private fun launchConfigDetail(
    context: Context,
    startRethinkToast: String,
    id: Int,
    onConfigDetailClick: (Int, WgType) -> Unit
) {
    if (!VpnController.hasTunnel()) {
        Utilities.showToastUiCentered(
            context,
            startRethinkToast,
            Toast.LENGTH_SHORT
        )
        return
    }

    onConfigDetailClick(id, WgType.DEFAULT)
}

private fun logEvent(eventLogger: EventLogger, msg: String, details: String) {
    eventLogger.log(EventType.PROXY_SWITCH, Severity.LOW, msg, EventSource.UI, false, details)
}

private fun formatTemplate(template: String, vararg args: Any): String =
    String.format(java.util.Locale.getDefault(), template, *args)

@Composable
private fun rememberWgStrings(): WgStrings {
    val disabled = stringResource(R.string.lbl_disabled)
    val failing = stringResource(R.string.status_failing)
    val waiting = stringResource(R.string.status_waiting)
    val active = stringResource(R.string.lbl_active)
    val routingRemainingApps = stringResource(R.string.routing_remaining_apps)
    val addRemoveApps = stringResource(R.string.add_remove_apps)
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
        startRethinkToast
    ) {
        WgStrings(
            disabled = disabled,
            failing = failing,
            waiting = waiting,
            active = active,
            routingRemainingApps = routingRemainingApps,
            addRemoveApps = addRemoveApps,
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
