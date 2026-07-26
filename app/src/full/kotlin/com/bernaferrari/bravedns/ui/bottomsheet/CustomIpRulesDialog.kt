package com.bernaferrari.bravedns.ui.bottomsheet


import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.CustomIp
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.IpRulesManager
import com.bernaferrari.bravedns.service.IpRulesManager.IpRuleStatus
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.ui.compose.rememberDrawablePainter
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleAction
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleActionOption
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleActionSelector
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleEditorHeader
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSheetBottomPaddingCompact
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSheetDeleteAction
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSheetDeleteDialog
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSheetLayout
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSheetModal
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSupportingText
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleValue
import Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "CIRDialog"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomIpRulesSheet(
    customIp: CustomIp,
    eventLogger: EventLogger,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    var status by remember { mutableStateOf(IpRuleStatus.getStatus(customIp.status)) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(customIp.uid, customIp.ipAddress) {
        val uid = customIp.uid
        if (uid == UID_EVERYBODY) {
            appNames = emptyList()
            appIcon = null
        } else {
            val (names, icon) = withContext(Dispatchers.IO) { fetchRuleSheetAppIdentity(context, uid) }
            appNames = names
            appIcon = icon
        }
        status = IpRuleStatus.getStatus(customIp.status)
    }

    RethinkRuleSheetModal(onDismissRequest = onDismiss) {
        val appName = formatCustomRuleSheetAppName(context, customIp.uid, appNames)
        val now = System.currentTimeMillis()
        val uptime = System.currentTimeMillis() - customIp.modifiedDateTime
        val time =
            DateUtils.getRelativeTimeSpanString(
                now - uptime,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
        val statusLabel =
            when (status) {
                IpRuleStatus.TRUST -> stringResource(R.string.ci_trust_txt)
                IpRuleStatus.BLOCK -> stringResource(R.string.lbl_blocked)
                IpRuleStatus.NONE -> stringResource(R.string.cd_no_rule_txt)
                IpRuleStatus.BYPASS_UNIVERSAL -> stringResource(R.string.ci_bypass_universal_txt)
            }
        val statusText = stringResource(R.string.ci_desc, statusLabel, time)
        val deleteToast = stringResource(R.string.univ_ip_delete_individual_toast, customIp.ipAddress)

        RethinkRuleSheetLayout(bottomPadding = RethinkRuleSheetBottomPaddingCompact) {
            RethinkRuleSheetDeleteAction(
                label = stringResource(R.string.lbl_delete),
                onClick = { showDeleteDialog = true },
            )

            RethinkRuleEditorHeader(
                appName = appName,
                appIcon = {
                    appIcon?.let { icon ->
                        rememberDrawablePainter(icon)?.let { painter ->
                            Image(painter = painter, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                },
            )

            RethinkRuleValue(
                value = customIp.ipAddress,
                textStyle = androidx.compose.material3.MaterialTheme.typography.titleLarge
            )

            RethinkRuleSupportingText(
                text = statusText,
            )

            val thirdOption =
                if (customIp.uid == UID_EVERYBODY) {
                    RethinkRuleActionOption(
                        action = RethinkRuleAction.Bypass,
                        label = stringResource(R.string.ci_bypass_universal),
                    )
                } else {
                    RethinkRuleActionOption(
                        action = RethinkRuleAction.Trust,
                        label = stringResource(R.string.ci_trust_rule),
                    )
                }

            RethinkRuleActionSelector(
                options =
                    listOf(
                        RethinkRuleActionOption(
                            action = RethinkRuleAction.None,
                            label = stringResource(R.string.ci_no_rule),
                        ),
                        RethinkRuleActionOption(
                            action = RethinkRuleAction.Block,
                            label = stringResource(R.string.ci_block),
                        ),
                        thirdOption
                    ),
                selectedAction = status.toRethinkRuleAction(),
                onActionChange = { action ->
                    updateRule(customIp, action.toIpRuleStatus(), scope, eventLogger) { newStatus -> status = newStatus }
                },
            )
        }

        if (showDeleteDialog) {
            RethinkRuleSheetDeleteDialog(
                title = stringResource(R.string.univ_firewall_dialog_title),
                message = stringResource(R.string.univ_firewall_dialog_message),
                deleteLabel = stringResource(R.string.lbl_delete),
                cancelLabel = stringResource(R.string.lbl_cancel),
                onDismiss = { showDeleteDialog = false },
                onConfirm = {
                    showDeleteDialog = false
                    scope.launch(Dispatchers.IO) {
                        IpRulesManager.removeIpRule(customIp.uid, customIp.ipAddress, customIp.port)
                        withContext(Dispatchers.Main) {
                            Utilities.showToastUiCentered(
                                context,
                                deleteToast,
                                Toast.LENGTH_SHORT
                            )
                        }
                    }
                    logEvent(eventLogger, "Deleted custom IP rule for ${customIp.ipAddress}")
                    onDeleted()
                    onDismiss()
                },
            )
        }
    }
}

private fun IpRuleStatus.toRethinkRuleAction() = when (this) {
    IpRuleStatus.NONE -> RethinkRuleAction.None
    IpRuleStatus.BLOCK -> RethinkRuleAction.Block
    IpRuleStatus.TRUST -> RethinkRuleAction.Trust
    IpRuleStatus.BYPASS_UNIVERSAL -> RethinkRuleAction.Bypass
}

private fun RethinkRuleAction.toIpRuleStatus() = when (this) {
    RethinkRuleAction.None -> IpRuleStatus.NONE
    RethinkRuleAction.Block -> IpRuleStatus.BLOCK
    RethinkRuleAction.Trust -> IpRuleStatus.TRUST
    RethinkRuleAction.Bypass -> IpRuleStatus.BYPASS_UNIVERSAL
}

private fun updateRule(
    customIp: CustomIp,
    rule: IpRuleStatus,
    scope: kotlinx.coroutines.CoroutineScope,
    eventLogger: EventLogger,
    onUpdated: (IpRuleStatus) -> Unit
) {
    launchRuleMutation(scope, mutation = {
        val updated =
            when (rule) {
                IpRuleStatus.NONE -> noRuleIp(customIp, eventLogger)
                IpRuleStatus.BLOCK -> blockIp(customIp, eventLogger)
                IpRuleStatus.BYPASS_UNIVERSAL -> byPassUniversal(customIp, eventLogger)
                IpRuleStatus.TRUST -> byPassAppRule(customIp, eventLogger)
            }
        Logger.v(TAG, "$TAG changeIpStatus: ${updated.ipAddress}, status: ${rule.name}")
        rule
    }, onUpdated = onUpdated)
}

private suspend fun byPassUniversal(orig: CustomIp, eventLogger: EventLogger): CustomIp {
    Logger.i(TAG, "$TAG set ${orig.ipAddress} to bypass universal")
    val copy = orig.deepCopy()
    IpRulesManager.updateBypass(copy)
    logEvent(eventLogger, "Set IP ${copy.ipAddress} to bypass universal")
    return copy
}

private suspend fun byPassAppRule(orig: CustomIp, eventLogger: EventLogger): CustomIp {
    Logger.i(TAG, "$TAG set ${orig.ipAddress} to bypass app")
    val copy = orig.deepCopy()
    IpRulesManager.updateTrust(copy)
    logEvent(eventLogger, "Set IP ${copy.ipAddress} to trust")
    return copy
}

private suspend fun blockIp(orig: CustomIp, eventLogger: EventLogger): CustomIp {
    Logger.i(TAG, "$TAG block ${orig.ipAddress}")
    val copy = orig.deepCopy()
    IpRulesManager.updateBlock(copy)
    logEvent(eventLogger, "Blocked IP ${copy.ipAddress}")
    return copy
}

private suspend fun noRuleIp(orig: CustomIp, eventLogger: EventLogger): CustomIp {
    Logger.i(TAG, "$TAG no rule for ${orig.ipAddress}")
    val copy = orig.deepCopy()
    IpRulesManager.updateNoRule(copy)
    logEvent(eventLogger, "Set no rule for IP ${copy.ipAddress}")
    return copy
}

private fun logEvent(eventLogger: EventLogger, details: String) {
    logFirewallRuleChange(eventLogger, "Custom IP", details)
}
