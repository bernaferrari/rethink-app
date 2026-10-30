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

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogDetail
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogRow
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogRowModel
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogKind
import com.bernaferrari.bravedns.database.ConnectionTracker
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.service.FirewallRuleset
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.ui.compose.common.AppIcon
import com.bernaferrari.bravedns.util.Constants.Companion.TIME_FORMAT_1
import com.bernaferrari.bravedns.util.KnownPorts
import com.bernaferrari.bravedns.util.Protocol
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.util.UIUtils.getDurationInHumanReadableFormat
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.formatByteCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

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

    var packageName by remember(ct.uid) { mutableStateOf<String?>(null) }
    var appCount by remember(ct.uid) { mutableIntStateOf(1) }

    LaunchedEffect(ct.uid, ct.appName, ct.usrId) {
        val apps = withContext(Dispatchers.IO) { FirewallManager.getPackageNamesByUid(ct.uid) }
        appCount = apps.size
        packageName = apps.firstOrNull()
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
    val details = listOf(RethinkLogDetail(stringResource(R.string.log_detail_app_name), appName)) +
        connectionDetails(ct, protocol, ruleTitle, summary, unknownApp)
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
        // No package (kernel, unknown uid): the row draws its verdict-tinted placeholder.
        icon = packageName?.let { pkg -> { _ -> AppIcon(pkg, size = Dp.Unspecified) } },
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
        RethinkLogDetail(stringResource(R.string.log_detail_country), UIUtils.countryLabel(ct.flag, unknown)),
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

/** Data received, as a drawn download arrow and the size, for allowed connections (details have both directions). */
private fun dataMoved(ct: ConnectionTracker): RethinkLogFact? {
    if (ct.isBlocked || ct.downloadBytes <= 0) return null
    return RethinkLogFact(formatByteCount(ct.downloadBytes), MaterialSymbols.Filled.ArrowDownward)
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

/**
 * Plain-text readouts for the details: data as "x sent · y received", the duration, and whether the
 * connection was metered. Speed, size, proxy and relay already have their own detail rows, so
 * the old emoji flags (🐘 🐢 🗝️ 🚀) said nothing those rows don't.
 */
@Composable
private fun summaryInfo(context: Context, ct: ConnectionTracker): Summary {
    val connType = ConnectionTracker.ConnType.get(ct.connType)
    var hasCid by remember(ct.connId, ct.uid) { mutableStateOf(false) }
    LaunchedEffect(ct.connId, ct.uid) {
        hasCid = VpnController.hasCid(ct.connId, ct.uid)
    }
    val activeLabel = stringResource(R.string.lbl_active)
    val meteredLabel = stringResource(R.string.ada_app_metered)
    val durationText = getDurationInHumanReadableFormat(context, ct.duration)
    val download = stringResource(R.string.log_data_down, formatByteCount(ct.downloadBytes))
    val upload = stringResource(R.string.log_data_up, formatByteCount(ct.uploadBytes))
    val metered = if (connType.isMetered()) meteredLabel else ""

    if (ct.duration == 0 && ct.downloadBytes == 0L && ct.uploadBytes == 0L && ct.message.isEmpty()) {
        val dataUsage = if (hasCid) activeLabel else ""
        return Summary(dataUsage, "", metered, dataUsage.isNotEmpty() || metered.isNotEmpty())
    }
    return Summary("$upload \u00b7 $download", durationText, metered, true)
}
