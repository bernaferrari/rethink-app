package com.bernaferrari.bravedns.ui.compose.firewall

import Logger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.rules.RuleImportPreview
import com.bernaferrari.bravedns.service.IpRulesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RulesImportAction(uid: Int, tab: RethinkRulesTab) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember(uid, tab) { mutableStateOf<RuleImportPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true; failed = false
            scope.launch {
                try {
                    preview = readRuleImport(context, uri, tab)
                    failed = preview == null
                } finally { busy = false }
            }
        }
    }
    TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) }) {
        Text(stringResource(R.string.port_import_rules))
    }
    preview?.let { parsed ->
        RulesImportPreviewDialog(parsed, busy,
            onImport = {
                busy = true
                scope.launch {
                    try {
                        summary = withContext(Dispatchers.IO) { com.bernaferrari.bravedns.rules.RuleImportService.import(uid, tab, parsed.entries) }
                        preview = null
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: android.database.SQLException) {
                        Logger.e(Logger.LOG_TAG_UI, "Could not import rules", error); failed = true
                    } catch (error: IllegalStateException) {
                        Logger.e(Logger.LOG_TAG_UI, "Could not import rules", error); failed = true
                    } finally { busy = false }
                }
            },
            onDismiss = { preview = null },
        )
    }
    if (failed || summary != null) com.bernaferrari.bravedns.ui.compose.theme.RethinkDialogScaffold(
        onDismissRequest = { failed = false; summary = null },
        text = { Text(if (failed) stringResource(R.string.port_import_error) else
            stringResource(R.string.port_import_summary, summary!!.first, summary!!.second)) },
        confirmButton = { TextButton(onClick = { failed = false; summary = null }) { Text(stringResource(R.string.lbl_done)) } })
}
