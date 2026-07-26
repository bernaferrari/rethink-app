/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.bubble

import Logger
import Logger.LOG_TAG_UI
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AllowedAppInfo
import com.bernaferrari.bravedns.data.BlockedAppInfo
import com.bernaferrari.bravedns.ui.compose.rememberDrawablePainter
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Android paging, app icon and time-format bridge for the portable bubble surface. */
@Composable
fun BubbleScreen(
    vpnOn: Boolean,
    allowedItems: LazyPagingItems<AllowedAppInfo>,
    blockedItems: LazyPagingItems<BlockedAppInfo>,
    onAllowApp: (BlockedAppInfo, () -> Unit) -> Unit,
    onRemoveAllowed: (AllowedAppInfo, () -> Unit) -> Unit,
) {
    val allowed = List(allowedItems.itemCount) { index -> allowedItems[index] }.filterNotNull()
    val blocked = List(blockedItems.itemCount) { index -> blockedItems[index] }.filterNotNull()
    val allowedById = allowed.associateBy { it.id() }
    val blockedById = blocked.associateBy { it.id() }
    RethinkBubbleScreen(
        vpnOn = vpnOn,
        allowedItems = allowed.map { RethinkBubbleAllowedItem(it.id(), it.packageName, it.appName, allowedTimeRemaining(it)) },
        blockedItems = blocked.map { item ->
            RethinkBubbleBlockedItem(item.id(), item.packageName, item.appName, stringResource(R.string.bubble_blocked_count, item.count), timeAgo(item.lastBlocked))
        },
        blockedLoading = blockedItems.loadState.refresh is LoadState.Loading,
        blockedError = blockedItems.loadState.refresh is LoadState.Error,
        strings = RethinkBubbleStrings(
            title = stringResource(R.string.firewall_bubble_title), subtitle = stringResource(R.string.firewall_bubble_subtitle),
            allowedTitle = stringResource(R.string.bubble_allowed_title), activityTitle = stringResource(R.string.bubble_activity_title), loading = stringResource(R.string.bubble_loading),
            emptyTitle = stringResource(R.string.bubble_empty_state_title), emptyDescription = stringResource(R.string.bubble_empty_state_desc), remove = stringResource(R.string.lbl_remove), allow = stringResource(R.string.bubble_allow_btn),
        ),
        appIcon = { packageName -> AndroidAppIcon(packageName) },
        onAllow = { id -> blockedById[id]?.let { app -> onAllowApp(app) { blockedItems.refresh(); allowedItems.refresh() } } },
        onRemove = { id -> allowedById[id]?.let { app -> onRemoveAllowed(app) { allowedItems.refresh(); blockedItems.refresh() } } },
    )
}

@Composable
private fun AndroidAppIcon(packageName: String) {
    val context = LocalContext.current
    val icon = remember(packageName) { loadAppIcon(context, packageName) }
    Box(
        Modifier.size(46.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(SharedDimensions.cornerRadiusMdLg)),
        contentAlignment = Alignment.Center,
    ) { rememberDrawablePainter(icon)?.let { Image(it, null, Modifier.size(26.dp)) } }
}

private fun AllowedAppInfo.id() = "$uid:$packageName"
private fun BlockedAppInfo.id() = "$uid:$packageName"

private fun loadAppIcon(context: android.content.Context, packageName: String): Drawable = try {
    if (packageName != "Unknown") context.packageManager.getApplicationIcon(packageName) else ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!
} catch (_: Exception) {
    Logger.e(LOG_TAG_UI, "App icon not found for $packageName")
    ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!
}

private fun allowedTimeRemaining(app: AllowedAppInfo): String {
    val remaining = (app.allowedAt + 15 * 60 * 1000 - System.currentTimeMillis()) / 1000 / 60
    return if (remaining > 0) "$remaining min${if (remaining != 1L) "s" else ""} remaining" else "Expired"
}

@Composable
private fun timeAgo(timestamp: Long): String {
    val justNow = stringResource(R.string.bubble_time_just_now)
    val minutesTemplate = stringResource(R.string.bubble_time_minutes_ago)
    val hoursTemplate = stringResource(R.string.bubble_time_hours_ago)
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> justNow
        diff < TimeUnit.HOURS.toMillis(1) -> String.format(Locale.getDefault(), minutesTemplate, TimeUnit.MILLISECONDS.toMinutes(diff))
        diff < TimeUnit.DAYS.toMillis(1) -> String.format(Locale.getDefault(), hoursTemplate, TimeUnit.MILLISECONDS.toHours(diff))
        else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
