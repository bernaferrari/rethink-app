package com.celzero.bravedns.ui.bottomsheet


import android.graphics.drawable.Drawable
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
import com.celzero.bravedns.database.CustomDomain
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import io.github.aakira.napier.Napier
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
            appNames = withContext(Dispatchers.IO) { FirewallManager.getAppNamesByUid(uid) }
            val appInfo = withContext(Dispatchers.IO) { FirewallManager.getAppInfoByUid(uid) }
            appIcon =
                Utilities.getIcon(
                    context,
                    appInfo?.packageName ?: "",
                    appInfo?.appName ?: ""
                )
        } else {
            appNames = emptyList()
            appIcon = null
        }

        val rules = DomainRulesManager.getDomainRule(customDomain.domain, uid)
        status = rules
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        val appName =
            when {
                customDomain.uid == UID_EVERYBODY ->
                    stringResource(R.string.firewall_act_universal_tab).replaceFirstChar(Char::titlecase)

                appNames.isEmpty() ->
                    stringResource(R.string.network_log_app_name_unknown) + " (${customDomain.uid})"

                appNames.size >= 2 ->
                    stringResource(
                        R.string.ctbs_app_other_apps,
                        appNames[0],
                        appNames.size.minus(1).toString()
                    )

                else -> appNames[0]
            }

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

        val borderColor = MaterialTheme.colorScheme.outline
        val neutralText = MaterialTheme.colorScheme.onSurfaceVariant
        val neutralBg = MaterialTheme.colorScheme.surfaceVariant
        val negativeText = MaterialTheme.colorScheme.error
        val negativeBg = MaterialTheme.colorScheme.errorContainer
        val positiveText = MaterialTheme.colorScheme.tertiary
        val positiveBg = MaterialTheme.colorScheme.tertiaryContainer

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(60.dp)
                        .height(3.dp)
                        .background(borderColor, RoundedCornerShape(2.dp))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
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

            Text(
                text = stringResource(R.string.lbl_domain),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
            )

            SelectionContainer(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)) {
                Text(
                    text = customDomain.domain,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RuleChip(
                    label = stringResource(R.string.ci_no_rule),
                    selected = status == DomainRulesManager.Status.NONE,
                    selectedText = neutralText,
                    selectedContainer = neutralBg
                ) {
                    updateRule(
                        customDomain,
                        DomainRulesManager.Status.NONE,
                        scope,
                        eventLogger
                    ) { newStatus ->
                        status = newStatus
                    }
                }
                RuleChip(
                    label = stringResource(R.string.ci_block),
                    selected = status == DomainRulesManager.Status.BLOCK,
                    selectedText = negativeText,
                    selectedContainer = negativeBg
                ) {
                    updateRule(
                        customDomain,
                        DomainRulesManager.Status.BLOCK,
                        scope,
                        eventLogger
                    ) { newStatus ->
                        status = newStatus
                    }
                }
                RuleChip(
                    label = stringResource(R.string.ci_trust_rule),
                    selected = status == DomainRulesManager.Status.TRUST,
                    selectedText = positiveText,
                    selectedContainer = positiveBg
                ) {
                    updateRule(
                        customDomain,
                        DomainRulesManager.Status.TRUST,
                        scope,
                        eventLogger
                    ) { newStatus ->
                        status = newStatus
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(text = stringResource(R.string.cd_remove_dialog_title)) },
                text = { Text(text = stringResource(R.string.cd_remove_dialog_message)) },
                confirmButton = {
                    Button(
                        onClick = {
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
    customDomain: CustomDomain,
    rule: DomainRulesManager.Status,
    scope: kotlinx.coroutines.CoroutineScope,
    eventLogger: EventLogger,
    onUpdated: (DomainRulesManager.Status) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        when (rule) {
            DomainRulesManager.Status.NONE -> DomainRulesManager.noRule(customDomain)
            DomainRulesManager.Status.BLOCK -> DomainRulesManager.block(customDomain)
            DomainRulesManager.Status.TRUST -> DomainRulesManager.trust(customDomain)
        }
        val status = DomainRulesManager.Status.getStatus(customDomain.status)
        withContext(Dispatchers.Main) {
            onUpdated(status)
        }
        logEvent(eventLogger, "Domain rule for ${customDomain.domain} set to ${status.name}")
    }
}

private fun logEvent(eventLogger: EventLogger, details: String) {
    eventLogger.log(
        EventType.FW_RULE_MODIFIED,
        Severity.LOW,
        "Custom Domain",
        EventSource.UI,
        false,
        details
    )
    Napier.v("$TAG $details")
}
