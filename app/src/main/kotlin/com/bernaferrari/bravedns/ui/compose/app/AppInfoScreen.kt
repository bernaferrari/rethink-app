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
package com.bernaferrari.bravedns.ui.compose.app

import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.AppInfo
import com.bernaferrari.bravedns.database.ConnectionTracker
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.service.FirewallManager.ConnectionStatus
import com.bernaferrari.bravedns.service.FirewallManager.FirewallStatus
import com.bernaferrari.bravedns.service.ProxyManager
import com.bernaferrari.bravedns.service.ProxyManager.ID_NONE
import com.bernaferrari.bravedns.service.WireguardManager
import com.bernaferrari.bravedns.ui.compose.apps.RethinkAppInfoActions
import com.bernaferrari.bravedns.ui.compose.apps.RethinkAppInfoActivityItem
import com.bernaferrari.bravedns.ui.compose.apps.RethinkAppInfoScreen
import com.bernaferrari.bravedns.ui.compose.apps.RethinkAppInfoState
import com.bernaferrari.bravedns.ui.compose.apps.RethinkAppInfoStrings
import com.bernaferrari.bravedns.ui.compose.apps.RethinkAppMode
import com.bernaferrari.bravedns.ui.compose.common.AppIcon
import com.bernaferrari.bravedns.ui.compose.firewall.launchReversibleRuleMutation
import com.bernaferrari.bravedns.ui.compose.logs.ConnectionSheetHost
import com.bernaferrari.bravedns.ui.compose.logs.ConnectionSheetNavigation
import com.bernaferrari.bravedns.ui.compose.logs.connectionLogModel
import com.bernaferrari.bravedns.ui.compose.logs.toConnectionSheetRequest
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.util.Constants.Companion.INVALID_UID
import com.bernaferrari.bravedns.util.Constants.Companion.TIME_FORMAT_1
import com.bernaferrari.bravedns.util.UIUtils.openAndroidAppInfo
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.Utilities.showToastUiCentered
import com.bernaferrari.bravedns.viewmodel.AppConnectionsViewModel
import com.bernaferrari.bravedns.viewmodel.CustomDomainViewModel
import com.bernaferrari.bravedns.viewmodel.CustomIpViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * App detail for one uid: reads the app's firewall flags, temporary access and recent traffic,
 * and writes every change through [FirewallManager] with an Undo snackbar. Recent activity rows
 * open the Connection sheet ([connectionSheetNavigation] says where its links lead).
 */
@Composable
fun AppInfoScreen(
    uid: Int,
    eventLogger: EventLogger,
    ipRulesViewModel: CustomIpViewModel,
    domainRulesViewModel: CustomDomainViewModel,
    networkLogsViewModel: AppConnectionsViewModel,
    onBackClick: () -> Unit,
    onCustomIpRulesClick: (Int) -> Unit,
    onCustomDomainRulesClick: (Int) -> Unit,
    /** The Activity log filtered to this app ([query] is its name). */
    onSeeAllActivity: ((query: String) -> Unit)? = null,
    connectionSheetNavigation: ConnectionSheetNavigation = ConnectionSheetNavigation(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var loaded by remember(uid) { mutableStateOf(false) }
    var appInfo by remember(uid) { mutableStateOf<AppInfo?>(null) }
    var appStatus by remember(uid) { mutableStateOf(FirewallStatus.NONE) }
    var connStatus by remember(uid) { mutableStateOf(ConnectionStatus.ALLOW) }
    // The network flags to restore when a mode goes back to Normal (modes clear them).
    var baselineConnStatus by remember(uid) { mutableStateOf(ConnectionStatus.ALLOW) }
    var isProxyExcluded by remember(uid) { mutableStateOf(false) }
    var tempAllowExpiry by remember(uid) { mutableLongStateOf(0L) }
    var proxyDetails by remember(uid) { mutableStateOf("") }
    var anyProxySetUp by remember(uid) { mutableStateOf(false) }
    var reloadToken by remember(uid) { mutableIntStateOf(0) }
    var busy by remember(uid) { mutableStateOf(false) }
    var epoch by remember(uid) { mutableIntStateOf(0) }
    var selectedConnection by remember(uid) { mutableStateOf<ConnectionTracker?>(null) }

    val wireguardAppsProxyMapDesc = stringResource(R.string.wireguard_apps_proxy_map_desc)
    val excludeNoPackageErrToast = stringResource(R.string.exclude_no_package_err_toast)
    val appConfig = koinInject<AppConfig>()
    // Keepout's own traffic is logged apart from every other app's.
    val isKeepout = uid == context.applicationInfo.uid

    LaunchedEffect(uid, reloadToken) {
        if (uid != INVALID_UID) {
            ipRulesViewModel.setUid(uid)
            domainRulesViewModel.setUid(uid)
        }
        val load = if (uid == INVALID_UID) null else withContext(Dispatchers.IO) { loadAppInfo(uid, wireguardAppsProxyMapDesc, appConfig) }
        appInfo = load?.info
        if (load != null) {
            appStatus = load.appStatus
            connStatus = load.connStatus
            if (load.appStatus == FirewallStatus.NONE) baselineConnStatus = load.connStatus
            isProxyExcluded = load.isProxyExcluded
            tempAllowExpiry = load.tempAllowExpiry
            proxyDetails = load.proxyDetails
            anyProxySetUp = load.anyProxySetUp
        }
        loaded = true
    }

    // Live countdown from the stored expiry; when it passes, re-read what the firewall now holds.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tempAllowExpiry) {
        while (tempAllowExpiry > 0L) {
            now = System.currentTimeMillis()
            if (now >= tempAllowExpiry) {
                tempAllowExpiry = withContext(Dispatchers.IO) { FirewallManager.tempAllowExpiry(uid) }
                break
            }
            delay(1_000L)
        }
    }
    val minutesLeft = minutesLeft(tempAllowExpiry, now)

    val recentFlow = remember(uid) {
        if (isKeepout) networkLogsViewModel.recentKeepoutConnections() else networkLogsViewModel.recentConnections(uid)
    }
    val recent by recentFlow.collectAsState(initial = null)
    val activityRows = remember(recent) { recent?.let { foldRecentActivity(it) } }
    val domainCountFlow = remember(uid) { domainRulesViewModel.domainRulesCount(uid) }
    val domainRuleCount by domainCountFlow.collectAsState(initial = null)
    val ipCountFlow = remember(uid) { ipRulesViewModel.ipRulesCount(uid) }
    val ipRuleCount by ipCountFlow.collectAsState(initial = null)

    val info = appInfo
    val appName = info?.appName?.takeIf { it.isNotBlank() } ?: info?.packageName.orEmpty()
    val undoLabel = stringResource(R.string.lbl_undo)

    /** Write [status]/[connection] now (optimistically), then offer Undo back to what it was. */
    fun applyFirewallRule(status: FirewallStatus, connection: ConnectionStatus, message: String) {
        val app = info ?: return
        if (status == FirewallStatus.EXCLUDE && FirewallManager.isUnknownPackage(uid)) {
            showToastUiCentered(context, excludeNoPackageErrToast, Toast.LENGTH_LONG)
            return
        }
        val previous = appStatus to connStatus
        val previousBaseline = baselineConnStatus
        fun show(s: FirewallStatus, c: ConnectionStatus) {
            appStatus = s
            connStatus = c
            if (s == FirewallStatus.NONE) baselineConnStatus = c
        }
        show(status, connection)
        val next = epoch + 1
        epoch = next
        snackbar.currentSnackbarData?.dismiss()
        scope.launchReversibleRuleMutation(
            snackbarHostState = snackbar,
            editedMessage = message,
            undoLabel = undoLabel,
            epoch = next,
            currentEpoch = { epoch },
            setApplying = { busy = it },
            apply = {
                withContext(Dispatchers.IO + NonCancellable) { FirewallManager.updateFirewallStatus(app.uid, status, connection) }
                logChange(eventLogger, app, status, connection)
                true
            },
            undo = {
                show(previous.first, previous.second)
                baselineConnStatus = previousBaseline
                withContext(Dispatchers.IO + NonCancellable) { FirewallManager.updateFirewallStatus(app.uid, previous.first, previous.second) }
                logChange(eventLogger, app, previous.first, previous.second, undo = true)
            },
        )
    }

    val blockedWifiMessage = stringResource(R.string.app_done_blocked_wifi, appName)
    val allowedWifiMessage = stringResource(R.string.connection_done_allowed_wifi, appName)
    val blockedMobileMessage = stringResource(R.string.app_done_blocked_mobile, appName)
    val allowedMobileMessage = stringResource(R.string.connection_done_allowed_mobile, appName)
    val tempAllowedMessage = stringResource(R.string.connection_done_allowed_temporarily, appName)
    val tempEndedMessage = stringResource(R.string.app_temp_access_ended)

    val strings = appInfoStrings()
    val kind = appStatusKind(appStatus, connStatus, minutesLeft != null)
    val status = when (kind) {
        AppStatusKind.TemporarilyAllowed -> pluralStringResource(R.plurals.app_status_temp_allowed, minutesLeft ?: 0, minutesLeft ?: 0)
        AppStatusKind.NoInternet -> stringResource(R.string.app_status_no_internet)
        AppStatusKind.BlockedOnWifi -> stringResource(R.string.ada_app_status_block_wifi)
        AppStatusKind.BlockedOnMobileData -> stringResource(R.string.ada_app_status_block_md)
        AppStatusKind.CanUseInternet -> stringResource(R.string.app_status_can_use_internet)
        AppStatusKind.TrustedOnly -> stringResource(R.string.app_status_trusted_only)
        AppStatusKind.SkipsAllFiltering -> stringResource(R.string.ada_app_status_bypass_dns_firewall)
        AppStatusKind.SkipsUniversalRules -> stringResource(R.string.app_status_skips_universal)
        AppStatusKind.Excluded -> stringResource(R.string.app_status_excluded)
        AppStatusKind.Unknown -> stringResource(R.string.ada_app_status_unknown)
    }
    val blockedLabel = stringResource(R.string.lbl_blocked)
    val allowedLabel = stringResource(R.string.lbl_allowed)
    val activity = activityRows?.map { row ->
        RethinkAppInfoActivityItem(
            id = row.key,
            title = row.destination,
            subtitle = listOf(
                if (row.blocked) blockedLabel else allowedLabel,
                Utilities.convertLongToTime(row.latest.timeStamp, TIME_FORMAT_1),
                pluralStringResource(R.plurals.app_activity_count, row.count, row.count),
            ).joinToString(" · "),
            blocked = row.blocked,
        )
    }
    val modeLabels = mapOf(
        RethinkAppMode.Normal to strings.normal,
        RethinkAppMode.TrustedOnly to strings.trustedOnly,
        RethinkAppMode.SkipAllFiltering to strings.skipAllFiltering,
        RethinkAppMode.SkipUniversalRules to strings.skipUniversalRules,
        RethinkAppMode.Excluded to strings.exclude,
    )
    val modeChangedFormat = stringResource(R.string.app_mode_changed)

    RethinkAppInfoScreen(
        state = RethinkAppInfoState(
            loading = !loaded,
            appAvailable = info != null,
            title = if (!loaded) "" else appName.ifBlank { stringResource(R.string.bsct_app_info) },
            subtitle = info?.packageName?.takeIf { it.isNotBlank() && it != appName },
            status = status,
            statusTone = kind.tone,
            statusDetail = proxyDetails.takeIf { it.isNotBlank() },
            wifiBlocked = connStatus.wifiBlocked,
            mobileBlocked = connStatus.mobileBlocked,
            mode = appStatus.toAppMode(),
            tempAllowMinutesLeft = minutesLeft,
            proxyExcluded = isProxyExcluded,
            showProxyExclude = showProxyExclude(anyProxySetUp, isProxyExcluded),
            activity = activity,
            domainRuleCount = domainRuleCount,
            ipRuleCount = ipRuleCount,
            enabled = !busy,
        ),
        strings = strings,
        actions = RethinkAppInfoActions(
            onBackClick = onBackClick,
            onWifiClick = {
                val next = connStatus.toggledWifi()
                applyFirewallRule(FirewallStatus.NONE, next, if (next.wifiBlocked) blockedWifiMessage else allowedWifiMessage)
            },
            onMobileClick = {
                val next = connStatus.toggledMobile()
                applyFirewallRule(FirewallStatus.NONE, next, if (next.mobileBlocked) blockedMobileMessage else allowedMobileMessage)
            },
            onModeChange = { mode ->
                modeChange(appStatus, connStatus, baselineConnStatus, mode)?.let { (status, connection) ->
                    if (appStatus == FirewallStatus.NONE) baselineConnStatus = connStatus
                    applyFirewallRule(status, connection, modeChangedFormat.format(appName, modeLabels.getValue(mode)))
                }
            },
            onTempAllowChange = { enabled ->
                if (enabled) {
                    val next = epoch + 1
                    epoch = next
                    snackbar.currentSnackbarData?.dismiss()
                    scope.launchReversibleRuleMutation(
                        snackbarHostState = snackbar,
                        editedMessage = tempAllowedMessage,
                        undoLabel = undoLabel,
                        epoch = next,
                        currentEpoch = { epoch },
                        setApplying = { busy = it },
                        apply = {
                            tempAllowExpiry = withContext(Dispatchers.IO + NonCancellable) {
                                FirewallManager.updateTempAllow(uid, true)
                                FirewallManager.tempAllowExpiry(uid)
                            }
                            tempAllowExpiry > 0L
                        },
                        undo = {
                            withContext(Dispatchers.IO + NonCancellable) { FirewallManager.updateTempAllow(uid, false) }
                            tempAllowExpiry = 0L
                        },
                    )
                } else {
                    epoch++
                    snackbar.currentSnackbarData?.dismiss()
                    scope.launch {
                        busy = true
                        withContext(Dispatchers.IO + NonCancellable) { FirewallManager.updateTempAllow(uid, false) }
                        tempAllowExpiry = 0L
                        busy = false
                        snackbar.showSnackbar(tempEndedMessage)
                    }
                }
            },
            onProxyExcludedChange = { enabled ->
                isProxyExcluded = enabled
                scope.launch(Dispatchers.IO) { FirewallManager.updateIsProxyExcluded(uid, enabled) }
            },
            onSystemAppInfo = { info?.let { openAndroidAppInfo(context, it.packageName) } },
            onIpRules = { onCustomIpRulesClick(uid) },
            onDomainRules = { onCustomDomainRulesClick(uid) },
            // Keepout's own rows aren't in the connection log the Connection sheet edits.
            onActivityClick = { item: RethinkAppInfoActivityItem ->
                activityRows?.firstOrNull { it.key == item.id }?.let { selectedConnection = it.latest }
                Unit
            }.takeUnless { isKeepout },
            onSeeAllActivity = onSeeAllActivity?.let { open -> { open(appName) } }?.takeIf { appName.isNotBlank() },
        // The activity log reads the connection log, which never holds Keepout's own uid.
        ).let { if (isKeepout) it.copy(onSeeAllActivity = null) else it },
        appNotes = info?.let { app -> { AppNotesSection(uid, app) } },
        snackbarHostState = snackbar,
        // The page header: a step up from the 40dp list rows.
        titleLeading = info?.let { app -> { AppIcon(app.packageName, appName = app.appName, size = SharedDimensions.iconContainerLg) } },
    )

    selectedConnection?.let { ct ->
        val model = connectionLogModel(ct)
        ConnectionSheetHost(
            request = ct.toConnectionSheetRequest(model.timeLabel, model.typeLabel, model.details),
            eventLogger = eventLogger,
            onDismiss = {
                selectedConnection = null
                reloadToken++
            },
            // Already on this app's page: no "Open <app>".
            navigation = connectionSheetNavigation.copy(onOpenApp = null),
            onRulesChanged = { reloadToken++ },
        )
    }
}

@Composable
private fun appInfoStrings(): RethinkAppInfoStrings {
    val wifiAllowed = stringResource(R.string.app_state_wifi_allowed)
    val wifiBlocked = stringResource(R.string.app_state_wifi_blocked)
    val mobileAllowed = stringResource(R.string.app_state_mobile_allowed)
    val mobileBlocked = stringResource(R.string.app_state_mobile_blocked)
    val resources = LocalContext.current.resources
    return RethinkAppInfoStrings(
        unavailable = stringResource(R.string.ada_noapp_dialog_message),
        loading = stringResource(R.string.lbl_loading),
        internetAccess = stringResource(R.string.app_internet_access),
        wifi = stringResource(R.string.ada_app_unmetered),
        mobile = stringResource(R.string.lbl_mobile_data),
        allowed = stringResource(R.string.lbl_allowed),
        blocked = stringResource(R.string.lbl_blocked),
        meteredFootnote = stringResource(R.string.app_metered_footnote),
        temporaryAllow = stringResource(R.string.temp_allow_label),
        stopTemporaryAccess = { minutes -> resources.getQuantityString(R.plurals.app_stop_temp_access, minutes, minutes) },
        recentActivity = stringResource(R.string.app_recent_activity),
        seeAllActivity = stringResource(R.string.app_see_all_activity),
        noRecentActivity = stringResource(R.string.app_no_recent_activity),
        rules = stringResource(R.string.lbl_rules),
        ipRules = stringResource(R.string.lbl_ip_rules),
        domainRules = stringResource(R.string.lbl_domain_rules),
        specialHandling = stringResource(R.string.app_special_handling),
        modeTitle = stringResource(R.string.ada_app_mode_title),
        normal = stringResource(R.string.ada_app_mode_normal),
        normalDescription = stringResource(R.string.ada_app_mode_normal_desc),
        trustedOnly = stringResource(R.string.ada_app_isolate),
        trustedOnlyDescription = stringResource(R.string.ada_app_isolate_desc),
        skipAllFiltering = stringResource(R.string.ada_app_bypass_dns_firewall),
        skipAllFilteringDescription = stringResource(R.string.ada_app_bypass_dns_firewall_desc),
        skipUniversalRules = stringResource(R.string.ada_app_bypass_univ),
        skipUniversalRulesDescription = stringResource(R.string.ada_app_bypass_univ_desc),
        exclude = stringResource(R.string.ada_app_exclude),
        excludeDescription = stringResource(R.string.ada_app_exclude_desc),
        excludeWarning = stringResource(R.string.app_mode_exclude_warning),
        skipAllFilteringWarning = stringResource(R.string.app_mode_skip_all_warning),
        apply = stringResource(R.string.lbl_apply),
        cancel = stringResource(R.string.lbl_cancel),
        more = stringResource(R.string.app_more),
        proxyExclude = stringResource(R.string.exclude_apps_from_proxy),
        proxyExcludeDescription = stringResource(R.string.exclude_apps_from_proxy_desc),
        systemAppInfo = stringResource(R.string.about_settings_app_info),
        wifiState = { blocked -> if (blocked) wifiBlocked else wifiAllowed },
        mobileState = { blocked -> if (blocked) mobileBlocked else mobileAllowed },
    )
}

private data class AppInfoLoad(
    val info: AppInfo,
    val appStatus: FirewallStatus,
    val connStatus: ConnectionStatus,
    val isProxyExcluded: Boolean,
    val tempAllowExpiry: Long,
    val proxyDetails: String,
    val anyProxySetUp: Boolean,
)

/** The app behind [uid], or null when it isn't installed (or was uninstalled). */
private suspend fun loadAppInfo(uid: Int, wireguardAppsProxyMapDesc: String, appConfig: AppConfig): AppInfoLoad? {
    val info = FirewallManager.getAppInfoByUid(uid)
    if (info == null || info.tombstoneTs > 0) return null
    val proxy = ProxyManager.getProxyIdForApp(uid).takeIf { it.isNotEmpty() && it != ID_NONE }
        ?.let { wireguardAppsProxyMapDesc.format(it) }
        .orEmpty()
    return AppInfoLoad(
        info = info,
        appStatus = FirewallManager.appStatus(info.uid),
        connStatus = FirewallManager.connectionStatus(info.uid),
        isProxyExcluded = info.isProxyExcluded,
        tempAllowExpiry = FirewallManager.tempAllowExpiry(info.uid),
        proxyDetails = proxy,
        anyProxySetUp = anyProxySetUp(appConfig),
    )
}

/**
 * Whether any proxy is set up or on, from the same reads as the Proxy & VPN screen: a WireGuard
 * tunnel exists, a SOCKS5 or HTTP server is saved, or a proxy (Orbot included) is on.
 */
private suspend fun anyProxySetUp(appConfig: AppConfig): Boolean =
    appConfig.isProxyEnabled() ||
        appConfig.isOrbotProxyEnabled() ||
        WireguardManager.getNumberOfMappings() > 0 ||
        !runCatching { appConfig.getSocks5ProxyDetails() }.getOrNull()?.proxyIP.isNullOrBlank() ||
        !runCatching { appConfig.getHttpProxyDetails() }.getOrNull()?.proxyIP.isNullOrBlank()

private fun logChange(eventLogger: EventLogger, app: AppInfo, status: FirewallStatus, connection: ConnectionStatus, undo: Boolean = false) {
    eventLogger.log(
        type = EventType.FW_RULE_MODIFIED,
        severity = Severity.LOW,
        message = if (undo) "Firewall status restored" else "Firewall status changed",
        source = EventSource.MANAGER,
        userAction = true,
        details = "Firewall status ${if (undo) "restored" else "changed"} for ${app.appName} (${app.uid}), new status: $status, conn status: $connection",
    )
}
