/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Android adapter for the common Home renderer. Keep platform resources and live VPN state here;
 * the actual Compose screen lives in :shared/commonMain.
 */
package com.bernaferrari.bravedns.ui.compose.home

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.AppBlockCount
import com.bernaferrari.bravedns.ui.compose.rememberDrawablePainter
import com.bernaferrari.bravedns.util.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class HomeProtectionStatus {
    Protected,
    UltraSecure,
    Failing,
    Waiting,
    Exposed,
    NoNetwork,
}

data class HomeScreenUiState(
    val isVpnActive: Boolean = false,
    val networkLogsCount: Long = 0,
    val dnsLogsCount: Long = 0,
    val protectionStatus: HomeProtectionStatus? = null,
    val isProtectionFailing: Boolean = false,
    val allowedToday: Long = 0,
    val blockedToday: Long = 0,
    val dnsBlockedToday: Long = 0,
    val topBlocked: List<AppBlockCount> = emptyList(),
    val dnsName: String = "",
)

@Composable
fun HomeScreen(
    uiState: HomeScreenUiState,
    onStartStopClick: () -> Unit,
    onActivityClick: (() -> Unit)? = null,
    onAppsClick: (() -> Unit)? = null,
    onLogsClick: (() -> Unit)? = null,
    onDnsClick: (() -> Unit)? = null,
    onAppClick: ((Int) -> Unit)? = null,
) {
    RethinkHomeScreen(
        uiState = uiState.toCommonUiState(),
        strings = androidHomeStrings(),
        onStartStopClick = onStartStopClick,
        onActivityClick = onActivityClick,
        onAppsClick = onAppsClick,
        onLogsClick = onLogsClick,
        onDnsClick = onDnsClick,
        onBlockedAppClick = onAppClick?.let { open -> { id: String -> id.toIntOrNull()?.let(open) } },
    )
}

@Composable
private fun HomeScreenUiState.toCommonUiState() = RethinkHomeUiState(
    isVpnActive = isVpnActive,
    networkLogsCount = networkLogsCount,
    dnsLogsCount = dnsLogsCount,
    protectionStatus = protectionStatusLabel(protectionStatus),
    isProtectionFailing = isProtectionFailing,
    allowedToday = allowedToday,
    blockedToday = blockedToday,
    dnsBlockedToday = dnsBlockedToday,
    topBlocked = topBlocked.map { app ->
        RethinkHomeBlockedApp(
            id = app.uid.toString(),
            label = app.appName.ifBlank { app.packageName },
            count = app.count,
            icon = { PackageIcon(app.packageName, app.appName) },
        )
    },
    dnsName = dnsName,
)

@Composable
private fun PackageIcon(packageName: String, label: String) {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(packageName) {
        icon = withContext(Dispatchers.IO) { Utilities.getIcon(context, packageName, label) }
    }
    icon?.let { drawable ->
        rememberDrawablePainter(drawable)?.let { painter ->
            Image(painter = painter, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun protectionStatusLabel(status: HomeProtectionStatus?): String {
    if (status == null) return ""
    return stringResource(
        when (status) {
            HomeProtectionStatus.Protected -> R.string.home_protected
            HomeProtectionStatus.UltraSecure -> R.string.lbl_ultra_secure
            HomeProtectionStatus.Failing -> R.string.status_failing
            HomeProtectionStatus.Waiting -> R.string.status_waiting
            HomeProtectionStatus.Exposed -> R.string.status_exposed
            HomeProtectionStatus.NoNetwork -> R.string.status_no_network
        }
    )
}

@Composable
private fun androidHomeStrings() = RethinkHomeStrings(
    productName = stringResource(R.string.app_name),
    protected = stringResource(R.string.home_protected),
    notActive = stringResource(R.string.home_not_active),
    start = stringResource(R.string.lbl_start),
    stop = stringResource(R.string.lbl_stop),
    protectedSubtitle = stringResource(R.string.home_protected_subtitle),
    inactiveSubtitle = stringResource(R.string.home_inactive_subtitle),
    failingSubtitle = stringResource(R.string.home_failing_subtitle),
    connections = stringResource(R.string.home_connections),
    dnsQueries = stringResource(R.string.home_dns_queries),
    networkProtection = stringResource(R.string.home_network_protection),
    live = stringResource(R.string.home_live),
    offline = stringResource(R.string.home_offline),
    reconnecting = stringResource(R.string.home_reconnecting),
    activityTitle = stringResource(R.string.home_activity_title),
    activitySubtitle = stringResource(R.string.home_activity_subtitle),
    last24Hours = stringResource(R.string.home_last_24_hours),
    allowed = stringResource(R.string.lbl_allowed),
    blocked = stringResource(R.string.lbl_blocked),
    stoppedAtDns = stoppedAtDnsLabel(),
    stoppedMost = stringResource(R.string.home_stopped_most),
    nothingStopped = stringResource(R.string.home_nothing_stopped),
    seeStatistics = stringResource(R.string.home_see_statistics),
    apps = stringResource(R.string.lbl_apps),
    appsCaption = stringResource(R.string.home_apps_caption),
    logs = stringResource(R.string.lbl_logs),
    logsCaption = stringResource(R.string.home_logs_caption),
    dns = stringResource(R.string.lbl_dns),
    dnsCaption = stringResource(R.string.home_dns_caption),
    blockedCount = blockedCountLabel(),
)

@Composable
private fun stoppedAtDnsLabel(): (Long) -> String {
    val resources = LocalResources.current
    return { count -> resources.getQuantityString(R.plurals.home_stopped_at_dns_count, count.toInt(), count.toInt()) }
}

@Composable
private fun blockedCountLabel(): (Long) -> String {
    val resources = LocalResources.current
    return { count -> resources.getQuantityString(R.plurals.home_blocked_count, count.toInt(), count.toInt()) }
}
