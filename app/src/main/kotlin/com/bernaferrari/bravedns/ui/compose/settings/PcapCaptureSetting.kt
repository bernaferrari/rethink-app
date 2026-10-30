package com.bernaferrari.bravedns.ui.compose.settings

import Logger
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.diagnostics.PcapCaptureManager
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.util.PcapMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File

@Composable
internal fun PcapCaptureSetting(operationDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    val manager = koinInject<PcapCaptureManager>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val active by manager.activeFlow.collectAsStateWithLifecycle(initialValue = manager.active)
    var files by remember { mutableStateOf(emptyList<File>()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }
    val title = stringResource(R.string.port_capture_title)
    val shareTitle = stringResource(R.string.port_capture_share)
    fun perform(action: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                failed = !withContext(operationDispatcher) { action() }
                files = withContext(operationDispatcher) { manager.files() }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: java.io.IOException) {
                Logger.e(Logger.LOG_TAG_UI, "Packet capture operation failed", error)
                failed = true
            } catch (error: IllegalStateException) {
                Logger.e(Logger.LOG_TAG_UI, "Packet capture operation failed", error)
                failed = true
            } finally { busy = false }
        }
    }
    RethinkListItem(headline = title, supporting = stringResource(R.string.port_capture_description),
        position = CardPosition.Single, onClick = { open = true; perform { true } })
    if (open) com.bernaferrari.bravedns.ui.compose.theme.RethinkDialogScaffold(
        onDismissRequest = { open = false },
        title = title,
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
            item { Text(stringResource(R.string.port_capture_privacy)) }
            if (failed) item { Text(stringResource(R.string.port_capture_error), color = MaterialTheme.colorScheme.error) }
            item {
                if (active) TextButton(enabled = !busy, onClick = { perform { manager.stop() } }) {
                    Text(stringResource(R.string.port_capture_stop))
                } else Column {
                    TextButton(enabled = !busy, onClick = { perform { manager.start(PcapMode.EXTERNAL_FILE) } }) {
                        Text(stringResource(R.string.port_capture_start))
                    }
                    TextButton(enabled = !busy, onClick = { perform { manager.start(PcapMode.LOGCAT) } }) {
                        Text(stringResource(R.string.port_capture_logcat))
                    }
                }
            }
            items(files, key = { it.name }) { file ->
                Column {
                    Text(file.name, style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(enabled = !active && !busy, onClick = {
                            try {
                                if (manager.canExpose(file)) {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                                        .setType("application/vnd.tcpdump.pcap").putExtra(Intent.EXTRA_STREAM, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), shareTitle))
                                }
                            } catch (error: android.content.ActivityNotFoundException) {
                                Logger.e(Logger.LOG_TAG_UI, "No capture sharing application", error); failed = true
                            }
                        }) { Text(shareTitle) }
                        TextButton(enabled = !active && !busy, onClick = { pendingDelete = file }) {
                            Text(stringResource(R.string.lbl_delete))
                        }
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.lbl_done)) } },
    )
    pendingDelete?.let { file ->
        com.bernaferrari.bravedns.ui.compose.theme.RethinkDialogScaffold(onDismissRequest = { pendingDelete = null }, title = stringResource(R.string.port_capture_delete),
            text = { Text(file.name) }, confirmButton = { TextButton(onClick = {
                pendingDelete = null; perform { manager.delete(file) }
            }) { Text(stringResource(R.string.lbl_delete)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.lbl_cancel)) } })
    }
}
