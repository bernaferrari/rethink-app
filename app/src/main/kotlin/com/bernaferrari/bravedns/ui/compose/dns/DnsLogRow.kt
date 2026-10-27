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

package com.bernaferrari.bravedns.ui.compose.dns

import Logger
import Logger.LOG_TAG_UI

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable
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
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.compose.theme.rememberReducedMotion
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogDetail
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogLatencyStrings
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogRow
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogRowModel
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogKind
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.bernaferrari.bravedns.database.DnsLog
import com.bernaferrari.bravedns.image.FavIconDownloader
import com.bernaferrari.bravedns.image.FavIconImageLoader
import com.bernaferrari.bravedns.net.doh.Transaction
import com.bernaferrari.bravedns.service.ProxyManager
import com.bernaferrari.bravedns.ui.compose.rememberDrawablePainter
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Constants.Companion.MAX_ENDPOINT
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.util.Utilities.getIcon
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
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
) {
    RethinkLogRow(
        model = dnsLogModel(log, loadFavIcon, isRethinkDns, onShowBlocklist),
        index = index,
        itemCount = itemCount,
        selected = selected,
        onSelect = onSelect,
    )
}

/** A query as the shared log model: the name leads, then the answer or blocklist, then who asked. */
@Composable
fun dnsLogModel(
    log: DnsLog,
    loadFavIcon: Boolean,
    isRethinkDns: Boolean,
    onShowBlocklist: (DnsLog) -> Unit,
): RethinkLogRowModel {
    val context = LocalContext.current
    val dnsType = dnsTypeName(log, isRethinkDns)
    val hint = unicodeHint(log, isRethinkDns)
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
                withContext(Dispatchers.IO) { getIcon(context, log.packageName) }
            }
    }

    LaunchedEffect(log.queryStr, loadFavIcon, log.groundedQuery()) {
        showFav = false
        favIcon = null
        if (!loadFavIcon || log.groundedQuery()) return@LaunchedEffect
        displayFavIcon(
            context = context,
            log = log,
            loadFavIcon = true,
            onShowFlag = { showFav = false; favIcon = null },
            onShowFav = { d -> showFav = true; favIcon = d },
        )
    }

    val blocklists = log.blockLists.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val details = dnsLogDetails(log, dnsType, hint, blocklists)
    val favicon = favIcon.takeIf { showFav }
    return RethinkLogRowModel(
        id = log.id.toString(),
        destination = log.queryStr,
        appLabel = appLabel,
        typeLabel = dnsType,
        timeLabel = log.wallTime(),
        isBlocked = log.isBlocked,
        allowedLabel = stringResource(R.string.lbl_allowed),
        blockedLabel = stringResource(R.string.lbl_blocked),
        details = details,
        icon = favicon?.let { drawable -> { _ -> DrawableIcon(drawable) } },
        latencyMs = log.latency,
        latencyStrings = RethinkLogLatencyStrings(
            title = stringResource(R.string.dns_detail_latency),
            fast = stringResource(R.string.lbl_fast),
            ok = stringResource(R.string.log_latency_ok),
            slow = stringResource(R.string.lbl_slow),
        ),
        blocklistsLabel = if (blocklists.isNotEmpty()) {
            pluralStringResource(R.plurals.log_blocklists_matched, blocklists.size, blocklists.size)
        } else {
            null
        },
        onBlocklistsClick = if (blocklists.isNotEmpty()) ({ onShowBlocklist(log) }) else null,
        kind = RethinkLogKind.Dns,
        reason = blocklistSummary(blocklists),
        endpoint = firstAnswer(log.responseIps),
        // Blocked rows name their blocklists instead; the latency of a refusal says little.
        facts = listOfNotNull(
            log.latency.takeIf { it > 0 && !log.isBlocked }?.let { stringResource(R.string.log_latency_ms, it.toInt()) },
        ),
        appIcon = appIcon?.let { drawable -> { DrawableIcon(drawable) } },
    )
}

@Composable
private fun dnsLogDetails(log: DnsLog, dnsType: String, hint: String, blocklists: List<String>): List<RethinkLogDetail> {
    val unknown = stringResource(R.string.network_log_app_name_unknown)
    val country = countryLabel(log.flag, unknown)
    val dnssec = when {
        !log.dnssecOk && !log.dnssecValid -> null
        log.dnssecOk && log.dnssecValid -> stringResource(R.string.log_detail_dnssec_valid)
        else -> stringResource(R.string.log_detail_dnssec_unverified)
    }
    return listOfNotNull(
        RethinkLogDetail(stringResource(R.string.log_detail_protocol), dnsType),
        RethinkLogDetail(stringResource(R.string.log_detail_country), country),
        log.responseIps.takeIf { it.isNotBlank() }?.let { RethinkLogDetail(stringResource(R.string.log_detail_response), it, monospace = true) },
        log.serverIP.takeIf { it.isNotBlank() }?.let { RethinkLogDetail(stringResource(R.string.log_detail_resolver), it, monospace = true) },
        blocklists.takeIf { it.isNotEmpty() }?.let { RethinkLogDetail(stringResource(R.string.log_detail_blocklists), it.joinToString(", "), isError = true) },
        dnssec?.let { RethinkLogDetail(stringResource(R.string.log_detail_dnssec), it) },
        hint.takeIf { it.isNotBlank() }?.let { RethinkLogDetail(stringResource(R.string.log_detail_flags), it) },
    )
}

private fun countryLabel(flag: String, unknown: String): String {
    val countryName = UIUtils.getCountryNameFromFlag(flag).trim()
    val hasName = countryName.isNotBlank() && countryName != "--"
    return when {
        hasName && flag.isNotBlank() -> "$countryName $flag"
        hasName -> countryName
        flag.isNotBlank() && flag != "--" -> flag
        else -> unknown
    }
}

/** The first two blocklists, then a count of the rest. */
private fun blocklistSummary(blocklists: List<String>): String? = when {
    blocklists.size > 2 -> "${blocklists.take(2).joinToString(", ")} +${blocklists.size - 2}"
    blocklists.isNotEmpty() -> blocklists.joinToString(", ")
    else -> null
}

/** The first address of an answer, without the "CC (address)" country wrapper. */
private fun firstAnswer(responseIps: String): String? =
    responseIps.split(",").firstOrNull()?.trim()
        ?.let { it.substringAfter('(', it).substringBefore(')') }
        ?.takeIf { it.isNotBlank() && it != "--" }

@Composable
private fun DrawableIcon(drawable: Drawable) {
    rememberDrawablePainter(drawable)?.let { painter ->
        androidx.compose.foundation.Image(painter = painter, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun unicodeHint(log: DnsLog, isRethinkDns: Boolean): String {
    val rocket = stringResource(R.string.symbol_rocket)
    val bunny = stringResource(R.string.symbol_bunny)
    val key = stringResource(R.string.symbol_key)
    val duck = stringResource(R.string.symbol_duck)
    val diamond = stringResource(R.string.symbol_diamond)
    val heavy = stringResource(R.string.symbol_heavy)
    val lock = stringResource(R.string.symbol_lock)
    val unlock = stringResource(R.string.symbol_unlock)
    val rethink = getRethinkUnicode(log)
    val ciDesc = stringResource(R.string.ci_desc)
    var hint = ""
    if (isRoundTripShorter(log.latency, log.isBlocked)) {
        hint = formatTemplate(ciDesc, hint, rocket)
    }
    if (containsRelayProxy(log.relayIP)) {
        hint = formatTemplate(ciDesc, hint, bunny)
    } else if (isConnectionProxied(log.proxyId)) {
        hint = formatTemplate(ciDesc, hint, key)
    }
    if (isRethinkUsed(log, isRethinkDns)) {
        hint = formatTemplate(ciDesc, hint, rethink)
    } else if (isGoosOrSystemUsed(log)) {
        hint = formatTemplate(ciDesc, hint, duck)
    } else if (isDefaultResolverUsed(log)) {
        hint = formatTemplate(ciDesc, hint, diamond)
    } else if (containsMultipleIPs(log)) {
        hint = formatTemplate(ciDesc, hint, heavy)
    }
    if (dnssecIndicatorRequired(log)) {
        hint = if (dnssecOk(log)) {
            formatTemplate(ciDesc, hint, lock)
        } else {
            formatTemplate(ciDesc, hint, unlock)
        }
    }
    return hint
}

@Composable
private fun dnsTypeName(log: DnsLog, isRethinkDns: Boolean): String {
    val rdns = stringResource(R.string.lbl_rdns)
    val doh = stringResource(R.string.other_dns_list_tab1)
    val dcAbbr = stringResource(R.string.lbl_dc_abbr)
    val dp = stringResource(R.string.lbl_dp)
    val dot = stringResource(R.string.lbl_dot)
    val odoh = stringResource(R.string.lbl_odoh)
    return when (Transaction.TransportType.fromOrdinal(log.dnsType)) {
        Transaction.TransportType.DOH ->
            if (isRethinkDns && isRethinkUsed(log, isRethinkDns)) rdns else doh
        Transaction.TransportType.DNS_CRYPT -> dcAbbr
        Transaction.TransportType.DNS_PROXY -> dp
        Transaction.TransportType.DOT -> dot
        Transaction.TransportType.ODOH -> odoh
    }
}

private fun formatTemplate(template: String, vararg args: Any): String =
    String.format(java.util.Locale.getDefault(), template, *args)

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

@Composable
private fun getRethinkUnicode(log: DnsLog): String {
    val sparkle = stringResource(R.string.symbol_sparkle)
    val max = stringResource(R.string.symbol_max)
    val sky = stringResource(R.string.symbol_sky)
    if (log.relayIP.endsWith(Backend.RPN) || log.relayIP == Backend.Auto) {
        return sparkle
    }
    return if (log.serverIP.contains(MAX_ENDPOINT)) max else sky
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
    return (result as? SuccessResult)?.image?.toBitmap()?.let { it.toDrawable(context.resources) }
}
