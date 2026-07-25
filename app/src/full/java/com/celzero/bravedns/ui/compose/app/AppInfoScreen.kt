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
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MobileOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.apps.RethinkAppInfoLogItem
import com.celzero.bravedns.ui.compose.apps.RethinkAppInfoLogSection
import com.celzero.bravedns.ui.compose.apps.RethinkAppInfoScreen
import com.celzero.bravedns.ui.compose.apps.RethinkAppInfoState
import com.celzero.bravedns.ui.compose.apps.RethinkAppInfoStrings
import com.celzero.bravedns.ui.components.CloseConnsDialog
import com.celzero.bravedns.data.AppConnection
import com.celzero.bravedns.database.AppInfo
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.ProxyManager.ID_NONE
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.bottomsheet.AppDomainRulesSheet
import com.celzero.bravedns.ui.bottomsheet.AppIpRulesSheet
import com.celzero.bravedns.ui.compose.apps.DiagonalWipeIcon
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.CompactEmptyState
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.ui.compose.theme.cardPositionFor
import com.celzero.bravedns.util.Constants.Companion.INVALID_UID
import com.celzero.bravedns.util.Constants.Companion.RETHINK_PACKAGE
import com.celzero.bravedns.util.UIUtils.openAndroidAppInfo
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.viewmodel.AppConnectionsViewModel
import com.celzero.bravedns.viewmodel.CustomDomainViewModel
import com.celzero.bravedns.viewmodel.CustomIpViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    onBackClick: () -> Unit,
    onAppWiseIpLogsClick: (Int, Boolean) -> Unit,
    onCustomIpRulesClick: (Int) -> Unit,
    onCustomDomainRulesClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var appInfo by remember(uid) { mutableStateOf<AppInfo?>(null) }
    var appStatus by remember(uid) { mutableStateOf(FirewallManager.FirewallStatus.NONE) }
    var connStatus by remember(uid) { mutableStateOf(FirewallManager.ConnectionStatus.ALLOW) }
    var baselineConnStatus by remember(uid) { mutableStateOf(FirewallManager.ConnectionStatus.ALLOW) }
    var firewallStatusText by remember(uid) { mutableStateOf("") }
    var firewallUpdateVersion by remember(uid) { mutableStateOf(0) }
    var isProxyExcluded by remember(uid) { mutableStateOf(false) }
    var isTempAllowed by remember(uid) { mutableStateOf(false) }
    var proxyDetails by remember(uid) { mutableStateOf("") }
    var showNoAppFoundDialog by remember(uid) { mutableStateOf(false) }

    var showDomainRulesSheet by remember { mutableStateOf(false) }
    var selectedDomain by remember { mutableStateOf("") }
    var showIpRulesSheet by remember { mutableStateOf(false) }
    var selectedIp by remember { mutableStateOf("") }
    var selectedDomains by remember { mutableStateOf("") }

    var refreshToken by remember(uid) { mutableStateOf(0) }
    var closeDialogConn by remember(uid) { mutableStateOf<com.celzero.bravedns.data.AppConnection?>(null) }

    val wireguardAppsProxyMapDesc = stringResource(R.string.wireguard_apps_proxy_map_desc)
    val excludeNoPackageErrToast = stringResource(R.string.exclude_no_package_err_toast)
    val adaAppStatusBlockMd = stringResource(R.string.ada_app_status_block_md)
    val adaAppStatusBlockWifi = stringResource(R.string.ada_app_status_block_wifi)
    val adaAppStatusBlock = stringResource(R.string.ada_app_status_block)
    val adaAppStatusAllow = stringResource(R.string.ada_app_status_allow)
    val adaAppStatusExclude = stringResource(R.string.ada_app_status_exclude)
    val adaAppStatusWhitelist = stringResource(R.string.ada_app_status_whitelist)
    val adaAppStatusIsolate = stringResource(R.string.ada_app_status_isolate)
    val adaAppStatusBypassDnsFirewall = stringResource(R.string.ada_app_status_bypass_dns_firewall)
    val adaAppStatusUnknown = stringResource(R.string.ada_app_status_unknown)
    val getFirewallStatusText: (FirewallManager.FirewallStatus, FirewallManager.ConnectionStatus) -> String =
        { firewallStatus, connectionStatus ->
            when (firewallStatus) {
                FirewallManager.FirewallStatus.NONE -> {
                    when (connectionStatus) {
                        FirewallManager.ConnectionStatus.METERED -> adaAppStatusBlockMd
                        FirewallManager.ConnectionStatus.UNMETERED -> adaAppStatusBlockWifi
                        FirewallManager.ConnectionStatus.BOTH -> adaAppStatusBlock
                        FirewallManager.ConnectionStatus.ALLOW -> adaAppStatusAllow
                    }
                }
                FirewallManager.FirewallStatus.EXCLUDE -> adaAppStatusExclude
                FirewallManager.FirewallStatus.BYPASS_UNIVERSAL -> adaAppStatusWhitelist
                FirewallManager.FirewallStatus.ISOLATE -> adaAppStatusIsolate
                FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL -> adaAppStatusBypassDnsFirewall
                FirewallManager.FirewallStatus.UNTRACKED -> adaAppStatusUnknown
            }
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
            uid = uid,
            wireguardAppsProxyMapDesc = wireguardAppsProxyMapDesc,
            getFirewallStatusText = getFirewallStatusText,
            onLoaded = {
                appInfo = it.info
                appStatus = it.appStatus
                connStatus = it.connStatus
                if (it.appStatus == FirewallManager.FirewallStatus.NONE) {
                    baselineConnStatus = it.connStatus
                }
                isProxyExcluded = it.isProxyExcluded
                isTempAllowed = it.isTempAllowed
                proxyDetails = it.proxyDetails
                firewallStatusText = it.firewallStatusText
            },
            onMissing = { showNoAppFoundDialog = true }
        )
    }

    // CloseConnsDialog displayed when user long-presses an active connection
    closeDialogConn?.let { conn ->
        CloseConnsDialog(
            conn = conn,
            onConfirm = {
                closeDialogConn = null
                refreshToken++
            },
            onDismiss = { closeDialogConn = null }
        )
    }

    if (showNoAppFoundDialog) {
        RethinkConfirmDialog(
            onDismissRequest = { showNoAppFoundDialog = false },
            title = stringResource(id = R.string.ada_noapp_dialog_title),
            message = stringResource(id = R.string.ada_noapp_dialog_message),
            confirmText = stringResource(id = R.string.fapps_info_dialog_positive_btn),
            onConfirm = {
                showNoAppFoundDialog = false
                onBackClick()
            }
        )
    }

    if (showDomainRulesSheet && selectedDomain.isNotEmpty()) {
        AppDomainRulesSheet(
            uid = uid,
            domain = selectedDomain,
            eventLogger = eventLogger,
            onDismiss = { showDomainRulesSheet = false },
            onUpdated = { refreshToken++ }
        )
    }
    if (showIpRulesSheet && selectedIp.isNotEmpty()) {
        AppIpRulesSheet(
            uid = uid,
            ipAddress = selectedIp,
            domains = selectedDomains,
            eventLogger = eventLogger,
            onDismiss = { showIpRulesSheet = false },
            onUpdated = { refreshToken++ }
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
    val activeItems = activeConns.collectAsLazyPagingItems()
    val domainItems =
        if (isRethink) {
            networkLogsViewModel.getRethinkDomainLogsLimited().collectAsLazyPagingItems()
        } else {
            networkLogsViewModel.getDomainLogsLimited(uid).collectAsLazyPagingItems()
        }
    val ipItems =
        if (isRethink) {
            networkLogsViewModel.getRethinkIpLogsLimited().collectAsLazyPagingItems()
        } else {
            networkLogsViewModel.getIpLogsLimited(uid).collectAsLazyPagingItems()
        }
    val activePreview =
        remember(activeItems.itemSnapshotList.items, refreshToken) {
            activeItems.itemSnapshotList.items.take(8)
        }
    val domainPreview =
        remember(domainItems.itemSnapshotList.items, refreshToken) {
            domainItems.itemSnapshotList.items.take(8)
        }
    val ipPreview =
        remember(ipItems.itemSnapshotList.items, refreshToken) {
            ipItems.itemSnapshotList.items.take(8)
        }
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val info = appInfo
    val title = info?.appName?.takeIf { it.isNotBlank() } ?: stringResource(id = R.string.bsct_app_info)
    val subtitle = info?.packageName?.takeIf { it.isNotBlank() }
    val wifiBlocked =
        connStatus == FirewallManager.ConnectionStatus.UNMETERED ||
            connStatus == FirewallManager.ConnectionStatus.BOTH
    val mobileBlocked =
        connStatus == FirewallManager.ConnectionStatus.METERED ||
            connStatus == FirewallManager.ConnectionStatus.BOTH
    val isIsolated = appStatus == FirewallManager.FirewallStatus.ISOLATE
    val isBypassDnsFirewall = appStatus == FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL
    val isBypassUniversal = appStatus == FirewallManager.FirewallStatus.BYPASS_UNIVERSAL
    val isExcluded = appStatus == FirewallManager.FirewallStatus.EXCLUDE
    var appIcon by remember(uid) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(info?.packageName, info?.appName) {
        if (info == null) {
            appIcon = null
            return@LaunchedEffect
        }
        appIcon =
            withContext(Dispatchers.IO) {
                Utilities.getIcon(context, info.packageName, info.appName)
            }
    }

    fun applyFirewallRule(
        firewallStatus: FirewallManager.FirewallStatus,
        connectionStatus: FirewallManager.ConnectionStatus
    ) {
        val requestVersion = firewallUpdateVersion + 1
        firewallUpdateVersion = requestVersion

        // Optimistic update to keep UI deterministic and avoid stale rapid-tap states.
        val optimisticText = getFirewallStatusText(firewallStatus, connectionStatus)
        firewallStatusText = optimisticText
        appStatus = firewallStatus
        connStatus = connectionStatus
        if (firewallStatus == FirewallManager.FirewallStatus.NONE) {
            baselineConnStatus = connectionStatus
        }

        updateFirewallStatus(
            scope = scope,
            context = context,
            uid = uid,
            appInfo = info,
            aStat = firewallStatus,
            cStat = connectionStatus,
            eventLogger = eventLogger,
            excludeNoPackageErrToast = excludeNoPackageErrToast,
            getFirewallStatusText = getFirewallStatusText
        ) { statusText, updatedAppStatus, updatedConnStatus ->
            if (requestVersion != firewallUpdateVersion) return@updateFirewallStatus
            firewallStatusText = statusText
            appStatus = updatedAppStatus
            connStatus = updatedConnStatus
            if (updatedAppStatus == FirewallManager.FirewallStatus.NONE) {
                baselineConnStatus = updatedConnStatus
            }
        }
    }

    fun toggleExclusiveStatus(target: FirewallManager.FirewallStatus) {
        val turningOff = appStatus == target
        if (!turningOff && appStatus == FirewallManager.FirewallStatus.NONE) {
            baselineConnStatus = connStatus
        }
        val nextStatus =
            if (turningOff) {
                FirewallManager.FirewallStatus.NONE
            } else {
                target
            }
        val nextConnStatus =
            if (nextStatus == FirewallManager.FirewallStatus.NONE) {
                baselineConnStatus
            } else {
                FirewallManager.ConnectionStatus.ALLOW
            }
        applyFirewallRule(nextStatus, nextConnStatus)
    }

    RethinkAppInfoScreen(
        state = RethinkAppInfoState(
            appAvailable = info != null,
            title = title,
            subtitle = subtitle,
            status = firewallStatusText,
            temporaryAllowed = isTempAllowed,
            proxyDetails = proxyDetails,
            wifiBlocked = wifiBlocked,
            mobileBlocked = mobileBlocked,
            isolated = isIsolated,
            bypassDnsFirewall = isBypassDnsFirewall,
            bypassUniversalFirewall = isBypassUniversal,
            excluded = isExcluded,
            proxyExcluded = isProxyExcluded,
            tempAllowed = isTempAllowed,
            activeConnections = RethinkAppInfoLogSection(
                title = stringResource(R.string.top_active_conns),
                count = activeItems.itemCount,
                loading = activeItems.loadState.refresh is LoadState.Loading && activeItems.itemCount == 0,
                empty = activeItems.itemCount == 0,
                entries = activePreview.mapIndexed { index, item ->
                    RethinkAppInfoLogItem(index.toString(), beautifyCommaSeparated(item.ipAddress), beautifyCommaSeparated(item.appOrDnsName))
                },
            ),
            domainLogs = RethinkAppInfoLogSection(
                title = stringResource(R.string.ssv_most_contacted_domain_heading),
                count = domainItems.itemCount,
                loading = domainItems.loadState.refresh is LoadState.Loading && domainItems.itemCount == 0,
                empty = domainItems.itemCount == 0,
                entries = domainPreview.mapIndexed { index, item ->
                    val domain = beautifyCommaSeparated(item.appOrDnsName).ifBlank { beautifyCommaSeparated(item.ipAddress) }
                    val address = beautifyCommaSeparated(item.ipAddress)
                    RethinkAppInfoLogItem(index.toString(), domain, address.takeIf { it.isNotBlank() && it != beautifyCommaSeparated(item.appOrDnsName) })
                },
            ),
            ipLogs = RethinkAppInfoLogSection(
                title = stringResource(R.string.ssv_most_contacted_ips_heading),
                count = ipItems.itemCount,
                loading = ipItems.loadState.refresh is LoadState.Loading && ipItems.itemCount == 0,
                empty = ipItems.itemCount == 0,
                entries = ipPreview.mapIndexed { index, item ->
                    RethinkAppInfoLogItem(index.toString(), beautifyCommaSeparated(item.ipAddress), beautifyCommaSeparated(item.appOrDnsName))
                },
            ),
        ),
        strings = RethinkAppInfoStrings(
            unavailable = stringResource(R.string.ada_noapp_dialog_message),
            back = stringResource(R.string.ada_noapp_dialog_positive),
            status = stringResource(R.string.lbl_status),
            temporaryAllow = stringResource(R.string.temp_allow_label),
            firewall = stringResource(R.string.lbl_firewall),
            wifi = stringResource(R.string.ada_app_unmetered),
            wifiDescription = stringResource(R.string.firewall_status_block_unmetered),
            mobile = stringResource(R.string.lbl_mobile_data),
            mobileDescription = stringResource(R.string.firewall_status_block_metered),
            isolate = stringResource(R.string.ada_app_isolate),
            isolateDescription = stringResource(R.string.firewall_status_isolate),
            bypassDns = stringResource(R.string.ada_app_bypass_dns_firewall),
            bypassDnsDescription = stringResource(R.string.firewall_status_bypass_dns_firewall),
            bypassUniversal = stringResource(R.string.ada_app_bypass_univ),
            bypassUniversalDescription = stringResource(R.string.firewall_status_whitelisted),
            exclude = stringResource(R.string.ada_app_exclude),
            excludeDescription = stringResource(R.string.firewall_status_excluded),
            enabled = stringResource(R.string.lbbs_enabled),
            disabled = stringResource(R.string.lbl_disabled),
            advanced = stringResource(R.string.lbl_advanced),
            proxyExclude = stringResource(R.string.exclude_apps_from_proxy),
            proxyExcludeDescription = stringResource(R.string.settings_exclude_proxy_apps_desc),
            temporaryAllowDescription = stringResource(R.string.temp_allow_desc),
            rules = stringResource(R.string.lbl_rules),
            systemAppInfo = stringResource(R.string.about_settings_app_info),
            ipRules = stringResource(R.string.lbl_ip_rules),
            domainRules = stringResource(R.string.lbl_domain_rules),
            loading = stringResource(R.string.lbl_loading),
            empty = stringResource(R.string.fapps_empty_subtitle),
        ),
        onBackClick = onBackClick,
        onWifiClick = {
            val next = when (connStatus) {
                FirewallManager.ConnectionStatus.UNMETERED -> FirewallManager.ConnectionStatus.ALLOW
                FirewallManager.ConnectionStatus.BOTH -> FirewallManager.ConnectionStatus.METERED
                FirewallManager.ConnectionStatus.METERED -> FirewallManager.ConnectionStatus.BOTH
                FirewallManager.ConnectionStatus.ALLOW -> FirewallManager.ConnectionStatus.UNMETERED
            }
            applyFirewallRule(FirewallManager.FirewallStatus.NONE, next)
        },
        onMobileClick = {
            val next = when (connStatus) {
                FirewallManager.ConnectionStatus.METERED -> FirewallManager.ConnectionStatus.ALLOW
                FirewallManager.ConnectionStatus.UNMETERED -> FirewallManager.ConnectionStatus.BOTH
                FirewallManager.ConnectionStatus.BOTH -> FirewallManager.ConnectionStatus.UNMETERED
                FirewallManager.ConnectionStatus.ALLOW -> FirewallManager.ConnectionStatus.METERED
            }
            applyFirewallRule(FirewallManager.FirewallStatus.NONE, next)
        },
        onIsolateClick = { toggleExclusiveStatus(FirewallManager.FirewallStatus.ISOLATE) },
        onBypassDnsClick = { toggleExclusiveStatus(FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL) },
        onBypassUniversalClick = { toggleExclusiveStatus(FirewallManager.FirewallStatus.BYPASS_UNIVERSAL) },
        onExcludeClick = { toggleExclusiveStatus(FirewallManager.FirewallStatus.EXCLUDE) },
        onProxyExcludedChange = { enabled ->
            isProxyExcluded = enabled
            scope.launch(Dispatchers.IO) { FirewallManager.updateIsProxyExcluded(uid, enabled) }
        },
        onTempAllowChange = { enabled ->
            isTempAllowed = enabled
            scope.launch(Dispatchers.IO) { FirewallManager.updateTempAllow(uid, enabled) }
        },
        onSystemAppInfo = { info?.let { openAndroidAppInfo(context, it.packageName) } },
        onIpRules = { onCustomIpRulesClick(uid) },
        onDomainRules = { onCustomDomainRulesClick(uid) },
        onActiveConnections = { onAppWiseIpLogsClick(uid, false) },
        onDomains = { onAppWiseIpLogsClick(uid, false) },
        onIps = { onAppWiseIpLogsClick(uid, false) },
        onActiveEntry = { entry -> activePreview.getOrNull(entry.id.toIntOrNull() ?: -1)?.let { closeDialogConn = it } },
        onDomainEntry = { entry -> domainPreview.getOrNull(entry.id.toIntOrNull() ?: -1)?.let { selectedDomain = it.appOrDnsName.orEmpty(); showDomainRulesSheet = true } },
        onIpEntry = { entry -> ipPreview.getOrNull(entry.id.toIntOrNull() ?: -1)?.let { selectedIp = it.ipAddress; selectedDomains = it.appOrDnsName.orEmpty(); showIpRulesSheet = true } },
        titleLeading = {
            val iconPainter = rememberDrawablePainter(appIcon ?: Utilities.getDefaultIcon(context))
            iconPainter?.let { painter ->
                Image(painter, null, Modifier.size(Dimensions.iconSizeXl).clip(RoundedCornerShape(Dimensions.cornerRadiusMd)))
            }
        },
    )

}

private fun beautifyCommaSeparated(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return value
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(", ")
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
    uid: Int,
    wireguardAppsProxyMapDesc: String,
    getFirewallStatusText: (FirewallManager.FirewallStatus, FirewallManager.ConnectionStatus) -> String,
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
            ?.let { wireguardAppsProxyMapDesc.format(it) }
            .orEmpty()
    val firewallStatusText = getFirewallStatusText(status, conn)
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
    eventLogger: EventLogger,
    excludeNoPackageErrToast: String,
    getFirewallStatusText: (FirewallManager.FirewallStatus, FirewallManager.ConnectionStatus) -> String,
    onUpdated: (String, FirewallManager.FirewallStatus, FirewallManager.ConnectionStatus) -> Unit
) {
    val info = appInfo ?: return
    if (aStat == FirewallManager.FirewallStatus.EXCLUDE && FirewallManager.isUnknownPackage(uid)) {
        showToastUiCentered(context, excludeNoPackageErrToast, Toast.LENGTH_LONG)
        return
    }
    scope.launch(Dispatchers.IO) {
        FirewallManager.updateFirewallStatus(info.uid, aStat, cStat)
        val statusText = getFirewallStatusText(aStat, cStat)
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
