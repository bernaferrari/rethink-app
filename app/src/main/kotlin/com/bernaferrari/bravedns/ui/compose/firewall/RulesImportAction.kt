package com.bernaferrari.bravedns.ui.compose.firewall

import Logger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.rules.RuleImportParser
import com.bernaferrari.bravedns.rules.RuleImportPreview
import com.bernaferrari.bravedns.service.DomainRulesManager
import com.bernaferrari.bravedns.service.IpRulesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

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
                    preview = withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open rule file")
                        val coroutine = kotlinx.coroutines.currentCoroutineContext()
                        com.bernaferrari.bravedns.rules.RuleImportReader.preview(input) { line ->
                            coroutine.ensureActive()
                            com.bernaferrari.bravedns.rules.RuleImportService.normalize(tab, line)
                        }
                    }
                } catch (error: IOException) {
                    Logger.e(Logger.LOG_TAG_UI, "Could not read rule import", error); failed = true
                } catch (error: IllegalArgumentException) {
                    Logger.e(Logger.LOG_TAG_UI, "Rule import exceeds bounds", error); failed = true
                } catch (error: SecurityException) {
                    Logger.e(Logger.LOG_TAG_UI, "Rule import permission denied", error); failed = true
                } finally { busy = false }
            }
        }
    }
    TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) }) {
        Text(stringResource(R.string.port_import_rules))
    }
    preview?.let { parsed ->
        AlertDialog(onDismissRequest = { if (!busy) preview = null },
            title = { Text(stringResource(R.string.port_import_rules)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.port_import_preview, parsed.entries.size, parsed.duplicateCount, parsed.invalidCount))
                Text(stringResource(R.string.port_import_policy))
                if (parsed.limitReached) Text(stringResource(R.string.port_import_limit))
                parsed.entries.take(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            } },
            confirmButton = { TextButton(enabled = !busy && parsed.entries.isNotEmpty(), onClick = {
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
            }) { Text(stringResource(R.string.port_import_rules)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { preview = null }) { Text(stringResource(R.string.lbl_cancel)) } })
    }
    if (failed || summary != null) AlertDialog(
        onDismissRequest = { failed = false; summary = null },
        text = { Text(if (failed) stringResource(R.string.port_import_error) else
            stringResource(R.string.port_import_summary, summary!!.first, summary!!.second)) },
        confirmButton = { TextButton(onClick = { failed = false; summary = null }) { Text(stringResource(R.string.lbl_done)) } })
}
