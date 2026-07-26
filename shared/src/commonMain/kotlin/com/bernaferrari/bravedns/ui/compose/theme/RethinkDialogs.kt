/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared confirmation dialog. Hosts supply copy and own the action side effects. */
@Composable
fun RethinkConfirmDialog(
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = onDismissRequest,
    isConfirmDestructive: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    text: (@Composable (() -> Unit))? = null,
) {
    val confirmColors = if (isConfirmDestructive) {
        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
    } else {
        ButtonDefaults.textButtonColors()
    }
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = title?.let { { Text(it) } },
        text = text ?: message?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = confirmColors, enabled = confirmEnabled) { Text(confirmText) }
        },
        dismissButton = if (dismissText != null && onDismiss != null) {
            { TextButton(onClick = onDismiss, enabled = dismissEnabled) { Text(dismissText) } }
        } else {
            null
        },
    )
}
