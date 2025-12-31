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
import Logger.LOG_TAG_FIREWALL
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.Spanned
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.FirewallStatusSpinnerAdapter
import com.celzero.bravedns.database.ConnectionTracker
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.databinding.BottomSheetConnTrackBinding
import com.celzero.bravedns.databinding.DialogInfoRulesLayoutBinding
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
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
    private val b = BottomSheetConnTrackBinding.inflate(LayoutInflater.from(activity))
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()
    private val eventLogger by inject<EventLogger>()

    init {
        dialog.setContentView(b.root)
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
        b.bsConnConnectionTypeHeading.text = info.ipAddress.orEmpty()
        b.bsConnConnectionFlag.text = info.flag.orEmpty()

        b.bsConnBlockAppTxt.text = htmlToSpannedText(activity.getString(R.string.bsct_block))
        b.bsConnBlockConnAllTxt.text = htmlToSpannedText(activity.getString(R.string.bsct_block_ip))
        b.bsConnDomainTxt.text = htmlToSpannedText(activity.getString(R.string.bsct_block_domain))

        // updates the application name and other details
        updateAppDetails()
        // updates the connection detail chip
        updateConnDetailsChip()
        // updates the blocked rules chip
        updateBlockedRulesChip()
        // assigns color for the blocked rules chip
        lightenUpChip()
        // updates the summary details
        displaySummaryDetails()
        // setup click and item selected listeners
        setupClickListeners()
        // updates the ip rules button
        info.let { updateIpRulesUi(it.uid, it.ipAddress) }
        // updates the value from dns request cache if available
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

        if (domain.isNullOrEmpty() || uid == null) {
            b.bsConnDnsCacheText.visibility = View.VISIBLE
            b.bsConnDnsCacheText.text = UIUtils.getCountryNameFromFlag(flag)
            b.bsConnDomainRuleLl.visibility = View.GONE
            return
        }

        val status = DomainRulesManager.getDomainRule(domain, uid)
        b.bsConnDomainSpinner.setSelection(status.id)
        b.bsConnDnsCacheText.visibility = View.VISIBLE
        b.bsConnDnsCacheText.text =
            activity.getString(R.string.two_argument, UIUtils.getCountryNameFromFlag(flag), domain)
    }

    private fun updateConnDetailsChip() {
        val currentInfo = info
        if (currentInfo == null) {
            Logger.w(LOG_TAG_FIREWALL, "ip-details missing: not updating the chip details")
            return
        }

        val protocol = Protocol.getProtocolName(currentInfo.protocol).name
        val time =
            DateUtils.getRelativeTimeSpanString(
                currentInfo.timeStamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
        val protocolDetails = "$protocol/${currentInfo.port}"
        if (currentInfo.isBlocked) {
            b.bsConnTrackPortDetailChip.text =
                activity.getString(R.string.bsct_conn_desc_blocked, protocolDetails, time)
            return
        }

        b.bsConnTrackPortDetailChip.text =
            activity.getString(R.string.bsct_conn_desc_allowed, protocolDetails, time)
    }

    private fun updateBlockedRulesChip() {
        if (info.blockedByRule.isNullOrBlank()) {
            b.bsConnTrackAppInfo.text = activity.getString(R.string.firewall_rule_no_rule)
            return
        }

        val rule = info.blockedByRule ?: return
        val isIpnProxy = isNotLocalAndRpnProxy(info.proxyDetails ?: "")
        // TODO: below code is not required, remove it in future (20/03/2023)
        if (rule.contains(FirewallRuleset.RULE2G.id)) {
            b.bsConnTrackAppInfo.text =
                getFirewallRule(FirewallRuleset.RULE2G.id)?.title?.let { activity.getString(it) }
            return
        } else if (!info.proxyDetails.isNullOrEmpty() && isIpnProxy) {
            // add the proxy id to the chip text if available
            b.bsConnTrackAppInfo.text = activity.getString(R.string.two_argument_colon, activity.getString(FirewallRuleset.RULE12.title), info.proxyDetails)
        } else {
            if (isInvalidProxyDetails()) {
                b.bsConnTrackAppInfo.text = activity.getString(getFirewallRule(FirewallRuleset.RULE18.id)?.title ?: R.string.firewall_rule_no_rule)
            } else {
                b.bsConnTrackAppInfo.text = getFirewallRule(rule)?.title?.let { activity.getString(it) }
            }
        }
    }

    private fun isInvalidProxyDetails(): Boolean {
        val isIpnProxy = isNotLocalAndRpnProxy(info.proxyDetails ?: "")
        val rule = info.blockedByRule ?: return false
        val isRuleAddedAsProxy = getFirewallRule(rule)?.id == FirewallRuleset.RULE12.id
        if (isRuleAddedAsProxy && (info.proxyDetails.isNullOrEmpty() || !isIpnProxy)) {
            // when the conn is marked as proxied with id from flow, but the returned summary
            // doesn't have the proxy details. change the rule from proxied to error (RULE1C)
            return true
        }
        return false
    }

    private fun updateAppDetails() {
        val currentInfo = info ?: return

        io {
            val appNames = FirewallManager.getAppNamesByUid(currentInfo.uid)
            if (appNames.isEmpty()) {
                uiCtx { handleNonApp() }
                return@io
            }
            val pkgName = FirewallManager.getPackageNameByAppName(appNames[0])

            val appCount = appNames.count()
            uiCtx {
                if (appCount >= 1) {
                    b.bsConnBlockedRule2HeaderLl.visibility = View.GONE
                    b.bsConnTrackAppName.text =
                        if (appCount >= 2) {
                            activity.getString(
                                R.string.ctbs_app_other_apps,
                                appNames[0],
                                appCount.minus(1).toString()
                            ) + "      ❯"
                        } else {
                            appNames[0] + "      ❯"
                        }
                    if (pkgName == null) return@uiCtx
                    b.bsConnTrackAppIcon.setImageDrawable(
                        getIcon(activity, pkgName, info.appName)
                    )
                } else {
                    // apps which are not available in cache are treated as non app.
                    // TODO: check packageManager#getApplicationInfo() for appInfo
                    handleNonApp()
                }
            }
        }
    }

    private fun displaySummaryDetails() {
        b.bsConnConnTypeSecondary.visibility = View.GONE
        // show connId and message if the log level is less than DEBUG
        if (Logger.LoggerLevel.fromId(persistentState.goLoggerLevel.toInt())
                .isLessThan(Logger.LoggerLevel.DEBUG)
        ) {
            b.connectionMessage.text = "${info.proxyDetails}; ${info.rpid}; ${info.connId}; ${info.message}; ${info.synack}"
        } else {
            b.connectionMessage.text = info.message
        }

        val currentInfo = info
        if (currentInfo != null && VpnController.hasCid(currentInfo.connId, currentInfo.uid)) {
            b.bsConnConnDuration.text =
                activity.getString(
                    R.string.two_argument_space,
                    activity.getString(R.string.lbl_active),
                    activity.getString(R.string.symbol_green_circle)
                )
        } else {
            b.bsConnConnDuration.text =
                activity.getString(
                    R.string.two_argument_space,
                    activity.getString(R.string.symbol_hyphen),
                    activity.getString(R.string.symbol_clock)
                )
        }

        val connType = ConnectionTracker.ConnType.get(info.connType)
        if (connType.isMetered()) {
            b.bsConnConnType.text =
                activity.getString(
                    R.string.two_argument_space,
                    activity.getString(R.string.ada_app_metered),
                    activity.getString(R.string.symbol_currency)
                )
        } else {
            b.bsConnConnType.text =
                activity.getString(
                    R.string.two_argument_space,
                    activity.getString(R.string.ada_app_unmetered),
                    activity.getString(R.string.symbol_global)
                )
        }

        if (
            info.message?.isEmpty() == true &&
                info.duration == 0 &&
                info.downloadBytes == 0L &&
                info.uploadBytes == 0L
        ) {
            b.bsConnSummaryDetailLl.visibility = View.GONE
            b.bsConnConnTypeSecondary.visibility = View.VISIBLE
            b.bsConnConnTypeSecondary.text = b.bsConnConnType.text
            return
        }

        b.connectionMessageLl.visibility = View.VISIBLE
        val downloadBytes =
            activity.getString(
                R.string.symbol_download,
                Utilities.humanReadableByteCount(info.downloadBytes ?: 0L, true)
            )
        val uploadBytes =
            activity.getString(
                R.string.symbol_upload,
                Utilities.humanReadableByteCount(info.uploadBytes ?: 0L, true)
            )

        b.bsConnConnUpload.text = uploadBytes
        b.bsConnConnDownload.text = downloadBytes
        val duration = UIUtils.getDurationInHumanReadableFormat(activity, info.duration ?: 0)
        b.bsConnConnDuration.text =
            activity.getString(R.string.two_argument_space, duration, activity.getString(R.string.symbol_clock))
    }

    private fun lightenUpChip() {
        // Load icons for the firewall rules if available
        b.bsConnTrackAppInfo.chipIcon =
            ContextCompat.getDrawable(
                activity,
                FirewallRuleset.getRulesIcon(info.blockedByRule)
            )
        if (info.isBlocked == true || isInvalidProxyDetails()) {
            b.bsConnTrackAppInfo.setTextColor(fetchColor(activity, R.attr.chipTextNegative))
            val colorFilter =
                PorterDuffColorFilter(
                    fetchColor(activity, R.attr.chipTextNegative),
                    PorterDuff.Mode.SRC_IN
                )
            b.bsConnTrackAppInfo.chipBackgroundColor =
                ColorStateList.valueOf(fetchColor(activity, R.attr.chipBgColorNegative))
            b.bsConnTrackAppInfo.chipIcon?.colorFilter = colorFilter
        } else {
            b.bsConnTrackAppInfo.setTextColor(fetchColor(activity, R.attr.chipTextPositive))
            val colorFilter =
                PorterDuffColorFilter(
                    fetchColor(activity, R.attr.chipTextPositive),
                    PorterDuff.Mode.SRC_IN
                )
            b.bsConnTrackAppInfo.chipBackgroundColor =
                ColorStateList.valueOf(fetchColor(activity, R.attr.chipBgColorPositive))
            b.bsConnTrackAppInfo.chipIcon?.colorFilter = colorFilter
        }
    }

    private fun handleNonApp() {
        // show universal setting layout
        b.bsConnBlockedRule2HeaderLl.visibility = View.VISIBLE
        // hide the app firewall layout
        b.bsConnBlockedRule1HeaderLl.visibility = View.GONE
        b.bsConnUnknownAppCheck.isChecked = persistentState.getBlockUnknownConnections()
        b.bsConnTrackAppName.text = info.appName.orEmpty()
    }

    private fun setupClickListeners() {
        b.bsConnUnknownAppCheck.setOnCheckedChangeListener(null)
        b.bsConnUnknownAppCheck.setOnClickListener {
            Logger.d(
                LOG_TAG_FIREWALL,
                "Unknown app, universal firewall settings(block unknown app): ${b.bsConnUnknownAppCheck.isChecked} "
            )
            persistentState.setBlockUnknownConnections(b.bsConnUnknownAppCheck.isChecked)
            logEvent("Universal firewall setting changed", "Block unknown apps: ${b.bsConnUnknownAppCheck.isChecked}")
        }

        b.bsConnTrackAppInfo.setOnClickListener { showFirewallRulesDialog(info.blockedByRule) }

        b.bsConnTrackAppNameHeader.setOnClickListener {
            val uid = info.uid
            io {
                val ai = FirewallManager.getAppInfoByUid(uid)
                uiCtx {
                    // case: app is uninstalled but still available in RethinkDNS database
                    if (ai == null || uid == Constants.INVALID_UID) {
                        showToastUiCentered(
                            activity,
                            activity.getString(R.string.ct_bs_app_info_error),
                            Toast.LENGTH_SHORT
                        )
                        return@uiCtx
                    }
                    openAppDetailActivity(uid)
                }
            }
        }

        // spinner to show firewall rules
        b.bsConnFirewallSpinner.adapter =
            FirewallStatusSpinnerAdapter(
                activity,
                FirewallManager.getLabel(activity)
            )
        b.bsConnFirewallSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val iv = view?.findViewById<AppCompatImageView>(R.id.spinner_icon)
                    iv?.visibility = View.VISIBLE
                    val fStatus = FirewallManager.FirewallStatus.getStatusByLabel(position)
                    val connStatus = FirewallManager.ConnectionStatus.getStatusByLabel(position)

                    // no change, prev selection and current selection are same
                    val uid = info.uid
                    io {
                        val a = FirewallManager.appStatus(uid)
                        val c = FirewallManager.connectionStatus(uid)

                        if (a == fStatus && c == connStatus) return@io

                        if (VpnController.isVpnLockdown() && fStatus.isExclude()) {
                            uiCtx {
                                // reset the spinner to previous selection
                                updateFirewallRulesUi(a, c)
                                showToastUiCentered(
                                    activity,
                                    activity.getString(R.string.hsf_exclude_error),
                                    Toast.LENGTH_LONG
                                )
                            }
                            return@io
                        }

                        // TODO: instead disable/remove exclude from the view if pkg is unknown?
                        if (FirewallManager.isUnknownPackage(uid) && fStatus.isExclude()) {
                            uiCtx {
                                // reset the spinner to previous selection
                                updateFirewallRulesUi(a, c)
                                showToastUiCentered(
                                    activity,
                                    activity.getString(R.string.exclude_no_package_err_toast),
                                    Toast.LENGTH_LONG
                                )
                            }
                            return@io
                        }

                        Logger.i(
                            LOG_TAG_FIREWALL,
                            "Change in firewall rule for app uid: ${info.uid}, firewall status: $fStatus, conn status: $connStatus"
                        )
                        applyFirewallRule(fStatus, connStatus)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        b.bsConnIpRuleSpinner.adapter =
            FirewallStatusSpinnerAdapter(
                activity,
                IpRulesManager.IpRuleStatus.getLabel(activity)
            )
        b.bsConnIpRuleSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val iv = view?.findViewById<AppCompatImageView>(R.id.spinner_icon)
                    iv?.visibility = View.VISIBLE
                    val fid = IpRulesManager.IpRuleStatus.getStatus(position)

                    applyIpRule(fid)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        b.bsConnDomainSpinner.adapter =
            FirewallStatusSpinnerAdapter(
                activity,
                DomainRulesManager.Status.getLabel(activity)
            )
        b.bsConnDomainSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val dnsQuery = info.dnsQuery
                    val uid = info.uid
                    if (dnsQuery == null) {
                        Logger.w(LOG_TAG_FIREWALL, "DNS query is null, cannot apply domain rule")
                        return
                    }

                    val iv = view?.findViewById<AppCompatImageView>(R.id.spinner_icon)
                    iv?.visibility = View.VISIBLE
                    val fid = DomainRulesManager.Status.getStatus(position)

                    // no need to apply rule, prev selection and current selection are same
                    if (uid != null && DomainRulesManager.getDomainRule(dnsQuery, uid) == fid) {
                        return
                    }

                    applyDomainRule(fid)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
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
        // no need to update the state if it's untracked
        if (firewallStatus.isUntracked()) return

        when (firewallStatus) {
            FirewallManager.FirewallStatus.NONE -> {
                when (connStatus) {
                    FirewallManager.ConnectionStatus.ALLOW -> {
                        b.bsConnFirewallSpinner.setSelection(0, true)
                    }
                    FirewallManager.ConnectionStatus.BOTH -> {
                        b.bsConnFirewallSpinner.setSelection(1, true)
                    }
                    FirewallManager.ConnectionStatus.UNMETERED -> {
                        b.bsConnFirewallSpinner.setSelection(2, true)
                    }
                    FirewallManager.ConnectionStatus.METERED -> {
                        b.bsConnFirewallSpinner.setSelection(3, true)
                    }
                }
            }
            FirewallManager.FirewallStatus.ISOLATE -> {
                b.bsConnFirewallSpinner.setSelection(4, true)
            }
            FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL -> {
                b.bsConnFirewallSpinner.setSelection(5, true)
            }
            FirewallManager.FirewallStatus.BYPASS_UNIVERSAL -> {
                b.bsConnFirewallSpinner.setSelection(6, true)
            }
            FirewallManager.FirewallStatus.EXCLUDE -> {
                b.bsConnFirewallSpinner.setSelection(7, true)
            }
            else -> {
                // no-op
            }
        }
    }

    private fun updateIpRulesUi(uid: Int, ipAddress: String) {
        io {
            val rule = IpRulesManager.getMostSpecificRuleMatch(uid, ipAddress)
            uiCtx { b.bsConnIpRuleSpinner.setSelection(rule.id) }
        }
    }

    private fun showFirewallRulesDialog(blockedRule: String?) {
        if (blockedRule == null) return

        val dialogBinding = DialogInfoRulesLayoutBinding.inflate(activity.layoutInflater)
        val builder = MaterialAlertDialogBuilder(activity, R.style.App_Dialog_NoDim).setView(dialogBinding.root)
        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        dialog.show()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(true)
        dialog.window?.attributes = lp

        val heading = dialogBinding.infoRulesDialogRulesTitle
        val okBtn = dialogBinding.infoRulesDialogCancelImg
        val desc = dialogBinding.infoRulesDialogRulesDesc
        val icon = dialogBinding.infoRulesDialogRulesIcon
        icon.visibility = View.VISIBLE
        val headingText: String
        var descText: Spanned

        if (blockedRule.contains(FirewallRuleset.RULE2G.id)) {
            val group: Multimap<String, String> = HashMultimap.create()

            val blocklists =
                if (info.blocklists?.isEmpty() == true) {
                    val startIndex = blockedRule.indexOfFirst { it == '|' }
                    blockedRule.substring(startIndex + 1).split(",")
                } else {
                    info.blocklists?.split(",") ?: listOf()
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

        desc.text = descText
        heading.text = headingText
        icon.setImageDrawable(
            ContextCompat.getDrawable(activity, FirewallRuleset.getRulesIcon(blockedRule))
        )

        okBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
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
                logEvent("Firewall rule changed", "UID: $uid, FirewallStatus: ${firewallStatus.name}, ConnectionStatus: ${connStatus.name}")
            }
            updateFirewallRulesUi(firewallStatus, connStatus)
        }
    }

    private fun applyIpRule(ipRuleStatus: IpRulesManager.IpRuleStatus) {
        val currentInfo = info ?: return
        io {
            // no need to apply rule, prev selection and current selection are same
            if (
                IpRulesManager.getMostSpecificRuleMatch(currentInfo.uid, currentInfo.ipAddress) ==
                    ipRuleStatus
            )
                return@io

            val ipPair = IpRulesManager.getIpNetPort(currentInfo.ipAddress)
            val ip = ipPair.first ?: return@io
            IpRulesManager.addIpRule(currentInfo.uid, ip, /*wildcard-port*/ 0, ipRuleStatus, proxyId = "", proxyCC = "")
            Logger.i(LOG_TAG_FIREWALL, "apply ip-rule for ${currentInfo.uid}, $ip, ${ipRuleStatus.name}")
            logEvent("IP rule changed", "UID: ${currentInfo.uid}, IP: $ip, IpRuleStatus: ${ipRuleStatus.name}")
        }
    }

    private fun applyDomainRule(domainRuleStatus: DomainRulesManager.Status) {
        val currentInfo = info ?: return
        val dnsQuery = currentInfo.dnsQuery ?: return
        Logger.i(
            LOG_TAG_FIREWALL,
            "Apply domain rule for $dnsQuery, ${domainRuleStatus.name}"
        )
        io {
            DomainRulesManager.addDomainRule(
                dnsQuery,
                domainRuleStatus,
                DomainRulesManager.DomainType.DOMAIN,
                currentInfo.uid,
            )
            logEvent("Domain rule changed", "Domain: $dnsQuery, UID: ${currentInfo.uid}, DomainRuleStatus: ${domainRuleStatus.name}")
        }
    }

    private fun logEvent(msg: String, details: String) {
        eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, msg, EventSource.UI, false, details)
    }

    private fun io(f: suspend () -> Unit) = activity.lifecycleScope.launch(Dispatchers.IO) { f() }

    private suspend fun uiCtx(f: suspend () -> Unit) = withContext(Dispatchers.Main) { f() }
}
