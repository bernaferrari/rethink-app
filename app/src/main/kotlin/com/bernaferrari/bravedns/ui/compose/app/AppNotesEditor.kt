package com.bernaferrari.bravedns.ui.compose.app

import Logger
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.AppInfo
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AppNotesEditor(app: AppInfo) {
    var open by rememberSaveable(app.uid, app.packageName) { mutableStateOf(false) }
    var draft by rememberSaveable(app.uid, app.packageName) { mutableStateOf(app.notes) }
    var savedNotes by remember(app.uid, app.packageName) { mutableStateOf(app.notes) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.port_notes)
    RethinkListItem(headline = title, supporting = savedNotes.ifBlank { stringResource(R.string.port_notes_hint) },
        position = CardPosition.Single, onClick = { open = true })
    if (open) AlertDialog(
        onDismissRequest = { if (!saving) open = false },
        title = { Text(title) },
        text = { Column {
            OutlinedTextField(value = draft, onValueChange = { if (it.length <= 500) { draft = it; failed = false } },
                label = { Text(title) }, enabled = !saving,
                supportingText = { Text("${draft.length}/500") }, isError = failed)
            if (failed) Text(stringResource(R.string.port_notes_error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            saving = true
            scope.launch {
                try {
                    val success = withContext(Dispatchers.IO) { FirewallManager.updateNotes(app.uid, app.packageName, draft) }
                    if (success) { savedNotes = draft; open = false } else failed = true
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: android.database.SQLException) {
                    Logger.e(Logger.LOG_TAG_UI, "Could not save app notes", error)
                    failed = true
                } catch (error: IllegalStateException) {
                    Logger.e(Logger.LOG_TAG_UI, "Could not save app notes", error)
                    failed = true
                } finally { saving = false }
            }
        }) { Text(stringResource(R.string.port_save)) } },
        dismissButton = { TextButton(enabled = !saving, onClick = { open = false }) { Text(stringResource(R.string.lbl_cancel)) } },
    )
}
