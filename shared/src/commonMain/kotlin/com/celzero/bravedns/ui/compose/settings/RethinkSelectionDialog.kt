/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.theme.RethinkRadioChoiceList

data class RethinkSelectionOption(
    val id: String,
    val title: String,
    val description: String? = null,
)

/** Shared radio-choice dialog for target-neutral settings choices. */
@Composable
fun RethinkSelectionDialog(
    title: String,
    options: List<RethinkSelectionOption>,
    initialSelectedId: String,
    confirm: String,
    cancel: String,
    onDismiss: () -> Unit,
    onConfirm: (RethinkSelectionOption) -> Unit,
) {
    var selectedId by remember(initialSelectedId, options) { mutableStateOf(initialSelectedId) }
    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = title,
        text = {
            RethinkRadioChoiceList(
                options = options,
                selected = { it.id == selectedId },
                label = { it.title },
                supporting = { it.description?.takeIf(String::isNotBlank) },
                onSelected = { selectedId = it.id },
            )
        },
        confirmText = confirm,
        dismissText = cancel,
        onConfirm = { options.firstOrNull { it.id == selectedId }?.let(onConfirm) },
        onDismiss = onDismiss,
    )
}
