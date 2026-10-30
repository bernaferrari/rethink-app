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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.request.CachePolicy
import coil3.toBitmap
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogDetail
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogLatencyStrings
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogRow
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogRowModel
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogKind
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.fillMaxSize
import com.bernaferrari.bravedns.database.DnsLog
import com.bernaferrari.bravedns.image.FavIconDownloader
import com.bernaferrari.bravedns.image.FavIconImageLoader
import com.bernaferrari.bravedns.net.doh.Transaction
import com.bernaferrari.bravedns.service.ProxyManager
import com.bernaferrari.bravedns.ui.compose.rememberDrawablePainter
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.ui.compose.common.AppIcon
import com.celzero.firestack.backend.Backend

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
    val appLabel = log.appName.ifEmpty {
        stringResource(R.string.network_log_app_name_unknown)
    }

    var favIcon by remember(log.queryStr) { mutableStateOf<Drawable?>(null) }
    var showFav by remember(log.queryStr, loadFavIcon) { mutableStateOf(false) }
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
    val details = dnsLogDetails(log, appLabel, dnsType, blocklists)
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
        appIcon = log.packageName
            .takeUnless { it.isEmpty() || it == Constants.EMPTY_PACKAGE_NAME }
            ?.let { pkg -> { AppIcon(pkg, size = Dp.Unspecified) } },
    )
}

@Composable
private fun dnsLogDetails(log: DnsLog, appLabel: String, dnsType: String, blocklists: List<String>): List<RethinkLogDetail> {
    val unknown = stringResource(R.string.network_log_app_name_unknown)
    val country = UIUtils.countryLabel(log.flag, unknown)
    val dnssec = when {
        !log.dnssecOk && !log.dnssecValid -> null
        log.dnssecOk && log.dnssecValid -> stringResource(R.string.log_detail_dnssec_valid)
        else -> stringResource(R.string.log_detail_dnssec_unverified)
    }
    return listOfNotNull(
        // The row shows only the app's icon; its name is here.
        RethinkLogDetail(stringResource(R.string.log_detail_app_name), appLabel),
        RethinkLogDetail(stringResource(R.string.log_detail_protocol), dnsType),
        RethinkLogDetail(stringResource(R.string.log_detail_country), country),
        log.responseIps.takeIf { it.isNotBlank() }?.let { RethinkLogDetail(stringResource(R.string.log_detail_response), it, monospace = true) },
        log.serverIP.takeIf { it.isNotBlank() }?.let { RethinkLogDetail(stringResource(R.string.log_detail_resolver), it, monospace = true) },
        // Blocklists: the "N blocklists matched" action under the details names and opens them.
        // Relay or proxy in plain text, where the old emoji flags (🐇 🗝️) only hinted at it.
        (log.relayIP.takeIf(::containsRelayProxy) ?: log.proxyId.takeIf(::isConnectionProxied))
            ?.let { RethinkLogDetail(stringResource(R.string.log_detail_proxy), it, monospace = true) },
        dnssec?.let { RethinkLogDetail(stringResource(R.string.log_detail_dnssec), it) },
    )
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

private fun containsRelayProxy(rpid: String) = rpid.isNotEmpty()

private fun isConnectionProxied(proxy: String?): Boolean {
    if (proxy.isNullOrEmpty()) return false
    return ProxyManager.isNotLocalAndRpnProxy(proxy)
}

private fun isRethinkUsed(log: DnsLog, isRethinkDns: Boolean): Boolean {
    if (log.status != Transaction.Status.COMPLETE.name) return false
    return isRethinkDns &&
        (log.resolverId.contains(Backend.Preferred) || log.resolverId.contains(Backend.BlockFree))
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
