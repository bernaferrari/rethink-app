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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.WgPeerRow
import com.celzero.bravedns.data.SsidItem
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.database.WgConfigFilesImmutable
import com.celzero.bravedns.net.doh.Transaction
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager.ID_WG_BASE
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.service.WireguardManager.ERR_CODE_OTHER_WG_ACTIVE
import com.celzero.bravedns.service.WireguardManager.ERR_CODE_VPN_NOT_ACTIVE
import com.celzero.bravedns.service.WireguardManager.ERR_CODE_VPN_NOT_FULL
import com.celzero.bravedns.service.WireguardManager.ERR_CODE_WG_INVALID
import com.celzero.bravedns.service.WireguardManager.INVALID_CONF_ID
import com.celzero.bravedns.service.WireguardManager.WG_UPTIME_THRESHOLD
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.dialog.WgAddPeerDialog
import com.celzero.bravedns.ui.dialog.WgHopDialog
import com.celzero.bravedns.ui.dialog.WgIncludeAppsDialog
import com.celzero.bravedns.ui.dialog.WgSsidDialog
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.SsidPermissionManager
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.tos
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.ProxyAppsMappingViewModel
import com.celzero.bravedns.wireguard.Config
import com.celzero.bravedns.wireguard.Peer
import com.celzero.bravedns.wireguard.WgHopManager
import com.celzero.firestack.backend.RouterStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class WgConfigDetailActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val eventLogger by inject<EventLogger>()

    private val mappingViewModel: ProxyAppsMappingViewModel by viewModel()

    private var configId: Int = INVALID_CONF_ID
    private var wgType: WgType = WgType.DEFAULT

    private var configFiles by mutableStateOf<WgConfigFilesImmutable?>(null)
    private var config by mutableStateOf<Config?>(null)
    private var peers by mutableStateOf<List<Peer>>(emptyList())
    private var statusText by mutableStateOf("")
    private var statusColor by mutableStateOf<Int?>(null)
    private var catchAllEnabled by mutableStateOf(false)
    private var useMobileEnabled by mutableStateOf(false)
    private var ssidEnabled by mutableStateOf(false)
    private var ssids by mutableStateOf<List<SsidItem>>(emptyList())
    private var showInvalidConfigDialog by mutableStateOf(false)
    private var showDeleteInterfaceDialog by mutableStateOf(false)
    private var showSsidPermissionDialog by mutableStateOf(false)
    private var showAddPeerDialog by mutableStateOf(false)
    private var showHopDialog by mutableStateOf(false)
    private var hopDialogConfigs by mutableStateOf<List<com.celzero.bravedns.wireguard.Config>>(emptyList())
    private var hopDialogSelectedId by mutableStateOf(INVALID_CONF_ID)
    private var showSsidDialog by mutableStateOf(false)
    private var ssidDialogCurrent by mutableStateOf("")
    private var showIncludeAppsDialog by mutableStateOf(false)
    private var includeAppsAdapter by mutableStateOf<com.celzero.bravedns.adapter.WgIncludeAppsAdapter?>(null)
    private var includeAppsProxyId by mutableStateOf("")
    private var includeAppsProxyName by mutableStateOf("")

    companion object {
        private const val CLIPBOARD_PUBLIC_KEY_LBL = "Public Key"
        const val INTENT_EXTRA_WG_TYPE = "WIREGUARD_TUNNEL_TYPE"
    }

    enum class WgType(val value: Int) {
        DEFAULT(0),
        ONE_WG(1);

        fun isOneWg() = this == ONE_WG

        fun isDefault() = this == DEFAULT

        companion object {
            fun fromInt(value: Int): WgType {
                return entries.firstOrNull { it.value == value }
                    ?: throw IllegalArgumentException("Invalid WgType value: $value")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }
        configId = intent.getIntExtra(WgConfigEditorActivity.INTENT_EXTRA_WG_ID, INVALID_CONF_ID)
        wgType = WgType.fromInt(intent.getIntExtra(INTENT_EXTRA_WG_TYPE, WgType.DEFAULT.value))

        setContent {
            RethinkTheme {
                WgConfigDetailContent()
            }
        }
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @Composable
    private fun WgConfigDetailContent() {
        val scope = rememberCoroutineScope()
        val appsCount by
            mappingViewModel
                .getAppCountById(ID_WG_BASE + configId)
                .asFlow()
                .collectAsState(initial = 0)
        LaunchedEffect(configId) {
            refreshConfig()
        }

        LaunchedEffect(configFiles?.isActive, configFiles?.id) {
            updateStatusUi(configId)
        }

        if (showInvalidConfigDialog) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(text = getString(R.string.lbl_wireguard)) },
                text = { Text(text = getString(R.string.config_invalid_desc)) },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                showInvalidConfigDialog = false
                                finish()
                            }
                        ) {
                            Text(text = getString(R.string.fapps_info_dialog_positive_btn))
                        }
                        TextButton(
                            onClick = {
                                showInvalidConfigDialog = false
                                WireguardManager.deleteConfig(configId)
                            }
                        ) {
                            Text(text = getString(R.string.lbl_delete))
                        }
                    }
                }
            )
        }

        if (showDeleteInterfaceDialog) {
            val delText =
                getString(
                    R.string.two_argument_space,
                    getString(R.string.config_delete_dialog_title),
                    getString(R.string.lbl_wireguard)
                )
            AlertDialog(
                onDismissRequest = { showDeleteInterfaceDialog = false },
                title = { Text(text = delText) },
                text = { Text(text = getString(R.string.config_delete_dialog_desc)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteInterfaceDialog = false
                            lifecycleScope.launch(Dispatchers.IO) {
                                WireguardManager.deleteConfig(configId)
                                withContext(Dispatchers.Main) {
                                    Utilities.showToastUiCentered(
                                        this@WgConfigDetailActivity,
                                        getString(R.string.config_add_success_toast),
                                        Toast.LENGTH_SHORT
                                    )
                                    finish()
                                }
                                logEvent(
                                    "Delete WireGuard config",
                                    "User deleted WireGuard config with id $configId"
                                )
                            }
                        }
                    ) {
                        Text(text = delText)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteInterfaceDialog = false }) {
                        Text(text = getString(R.string.lbl_cancel))
                    }
                }
            )
        }

        if (showSsidPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showSsidPermissionDialog = false },
                title = { Text(text = getString(R.string.lbl_ssid)) },
                text = { Text(text = SsidPermissionManager.getPermissionExplanation(this@WgConfigDetailActivity)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSsidPermissionDialog = false
                            SsidPermissionManager.requestSsidPermissions(this@WgConfigDetailActivity)
                        }
                    ) {
                        Text(text = getString(R.string.fapps_info_dialog_positive_btn))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSsidPermissionDialog = false
                            lifecycleScope.launch(Dispatchers.IO) {
                                WireguardManager.updateSsidEnabled(configId, false)
                            }
                        }
                    ) {
                        Text(text = getString(R.string.lbl_cancel))
                    }
                }
            )
        }

        if (showAddPeerDialog) {
            WgAddPeerDialog(
                configId = configId,
                wgPeer = null,
                onDismiss = {
                    showAddPeerDialog = false
                    scope.launch { refreshConfig() }
                }
            )
        }

        if (showHopDialog) {
            WgHopDialog(
                srcId = configId,
                hopables = hopDialogConfigs,
                selectedId = hopDialogSelectedId,
                onDismiss = { showHopDialog = false }
            )
        }

        if (showSsidDialog) {
            WgSsidDialog(
                currentSsids = ssidDialogCurrent,
                onSave = { newSsids ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        WireguardManager.updateSsids(configId, newSsids)
                        val cfg = WireguardManager.getConfigFilesById(configId)
                        withContext(Dispatchers.Main) {
                            ssids = SsidItem.parseStorageList(cfg?.ssids ?: "")
                        }
                    }
                },
                onDismiss = { showSsidDialog = false }
            )
        }

        val appsAdapter = includeAppsAdapter
        if (showIncludeAppsDialog && appsAdapter != null) {
            WgIncludeAppsDialog(
                adapter = appsAdapter,
                viewModel = mappingViewModel,
                proxyId = includeAppsProxyId,
                proxyName = includeAppsProxyName,
                onDismiss = { showIncludeAppsDialog = false }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (config == null || configFiles == null) {
                Text(text = stringResource(id = R.string.config_invalid_desc))
                Button(onClick = { finish() }) { Text(text = stringResource(id = R.string.fapps_info_dialog_positive_btn)) }
                return@Column
            }

            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = config?.getName().orEmpty(), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = getString(R.string.single_argument_parenthesis, configId.toString()),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(text = statusText, color = statusColor?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface)
                }
            }

            if (wgType.isOneWg()) {
                Text(text = stringResource(id = R.string.one_wg_apps_added))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { showAddPeerDialog = true }) {
                    Text(text = stringResource(id = R.string.lbl_add))
                }
                Button(onClick = { showDeleteInterfaceDialog = true }) {
                    Text(text = stringResource(id = R.string.lbl_delete))
                }
                Button(onClick = { openEditConfig() }) {
                    Text(text = stringResource(id = R.string.rt_edit_dialog_positive))
                }
            }

            if (wgType.isDefault()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { openAppsDialog(config?.getName().orEmpty()) }, enabled = !catchAllEnabled) {
                        Text(text = getString(R.string.add_remove_apps, appsCount.toString()))
                    }
                    Button(onClick = { openHopDialog() }) {
                        Text(text = stringResource(id = R.string.hop_add_remove_title))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.catch_all_wg_dialog_title))
                    Text(text = stringResource(id = R.string.catch_all_wg_dialog_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = catchAllEnabled,
                    onCheckedChange = { enabled -> updateCatchAll(enabled) }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.wg_setting_use_on_mobile))
                    Text(text = stringResource(id = R.string.wg_setting_use_on_mobile_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = useMobileEnabled,
                    onCheckedChange = { enabled -> updateUseOnMobileNetwork(enabled) }
                )
            }

            if (SsidPermissionManager.isDeviceSupported(this@WgConfigDetailActivity)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(id = R.string.wg_setting_ssid_title))
                        Text(text = stringResource(id = R.string.wg_setting_ssid_desc, stringResource(id = R.string.lbl_ssids)), style = MaterialTheme.typography.bodySmall)
                        if (ssids.isNotEmpty()) {
                            Text(text = ssids.joinToString { it.name }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Switch(
                        checked = ssidEnabled,
                        onCheckedChange = { enabled -> toggleSsid(enabled) }
                    )
                }
                Button(onClick = { openSsidDialog() }) {
                    Text(text = stringResource(id = R.string.rt_edit_dialog_positive))
                }
            }

            Text(text = stringResource(id = R.string.lbl_peer), style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(peers) { peer ->
                    WgPeerRow(
                        context = this@WgConfigDetailActivity,
                        configId = configId,
                        wgPeer = peer,
                        onPeerChanged = { scope.launch { refreshConfig() } }
                    )
                }
            }
        }
    }

    private suspend fun refreshConfig() {
        val cfg = WireguardManager.getConfigById(configId)
        val mapping = WireguardManager.getConfigFilesById(configId)
        if (cfg == null || mapping == null) {
            showInvalidConfigDialog = true
            return
        }
        config = cfg
        configFiles = mapping
        peers = cfg.getPeers() ?: emptyList()
        catchAllEnabled = mapping.isCatchAll
        useMobileEnabled = mapping.useOnlyOnMetered
        ssidEnabled = mapping.ssidEnabled
        ssids = SsidItem.parseStorageList(mapping.ssids)
    }

    private suspend fun updateStatusUi(id: Int) {
        val mapping = WireguardManager.getConfigFilesById(id)
        val cid = ID_WG_BASE + id
        if (mapping?.isActive == true) {
            val statusPair = VpnController.getProxyStatusById(cid)
            val stats = VpnController.getProxyStats(cid)
            val ps = UIUtils.ProxyStatus.entries.find { it.id == statusPair.first }
            val dnsStatusId = if (persistentState.splitDns) {
                VpnController.getDnsStatus(cid)
            } else {
                null
            }
            withContext(Dispatchers.Main) {
                if (dnsStatusId != null && isDnsError(dnsStatusId)) {
                    statusText = getString(R.string.status_failing).replaceFirstChar(Char::titlecase)
                    statusColor = UIUtils.fetchColor(this@WgConfigDetailActivity, R.attr.chipTextNegative)
                    return@withContext
                }
                statusText = getStatusText(ps, getHandshakeTime(stats).toString(), stats, statusPair.second)
                statusColor = UIUtils.fetchColor(this@WgConfigDetailActivity, getStrokeColorForStatus(ps, stats))
            }
        } else {
            withContext(Dispatchers.Main) {
                statusText = getString(R.string.lbl_disabled).replaceFirstChar(Char::titlecase)
                statusColor = null
            }
        }
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

    private fun getStatusText(
        status: UIUtils.ProxyStatus?,
        handshakeTime: String? = null,
        stats: RouterStats?,
        errMsg: String? = null
    ): String {
        if (status == null) {
            val txt = if (!errMsg.isNullOrEmpty()) {
                getString(R.string.status_waiting) + " ($errMsg)"
            } else {
                getString(R.string.status_waiting)
            }
            return txt.replaceFirstChar(Char::titlecase)
        }

        if (status == UIUtils.ProxyStatus.TPU) {
            return getString(UIUtils.getProxyStatusStringRes(status.id))
                .replaceFirstChar(Char::titlecase)
        }

        val now = System.currentTimeMillis()
        val lastOk = stats?.lastOK ?: 0L
        val since = stats?.since ?: 0L
        if (now - since > WG_UPTIME_THRESHOLD && lastOk == 0L) {
            return getString(R.string.status_failing).replaceFirstChar(Char::titlecase)
        }

        val baseText = getString(UIUtils.getProxyStatusStringRes(status.id))
            .replaceFirstChar(Char::titlecase)

        return if (stats?.lastOK != 0L && handshakeTime != null) {
            getString(R.string.about_version_install_source, baseText, handshakeTime)
        } else {
            baseText
        }
    }

    private fun getHandshakeTime(stats: RouterStats?): CharSequence {
        if (stats == null) {
            return ""
        }
        if (stats.lastOK == 0L) {
            return ""
        }
        val now = System.currentTimeMillis()
        return DateUtils.getRelativeTimeSpanString(
            stats.lastOK,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )
    }

    private fun getStrokeColorForStatus(status: UIUtils.ProxyStatus?, stats: RouterStats?): Int {
        val now = System.currentTimeMillis()
        val lastOk = stats?.lastOK ?: 0L
        val since = stats?.since ?: 0L
        val isFailing = now - since > WG_UPTIME_THRESHOLD && lastOk == 0L
        return when (status) {
            UIUtils.ProxyStatus.TOK -> if (isFailing) R.attr.chipTextNeutral else R.attr.accentGood
            UIUtils.ProxyStatus.TUP, UIUtils.ProxyStatus.TZZ, UIUtils.ProxyStatus.TNT -> R.attr.chipTextNeutral
            else -> R.attr.chipTextNegative
        }
    }

    private fun updateUseOnMobileNetwork(enabled: Boolean) {
        useMobileEnabled = enabled
        lifecycleScope.launch(Dispatchers.IO) {
            WireguardManager.updateUseOnMobileNetworkConfig(configId, enabled)
        }
        logEvent(
            "WireGuard Use on Mobile Networks",
            "User ${if (enabled) "enabled" else "disabled"} use on mobile networks for WireGuard config with id $configId"
        )
    }

    private fun updateCatchAll(enabled: Boolean) {
        catchAllEnabled = enabled
        lifecycleScope.launch(Dispatchers.IO) {
            if (!VpnController.hasTunnel()) {
                withContext(Dispatchers.Main) {
                    catchAllEnabled = !enabled
                    Utilities.showToastUiCentered(
                        this@WgConfigDetailActivity,
                        ERR_CODE_VPN_NOT_ACTIVE + getString(R.string.settings_socks5_vpn_disabled_error),
                        Toast.LENGTH_LONG
                    )
                }
                return@launch
            }

            if (!WireguardManager.canEnableProxy()) {
                withContext(Dispatchers.Main) {
                    catchAllEnabled = false
                    Utilities.showToastUiCentered(
                        this@WgConfigDetailActivity,
                        ERR_CODE_VPN_NOT_FULL + getString(R.string.wireguard_enabled_failure),
                        Toast.LENGTH_LONG
                    )
                }
                return@launch
            }

            if (WireguardManager.oneWireGuardEnabled()) {
                withContext(Dispatchers.Main) {
                    catchAllEnabled = false
                    Utilities.showToastUiCentered(
                        this@WgConfigDetailActivity,
                        ERR_CODE_OTHER_WG_ACTIVE + getString(R.string.wireguard_enabled_failure),
                        Toast.LENGTH_LONG
                    )
                }
                return@launch
            }

            val cfg = WireguardManager.getConfigFilesById(configId)
            if (cfg == null) {
                withContext(Dispatchers.Main) {
                    catchAllEnabled = false
                    Utilities.showToastUiCentered(
                        this@WgConfigDetailActivity,
                        ERR_CODE_WG_INVALID + getString(R.string.wireguard_enabled_failure),
                        Toast.LENGTH_LONG
                    )
                }
                return@launch
            }

            WireguardManager.updateCatchAllConfig(configId, enabled)
            logEvent(
                "WireGuard Catch All apps",
                "User ${if (enabled) "enabled" else "disabled"} catch all apps for WireGuard config with id $configId"
            )
        }
    }

    private fun toggleSsid(enabled: Boolean) {
        ssidEnabled = enabled
        if (!SsidPermissionManager.hasRequiredPermissions(this) || !SsidPermissionManager.isLocationEnabled(this)) {
            ssidEnabled = false
            showSsidPermissionDialog = true
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            WireguardManager.updateSsidEnabled(configId, enabled)
        }
    }

    private fun openAppsDialog(proxyName: String) {
        val proxyId = ID_WG_BASE + configId
        includeAppsAdapter = com.celzero.bravedns.adapter.WgIncludeAppsAdapter(this, proxyId, proxyName)
        includeAppsProxyId = proxyId
        includeAppsProxyName = proxyName
        showIncludeAppsDialog = true
    }

    private fun openHopDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val hopables = WgHopManager.getHopableWgs(configId)
            val selectedId = convertStringIdToId(WgHopManager.getHop(configId))
            withContext(Dispatchers.Main) {
                hopDialogConfigs = hopables
                hopDialogSelectedId = selectedId
                showHopDialog = true
            }
        }
    }

    private fun convertStringIdToId(id: String): Int {
        return try {
            id.removePrefix(ID_WG_BASE).toIntOrNull() ?: INVALID_CONF_ID
        } catch (_: Exception) {
            INVALID_CONF_ID
        }
    }

    private fun openEditConfig() {
        val intent = Intent(this, WgConfigEditorActivity::class.java)
        intent.putExtra(WgConfigEditorActivity.INTENT_EXTRA_WG_ID, configId)
        intent.putExtra(INTENT_EXTRA_WG_TYPE, wgType.value)
        startActivity(intent)
    }

    private fun openSsidDialog() {
        ssidDialogCurrent = WireguardManager.getConfigFilesById(configId)?.ssids.orEmpty()
        showSsidDialog = true
    }

    private fun logEvent(msg: String, details: String) {
        eventLogger.log(
            type = EventType.PROXY_SWITCH,
            severity = Severity.LOW,
            message = msg,
            source = EventSource.MANAGER,
            userAction = true,
            details = details
        )
    }
}
