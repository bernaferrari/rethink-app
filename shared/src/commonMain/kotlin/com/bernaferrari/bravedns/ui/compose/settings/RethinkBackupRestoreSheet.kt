/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

enum class RethinkBackupRestoreFailure { Backup, Restore }

data class RethinkBackupRestoreDialogCopy(
    val title: String,
    val message: String,
    val confirm: String,
    val dismiss: String,
)

data class RethinkBackupRestoreStrings(
    val title: String,
    val description: String,
    val backupTitle: String,
    val backupDescription: String,
    val restoreTitle: String,
    val restoreDescription: String,
    val backupConfirmation: RethinkBackupRestoreDialogCopy,
    val restoreConfirmation: RethinkBackupRestoreDialogCopy,
    val backupFailure: RethinkBackupRestoreDialogCopy,
    val restoreFailure: RethinkBackupRestoreDialogCopy,
)

/** Shared backup/restore sheet. Hosts own document pickers, work queues, notifications, and restart. */
@Composable
fun RethinkBackupRestoreSheet(
    versionText: String,
    strings: RethinkBackupRestoreStrings,
    failure: RethinkBackupRestoreFailure?,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onFailureDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmation by remember { mutableStateOf<RethinkBackupRestoreFailure?>(null) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
            Text(strings.title, style = MaterialTheme.typography.titleLarge)
            Text(strings.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RethinkListGroup {
            RethinkListItem(
                headline = strings.backupTitle,
                supporting = strings.backupDescription,
                leadingIcon = MaterialSymbols.Filled.Backup,
                position = CardPosition.First,
                onClick = { confirmation = RethinkBackupRestoreFailure.Backup },
            )
            RethinkListItem(
                headline = strings.restoreTitle,
                supporting = strings.restoreDescription,
                leadingIcon = MaterialSymbols.Filled.Restore,
                position = CardPosition.Last,
                onClick = { confirmation = RethinkBackupRestoreFailure.Restore },
            )
        }
        Surface(
            shape = RoundedCornerShape(SharedDimensions.cardCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                versionText,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(SharedDimensions.spacingMd),
            )
        }
    }
    confirmation?.let { action ->
        val copy = if (action == RethinkBackupRestoreFailure.Backup) strings.backupConfirmation else strings.restoreConfirmation
        RethinkBackupRestoreConfirmation(
            copy = copy,
            onConfirm = {
                confirmation = null
                if (action == RethinkBackupRestoreFailure.Backup) onBackup() else onRestore()
            },
            onDismiss = { confirmation = null },
        )
    }
    failure?.let { failed ->
        val copy = if (failed == RethinkBackupRestoreFailure.Backup) strings.backupFailure else strings.restoreFailure
        RethinkBackupRestoreConfirmation(
            copy = copy,
            onConfirm = {
                onFailureDismiss()
                if (failed == RethinkBackupRestoreFailure.Backup) onBackup() else onRestore()
            },
            onDismiss = onFailureDismiss,
        )
    }
}

@Composable
private fun RethinkBackupRestoreConfirmation(
    copy: RethinkBackupRestoreDialogCopy,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = copy.title,
        message = copy.message,
        confirmText = copy.confirm,
        dismissText = copy.dismiss,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
