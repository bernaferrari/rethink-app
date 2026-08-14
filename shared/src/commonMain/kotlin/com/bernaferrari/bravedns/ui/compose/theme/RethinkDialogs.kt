/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    } else {
        ButtonDefaults.buttonColors()
    }
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(SharedDimensions.cardCornerRadiusLarge),
        title = title?.let { { Text(it, style = MaterialTheme.typography.headlineSmall) } },
        text = text ?: message?.let { { Text(it) } },
        confirmButton = {
            Button(onClick = onConfirm, colors = confirmColors, enabled = confirmEnabled) { Text(confirmText) }
        },
        dismissButton = if (dismissText != null && onDismiss != null) {
            { TextButton(onClick = onDismiss, enabled = dismissEnabled) { Text(dismissText) } }
        } else {
            null
        },
    )
}
