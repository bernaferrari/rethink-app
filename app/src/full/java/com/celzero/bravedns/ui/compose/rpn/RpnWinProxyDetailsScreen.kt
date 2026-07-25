/*
 * Copyright 2025 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.rpn

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import com.celzero.bravedns.database.CountryConfig
import com.celzero.bravedns.data.SsidItem
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.util.Utilities
import Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpnWinProxyDetailsScreen(
    countryCode: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val title = stringResource(R.string.rpn_proxy_details_title)
    val noProxyTitle = stringResource(R.string.rpn_no_proxy_found_title)
    val noProxyDesc = stringResource(R.string.rpn_no_proxy_found_desc)
    val selectAppsLabel = stringResource(R.string.rpn_select_apps_for_proxy)
    val appsInfoToast = stringResource(R.string.rpn_proxy_apps_info_toast)
    var appsCount by remember { mutableStateOf("-") }
    var domainsCount by remember { mutableStateOf("-") }
    var ipsCount by remember { mutableStateOf("-") }
    var proxyError by remember { mutableStateOf("") }
    var proxyName by remember { mutableStateOf("") }
    var proxyWho by remember { mutableStateOf("") }
    var proxyLatencyMs by remember { mutableStateOf<Int?>(null) }
    var proxyLastConnectedMs by remember { mutableStateOf<Long?>(null) }
    var isProxyActive by remember { mutableStateOf(false) }
    var serverConfig by remember { mutableStateOf<CountryConfig?>(null) }
    var showNoProxyFoundDialog by remember { mutableStateOf(false) }
    var showSsidEditor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(countryCode) {
        if (countryCode.isEmpty()) {
            Logger.w(tag = TAG, message = "empty country code, showing dialog")
            showNoProxyFoundDialog = true
            return@LaunchedEffect
        }

        val loaded =
            withContext(Dispatchers.IO) {
            val appsByCountry = ProxyManager.getAppsCountForProxy(countryCode)
            val appsByWin = ProxyManager.getAppsCountForProxy(ProxyManager.ID_RPN_WIN)
            val apps = if (appsByCountry > 0) appsByCountry else appsByWin
            val ipCount = IpRulesManager.getRulesCountByCC(countryCode)
            val domainCount = DomainRulesManager.getRulesCountByCC(countryCode)
            val details = RpnProxyManager.getWinProxyDetails(countryCode)
            Logger.i(tag = TAG, message = "apps: $apps, ips: $ipCount, domains: $domainCount for country code: $countryCode, has details: ${details != null}")
            Triple(apps to domainCount to ipCount, details, details == null)
            }

        appsCount = loaded.first.first.first.toString()
        domainsCount = loaded.first.first.second.toString()
        ipsCount = loaded.first.second.toString()
        proxyName = loaded.second?.name.orEmpty()
        proxyWho = loaded.second?.who.orEmpty()
        proxyLatencyMs = loaded.second?.latencyMs
        proxyLastConnectedMs = loaded.second?.lastConnectedMs
        isProxyActive = loaded.second?.isActive == true
        serverConfig = withContext(Dispatchers.IO) {
            RpnProxyManager.getWinServers().firstOrNull {
                it.cc.equals(countryCode, ignoreCase = true) ||
                    it.key.equals(countryCode, ignoreCase = true)
            }
        }
        showNoProxyFoundDialog = loaded.third
    }

    val fallback = stringResource(R.string.symbol_hyphen)
    val latencyText = proxyLatencyMs?.let { stringResource(R.string.dns_query_latency, it.toString()) } ?: fallback
    val lastConnectedMs = proxyLastConnectedMs
    val lastConnectedText =
        if (lastConnectedMs == null || lastConnectedMs <= 0L) fallback
        else {
            val minutes = ((System.currentTimeMillis() - lastConnectedMs).coerceAtLeast(0L)) / 60_000L
            when {
                minutes < 1L -> stringResource(R.string.bubble_time_just_now)
                minutes < 60L -> stringResource(R.string.bubble_time_minutes_ago, minutes.toInt())
                else -> stringResource(R.string.bubble_time_hours_ago, (minutes / 60L).toInt())
            }
        }
    val statusText = if (isProxyActive) stringResource(R.string.rpn_proxy_connected) else stringResource(R.string.lbl_disabled)

    RethinkRpnWinProxyDetailsScreen(
        state = RethinkRpnWinProxyDetailsState(
            countryCode = countryCode,
            appsCount = appsCount,
            domainsCount = domainsCount,
            ipsCount = ipsCount,
            proxyError = proxyError,
            proxyName = proxyName,
            proxyWho = proxyWho,
            proxyLatency = latencyText,
            proxyLastConnected = lastConnectedText,
            proxyStatus = statusText,
            isProxyActive = isProxyActive,
            options = serverConfig?.let {
                RethinkRpnWinServerOptions(it.hopEnabled, it.catchAll, it.lockdown, it.mobileOnly, it.ssidBased)
            },
        ),
        strings = RethinkRpnWinProxyDetailsStrings(
            title = title,
            fallback = fallback,
            proxyName = stringResource(R.string.rpn_proxy_name),
            apps = stringResource(R.string.rpn_proxy_apps),
            domains = stringResource(R.string.rpn_proxy_domains),
            ips = stringResource(R.string.rpn_proxy_ips),
            who = stringResource(R.string.rpn_proxy_who),
            error = stringResource(R.string.rpn_proxy_error),
            latency = stringResource(R.string.rpn_proxy_latency),
            lastConnected = stringResource(R.string.rpn_proxy_last_connected),
            status = stringResource(R.string.rpn_proxy_status),
            serverOptions = stringResource(R.string.rpn_server_options_title),
            hop = stringResource(R.string.rpn_server_hop),
            catchAll = stringResource(R.string.rpn_server_catch_all),
            lockdown = stringResource(R.string.rpn_server_lockdown),
            mobileOnly = stringResource(R.string.rpn_server_mobile_only),
            wifiOnly = stringResource(R.string.rpn_server_wifi_only),
            editWifi = stringResource(R.string.rpn_server_edit_wifi),
            selectApps = selectAppsLabel,
        ),
        onBackClick = onBackClick,
        onHopChanged = { enabled -> serverConfig?.let { config ->
            serverConfig = config.copy(hopEnabled = enabled)
            scope.launch(Dispatchers.IO) { RpnProxyManager.setHopForWinServer(config.key, enabled) }
        } },
        onCatchAllChanged = { enabled -> serverConfig?.let { config ->
            serverConfig = config.copy(catchAll = enabled)
            scope.launch(Dispatchers.IO) { RpnProxyManager.setCatchAllForWinServer(config.key, enabled) }
        } },
        onLockdownChanged = { enabled -> serverConfig?.let { config ->
            serverConfig = config.copy(lockdown = enabled)
            scope.launch(Dispatchers.IO) { RpnProxyManager.setLockdownForWinServer(config.key, enabled) }
        } },
        onMobileOnlyChanged = { enabled -> serverConfig?.let { config ->
            serverConfig = config.copy(mobileOnly = enabled)
            scope.launch(Dispatchers.IO) { RpnProxyManager.setMobileOnlyForWinServer(config.key, enabled) }
        } },
        onSsidChanged = { enabled -> serverConfig?.let { config ->
            serverConfig = config.copy(ssidBased = enabled)
            scope.launch(Dispatchers.IO) { RpnProxyManager.setSsidEnabledForWinServer(config.key, enabled) }
        } },
        onEditSsids = { showSsidEditor = true },
        onSelectApps = { Utilities.showToastUiCentered(context, appsInfoToast, Toast.LENGTH_LONG) },
    )
    if (showNoProxyFoundDialog) {
        RethinkConfirmDialog(
            onDismissRequest = {},
            title = noProxyTitle,
            message = noProxyDesc,
            confirmText = stringResource(R.string.ada_noapp_dialog_positive),
            onConfirm = onBackClick,
        )
    }
    if (showSsidEditor) {
        val config = serverConfig
        if (config != null) SsidEditorDialog(
            initialValue = config.ssids,
            onDismiss = { showSsidEditor = false },
            onSave = { ssids ->
                showSsidEditor = false
                scope.launch(Dispatchers.IO) { RpnProxyManager.updateSsids(config.key, ssids) }
                serverConfig = config.copy(ssids = ssids)
            },
        )
    }
}

@Composable
private fun SsidEditorDialog(initialValue: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(initialValue) { mutableStateOf(SsidItem.parseStorageList(initialValue).joinToString("\n") { it.name }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rpn_server_wifi_networks_title)) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(stringResource(R.string.rpn_server_wifi_networks_hint)) }, minLines = 3) },
        confirmButton = { TextButton(onClick = {
            onSave(SsidItem.toStorageList(value.lines().map(String::trim).filter(String::isNotBlank).distinct().map {
                SsidItem(it, SsidItem.SsidType.EQUAL_WILDCARD)
            }))
        }) { Text(stringResource(R.string.lbl_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lbl_cancel)) } },
    )
}

private const val TAG = "RpnWinProxyDetails"
