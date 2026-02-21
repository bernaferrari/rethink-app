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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.ConnectionTracker
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.FirewallRuleset
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.util.Constants.Companion.TIME_FORMAT_1
import com.celzero.bravedns.util.KnownPorts
import com.celzero.bravedns.util.Protocol
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.getDurationInHumanReadableFormat
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getDefaultIcon
import com.celzero.bravedns.util.Utilities.getIcon
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import com.celzero.bravedns.ui.compose.theme.Dimensions
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.ui.res.stringResource


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
    val protocolLabel = protocolLabel(context, ct.port, ct.protocol)
    val indicatorColor = hintColor(context, ct)
    val summary = summaryInfo(context, ct)
    val domain = ct.dnsQuery
    val ipAddress = ct.ipAddress
    val flag = ct.flag

    var appCount by remember(ct.uid) { mutableStateOf(1) }
    var appIcon by remember(ct.uid) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(ct.uid, ct.appName, ct.usrId) {
        val apps =
            withContext(Dispatchers.IO) { FirewallManager.getPackageNamesByUid(ct.uid) }
        val count = apps.count()
        appCount = count
        appIcon =
            if (apps.isEmpty()) {
                getDefaultIcon(context)
            } else {
                getIcon(context, apps[0])
            }
    }

    val appName =
        when {
            ct.usrId != NO_USER_ID ->
                stringResource(
                    R.string.about_version_install_source,
                    ct.appName,
                    ct.usrId.toString()
                )

            appCount > 1 ->
                stringResource(
                    R.string.ctbs_app_other_apps,
                    ct.appName,
                    "${appCount - 1}"
                )

            else -> ct.appName
        }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "rowScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onShowConnTracker(ct) }
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
            ) {
                // App Icon with tinted background if blocked
                val iconBgColor = if (ct.isBlocked) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    val iconDrawable = appIcon ?: getDefaultIcon(context)
                    val iconPainter = rememberDrawablePainter(iconDrawable)
                    iconPainter?.let { painter ->
                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )

                        // Status Badge
                        val badgeColor =
                            if (ct.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        val badgeContainerColor =
                            if (ct.isBlocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = badgeContainerColor.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = Dimensions.spacingSm)
                        ) {
                            Text(
                                text = if (ct.isBlocked) stringResource(R.string.lbl_blocked) else stringResource(R.string.lbl_allowed),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = ipAddress,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!domain.isNullOrEmpty()) {
                        Text(
                            text = domain,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = flag,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = protocolLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (summary.showSummary) {
                    Text(
                        text = summary.dataUsage,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
                ) {
                    Text(
                        text = summary.duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = summary.delay,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
        context.resources.getString(R.string.connection_http3)
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
            if (isError) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            }
        }

        FirewallRuleset.shouldShowHint(rule) -> {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        else -> null
    }
}

private data class Summary(val dataUsage: String, val duration: String, val delay: String, val showSummary: Boolean)

private fun summaryInfo(context: Context, ct: ConnectionTracker): Summary {
    val connType = ConnectionTracker.ConnType.get(ct.connType)
    var dataUsage = ""
    var delay = ""
    var duration = ""
    var showSummary = false

    if (ct.duration == 0 && ct.downloadBytes == 0L && ct.uploadBytes == 0L && ct.message.isEmpty()) {
        var hasMinSummary = false
        if (VpnController.hasCid(ct.connId, ct.uid)) {
            dataUsage = context.resources.getString(R.string.lbl_active)
            duration = context.resources.getString(R.string.symbol_green_circle)
            hasMinSummary = true
        }

        if (connType.isMetered()) {
            delay = context.resources.getString(R.string.symbol_currency)
            hasMinSummary = true
        }

        if (isRpnProxy(ct.rpid)) {
            delay =
                context.resources.getString(
                    R.string.ci_desc,
                    delay,
                    context.resources.getString(R.string.symbol_sparkle)
                )
        } else if (isConnectionProxied(ct.blockedByRule, ct.proxyDetails)) {
            delay =
                context.resources.getString(
                    R.string.ci_desc,
                    delay,
                    context.resources.getString(R.string.symbol_key)
                )
            hasMinSummary = true
        }
        showSummary = hasMinSummary
        return Summary(dataUsage, duration, delay, showSummary)
    }

    showSummary = true
    duration =
        context.resources.getString(R.string.single_argument, getDurationInHumanReadableFormat(context, ct.duration))
    val download =
        context.resources.getString(
            R.string.symbol_download,
            Utilities.humanReadableByteCount(ct.downloadBytes, true)
        )
    val upload =
        context.resources.getString(
            R.string.symbol_upload,
            Utilities.humanReadableByteCount(ct.uploadBytes, true)
        )
    dataUsage = context.resources.getString(R.string.two_argument, upload, download)

    if (connType.isMetered()) {
        delay =
            context.resources.getString(
                R.string.ci_desc,
                delay,
                context.resources.getString(R.string.symbol_currency)
            )
    }
    if (isConnectionHeavier(ct)) {
        delay =
            context.resources.getString(
                R.string.ci_desc,
                delay,
                context.resources.getString(R.string.symbol_heavy)
            )
    }
    if (isConnectionSlower(ct)) {
        delay =
            context.resources.getString(
                R.string.ci_desc,
                delay,
                context.resources.getString(R.string.symbol_turtle)
            )
    }
    if (isRpnProxy(ct.rpid)) {
        delay =
            context.resources.getString(
                R.string.ci_desc,
                delay,
                context.resources.getString(R.string.symbol_sparkle)
            )
    } else if (containsRelayProxy(ct.rpid)) {
        delay =
            context.resources.getString(
                R.string.ci_desc,
                delay,
                context.resources.getString(R.string.symbol_bunny)
            )
    } else if (isConnectionProxied(ct.blockedByRule, ct.proxyDetails)) {
        delay =
            context.resources.getString(
                R.string.ci_desc,
                delay,
                context.resources.getString(R.string.symbol_key)
            )
    }
    if (isRoundTripShorter(ct.synack, ct.isBlocked)) {
        delay =
            context.resources.getString(
                R.string.ci_desc,
                delay,
                context.resources.getString(R.string.symbol_rocket)
            )
    }
    showSummary = delay.isNotEmpty() || dataUsage.isNotEmpty()
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
