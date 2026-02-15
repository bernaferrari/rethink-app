package com.celzero.bravedns.ui.bottomsheet


import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.CustomIp
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.IpRulesManager.IpRuleStatus
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import io.github.aakira.napier.Napier
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
            val loadedAppNames = withContext(Dispatchers.IO) { FirewallManager.getAppNamesByUid(uid) }
            val appInfo = withContext(Dispatchers.IO) { FirewallManager.getAppInfoByUid(uid) }
            appNames = loadedAppNames
            appIcon =
                Utilities.getIcon(
                    context,
                    appInfo?.packageName ?: "",
                    appInfo?.appName ?: ""
                )
        }
        status = IpRuleStatus.getStatus(customIp.status)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        val appName =
            when {
                customIp.uid == UID_EVERYBODY ->
                    stringResource(R.string.firewall_act_universal_tab).replaceFirstChar(Char::titlecase)
                appNames.isEmpty() ->
                    stringResource(R.string.network_log_app_name_unknown) + " (${customIp.uid})"
                appNames.size >= 2 ->
                    stringResource(
                        R.string.ctbs_app_other_apps,
                        appNames[0],
                        appNames.size.minus(1).toString()
                    )
                else -> appNames[0]
            }
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
        val borderColor = MaterialTheme.colorScheme.outline
        val neutralText = MaterialTheme.colorScheme.onSurfaceVariant
        val neutralBg = MaterialTheme.colorScheme.surfaceVariant
        val negativeText = MaterialTheme.colorScheme.error
        val negativeBg = MaterialTheme.colorScheme.errorContainer
        val positiveText = MaterialTheme.colorScheme.tertiary
        val positiveBg = MaterialTheme.colorScheme.tertiaryContainer

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
                val deleteText = MaterialTheme.colorScheme.error
                TextButton(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = deleteText)
                ) {
                    Text(text = stringResource(R.string.lbl_delete))
                }
            }

            if (appName.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    appIcon?.let { icon ->
                        val painter = rememberDrawablePainter(icon)
                        painter?.let {
                            Image(
                                painter = it,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
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
                    text = customIp.ipAddress,
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
                    label = stringResource(R.string.ci_no_rule),
                    selected = status == IpRuleStatus.NONE,
                    selectedText = neutralText,
                    selectedContainer = neutralBg
                ) {
                    updateRule(customIp, IpRuleStatus.NONE, scope, eventLogger) { newStatus ->
                        status = newStatus
                    }
                }
                RuleChip(
                    label = stringResource(R.string.ci_block),
                    selected = status == IpRuleStatus.BLOCK,
                    selectedText = negativeText,
                    selectedContainer = negativeBg
                ) {
                    updateRule(customIp, IpRuleStatus.BLOCK, scope, eventLogger) { newStatus ->
                        status = newStatus
                    }
                }
                if (customIp.uid == UID_EVERYBODY) {
                    RuleChip(
                        label = stringResource(R.string.ci_bypass_universal),
                        selected = status == IpRuleStatus.BYPASS_UNIVERSAL,
                        selectedText = positiveText,
                        selectedContainer = positiveBg
                    ) {
                        updateRule(customIp, IpRuleStatus.BYPASS_UNIVERSAL, scope, eventLogger) { newStatus ->
                            status = newStatus
                        }
                    }
                } else {
                    RuleChip(
                        label = stringResource(R.string.ci_trust_rule),
                        selected = status == IpRuleStatus.TRUST,
                        selectedText = positiveText,
                        selectedContainer = positiveBg
                    ) {
                        updateRule(customIp, IpRuleStatus.TRUST, scope, eventLogger) { newStatus ->
                            status = newStatus
                        }
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(text = stringResource(R.string.univ_firewall_dialog_title)) },
                text = { Text(text = stringResource(R.string.univ_firewall_dialog_message)) },
                confirmButton = {
                    Button(
                        onClick = {
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
                        }
                    ) {
                        Text(text = stringResource(R.string.lbl_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(text = stringResource(R.string.lbl_cancel))
                    }
                }
            )
        }
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

private fun updateRule(
    customIp: CustomIp,
    rule: IpRuleStatus,
    scope: kotlinx.coroutines.CoroutineScope,
    eventLogger: EventLogger,
    onUpdated: (IpRuleStatus) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        val updated =
            when (rule) {
                IpRuleStatus.NONE -> noRuleIp(customIp, eventLogger)
                IpRuleStatus.BLOCK -> blockIp(customIp, eventLogger)
                IpRuleStatus.BYPASS_UNIVERSAL -> byPassUniversal(customIp, eventLogger)
                IpRuleStatus.TRUST -> byPassAppRule(customIp, eventLogger)
            }
        onUpdated(rule)
        Napier.v("$TAG changeIpStatus: ${updated.ipAddress}, status: ${rule.name}")
    }
}

private suspend fun byPassUniversal(orig: CustomIp, eventLogger: EventLogger): CustomIp {
    Napier.i("$TAG set ${orig.ipAddress} to bypass universal")
    val copy = orig.deepCopy()
    IpRulesManager.updateBypass(copy)
    logEvent(eventLogger, "Set IP ${copy.ipAddress} to bypass universal")
    return copy
}

private suspend fun byPassAppRule(orig: CustomIp, eventLogger: EventLogger): CustomIp {
    Napier.i("$TAG set ${orig.ipAddress} to bypass app")
    val copy = orig.deepCopy()
    IpRulesManager.updateTrust(copy)
    logEvent(eventLogger, "Set IP ${copy.ipAddress} to trust")
    return copy
}

private suspend fun blockIp(orig: CustomIp, eventLogger: EventLogger): CustomIp {
    Napier.i("$TAG block ${orig.ipAddress}")
    val copy = orig.deepCopy()
    IpRulesManager.updateBlock(copy)
    logEvent(eventLogger, "Blocked IP ${copy.ipAddress}")
    return copy
}

private suspend fun noRuleIp(orig: CustomIp, eventLogger: EventLogger): CustomIp {
    Napier.i("$TAG no rule for ${orig.ipAddress}")
    val copy = orig.deepCopy()
    IpRulesManager.updateNoRule(copy)
    logEvent(eventLogger, "Set no rule for IP ${copy.ipAddress}")
    return copy
}

private fun logEvent(eventLogger: EventLogger, details: String) {
    eventLogger.log(
        EventType.FW_RULE_MODIFIED,
        Severity.LOW,
        "Custom IP",
        EventSource.UI,
        false,
        details
    )
}
