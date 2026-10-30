package com.bernaferrari.bravedns.ui.compose.firewall

import Logger
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.rules.RuleImportPreview
import com.bernaferrari.bravedns.rules.RuleImportReader
import com.bernaferrari.bravedns.rules.RuleImportService
import com.bernaferrari.bravedns.ui.compose.theme.RethinkDialogScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
internal fun RulesImportPreviewDialog(preview: RuleImportPreview, busy: Boolean, onImport: () -> Unit, onDismiss: () -> Unit) {
    RethinkDialogScaffold(
        onDismissRequest = { if (!busy) onDismiss() }, title = stringResource(R.string.port_import_rules),
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.port_import_preview, preview.entries.size, preview.duplicateCount, preview.invalidCount))
            Text(stringResource(R.string.port_import_policy))
            if (busy) LinearProgressIndicator()
            if (preview.limitReached) Text(stringResource(R.string.port_import_limit))
            preview.entries.take(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        } },
        confirmButton = { TextButton(enabled = !busy && preview.entries.isNotEmpty(), onClick = onImport) { Text(stringResource(R.string.port_import_rules)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.lbl_cancel)) } },
    )
}

internal suspend fun readRuleImport(context: Context, uri: Uri, tab: RethinkRulesTab): RuleImportPreview? = withContext(Dispatchers.IO) {
    try {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open rule file")
        val coroutine = currentCoroutineContext()
        RuleImportReader.preview(input) { line -> coroutine.ensureActive(); RuleImportService.normalize(tab, line) }
    } catch (error: IOException) {
        Logger.e(Logger.LOG_TAG_UI, "Could not read rule import", error); null
    } catch (error: IllegalArgumentException) {
        Logger.e(Logger.LOG_TAG_UI, "Invalid rule import", error); null
    } catch (error: SecurityException) {
        Logger.e(Logger.LOG_TAG_UI, "Rule import permission denied", error); null
    }
}
