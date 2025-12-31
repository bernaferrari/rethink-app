/*
 * Copyright 2025 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.ui.bottomsheet

import Logger
import Logger.LOG_TAG_UI
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.scheduler.BugReportZipper
import com.celzero.bravedns.scheduler.EnhancedBugReport
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastO
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BugReportFilesDialog(private val activity: FragmentActivity) : KoinComponent {
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()

    private val bugReportFiles = mutableStateListOf<BugReportFile>()
    private var isSending by mutableStateOf(false)
    private var progressText by mutableStateOf("")
    private var pendingDelete by mutableStateOf<BugReportFile?>(null)

    init {
        val composeView = ComposeView(activity)
        composeView.setContent {
            RethinkTheme {
                BugReportFilesContent()
            }
        }
        dialog.setContentView(composeView)
        dialog.setOnShowListener {
            dialog.useTransparentNoDimBackground()
            dialog.window?.let { window ->
                if (Utilities.isAtleastQ()) {
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    controller.isAppearanceLightNavigationBars = false
                    window.isNavigationBarContrastEnforced = false
                }
            }
        }
        loadBugReportFiles()
    }

    fun show() {
        dialog.show()
    }

    private fun getThemeId(): Int {
        val isDark =
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return Themes.getBottomsheetCurrentTheme(isDark, persistentState.theme)
    }

    private fun loadBugReportFiles() {
        activity.lifecycleScope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    collectAllBugReportFiles()
                }

                bugReportFiles.clear()
                bugReportFiles.addAll(files)
            } catch (e: Exception) {
                Logger.e(LOG_TAG_UI, "err loading bug report: ${e.message}", e)
                showToastUiCentered(
                    activity,
                    activity.getString(R.string.bug_report_file_not_found),
                    Toast.LENGTH_SHORT
                )
            }
        }
    }

    private fun collectAllBugReportFiles(): List<BugReportFile> {
        val files = mutableListOf<BugReportFile>()
        val dir = activity.filesDir

        val bugReportZip = File(BugReportZipper.getZipFileName(dir))
        if (bugReportZip.exists() && bugReportZip.length() > 0) {
            files.add(
                BugReportFile(
                    file = bugReportZip,
                    name = bugReportZip.name,
                    type = FileType.ZIP,
                    isSelected = true
                )
            )
        }

        if (isAtleastO()) {
            val tombstoneZip = EnhancedBugReport.getTombstoneZipFile(activity)
            if (tombstoneZip != null && tombstoneZip.exists() && tombstoneZip.length() > 0) {
                files.add(
                    BugReportFile(
                        file = tombstoneZip,
                        name = tombstoneZip.name,
                        type = FileType.ZIP,
                        isSelected = true
                    )
                )
            }
        }

        val bugReportDir = File(dir, BugReportZipper.BUG_REPORT_DIR_NAME)
        if (bugReportDir.exists() && bugReportDir.isDirectory) {
            bugReportDir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 0) {
                    files.add(
                        BugReportFile(
                            file = file,
                            name = file.name,
                            type = getFileType(file),
                            isSelected = true
                        )
                    )
                }
            }
        }

        if (isAtleastO()) {
            val tombstoneDir = File(dir, EnhancedBugReport.TOMBSTONE_DIR_NAME)
            if (tombstoneDir.exists() && tombstoneDir.isDirectory) {
                tombstoneDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.length() > 0) {
                        files.add(
                            BugReportFile(
                                file = file,
                                name = file.name,
                                type = FileType.TEXT,
                                isSelected = true
                            )
                        )
                    }
                }
            }
        }

        return files.sortedByDescending { it.file.lastModified() }
    }

    private fun getFileType(file: File): FileType {
        return when (file.extension.lowercase()) {
            "zip" -> FileType.ZIP
            "txt", "log" -> FileType.TEXT
            else -> FileType.TEXT
        }
    }

    @Composable
    private fun BugReportFilesContent() {
        val scope = rememberCoroutineScope()
        val totalSize = bugReportFiles.filter { it.isSelected }.sumOf { it.file.length() }
        val hasSelection = bugReportFiles.any { it.isSelected }
        val allSelected = bugReportFiles.isNotEmpty() && bugReportFiles.all { it.isSelected }

        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            bugReportFiles.forEach { it.isSelected = checked }
                        }
                    )
                    Text(
                        text =
                            if (allSelected) {
                                activity.getString(R.string.bug_report_deselect_all)
                            } else {
                                activity.getString(R.string.lbl_select_all)
                                    .replaceFirstChar(Char::titlecase)
                            },
                        modifier = Modifier.clickable {
                            bugReportFiles.forEach { it.isSelected = !allSelected }
                        }
                    )
                }
                Text(
                    text = formatFileSize(totalSize),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (bugReportFiles.isEmpty()) {
                Text(text = activity.getString(R.string.bug_report_no_files_available))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(bugReportFiles, key = { it.file.absolutePath }) { item ->
                        BugReportFileRow(item)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSending) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = progressText)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        if (!isSending) {
                            scope.launch { sendBugReport() }
                        }
                    },
                    enabled = hasSelection && !isSending
                ) {
                    Text(text = activity.getString(R.string.about_bug_report_dialog_positive_btn))
                }
            }
        }

        pendingDelete?.let { fileItem ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(text = activity.getString(R.string.lbl_delete)) },
                text = {
                    Text(
                        text =
                            activity.getString(
                                R.string.bug_report_delete_confirmation,
                                fileItem.name
                            )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDelete = null
                            deleteFile(fileItem)
                        }
                    ) {
                        Text(text = activity.getString(R.string.lbl_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(text = activity.getString(R.string.lbl_cancel))
                    }
                }
            )
        }
    }

    @Composable
    private fun BugReportFileRow(fileItem: BugReportFile) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = fileItem.isSelected,
                onCheckedChange = { checked ->
                    fileItem.isSelected = checked
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileItem.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text =
                        "${formatFileSize(fileItem.file.length())} - ${formatDate(fileItem.file.lastModified())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { openFile(fileItem.file) }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_share),
                    contentDescription = null
                )
            }
            IconButton(onClick = { pendingDelete = fileItem }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    private suspend fun sendBugReport() {
        val selectedFiles = bugReportFiles.filter { it.isSelected }.map { it.file }

        if (selectedFiles.isEmpty()) {
            showToastUiCentered(
                activity,
                activity.getString(R.string.bug_report_no_files_selected),
                Toast.LENGTH_SHORT
            )
            return
        }

        isSending = true
        progressText = activity.getString(R.string.bug_report_creating_zip)

        try {
            val attachmentUri = withContext(Dispatchers.IO) {
                if (selectedFiles.size == 1) {
                    getFileUri(selectedFiles[0])
                } else {
                    createCombinedZip(selectedFiles)
                }
            }

            if (attachmentUri != null) {
                val emailIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(activity.getString(R.string.about_mail_to)))
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        activity.getString(R.string.about_mail_bugreport_subject)
                    )
                    putExtra(Intent.EXTRA_STREAM, attachmentUri)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                activity.startActivity(
                    Intent.createChooser(
                        emailIntent,
                        activity.getString(R.string.about_mail_bugreport_share_title)
                    )
                )
                dialog.dismiss()
            } else {
                showToastUiCentered(
                    activity,
                    activity.getString(R.string.error_loading_log_file),
                    Toast.LENGTH_SHORT
                )
            }
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "err sending bug report: ${e.message}", e)
            showToastUiCentered(
                activity,
                activity.getString(R.string.error_loading_log_file),
                Toast.LENGTH_SHORT
            )
        } finally {
            isSending = false
            progressText = ""
        }
    }

    private fun createCombinedZip(files: List<File>): Uri? {
        val tempDir = activity.cacheDir
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val zipFile = File(tempDir, "rethinkdns_bugreport_$timestamp.zip")

        try {
            val addedEntries = mutableSetOf<String>()

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                files.forEach { file ->
                    if (file.extension == "zip") {
                        ZipFile(file).use { zf ->
                            val entries = zf.entries()
                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement()
                                if (!entry.isDirectory && !addedEntries.contains(entry.name)) {
                                    addedEntries.add(entry.name)

                                    val newEntry = ZipEntry(entry.name)
                                    zos.putNextEntry(newEntry)
                                    zf.getInputStream(entry).use { input ->
                                        input.copyTo(zos)
                                    }
                                    zos.closeEntry()
                                }
                            }
                        }
                    } else {
                        if (!addedEntries.contains(file.name)) {
                            addedEntries.add(file.name)

                            val entry = ZipEntry(file.name)
                            zos.putNextEntry(entry)
                            FileInputStream(file).use { input ->
                                input.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }

            return getFileUri(zipFile)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "err creating combined zip: ${e.message}", e)
            zipFile.delete()
            return null
        }
    }

    private fun getFileUri(file: File): Uri? {
        return try {
            FileProvider.getUriForFile(
                activity,
                BugReportZipper.FILE_PROVIDER_NAME,
                file
            )
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "err getting file uri: ${e.message}", e)
            null
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < BYTES_IN_KB -> "$size B"
            size < BYTES_IN_MB -> "${size / BYTES_IN_KB} KB"
            else -> String.format(Locale.US, "%.1f MB", size / MB_DIVISOR)
        }
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date(timestamp))
    }

    private fun deleteFile(fileItem: BugReportFile) {
        activity.lifecycleScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    fileItem.file.delete()
                }

                if (deleted) {
                    bugReportFiles.remove(fileItem)

                    showToastUiCentered(
                        activity,
                        activity.getString(R.string.bug_report_file_deleted, fileItem.name),
                        Toast.LENGTH_SHORT
                    )

                    if (bugReportFiles.isEmpty()) {
                        showToastUiCentered(
                            activity,
                            activity.getString(R.string.bug_report_no_files_available),
                            Toast.LENGTH_SHORT
                        )
                        dialog.dismiss()
                    }
                } else {
                    showToastUiCentered(
                        activity,
                        activity.getString(R.string.bug_report_delete_failed, fileItem.name),
                        Toast.LENGTH_SHORT
                    )
                }
            } catch (e: Exception) {
                Logger.e(LOG_TAG_UI, "err deleting file: ${e.message}", e)
                showToastUiCentered(
                    activity,
                    activity.getString(R.string.bug_report_delete_failed, fileItem.name),
                    Toast.LENGTH_SHORT
                )
            }
        }
    }

    private fun openFile(file: File) {
        try {
            val uri = getFileUri(file) ?: return

            val mimeType = when (file.extension.lowercase()) {
                "zip" -> "application/zip"
                "txt", "log" -> "text/plain"
                else -> "text/plain"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.about_bug_report)))
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "err opening file: ${e.message}", e)
            showToastUiCentered(
                activity,
                activity.getString(R.string.bug_report_error_opening_file),
                Toast.LENGTH_SHORT
            )
        }
    }

    data class BugReportFile(
        val file: File,
        val name: String,
        val type: FileType,
        var isSelected: Boolean
    )

    enum class FileType {
        ZIP,
        TEXT
    }

    companion object {
        private const val BYTES_IN_KB = 1024L
        private const val BYTES_IN_MB = 1024L * 1024L
        private const val MB_DIVISOR = 1024.0 * 1024.0
    }
}
