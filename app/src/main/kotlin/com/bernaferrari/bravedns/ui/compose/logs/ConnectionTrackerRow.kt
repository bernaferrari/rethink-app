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

package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import android.content.Context
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.compose.theme.rememberReducedMotion
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogDetail
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogRow
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogRowModel
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogKind
import com.bernaferrari.bravedns.database.ConnectionTracker
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.service.FirewallRuleset
import com.bernaferrari.bravedns.service.ProxyManager
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.ui.compose.rememberDrawablePainter
import com.bernaferrari.bravedns.util.Constants.Companion.TIME_FORMAT_1
import com.bernaferrari.bravedns.util.KnownPorts
import com.bernaferrari.bravedns.util.Protocol
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.util.UIUtils.getDurationInHumanReadableFormat
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.Utilities.getIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_BYTES = 500000 // 500 KB
private const val MAX_TIME_TCP = 135 // seconds
private const val MAX_TIME_UDP = 135 // seconds
private const val NO_USER_ID = 0

@Composable
fun ConnectionRow(
    ct: ConnectionTracker,
    index: Int = 0,
    itemCount: Int = 1,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
) {
    RethinkLogRow(
        model = connectionLogModel(ct),
        index = index,
        itemCount = itemCount,
        selected = selected,
        onSelect = onSelect,
    )
}

/** A connection as the shared log model: the app leads, then where it went, how, and the verdict. */
@Composable
fun connectionLogModel(ct: ConnectionTracker): RethinkLogRowModel {
    val context = LocalContext.current
    val unknownApp = stringResource(R.string.network_log_app_name_unknown)
    val http3 = stringResource(R.string.connection_http3)
    val summary = summaryInfo(context, ct)
    val protocol = protocolLabel(http3, ct.port, ct.protocol)
    val time = Utilities.convertLongToTime(ct.timeStamp, TIME_FORMAT_1)
    val destination = ct.dnsQuery?.takeIf { it.isNotBlank() } ?: ct.ipAddress
    val appDisplay = if (ct.appName.isBlank()) unknownApp else ct.appName

    var appIcon by remember(ct.uid) { mutableStateOf<Drawable?>(null) }
    var appCount by remember(ct.uid) { mutableIntStateOf(1) }

    LaunchedEffect(ct.uid, ct.appName, ct.usrId) {
        val apps = withContext(Dispatchers.IO) { FirewallManager.getPackageNamesByUid(ct.uid) }
        appCount = apps.size
        appIcon = if (apps.isEmpty()) null else withContext(Dispatchers.IO) { getIcon(context, apps[0]) }
    }

    val appName =
        when {
            ct.usrId != NO_USER_ID ->
                stringResource(R.string.about_version_install_source, appDisplay, ct.usrId.toString())
            appCount > 1 ->
                stringResource(R.string.ctbs_app_other_apps, appDisplay, "${appCount - 1}")
            else -> appDisplay
        }

    val rule = FirewallRuleset.getFirewallRule(ct.blockedByRule)
    val ruleTitle = rule?.let { stringResource(it.title) }
    val details = connectionDetails(ct, protocol, ruleTitle, summary, unknownApp)
    val facts = listOfNotNull(dataMoved(ct))
    return RethinkLogRowModel(
        id = ct.id.toString(),
        destination = destination,
        appLabel = appName,
        typeLabel = protocol,
        timeLabel = time,
        isBlocked = ct.isBlocked,
        allowedLabel = stringResource(R.string.lbl_allowed),
        blockedLabel = stringResource(R.string.lbl_blocked),
        details = details,
        icon = { statusContainer -> AppIconSlot(appIcon, statusContainer) },
        kind = RethinkLogKind.Network,
        reason = ruleTitle.takeIf { ct.isBlocked },
        endpoint = endpointOf(ct),
        facts = facts,
    )
}

private fun endpointOf(ct: ConnectionTracker): String = buildString {
    append(ct.ipAddress)
    if (ct.port > 0) append(":${ct.port}")
}

@Composable
private fun connectionDetails(
    ct: ConnectionTracker,
    protocol: String,
    ruleTitle: String?,
    summary: Summary,
    unknown: String,
): List<RethinkLogDetail> {
    val endpoint = endpointOf(ct)
    val latency = stringResource(R.string.log_latency_ms, ct.synack.toInt())
    return listOfNotNull(
        RethinkLogDetail(stringResource(R.string.log_detail_protocol), protocol),
        RethinkLogDetail(stringResource(R.string.log_detail_country), countryDisplay(unknown, ct.flag)),
        endpoint.takeIf { it.isNotBlank() }?.let { RethinkLogDetail(stringResource(R.string.log_detail_address), it, monospace = true) },
        ct.dnsQuery?.takeIf { it.isNotBlank() }?.let { RethinkLogDetail(stringResource(R.string.log_detail_domain), it, monospace = true) },
        ct.blockedByRule.takeIf { it.isNotBlank() }?.let { RethinkLogDetail(stringResource(R.string.log_detail_rule), ruleTitle ?: it, isError = ct.isBlocked) },
        ct.proxyDetails.takeIf { it.isNotBlank() }?.let { RethinkLogDetail(stringResource(R.string.log_detail_proxy), it, monospace = true) },
        summary.duration.takeIf { it.isNotBlank() }?.let { RethinkLogDetail(stringResource(R.string.log_detail_duration), it) },
        summary.dataUsage.takeIf { it.isNotBlank() }?.let { RethinkLogDetail(stringResource(R.string.log_detail_data), it) },
        latency.takeIf { ct.synack > 0 }?.let { RethinkLogDetail(stringResource(R.string.dns_detail_latency), it) },
        summary.delay.takeIf { it.isNotBlank() }?.let { RethinkLogDetail(stringResource(R.string.log_detail_flags), it) },
        ct.message.takeIf { it.isNotBlank() }?.let { RethinkLogDetail(stringResource(R.string.log_detail_message), it, isError = ct.isBlocked) },
    )
}

/** "↓ 1.2 MB · ↑ 12 KB" for allowed connections that moved data. */
@Composable
private fun dataMoved(ct: ConnectionTracker): String? {
    if (ct.isBlocked || (ct.downloadBytes <= 0 && ct.uploadBytes <= 0)) return null
    return stringResource(
        R.string.log_data_down_up,
        Utilities.humanReadableByteCount(ct.downloadBytes, true),
        Utilities.humanReadableByteCount(ct.uploadBytes, true),
    )
}

@Composable
private fun AppIconSlot(
    appIcon: Drawable?,
    statusColor: Color,
) {
    val iconDrawable = appIcon

    Box(modifier = Modifier.size(SharedDimensions.iconContainerMd), contentAlignment = Alignment.Center) {
        if (iconDrawable != null) {
            Crossfade(targetState = iconDrawable, animationSpec = tween(durationMillis = 180), label = "connIcon") { drawable ->
                rememberDrawablePainter(drawable)?.let { painter ->
                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(SharedDimensions.iconContainerMd)
                                .clip(RoundedCornerShape(SharedDimensions.cornerRadiusMd)),
                    )
                }
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .size(SharedDimensions.iconContainerMd)
                        .clip(RoundedCornerShape(SharedDimensions.cornerRadiusMd))
                        .background(statusColor)
            )
        }
    }
}

private fun countryDisplay(unknown: String, flag: String): String {
    val countryName = UIUtils.getCountryNameFromFlag(flag).trim()
    val normalizedName = countryName.takeUnless { it.isBlank() || it == "--" }
    val normalizedFlag = flag.trim().takeUnless { it.isBlank() || it == "--" }

    return when {
        normalizedName != null && normalizedFlag != null -> "$normalizedName $normalizedFlag"
        normalizedName != null -> normalizedName
        normalizedFlag != null -> normalizedFlag
        else -> unknown
    }
}

private fun protocolLabel(http3: String, port: Int, proto: Int): String {
    if (Protocol.UDP.protocolType != proto && Protocol.TCP.protocolType != proto) {
        return Protocol.getProtocolName(proto).name
    }

    val resolvedPort = KnownPorts.resolvePort(port)
    return if (port == KnownPorts.HTTPS_PORT && proto == Protocol.UDP.protocolType) {
        http3
    } else if (resolvedPort != KnownPorts.PORT_VAL_UNKNOWN) {
        resolvedPort.uppercase(Locale.ROOT)
    } else {
        Protocol.getProtocolName(proto).name
    }
}

private data class Summary(
    val dataUsage: String,
    val duration: String,
    val delay: String,
    val showSummary: Boolean
)

@Composable
private fun summaryInfo(context: Context, ct: ConnectionTracker): Summary {
    val connType = ConnectionTracker.ConnType.get(ct.connType)
    var hasCid by remember(ct.connId, ct.uid) { mutableStateOf(false) }
    LaunchedEffect(ct.connId, ct.uid) {
        hasCid = VpnController.hasCid(ct.connId, ct.uid)
    }
    val activeLabel = stringResource(R.string.lbl_active)
    val greenCircle = stringResource(R.string.symbol_green_circle)
    val currency = stringResource(R.string.symbol_currency)
    val sparkle = stringResource(R.string.symbol_sparkle)
    val key = stringResource(R.string.symbol_key)
    val heavy = stringResource(R.string.symbol_heavy)
    val turtle = stringResource(R.string.symbol_turtle)
    val bunny = stringResource(R.string.symbol_bunny)
    val rocket = stringResource(R.string.symbol_rocket)
    val ciDesc = stringResource(R.string.ci_desc)
    val durationText = stringResource(
        R.string.single_argument,
        getDurationInHumanReadableFormat(context, ct.duration)
    )
    val download = stringResource(
        R.string.symbol_download,
        Utilities.humanReadableByteCount(ct.downloadBytes, true)
    )
    val upload = stringResource(
        R.string.symbol_upload,
        Utilities.humanReadableByteCount(ct.uploadBytes, true)
    )
    val usageText = stringResource(R.string.two_argument, upload, download)
    var dataUsage = ""
    var delay = ""
    var duration = ""

    if (ct.duration == 0 && ct.downloadBytes == 0L && ct.uploadBytes == 0L && ct.message.isEmpty()) {
        var hasMinSummary = false
        if (hasCid) {
            dataUsage = activeLabel
            duration = greenCircle
            hasMinSummary = true
        }

        if (connType.isMetered()) {
            delay = currency
            hasMinSummary = true
        }

        if (isRpnProxy(ct.rpid)) {
            delay = formatTemplate(ciDesc, delay, sparkle)
        } else if (isConnectionProxied(ct.blockedByRule, ct.proxyDetails)) {
            delay = formatTemplate(ciDesc, delay, key)
            hasMinSummary = true
        }

        return Summary(dataUsage, duration, delay, hasMinSummary)
    }

    duration = durationText
    dataUsage = usageText

    if (connType.isMetered()) {
        delay = formatTemplate(ciDesc, delay, currency)
    }
    if (isConnectionHeavier(ct)) {
        delay = formatTemplate(ciDesc, delay, heavy)
    }
    if (isConnectionSlower(ct)) {
        delay = formatTemplate(ciDesc, delay, turtle)
    }
    if (isRpnProxy(ct.rpid)) {
        delay = formatTemplate(ciDesc, delay, sparkle)
    } else if (containsRelayProxy(ct.rpid)) {
        delay = formatTemplate(ciDesc, delay, bunny)
    } else if (isConnectionProxied(ct.blockedByRule, ct.proxyDetails)) {
        delay = formatTemplate(ciDesc, delay, key)
    }
    if (isRoundTripShorter(ct.synack, ct.isBlocked)) {
        delay = formatTemplate(ciDesc, delay, rocket)
    }

    val showSummary = delay.isNotEmpty() || dataUsage.isNotEmpty()
    return Summary(dataUsage, duration, delay, showSummary)
}

private fun formatTemplate(template: String, vararg args: Any): String =
    String.format(Locale.getDefault(), template, *args)

private fun isRoundTripShorter(rtt: Long, blocked: Boolean): Boolean {
    return rtt in 1..20 && !blocked
}

private fun containsRelayProxy(rpid: String): Boolean {
    return rpid.isNotEmpty()
}

private fun isConnectionProxied(ruleName: String?, proxyDetails: String): Boolean {
    if (ruleName == null) return false
    val rule = FirewallRuleset.getFirewallRule(ruleName) ?: return false
    val proxy = ProxyManager.isNotLocalAndRpnProxy(proxyDetails)
    val isProxyError = FirewallRuleset.isProxyError(ruleName)
    return (FirewallRuleset.isProxied(rule) && proxyDetails.isNotEmpty() && proxy) || isProxyError
}

private fun isRpnProxy(pid: String): Boolean {
    return pid.isNotEmpty() && ProxyManager.isRpnProxy(pid)
}

private fun isConnectionHeavier(ct: ConnectionTracker): Boolean {
    return ct.downloadBytes + ct.uploadBytes > MAX_BYTES
}

private fun isConnectionSlower(ct: ConnectionTracker): Boolean {
    return (ct.protocol == Protocol.UDP.protocolType && ct.duration > MAX_TIME_UDP) ||
        (ct.protocol == Protocol.TCP.protocolType && ct.duration > MAX_TIME_TCP)
}
