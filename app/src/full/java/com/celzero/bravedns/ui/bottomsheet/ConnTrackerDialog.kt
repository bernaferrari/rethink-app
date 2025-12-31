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
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.TypedValue
import android.widget.TextView
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
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
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Protocol
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.UIUtils.htmlToSpannedText
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getIcon
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale

class ConnTrackerDialog(
    private val activity: FragmentActivity,
    private val info: ConnectionTracker
) : KoinComponent {
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()
    private val eventLogger by inject<EventLogger>()

    private val firewallLabels = FirewallManager.getLabel(activity).toList()
    private val ipRuleLabels = IpRulesManager.IpRuleStatus.getLabel(activity).toList()
    private val domainRuleLabels = DomainRulesManager.Status.getLabel(activity).toList()
    private val blockAppText = htmlToSpannedText(activity.getString(R.string.bsct_block))
    private val blockIpText = htmlToSpannedText(activity.getString(R.string.bsct_block_ip))
    private val blockDomainText = htmlToSpannedText(activity.getString(R.string.bsct_block_domain))

    private var portDetailText by mutableStateOf("")
    private var appInfoText by mutableStateOf("")
    private var appInfoIconRes by mutableIntStateOf(0)
    private var appInfoNegative by mutableStateOf(false)

    private var appName by mutableStateOf("")
    private var appIcon by mutableStateOf<Drawable?>(null)
    private var showFirewallRule by mutableStateOf(true)
    private var showUnknownAppSwitch by mutableStateOf(false)
    private var unknownAppChecked by mutableStateOf(false)

    private var connectionFlag by mutableStateOf("")
    private var connectionHeading by mutableStateOf("")
    private var dnsCacheText by mutableStateOf("")
    private var showDnsCacheText by mutableStateOf(false)
    private var showDomainRule by mutableStateOf(true)

    private var connDurationText by mutableStateOf("")
    private var connUploadText by mutableStateOf("")
    private var connDownloadText by mutableStateOf("")
    private var connTypeText by mutableStateOf("")
    private var showSummaryDetails by mutableStateOf(true)
    private var connTypeSecondaryText by mutableStateOf("")
    private var showConnTypeSecondary by mutableStateOf(false)

    private var connectionMessageText by mutableStateOf("")
    private var showConnectionMessage by mutableStateOf(false)

    private var firewallSelection by mutableIntStateOf(0)
    private var ipRuleSelection by mutableIntStateOf(0)
    private var domainRuleSelection by mutableIntStateOf(0)

    private var showRulesDialog by mutableStateOf(false)
    private var rulesDialogTitle by mutableStateOf("")
    private var rulesDialogDesc by mutableStateOf<Spanned?>(null)
    private var rulesDialogIconRes by mutableIntStateOf(0)

    init {
        val composeView = ComposeView(activity)
        composeView.setContent {
            RethinkTheme {
                ConnTrackerContent()
            }
        }
        dialog.setContentView(composeView)
        dialog.setOnShowListener {
            dialog.useTransparentNoDimBackground()
            dialog.window?.let { window ->
                if (isAtleastQ()) {
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    controller.isAppearanceLightNavigationBars = false
                    window.isNavigationBarContrastEnforced = false
                }
            }
        }
        initView()
        refreshFirewallRulesUi()
    }

    fun show() {
        dialog.show()
        refreshFirewallRulesUi()
    }

    private fun getThemeId(): Int {
        val isDark =
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return Themes.getBottomsheetCurrentTheme(isDark, persistentState.theme)
    }

    private fun initView() {
        connectionHeading = info.ipAddress
        connectionFlag = info.flag

        updateAppDetails()
        updateConnDetailsChip()
        updateBlockedRulesChip()
        displaySummaryDetails()
        updateIpRulesUi(info.uid, info.ipAddress)
        updateDnsIfAvailable()
    }

    private fun refreshFirewallRulesUi() {
        val uid = info.uid
        io {
            val appStatus = FirewallManager.appStatus(uid)
            val connStatus = FirewallManager.connectionStatus(uid)
            uiCtx { updateFirewallRulesUi(appStatus, connStatus) }
        }
    }

    private fun updateDnsIfAvailable() {
        val domain = info.dnsQuery
        val uid = info.uid
        val flag = info.flag

        if (domain.isNullOrEmpty()) {
            showDnsCacheText = true
            dnsCacheText = UIUtils.getCountryNameFromFlag(flag)
            showDomainRule = false
            return
        }

        val status = DomainRulesManager.getDomainRule(domain, uid)
        domainRuleSelection = status.id
        showDnsCacheText = true
        showDomainRule = true
        dnsCacheText =
            activity.getString(R.string.two_argument, UIUtils.getCountryNameFromFlag(flag), domain)
    }

    private fun updateConnDetailsChip() {
        val protocol = Protocol.getProtocolName(info.protocol).name
        val time =
            DateUtils.getRelativeTimeSpanString(
                info.timeStamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
        val protocolDetails = "$protocol/${info.port}"
        portDetailText =
            if (info.isBlocked) {
                activity.getString(R.string.bsct_conn_desc_blocked, protocolDetails, time)
            } else {
                activity.getString(R.string.bsct_conn_desc_allowed, protocolDetails, time)
            }
    }

    private fun updateBlockedRulesChip() {
        if (info.blockedByRule.isBlank()) {
            appInfoText = activity.getString(R.string.firewall_rule_no_rule)
        } else {
            val rule = info.blockedByRule
            val isIpnProxy = isNotLocalAndRpnProxy(info.proxyDetails)
            if (rule.contains(FirewallRuleset.RULE2G.id)) {
                appInfoText =
                    getFirewallRule(FirewallRuleset.RULE2G.id)?.title?.let { activity.getString(it) }
                        ?: activity.getString(R.string.firewall_rule_no_rule)
            } else if (info.proxyDetails.isNotEmpty() && isIpnProxy) {
                appInfoText =
                    activity.getString(
                        R.string.two_argument_colon,
                        activity.getString(FirewallRuleset.RULE12.title),
                        info.proxyDetails
                    )
            } else {
                appInfoText =
                    if (isInvalidProxyDetails()) {
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
        appInfoIconRes = FirewallRuleset.getRulesIcon(info.blockedByRule)
        appInfoNegative = info.isBlocked || isInvalidProxyDetails()
    }

    private fun isInvalidProxyDetails(): Boolean {
        val isIpnProxy = isNotLocalAndRpnProxy(info.proxyDetails)
        val rule = info.blockedByRule
        val isRuleAddedAsProxy = getFirewallRule(rule)?.id == FirewallRuleset.RULE12.id
        if (isRuleAddedAsProxy && (info.proxyDetails.isEmpty() || !isIpnProxy)) {
            return true
        }
        return false
    }

    private fun updateAppDetails() {
        io {
            val appNames = FirewallManager.getAppNamesByUid(info.uid)
            if (appNames.isEmpty()) {
                uiCtx { handleNonApp() }
                return@io
            }
            val pkgName = FirewallManager.getPackageNameByAppName(appNames[0])
            val appCount = appNames.count()
            uiCtx {
                showFirewallRule = true
                showUnknownAppSwitch = false
                appName =
                    if (appCount >= 2) {
                        activity.getString(
                            R.string.ctbs_app_other_apps,
                            appNames[0],
                            appCount.minus(1).toString()
                        ) + "  ❯"
                    } else {
                        appNames[0] + "  ❯"
                    }
                if (pkgName != null) {
                    appIcon = getIcon(activity, pkgName, info.appName)
                } else {
                    appIcon = ContextCompat.getDrawable(activity, R.drawable.default_app_icon)
                }
            }
        }
    }

    private fun displaySummaryDetails() {
        connectionMessageText =
            if (
                Logger.LoggerLevel.fromId(persistentState.goLoggerLevel.toInt())
                    .isLessThan(Logger.LoggerLevel.DEBUG)
            ) {
                "${info.proxyDetails}; ${info.rpid}; ${info.connId}; ${info.message}; ${info.synack}"
            } else {
                info.message
            }

        showConnectionMessage = connectionMessageText.isNotBlank()

        if (VpnController.hasCid(info.connId, info.uid)) {
            connDurationText =
                activity.getString(
                    R.string.two_argument_space,
                    activity.getString(R.string.lbl_active),
                    activity.getString(R.string.symbol_green_circle)
                )
        } else {
            connDurationText =
                activity.getString(
                    R.string.two_argument_space,
                    activity.getString(R.string.symbol_hyphen),
                    activity.getString(R.string.symbol_clock)
                )
        }

        val connType = ConnectionTracker.ConnType.get(info.connType)
        connTypeText =
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

        if (
            info.message.isEmpty() && info.duration == 0 && info.downloadBytes == 0L &&
                info.uploadBytes == 0L
        ) {
            showSummaryDetails = false
            showConnTypeSecondary = true
            connTypeSecondaryText = connTypeText
            return
        }

        showSummaryDetails = true
        showConnTypeSecondary = false
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
        connUploadText = uploadBytes
        connDownloadText = downloadBytes
        val duration = UIUtils.getDurationInHumanReadableFormat(activity, info.duration)
        connDurationText =
            activity.getString(R.string.two_argument_space, duration, activity.getString(R.string.symbol_clock))
    }

    private fun handleNonApp() {
        showFirewallRule = false
        showUnknownAppSwitch = true
        unknownAppChecked = persistentState.getBlockUnknownConnections()
        appName = info.appName
        appIcon = ContextCompat.getDrawable(activity, R.drawable.default_app_icon)
    }

    private fun openAppDetailActivity(uid: Int) {
        dialog.dismiss()
        val intent = Intent(activity, AppInfoActivity::class.java)
        intent.putExtra(AppInfoActivity.INTENT_UID, uid)
        activity.startActivity(intent)
    }

    private fun updateFirewallRulesUi(
        firewallStatus: FirewallManager.FirewallStatus,
        connStatus: FirewallManager.ConnectionStatus
    ) {
        if (firewallStatus.isUntracked()) return

        firewallSelection =
            when (firewallStatus) {
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
                else -> firewallSelection
            }
    }

    private fun updateIpRulesUi(uid: Int, ipAddress: String) {
        io {
            val rule = IpRulesManager.getMostSpecificRuleMatch(uid, ipAddress)
            uiCtx { ipRuleSelection = rule.id }
        }
    }

    private fun onFirewallSelected(position: Int) {
        val fStatus = FirewallManager.FirewallStatus.getStatusByLabel(position)
        val connStatus = FirewallManager.ConnectionStatus.getStatusByLabel(position)
        val uid = info.uid
        io {
            val a = FirewallManager.appStatus(uid)
            val c = FirewallManager.connectionStatus(uid)
            if (a == fStatus && c == connStatus) return@io

            if (VpnController.isVpnLockdown() && fStatus.isExclude()) {
                uiCtx {
                    updateFirewallRulesUi(a, c)
                    showToastUiCentered(
                        activity,
                        activity.getString(R.string.hsf_exclude_error),
                        Toast.LENGTH_LONG
                    )
                }
                return@io
            }

            if (FirewallManager.isUnknownPackage(uid) && fStatus.isExclude()) {
                uiCtx {
                    updateFirewallRulesUi(a, c)
                    showToastUiCentered(
                        activity,
                        activity.getString(R.string.exclude_no_package_err_toast),
                        Toast.LENGTH_LONG
                    )
                }
                return@io
            }

            Napier.i(
                "Change in firewall rule for app uid: ${info.uid}, firewall status: $fStatus, conn status: $connStatus"
            )
            applyFirewallRule(fStatus, connStatus)
        }
    }

    private fun applyIpRule(ipRuleStatus: IpRulesManager.IpRuleStatus) {
        io {
            if (IpRulesManager.getMostSpecificRuleMatch(info.uid, info.ipAddress) == ipRuleStatus) {
                return@io
            }
            val ipPair = IpRulesManager.getIpNetPort(info.ipAddress)
            val ip = ipPair.first ?: return@io
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
                "IP rule changed",
                "UID: ${info.uid}, IP: $ip, IpRuleStatus: ${ipRuleStatus.name}"
            )
        }
    }

    private fun applyDomainRule(domainRuleStatus: DomainRulesManager.Status) {
        val dnsQuery = info.dnsQuery ?: return
        Napier.i("Apply domain rule for $dnsQuery, ${domainRuleStatus.name}")
        io {
            DomainRulesManager.addDomainRule(
                dnsQuery,
                domainRuleStatus,
                DomainRulesManager.DomainType.DOMAIN,
                info.uid
            )
            logEvent(
                "Domain rule changed",
                "Domain: $dnsQuery, UID: ${info.uid}, DomainRuleStatus: ${domainRuleStatus.name}"
            )
        }
    }

    private fun showFirewallRulesDialog(blockedRule: String) {
        val iconRes = FirewallRuleset.getRulesIcon(blockedRule)
        var headingText: String
        var descText: Spanned

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
            descText = formatText(group)
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
                    descText = htmlToSpannedText(tempDesc)
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

        rulesDialogTitle = headingText
        rulesDialogDesc = descText
        rulesDialogIconRes = iconRes
        showRulesDialog = true
    }

    private fun formatText(groupNames: Multimap<String, String>): Spanned {
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

    private suspend fun applyFirewallRule(
        firewallStatus: FirewallManager.FirewallStatus,
        connStatus: FirewallManager.ConnectionStatus
    ) {
        val uid = info.uid
        uiCtx {
            io {
                FirewallManager.updateFirewallStatus(uid, firewallStatus, connStatus)
                logEvent(
                    "Firewall rule changed",
                    "UID: $uid, FirewallStatus: ${firewallStatus.name}, ConnectionStatus: ${connStatus.name}"
                )
            }
            updateFirewallRulesUi(firewallStatus, connStatus)
        }
    }

    private fun logEvent(msg: String, details: String) {
        eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, msg, EventSource.UI, false, details)
    }

    private fun io(f: suspend () -> Unit) = activity.lifecycleScope.launch(Dispatchers.IO) { f() }

    private suspend fun uiCtx(f: suspend () -> Unit) = withContext(Dispatchers.Main) { f() }

    @Composable
    private fun ConnTrackerContent() {
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
                            showFirewallRulesDialog(info.blockedByRule)
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
                        .clickable { openAppDetailActivity(info.uid) }
                        .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AndroidView(
                    factory = { context ->
                        androidx.appcompat.widget.AppCompatImageView(context).apply {
                            layoutParams =
                                android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                        }
                    },
                    update = { imageView ->
                        val icon = appIcon ?: ContextCompat.getDrawable(activity, R.drawable.default_app_icon)
                        imageView.setImageDrawable(icon)
                    },
                    modifier = Modifier.size(30.dp)
                )
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
                    onSelect = { onFirewallSelected(it) }
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
                    applyIpRule(IpRulesManager.IpRuleStatus.getStatus(it))
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
                        applyDomainRule(fid)
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
        AndroidView(
            modifier = modifier.fillMaxWidth(),
            factory = { context ->
                TextView(context).apply {
                    setTextColor(fetchColor(activity, R.attr.primaryTextColor))
                    val size = activity.resources.getDimension(R.dimen.large_font_text_view)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
                }
            },
            update = { textView ->
                textView.text = spanned
            }
        )
    }
}
