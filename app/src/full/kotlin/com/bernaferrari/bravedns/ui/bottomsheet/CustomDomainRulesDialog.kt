package com.bernaferrari.bravedns.ui.bottomsheet


import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
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
import com.bernaferrari.bravedns.database.CustomDomain
import com.bernaferrari.bravedns.service.DomainRulesManager
import com.bernaferrari.bravedns.service.EventLogger
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

private const val TAG = "CDRDialog"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDomainRulesSheet(
    customDomain: CustomDomain,
    eventLogger: EventLogger,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    var status by remember { mutableStateOf(DomainRulesManager.Status.NONE) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(customDomain.uid, customDomain.domain) {
        val uid = customDomain.uid
        if (uid != UID_EVERYBODY) {
            val (names, icon) = withContext(Dispatchers.IO) { fetchRuleSheetAppIdentity(context, uid) }
            appNames = names
            appIcon = icon
        } else {
            appNames = emptyList()
            appIcon = null
        }

        val rules = DomainRulesManager.getDomainRule(customDomain.domain, uid)
        status = rules
    }

    RethinkRuleSheetModal(onDismissRequest = onDismiss) {
        val appName = formatCustomRuleSheetAppName(context, customDomain.uid, appNames)

        val now = System.currentTimeMillis()
        val time =
            DateUtils.getRelativeTimeSpanString(
                customDomain.modifiedTs,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
        val statusLabel =
            when (status) {
                DomainRulesManager.Status.TRUST -> stringResource(R.string.ci_trust_txt)
                DomainRulesManager.Status.BLOCK -> stringResource(R.string.lbl_blocked)
                DomainRulesManager.Status.NONE -> stringResource(R.string.cd_no_rule_txt)
            }
        val statusText = stringResource(R.string.ci_desc, statusLabel, time)
        val deletedToast = stringResource(R.string.cd_toast_deleted)

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

            RethinkRuleValue(value = customDomain.domain)

            RethinkRuleSupportingText(
                text = statusText,
            )

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
                        RethinkRuleActionOption(
                            action = RethinkRuleAction.Trust,
                            label = stringResource(R.string.ci_trust_rule),
                        )
                    ),
                selectedAction = status.toRethinkRuleAction(),
                onActionChange = { action ->
                    updateRule(customDomain, action.toDomainRuleStatus(), scope, eventLogger) { newStatus -> status = newStatus }
                },
            )
        }

        if (showDeleteDialog) {
            RethinkRuleSheetDeleteDialog(
                title = stringResource(R.string.cd_remove_dialog_title),
                message = stringResource(R.string.cd_remove_dialog_message),
                deleteLabel = stringResource(R.string.lbl_delete),
                cancelLabel = stringResource(R.string.lbl_cancel),
                onDismiss = { showDeleteDialog = false },
                onConfirm = {
                    showDeleteDialog = false
                    scope.launch(Dispatchers.IO) {
                        DomainRulesManager.deleteDomain(customDomain)
                        withContext(Dispatchers.Main) {
                            Utilities.showToastUiCentered(
                                context,
                                deletedToast,
                                Toast.LENGTH_SHORT
                            )
                        }
                    }
                    logEvent(
                        eventLogger,
                        "Deleted custom domain rule for ${customDomain.domain}"
                    )
                    onDeleted()
                    onDismiss()
                },
            )
        }
    }
}

private fun DomainRulesManager.Status.toRethinkRuleAction() = when (this) {
    DomainRulesManager.Status.NONE -> RethinkRuleAction.None
    DomainRulesManager.Status.BLOCK -> RethinkRuleAction.Block
    DomainRulesManager.Status.TRUST -> RethinkRuleAction.Trust
}

private fun RethinkRuleAction.toDomainRuleStatus() = when (this) {
    RethinkRuleAction.None, RethinkRuleAction.Bypass -> DomainRulesManager.Status.NONE
    RethinkRuleAction.Block -> DomainRulesManager.Status.BLOCK
    RethinkRuleAction.Trust -> DomainRulesManager.Status.TRUST
}

private fun updateRule(
    customDomain: CustomDomain,
    rule: DomainRulesManager.Status,
    scope: kotlinx.coroutines.CoroutineScope,
    eventLogger: EventLogger,
    onUpdated: (DomainRulesManager.Status) -> Unit
) {
    launchRuleMutation(scope, mutation = {
        when (rule) {
            DomainRulesManager.Status.NONE -> DomainRulesManager.noRule(customDomain)
            DomainRulesManager.Status.BLOCK -> DomainRulesManager.block(customDomain)
            DomainRulesManager.Status.TRUST -> DomainRulesManager.trust(customDomain)
        }
        val status = DomainRulesManager.Status.getStatus(customDomain.status)
        logEvent(eventLogger, "Domain rule for ${customDomain.domain} set to ${status.name}")
        status
    }, onUpdated = onUpdated)
}

private fun logEvent(eventLogger: EventLogger, details: String) {
    logFirewallRuleChange(eventLogger, "Custom Domain", details, TAG)
}
