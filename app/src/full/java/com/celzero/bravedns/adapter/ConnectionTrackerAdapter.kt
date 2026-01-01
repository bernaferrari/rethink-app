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
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.celzero.bravedns.R
import com.celzero.bravedns.database.ConnectionTracker
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.FirewallRuleset
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.bottomsheet.ConnTrackerDialog
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants.Companion.TIME_FORMAT_1
import com.celzero.bravedns.util.KnownPorts
import com.celzero.bravedns.util.Protocol
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.getDurationInHumanReadableFormat
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getDefaultIcon
import com.celzero.bravedns.util.Utilities.getIcon
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class ConnectionTrackerAdapter(private val context: Context) :
    PagingDataAdapter<ConnectionTracker, ConnectionTrackerAdapter.ConnectionTrackerViewHolder>(
        DIFF_CALLBACK
    ) {

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<ConnectionTracker>() {
                override fun areItemsTheSame(old: ConnectionTracker, new: ConnectionTracker): Boolean {
                    return old.id == new.id
                }

                override fun areContentsTheSame(old: ConnectionTracker, new: ConnectionTracker): Boolean {
                    return old == new
                }
            }

        private const val MAX_BYTES = 500000 // 500 KB
        private const val MAX_TIME_TCP = 135 // seconds
        private const val MAX_TIME_UDP = 135 // seconds
        private const val NO_USER_ID = 0
        private const val TAG = "ConnTrackAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConnectionTrackerViewHolder {
        val composeView = ComposeView(parent.context)
        composeView.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        return ConnectionTrackerViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: ConnectionTrackerViewHolder, position: Int) {
        val connTracker: ConnectionTracker = getItem(position) ?: return
        holder.update(connTracker)
    }

    inner class ConnectionTrackerViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        fun update(connTracker: ConnectionTracker) {
            composeView.setContent {
                RethinkTheme {
                    ConnectionRow(connTracker)
                }
            }
        }
    }

    @Composable
    fun ConnectionRow(ct: ConnectionTracker) {
        val time = Utilities.convertLongToTime(ct.timeStamp, TIME_FORMAT_1)
        val protocolLabel = protocolLabel(ct.port, ct.protocol)
        val indicatorColor = hintColor(ct)
        val summary = summaryInfo(ct)
        val domain = ct.dnsQuery
        val ipAddress = ct.ipAddress
        val flag = ct.flag

        var appName by remember(ct.uid, ct.appName, ct.usrId) { mutableStateOf(ct.appName) }
        var appIcon by remember(ct.uid) { mutableStateOf<Drawable?>(null) }

        LaunchedEffect(ct.uid, ct.appName, ct.usrId) {
            val apps =
                withContext(Dispatchers.IO) { FirewallManager.getPackageNamesByUid(ct.uid) }
            val count = apps.count()
            appName =
                when {
                    ct.usrId != NO_USER_ID ->
                        context.getString(
                            R.string.about_version_install_source,
                            ct.appName,
                            ct.usrId.toString()
                        )
                    count > 1 ->
                        context.getString(
                            R.string.ctbs_app_other_apps,
                            ct.appName,
                            "${count - 1}"
                        )
                    else -> ct.appName
                }
            appIcon =
                if (apps.isEmpty()) {
                    getDefaultIcon(context)
                } else {
                    getIcon(context, apps[0])
                }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { openBottomSheet(ct) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                AndroidView(
                    factory = { ctx -> AppCompatImageView(ctx) },
                    update = { imageView ->
                        Glide.with(imageView)
                            .load(appIcon)
                            .error(getDefaultIcon(context))
                            .into(imageView)
                    },
                    modifier = Modifier.size(32.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = protocolLabel,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        Text(
                            text = flag,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = ipAddress,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!domain.isNullOrEmpty()) {
                        Text(
                            text = domain,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = time, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = summary.duration,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = summary.delay,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (summary.showSummary) {
                Text(
                    text = summary.dataUsage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.fillMaxWidth())
        }
    }

    private fun protocolLabel(port: Int, proto: Int): String {
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

    private fun hintColor(ct: ConnectionTracker): Color? {
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
                    Color(UIUtils.fetchColor(context, R.attr.chipTextNeutral))
                } else {
                    Color(ContextCompat.getColor(context, R.color.colorRed_A400))
                }
            }
            FirewallRuleset.shouldShowHint(rule) -> {
                Color(ContextCompat.getColor(context, R.color.primaryLightColorText))
            }
            else -> null
        }
    }

    private data class Summary(val dataUsage: String, val duration: String, val delay: String, val showSummary: Boolean)

    private fun summaryInfo(ct: ConnectionTracker): Summary {
        val connType = ConnectionTracker.ConnType.get(ct.connType)
        var dataUsage = ""
        var delay = ""
        var duration = ""
        var showSummary = false

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
                delay =
                    context.getString(
                        R.string.ci_desc,
                        delay,
                        context.getString(R.string.symbol_sparkle)
                    )
            } else if (isConnectionProxied(ct.blockedByRule, ct.proxyDetails)) {
                delay =
                    context.getString(
                        R.string.ci_desc,
                        delay,
                        context.getString(R.string.symbol_key)
                    )
                hasMinSummary = true
            }
            showSummary = hasMinSummary
            return Summary(dataUsage, duration, delay, showSummary)
        }

        showSummary = true
        duration = context.getString(R.string.single_argument, getDurationInHumanReadableFormat(context, ct.duration))
        val download =
            context.getString(
                R.string.symbol_download,
                Utilities.humanReadableByteCount(ct.downloadBytes, true)
            )
        val upload =
            context.getString(
                R.string.symbol_upload,
                Utilities.humanReadableByteCount(ct.uploadBytes, true)
            )
        dataUsage = context.getString(R.string.two_argument, upload, download)

        if (connType.isMetered()) {
            delay =
                context.getString(
                    R.string.ci_desc,
                    delay,
                    context.getString(R.string.symbol_currency)
                )
        }
        if (isConnectionHeavier(ct)) {
            delay =
                context.getString(
                    R.string.ci_desc,
                    delay,
                    context.getString(R.string.symbol_heavy)
                )
        }
        if (isConnectionSlower(ct)) {
            delay =
                context.getString(
                    R.string.ci_desc,
                    delay,
                    context.getString(R.string.symbol_turtle)
                )
        }
        if (isRpnProxy(ct.rpid)) {
            delay =
                context.getString(
                    R.string.ci_desc,
                    delay,
                    context.getString(R.string.symbol_sparkle)
                )
        } else if (containsRelayProxy(ct.rpid)) {
            delay =
                context.getString(
                    R.string.ci_desc,
                    delay,
                    context.getString(R.string.symbol_bunny)
                )
        } else if (isConnectionProxied(ct.blockedByRule, ct.proxyDetails)) {
            delay =
                context.getString(
                    R.string.ci_desc,
                    delay,
                    context.getString(R.string.symbol_key)
                )
        }
        if (isRoundTripShorter(ct.synack, ct.isBlocked)) {
            delay =
                context.getString(
                    R.string.ci_desc,
                    delay,
                    context.getString(R.string.symbol_rocket)
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

    private fun openBottomSheet(ct: ConnectionTracker) {
        if (context !is FragmentActivity) {
            Napier.w("$TAG err opening the connection tracker bottomsheet")
            return
        }
        ConnTrackerDialog(context, ct).show()
    }
}
