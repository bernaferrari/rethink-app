/*
 * Copyright 2020 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.ui.bottomsheet

import Logger
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.text.TextUtils
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.celzero.bravedns.R
import com.celzero.bravedns.database.ConnectionTracker
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.FirewallRuleset
import com.celzero.bravedns.service.FirewallRuleset.Companion.getFirewallRule
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager.isNotLocalAndRpnProxy
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.activity.AppInfoActivity
import com.celzero.bravedns.util.Protocol
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.UIUtils.htmlToSpannedText
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getIcon
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnTrackerSheet(
    activity: FragmentActivity,
    info: ConnectionTracker,
    persistentState: PersistentState,
    eventLogger: EventLogger,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val firewallLabels = remember { FirewallManager.getLabel(activity).toList() }
    val ipRuleLabels = remember { IpRulesManager.IpRuleStatus.getLabel(activity).toList() }
    val domainRuleLabels = remember { DomainRulesManager.Status.getLabel(activity).toList() }
    val blockAppText = remember { htmlToSpannedText(activity.getString(R.string.bsct_block)) }
    val blockIpText = remember { htmlToSpannedText(activity.getString(R.string.bsct_block_ip)) }
    val blockDomainText = remember { htmlToSpannedText(activity.getString(R.string.bsct_block_domain)) }

    var portDetailText by remember { mutableStateOf("") }
    var appInfoText by remember { mutableStateOf("") }
    var appInfoIconRes by remember { mutableIntStateOf(0) }
    var appInfoNegative by remember { mutableStateOf(false) }
    var appName by remember { mutableStateOf("") }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    var showFirewallRule by remember { mutableStateOf(true) }
    var showUnknownAppSwitch by remember { mutableStateOf(false) }
    var unknownAppChecked by remember { mutableStateOf(false) }
    var connectionFlag by remember { mutableStateOf("") }
    var connectionHeading by remember { mutableStateOf("") }
    var dnsCacheText by remember { mutableStateOf("") }
    var showDnsCacheText by remember { mutableStateOf(false) }
    var showDomainRule by remember { mutableStateOf(true) }
    var connDurationText by remember { mutableStateOf("") }
    var connUploadText by remember { mutableStateOf("") }
    var connDownloadText by remember { mutableStateOf("") }
    var connTypeText by remember { mutableStateOf("") }
    var showSummaryDetails by remember { mutableStateOf(true) }
    var connTypeSecondaryText by remember { mutableStateOf("") }
    var showConnTypeSecondary by remember { mutableStateOf(false) }
    var connectionMessageText by remember { mutableStateOf("") }
    var showConnectionMessage by remember { mutableStateOf(false) }
    var firewallSelection by remember { mutableIntStateOf(0) }
    var ipRuleSelection by remember { mutableIntStateOf(0) }
    var domainRuleSelection by remember { mutableIntStateOf(0) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var rulesDialogTitle by remember { mutableStateOf("") }
    var rulesDialogDesc by remember { mutableStateOf<Spanned?>(null) }
    var rulesDialogIconRes by remember { mutableIntStateOf(0) }

    LaunchedEffect(info) {
        connectionHeading = info.ipAddress
        connectionFlag = info.flag
        updateAppDetails(
            activity,
            info,
            persistentState,
            onUnknownSwitch = { showUnknownAppSwitch = it },
            onShowFirewall = { showFirewallRule = it },
            onUnknownChecked = { unknownAppChecked = it },
            onAppName = { appName = it },
            onAppIcon = { appIcon = it },
            scope = scope
        )
        updateConnDetailsChip(activity, info) { portDetailText = it }
        updateBlockedRulesChip(
            activity,
            info,
            persistentState,
            onText = { appInfoText = it },
            onIcon = { appInfoIconRes = it },
            onNegative = { appInfoNegative = it }
        )
        displaySummaryDetails(
            activity,
            info,
            persistentState,
            onMessage = { connectionMessageText = it },
            onShowMessage = { showConnectionMessage = it },
            onDuration = { connDurationText = it },
            onConnType = { connTypeText = it },
            onUpload = { connUploadText = it },
            onDownload = { connDownloadText = it },
            onShowSummary = { showSummaryDetails = it },
            onShowSecondary = { showConnTypeSecondary = it },
            onSecondaryText = { connTypeSecondaryText = it }
        )
        updateIpRulesUi(info.uid, info.ipAddress, scope) { ipRuleSelection = it }
        updateDnsIfAvailable(
            activity,
            info,
            onShowDns = { showDnsCacheText = it },
            onDnsText = { dnsCacheText = it },
            onDomainRule = { showDomainRule = it },
            onDomainRuleSelection = { domainRuleSelection = it }
        )
        refreshFirewallRulesUi(info, scope) { firewallSelection = it }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        val borderColor = Color(fetchColor(activity, R.attr.border))
        val chipTextColor =
            if (appInfoNegative) {
                Color(fetchColor(activity, R.attr.chipTextNegative))
            } else {
                Color(fetchColor(activity, R.attr.chipTextPositive))
            }
        val chipBackgroundColor =
            if (appInfoNegative) {
                Color(fetchColor(activity, R.attr.chipBgColorNegative))
            } else {
                Color(fetchColor(activity, R.attr.chipBgColorPositive))
            }
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(60.dp)
                        .height(3.dp)
                        .background(borderColor, RoundedCornerShape(2.dp))
                        .padding(top = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = portDetailText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                AssistChip(
                    onClick = {
                        if (info.blockedByRule.isNotBlank()) {
                            val result =
                                showFirewallRulesDialog(
                                    activity,
                                    info
                                )
                            rulesDialogTitle = result.title
                            rulesDialogDesc = result.desc
                            rulesDialogIconRes = result.icon
                            showRulesDialog = true
                        }
                    },
                    label = { Text(text = appInfoText, color = chipTextColor) },
                    leadingIcon = {
                        if (appInfoIconRes != 0) {
                            Image(
                                painter = painterResource(id = appInfoIconRes),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                colorFilter = ColorFilter.tint(chipTextColor)
                            )
                        }
                    },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor = chipBackgroundColor
                        )
                )
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable {
                            onDismiss()
                            val intent = Intent(activity, AppInfoActivity::class.java)
                            intent.putExtra(AppInfoActivity.INTENT_UID, info.uid)
                            activity.startActivity(intent)
                        }
                        .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val iconDrawable =
                    appIcon ?: ContextCompat.getDrawable(activity, R.drawable.default_app_icon)
                iconDrawable?.let { drawable ->
                    val painter = rememberDrawablePainter(drawable)
                    painter?.let {
                        Image(
                            painter = it,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = connectionFlag,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(end = 8.dp)
                )
                SelectionContainer {
                    Text(
                        text = connectionHeading,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (showDnsCacheText) {
                SelectionContainer {
                    Text(
                        text = dnsCacheText,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    )
                }
            }

            if (showSummaryDetails) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 50.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = connDurationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = connUploadText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier =
                            Modifier.width(1.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = connTypeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = connDownloadText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showConnTypeSecondary) {
                Text(
                    text = connTypeSecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
            }

            if (showFirewallRule) {
                SelectionRow(
                    label = { HtmlText(blockAppText) },
                    labels = firewallLabels,
                    selectedIndex = firewallSelection,
                    onSelect = { onFirewallSelected(info, it, persistentState, activity, eventLogger, scope) }
                )
            }

            if (showUnknownAppSwitch) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activity.getString(R.string.univ_firewall_rule_3),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = unknownAppChecked,
                        onCheckedChange = {
                            unknownAppChecked = it
                            Napier.d(
                                "Unknown app, universal firewall settings(block unknown app): $unknownAppChecked"
                            )
                            persistentState.setBlockUnknownConnections(unknownAppChecked)
                            logEvent(
                                eventLogger,
                                "Universal firewall setting changed",
                                "Block unknown apps: $unknownAppChecked"
                            )
                        }
                    )
                }
            }

            SelectionRow(
                label = { HtmlText(blockIpText) },
                labels = ipRuleLabels,
                selectedIndex = ipRuleSelection,
                onSelect = {
                    ipRuleSelection = it
                    applyIpRule(info, IpRulesManager.IpRuleStatus.getStatus(it), eventLogger, scope)
                }
            )

            if (showDomainRule) {
                SelectionRow(
                    label = { HtmlText(blockDomainText) },
                    labels = domainRuleLabels,
                    selectedIndex = domainRuleSelection,
                    onSelect = {
                        val dnsQuery = info.dnsQuery
                        if (dnsQuery == null) {
                            Napier.w("DNS query is null, cannot apply domain rule")
                            return@SelectionRow
                        }
                        val fid = DomainRulesManager.Status.getStatus(it)
                        if (DomainRulesManager.getDomainRule(dnsQuery, info.uid) == fid) {
                            return@SelectionRow
                        }
                        domainRuleSelection = it
                        applyDomainRule(info, fid, eventLogger, scope)
                    }
                )
            }

            if (showConnectionMessage) {
                SelectionContainer {
                    Text(
                        text = connectionMessageText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    )
                }
            }
        }

        if (showRulesDialog) {
            AlertDialog(
                onDismissRequest = { showRulesDialog = false },
                title = { Text(text = rulesDialogTitle) },
                icon = {
                    if (rulesDialogIconRes != 0) {
                        Image(
                            painter = painterResource(id = rulesDialogIconRes),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                text = {
                    val desc = rulesDialogDesc
                    if (desc != null) {
                        HtmlText(desc)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRulesDialog = false }) {
                        Text(text = activity.getString(R.string.lbl_dismiss))
                    }
                }
            )
        }
    }
}

private fun updateConnDetailsChip(
    activity: FragmentActivity,
    info: ConnectionTracker,
    onText: (String) -> Unit
) {
    val protocol = Protocol.getProtocolName(info.protocol).name
    val time =
        DateUtils.getRelativeTimeSpanString(
            info.timeStamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )
    val protocolDetails = "$protocol/${info.port}"
    val text =
        if (info.isBlocked) {
            activity.getString(R.string.bsct_conn_desc_blocked, protocolDetails, time)
        } else {
            activity.getString(R.string.bsct_conn_desc_allowed, protocolDetails, time)
        }
    onText(text)
}

private fun updateBlockedRulesChip(
    activity: FragmentActivity,
    info: ConnectionTracker,
    persistentState: PersistentState,
    onText: (String) -> Unit,
    onIcon: (Int) -> Unit,
    onNegative: (Boolean) -> Unit
) {
    val text =
        if (info.blockedByRule.isBlank()) {
            activity.getString(R.string.firewall_rule_no_rule)
        } else {
            val rule = info.blockedByRule
            val isIpnProxy = isNotLocalAndRpnProxy(info.proxyDetails)
            if (rule.contains(FirewallRuleset.RULE2G.id)) {
                getFirewallRule(FirewallRuleset.RULE2G.id)?.title?.let { activity.getString(it) }
                    ?: activity.getString(R.string.firewall_rule_no_rule)
            } else if (info.proxyDetails.isNotEmpty() && isIpnProxy) {
                activity.getString(
                    R.string.two_argument_colon,
                    activity.getString(FirewallRuleset.RULE12.title),
                    info.proxyDetails
                )
            } else {
                if (isInvalidProxyDetails(info)) {
                    activity.getString(
                        getFirewallRule(FirewallRuleset.RULE18.id)?.title
                            ?: R.string.firewall_rule_no_rule
                    )
                } else {
                    getFirewallRule(rule)?.title?.let { activity.getString(it) }
                        ?: activity.getString(R.string.firewall_rule_no_rule)
                }
            }
        }
    onText(text)
    onIcon(FirewallRuleset.getRulesIcon(info.blockedByRule))
    onNegative(info.isBlocked || isInvalidProxyDetails(info))
}

private fun isInvalidProxyDetails(info: ConnectionTracker): Boolean {
    val isIpnProxy = isNotLocalAndRpnProxy(info.proxyDetails)
    val rule = info.blockedByRule
    val isRuleAddedAsProxy = getFirewallRule(rule)?.id == FirewallRuleset.RULE12.id
    return isRuleAddedAsProxy && (info.proxyDetails.isEmpty() || !isIpnProxy)
}

private fun updateAppDetails(
    activity: FragmentActivity,
    info: ConnectionTracker,
    persistentState: PersistentState,
    onUnknownSwitch: (Boolean) -> Unit,
    onShowFirewall: (Boolean) -> Unit,
    onUnknownChecked: (Boolean) -> Unit,
    onAppName: (String) -> Unit,
    onAppIcon: (Drawable?) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch(Dispatchers.IO) {
        val appNames = FirewallManager.getAppNamesByUid(info.uid)
        if (appNames.isEmpty()) {
            withContext(Dispatchers.Main) {
                onShowFirewall(false)
                onUnknownSwitch(true)
                onUnknownChecked(persistentState.getBlockUnknownConnections())
                onAppName(info.appName)
                onAppIcon(ContextCompat.getDrawable(activity, R.drawable.default_app_icon))
            }
            return@launch
        }
        val pkgName = FirewallManager.getPackageNameByAppName(appNames[0])
        val appCount = appNames.count()
        withContext(Dispatchers.Main) {
            onShowFirewall(true)
            onUnknownSwitch(false)
            onAppName(
                if (appCount >= 2) {
                    activity.getString(
                        R.string.ctbs_app_other_apps,
                        appNames[0],
                        appCount.minus(1).toString()
                    ) + "  ❯"
                } else {
                    appNames[0] + "  ❯"
                }
            )
            val icon =
                if (pkgName != null) {
                    getIcon(activity, pkgName, info.appName)
                } else {
                    ContextCompat.getDrawable(activity, R.drawable.default_app_icon)
                }
            onAppIcon(icon)
        }
    }
}

private fun displaySummaryDetails(
    activity: FragmentActivity,
    info: ConnectionTracker,
    persistentState: PersistentState,
    onMessage: (String) -> Unit,
    onShowMessage: (Boolean) -> Unit,
    onDuration: (String) -> Unit,
    onConnType: (String) -> Unit,
    onUpload: (String) -> Unit,
    onDownload: (String) -> Unit,
    onShowSummary: (Boolean) -> Unit,
    onShowSecondary: (Boolean) -> Unit,
    onSecondaryText: (String) -> Unit
) {
    val connectionMessageText =
        if (
            Logger.LoggerLevel.fromId(persistentState.goLoggerLevel.toInt())
                .isLessThan(Logger.LoggerLevel.DEBUG)
        ) {
            "${info.proxyDetails}; ${info.rpid}; ${info.connId}; ${info.message}; ${info.synack}"
        } else {
            info.message
        }
    onMessage(connectionMessageText)
    onShowMessage(connectionMessageText.isNotBlank())

    val durationText =
        if (VpnController.hasCid(info.connId, info.uid)) {
            activity.getString(
                R.string.two_argument_space,
                activity.getString(R.string.lbl_active),
                activity.getString(R.string.symbol_green_circle)
            )
        } else {
            activity.getString(
                R.string.two_argument_space,
                activity.getString(R.string.symbol_hyphen),
                activity.getString(R.string.symbol_clock)
            )
        }
    onDuration(durationText)

    val connType = ConnectionTracker.ConnType.get(info.connType)
    val connTypeText =
        if (connType.isMetered()) {
            activity.getString(
                R.string.two_argument_space,
                activity.getString(R.string.ada_app_metered),
                activity.getString(R.string.symbol_currency)
            )
        } else {
            activity.getString(
                R.string.two_argument_space,
                activity.getString(R.string.ada_app_unmetered),
                activity.getString(R.string.symbol_global)
            )
        }
    onConnType(connTypeText)

    if (info.message.isEmpty() && info.duration == 0 && info.downloadBytes == 0L &&
        info.uploadBytes == 0L
    ) {
        onShowSummary(false)
        onShowSecondary(true)
        onSecondaryText(connTypeText)
        return
    }

    onShowSummary(true)
    onShowSecondary(false)
    val downloadBytes =
        activity.getString(
            R.string.symbol_download,
            Utilities.humanReadableByteCount(info.downloadBytes, true)
        )
    val uploadBytes =
        activity.getString(
            R.string.symbol_upload,
            Utilities.humanReadableByteCount(info.uploadBytes, true)
        )
    onUpload(uploadBytes)
    onDownload(downloadBytes)
    val duration = UIUtils.getDurationInHumanReadableFormat(activity, info.duration)
    onDuration(activity.getString(R.string.two_argument_space, duration, activity.getString(R.string.symbol_clock)))
}

private fun updateDnsIfAvailable(
    activity: FragmentActivity,
    info: ConnectionTracker,
    onShowDns: (Boolean) -> Unit,
    onDnsText: (String) -> Unit,
    onDomainRule: (Boolean) -> Unit,
    onDomainRuleSelection: (Int) -> Unit
) {
    val domain = info.dnsQuery
    val uid = info.uid
    val flag = info.flag

    if (domain.isNullOrEmpty()) {
        onShowDns(true)
        onDnsText(UIUtils.getCountryNameFromFlag(flag))
        onDomainRule(false)
        return
    }

    val status = DomainRulesManager.getDomainRule(domain, uid)
    onDomainRuleSelection(status.id)
    onShowDns(true)
    onDomainRule(true)
    onDnsText(
        activity.getString(
            R.string.two_argument,
            UIUtils.getCountryNameFromFlag(flag),
            domain
        )
    )
}

private fun refreshFirewallRulesUi(
    info: ConnectionTracker,
    scope: kotlinx.coroutines.CoroutineScope,
    onSelection: (Int) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        val appStatus = FirewallManager.appStatus(info.uid)
        val connStatus = FirewallManager.connectionStatus(info.uid)
        val selection =
            when (appStatus) {
                FirewallManager.FirewallStatus.NONE -> {
                    when (connStatus) {
                        FirewallManager.ConnectionStatus.ALLOW -> 0
                        FirewallManager.ConnectionStatus.BOTH -> 1
                        FirewallManager.ConnectionStatus.UNMETERED -> 2
                        FirewallManager.ConnectionStatus.METERED -> 3
                    }
                }
                FirewallManager.FirewallStatus.ISOLATE -> 4
                FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL -> 5
                FirewallManager.FirewallStatus.BYPASS_UNIVERSAL -> 6
                FirewallManager.FirewallStatus.EXCLUDE -> 7
                else -> 0
            }
        withContext(Dispatchers.Main) { onSelection(selection) }
    }
}

private fun updateIpRulesUi(
    uid: Int,
    ipAddress: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onSelection: (Int) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        val rule = IpRulesManager.getMostSpecificRuleMatch(uid, ipAddress)
        withContext(Dispatchers.Main) { onSelection(rule.id) }
    }
}

private fun onFirewallSelected(
    info: ConnectionTracker,
    position: Int,
    persistentState: PersistentState,
    activity: FragmentActivity,
    eventLogger: EventLogger,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val fStatus = FirewallManager.FirewallStatus.getStatusByLabel(position)
    val connStatus = FirewallManager.ConnectionStatus.getStatusByLabel(position)
    val uid = info.uid
    scope.launch(Dispatchers.IO) {
        val a = FirewallManager.appStatus(uid)
        val c = FirewallManager.connectionStatus(uid)
        if (a == fStatus && c == connStatus) return@launch

        if (VpnController.isVpnLockdown() && fStatus.isExclude()) {
            withContext(Dispatchers.Main) {
                showToastUiCentered(
                    activity,
                    activity.getString(R.string.hsf_exclude_error),
                    Toast.LENGTH_LONG
                )
            }
            return@launch
        }

        if (FirewallManager.isUnknownPackage(uid) && fStatus.isExclude()) {
            withContext(Dispatchers.Main) {
                showToastUiCentered(
                    activity,
                    activity.getString(R.string.exclude_no_package_err_toast),
                    Toast.LENGTH_LONG
                )
            }
            return@launch
        }

        Napier.i(
            "Change in firewall rule for app uid: ${info.uid}, firewall status: $fStatus, conn status: $connStatus"
        )
        FirewallManager.updateFirewallStatus(uid, fStatus, connStatus)
        logEvent(
            eventLogger,
            "Firewall rule changed",
            "UID: $uid, FirewallStatus: ${fStatus.name}, ConnectionStatus: ${connStatus.name}"
        )
    }
}

private fun applyIpRule(
    info: ConnectionTracker,
    ipRuleStatus: IpRulesManager.IpRuleStatus,
    eventLogger: EventLogger,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch(Dispatchers.IO) {
        if (IpRulesManager.getMostSpecificRuleMatch(info.uid, info.ipAddress) == ipRuleStatus) {
            return@launch
        }
        val ipPair = IpRulesManager.getIpNetPort(info.ipAddress)
        val ip = ipPair.first ?: return@launch
        IpRulesManager.addIpRule(
            info.uid,
            ip,
            /*wildcard-port*/ 0,
            ipRuleStatus,
            proxyId = "",
            proxyCC = ""
        )
        Napier.i("apply ip-rule for ${info.uid}, $ip, ${ipRuleStatus.name}")
        logEvent(
            eventLogger,
            "IP rule changed",
            "UID: ${info.uid}, IP: $ip, IpRuleStatus: ${ipRuleStatus.name}"
        )
    }
}

private fun applyDomainRule(
    info: ConnectionTracker,
    domainRuleStatus: DomainRulesManager.Status,
    eventLogger: EventLogger,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val dnsQuery = info.dnsQuery ?: return
    Napier.i("Apply domain rule for $dnsQuery, ${domainRuleStatus.name}")
    scope.launch(Dispatchers.IO) {
        DomainRulesManager.addDomainRule(
            dnsQuery,
            domainRuleStatus,
            DomainRulesManager.DomainType.DOMAIN,
            info.uid
        )
        logEvent(
            eventLogger,
            "Domain rule changed",
            "Domain: $dnsQuery, UID: ${info.uid}, DomainRuleStatus: ${domainRuleStatus.name}"
        )
    }
}

private data class RulesDialogContent(
    val title: String,
    val desc: Spanned,
    val icon: Int
)

private fun showFirewallRulesDialog(
    activity: FragmentActivity,
    info: ConnectionTracker
): RulesDialogContent {
    val blockedRule = info.blockedByRule
    val iconRes = FirewallRuleset.getRulesIcon(blockedRule)
    val headingText: String
    val descText: Spanned

    if (blockedRule.contains(FirewallRuleset.RULE2G.id)) {
        val group: Multimap<String, String> = HashMultimap.create()
        val blocklists =
            if (info.blocklists.isEmpty()) {
                val startIndex = blockedRule.indexOfFirst { it == '|' }
                blockedRule.substring(startIndex + 1).split(",")
            } else {
                info.blocklists.split(",")
            }

        blocklists.forEach {
            val items = it.split(":")
            if (items.count() <= 1) return@forEach
            group.put(items[0], items[1])
        }
        descText = formatText(activity, group)
        val groupCount = group.keys().distinct().count()
        headingText =
            if (groupCount > 1) {
                "${group.keys().firstOrNull()} +${groupCount - 1}"
            } else if (groupCount == 1) {
                group.keys().firstOrNull() ?: activity.getString(R.string.firewall_rule_no_rule)
            } else {
                val tempDesc =
                    getFirewallRule(FirewallRuleset.RULE2G.id)?.let { activity.getString(it.desc) }
                        ?: activity.getString(R.string.firewall_rule_no_rule_desc)
                htmlToSpannedText(tempDesc)
                getFirewallRule(FirewallRuleset.RULE2G.id)?.let { activity.getString(it.title) }
                    ?: activity.getString(R.string.firewall_rule_no_rule)
            }
    } else {
        headingText =
            getFirewallRule(blockedRule)?.let { activity.getString(it.title) }
                ?: activity.getString(R.string.firewall_rule_no_rule)
        val tempDesc =
            getFirewallRule(blockedRule)?.let { activity.getString(it.desc) }
                ?: activity.getString(R.string.firewall_rule_no_rule_desc)
        descText = htmlToSpannedText(tempDesc)
    }
    return RulesDialogContent(headingText, descText, iconRes)
}

private fun formatText(activity: FragmentActivity, groupNames: Multimap<String, String>): Spanned {
    var text = ""
    groupNames.keys().distinct().forEach {
        val heading =
            it.replaceFirstChar { a ->
                if (a.isLowerCase()) a.titlecase(Locale.getDefault()) else a.toString()
            }
        text +=
            activity.getString(
                R.string.dns_btm_sheet_dialog_message,
                heading,
                groupNames.get(it).count().toString(),
                TextUtils.join(", ", groupNames.get(it))
            )
    }
    text = text.replace(",", ", ")
    return htmlToSpannedText(text)
}

private fun logEvent(
    eventLogger: EventLogger,
    msg: String,
    details: String
) {
    eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, msg, EventSource.UI, false, details)
}

@Composable
private fun SelectionRow(
    label: @Composable () -> Unit,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(0.6f)) { label() }
        Box(modifier = Modifier.weight(0.4f), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = { expanded = true }) {
                Text(text = labels.getOrNull(selectedIndex) ?: "")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                labels.forEachIndexed { index, item ->
                    DropdownMenuItem(
                        text = { Text(text = item) },
                        onClick = {
                            expanded = false
                            onSelect(index)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HtmlText(spanned: Spanned, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Text(
        text = spanned.toString(),
        style = MaterialTheme.typography.bodyLarge,
        color = Color(fetchColor(context, R.attr.primaryTextColor)),
        modifier = modifier.fillMaxWidth()
    )
}
