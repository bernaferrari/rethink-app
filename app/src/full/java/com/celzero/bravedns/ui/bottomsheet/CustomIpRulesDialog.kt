package com.celzero.bravedns.ui.bottomsheet

import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.database.CustomIp
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.IpRulesManager.IpRuleStatus
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.celzero.bravedns.util.Themes.Companion.getBottomsheetCurrentTheme
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.aakira.napier.Napier
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
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()
    private val eventLogger by inject<EventLogger>()

    private var appName by mutableStateOf("")
    private var appIcon by mutableStateOf<Drawable?>(null)
    private var status by mutableStateOf(IpRuleStatus.getStatus(ci.status))
    private var statusText by mutableStateOf("")
    private var showDeleteDialog by mutableStateOf(false)

    companion object {
        private const val TAG = "CIRDialog"
    }

    init {
        val composeView = ComposeView(activity)
        composeView.setContent {
            RethinkTheme {
                CustomIpRulesContent()
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
        dialog.setOnDismissListener {
            Napier.v("$TAG onDismiss; ip: ${ci.ipAddress}")
        }

        Napier.v("$TAG view created for ${ci.ipAddress}")
        initData()
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

    private fun initData() {
        val uid = ci.uid
        io {
            if (uid == UID_EVERYBODY) {
                uiCtx {
                    appName =
                        activity.getString(R.string.firewall_act_universal_tab)
                            .replaceFirstChar(Char::titlecase)
                    appIcon = null
                }
            } else {
                val appNames = FirewallManager.getAppNamesByUid(ci.uid)
                val name = getAppName(ci.uid, appNames)
                val appInfo = FirewallManager.getAppInfoByUid(ci.uid)
                uiCtx {
                    appName = name
                    appIcon =
                        Utilities.getIcon(
                            activity,
                            appInfo?.packageName ?: "",
                            appInfo?.appName ?: ""
                        )
                }
            }
        }
        status = IpRuleStatus.getStatus(ci.status)
        statusText = buildStatusText(status, ci.modifiedDateTime)
    }

    @Composable
    private fun CustomIpRulesContent() {
        val borderColor = Color(UIUtils.fetchColor(activity, R.attr.border))
        val neutralText = Color(UIUtils.fetchColor(activity, R.attr.chipTextNeutral))
        val neutralBg = Color(UIUtils.fetchColor(activity, R.attr.chipBgColorNeutral))
        val negativeText = Color(UIUtils.fetchColor(activity, R.attr.chipTextNegative))
        val negativeBg = Color(UIUtils.fetchColor(activity, R.attr.chipBgColorNegative))
        val positiveText = Color(UIUtils.fetchColor(activity, R.attr.chipTextPositive))
        val positiveBg = Color(UIUtils.fetchColor(activity, R.attr.chipBgColorPositive))

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(60.dp)
                        .height(3.dp)
                        .background(borderColor, RoundedCornerShape(2.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                val deleteText = Color(UIUtils.fetchColor(activity, R.attr.chipTextNegative))
                TextButton(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = deleteText)
                ) {
                    Text(text = activity.getString(R.string.lbl_delete))
                }
            }

            if (appName.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    appIcon?.let { icon ->
                        AndroidView(
                            factory = { ctx -> androidx.appcompat.widget.AppCompatImageView(ctx) },
                            update = { view -> view.setImageDrawable(icon) },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = ci.ipAddress,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                )
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RuleChip(
                    label = activity.getString(R.string.ci_no_rule),
                    selected = status == IpRuleStatus.NONE,
                    selectedText = neutralText,
                    selectedContainer = neutralBg
                ) {
                    updateRule(IpRuleStatus.NONE)
                }
                RuleChip(
                    label = activity.getString(R.string.ci_block),
                    selected = status == IpRuleStatus.BLOCK,
                    selectedText = negativeText,
                    selectedContainer = negativeBg
                ) {
                    updateRule(IpRuleStatus.BLOCK)
                }
                if (ci.uid == UID_EVERYBODY) {
                    RuleChip(
                        label = activity.getString(R.string.ci_bypass_universal),
                        selected = status == IpRuleStatus.BYPASS_UNIVERSAL,
                        selectedText = positiveText,
                        selectedContainer = positiveBg
                    ) {
                        updateRule(IpRuleStatus.BYPASS_UNIVERSAL)
                    }
                } else {
                    RuleChip(
                        label = activity.getString(R.string.ci_trust_rule),
                        selected = status == IpRuleStatus.TRUST,
                        selectedText = positiveText,
                        selectedContainer = positiveBg
                    ) {
                        updateRule(IpRuleStatus.TRUST)
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(text = activity.getString(R.string.univ_firewall_dialog_title)) },
                text = { Text(text = activity.getString(R.string.univ_firewall_dialog_message)) },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            io { IpRulesManager.removeIpRule(ci.uid, ci.ipAddress, ci.port) }
                            Utilities.showToastUiCentered(
                                activity,
                                activity.getString(
                                    R.string.univ_ip_delete_individual_toast,
                                    ci.ipAddress
                                ),
                                Toast.LENGTH_SHORT
                            )
                            dialog.dismiss()
                            logEvent("Deleted custom IP rule for ${ci.ipAddress}")
                        }
                    ) {
                        Text(text = activity.getString(R.string.lbl_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(text = activity.getString(R.string.lbl_cancel))
                    }
                }
            )
        }
    }

    @Composable
    private fun RuleChip(
        label: String,
        selected: Boolean,
        selectedText: Color,
        selectedContainer: Color,
        onClick: () -> Unit
    ) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(text = label) },
            colors =
                androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedLabelColor = selectedText,
                    selectedContainerColor = selectedContainer
                )
        )
    }

    private fun updateRule(rule: IpRuleStatus) {
        if (rule == status) return
        io {
            val updated =
                when (rule) {
                    IpRuleStatus.NONE -> noRuleIp(ci)
                    IpRuleStatus.BLOCK -> blockIp(ci)
                    IpRuleStatus.BYPASS_UNIVERSAL -> byPassUniversal(ci)
                    IpRuleStatus.TRUST -> byPassAppRule(ci)
                }
            ci = updated
            status = rule
            statusText = buildStatusText(rule, ci.modifiedDateTime)
            Napier.v("$TAG changeIpStatus: ${ci.ipAddress}, status: ${rule.name}")
        }
    }

    private fun buildStatusText(status: IpRuleStatus, modifiedTs: Long): String {
        val now = System.currentTimeMillis()
        val uptime = System.currentTimeMillis() - modifiedTs
        val time =
            DateUtils.getRelativeTimeSpanString(
                now - uptime,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
        val label =
            when (status) {
                IpRuleStatus.TRUST -> activity.getString(R.string.ci_trust_txt)
                IpRuleStatus.BLOCK -> activity.getString(R.string.lbl_blocked)
                IpRuleStatus.NONE -> activity.getString(R.string.cd_no_rule_txt)
                IpRuleStatus.BYPASS_UNIVERSAL ->
                    activity.getString(R.string.ci_bypass_universal_txt)
            }
        return activity.getString(R.string.ci_desc, label, time)
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

    private suspend fun byPassUniversal(orig: CustomIp): CustomIp {
        Napier.i("$TAG set ${orig.ipAddress} to bypass universal")
        val copy = orig.deepCopy()
        IpRulesManager.updateBypass(copy)
        logEvent("Set IP ${copy.ipAddress} to bypass universal")
        return copy
    }

    private suspend fun byPassAppRule(orig: CustomIp): CustomIp {
        Napier.i("$TAG set ${orig.ipAddress} to bypass app")
        val copy = orig.deepCopy()
        IpRulesManager.updateTrust(copy)
        logEvent("Set IP ${copy.ipAddress} to trust")
        return copy
    }

    private suspend fun blockIp(orig: CustomIp): CustomIp {
        Napier.i("$TAG block ${orig.ipAddress}")
        val copy = orig.deepCopy()
        IpRulesManager.updateBlock(copy)
        logEvent("Blocked IP ${copy.ipAddress}")
        return copy
    }

    private suspend fun noRuleIp(orig: CustomIp): CustomIp {
        Napier.i("$TAG no rule for ${orig.ipAddress}")
        val copy = orig.deepCopy()
        IpRulesManager.updateNoRule(copy)
        logEvent("Set no rule for IP ${copy.ipAddress}")
        return copy
    }

    private fun logEvent(details: String) {
        eventLogger.log(
            EventType.FW_RULE_MODIFIED,
            Severity.LOW,
            "Custom IP",
            EventSource.UI,
            false,
            details
        )
    }

    private fun io(f: suspend () -> Unit) {
        activity.lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    override fun onDismissCC(obj: Any?) {
        try {
            val cip = obj as CustomIp
            ci = cip
            status = IpRuleStatus.getStatus(cip.status)
            statusText = buildStatusText(status, cip.modifiedDateTime)
            Napier.v("$TAG onDismissCC: ${cip.ipAddress}, ${cip.proxyCC}")
        } catch (e: Exception) {
            Napier.w("$TAG err in onDismissCC ${e.message}", e)
        }
    }

    override fun onDismissWg(obj: Any?) {
        try {
            val cip = obj as CustomIp
            ci = cip
            status = IpRuleStatus.getStatus(cip.status)
            statusText = buildStatusText(status, cip.modifiedDateTime)
            Napier.v("$TAG onDismissWg: ${cip.ipAddress}, ${cip.proxyCC}")
        } catch (e: Exception) {
            Napier.w("$TAG err in onDismissWg ${e.message}", e)
        }
    }
}
