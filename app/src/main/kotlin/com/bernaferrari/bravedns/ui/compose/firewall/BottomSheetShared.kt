/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import Logger
import Logger.LOG_TAG_UI
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormActionRow
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Android app-identity and mutation adapters for the shared rule-sheet renderers. */
/** The uid's app names and the first one's package (for its icon); the package is null when unknown. */
suspend fun fetchRuleSheetAppIdentity(uid: Int): Pair<List<String>, String?> {
    val appNames = FirewallManager.getAppNamesByUid(uid)
    val packageName = appNames.firstOrNull()?.let { FirewallManager.getPackageNameByAppName(it) }
    return appNames to packageName?.takeIf { it.isNotEmpty() }
}

fun formatRuleSheetAppName(context: Context, appNames: List<String>): String? =
    when {
        appNames.isEmpty() -> null
        appNames.size >= 2 -> context.getString(R.string.ctbs_app_other_apps, appNames[0], appNames.size.minus(1).toString())
        else -> appNames[0]
    }

fun logFirewallRuleChange(
    eventLogger: EventLogger,
    title: String,
    details: String,
    tag: String? = null,
) {
    eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, title, EventSource.UI, false, details)
    tag?.let { Logger.v(LOG_TAG_UI, "$it $details") }
}

/** Trailing Apply/Cancel row. Apply stays disabled until the sheet has a valid pending change. */
@Composable
fun RuleSheetApplyBar(
    enabled: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RethinkFormActionRow(
        confirmLabel = stringResource(R.string.lbl_apply),
        onConfirm = onApply,
        dismissLabel = stringResource(R.string.lbl_cancel),
        onDismiss = onDismiss,
        confirmEnabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SharedDimensions.screenPaddingHorizontal),
    )
}

@Composable
fun RuleSheetSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SharedDimensions.screenPaddingHorizontal),
    )
}

suspend fun SnackbarHostState.showRuleUndoSnackbar(
    message: String,
    undoLabel: String,
): Boolean {
    val result = showSnackbar(
        message = message,
        actionLabel = undoLabel,
        withDismissAction = true,
        duration = SnackbarDuration.Short,
    )
    return result == SnackbarResult.ActionPerformed
}

/**
 * Persist [apply], then offer a one-shot Restore snackbar. A newer Apply increments [epoch] so a
 * stale snackbar cannot rewind a later commit.
 */
fun CoroutineScope.launchReversibleRuleMutation(
    snackbarHostState: SnackbarHostState,
    editedMessage: String,
    undoLabel: String,
    epoch: Int,
    currentEpoch: () -> Int,
    setApplying: (Boolean) -> Unit,
    apply: suspend () -> Boolean,
    undo: suspend () -> Unit,
) {
    launch {
        setApplying(true)
        try {
            val applied = apply()
            setApplying(false)
            if (!applied || currentEpoch() != epoch) return@launch
            val undone = snackbarHostState.showRuleUndoSnackbar(editedMessage, undoLabel)
            if (undone && currentEpoch() == epoch) {
                setApplying(true)
                undo()
            }
        } finally {
            setApplying(false)
        }
    }
}
