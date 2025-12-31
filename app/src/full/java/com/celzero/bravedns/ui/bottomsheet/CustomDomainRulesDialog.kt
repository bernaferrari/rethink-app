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
import com.celzero.bravedns.database.CustomDomain
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.database.WgConfigFilesImmutable
import com.celzero.bravedns.databinding.BottomSheetCustomDomainsBinding
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
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

class CustomDomainRulesDialog(
    private val activity: FragmentActivity,
    private var cd: CustomDomain
) : KoinComponent,
    ProxyCountriesDialog.CountriesDismissListener,
    WireguardListDialog.WireguardDismissListener {
    private val b = BottomSheetCustomDomainsBinding.inflate(LayoutInflater.from(activity))
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()
    private val eventLogger by inject<EventLogger>()

    companion object {
        private const val TAG = "CDRDialog"
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
            Logger.v(LOG_TAG_UI, "$TAG onDismiss; domain: ${cd.domain}")
        }

        Logger.v(LOG_TAG_UI, "$TAG view created for ${cd.domain}")
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
        val uid = cd.uid
        io {
            if (uid == UID_EVERYBODY) {
                b.customDomainAppNameTv.text =
                    activity.getString(R.string.firewall_act_universal_tab).replaceFirstChar(Char::titlecase)
                b.customDomainAppIconIv.visibility = View.GONE
            } else {
                val appNames = FirewallManager.getAppNamesByUid(cd.uid)
                val appName = getAppName(cd.uid, appNames)
                val appInfo = FirewallManager.getAppInfoByUid(cd.uid)
                uiCtx {
                    b.customDomainAppNameTv.text = appName
                    displayIcon(
                        Utilities.getIcon(
                            activity,
                            appInfo?.packageName ?: "",
                            appInfo?.appName ?: ""
                        ),
                        b.customDomainAppIconIv
                    )
                }
            }
        }
        Logger.v(LOG_TAG_UI, "$TAG init for ${cd.domain}, uid: $uid")
        val rules = DomainRulesManager.getDomainRule(cd.domain, uid)
        b.customDomainTv.text = cd.domain
        updateStatusUi(
            DomainRulesManager.Status.getStatus(cd.status),
            cd.modifiedTs
        )
        b.customDomainToggleGroup.tag = 1
        updateToggleGroup(rules.id)
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

    private fun updateStatusUi(status: DomainRulesManager.Status, modifiedTs: Long) {
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
            DomainRulesManager.Status.TRUST -> {
                b.customDomainLastUpdated.text =
                    activity.getString(
                        R.string.ci_desc,
                        activity.getString(R.string.ci_trust_txt),
                        time
                    )
            }

            DomainRulesManager.Status.BLOCK -> {
                b.customDomainLastUpdated.text =
                    activity.getString(
                        R.string.ci_desc,
                        activity.getString(R.string.lbl_blocked),
                        time
                    )
            }

            DomainRulesManager.Status.NONE -> {
                b.customDomainLastUpdated.text =
                    activity.getString(
                        R.string.ci_desc,
                        activity.getString(R.string.cd_no_rule_txt),
                        time
                    )
            }
        }
    }

    private fun initClickListeners() {
        b.customDomainToggleGroup.addOnButtonCheckedListener(domainRulesGroupListener)

        b.customDomainDeleteChip.setOnClickListener {
            showDialogForDelete()
        }

        /*b.chooseProxyCard.setOnClickListener {
            val ctx = activity
            val v: MutableList<WgConfigFilesImmutable?> = mutableListOf()
            io {
                v.add(null)
                v.addAll(WireguardManager.getAllMappings())
                if (v.isEmpty()) {
                    Logger.v(LOG_TAG_UI, "$TAG no wireguard configs found")
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
                    Logger.v(LOG_TAG_UI, "$TAG show wg list(${v.size} for ${cd.domain}")
                    showWgListDialog(v)
                }
            }
        }

        b.chooseCountryCard.setOnClickListener {
            io {
                val ctrys = RpnProxyManager.getProtonUniqueCC()
                if (ctrys.isEmpty()) {
                    Logger.v(LOG_TAG_UI, "$TAG no country codes found")
                    uiCtx {
                        Utilities.showToastUiCentered(
                            activity,
                            "No ProtonVPN country codes found",
                            Toast.LENGTH_SHORT
                        )
                    }
                    return@io
                }
                uiCtx {
                    Logger.v(LOG_TAG_UI, "$TAG show countries(${ctrys.size} for ${cd.domain}")
                    showProxyCountriesDialog(ctrys)
                }
            }
        }*/
    }

    private val domainRulesGroupListener =
        MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
            val b: MaterialButton = b.customDomainToggleGroup.findViewById(checkedId)

            val statusId = findSelectedRuleByTag(getTag(b.tag))

            if (statusId == null) {
                return@OnButtonCheckedListener
            }

            if (isChecked) {
                val hasStatusChanged = cd.status != statusId.id
                if (!hasStatusChanged) {
                    return@OnButtonCheckedListener
                }
                val t = toggleBtnUi(statusId)
                selectToggleBtnUi(b, t)
                changeDomainStatus(statusId, cd)
            } else {
                unselectToggleBtnUi(b)
            }
        }

    private fun selectToggleBtnUi(b: MaterialButton, toggleBtnUi: ToggleBtnUi) {
        b.setTextColor(toggleBtnUi.txtColor)
        b.backgroundTintList = ColorStateList.valueOf(toggleBtnUi.bgColor)
    }

    private fun changeDomainStatus(id: DomainRulesManager.Status, cd: CustomDomain) {
        io {
            when (id) {
                DomainRulesManager.Status.NONE -> {
                    noRule(cd)
                }

                DomainRulesManager.Status.BLOCK -> {
                    block(cd)
                }

                DomainRulesManager.Status.TRUST -> {
                    whitelist(cd)
                }
            }
        }
    }

    private suspend fun whitelist(cd: CustomDomain) {
        DomainRulesManager.trust(cd)
        logEvent("Whitelisted custom domain rule for ${cd.domain}")
    }

    private suspend fun block(cd: CustomDomain) {
        DomainRulesManager.block(cd)
        logEvent("Blocked custom domain rule for ${cd.domain}")
    }

    private suspend fun noRule(cd: CustomDomain) {
        DomainRulesManager.noRule(cd)
        logEvent("Domain rule for ${cd.domain} is set to no rule")
    }

    private fun unselectToggleBtnUi(b: MaterialButton) {
        b.setTextColor(fetchToggleBtnColors(activity, R.color.defaultToggleBtnTxt))
        b.backgroundTintList =
            ColorStateList.valueOf(
                fetchToggleBtnColors(
                    activity,
                    R.color.defaultToggleBtnBg
                )
            )
    }

    data class ToggleBtnUi(val txtColor: Int, val bgColor: Int)

    private fun showDialogForDelete() {
        val builder = MaterialAlertDialogBuilder(activity, R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.cd_remove_dialog_title)
        builder.setMessage(R.string.cd_remove_dialog_message)
        builder.setCancelable(true)
        builder.setPositiveButton(activity.getString(R.string.lbl_delete)) { _, _ ->
            io { DomainRulesManager.deleteDomain(cd) }
            Utilities.showToastUiCentered(
                activity,
                activity.getString(R.string.cd_toast_deleted),
                Toast.LENGTH_SHORT
            )
            logEvent("Deleted custom domain rule for ${cd.domain}")
            dialog.dismiss()
        }

        builder.setNegativeButton(activity.getString(R.string.lbl_cancel)) { _, _ ->
            // no-op
        }
        builder.create().show()
    }

    private fun findSelectedRuleByTag(ruleId: Int): DomainRulesManager.Status? {
        return when (ruleId) {
            DomainRulesManager.Status.NONE.id -> {
                DomainRulesManager.Status.NONE
            }

            DomainRulesManager.Status.TRUST.id -> {
                DomainRulesManager.Status.TRUST
            }

            DomainRulesManager.Status.BLOCK.id -> {
                DomainRulesManager.Status.BLOCK
            }

            else -> {
                null
            }
        }
    }

    private fun toggleBtnUi(id: DomainRulesManager.Status): ToggleBtnUi {
        return when (id) {
            DomainRulesManager.Status.NONE -> {
                ToggleBtnUi(
                    fetchColor(activity, R.attr.chipTextNeutral),
                    fetchColor(activity, R.attr.chipBgColorNeutral)
                )
            }

            DomainRulesManager.Status.BLOCK -> {
                ToggleBtnUi(
                    fetchColor(activity, R.attr.chipTextNegative),
                    fetchColor(activity, R.attr.chipBgColorNegative)
                )
            }

            DomainRulesManager.Status.TRUST -> {
                ToggleBtnUi(
                    fetchColor(activity, R.attr.chipTextPositive),
                    fetchColor(activity, R.attr.chipBgColorPositive)
                )
            }
        }
    }

    private fun updateToggleGroup(id: Int) {
        val fid = findSelectedRuleByTag(id) ?: return

        val t = toggleBtnUi(fid)

        when (id) {
            DomainRulesManager.Status.NONE.id -> {
                b.customDomainToggleGroup.check(b.customDomainTgNoRule.id)
                selectToggleBtnUi(b.customDomainTgNoRule, t)
                unselectToggleBtnUi(b.customDomainTgBlock)
                unselectToggleBtnUi(b.customDomainTgWhitelist)
            }

            DomainRulesManager.Status.BLOCK.id -> {
                b.customDomainToggleGroup.check(b.customDomainTgBlock.id)
                selectToggleBtnUi(b.customDomainTgBlock, t)
                unselectToggleBtnUi(b.customDomainTgNoRule)
                unselectToggleBtnUi(b.customDomainTgWhitelist)
            }

            DomainRulesManager.Status.TRUST.id -> {
                b.customDomainToggleGroup.check(b.customDomainTgWhitelist.id)
                selectToggleBtnUi(b.customDomainTgWhitelist, t)
                unselectToggleBtnUi(b.customDomainTgBlock)
                unselectToggleBtnUi(b.customDomainTgNoRule)
            }
        }
    }

    private fun getTag(tag: Any): Int {
        return tag.toString().toIntOrNull() ?: 0
    }

    private fun showWgListDialog(data: List<WgConfigFilesImmutable?>) {
        Logger.v(LOG_TAG_UI, "$TAG show wg list(${data.size} for ${cd.domain}")
        WireguardListDialog(
            activity,
            WireguardListDialog.InputType.DOMAIN,
            cd,
            data,
            this
        ).show()
    }

    private fun showProxyCountriesDialog(data: List<String>) {
        Logger.v(LOG_TAG_UI, "$TAG show countries(${data.size} for ${cd.domain}")
        ProxyCountriesDialog(
            activity,
            ProxyCountriesDialog.InputType.DOMAIN,
            cd,
            data,
            this
        ).show()
    }

    private fun io(f: suspend () -> Unit) {
        activity.lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    override fun onDismissCC(obj: Any?) {
        try {
            val customDomain = obj as CustomDomain
            cd = customDomain
            updateStatusUi(
                DomainRulesManager.Status.getStatus(cd.status),
                cd.modifiedTs
            )
            Logger.i(LOG_TAG_UI, "$TAG onDismissCC: ${cd.domain}, ${cd.proxyCC}")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG err in onDismissCC ${e.message}", e)
        }
    }

    override fun onDismissWg(obj: Any?) {
        try {
            val customDomain = obj as CustomDomain
            cd = customDomain
            updateStatusUi(
                DomainRulesManager.Status.getStatus(cd.status),
                cd.modifiedTs
            )
            Logger.i(LOG_TAG_UI, "$TAG onDismissWg: ${cd.domain}, ${cd.proxyId}")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG err in onDismissWg ${e.message}", e)
        }
    }

    private fun logEvent(details: String) {
        eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, "Custom Domain", EventSource.UI, false, details)
    }
}
