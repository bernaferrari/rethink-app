/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.apps

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkAppSort
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.AppInfo
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.RefreshDatabase
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.database.hasInternetPermission
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.service.ProxyManager
import com.bernaferrari.bravedns.ui.compose.common.AppIcon
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallApp
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallAppListScreen
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallAppListStrings
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallAppStatus
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkAppSelectionAction
import com.bernaferrari.bravedns.ui.compose.firewall.showRuleUndoSnackbar
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallFilter
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallFilters
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallTopLevelFilter
import com.bernaferrari.bravedns.ui.compose.firewall.rethinkUsageText
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.formatByteCount
import com.bernaferrari.bravedns.viewmodel.AppConnectionStatus
import com.bernaferrari.bravedns.viewmodel.AppInfoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun defaultAppFilters() =
    RethinkFirewallFilters(topLevel = RethinkFirewallTopLevelFilter.Installed)

/** Android data/service adapter for the portable firewall app-list renderer. */
@Composable
fun AppListScreen(
    viewModel: AppInfoViewModel,
    eventLogger: EventLogger,
    refreshDatabase: RefreshDatabase,
    onAppClick: ((Int) -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = org.koin.compose.koinInject<com.bernaferrari.bravedns.service.PersistentState>()
    var sortOrder by rememberSaveable {
        mutableStateOf(RethinkAppSort.entries.firstOrNull { it.name == preferences.appListSortName } ?: RethinkAppSort.Name)
    }
    val refreshCompleteText = stringResource(R.string.refresh_complete)
    var queryText by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    var currentFilters by remember { mutableStateOf(defaultAppFilters()) }
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.lbl_undo)
    val resources = context.resources
    var pendingToggle by remember { mutableStateOf<AndroidFirewallToggleRequest?>(null) }

    fun applyFilters(filters: RethinkFirewallFilters) {
        currentFilters = filters
        queryText = filters.query
        viewModel.setFilter(filters)
    }

    /** Select mode: change exactly [apps]' UIDs, then offer Undo back to each app's own rule. */
    fun applySelection(action: RethinkAppSelectionAction, apps: List<RethinkFirewallApp>) {
        val uids = apps.map { it.uid }.toSet()
        if (uids.isEmpty()) return
        scope.launch {
            val previous = withContext(Dispatchers.IO + NonCancellable) { viewModel.applyToSelected(uids, action) }
            eventLogger.log(
                EventType.FW_RULE_MODIFIED,
                Severity.LOW,
                "App list, selected apps",
                EventSource.UI,
                true,
                "Selection ${action.name} for UIDs ${uids.joinToString()}",
            )
            val message = resources.getQuantityString(R.plurals.apps_bulk_done, uids.size, uids.size)
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showRuleUndoSnackbar(message, undoLabel)) {
                withContext(Dispatchers.IO + NonCancellable) { viewModel.restore(previous) }
                eventLogger.log(
                    EventType.FW_RULE_MODIFIED,
                    Severity.LOW,
                    "App list, selected apps",
                    EventSource.UI,
                    true,
                    "Undo selection ${action.name} for UIDs ${uids.joinToString()}",
                )
            }
        }
    }

    fun refreshAppList(action: Int = RefreshDatabase.ACTION_REFRESH_INTERACTIVE, showToast: Boolean = true) {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch(Dispatchers.IO) {
            refreshDatabase.refresh(action) {
                scope.launch(Dispatchers.Main) {
                    isRefreshing = false
                    if (showToast) Utilities.showToastUiCentered(context, refreshCompleteText, Toast.LENGTH_SHORT)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        applyFilters(defaultAppFilters())
        if (withContext(Dispatchers.IO) { viewModel.getAppCount() } == 0) {
            refreshAppList(RefreshDatabase.ACTION_REFRESH_FORCE, showToast = false)
        }
    }

    val loadedApps by viewModel.appInfo.collectAsState()
    // Null until the first load: the list shows loading, not "No apps found" / "Search 0 apps".
    val portableApps = remember(loadedApps, context) {
        loadedApps?.map { it.toRethinkFirewallApp(context) }
    }
    val haptics = LocalHapticFeedback.current

    /** One row's Wi‑Fi / mobile data tap: write it now, then offer Undo back to the app's exact rule. */
    fun commitConnectionToggle(app: RethinkFirewallApp, wifi: Boolean) {
        // As App detail's tiles: the toggle haptic follows the state being left.
        val wasBlocked = if (wifi) app.wifiBlocked else app.mobileBlocked
        haptics.performHapticFeedback(if (wasBlocked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        scope.launch {
            val change = withContext(Dispatchers.IO + NonCancellable) { viewModel.toggleNetwork(app.uid, wifi) }
            logToggle(eventLogger, app, change.after.connection.name)
            val message = resources.getString(networkToggleMessage(change.after.connection, wifi), app.appName)
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showRuleUndoSnackbar(message, undoLabel)) {
                val restored = withContext(Dispatchers.IO + NonCancellable) { viewModel.undo(change) }
                if (restored) logToggle(eventLogger, app, change.before.connection.name, undo = true)
            }
        }
    }

    fun requestConnectionToggle(app: RethinkFirewallApp, wifi: Boolean) {
        scope.launch {
            val packages = withContext(Dispatchers.IO) { FirewallManager.getAppNamesByUid(app.uid) }
            if (packages.size > 1) {
                pendingToggle = AndroidFirewallToggleRequest(app, wifi, packages)
            } else {
                commitConnectionToggle(app, wifi)
            }
        }
    }

    RethinkFirewallAppListScreen(
        filterState = com.bernaferrari.bravedns.ui.compose.firewall.RethinkAppListFilterState(queryText, currentFilters.status, currentFilters),
        sorting = com.bernaferrari.bravedns.ui.compose.firewall.RethinkAppSorting(sortOrder) { sortOrder = it; preferences.appListSortName = it.name },
        apps = portableApps,
        strings = androidFirewallAppListStrings(),
        isRefreshing = isRefreshing,
        onQueryChange = { query -> applyFilters(currentFilters.copy(query = query)) },
        onRefresh = { refreshAppList() },
        onFiltersChange = ::applyFilters,
        onQuickFilterChange = { filter -> applyFilters(currentFilters.copy(status = filter)) },
        onSelectionAction = ::applySelection,
        loadCategories = { topLevel ->
            withContext(Dispatchers.IO) {
                when (topLevel) {
                    RethinkFirewallTopLevelFilter.Installed -> FirewallManager.getCategoriesForInstalledApps()
                    RethinkFirewallTopLevelFilter.System -> FirewallManager.getCategoriesForSystemApps()
                    RethinkFirewallTopLevelFilter.All -> FirewallManager.getAllCategories()
                }
            }
        },
        onAppClick = { app -> onAppClick?.invoke(app.uid) },
        onWifiToggle = { app -> requestConnectionToggle(app, wifi = true) },
        onMobileToggle = { app -> requestConnectionToggle(app, wifi = false) },
        appIcon = { app -> AppIcon(app.packageName, appName = app.appName) },
        onBackClick = onBackClick,
        loadAlsoAffects = { apps ->
            // A rule is per UID: name the other packages that share a selected UID.
            val selectedNames = apps.map { it.appName }.toSet()
            withContext(Dispatchers.IO) {
                apps.map { it.uid }.distinct().flatMap { FirewallManager.getAppNamesByUid(it) }
            }.filterNot { it in selectedNames }.distinct()
        },
        snackbarHostState = snackbar,
        selectionBackHandler = { enabled, onBack -> BackHandler(enabled = enabled, onBack = onBack) },
    )

    pendingToggle?.let { request ->
        RethinkConfirmDialog(
            onDismissRequest = { pendingToggle = null },
            title = stringResource(R.string.ctbs_block_other_apps, request.app.appName, request.packages.size.toString()),
            confirmText = stringResource(R.string.lbl_proceed),
            dismissText = stringResource(R.string.ctbs_dialog_negative_btn),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                    request.packages.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            onConfirm = {
                pendingToggle = null
                commitConnectionToggle(request.app, request.wifi)
            },
            onDismiss = { pendingToggle = null },
        )
    }
}

private data class AndroidFirewallToggleRequest(
    val app: RethinkFirewallApp,
    val wifi: Boolean,
    val packages: List<String>,
)

@Composable
private fun androidFirewallAppListStrings(): RethinkFirewallAppListStrings {
    val blocked = stringResource(R.string.lbl_blocked)
    val bypass = stringResource(R.string.fapps_firewall_filter_bypass_universal)
    val skipAll = stringResource(R.string.ada_app_status_bypass_dns_firewall)
    val excluded = stringResource(R.string.fapps_firewall_filter_excluded)
    val lockdown = stringResource(R.string.fapps_firewall_filter_isolate)
    val resources = LocalContext.current.resources
    return RethinkFirewallAppListStrings(
        sortTitle = stringResource(R.string.port_sort_title),
        uidLabel = { stringResource(R.string.port_uid_number, it) },
        sortLabels = listOf(stringResource(R.string.port_sort_name), stringResource(R.string.port_sort_package), stringResource(R.string.port_sort_uid)),
        title = stringResource(R.string.apps_info_title),
        searchHint = { count -> stringResource(R.string.search_apps_count_placeholder, count) },
        searchHintLoading = stringResource(R.string.apps_search_hint),
        loading = stringResource(R.string.lbl_loading),
        refresh = stringResource(R.string.cd_refresh),
        filter = stringResource(R.string.cd_filter),
        moreOptions = stringResource(R.string.cd_more),
        more = stringResource(R.string.app_more),
        clearSearch = stringResource(R.string.cd_clear_search),
        emptyTitle = stringResource(R.string.fapps_empty_title),
        emptyDescription = stringResource(R.string.fapps_empty_subtitle),
        view = stringResource(R.string.lbl_view),
        installed = stringResource(R.string.fapps_filter_parent_installed),
        system = stringResource(R.string.fapps_filter_parent_system),
        all = stringResource(R.string.lbl_all),
        status = stringResource(R.string.lbl_status),
        categories = stringResource(R.string.fapps_filter_categories_heading),
        clear = stringResource(R.string.fapps_filter_clear_btn),
        cancel = stringResource(R.string.lbl_cancel),
        close = stringResource(R.string.lbl_done),
        proxy = stringResource(R.string.lbl_proxy),
        // State first ("Wi‑Fi: blocked"), the double-tap action separately ("Allow on Wi‑Fi").
        wifiStateLabel = { isBlocked -> stringResource(if (isBlocked) R.string.app_state_wifi_blocked else R.string.app_state_wifi_allowed) },
        mobileStateLabel = { isBlocked -> stringResource(if (isBlocked) R.string.app_state_mobile_blocked else R.string.app_state_mobile_allowed) },
        wifiActionLabel = { isBlocked -> stringResource(if (isBlocked) R.string.apps_action_allow_on_wifi else R.string.apps_action_block_on_wifi) },
        mobileActionLabel = { isBlocked -> stringResource(if (isBlocked) R.string.apps_action_allow_on_mobile else R.string.apps_action_block_on_mobile) },
        select = stringResource(R.string.apps_select),
        exitSelect = stringResource(R.string.apps_exit_select),
        selectAll = stringResource(R.string.lbl_select_all),
        selectedCount = { count -> pluralStringResource(R.plurals.apps_selected_count, count, count) },
        selectionActionLabel = { action ->
            when (action) {
                RethinkAppSelectionAction.BlockWifi -> stringResource(R.string.apps_action_block_wifi)
                RethinkAppSelectionAction.BlockMobile -> stringResource(R.string.apps_action_block_mobile)
                RethinkAppSelectionAction.AllowAll -> stringResource(R.string.apps_action_allow_all)
                RethinkAppSelectionAction.AllowWifi -> stringResource(R.string.apps_action_allow_wifi)
                RethinkAppSelectionAction.AllowMobile -> stringResource(R.string.apps_action_allow_mobile)
                RethinkAppSelectionAction.TrustedOnly -> stringResource(R.string.ada_app_isolate)
                RethinkAppSelectionAction.SkipUniversalRules -> stringResource(R.string.ada_app_bypass_univ)
                RethinkAppSelectionAction.SkipAllFiltering -> stringResource(R.string.ada_app_bypass_dns_firewall)
                RethinkAppSelectionAction.Exclude -> stringResource(R.string.ada_app_exclude)
            }
        },
        selectionConfirmTitle = { action, count ->
            when (action) {
                RethinkAppSelectionAction.BlockWifi -> pluralStringResource(R.plurals.apps_confirm_block_wifi, count, count)
                RethinkAppSelectionAction.BlockMobile -> pluralStringResource(R.plurals.apps_confirm_block_mobile, count, count)
                RethinkAppSelectionAction.AllowAll -> pluralStringResource(R.plurals.apps_confirm_allow_all, count, count)
                RethinkAppSelectionAction.AllowWifi -> pluralStringResource(R.plurals.apps_confirm_allow_wifi, count, count)
                RethinkAppSelectionAction.AllowMobile -> pluralStringResource(R.plurals.apps_confirm_allow_mobile, count, count)
                RethinkAppSelectionAction.TrustedOnly -> resources.getQuantityString(R.plurals.apps_confirm_mode, count, count, stringResource(R.string.ada_app_isolate))
                RethinkAppSelectionAction.SkipUniversalRules -> resources.getQuantityString(R.plurals.apps_confirm_mode, count, count, stringResource(R.string.ada_app_bypass_univ))
                RethinkAppSelectionAction.SkipAllFiltering -> resources.getQuantityString(R.plurals.apps_confirm_mode, count, count, stringResource(R.string.ada_app_bypass_dns_firewall))
                RethinkAppSelectionAction.Exclude -> resources.getQuantityString(R.plurals.apps_confirm_mode, count, count, stringResource(R.string.ada_app_exclude))
            }
        },
        selectionNetworkMessage = stringResource(R.string.apps_confirm_network_message),
        selectionModeMessage = stringResource(R.string.apps_confirm_mode_message),
        alsoAffects = { names -> stringResource(R.string.apps_also_affects, names) },
        filterLabel = { filter ->
            when (filter) {
                RethinkFirewallFilter.All -> stringResource(R.string.lbl_all)
                RethinkFirewallFilter.Allowed -> stringResource(R.string.lbl_allowed)
                RethinkFirewallFilter.Blocked -> blocked
                RethinkFirewallFilter.Bypass -> bypass
                RethinkFirewallFilter.SkipAllFiltering -> skipAll
                RethinkFirewallFilter.Excluded -> excluded
                RethinkFirewallFilter.Lockdown -> lockdown
                RethinkFirewallFilter.BlockedWifi -> stringResource(R.string.ada_app_status_block_wifi)
                RethinkFirewallFilter.BlockedMobile -> stringResource(R.string.ada_app_status_block_md)
            }
        },
        statusLabel = { status ->
            when (status) {
                RethinkFirewallAppStatus.Allowed -> ""
                RethinkFirewallAppStatus.Blocked -> blocked
                RethinkFirewallAppStatus.Bypass -> bypass
                RethinkFirewallAppStatus.SkipAllFiltering -> skipAll
                RethinkFirewallAppStatus.Excluded -> excluded
                RethinkFirewallAppStatus.Lockdown -> lockdown
                RethinkFirewallAppStatus.Unknown -> stringResource(R.string.network_log_app_name_unknown)
            }
        },
    )
}

private fun AppInfo.toRethinkFirewallApp(
    context: Context,
): RethinkFirewallApp {
    val appStatus = FirewallManager.FirewallStatus.getStatus(firewallStatus)
    val connection = FirewallManager.ConnectionStatus.getStatus(connectionStatus)
    val status = when (appStatus) {
        FirewallManager.FirewallStatus.NONE -> if (connection == FirewallManager.ConnectionStatus.ALLOW) RethinkFirewallAppStatus.Allowed else RethinkFirewallAppStatus.Blocked
        FirewallManager.FirewallStatus.BYPASS_UNIVERSAL -> RethinkFirewallAppStatus.Bypass
        FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL -> RethinkFirewallAppStatus.SkipAllFiltering
        FirewallManager.FirewallStatus.EXCLUDE -> RethinkFirewallAppStatus.Excluded
        FirewallManager.FirewallStatus.ISOLATE -> RethinkFirewallAppStatus.Lockdown
        FirewallManager.FirewallStatus.UNTRACKED -> RethinkFirewallAppStatus.Unknown
    }
    return RethinkFirewallApp(
        id = "$uid:$packageName",
        uid = uid,
        appName = appName,
        packageName = packageName,
        status = status,
        wifiBlocked = appStatus == FirewallManager.FirewallStatus.NONE && (connection == FirewallManager.ConnectionStatus.UNMETERED || connection == FirewallManager.ConnectionStatus.BOTH),
        mobileBlocked = appStatus == FirewallManager.FirewallStatus.NONE && (connection == FirewallManager.ConnectionStatus.METERED || connection == FirewallManager.ConnectionStatus.BOTH),
        dataUsage = dataUsageText(this),
        proxyEnabled = !isProxyExcluded &&
            ProxyManager.getProxyIdForApp(uid).let { it.isNotEmpty() && it != ProxyManager.ID_NONE },
        hasInternetPermission = hasInternetPermission(context.packageManager),
        tombstoned = tombstoneTs > 0,
        canToggleConnections = packageName != context.packageName,
    )
}

private fun dataUsageText(app: AppInfo): String? {
    if (app.uploadBytes <= 0L && app.downloadBytes <= 0L) return null
    return rethinkUsageText(
        download = formatByteCount(app.downloadBytes),
        upload = formatByteCount(app.uploadBytes),
    )
}

/** The snackbar line for a row tap that left the app with [connection]. */
private fun networkToggleMessage(connection: AppConnectionStatus, wifi: Boolean): Int = when {
    wifi && (connection == AppConnectionStatus.Unmetered || connection == AppConnectionStatus.Both) -> R.string.app_done_blocked_wifi
    wifi -> R.string.connection_done_allowed_wifi
    connection == AppConnectionStatus.Metered || connection == AppConnectionStatus.Both -> R.string.app_done_blocked_mobile
    else -> R.string.connection_done_allowed_mobile
}

private fun logToggle(eventLogger: EventLogger, app: RethinkFirewallApp, connection: String, undo: Boolean = false) {
    eventLogger.log(
        EventType.FW_RULE_MODIFIED,
        Severity.LOW,
        "App list, rule change",
        EventSource.UI,
        true,
        "${if (undo) "Undo: " else ""}UID: ${app.uid}, App: ${app.appName}, New FW status: $connection",
    )
}
