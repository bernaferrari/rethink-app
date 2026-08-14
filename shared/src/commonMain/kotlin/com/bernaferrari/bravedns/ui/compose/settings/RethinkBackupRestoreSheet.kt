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
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormActionRow
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
    val prompt = when {
        failure != null -> failure to if (failure == RethinkBackupRestoreFailure.Backup) strings.backupFailure else strings.restoreFailure
        confirmation != null -> confirmation to if (confirmation == RethinkBackupRestoreFailure.Backup) strings.backupConfirmation else strings.restoreConfirmation
        else -> null
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
    ) {
        if (prompt != null) {
            val (action, copy) = prompt
            Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                Text(copy.title, style = MaterialTheme.typography.titleLarge)
                Text(copy.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RethinkFormActionRow(
                confirmLabel = copy.confirm,
                onConfirm = {
                    if (failure != null) {
                        onFailureDismiss()
                        if (action == RethinkBackupRestoreFailure.Backup) onBackup() else onRestore()
                    } else {
                        confirmation = null
                        if (action == RethinkBackupRestoreFailure.Backup) onBackup() else onRestore()
                    }
                },
                dismissLabel = copy.dismiss,
                onDismiss = {
                    if (failure != null) onFailureDismiss() else confirmation = null
                },
            )
        } else {
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
    }
}
