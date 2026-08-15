/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Canonical modal chrome. Alert-style hosts supply [icon]/[title]/[message]/[text]/actions;
 * full-width pickers use [RethinkFullWidthDialog] instead of constructing a raw [Dialog].
 */
@Composable
fun RethinkDialogScaffold(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable (() -> Unit))? = null,
    title: String? = null,
    message: String? = null,
    text: (@Composable (() -> Unit))? = null,
    dismissButton: (@Composable (() -> Unit))? = null,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(SharedDimensions.cardCornerRadiusLarge),
        icon = icon,
        title = title?.let { { Text(it, style = MaterialTheme.typography.headlineSmall) } },
        text = text ?: message?.let { { Text(it) } },
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}

/** Full-bleed dialog shell for list pickers. Does not clip or constrain width. */
@Composable
fun RethinkFullWidthDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, content = content)
    }
}

@Composable
internal fun rethinkDialogConfirmColors(destructive: Boolean): ButtonColors =
    if (destructive) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    } else {
        ButtonDefaults.buttonColors()
    }

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
    icon: (@Composable (() -> Unit))? = null,
) {
    RethinkDialogScaffold(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        icon = icon,
        title = title,
        message = message,
        text = text,
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = rethinkDialogConfirmColors(isConfirmDestructive),
                enabled = confirmEnabled,
            ) { Text(confirmText) }
        },
        dismissButton = if (dismissText != null && onDismiss != null) {
            { TextButton(onClick = onDismiss, enabled = dismissEnabled) { Text(dismissText) } }
        } else {
            null
        },
    )
}
