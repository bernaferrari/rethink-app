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
package com.celzero.bravedns.adapter

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.view.ViewGroup
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.database.WgConfigFiles
import com.celzero.bravedns.net.doh.Transaction
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.service.WireguardManager.ERR_CODE_OTHER_WG_ACTIVE
import com.celzero.bravedns.service.WireguardManager.ERR_CODE_VPN_NOT_ACTIVE
import com.celzero.bravedns.service.WireguardManager.ERR_CODE_VPN_NOT_FULL
import com.celzero.bravedns.service.WireguardManager.ERR_CODE_WG_INVALID
import com.celzero.bravedns.service.WireguardManager.WG_UPTIME_THRESHOLD
import com.celzero.bravedns.ui.activity.WgConfigDetailActivity
import com.celzero.bravedns.ui.activity.WgConfigDetailActivity.Companion.INTENT_EXTRA_WG_TYPE
import com.celzero.bravedns.ui.activity.WgConfigEditorActivity.Companion.INTENT_EXTRA_WG_ID
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.wireguard.WgHopManager
import com.celzero.firestack.backend.RouterStats
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WgConfigAdapter(
    private val context: Context,
    private val listener: OneWgConfigAdapter.DnsStatusListener,
    private val splitDns: Boolean,
    private val eventLogger: EventLogger
) : PagingDataAdapter<WgConfigFiles, WgConfigAdapter.WgInterfaceViewHolder>(DIFF_CALLBACK) {

    companion object {
        private const val DELAY_MS = 1500L
        private const val TAG = "WgConfigAdapter"
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<WgConfigFiles>() {

                override fun areItemsTheSame(
                    oldConnection: WgConfigFiles,
                    newConnection: WgConfigFiles
                ): Boolean {
                    return oldConnection == newConnection
                }

                override fun areContentsTheSame(
                    oldConnection: WgConfigFiles,
                    newConnection: WgConfigFiles
                ): Boolean {
                    return oldConnection == newConnection
                }
            }
    }

    override fun onBindViewHolder(holder: WgInterfaceViewHolder, position: Int) {
        val wgConfigFiles: WgConfigFiles = getItem(position) ?: return
        holder.update(wgConfigFiles)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WgInterfaceViewHolder {
        val composeView = ComposeView(parent.context)
        composeView.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        return WgInterfaceViewHolder(composeView)
    }

    inner class WgInterfaceViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {
        fun update(config: WgConfigFiles) {
            composeView.setContent {
                RethinkTheme {
                    ConfigRow(config)
                }
            }
        }
    }

    @Composable
    fun ConfigRow(config: WgConfigFiles) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()
        var isChecked by remember(config.id, config.isActive) {
            mutableStateOf(config.isActive && VpnController.hasTunnel())
        }
        var statusText by remember(config.id) {
            mutableStateOf(context.getString(R.string.lbl_disabled).replaceFirstChar(Char::titlecase))
        }
        var appsText by remember(config.id) { mutableStateOf("") }
        var showActiveLayout by remember(config.id) { mutableStateOf(false) }
        var uptimeText by remember(config.id) { mutableStateOf("") }
        var rxtxText by remember(config.id) { mutableStateOf("") }
        var strokeColor by remember(config.id) {
            mutableStateOf(Color(fetchColor(context, R.attr.chipTextNegative)))
        }
        var strokeWidth by remember(config.id) { mutableStateOf(0.dp) }
        var chips by remember(config.id) { mutableStateOf(WgChips()) }
        var inProgress by remember(config.id) { mutableStateOf(false) }

        LaunchedEffect(
            config.id,
            config.isActive,
            config.isCatchAll,
            config.useOnlyOnMetered,
            config.ssidEnabled
        ) {
            chips = withContext(Dispatchers.IO) { computeChips(config) }
            while (isActive) {
                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    delay(DELAY_MS)
                    continue
                }
                val uiState = withContext(Dispatchers.IO) { computeStatusUi(config) }
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

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clickable { launchConfigDetail(config.id) },
            shape = CardDefaults.shape,
            colors = CardDefaults.cardColors(),
            border = if (strokeWidth > 0.dp) BorderStroke(strokeWidth, strokeColor) else null
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = config.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text =
                                    context.getString(
                                        R.string.single_argument_parenthesis,
                                        config.id.toString()
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        if (chips.hasAny()) {
                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (chips.ipv4) {
                                    WgChip(text = context.getString(R.string.settings_ip_text_ipv4))
                                }
                                if (chips.ipv6) {
                                    WgChip(text = context.getString(R.string.settings_ip_text_ipv6))
                                }
                                if (chips.splitTunnel) {
                                    WgChip(text = context.getString(R.string.lbl_split))
                                }
                                if (chips.amnezia) {
                                    WgChip(text = context.getString(R.string.lbl_amnezia))
                                }
                                if (chips.hopSrc) {
                                    WgChip(text = context.getString(R.string.lbl_hopping))
                                }
                                if (chips.hopping) {
                                    WgChip(text = context.getString(R.string.cd_dns_crypt_relay_heading))
                                }
                                if (chips.properties.isNotEmpty()) {
                                    WgChip(text = chips.properties)
                                }
                            }
                        }
                    }

                    Switch(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            if (inProgress) return@Switch
                            inProgress = true
                            scope.launch(Dispatchers.IO) {
                                val success =
                                    if (checked) {
                                        enableWgIfPossible(config)
                                    } else {
                                        disableWgIfPossible(config)
                                    }
                                withContext(Dispatchers.Main) {
                                    isChecked = if (checked) success else !success
                                    inProgress = false
                                }
                            }
                        }
                    )
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Text(
                    text = appsText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (showActiveLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uptimeText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = rxtxText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun WgChip(text: String) {
        AssistChip(onClick = {}, label = { Text(text = text) })
    }

    private data class WgChips(
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

    private data class WgUiState(
        val isActive: Boolean,
        val statusText: String,
        val appsText: String,
        val showActiveLayout: Boolean,
        val uptimeText: String,
        val rxtxText: String,
        val strokeColor: Color,
        val strokeWidth: Dp
    )

    private suspend fun computeChips(config: WgConfigFiles): WgChips {
        val id = ProxyManager.ID_WG_BASE + config.id
        val pair = VpnController.getSupportedIpVersion(id)
        val cfg = WireguardManager.getConfigById(config.id)
        val splitTunnel =
            if (cfg?.getPeers()?.isNotEmpty() == true && pair != null) {
                VpnController.isSplitTunnelProxy(id, pair)
            } else {
                false
            }
        val hopSrc = WgHopManager.getMapBySrc(id).isNotEmpty()
        val hopping = WgHopManager.isAlreadyHop(id)
        val properties = buildString {
            if (config.isCatchAll) append(context.getString(R.string.symbol_lightening))
            if (config.useOnlyOnMetered) append(context.getString(R.string.symbol_mobile))
            if (config.ssidEnabled) append(context.getString(R.string.symbol_id))
        }
        val amnezia = cfg?.getInterface()?.isAmnezia() == true
        return WgChips(
            ipv4 = pair?.first == true,
            ipv6 = pair?.second == true,
            splitTunnel = splitTunnel,
            amnezia = amnezia,
            hopSrc = hopSrc,
            hopping = hopping,
            properties = properties
        )
    }

    private suspend fun computeStatusUi(config: WgConfigFiles): WgUiState {
        val proxyId = ProxyManager.ID_WG_BASE + config.id
        val appCount = ProxyManager.getAppsCountForProxy(proxyId)
        val appsText =
            if (config.isCatchAll) {
                context.getString(R.string.routing_remaining_apps)
            } else {
                context.getString(R.string.add_remove_apps, appCount.toString())
            }

        if (config.isActive && !VpnController.hasTunnel()) {
            return WgUiState(
                isActive = false,
                statusText = context.getString(R.string.lbl_disabled).replaceFirstChar(Char::titlecase),
                appsText = appsText,
                showActiveLayout = false,
                uptimeText = "",
                rxtxText = "",
                strokeColor = Color(fetchColor(context, R.attr.chipTextNegative)),
                strokeWidth = 0.dp
            )
        }

        if (!config.isActive || !VpnController.hasTunnel()) {
            return WgUiState(
                isActive = false,
                statusText = context.getString(R.string.lbl_disabled).replaceFirstChar(Char::titlecase),
                appsText = appsText,
                showActiveLayout = false,
                uptimeText = "",
                rxtxText = "",
                strokeColor = Color(fetchColor(context, R.attr.chipTextNegative)),
                strokeWidth = 0.dp
            )
        }

        val statusPair = VpnController.getProxyStatusById(proxyId)
        val stats = VpnController.getProxyStats(proxyId)
        val dnsStatusId = VpnController.getDnsStatus(proxyId)
        val statusText =
            if (dnsStatusId != null && isDnsError(dnsStatusId)) {
                context.getString(R.string.status_failing).replaceFirstChar(Char::titlecase)
            } else {
                getStatusText(
                    UIUtils.ProxyStatus.entries.find { it.id == statusPair.first },
                    getHandshakeTime(stats).toString(),
                    stats,
                    statusPair.second
                )
            }

        val strokeColor =
            if (dnsStatusId != null && isDnsError(dnsStatusId)) {
                Color(fetchColor(context, R.attr.chipTextNegative))
            } else {
                val status =
                    UIUtils.ProxyStatus.entries.find { it.id == statusPair.first }
                Color(fetchColor(context, getStrokeColorForStatus(status, stats)))
            }

        val rxtx = getRxTx(stats)
        val time = getUpTime(stats)
        val uptimeText =
            if (time.isNotEmpty()) {
                val t = context.getString(R.string.logs_card_duration, time)
                context.getString(
                    R.string.two_argument_space,
                    context.getString(R.string.lbl_active),
                    t
                )
            } else {
                context.getString(R.string.lbl_active)
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

    private fun isDnsError(statusId: Long?): Boolean {
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

    private fun getStrokeColorForStatus(status: UIUtils.ProxyStatus?, stats: RouterStats?): Int {
        val now = System.currentTimeMillis()
        val lastOk = stats?.lastOK ?: 0L
        val since = stats?.since ?: 0L
        val isFailing = now - since > WG_UPTIME_THRESHOLD && lastOk == 0L
        return when (status) {
            UIUtils.ProxyStatus.TOK -> if (isFailing) R.attr.chipTextNeutral else R.attr.accentGood
            UIUtils.ProxyStatus.TUP,
            UIUtils.ProxyStatus.TZZ,
            UIUtils.ProxyStatus.TNT -> R.attr.chipTextNeutral
            else -> R.attr.chipTextNegative
        }
    }

    private fun getStatusText(
        status: UIUtils.ProxyStatus?,
        handshakeTime: String? = null,
        stats: RouterStats?,
        errMsg: String? = null
    ): String {
        if (status == null) {
            val txt =
                if (errMsg != null) {
                    context.getString(R.string.status_waiting) + " ($errMsg)"
                } else {
                    context.getString(R.string.status_waiting)
                }
            return txt.replaceFirstChar(Char::titlecase)
        }

        val now = System.currentTimeMillis()
        val lastOk = stats?.lastOK ?: 0L
        val since = stats?.since ?: 0L
        if (now - since > WG_UPTIME_THRESHOLD && lastOk == 0L) {
            return context.getString(R.string.status_failing).replaceFirstChar(Char::titlecase)
        }

        val baseText =
            context.getString(UIUtils.getProxyStatusStringRes(status.id))
                .replaceFirstChar(Char::titlecase)
        return if (stats?.lastOK != 0L && handshakeTime != null) {
            context.getString(R.string.about_version_install_source, baseText, handshakeTime)
        } else {
            baseText
        }
    }

    private fun getUpTime(stats: RouterStats?): CharSequence {
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

    private fun getRxTx(stats: RouterStats?): String {
        if (stats == null) return ""
        val rx =
            context.getString(
                R.string.symbol_download,
                Utilities.humanReadableByteCount(stats.rx, true)
            )
        val tx =
            context.getString(
                R.string.symbol_upload,
                Utilities.humanReadableByteCount(stats.tx, true)
            )
        return context.getString(R.string.two_argument_space, tx, rx)
    }

    private fun getHandshakeTime(stats: RouterStats?): CharSequence {
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

    private suspend fun enableWgIfPossible(config: WgConfigFiles): Boolean {
        if (!VpnController.hasTunnel()) {
            Napier.i(tag = TAG, message = "VPN not active, cannot enable WireGuard")
            withContext(Dispatchers.Main) {
                Utilities.showToastUiCentered(
                    context,
                    ERR_CODE_VPN_NOT_ACTIVE +
                        context.getString(R.string.settings_socks5_vpn_disabled_error),
                    Toast.LENGTH_LONG
                )
            }
            return false
        }

        if (!WireguardManager.canEnableProxy()) {
            Napier.i(tag = TAG, message = "not in DNS+Firewall mode, cannot enable WireGuard")
            withContext(Dispatchers.Main) {
                Utilities.showToastUiCentered(
                    context,
                    ERR_CODE_VPN_NOT_FULL + context.getString(R.string.wireguard_enabled_failure),
                    Toast.LENGTH_LONG
                )
            }
            return false
        }

        if (WireguardManager.oneWireGuardEnabled()) {
            Napier.i(tag = TAG, message = "one WireGuard is already enabled")
            withContext(Dispatchers.Main) {
                Utilities.showToastUiCentered(
                    context,
                    ERR_CODE_OTHER_WG_ACTIVE + context.getString(R.string.wireguard_enabled_failure),
                    Toast.LENGTH_LONG
                )
            }
            return false
        }

        if (!WireguardManager.isValidConfig(config.id)) {
            Napier.i(tag = TAG, message = "invalid WireGuard config")
            withContext(Dispatchers.Main) {
                Utilities.showToastUiCentered(
                    context,
                    ERR_CODE_WG_INVALID + context.getString(R.string.wireguard_enabled_failure),
                    Toast.LENGTH_LONG
                )
            }
            return false
        }

        Napier.i(tag = TAG, message = "enabling WireGuard, id: ${config.id}")
        WireguardManager.enableConfig(config.toImmutable())
        withContext(Dispatchers.Main) { listener.onDnsStatusChanged() }
        logEvent("WireGuard enabled", "WG ID: ${config.id}")
        return true
    }

    private suspend fun disableWgIfPossible(config: WgConfigFiles): Boolean {
        val canDisable = WireguardManager.canDisableConfig(config.toImmutable())
        if (!canDisable) {
            val msgRes =
                if (WgHopManager.isWgEitherHopOrSrc(config.id)) {
                    R.string.wireguard_disable_failure_relay
                } else {
                    R.string.wireguard_disable_failure
                }
            withContext(Dispatchers.Main) {
                Utilities.showToastUiCentered(context, context.getString(msgRes), Toast.LENGTH_LONG)
            }
            return false
        }

        Napier.i(tag = TAG, message = "disabling WireGuard, id: ${config.id}")
        WireguardManager.disableConfig(config.toImmutable())
        withContext(Dispatchers.Main) { listener.onDnsStatusChanged() }
        logEvent("WireGuard disabled", "WG ID: ${config.id}")
        return true
    }

    private fun launchConfigDetail(id: Int) {
        if (!VpnController.hasTunnel()) {
            Utilities.showToastUiCentered(
                context,
                context.getString(R.string.ssv_toast_start_rethink),
                Toast.LENGTH_SHORT
            )
            return
        }

        val intent = Intent(context, WgConfigDetailActivity::class.java)
        intent.putExtra(INTENT_EXTRA_WG_ID, id)
        intent.putExtra(INTENT_EXTRA_WG_TYPE, WgConfigDetailActivity.WgType.DEFAULT.value)
        context.startActivity(intent)
    }

    private fun logEvent(msg: String, details: String) {
        eventLogger.log(EventType.PROXY_SWITCH, Severity.LOW, msg, EventSource.UI, false, details)
    }
}
