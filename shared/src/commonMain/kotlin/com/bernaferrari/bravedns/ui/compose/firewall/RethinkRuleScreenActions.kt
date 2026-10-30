package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

@Composable
internal fun RethinkRulesTopBar(
    selectionCount: Int, title: String, strings: RethinkCustomRulesStrings,
    onBackClick: (() -> Unit)?, onClearSelection: () -> Unit, onDeleteSelection: () -> Unit,
    canImport: Boolean, importAction: (@Composable () -> Unit)?,
) {
    if (selectionCount > 0) {
        RethinkTopBar(title = strings.selectedCount(selectionCount), onBackClick = onClearSelection,
            backDescription = strings.clearSelection, navigationIcon = MaterialSymbols.Filled.Close,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            actions = { IconButton(onClick = onDeleteSelection) { Icon(MaterialSymbols.Filled.DeleteOutline, strings.delete) } })
    } else RethinkTopBar(title = title, onBackClick = onBackClick, actions = { if (canImport) importAction?.invoke() })
}

@Composable
internal fun RethinkRulesAddButton(
    selecting: Boolean, mode: RethinkRulesMode, loading: Boolean, count: Int, label: String, onAdd: () -> Unit,
) {
    if (!selecting && mode == RethinkRulesMode.SELECTED_UID && (loading || count > 0)) {
        ExtendedFloatingActionButton(onClick = onAdd,
            icon = { Icon(MaterialSymbols.Filled.Add, null) }, text = { Text(label) })
    }
}

@Composable
internal fun RethinkDeleteRulesDialog(
    rules: List<RethinkFirewallRule>, strings: RethinkCustomRulesStrings, onDismiss: () -> Unit, onConfirm: () -> Unit,
) {
    if (rules.isEmpty()) return
    val single = rules.singleOrNull()
    RethinkConfirmDialog(onDismissRequest = onDismiss,
        title = if (single != null) strings.deleteRuleTitle else strings.deleteSelectedTitle(rules.size),
        message = single?.let { listOfNotNull(it.value, it.port?.let(strings.port)).joinToString(" · ") }.orEmpty(),
        confirmText = strings.delete, dismissText = strings.cancel, isConfirmDestructive = true, onConfirm = onConfirm)
}
