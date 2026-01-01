/*
 * Copyright 2021 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.app

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.AppWiseDomainsAdapter
import com.celzero.bravedns.adapter.AppWiseIpsAdapter
import com.celzero.bravedns.database.AppInfo
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.ProxyManager.ID_NONE
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.activity.CustomRulesActivity
import com.celzero.bravedns.ui.bottomsheet.AppDomainRulesSheet
import com.celzero.bravedns.ui.bottomsheet.AppIpRulesSheet
import com.celzero.bravedns.util.Constants.Companion.INVALID_UID
import com.celzero.bravedns.util.Constants.Companion.RETHINK_PACKAGE
import com.celzero.bravedns.util.UIUtils.openAndroidAppInfo
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.viewmodel.AppConnectionsViewModel
import com.celzero.bravedns.viewmodel.CustomDomainViewModel
import com.celzero.bravedns.viewmodel.CustomIpViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoScreen(
    uid: Int,
    eventLogger: EventLogger,
    ipRulesViewModel: CustomIpViewModel,
    domainRulesViewModel: CustomDomainViewModel,
    networkLogsViewModel: AppConnectionsViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var appInfo by remember(uid) { mutableStateOf<AppInfo?>(null) }
    var appStatus by remember(uid) { mutableStateOf(FirewallManager.FirewallStatus.NONE) }
    var connStatus by remember(uid) { mutableStateOf(FirewallManager.ConnectionStatus.ALLOW) }
    var firewallStatusText by remember(uid) { mutableStateOf("") }
    var isProxyExcluded by remember(uid) { mutableStateOf(false) }
    var isTempAllowed by remember(uid) { mutableStateOf(false) }
    var proxyDetails by remember(uid) { mutableStateOf("") }
    var showNoAppFoundDialog by remember(uid) { mutableStateOf(false) }

    var showDomainRulesSheet by remember { mutableStateOf(false) }
    var selectedDomain by remember { mutableStateOf("") }
    var showIpRulesSheet by remember { mutableStateOf(false) }
    var selectedIp by remember { mutableStateOf("") }
    var selectedDomains by remember { mutableStateOf("") }

    val domainAdapter =
        remember(uid) {
            AppWiseDomainsAdapter(
                context,
                lifecycleOwner,
                uid,
                onShowDomainRules = { domain ->
                    selectedDomain = domain
                    showDomainRulesSheet = true
                }
            )
        }
    val ipAdapter =
        remember(uid) {
            AppWiseIpsAdapter(
                context,
                lifecycleOwner,
                uid,
                onShowIpRules = { ip, domains ->
                    selectedIp = ip
                    selectedDomains = domains
                    showIpRulesSheet = true
                }
            )
        }
    val activeAdapter =
        remember(uid) {
            AppWiseDomainsAdapter(
                context,
                lifecycleOwner,
                uid,
                isActiveConn = true,
                onShowDomainRules = { }
            )
        }

    LaunchedEffect(uid) {
        if (uid == INVALID_UID) {
            showNoAppFoundDialog = true
            return@LaunchedEffect
        }
        ipRulesViewModel.setUid(uid)
        domainRulesViewModel.setUid(uid)
        networkLogsViewModel.setUid(uid)
        loadAppInfo(
            context = context,
            uid = uid,
            onLoaded = {
                appInfo = it.info
                appStatus = it.appStatus
                connStatus = it.connStatus
                isProxyExcluded = it.isProxyExcluded
                isTempAllowed = it.isTempAllowed
                proxyDetails = it.proxyDetails
                firewallStatusText = it.firewallStatusText
            },
            onMissing = { showNoAppFoundDialog = true }
        )
    }

    activeAdapter.CloseDialogHost()

    if (showNoAppFoundDialog) {
        AlertDialog(
            onDismissRequest = { showNoAppFoundDialog = false },
            title = { Text(text = stringResource(id = R.string.ada_noapp_dialog_title)) },
            text = { Text(text = stringResource(id = R.string.ada_noapp_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNoAppFoundDialog = false
                        onBackClick()
                    }
                ) {
                    Text(text = stringResource(id = R.string.fapps_info_dialog_positive_btn))
                }
            }
        )
    }

    if (showDomainRulesSheet && selectedDomain.isNotEmpty()) {
        AppDomainRulesSheet(
            uid = uid,
            domain = selectedDomain,
            eventLogger = eventLogger,
            onDismiss = { showDomainRulesSheet = false },
            onUpdated = { domainAdapter.notifyRulesChanged() }
        )
    }
    if (showIpRulesSheet && selectedIp.isNotEmpty()) {
        AppIpRulesSheet(
            uid = uid,
            ipAddress = selectedIp,
            domains = selectedDomains,
            eventLogger = eventLogger,
            onDismiss = { showIpRulesSheet = false },
            onUpdated = { ipAdapter.notifyRulesChanged() }
        )
    }

    val isRethink = appInfo?.packageName == RETHINK_PACKAGE
    val uptime = VpnController.uptimeMs()
    val activeConns =
        if (isRethink) {
            networkLogsViewModel.getRethinkActiveConnsLimited(uptime)
        } else {
            networkLogsViewModel.fetchTopActiveConnections(uid, uptime)
        }
    val activeItems = activeConns.asFlow().collectAsLazyPagingItems()
    val domainItems =
        if (isRethink) {
            networkLogsViewModel.getRethinkDomainLogsLimited().asFlow().collectAsLazyPagingItems()
        } else {
            networkLogsViewModel.getDomainLogsLimited(uid).asFlow().collectAsLazyPagingItems()
        }
    val ipItems =
        if (isRethink) {
            networkLogsViewModel.getRethinkIpLogsLimited().asFlow().collectAsLazyPagingItems()
        } else {
            networkLogsViewModel.getIpLogsLimited(uid).asFlow().collectAsLazyPagingItems()
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.bsct_app_info)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back_24),
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (appInfo == null) {
                Text(text = stringResource(id = R.string.ada_noapp_dialog_message))
                Button(onClick = onBackClick) {
                    Text(text = stringResource(id = R.string.ada_noapp_dialog_positive))
                }
                return@Column
            }

            Card(colors = CardDefaults.cardColors()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = appInfo?.appName.orEmpty(), style = MaterialTheme.typography.titleLarge)
                    Text(text = appInfo?.packageName.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    Text(text = firewallStatusText, style = MaterialTheme.typography.bodySmall)
                    if (proxyDetails.isNotEmpty()) {
                        Text(text = proxyDetails, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { openAndroidAppInfo(context, appInfo?.packageName) }) {
                    Text(text = stringResource(id = R.string.about_settings_app_info))
                }
                Button(onClick = {
                    val intent = Intent(context, CustomRulesActivity::class.java)
                    intent.putExtra(AppInfoNav.EXTRA_UID, uid)
                    context.startActivity(intent)
                }) {
                    Text(text = stringResource(id = R.string.ada_app_dns_settings))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.exclude_apps_from_proxy))
                    Text(
                        text = stringResource(id = R.string.settings_exclude_proxy_apps_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = isProxyExcluded,
                    onCheckedChange = { enabled ->
                        isProxyExcluded = enabled
                        scope.launch(Dispatchers.IO) {
                            FirewallManager.updateIsProxyExcluded(uid, enabled)
                        }
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.temp_allow_label))
                    Text(text = stringResource(id = R.string.temp_allow_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = isTempAllowed,
                    onCheckedChange = { enabled ->
                        isTempAllowed = enabled
                        scope.launch(Dispatchers.IO) {
                            FirewallManager.updateTempAllow(uid, enabled)
                        }
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val newConnStatus =
                        when (connStatus) {
                            FirewallManager.ConnectionStatus.UNMETERED ->
                                FirewallManager.ConnectionStatus.ALLOW
                            FirewallManager.ConnectionStatus.BOTH ->
                                FirewallManager.ConnectionStatus.METERED
                            FirewallManager.ConnectionStatus.METERED ->
                                FirewallManager.ConnectionStatus.BOTH
                            FirewallManager.ConnectionStatus.ALLOW ->
                                FirewallManager.ConnectionStatus.UNMETERED
                        }
                    updateFirewallStatus(
                        scope,
                        context,
                        uid,
                        appInfo,
                        FirewallManager.FirewallStatus.NONE,
                        newConnStatus,
                        connStatus,
                        eventLogger
                    ) { statusText, updatedAppStatus, updatedConnStatus ->
                        firewallStatusText = statusText
                        appStatus = updatedAppStatus
                        connStatus = updatedConnStatus
                    }
                }) {
                    Text(text = stringResource(id = R.string.ada_app_unmetered))
                }
                Button(onClick = {
                    val newConnStatus =
                        when (connStatus) {
                            FirewallManager.ConnectionStatus.METERED ->
                                FirewallManager.ConnectionStatus.ALLOW
                            FirewallManager.ConnectionStatus.UNMETERED ->
                                FirewallManager.ConnectionStatus.BOTH
                            FirewallManager.ConnectionStatus.BOTH ->
                                FirewallManager.ConnectionStatus.UNMETERED
                            FirewallManager.ConnectionStatus.ALLOW ->
                                FirewallManager.ConnectionStatus.METERED
                        }
                    updateFirewallStatus(
                        scope,
                        context,
                        uid,
                        appInfo,
                        FirewallManager.FirewallStatus.NONE,
                        newConnStatus,
                        connStatus,
                        eventLogger
                    ) { statusText, updatedAppStatus, updatedConnStatus ->
                        firewallStatusText = statusText
                        appStatus = updatedAppStatus
                        connStatus = updatedConnStatus
                    }
                }) {
                    Text(text = stringResource(id = R.string.ada_app_metered))
                }
                Button(onClick = {
                    val next =
                        if (appStatus == FirewallManager.FirewallStatus.ISOLATE) {
                            FirewallManager.FirewallStatus.NONE
                        } else {
                            FirewallManager.FirewallStatus.ISOLATE
                        }
                    updateFirewallStatus(
                        scope,
                        context,
                        uid,
                        appInfo,
                        next,
                        FirewallManager.ConnectionStatus.ALLOW,
                        connStatus,
                        eventLogger
                    ) { statusText, updatedAppStatus, updatedConnStatus ->
                        firewallStatusText = statusText
                        appStatus = updatedAppStatus
                        connStatus = updatedConnStatus
                    }
                }) {
                    Text(text = stringResource(id = R.string.ada_app_isolate))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val next =
                        if (appStatus == FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL) {
                            FirewallManager.FirewallStatus.NONE
                        } else {
                            FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL
                        }
                    updateFirewallStatus(
                        scope,
                        context,
                        uid,
                        appInfo,
                        next,
                        FirewallManager.ConnectionStatus.ALLOW,
                        connStatus,
                        eventLogger
                    ) { statusText, updatedAppStatus, updatedConnStatus ->
                        firewallStatusText = statusText
                        appStatus = updatedAppStatus
                        connStatus = updatedConnStatus
                    }
                }) {
                    Text(text = stringResource(id = R.string.ada_app_bypass_dns_firewall))
                }
                Button(onClick = {
                    val next =
                        if (appStatus == FirewallManager.FirewallStatus.BYPASS_UNIVERSAL) {
                            FirewallManager.FirewallStatus.NONE
                        } else {
                            FirewallManager.FirewallStatus.BYPASS_UNIVERSAL
                        }
                    updateFirewallStatus(
                        scope,
                        context,
                        uid,
                        appInfo,
                        next,
                        FirewallManager.ConnectionStatus.ALLOW,
                        connStatus,
                        eventLogger
                    ) { statusText, updatedAppStatus, updatedConnStatus ->
                        firewallStatusText = statusText
                        appStatus = updatedAppStatus
                        connStatus = updatedConnStatus
                    }
                }) {
                    Text(text = stringResource(id = R.string.ada_app_bypass_univ))
                }
                Button(onClick = {
                    val next =
                        if (appStatus == FirewallManager.FirewallStatus.EXCLUDE) {
                            FirewallManager.FirewallStatus.NONE
                        } else {
                            FirewallManager.FirewallStatus.EXCLUDE
                        }
                    updateFirewallStatus(
                        scope,
                        context,
                        uid,
                        appInfo,
                        next,
                        FirewallManager.ConnectionStatus.ALLOW,
                        connStatus,
                        eventLogger
                    ) { statusText, updatedAppStatus, updatedConnStatus ->
                        firewallStatusText = statusText
                        appStatus = updatedAppStatus
                        connStatus = updatedConnStatus
                    }
                }) {
                    Text(text = stringResource(id = R.string.ada_app_exclude))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = stringResource(id = R.string.top_active_conns), style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height(200.dp)
            ) {
                items(count = activeItems.itemCount) { index ->
                    val item = activeItems[index] ?: return@items
                    activeAdapter.DomainRow(item)
                }
            }

            Text(text = stringResource(id = R.string.ssv_most_contacted_domain_heading), style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height(200.dp)
            ) {
                items(count = domainItems.itemCount) { index ->
                    val item = domainItems[index] ?: return@items
                    domainAdapter.DomainRow(item)
                }
            }

            Text(text = stringResource(id = R.string.ssv_most_contacted_ips_heading), style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height(200.dp)
            ) {
                items(count = ipItems.itemCount) { index ->
                    val item = ipItems[index] ?: return@items
                    ipAdapter.IpRow(item)
                }
            }
        }
    }
}

private data class AppInfoLoad(
    val info: AppInfo,
    val appStatus: FirewallManager.FirewallStatus,
    val connStatus: FirewallManager.ConnectionStatus,
    val isProxyExcluded: Boolean,
    val isTempAllowed: Boolean,
    val proxyDetails: String,
    val firewallStatusText: String
)

private suspend fun loadAppInfo(
    context: Context,
    uid: Int,
    onLoaded: (AppInfoLoad) -> Unit,
    onMissing: () -> Unit
) {
    val info = withContext(Dispatchers.IO) { FirewallManager.getAppInfoByUid(uid) }
    if (info == null || uid == INVALID_UID || info.tombstoneTs > 0) {
        onMissing()
        return
    }
    val status = FirewallManager.appStatus(info.uid)
    val conn = FirewallManager.connectionStatus(info.uid)
    val proxy =
        ProxyManager.getProxyIdForApp(uid).takeIf { it.isNotEmpty() && it != ID_NONE }
            ?.let { context.getString(R.string.wireguard_apps_proxy_map_desc, it) }
            .orEmpty()
    val firewallStatusText = getFirewallText(context, status, conn)
    onLoaded(
        AppInfoLoad(
            info = info,
            appStatus = status,
            connStatus = conn,
            isProxyExcluded = info.isProxyExcluded,
            isTempAllowed = FirewallManager.isTempAllowed(info.uid),
            proxyDetails = proxy,
            firewallStatusText = firewallStatusText
        )
    )
}

private fun updateFirewallStatus(
    scope: CoroutineScope,
    context: Context,
    uid: Int,
    appInfo: AppInfo?,
    aStat: FirewallManager.FirewallStatus,
    cStat: FirewallManager.ConnectionStatus,
    prevConnStat: FirewallManager.ConnectionStatus,
    eventLogger: EventLogger,
    onUpdated: (String, FirewallManager.FirewallStatus, FirewallManager.ConnectionStatus) -> Unit
) {
    val info = appInfo ?: return
    if (aStat == FirewallManager.FirewallStatus.EXCLUDE && FirewallManager.isUnknownPackage(uid)) {
        showToastUiCentered(context, context.getString(R.string.exclude_no_package_err_toast), Toast.LENGTH_LONG)
        return
    }
    scope.launch(Dispatchers.IO) {
        FirewallManager.updateFirewallStatus(info.uid, aStat, cStat)
        val statusText = getFirewallText(context, aStat, cStat)
        withContext(Dispatchers.Main) {
            onUpdated(statusText, aStat, cStat)
        }
        eventLogger.log(
            type = EventType.FW_RULE_MODIFIED,
            severity = Severity.LOW,
            message = "Firewall status changed",
            source = EventSource.MANAGER,
            userAction = true,
            details = "Firewall status changed for ${info.appName} (${info.uid}), new status: $aStat, conn status: $cStat"
        )
    }
}

private fun getFirewallText(
    context: Context,
    aStat: FirewallManager.FirewallStatus,
    cStat: FirewallManager.ConnectionStatus
): String {
    return when (aStat) {
        FirewallManager.FirewallStatus.NONE -> {
            when (cStat) {
                FirewallManager.ConnectionStatus.METERED ->
                    context.getString(R.string.ada_app_status_block_md)
                FirewallManager.ConnectionStatus.UNMETERED ->
                    context.getString(R.string.ada_app_status_block_wifi)
                FirewallManager.ConnectionStatus.BOTH ->
                    context.getString(R.string.ada_app_status_block)
                FirewallManager.ConnectionStatus.ALLOW ->
                    context.getString(R.string.ada_app_status_allow)
            }
        }
        FirewallManager.FirewallStatus.EXCLUDE -> context.getString(R.string.ada_app_status_exclude)
        FirewallManager.FirewallStatus.BYPASS_UNIVERSAL ->
            context.getString(R.string.ada_app_status_whitelist)
        FirewallManager.FirewallStatus.ISOLATE -> context.getString(R.string.ada_app_status_isolate)
        FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL ->
            context.getString(R.string.ada_app_status_bypass_dns_firewall)
        FirewallManager.FirewallStatus.UNTRACKED -> context.getString(R.string.ada_app_status_unknown)
    }
}
