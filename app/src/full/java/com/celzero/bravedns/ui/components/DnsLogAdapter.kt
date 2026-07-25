/*
Copyright 2020 RethinkDNS and its authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.celzero.bravedns.ui.components

import Logger
import Logger.LOG_TAG_UI

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.request.CachePolicy
import coil3.toBitmap
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.rememberReducedMotion
import com.celzero.bravedns.ui.compose.logs.RethinkLogDetail
import com.celzero.bravedns.ui.compose.logs.RethinkLogRow
import com.celzero.bravedns.ui.compose.logs.RethinkLogRowModel
import com.celzero.bravedns.database.DnsLog
import com.celzero.bravedns.image.FavIconDownloader
import com.celzero.bravedns.image.FavIconImageLoader
import com.celzero.bravedns.net.doh.Transaction
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.MAX_ENDPOINT
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities.getIcon
import com.celzero.firestack.backend.Backend
import kotlin.math.roundToInt

@Composable
fun DnsLogRow(
    log: DnsLog,
    loadFavIcon: Boolean,
    isRethinkDns: Boolean,
    onShowBlocklist: (DnsLog) -> Unit,
    index: Int = 0,
    itemCount: Int = 1,
) {
    val context = LocalContext.current
    val dnsType = dnsTypeName(context, log, isRethinkDns)
    val hint = unicodeHint(context, log, isRethinkDns)
    val appLabel = log.appName.ifEmpty {
        stringResource(R.string.network_log_app_name_unknown)
    }

    var appIcon by remember(log.packageName) { mutableStateOf<Drawable?>(null) }
    var favIcon by remember(log.queryStr) { mutableStateOf<Drawable?>(null) }
    var showFav by remember(log.queryStr, loadFavIcon) { mutableStateOf(false) }
    LaunchedEffect(log.packageName) {
        appIcon =
            if (log.packageName.isEmpty() || log.packageName == Constants.EMPTY_PACKAGE_NAME) {
                null
            } else {
                getIcon(context, log.packageName)
            }
    }

    LaunchedEffect(log.queryStr, loadFavIcon, log.groundedQuery()) {
        showFav = false
        favIcon = null
    }

    LaunchedEffect(log.queryStr, loadFavIcon, log.groundedQuery()) {
        if (!loadFavIcon || log.groundedQuery()) return@LaunchedEffect
        displayFavIcon(
            context = context,
            log = log,
            loadFavIcon = true,
            onShowFlag = { showFav = false; favIcon = null },
            onShowFav = { d -> showFav = true; favIcon = d },
        )
    }

    val countryName = UIUtils.getCountryNameFromFlag(log.flag).trim()
    val country = when {
        countryName.isNotBlank() && countryName != "--" && log.flag.isNotBlank() -> "$countryName ${log.flag}"
        countryName.isNotBlank() && countryName != "--" -> countryName
        log.flag.isNotBlank() && log.flag != "--" -> log.flag
        else -> stringResource(R.string.network_log_app_name_unknown)
    }
    val details = buildList {
        add(RethinkLogDetail("Transport", dnsType))
        add(RethinkLogDetail("Country", country))
        if (log.responseIps.isNotBlank()) add(RethinkLogDetail("Response", log.responseIps, monospace = true))
        if (log.serverIP.isNotBlank()) add(RethinkLogDetail("Resolver", log.serverIP, monospace = true))
        if (log.dnssecOk || log.dnssecValid) {
            add(RethinkLogDetail("DNSSEC", if (log.dnssecOk && log.dnssecValid) "✓  Valid" else "⚠  Unverified"))
        }
        if (hint.isNotBlank()) add(RethinkLogDetail("Flags", hint))
    }
    val blocklistCount = log.blockLists.split(",").count { it.isNotBlank() }
    RethinkLogRow(
        model = RethinkLogRowModel(
            id = log.id.toString(),
            destination = log.queryStr,
            appLabel = appLabel,
            typeLabel = dnsType,
            timeLabel = log.wallTime(),
            isBlocked = log.isBlocked,
            allowedLabel = stringResource(R.string.lbl_allowed),
            blockedLabel = stringResource(R.string.lbl_blocked),
            details = details,
            icon = { statusContainer -> AppIconSlot(showFav, favIcon, appIcon, statusContainer) },
            latencyMs = log.latency,
            blocklistsLabel = if (blocklistCount > 0) "$blocklistCount blocklists matched" else null,
            onBlocklistsClick = if (blocklistCount > 0) ({ onShowBlocklist(log) }) else null,
        ),
        index = index,
        itemCount = itemCount,
    )
    return
}

@Composable
private fun AppIconSlot(
    showFav: Boolean,
    favIcon: Drawable?,
    appIcon: Drawable?,
    statusColor: Color,
) {
    val iconDrawable = if (showFav && favIcon != null) favIcon else appIcon

    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        if (iconDrawable != null) {
            Crossfade(targetState = iconDrawable, animationSpec = tween(durationMillis = 180), label = "dnsIcon") { drawable ->
                rememberDrawablePainter(drawable)?.let { painter ->
                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(7.dp)),
                    )
                }
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusColor.copy(alpha = 0.5f))
            )
        }
    }
}

private fun unicodeHint(context: Context, log: DnsLog, isRethinkDns: Boolean): String {
    var hint = ""
    if (isRoundTripShorter(log.latency, log.isBlocked)) {
        hint = context.getString(R.string.ci_desc, hint, context.getString(R.string.symbol_rocket))
    }
    if (containsRelayProxy(log.relayIP)) {
        hint = context.getString(R.string.ci_desc, hint, context.getString(R.string.symbol_bunny))
    } else if (isConnectionProxied(log.proxyId)) {
        hint = context.getString(R.string.ci_desc, hint, context.getString(R.string.symbol_key))
    }
    if (isRethinkUsed(log, isRethinkDns)) {
        hint = context.getString(R.string.ci_desc, hint, getRethinkUnicode(context, log))
    } else if (isGoosOrSystemUsed(log)) {
        hint = context.getString(R.string.ci_desc, hint, context.getString(R.string.symbol_duck))
    } else if (isDefaultResolverUsed(log)) {
        hint = context.getString(R.string.ci_desc, hint, context.getString(R.string.symbol_diamond))
    } else if (containsMultipleIPs(log)) {
        hint = context.getString(R.string.ci_desc, hint, context.getString(R.string.symbol_heavy))
    }
    if (dnssecIndicatorRequired(log)) {
        hint = if (dnssecOk(log)) {
            context.getString(R.string.ci_desc, hint, context.getString(R.string.symbol_lock))
        } else {
            context.getString(R.string.ci_desc, hint, context.getString(R.string.symbol_unlock))
        }
    }
    return hint
}

private fun dnsTypeName(context: Context, log: DnsLog, isRethinkDns: Boolean): String =
    when (Transaction.TransportType.fromOrdinal(log.dnsType)) {
        Transaction.TransportType.DOH ->
            if (isRethinkDns && isRethinkUsed(log, isRethinkDns)) {
                context.getString(R.string.lbl_rdns)
            } else {
                context.getString(R.string.other_dns_list_tab1)
            }
        Transaction.TransportType.DNS_CRYPT -> context.getString(R.string.lbl_dc_abbr)
        Transaction.TransportType.DNS_PROXY -> context.getString(R.string.lbl_dp)
        Transaction.TransportType.DOT -> context.getString(R.string.lbl_dot)
        Transaction.TransportType.ODOH -> context.getString(R.string.lbl_odoh)
    }

private fun dnssecIndicatorRequired(log: DnsLog) =
    log.status == Transaction.Status.COMPLETE.name && (log.dnssecOk || log.dnssecValid)

private fun dnssecOk(log: DnsLog) = log.dnssecOk && log.dnssecValid

private fun isRoundTripShorter(rtt: Long, blocked: Boolean) = rtt in 1..10 && !blocked

private fun containsRelayProxy(rpid: String) = rpid.isNotEmpty()

private fun isConnectionProxied(proxy: String?): Boolean {
    if (proxy.isNullOrEmpty()) return false
    return ProxyManager.isNotLocalAndRpnProxy(proxy)
}

private fun containsMultipleIPs(log: DnsLog) = log.responseIps.split(",").size > 1

private fun isRethinkUsed(log: DnsLog, isRethinkDns: Boolean): Boolean {
    if (log.status != Transaction.Status.COMPLETE.name) return false
    return isRethinkDns &&
        (log.resolverId.contains(Backend.Preferred) || log.resolverId.contains(Backend.BlockFree))
}

private fun isGoosOrSystemUsed(log: DnsLog): Boolean {
    if (log.status != Transaction.Status.COMPLETE.name) return false
    return log.resolverId.contains(Backend.Goos) || log.resolverId.contains(Backend.System)
}

private fun isDefaultResolverUsed(log: DnsLog): Boolean {
    if (log.status != Transaction.Status.COMPLETE.name) return false
    return log.resolverId.contains(Backend.Default) || log.resolverId.contains(Backend.Bootstrap)
}

private fun getRethinkUnicode(context: Context, log: DnsLog): String {
    if (log.relayIP.endsWith(Backend.RPN) || log.relayIP == Backend.Auto) {
        return context.getString(R.string.symbol_sparkle)
    }
    return if (log.serverIP.contains(MAX_ENDPOINT)) {
        context.getString(R.string.symbol_max)
    } else {
        context.getString(R.string.symbol_sky)
    }
}

private suspend fun displayFavIcon(
    context: Context,
    log: DnsLog,
    loadFavIcon: Boolean,
    onShowFlag: () -> Unit,
    onShowFav: (Drawable) -> Unit,
) {
    if (!loadFavIcon || log.groundedQuery()) {
        onShowFlag()
        return
    }
    if (FavIconDownloader.isUrlAvailableInFailedCache(log.queryStr.dropLast(1)) != null) {
        onShowFlag()
        return
    }

    val trim = log.queryStr.dropLastWhile { it == '.' }
    val urls =
        listOf(
            FavIconDownloader.constructFavIcoUrlNextDns(trim),
            FavIconDownloader.constructFavUrlDuckDuckGo(trim),
            FavIconDownloader.getDomainUrlFromFdqnDuckduckgo(trim),
        )

    try {
        val drawable = urls.firstNotNullOfOrNull { loadCachedFavIcon(context, it) }
        if (drawable != null) {
            onShowFav(drawable)
        } else {
            onShowFlag()
        }
    } catch (_: Exception) {
        Logger.d(LOG_TAG_UI, "err loading icon, load flag instead")
        onShowFlag()
    }
}

private suspend fun loadCachedFavIcon(context: Context, url: String): Drawable? {
    val result =
        FavIconImageLoader.get(context).execute(
            ImageRequest.Builder(context.applicationContext)
                .data(url)
                .memoryCachePolicy(CachePolicy.READ_ONLY)
                .diskCachePolicy(CachePolicy.READ_ONLY)
                .networkCachePolicy(CachePolicy.DISABLED)
                .build(),
        )
    return (result as? SuccessResult)?.image?.toBitmap()?.let { BitmapDrawable(context.resources, it) }
}
