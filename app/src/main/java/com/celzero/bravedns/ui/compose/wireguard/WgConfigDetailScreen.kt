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
package com.celzero.bravedns.ui.compose.wireguard

import android.app.Activity
import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.WgIncludeAppsAdapter
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
import com.celzero.bravedns.ui.dialog.WgAddPeerDialog
import com.celzero.bravedns.ui.dialog.WgHopDialog
import com.celzero.bravedns.ui.dialog.WgIncludeAppsDialog
import com.celzero.bravedns.ui.dialog.WgSsidDialog
import com.celzero.bravedns.util.SsidPermissionManager
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.ProxyAppsMappingViewModel
import com.celzero.bravedns.wireguard.Config
import com.celzero.bravedns.wireguard.Peer
import com.celzero.bravedns.wireguard.WgHopManager
import com.celzero.firestack.backend.RouterStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WgConfigDetailScreen(
    configId: Int,
    wgType: WgType,
    persistentState: PersistentState,
    eventLogger: EventLogger,
    mappingViewModel: ProxyAppsMappingViewModel,
    onEditConfig: (Int, WgType) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State variables
    var configFiles by remember { mutableStateOf<WgConfigFilesImmutable?>(null) }
    var config by remember { mutableStateOf<Config?>(null) }
    var peers by remember { mutableStateOf<List<Peer>>(emptyList()) }
    var statusText by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf<Int?>(null) }
    var catchAllEnabled by remember { mutableStateOf(false) }
    var useMobileEnabled by remember { mutableStateOf(false) }
    var ssidEnabled by remember { mutableStateOf(false) }
    var ssids by remember { mutableStateOf<List<SsidItem>>(emptyList()) }
    var showInvalidConfigDialog by remember { mutableStateOf(false) }
    var showDeleteInterfaceDialog by remember { mutableStateOf(false) }
    var showSsidPermissionDialog by remember { mutableStateOf(false) }
    var showAddPeerDialog by remember { mutableStateOf(false) }
    var showHopDialog by remember { mutableStateOf(false) }
    var hopDialogConfigs by remember { mutableStateOf<List<Config>>(emptyList()) }
    var hopDialogSelectedId by remember { mutableStateOf(INVALID_CONF_ID) }
    var showSsidDialog by remember { mutableStateOf(false) }
    var ssidDialogCurrent by remember { mutableStateOf("") }
    var showIncludeAppsDialog by remember { mutableStateOf(false) }
    var includeAppsAdapter by remember { mutableStateOf<WgIncludeAppsAdapter?>(null) }
    var includeAppsProxyId by remember { mutableStateOf("") }
    var includeAppsProxyName by remember { mutableStateOf("") }

    val appsCount by mappingViewModel
        .getAppCountById(ID_WG_BASE + configId)
        .asFlow()
        .collectAsState(initial = 0)

    // Refresh config on launch
    LaunchedEffect(configId) {
        val cfg = WireguardManager.getConfigById(configId)
        val mapping = WireguardManager.getConfigFilesById(configId)
        if (cfg == null || mapping == null) {
            showInvalidConfigDialog = true
            return@LaunchedEffect
        }
        config = cfg
        configFiles = mapping
        peers = cfg.getPeers() ?: emptyList()
        catchAllEnabled = mapping.isCatchAll
        useMobileEnabled = mapping.useOnlyOnMetered
        ssidEnabled = mapping.ssidEnabled
        ssids = SsidItem.parseStorageList(mapping.ssids)
    }

    // Update status UI when config changes
    LaunchedEffect(configFiles?.isActive, configFiles?.id) {
        updateStatusUi(
            context = context,
            id = configId,
            persistentState = persistentState,
            onStatusUpdate = { text, color ->
                statusText = text
                statusColor = color
            }
        )
    }

    // Helper functions
    fun logEvent(msg: String, details: String) {
        eventLogger.log(
            type = EventType.PROXY_SWITCH,
            severity = Severity.LOW,
            message = msg,
            source = EventSource.MANAGER,
            userAction = true,
            details = details
        )
    }

    suspend fun refreshConfig() {
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

    fun updateUseOnMobileNetwork(enabled: Boolean) {
        useMobileEnabled = enabled
        scope.launch(Dispatchers.IO) {
            WireguardManager.updateUseOnMobileNetworkConfig(configId, enabled)
        }
        logEvent(
            "WireGuard Use on Mobile Networks",
            "User ${if (enabled) "enabled" else "disabled"} use on mobile networks for WireGuard config with id $configId"
        )
    }

    fun updateCatchAll(enabled: Boolean) {
        catchAllEnabled = enabled
        scope.launch(Dispatchers.IO) {
            if (!VpnController.hasTunnel()) {
                withContext(Dispatchers.Main) {
                    catchAllEnabled = !enabled
                    Utilities.showToastUiCentered(
                        context,
                        ERR_CODE_VPN_NOT_ACTIVE + context.getString(R.string.settings_socks5_vpn_disabled_error),
                        Toast.LENGTH_LONG
                    )
                }
                return@launch
            }

            if (!WireguardManager.canEnableProxy()) {
                withContext(Dispatchers.Main) {
                    catchAllEnabled = false
                    Utilities.showToastUiCentered(
                        context,
                        ERR_CODE_VPN_NOT_FULL + context.getString(R.string.wireguard_enabled_failure),
                        Toast.LENGTH_LONG
                    )
                }
                return@launch
            }

            if (WireguardManager.oneWireGuardEnabled()) {
                withContext(Dispatchers.Main) {
                    catchAllEnabled = false
                    Utilities.showToastUiCentered(
                        context,
                        ERR_CODE_OTHER_WG_ACTIVE + context.getString(R.string.wireguard_enabled_failure),
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
                        context,
                        ERR_CODE_WG_INVALID + context.getString(R.string.wireguard_enabled_failure),
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

    fun toggleSsid(enabled: Boolean) {
        ssidEnabled = enabled
        val activity = context as? Activity
        if (activity == null || !SsidPermissionManager.hasRequiredPermissions(activity) || !SsidPermissionManager.isLocationEnabled(activity)) {
            ssidEnabled = false
            showSsidPermissionDialog = true
            return
        }
        scope.launch(Dispatchers.IO) {
            WireguardManager.updateSsidEnabled(configId, enabled)
        }
    }

    fun openAppsDialog(proxyName: String) {
        val proxyId = ID_WG_BASE + configId
        includeAppsAdapter = WgIncludeAppsAdapter(context, proxyId, proxyName)
        includeAppsProxyId = proxyId
        includeAppsProxyName = proxyName
        showIncludeAppsDialog = true
    }

    fun openHopDialog() {
        scope.launch(Dispatchers.IO) {
            val hopables = WgHopManager.getHopableWgs(configId)
            val selectedId = convertStringIdToId(WgHopManager.getHop(configId))
            withContext(Dispatchers.Main) {
                hopDialogConfigs = hopables
                hopDialogSelectedId = selectedId
                showHopDialog = true
            }
        }
    }

    fun openSsidDialog() {
        ssidDialogCurrent = WireguardManager.getConfigFilesById(configId)?.ssids.orEmpty()
        showSsidDialog = true
    }

    // Dialogs
    if (showInvalidConfigDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(text = stringResource(R.string.lbl_wireguard)) },
            text = { Text(text = stringResource(R.string.config_invalid_desc)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showInvalidConfigDialog = false
                            onBackClick()
                        }
                    ) {
                        Text(text = stringResource(R.string.fapps_info_dialog_positive_btn))
                    }
                    TextButton(
                        onClick = {
                            showInvalidConfigDialog = false
                            WireguardManager.deleteConfig(configId)
                        }
                    ) {
                        Text(text = stringResource(R.string.lbl_delete))
                    }
                }
            }
        )
    }

    if (showDeleteInterfaceDialog) {
        val delText = stringResource(
            R.string.two_argument_space,
            stringResource(R.string.config_delete_dialog_title),
            stringResource(R.string.lbl_wireguard)
        )
        AlertDialog(
            onDismissRequest = { showDeleteInterfaceDialog = false },
            title = { Text(text = delText) },
            text = { Text(text = stringResource(R.string.config_delete_dialog_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteInterfaceDialog = false
                        scope.launch(Dispatchers.IO) {
                            WireguardManager.deleteConfig(configId)
                            withContext(Dispatchers.Main) {
                                Utilities.showToastUiCentered(
                                    context,
                                    context.getString(R.string.config_add_success_toast),
                                    Toast.LENGTH_SHORT
                                )
                                onBackClick()
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
                    Text(text = stringResource(R.string.lbl_cancel))
                }
            }
        )
    }

    if (showSsidPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showSsidPermissionDialog = false },
            title = { Text(text = stringResource(R.string.lbl_ssid)) },
            text = { Text(text = SsidPermissionManager.getPermissionExplanation(context)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSsidPermissionDialog = false
                        val activity = context as? Activity
                        if (activity != null) {
                            SsidPermissionManager.requestSsidPermissions(activity)
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.fapps_info_dialog_positive_btn))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSsidPermissionDialog = false
                        scope.launch(Dispatchers.IO) {
                            WireguardManager.updateSsidEnabled(configId, false)
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.lbl_cancel))
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
                scope.launch(Dispatchers.IO) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.lbl_wireguard)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (config == null || configFiles == null) {
                Text(text = stringResource(id = R.string.config_invalid_desc))
                Button(onClick = onBackClick) {
                    Text(text = stringResource(id = R.string.fapps_info_dialog_positive_btn))
                }
                return@Scaffold
            }

            // Config info card
            Card(colors = CardDefaults.cardColors()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = config?.getName().orEmpty(),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.single_argument_parenthesis, configId.toString()),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = statusText,
                        color = statusColor?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (wgType.isOneWg()) {
                Text(text = stringResource(id = R.string.one_wg_apps_added))
            }

            // Action buttons row 1
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { showAddPeerDialog = true }) {
                    Text(text = stringResource(id = R.string.lbl_add))
                }
                Button(onClick = { showDeleteInterfaceDialog = true }) {
                    Text(text = stringResource(id = R.string.lbl_delete))
                }
                Button(onClick = { onEditConfig(configId, wgType) }) {
                    Text(text = stringResource(id = R.string.rt_edit_dialog_positive))
                }
            }

            // Action buttons row 2 (only for default wg type)
            if (wgType.isDefault()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { openAppsDialog(config?.getName().orEmpty()) },
                        enabled = !catchAllEnabled
                    ) {
                        Text(text = stringResource(R.string.add_remove_apps, appsCount.toString()))
                    }
                    Button(onClick = { openHopDialog() }) {
                        Text(text = stringResource(id = R.string.hop_add_remove_title))
                    }
                }
            }

            // Catch all toggle
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.catch_all_wg_dialog_title))
                    Text(
                        text = stringResource(id = R.string.catch_all_wg_dialog_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = catchAllEnabled,
                    onCheckedChange = { enabled -> updateCatchAll(enabled) }
                )
            }

            // Use on mobile network toggle
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.wg_setting_use_on_mobile))
                    Text(
                        text = stringResource(id = R.string.wg_setting_use_on_mobile_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = useMobileEnabled,
                    onCheckedChange = { enabled -> updateUseOnMobileNetwork(enabled) }
                )
            }

            // SSID settings (only if device supported)
            if (SsidPermissionManager.isDeviceSupported(context)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(id = R.string.wg_setting_ssid_title))
                        Text(
                            text = stringResource(
                                id = R.string.wg_setting_ssid_desc,
                                stringResource(id = R.string.lbl_ssids)
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (ssids.isNotEmpty()) {
                            Text(
                                text = ssids.joinToString { it.name },
                                style = MaterialTheme.typography.bodySmall
                            )
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

            // Peers section
            Text(
                text = stringResource(id = R.string.lbl_peer),
                style = MaterialTheme.typography.titleMedium
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                peers.forEach { peer ->
                    WgPeerRow(
                        context = context,
                        configId = configId,
                        wgPeer = peer,
                        onPeerChanged = { scope.launch { refreshConfig() } }
                    )
                }
            }

            // Add some bottom spacing
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private suspend fun updateStatusUi(
    context: Context,
    id: Int,
    persistentState: PersistentState,
    onStatusUpdate: (String, Int?) -> Unit
) {
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
                val text = context.getString(R.string.status_failing)
                    .replaceFirstChar(Char::titlecase)
                val color = UIUtils.fetchColor(context, R.attr.chipTextNegative)
                onStatusUpdate(text, color)
                return@withContext
            }
            val text = getStatusText(context, ps, getHandshakeTime(stats).toString(), stats, statusPair.second)
            val color = UIUtils.fetchColor(context, getStrokeColorForStatus(ps, stats))
            onStatusUpdate(text, color)
        }
    } else {
        withContext(Dispatchers.Main) {
            val text = context.getString(R.string.lbl_disabled).replaceFirstChar(Char::titlecase)
            onStatusUpdate(text, null)
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
    context: Context,
    status: UIUtils.ProxyStatus?,
    handshakeTime: String? = null,
    stats: RouterStats?,
    errMsg: String? = null
): String {
    if (status == null) {
        val txt = if (!errMsg.isNullOrEmpty()) {
            context.getString(R.string.status_waiting) + " ($errMsg)"
        } else {
            context.getString(R.string.status_waiting)
        }
        return txt.replaceFirstChar(Char::titlecase)
    }

    if (status == UIUtils.ProxyStatus.TPU) {
        return context.getString(UIUtils.getProxyStatusStringRes(status.id))
            .replaceFirstChar(Char::titlecase)
    }

    val now = System.currentTimeMillis()
    val lastOk = stats?.lastOK ?: 0L
    val since = stats?.since ?: 0L
    if (now - since > WG_UPTIME_THRESHOLD && lastOk == 0L) {
        return context.getString(R.string.status_failing).replaceFirstChar(Char::titlecase)
    }

    val baseText = context.getString(UIUtils.getProxyStatusStringRes(status.id))
        .replaceFirstChar(Char::titlecase)

    return if (stats?.lastOK != 0L && handshakeTime != null) {
        context.getString(R.string.about_version_install_source, baseText, handshakeTime)
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

private fun convertStringIdToId(id: String): Int {
    return try {
        id.removePrefix(ID_WG_BASE).toIntOrNull() ?: INVALID_CONF_ID
    } catch (_: Exception) {
        INVALID_CONF_ID
    }
}
