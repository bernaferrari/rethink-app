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
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.rememberReducedMotion
import com.celzero.bravedns.ui.compose.logs.RethinkLogDetail
import com.celzero.bravedns.ui.compose.logs.RethinkLogRow
import com.celzero.bravedns.ui.compose.logs.RethinkLogRowModel
import com.celzero.bravedns.database.ConnectionTracker
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.FirewallRuleset
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import com.celzero.bravedns.util.Constants.Companion.TIME_FORMAT_1
import com.celzero.bravedns.util.KnownPorts
import com.celzero.bravedns.util.Protocol
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.getDurationInHumanReadableFormat
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getIcon
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
) {
    val context = LocalContext.current
    val summary = summaryInfo(context, ct)
    val protocol = protocolLabel(context, ct.port, ct.protocol)
    val time = Utilities.convertLongToTime(ct.timeStamp, TIME_FORMAT_1)
    val destination = ct.dnsQuery?.takeIf { it.isNotBlank() } ?: ct.ipAddress
    val appDisplay = if (ct.appName.isBlank()) stringResource(R.string.network_log_app_name_unknown) else ct.appName

    var appIcon by remember(ct.uid) { mutableStateOf<Drawable?>(null) }
    var appCount by remember(ct.uid) { mutableStateOf(1) }

    LaunchedEffect(ct.uid, ct.appName, ct.usrId) {
        val apps = withContext(Dispatchers.IO) { FirewallManager.getPackageNamesByUid(ct.uid) }
        appCount = apps.size
        appIcon = if (apps.isEmpty()) null else getIcon(context, apps[0])
    }

    val appName =
        when {
            ct.usrId != NO_USER_ID ->
                stringResource(R.string.about_version_install_source, appDisplay, ct.usrId.toString())
            appCount > 1 ->
                stringResource(R.string.ctbs_app_other_apps, appDisplay, "${appCount - 1}")
            else -> appDisplay
        }

    val endpoint = buildString {
        append(ct.ipAddress)
        if (ct.port > 0) append(":${ct.port}")
    }
    val details = buildList {
        add(RethinkLogDetail("Transport", protocol))
        add(RethinkLogDetail("Country", countryDisplay(context, ct.flag)))
        if (endpoint.isNotBlank()) add(RethinkLogDetail("Endpoint", endpoint, monospace = true))
        if (!ct.dnsQuery.isNullOrBlank()) add(RethinkLogDetail("DNS", ct.dnsQuery.orEmpty(), monospace = true))
        if (ct.blockedByRule.isNotBlank()) add(RethinkLogDetail("Rule", ct.blockedByRule, isError = ct.isBlocked))
        if (ct.proxyDetails.isNotBlank()) add(RethinkLogDetail("Proxy", ct.proxyDetails, monospace = true))
        if (summary.duration.isNotBlank()) add(RethinkLogDetail("Duration", summary.duration))
        if (summary.dataUsage.isNotBlank()) add(RethinkLogDetail("Usage", summary.dataUsage))
        if (ct.synack > 0) add(RethinkLogDetail("Latency", "${ct.synack}ms"))
        if (summary.delay.isNotBlank()) add(RethinkLogDetail("Flags", summary.delay))
        if (ct.message.isNotBlank()) add(RethinkLogDetail("Message", ct.message, isError = ct.isBlocked))
    }
    RethinkLogRow(
        model = RethinkLogRowModel(
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
        ),
        index = index,
        itemCount = itemCount,
    )
    return
}

@Composable
private fun AppIconSlot(
    appIcon: Drawable?,
    statusColor: Color,
) {
    val iconDrawable = appIcon

    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        if (iconDrawable != null) {
            Crossfade(targetState = iconDrawable, animationSpec = tween(durationMillis = 180), label = "connIcon") { drawable ->
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

private fun countryDisplay(context: Context, flag: String): String {
    val unknown = context.getString(R.string.network_log_app_name_unknown)
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

private fun protocolLabel(context: Context, port: Int, proto: Int): String {
    if (Protocol.UDP.protocolType != proto && Protocol.TCP.protocolType != proto) {
        return Protocol.getProtocolName(proto).name
    }

    val resolvedPort = KnownPorts.resolvePort(port)
    return if (port == KnownPorts.HTTPS_PORT && proto == Protocol.UDP.protocolType) {
        context.getString(R.string.connection_http3)
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
    var dataUsage = ""
    var delay = ""
    var duration = ""

    if (ct.duration == 0 && ct.downloadBytes == 0L && ct.uploadBytes == 0L && ct.message.isEmpty()) {
        var hasMinSummary = false
        if (hasCid) {
            dataUsage = context.getString(R.string.lbl_active)
            duration = context.getString(R.string.symbol_green_circle)
            hasMinSummary = true
        }

        if (connType.isMetered()) {
            delay = context.getString(R.string.symbol_currency)
            hasMinSummary = true
        }

        if (isRpnProxy(ct.rpid)) {
            delay = context.getString(R.string.ci_desc, delay, context.getString(R.string.symbol_sparkle))
        } else if (isConnectionProxied(ct.blockedByRule, ct.proxyDetails)) {
            delay = context.getString(R.string.ci_desc, delay, context.getString(R.string.symbol_key))
            hasMinSummary = true
        }

        return Summary(dataUsage, duration, delay, hasMinSummary)
    }

    duration = context.getString(
        R.string.single_argument,
        getDurationInHumanReadableFormat(context, ct.duration)
    )

    val download = context.getString(
        R.string.symbol_download,
        Utilities.humanReadableByteCount(ct.downloadBytes, true)
    )
    val upload = context.getString(
        R.string.symbol_upload,
        Utilities.humanReadableByteCount(ct.uploadBytes, true)
    )
    dataUsage = context.getString(R.string.two_argument, upload, download)

    if (connType.isMetered()) {
        delay = context.getString(R.string.ci_desc, delay, context.getString(R.string.symbol_currency))
    }
    if (isConnectionHeavier(ct)) {
        delay = context.getString(R.string.ci_desc, delay, context.getString(R.string.symbol_heavy))
    }
    if (isConnectionSlower(ct)) {
        delay = context.getString(R.string.ci_desc, delay, context.getString(R.string.symbol_turtle))
    }
    if (isRpnProxy(ct.rpid)) {
        delay = context.getString(R.string.ci_desc, delay, context.getString(R.string.symbol_sparkle))
    } else if (containsRelayProxy(ct.rpid)) {
        delay = context.getString(R.string.ci_desc, delay, context.getString(R.string.symbol_bunny))
    } else if (isConnectionProxied(ct.blockedByRule, ct.proxyDetails)) {
        delay = context.getString(R.string.ci_desc, delay, context.getString(R.string.symbol_key))
    }
    if (isRoundTripShorter(ct.synack, ct.isBlocked)) {
        delay = context.getString(R.string.ci_desc, delay, context.getString(R.string.symbol_rocket))
    }

    val showSummary = delay.isNotEmpty() || dataUsage.isNotEmpty()
    return Summary(dataUsage, duration, delay, showSummary)
}

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
