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

package com.celzero.bravedns.adapter

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.bumptech.glide.request.transition.Transition
import com.celzero.bravedns.R
import com.celzero.bravedns.database.DnsLog
import com.celzero.bravedns.glide.FavIconDownloader
import com.celzero.bravedns.net.doh.Transaction
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.MAX_ENDPOINT
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.Utilities.getDefaultIcon
import com.celzero.bravedns.util.Utilities.getIcon
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import com.celzero.firestack.backend.Backend
import io.github.aakira.napier.Napier


@Composable
fun DnsLogRow(
    log: DnsLog,
    loadFavIcon: Boolean,
    isRethinkDns: Boolean,
    onShowBlocklist: (DnsLog) -> Unit
) {
    val context = LocalContext.current
    val indicatorColor = statusIndicatorColor(context, log)
    val dnsTypeName = dnsTypeName(context, log, isRethinkDns)
    val unicodeHint = unicodeHint(context, log, isRethinkDns)
    val showSummary = unicodeHint.isNotEmpty() || log.typeName.isNotEmpty()
    val responseIp = log.responseIps.split(",").firstOrNull().orEmpty()
    val latencyText = context.getString(R.string.dns_query_latency, log.latency.toString())

    var appIcon by remember(log.packageName, log.appName) { mutableStateOf<Drawable?>(null) }
    var showFavIcon by remember(log.queryStr, loadFavIcon) { mutableStateOf(false) }
    var favIconDrawable by remember(log.queryStr) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(log.packageName, log.appName) {
        appIcon =
            if (log.packageName.isEmpty() || log.packageName == Constants.EMPTY_PACKAGE_NAME) {
                getDefaultIcon(context)
            } else {
                getIcon(context, log.packageName)
            }
    }

    LaunchedEffect(log.queryStr, loadFavIcon, log.groundedQuery()) {
        showFavIcon = false
        favIconDrawable = null
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onShowBlocklist(log) }
                .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier =
                    Modifier
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(indicatorColor ?: Color.Transparent)
            )
            if (showFavIcon && favIconDrawable != null) {
                val favPainter = rememberDrawablePainter(favIconDrawable)
                favPainter?.let { painter ->
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                Text(text = log.flag, style = MaterialTheme.typography.titleMedium)
            }
            val appPainter =
                rememberDrawablePainter(appIcon)
                    ?: rememberDrawablePainter(getDefaultIcon(context))
            appPainter?.let { painter ->
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = log.typeName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = dnsTypeName,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(text = log.queryStr, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text =
                        if (log.appName.isEmpty()) {
                            context.getString(R.string.network_log_app_name_unknown)
                                .uppercase()
                        } else {
                            log.appName
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Text(text = log.wallTime(), style = MaterialTheme.typography.bodySmall)
            Icon(
                painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                contentDescription = null
            )
        }

        if (showSummary) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = responseIp, style = MaterialTheme.typography.bodySmall)
                Text(text = latencyText, style = MaterialTheme.typography.bodySmall)
                Text(text = unicodeHint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    LaunchedEffect(log.queryStr, loadFavIcon, log.groundedQuery()) {
        if (!loadFavIcon || log.groundedQuery()) return@LaunchedEffect
        displayFavIcon(
            context,
            log,
            loadFavIcon,
            onShowFlag = {
                showFavIcon = false
                favIconDrawable = null
            },
            onShowFav = { drawable ->
                showFavIcon = true
                favIconDrawable = drawable
            }
        )
    }
}

private fun statusIndicatorColor(context: Context, log: DnsLog): Color? {
    return when {
        log.isBlocked -> Color(ContextCompat.getColor(context, R.color.colorRed_A400))
        determineMaybeBlocked(log) ->
            Color(fetchColor(context, R.attr.chipTextNeutral))
        else -> null
    }
}

private fun determineMaybeBlocked(log: DnsLog): Boolean {
    return log.upstreamBlock || log.blockLists.isNotEmpty()
}

private fun unicodeHint(context: Context, log: DnsLog, isRethinkDns: Boolean): String {
    var hint = ""

    if (isRoundTripShorter(log.latency, log.isBlocked)) {
        hint =
            context.getString(
                R.string.ci_desc,
                hint,
                context.getString(R.string.symbol_rocket)
            )
    }
    if (containsRelayProxy(log.relayIP)) {
        hint =
            context.getString(
                R.string.ci_desc,
                hint,
                context.getString(R.string.symbol_bunny)
            )
    } else if (isConnectionProxied(log.proxyId)) {
        hint =
            context.getString(
                R.string.ci_desc,
                hint,
                context.getString(R.string.symbol_key)
            )
    }
    if (isRethinkUsed(log, isRethinkDns)) {
        hint =
            context.getString(
                R.string.ci_desc,
                hint,
                getRethinkUnicode(context, log)
            )
    } else if (isGoosOrSystemUsed(log)) {
        hint =
            context.getString(
                R.string.ci_desc,
                hint,
                context.getString(R.string.symbol_duck)
            )
    } else if (isDefaultResolverUsed(log)) {
        hint =
            context.getString(
                R.string.ci_desc,
                hint,
                context.getString(R.string.symbol_diamond)
            )
    } else if (containsMultipleIPs(log)) {
        hint =
            context.getString(
                R.string.ci_desc,
                hint,
                context.getString(R.string.symbol_heavy)
            )
    }
    if (dnssecIndicatorRequired(log)) {
        hint =
            if (dnssecOk(log)) {
                context.getString(
                    R.string.ci_desc,
                    hint,
                    context.getString(R.string.symbol_lock)
                )
            } else {
                context.getString(
                    R.string.ci_desc,
                    hint,
                    context.getString(R.string.symbol_unlock)
                )
            }
    }
    return hint
}

private fun dnsTypeName(context: Context, log: DnsLog, isRethinkDns: Boolean): String {
    return when (Transaction.TransportType.fromOrdinal(log.dnsType)) {
        Transaction.TransportType.DOH -> {
            if (isRethinkDns && isRethinkUsed(log, isRethinkDns)) {
                context.getString(R.string.lbl_rdns)
            } else {
                context.getString(R.string.other_dns_list_tab1)
            }
        }
        Transaction.TransportType.DNS_CRYPT -> context.getString(R.string.lbl_dc_abbr)
        Transaction.TransportType.DNS_PROXY -> context.getString(R.string.lbl_dp)
        Transaction.TransportType.DOT -> context.getString(R.string.lbl_dot)
        Transaction.TransportType.ODOH -> context.getString(R.string.lbl_odoh)
    }
}

private fun dnssecIndicatorRequired(log: DnsLog): Boolean {
    if (log.status != Transaction.Status.COMPLETE.name) return false
    return log.dnssecOk || log.dnssecValid
}

private fun dnssecOk(log: DnsLog): Boolean {
    return log.dnssecOk && log.dnssecValid
}

private fun isRoundTripShorter(rtt: Long, blocked: Boolean): Boolean {
    return rtt in 1..10 && !blocked
}

private fun containsRelayProxy(rpid: String): Boolean {
    return rpid.isNotEmpty()
}

private fun isConnectionProxied(proxy: String?): Boolean {
    if (proxy.isNullOrEmpty()) return false
    return ProxyManager.isNotLocalAndRpnProxy(proxy)
}

private fun containsMultipleIPs(log: DnsLog): Boolean {
    return log.responseIps.split(",").size > 1
}

private fun isRethinkUsed(log: DnsLog, isRethinkDns: Boolean): Boolean {
    if (log.status != Transaction.Status.COMPLETE.name) return false
    return if (isRethinkDns) {
        log.resolverId.contains(Backend.Preferred) || log.resolverId.contains(Backend.BlockFree)
    } else {
        false
    }
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

private fun displayFavIcon(
    context: Context,
    log: DnsLog,
    loadFavIcon: Boolean,
    onShowFlag: () -> Unit,
    onShowFav: (Drawable) -> Unit
) {
    if (!loadFavIcon || log.groundedQuery()) {
        onShowFlag()
        return
    }

    if (FavIconDownloader.isUrlAvailableInFailedCache(log.queryStr.dropLast(1)) != null) {
        onShowFlag()
        return
    }

    displayNextDnsFavIcon(context, log, onShowFlag, onShowFav)
}

private fun displayNextDnsFavIcon(
    context: Context,
    log: DnsLog,
    onShowFlag: () -> Unit,
    onShowFav: (Drawable) -> Unit
) {
    val trim = log.queryStr.dropLastWhile { it == '.' }
    val nextDnsUrl = FavIconDownloader.constructFavIcoUrlNextDns(trim)
    val duckduckGoUrl = FavIconDownloader.constructFavUrlDuckDuckGo(trim)
    val duckduckgoDomainURL = FavIconDownloader.getDomainUrlFromFdqnDuckduckgo(trim)
    try {
        val factory = DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(true).build()
        Glide.with(context.applicationContext)
            .load(nextDnsUrl)
            .onlyRetrieveFromCache(true)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .transition(withCrossFade(factory))
            .into(
                object : CustomTarget<Drawable>() {
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        displayDuckduckgoFavIcon(
                            context,
                            duckduckGoUrl,
                            duckduckgoDomainURL,
                            onShowFlag,
                            onShowFav
                        )
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        onShowFav(resource)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        onShowFlag()
                    }
                }
            )
    } catch (_: Exception) {
        Napier.d("err loading icon, load flag instead")
        displayDuckduckgoFavIcon(
            context,
            duckduckGoUrl,
            duckduckgoDomainURL,
            onShowFlag,
            onShowFav
        )
    }
}

private fun displayDuckduckgoFavIcon(
    context: Context,
    url: String,
    subDomainURL: String,
    onShowFlag: () -> Unit,
    onShowFav: (Drawable) -> Unit
) {
    try {
        val factory = DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(true).build()
        Glide.with(context.applicationContext)
            .load(url)
            .onlyRetrieveFromCache(true)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .error(
                Glide.with(context.applicationContext)
                    .load(subDomainURL)
                    .onlyRetrieveFromCache(true)
            )
            .transition(withCrossFade(factory))
            .into(
                object : CustomTarget<Drawable>() {
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        onShowFlag()
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        onShowFav(resource)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        onShowFlag()
                    }
                }
            )
    } catch (_: Exception) {
        Napier.d("err loading icon, load flag instead")
        onShowFlag()
    }
}

