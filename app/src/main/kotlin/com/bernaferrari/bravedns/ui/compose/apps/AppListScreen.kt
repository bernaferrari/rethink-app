/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.apps

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.bernaferrari.bravedns.ui.compose.rememberDrawablePainter
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallApp
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallAppListScreen
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallAppListStrings
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallAppStatus
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallBulkAction
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallBulkDialogCopy
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallFilter
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallFilters
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallTopLevelFilter
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.Utilities.getIcon
import com.bernaferrari.bravedns.viewmodel.AppInfoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val refreshCompleteText = stringResource(R.string.refresh_complete)
    var queryText by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    var currentFilters by remember { mutableStateOf(defaultAppFilters()) }
    var bulkWifi by remember { mutableStateOf(false) }
    var bulkMobile by remember { mutableStateOf(false) }
    var bulkBypass by remember { mutableStateOf(false) }
    var bulkBypassDns by remember { mutableStateOf(false) }
    var bulkExclude by remember { mutableStateOf(false) }
    var bulkLockdown by remember { mutableStateOf(false) }
    var pendingToggle by remember { mutableStateOf<AndroidFirewallToggleRequest?>(null) }

    val dialogs = AndroidBulkDialogCopy(
        wifiEnableTitle = stringResource(R.string.fapps_unmetered_block_dialog_title),
        wifiDisableTitle = stringResource(R.string.fapps_unmetered_unblock_dialog_title),
        mobileEnableTitle = stringResource(R.string.fapps_metered_block_dialog_title),
        mobileDisableTitle = stringResource(R.string.fapps_metered_unblock_dialog_title),
        lockdownEnableTitle = stringResource(R.string.fapps_isolate_block_dialog_title),
        bypassEnableTitle = stringResource(R.string.fapps_bypass_block_dialog_title),
        excludeEnableTitle = stringResource(R.string.fapps_exclude_block_dialog_title),
        bypassDnsEnableTitle = stringResource(R.string.fapps_bypass_dns_firewall_dialog_title),
        genericDisableTitle = stringResource(R.string.fapps_unblock_dialog_title),
        wifiEnableMessage = stringResource(R.string.fapps_unmetered_block_dialog_message),
        wifiDisableMessage = stringResource(R.string.fapps_unmetered_unblock_dialog_message),
        mobileEnableMessage = stringResource(R.string.fapps_metered_block_dialog_message),
        mobileDisableMessage = stringResource(R.string.fapps_metered_unblock_dialog_message),
        lockdownEnableMessage = stringResource(R.string.fapps_isolate_block_dialog_message),
        bypassEnableMessage = stringResource(R.string.fapps_bypass_block_dialog_message),
        bypassDnsEnableMessage = stringResource(R.string.fapps_bypass_dns_firewall_dialog_message),
        excludeEnableMessage = stringResource(R.string.fapps_exclude_block_dialog_message),
        genericDisableMessage = stringResource(R.string.fapps_unblock_dialog_message),
    )

    fun applyFilters(filters: RethinkFirewallFilters) {
        currentFilters = filters
        queryText = filters.query
        viewModel.setFilter(filters)
    }

    fun resetBulkStates(type: RethinkFirewallBulkAction) {
        when (type) {
            RethinkFirewallBulkAction.Wifi -> { bulkMobile = false; bulkBypass = false; bulkBypassDns = false; bulkExclude = false; bulkLockdown = false }
            RethinkFirewallBulkAction.Mobile -> { bulkWifi = false; bulkBypass = false; bulkBypassDns = false; bulkExclude = false; bulkLockdown = false }
            RethinkFirewallBulkAction.Lockdown -> { bulkWifi = false; bulkMobile = false; bulkBypass = false; bulkBypassDns = false; bulkExclude = false }
            RethinkFirewallBulkAction.Bypass -> { bulkWifi = false; bulkMobile = false; bulkBypassDns = false; bulkExclude = false; bulkLockdown = false }
            RethinkFirewallBulkAction.BypassDns -> { bulkWifi = false; bulkMobile = false; bulkBypass = false; bulkExclude = false; bulkLockdown = false }
            RethinkFirewallBulkAction.Exclude -> { bulkWifi = false; bulkMobile = false; bulkBypass = false; bulkBypassDns = false; bulkLockdown = false }
        }
    }

    fun updateBulkRules(type: RethinkFirewallBulkAction) {
        scope.launch(Dispatchers.IO) {
            val enabled = when (type) {
                RethinkFirewallBulkAction.Wifi -> !bulkWifi
                RethinkFirewallBulkAction.Mobile -> !bulkMobile
                RethinkFirewallBulkAction.Bypass -> !bulkBypass
                RethinkFirewallBulkAction.BypassDns -> !bulkBypassDns
                RethinkFirewallBulkAction.Exclude -> !bulkExclude
                RethinkFirewallBulkAction.Lockdown -> !bulkLockdown
            }
            when (type) {
                RethinkFirewallBulkAction.Wifi -> viewModel.updateUnmeteredStatus(enabled)
                RethinkFirewallBulkAction.Mobile -> viewModel.updateMeteredStatus(enabled)
                RethinkFirewallBulkAction.Bypass -> viewModel.updateBypassStatus(enabled)
                RethinkFirewallBulkAction.BypassDns -> viewModel.updateBypassDnsFirewall(enabled)
                RethinkFirewallBulkAction.Exclude -> viewModel.updateExcludeStatus(enabled)
                RethinkFirewallBulkAction.Lockdown -> viewModel.updateLockdownStatus(enabled)
            }
            withContext(Dispatchers.Main) {
                when (type) {
                    RethinkFirewallBulkAction.Wifi -> bulkWifi = enabled
                    RethinkFirewallBulkAction.Mobile -> bulkMobile = enabled
                    RethinkFirewallBulkAction.Bypass -> bulkBypass = enabled
                    RethinkFirewallBulkAction.BypassDns -> bulkBypassDns = enabled
                    RethinkFirewallBulkAction.Exclude -> bulkExclude = enabled
                    RethinkFirewallBulkAction.Lockdown -> bulkLockdown = enabled
                }
                resetBulkStates(type)
            }
            eventLogger.log(
                EventType.FW_RULE_MODIFIED,
                Severity.LOW,
                "App list, bulk change",
                EventSource.UI,
                false,
                "Bulk ${type.name.lowercase()} rule update, enabled: $enabled",
            )
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
    val portableApps = remember(loadedApps, context) { loadedApps.map { it.toRethinkFirewallApp(context) } }
    fun requestConnectionToggle(app: RethinkFirewallApp, wifi: Boolean) {
        scope.launch(Dispatchers.IO) {
            val packages = FirewallManager.getAppNamesByUid(app.uid)
            if (packages.size > 1) {
                withContext(Dispatchers.Main) { pendingToggle = AndroidFirewallToggleRequest(app, wifi, packages) }
            } else {
                toggleFirewallConnection(eventLogger, app, wifi)
            }
        }
    }

    RethinkFirewallAppListScreen(
        apps = portableApps,
        query = queryText,
        selectedQuickFilter = currentFilters.status,
        filters = currentFilters,
        activeBulkActions = buildSet {
            if (bulkWifi) add(RethinkFirewallBulkAction.Wifi)
            if (bulkMobile) add(RethinkFirewallBulkAction.Mobile)
            if (bulkBypass) add(RethinkFirewallBulkAction.Bypass)
            if (bulkBypassDns) add(RethinkFirewallBulkAction.BypassDns)
            if (bulkExclude) add(RethinkFirewallBulkAction.Exclude)
            if (bulkLockdown) add(RethinkFirewallBulkAction.Lockdown)
        },
        strings = androidFirewallAppListStrings(dialogs),
        isRefreshing = isRefreshing,
        onQueryChange = { query -> applyFilters(currentFilters.copy(query = query)) },
        onRefresh = { refreshAppList() },
        onFiltersChange = ::applyFilters,
        onQuickFilterChange = { filter -> applyFilters(currentFilters.copy(status = filter)) },
        onBulkAction = ::updateBulkRules,
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
        appIcon = { app -> AndroidFirewallAppIcon(app) },
        onBackClick = onBackClick,
    )

    pendingToggle?.let { request ->
        RethinkConfirmDialog(
            onDismissRequest = { pendingToggle = null },
            title = stringResource(R.string.ctbs_block_other_apps, request.app.appName, request.packages.size.toString()),
            confirmText = stringResource(R.string.lbl_proceed),
            dismissText = stringResource(R.string.ctbs_dialog_negative_btn),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    request.packages.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            onConfirm = {
                pendingToggle = null
                scope.launch(Dispatchers.IO) { toggleFirewallConnection(eventLogger, request.app, request.wifi) }
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

private data class AndroidBulkDialogCopy(
    val wifiEnableTitle: String,
    val wifiDisableTitle: String,
    val mobileEnableTitle: String,
    val mobileDisableTitle: String,
    val lockdownEnableTitle: String,
    val bypassEnableTitle: String,
    val excludeEnableTitle: String,
    val bypassDnsEnableTitle: String,
    val genericDisableTitle: String,
    val wifiEnableMessage: String,
    val wifiDisableMessage: String,
    val mobileEnableMessage: String,
    val mobileDisableMessage: String,
    val lockdownEnableMessage: String,
    val bypassEnableMessage: String,
    val bypassDnsEnableMessage: String,
    val excludeEnableMessage: String,
    val genericDisableMessage: String,
) {
    fun copyFor(action: RethinkFirewallBulkAction, active: Boolean): RethinkFirewallBulkDialogCopy = when (action) {
        RethinkFirewallBulkAction.Wifi -> RethinkFirewallBulkDialogCopy(if (active) wifiDisableTitle else wifiEnableTitle, if (active) wifiDisableMessage else wifiEnableMessage)
        RethinkFirewallBulkAction.Mobile -> RethinkFirewallBulkDialogCopy(if (active) mobileDisableTitle else mobileEnableTitle, if (active) mobileDisableMessage else mobileEnableMessage)
        RethinkFirewallBulkAction.Bypass -> RethinkFirewallBulkDialogCopy(if (active) genericDisableTitle else bypassEnableTitle, if (active) genericDisableMessage else bypassEnableMessage)
        RethinkFirewallBulkAction.BypassDns -> RethinkFirewallBulkDialogCopy(if (active) genericDisableTitle else bypassDnsEnableTitle, if (active) genericDisableMessage else bypassDnsEnableMessage)
        RethinkFirewallBulkAction.Exclude -> RethinkFirewallBulkDialogCopy(if (active) genericDisableTitle else excludeEnableTitle, if (active) genericDisableMessage else excludeEnableMessage)
        RethinkFirewallBulkAction.Lockdown -> RethinkFirewallBulkDialogCopy(if (active) genericDisableTitle else lockdownEnableTitle, if (active) genericDisableMessage else lockdownEnableMessage)
    }
}

@Composable
private fun androidFirewallAppListStrings(dialogs: AndroidBulkDialogCopy): RethinkFirewallAppListStrings {
    val blocked = stringResource(R.string.lbl_blocked)
    val bypass = stringResource(R.string.fapps_firewall_filter_bypass_universal)
    val excluded = stringResource(R.string.fapps_firewall_filter_excluded)
    val lockdown = stringResource(R.string.fapps_firewall_filter_isolate)
    val wifi = stringResource(R.string.ada_app_unmetered)
    val mobile = stringResource(R.string.lbl_mobile_data)
    return RethinkFirewallAppListStrings(
        title = stringResource(R.string.apps_info_title),
        searchHint = { count -> stringResource(R.string.search_apps_count_placeholder, count) },
        refresh = stringResource(R.string.cd_refresh),
        filter = stringResource(R.string.cd_filter),
        rules = stringResource(R.string.lbl_rules),
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
        noCategories = stringResource(R.string.fapps_empty_subtitle),
        apply = stringResource(R.string.lbl_apply),
        cancel = stringResource(R.string.lbl_cancel),
        enabled = stringResource(R.string.lbbs_enabled),
        disabled = stringResource(R.string.lbl_disabled),
        bulkDescription = stringResource(R.string.fapps_info_dialog_message),
        selectedApps = { count -> stringResource(R.string.two_argument_colon, stringResource(R.string.lbl_apply), count.toString()) },
        actionLabel = { action ->
            when (action) {
                RethinkFirewallBulkAction.Wifi -> wifi
                RethinkFirewallBulkAction.Mobile -> mobile
                RethinkFirewallBulkAction.Bypass -> bypass
                RethinkFirewallBulkAction.BypassDns -> stringResource(R.string.bypass_dns_firewall)
                RethinkFirewallBulkAction.Exclude -> excluded
                RethinkFirewallBulkAction.Lockdown -> lockdown
            }
        },
        actionDescription = { action ->
            when (action) {
                RethinkFirewallBulkAction.Wifi -> stringResource(R.string.fapps_info_unmetered_msg)
                RethinkFirewallBulkAction.Mobile -> stringResource(R.string.fapps_info_metered_msg)
                RethinkFirewallBulkAction.Bypass -> stringResource(R.string.fapps_info_bypass_msg)
                RethinkFirewallBulkAction.BypassDns -> stringResource(R.string.fapps_info_bypass_dns_firewall_msg)
                RethinkFirewallBulkAction.Exclude -> stringResource(R.string.fapps_info_exclude_msg)
                RethinkFirewallBulkAction.Lockdown -> stringResource(R.string.fapps_info_isolate_msg)
            }
        },
        bulkDialogCopy = dialogs::copyFor,
        filterLabel = { filter ->
            when (filter) {
                RethinkFirewallFilter.All -> stringResource(R.string.lbl_all)
                RethinkFirewallFilter.Allowed -> stringResource(R.string.lbl_allowed)
                RethinkFirewallFilter.Blocked -> blocked
                RethinkFirewallFilter.Bypass -> bypass
                RethinkFirewallFilter.Excluded -> excluded
                RethinkFirewallFilter.Lockdown -> lockdown
                RethinkFirewallFilter.BlockedWifi -> stringResource(R.string.two_argument_colon, blocked, stringResource(R.string.firewall_rule_block_unmetered))
                RethinkFirewallFilter.BlockedMobile -> stringResource(R.string.two_argument_colon, blocked, mobile)
            }
        },
        statusLabel = { status ->
            when (status) {
                RethinkFirewallAppStatus.Allowed -> ""
                RethinkFirewallAppStatus.Blocked -> blocked
                RethinkFirewallAppStatus.Bypass -> bypass
                RethinkFirewallAppStatus.Excluded -> excluded
                RethinkFirewallAppStatus.Lockdown -> lockdown
                RethinkFirewallAppStatus.Unknown -> stringResource(R.string.network_log_app_name_unknown)
            }
        },
    )
}

private fun AppInfo.toRethinkFirewallApp(context: Context): RethinkFirewallApp {
    val appStatus = FirewallManager.FirewallStatus.getStatus(firewallStatus)
    val connection = FirewallManager.ConnectionStatus.getStatus(connectionStatus)
    val status = when (appStatus) {
        FirewallManager.FirewallStatus.NONE -> if (connection == FirewallManager.ConnectionStatus.ALLOW) RethinkFirewallAppStatus.Allowed else RethinkFirewallAppStatus.Blocked
        FirewallManager.FirewallStatus.BYPASS_UNIVERSAL, FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL -> RethinkFirewallAppStatus.Bypass
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
        dataUsage = dataUsageText(context, this),
        proxyEnabled = !isProxyExcluded && ProxyManager.getProxyIdForApp(uid) != ProxyManager.ID_NONE,
        hasInternetPermission = hasInternetPermission(context.packageManager),
        tombstoned = tombstoneTs > 0,
        canToggleConnections = packageName != context.packageName,
    )
}

private fun dataUsageText(context: Context, app: AppInfo): String? {
    if (app.uploadBytes <= 0L && app.downloadBytes <= 0L) return null
    val upload = context.getString(R.string.symbol_upload, Utilities.humanReadableByteCount(app.uploadBytes, true))
    val download = context.getString(R.string.symbol_download, Utilities.humanReadableByteCount(app.downloadBytes, true))
    return context.getString(R.string.two_argument, upload, download)
}

@Composable
private fun AndroidFirewallAppIcon(app: RethinkFirewallApp) {
    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf<Drawable?>(AndroidFirewallIconCache.get(app.packageName)) }
    LaunchedEffect(app.packageName, app.appName) {
        if (icon == null) {
            val loaded = withContext(Dispatchers.IO) { getIcon(context, app.packageName, app.appName) }
            icon = loaded
            AndroidFirewallIconCache.put(app.packageName, loaded)
        }
    }
    rememberDrawablePainter(icon ?: Utilities.getDefaultIcon(context))?.let { painter ->
        Image(painter, null, Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)))
    }
}

private val AndroidFirewallToggleMutex = Mutex()

private suspend fun toggleFirewallConnection(eventLogger: EventLogger, app: RethinkFirewallApp, wifi: Boolean) {
    AndroidFirewallToggleMutex.withLock {
        val current = FirewallManager.connectionStatus(app.uid)
        val next = if (wifi) {
            when (current) {
                FirewallManager.ConnectionStatus.METERED -> FirewallManager.ConnectionStatus.BOTH
                FirewallManager.ConnectionStatus.UNMETERED -> FirewallManager.ConnectionStatus.ALLOW
                FirewallManager.ConnectionStatus.BOTH -> FirewallManager.ConnectionStatus.METERED
                FirewallManager.ConnectionStatus.ALLOW -> FirewallManager.ConnectionStatus.UNMETERED
            }
        } else {
            when (current) {
                FirewallManager.ConnectionStatus.METERED -> FirewallManager.ConnectionStatus.ALLOW
                FirewallManager.ConnectionStatus.UNMETERED -> FirewallManager.ConnectionStatus.BOTH
                FirewallManager.ConnectionStatus.BOTH -> FirewallManager.ConnectionStatus.UNMETERED
                FirewallManager.ConnectionStatus.ALLOW -> FirewallManager.ConnectionStatus.METERED
            }
        }
        FirewallManager.updateFirewallStatus(app.uid, FirewallManager.FirewallStatus.NONE, next)
        eventLogger.log(
            EventType.FW_RULE_MODIFIED,
            Severity.LOW,
            "App list, rule change",
            EventSource.UI,
            false,
            "UID: ${app.uid}, App: ${app.appName}, New FW status: $next",
        )
    }
}

private object AndroidFirewallIconCache {
    private val cache = LruCache<String, Drawable>(256)
    fun get(packageName: String): Drawable? = cache.get(packageName)
    fun put(packageName: String, icon: Drawable?) { if (packageName.isNotBlank() && icon != null) cache.put(packageName, icon) }
}
