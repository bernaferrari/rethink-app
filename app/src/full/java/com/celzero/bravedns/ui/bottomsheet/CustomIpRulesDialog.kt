package com.celzero.bravedns.ui.bottomsheet

import Logger
import Logger.LOG_TAG_UI
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.celzero.bravedns.R
import com.celzero.bravedns.database.CustomIp
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.database.WgConfigFilesImmutable
import com.celzero.bravedns.databinding.BottomSheetCustomIpsBinding
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.IpRulesManager.IpRuleStatus
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.celzero.bravedns.util.Themes.Companion.getBottomsheetCurrentTheme
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.UIUtils.fetchToggleBtnColors
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CustomIpRulesDialog(
    private val activity: FragmentActivity,
    private var ci: CustomIp
) : KoinComponent,
    ProxyCountriesDialog.CountriesDismissListener,
    WireguardListDialog.WireguardDismissListener {
    private val b = BottomSheetCustomIpsBinding.inflate(LayoutInflater.from(activity))
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()
    private val eventLogger by inject<EventLogger>()

    companion object {
        private const val TAG = "CIRDialog"
    }

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
        dialog.setOnDismissListener {
            Logger.v(LOG_TAG_UI, "$TAG onDismiss; ip: ${ci.ipAddress}")
        }

        Logger.v(LOG_TAG_UI, "$TAG view created for ${ci.ipAddress}")
        init()
        initClickListeners()
    }

    fun show() {
        dialog.show()
    }

    private fun getThemeId(): Int {
        val isDark =
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return getBottomsheetCurrentTheme(isDark, persistentState.theme)
    }

    private fun init() {
        val uid = ci.uid
        io {
            if (uid == UID_EVERYBODY) {
                b.customIpAppNameTv.text =
                    activity.getString(R.string.firewall_act_universal_tab).replaceFirstChar(Char::titlecase)
                b.customIpAppIconIv.visibility = View.GONE
            } else {
                val appNames = FirewallManager.getAppNamesByUid(ci.uid)
                val appName = getAppName(ci.uid, appNames)
                val appInfo = FirewallManager.getAppInfoByUid(ci.uid)
                uiCtx {
                    b.customIpAppNameTv.text = appName
                    displayIcon(
                        Utilities.getIcon(
                            activity,
                            appInfo?.packageName ?: "",
                            appInfo?.appName ?: ""
                        ),
                        b.customIpAppIconIv
                    )
                }
            }
        }
        Logger.v(LOG_TAG_UI, "$TAG init for ${ci.ipAddress}, uid: $uid")
        val rules = IpRuleStatus.getStatus(ci.status)
        b.customIpTv.text = ci.ipAddress
        showBypassUi(uid)
        b.customIpToggleGroup.tag = 1
        updateToggleGroup(rules)
        updateStatusUi(rules, ci.modifiedDateTime)
    }

    private fun getAppName(uid: Int, appNames: List<String>): String {
        if (uid == UID_EVERYBODY) {
            return activity.getString(R.string.firewall_act_universal_tab)
                .replaceFirstChar(Char::titlecase)
        }

        if (appNames.isEmpty()) {
            return activity.getString(R.string.network_log_app_name_unknown) + " ($uid)"
        }

        val packageCount = appNames.count()
        return if (packageCount >= 2) {
            activity.getString(
                R.string.ctbs_app_other_apps,
                appNames[0],
                packageCount.minus(1).toString()
            )
        } else {
            appNames[0]
        }
    }

    private fun displayIcon(drawable: Drawable?, mIconImageView: ImageView) {
        Glide.with(activity)
            .load(drawable)
            .error(Utilities.getDefaultIcon(activity))
            .into(mIconImageView)
    }

    private fun showBypassUi(uid: Int) {
        if (uid == UID_EVERYBODY) {
            b.customIpTgBypassUniv.visibility = View.VISIBLE
            b.customIpTgBypassApp.visibility = View.GONE
        } else {
            b.customIpTgBypassUniv.visibility = View.GONE
            b.customIpTgBypassApp.visibility = View.VISIBLE
        }
    }

    private fun initClickListeners() {
        b.customIpToggleGroup.addOnButtonCheckedListener(ipRulesGroupListener)

        b.customIpDeleteChip.setOnClickListener {
            showDialogForDelete()
        }

        /* b.chooseProxyCard.setOnClickListener {
            val ctx = activity
            val v: MutableList<WgConfigFilesImmutable?> = mutableListOf()
            io {
                v.add(null)
                v.addAll(WireguardManager.getAllMappings())
                if (v.isEmpty() || v.size == 1) {
                    Logger.w(LOG_TAG_UI, "$TAG No Wireguard configs found")
                    uiCtx {
                        Utilities.showToastUiCentered(
                            ctx,
                            activity.getString(R.string.wireguard_no_config_msg),
                            Toast.LENGTH_SHORT
                        )
                    }
                    return@io
                }
                uiCtx {
                    Logger.v(LOG_TAG_UI, "$TAG show wg list(${v.size}) for ${ci.ipAddress}")
                    showWgListDialog(v)
                }
            }
        }

        b.chooseCountryCard.setOnClickListener {
            io {
                val ctrys = emptyList<String>()//RpnProxyManager.getProtonUniqueCC()
                if (ctrys.isEmpty()) {
                    Logger.w(LOG_TAG_UI, "$TAG No country codes found")
                    uiCtx {
                        Utilities.showToastUiCentered(
                            activity,
                            "No country codes found",
                            Toast.LENGTH_SHORT
                        )
                    }
                    return@io
                }
                uiCtx {
                    Logger.v(
                        LOG_TAG_UI,
                        "$TAG show country list(${ctrys.size}) for ${ci.ipAddress}"
                    )
                    showProxyCountriesDialog(ctrys)
                }
            }
        }*/
    }

    private val ipRulesGroupListener =
        MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
            val b: MaterialButton = b.customIpToggleGroup.findViewById(checkedId)
            val statusId = findSelectedIpRule(getTag(b.tag))
            if (statusId == null) {
                Logger.i(LOG_TAG_UI, "$TAG: statusId is null")
                return@OnButtonCheckedListener
            }

            if (isChecked) {
                val hasStatusChanged = ci.status != statusId.id
                if (hasStatusChanged) {
                    val t = getToggleBtnUiParams(statusId)
                    selectToggleBtnUi(b, t)

                    changeIpStatus(statusId, ci)
                }
            } else {
                unselectToggleBtnUi(b)
            }
        }

    private fun findSelectedIpRule(ruleId: Int): IpRulesManager.IpRuleStatus? {
        return when (ruleId) {
            IpRuleStatus.NONE.id -> {
                IpRuleStatus.NONE
            }
            IpRuleStatus.BLOCK.id -> {
                IpRuleStatus.BLOCK
            }
            IpRuleStatus.BYPASS_UNIVERSAL.id -> {
                IpRuleStatus.BYPASS_UNIVERSAL
            }
            IpRuleStatus.TRUST.id -> {
                IpRuleStatus.TRUST
            }
            else -> {
                null
            }
        }
    }

    private fun updateStatusUi(status: IpRuleStatus, modifiedTs: Long) {
        val now = System.currentTimeMillis()
        val uptime = System.currentTimeMillis() - modifiedTs
        val time =
            DateUtils.getRelativeTimeSpanString(
                now - uptime,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
        when (status) {
            IpRuleStatus.TRUST -> {
                b.customIpLastUpdated.text =
                    activity.getString(
                        R.string.ci_desc,
                        activity.getString(R.string.ci_trust_txt),
                        time
                    )
            }

            IpRuleStatus.BLOCK -> {
                b.customIpLastUpdated.text =
                    activity.getString(
                        R.string.ci_desc,
                        activity.getString(R.string.lbl_blocked),
                        time
                    )
            }

            IpRuleStatus.NONE -> {
                b.customIpLastUpdated.text =
                    activity.getString(
                        R.string.ci_desc,
                        activity.getString(R.string.cd_no_rule_txt),
                        time
                    )
            }
            IpRuleStatus.BYPASS_UNIVERSAL -> {
                b.customIpLastUpdated.text =
                    activity.getString(
                        R.string.ci_desc,
                        activity.getString(R.string.ci_bypass_universal_txt),
                        time
                    )
            }
        }
    }

    private fun changeIpStatus(id: IpRuleStatus, customIp: CustomIp) {
        io {
            val updated = when (id) {
                IpRuleStatus.NONE -> noRuleIp(customIp)
                IpRuleStatus.BLOCK -> blockIp(customIp)
                IpRuleStatus.BYPASS_UNIVERSAL -> byPassUniversal(customIp)
                IpRuleStatus.TRUST -> byPassAppRule(customIp)
            }
            ci = updated
            uiCtx {
                updateToggleGroup(id)
                updateStatusUi(id, ci.modifiedDateTime)
                Logger.v(LOG_TAG_UI, "$TAG changeIpStatus: ${ci.ipAddress}, status: ${id.name}")
            }
        }
    }

    private suspend fun byPassUniversal(orig: CustomIp): CustomIp {
        Logger.i(LOG_TAG_UI, "$TAG set ${orig.ipAddress} to bypass universal")
        val copy = orig.deepCopy()
        IpRulesManager.updateBypass(copy)
        logEvent("Set IP ${copy.ipAddress} to bypass universal")
        return copy
    }

    private suspend fun byPassAppRule(orig: CustomIp): CustomIp {
        Logger.i(LOG_TAG_UI, "$TAG set ${orig.ipAddress} to bypass app")
        val copy = orig.deepCopy()
        IpRulesManager.updateTrust(copy)
        logEvent("Set IP ${copy.ipAddress} to trust")
        return copy
    }

    private suspend fun blockIp(orig: CustomIp): CustomIp {
        Logger.i(LOG_TAG_UI, "$TAG block ${orig.ipAddress}")
        val copy = orig.deepCopy()
        IpRulesManager.updateBlock(copy)
        logEvent("Blocked IP ${copy.ipAddress}")
        return copy
    }

    private suspend fun noRuleIp(orig: CustomIp): CustomIp {
        Logger.i(LOG_TAG_UI, "$TAG no rule for ${orig.ipAddress}")
        val copy = orig.deepCopy()
        IpRulesManager.updateNoRule(copy)
        logEvent("Set no rule for IP ${copy.ipAddress}")
        return copy
    }

    private fun selectToggleBtnUi(btn: MaterialButton, toggleBtnUi: ToggleBtnUi) {
        btn.setTextColor(toggleBtnUi.txtColor)
        btn.backgroundTintList = ColorStateList.valueOf(toggleBtnUi.bgColor)
    }

    private fun unselectToggleBtnUi(btn: MaterialButton) {
        btn.setTextColor(fetchToggleBtnColors(activity, R.color.defaultToggleBtnTxt))
        btn.backgroundTintList =
            ColorStateList.valueOf(
                fetchToggleBtnColors(
                    activity,
                    R.color.defaultToggleBtnBg
                )
            )
    }

    data class ToggleBtnUi(val txtColor: Int, val bgColor: Int)

    private fun getToggleBtnUiParams(id: IpRuleStatus): ToggleBtnUi {
        return when (id) {
            IpRuleStatus.NONE -> {
                ToggleBtnUi(
                    fetchColor(activity, R.attr.chipTextNeutral),
                    fetchColor(activity, R.attr.chipBgColorNeutral)
                )
            }

            IpRuleStatus.BLOCK -> {
                ToggleBtnUi(
                    fetchColor(activity, R.attr.chipTextNegative),
                    fetchColor(activity, R.attr.chipBgColorNegative)
                )
            }

            IpRuleStatus.BYPASS_UNIVERSAL -> {
                ToggleBtnUi(
                    fetchColor(activity, R.attr.chipTextPositive),
                    fetchColor(activity, R.attr.chipBgColorPositive)
                )
            }

            IpRuleStatus.TRUST -> {
                ToggleBtnUi(
                    fetchColor(activity, R.attr.chipTextPositive),
                    fetchColor(activity, R.attr.chipBgColorPositive)
                )
            }
        }
    }

    private fun updateToggleGroup(id: IpRuleStatus) {
        val t = getToggleBtnUiParams(id)

        when (id) {
            IpRuleStatus.NONE -> {
                b.customIpToggleGroup.check(b.customIpTgNoRule.id)
                selectToggleBtnUi(b.customIpTgNoRule, t)
                unselectToggleBtnUi(b.customIpTgBlock)
                unselectToggleBtnUi(b.customIpTgBypassUniv)
                unselectToggleBtnUi(b.customIpTgBypassApp)
            }

            IpRuleStatus.BLOCK -> {
                b.customIpToggleGroup.check(b.customIpTgBlock.id)
                selectToggleBtnUi(b.customIpTgBlock, t)
                unselectToggleBtnUi(b.customIpTgNoRule)
                unselectToggleBtnUi(b.customIpTgBypassUniv)
                unselectToggleBtnUi(b.customIpTgBypassApp)
            }

            IpRuleStatus.BYPASS_UNIVERSAL -> {
                b.customIpToggleGroup.check(b.customIpTgBypassUniv.id)
                selectToggleBtnUi(b.customIpTgBypassUniv, t)
                unselectToggleBtnUi(b.customIpTgBlock)
                unselectToggleBtnUi(b.customIpTgNoRule)
                unselectToggleBtnUi(b.customIpTgBypassApp)
            }

            IpRuleStatus.TRUST -> {
                b.customIpToggleGroup.check(b.customIpTgBypassApp.id)
                selectToggleBtnUi(b.customIpTgBypassApp, t)
                unselectToggleBtnUi(b.customIpTgBlock)
                unselectToggleBtnUi(b.customIpTgNoRule)
                unselectToggleBtnUi(b.customIpTgBypassUniv)
            }
        }
    }

    private fun getTag(tag: Any): Int {
        return tag.toString().toIntOrNull() ?: 0
    }

    private fun showWgListDialog(data: List<WgConfigFilesImmutable?>) {
        WireguardListDialog(
            activity,
            WireguardListDialog.InputType.IP,
            ci,
            data,
            this
        ).show()
    }

    private fun showProxyCountriesDialog(data: List<String>) {
        Logger.v(LOG_TAG_UI, "$TAG show pcc dialog for ${ci.ipAddress}")
        ProxyCountriesDialog(
            activity,
            ProxyCountriesDialog.InputType.IP,
            ci,
            data,
            this
        ).show()
    }

    private fun showDialogForDelete() {
        val builder = MaterialAlertDialogBuilder(activity, R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.univ_firewall_dialog_title)
        builder.setMessage(R.string.univ_firewall_dialog_message)
        builder.setCancelable(true)
        builder.setPositiveButton(activity.getString(R.string.lbl_delete)) { _, _ ->
            io { IpRulesManager.removeIpRule(ci.uid, ci.ipAddress, ci.port) }
            Utilities.showToastUiCentered(
                activity,
                activity.getString(R.string.univ_ip_delete_individual_toast, ci.ipAddress),
                Toast.LENGTH_SHORT
            )
            dialog.dismiss()
            logEvent("Deleted custom IP rule for ${ci.ipAddress}")
        }

        builder.setNegativeButton(activity.getString(R.string.lbl_cancel)) { _, _ ->
            updateToggleGroup(IpRuleStatus.getStatus(ci.status))
        }

        builder.create().show()
    }

    override fun onDismissCC(obj: Any?) {
        try {
            val cip = obj as CustomIp
            this.ci = cip
            val status = IpRuleStatus.getStatus(cip.status)
            updateToggleGroup(status)
            updateStatusUi(status, cip.modifiedDateTime)
            Logger.v(LOG_TAG_UI, "$TAG onDismissCC: ${cip.ipAddress}, ${cip.proxyCC}")
        } catch (e: Exception) {
            Logger.w(LOG_TAG_UI, "$TAG err in onDismissCC ${e.message}", e)
        }
    }

    override fun onDismissWg(obj: Any?) {
        try {
            val cip = obj as CustomIp
            ci = cip
            val status = IpRuleStatus.getStatus(cip.status)
            updateToggleGroup(status)
            updateStatusUi(status, cip.modifiedDateTime)
            Logger.v(LOG_TAG_UI, "$TAG onDismissWg: ${cip.ipAddress}, ${cip.proxyCC}")
        } catch (e: Exception) {
            Logger.w(LOG_TAG_UI, "$TAG err in onDismissWg ${e.message}", e)
        }
    }

    private fun logEvent(details: String) {
        eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, "Custom IP", EventSource.UI, false, details)
    }

    private fun io(f: suspend () -> Unit) {
        activity.lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}
