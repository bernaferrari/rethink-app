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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.ConnectionTracker
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.FirewallRuleset
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import com.celzero.bravedns.util.Constants.Companion.TIME_FORMAT_1
import com.celzero.bravedns.util.KnownPorts
import com.celzero.bravedns.util.Protocol
import com.celzero.bravedns.util.UIUtils.getDurationInHumanReadableFormat
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getDefaultIcon
import com.celzero.bravedns.util.Utilities.getIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private const val MAX_BYTES = 500000 // 500 KB
private const val MAX_TIME_TCP = 135 // seconds
private const val MAX_TIME_UDP = 135 // seconds
private const val NO_USER_ID = 0

@Composable
fun ConnectionRow(
    ct: ConnectionTracker,
    onShowConnTracker: (ConnectionTracker) -> Unit
) {
    val context = LocalContext.current
    val time = Utilities.convertLongToTime(ct.timeStamp, TIME_FORMAT_1)
    val protocol = protocolLabel(context, ct.port, ct.protocol)
    val summary = summaryInfo(context, ct)
    val summaryHintColor = hintColor(context, ct) ?: MaterialTheme.colorScheme.tertiary
    val domain = ct.dnsQuery.orEmpty()
    val ipAddress = ct.ipAddress
    val destination = domain.takeIf { it.isNotBlank() } ?: ipAddress
    val destinationSecondary = ipAddress.takeIf { domain.isNotBlank() && it.isNotBlank() }

    var appCount by remember(ct.uid) { mutableStateOf(1) }
    var appIcon by remember(ct.uid) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(ct.uid, ct.appName, ct.usrId) {
        val apps = withContext(Dispatchers.IO) { FirewallManager.getPackageNamesByUid(ct.uid) }
        appCount = apps.count()
        appIcon = if (apps.isEmpty()) getDefaultIcon(context) else getIcon(context, apps[0])
    }

    val appName =
        when {
            ct.usrId != NO_USER_ID ->
                stringResource(R.string.about_version_install_source, ct.appName, ct.usrId.toString())
            appCount > 1 ->
                stringResource(R.string.ctbs_app_other_apps, ct.appName, "${appCount - 1}")
            else -> ct.appName
        }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.988f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "connRowScale"
    )

    val statusColor = if (ct.isBlocked) MaterialTheme.colorScheme.error else Color(0xFF2FB36B)
    val metaText =
        buildList {
                add(protocol)
                if (summary.showSummary && summary.dataUsage.isNotBlank()) add(summary.dataUsage)
                if (summary.duration.isNotBlank()) add(summary.duration)
            }
            .joinToString("  ·  ")

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(scale)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    onClick = { onShowConnTracker(ct) }
                ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = if (isPressed) 3.dp else 1.dp,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .padding(start = 8.dp, top = 10.dp, bottom = 10.dp)
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(statusColor, statusColor.copy(alpha = 0.35f))
                            )
                        )
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rememberDrawablePainter(appIcon ?: getDefaultIcon(context))?.let { painter ->
                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = destination,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        destinationSecondary?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = statusColor.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(statusColor)
                                )
                                Text(
                                    text = if (ct.isBlocked) stringResource(R.string.lbl_blocked) else stringResource(R.string.lbl_allowed),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor
                                )
                            }
                        }

                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (metaText.isNotBlank()) {
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (summary.delay.isNotBlank()) {
                    Text(
                        text = summary.delay,
                        style = MaterialTheme.typography.labelSmall,
                        color = summaryHintColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
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

@Composable
private fun hintColor(context: Context, ct: ConnectionTracker): Color? {
    val blocked =
        if (ct.blockedByRule == FirewallRuleset.RULE12.id) {
            ct.proxyDetails.isEmpty()
        } else {
            ct.isBlocked
        }
    val rule =
        if (ct.blockedByRule == FirewallRuleset.RULE12.id && ct.proxyDetails.isEmpty()) {
            FirewallRuleset.RULE18.id
        } else {
            ct.blockedByRule
        }
    return when {
        blocked -> {
            val isError = FirewallRuleset.isProxyError(rule)
            if (isError) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
        }
        FirewallRuleset.shouldShowHint(rule) -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> null
    }
}

private data class Summary(
    val dataUsage: String,
    val duration: String,
    val delay: String,
    val showSummary: Boolean
)

private fun summaryInfo(context: Context, ct: ConnectionTracker): Summary {
    val connType = ConnectionTracker.ConnType.get(ct.connType)
    var dataUsage = ""
    var delay = ""
    var duration = ""

    if (ct.duration == 0 && ct.downloadBytes == 0L && ct.uploadBytes == 0L && ct.message.isEmpty()) {
        var hasMinSummary = false
        if (VpnController.hasCid(ct.connId, ct.uid)) {
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
