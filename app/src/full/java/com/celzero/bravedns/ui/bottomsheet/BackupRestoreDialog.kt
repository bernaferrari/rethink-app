/*
 * Copyright 2022 RethinkDNS and its authors
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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.celzero.bravedns.R
import com.celzero.bravedns.backup.BackupAgent
import com.celzero.bravedns.backup.BackupHelper
import com.celzero.bravedns.backup.BackupHelper.Companion.BACKUP_FILE_EXTN
import com.celzero.bravedns.backup.BackupHelper.Companion.BACKUP_FILE_NAME
import com.celzero.bravedns.backup.BackupHelper.Companion.BACKUP_FILE_NAME_DATETIME
import com.celzero.bravedns.backup.BackupHelper.Companion.DATA_BUILDER_BACKUP_URI
import com.celzero.bravedns.backup.BackupHelper.Companion.DATA_BUILDER_RESTORE_URI
import com.celzero.bravedns.backup.BackupHelper.Companion.INTENT_RESTART_APP
import com.celzero.bravedns.backup.BackupHelper.Companion.INTENT_TYPE_OCTET
import com.celzero.bravedns.backup.BackupHelper.Companion.INTENT_TYPE_XZIP
import com.celzero.bravedns.backup.RestoreAgent
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.delay
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.aakira.napier.Napier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BackupRestoreDialog(
    private val activity: androidx.fragment.app.FragmentActivity,
    private val backupLauncher: ActivityResultLauncher<Intent>,
    private val restoreLauncher: ActivityResultLauncher<Intent>,
    private val onDismiss: () -> Unit
) : KoinComponent {
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()

    private var versionText by mutableStateOf("")
    private var showBackupDialog by mutableStateOf(false)
    private var showRestoreDialog by mutableStateOf(false)
    private var showBackupFailureDialog by mutableStateOf(false)
    private var showRestoreFailureDialog by mutableStateOf(false)

    init {
        val composeView = ComposeView(activity)
        composeView.setContent {
            RethinkTheme {
                BackupRestoreContent()
            }
        }
        dialog.setContentView(composeView)
        dialog.setOnShowListener {
            dialog.useTransparentNoDimBackground()
            dialog.window?.let { window ->
                if (isAtleastQ()) {
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    controller.isAppearanceLightNavigationBars = false
                    window.isNavigationBarContrastEnforced = false
                }
            }
        }
        dialog.setOnDismissListener { onDismiss() }
        init()
    }

    fun show() {
        dialog.show()
    }

    fun handleBackupResult(result: ActivityResult) {
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                var backupFileUri: Uri? = null
                result.data?.also { uri -> backupFileUri = uri.data }
                Napier.i("activity result for backup process with uri: $backupFileUri")
                startBackupProcess(backupFileUri)
            }
            Activity.RESULT_CANCELED -> {
                showBackupFailureDialog = true
            }
            else -> {
                showBackupFailureDialog = true
            }
        }
    }

    fun handleRestoreResult(result: ActivityResult) {
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                var fileUri: Uri? = null
                result.data?.also { uri -> fileUri = uri.data }
                Napier.i("activity result for restore process with uri: $fileUri")
                startRestoreProcess(fileUri)
            }
            Activity.RESULT_CANCELED -> {
                showRestoreFailureDialog = true
            }
            else -> {
                showRestoreFailureDialog = true
            }
        }
    }

    private fun getThemeId(): Int {
        val isDark =
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return Themes.getBottomsheetCurrentTheme(isDark, persistentState.theme)
    }

    private fun init() {
        showVersion()
    }

    @Composable
    private fun BackupRestoreContent() {
        val borderColor = Color(UIUtils.fetchColor(activity, R.attr.border))
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(60.dp)
                        .height(3.dp)
                        .background(borderColor, RoundedCornerShape(2.dp))
            )

            Text(
                text = activity.getString(R.string.brbs_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                ActionRow(
                    icon = R.drawable.ic_backup,
                    title = activity.getString(R.string.brbs_backup_title),
                    description = activity.getString(R.string.brbs_backup_desc)
                ) {
                    showBackupDialog = true
                }
                Spacer(modifier = Modifier.height(12.dp))
                ActionRow(
                    icon = R.drawable.ic_restore,
                    title = activity.getString(R.string.brbs_restore_title),
                    description = activity.getString(R.string.brbs_restore_desc)
                ) {
                    showRestoreDialog = true
                }
            }

            Text(
                text = activity.getString(R.string.brbs_backup_restore_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )

            Text(
                text = versionText,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
        }

        if (showBackupDialog) {
            AlertDialog(
                onDismissRequest = { showBackupDialog = false },
                title = { Text(text = activity.getString(R.string.brbs_backup_dialog_title)) },
                text = { Text(text = activity.getString(R.string.brbs_backup_dialog_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBackupDialog = false
                            backup()
                        }
                    ) {
                        Text(text = activity.getString(R.string.brbs_backup_dialog_positive))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBackupDialog = false }) {
                        Text(text = activity.getString(R.string.lbl_cancel))
                    }
                }
            )
        }

        if (showRestoreDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreDialog = false },
                title = { Text(text = activity.getString(R.string.brbs_restore_dialog_title)) },
                text = { Text(text = activity.getString(R.string.brbs_restore_dialog_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRestoreDialog = false
                            restore()
                        }
                    ) {
                        Text(text = activity.getString(R.string.brbs_restore_dialog_positive))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreDialog = false }) {
                        Text(text = activity.getString(R.string.lbl_cancel))
                    }
                }
            )
        }

        if (showBackupFailureDialog) {
            AlertDialog(
                onDismissRequest = { showBackupFailureDialog = false },
                title = { Text(text = activity.getString(R.string.brbs_backup_dialog_failure_title)) },
                text = { Text(text = activity.getString(R.string.brbs_backup_dialog_failure_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBackupFailureDialog = false
                            backup()
                        }
                    ) {
                        Text(text = activity.getString(R.string.brbs_backup_dialog_failure_positive))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBackupFailureDialog = false }) {
                        Text(text = activity.getString(R.string.lbl_dismiss))
                    }
                }
            )
        }

        if (showRestoreFailureDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreFailureDialog = false },
                title = { Text(text = activity.getString(R.string.brbs_restore_dialog_failure_title)) },
                text = { Text(text = activity.getString(R.string.brbs_restore_dialog_failure_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRestoreFailureDialog = false
                            restore()
                        }
                    ) {
                        Text(text = activity.getString(R.string.brbs_restore_dialog_failure_positive))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreFailureDialog = false }) {
                        Text(text = activity.getString(R.string.lbl_dismiss))
                    }
                }
            )
        }
    }

    @Composable
    private fun ActionRow(icon: Int, title: String, description: String, onClick: () -> Unit) {
        Row(
            modifier =
                Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(32.dp).padding(4.dp)
            )
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Image(
                painter = painterResource(id = R.drawable.ic_right_arrow_white),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    private fun showVersion() {
        val version = getVersionName()
        versionText =
            activity.getString(
                R.string.about_version_install_source,
                version,
                getDownloadSource()
            )
    }

    private fun getVersionName(): String {
        val pInfo: PackageInfo? =
            Utilities.getPackageMetadata(activity.packageManager, activity.packageName)
        return pInfo?.versionName ?: ""
    }

    private fun getDownloadSource(): String {
        if (Utilities.isFdroidFlavour()) return activity.getString(R.string.build__flavor_fdroid)
        if (Utilities.isPlayStoreFlavour()) return activity.getString(R.string.build__flavor_play_store)
        return activity.getString(R.string.build__flavor_website)
    }

    private fun backup() {
        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = INTENT_TYPE_OCTET
            val sdf = SimpleDateFormat(BACKUP_FILE_NAME_DATETIME, Locale.ROOT)
            val version = getVersionName().replace(' ', '_')
            val zipFileName: String =
                BACKUP_FILE_NAME + version + sdf.format(Date()) + BACKUP_FILE_EXTN

            intent.putExtra(Intent.EXTRA_TITLE, zipFileName)

            try {
                if (intent.resolveActivity(activity.packageManager) != null) {
                    backupLauncher.launch(intent)
                } else {
                    Napier.e("No activity found to handle CREATE_DOCUMENT intent")
                    Utilities.showToastUiCentered(
                        activity,
                        activity.getString(R.string.brbs_backup_dialog_failure_message),
                        Toast.LENGTH_LONG
                    )
                }
            } catch (e: android.content.ActivityNotFoundException) {
                Napier.e("Activity not found for CREATE_DOCUMENT: ${e.message}")
                Utilities.showToastUiCentered(
                    activity,
                    activity.getString(R.string.brbs_backup_dialog_failure_message),
                    Toast.LENGTH_LONG
                )
            }
        } catch (e: Exception) {
            Napier.e("err opening file picker for backup: ${e.message}")
            Utilities.showToastUiCentered(
                activity,
                activity.getString(R.string.brbs_backup_dialog_failure_message),
                Toast.LENGTH_LONG
            )
        }
    }

    private fun restore() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            val mimeTypes = arrayOf(INTENT_TYPE_OCTET, INTENT_TYPE_XZIP)
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)

            restoreLauncher.launch(intent)
        } catch (e: Exception) {
            Napier.e("err opening file picker: ${e.message}")
            Utilities.showToastUiCentered(
                activity,
                activity.getString(R.string.blocklist_update_check_failure),
                Toast.LENGTH_SHORT
            )
        }
    }

    private fun startRestoreProcess(fileUri: Uri?) {
        if (fileUri == null) {
            Napier.w("uri received from activity result is null, cancel restore process")
            showRestoreFailureDialog = true
            return
        }

        Napier.i("invoke worker to initiate the restore process")
        val data = Data.Builder()
        data.putString(DATA_BUILDER_RESTORE_URI, fileUri.toString())

        val importWorker =
            OneTimeWorkRequestBuilder<RestoreAgent>()
                .setInputData(data.build())
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(RestoreAgent.TAG)
                .build()
        WorkManager.getInstance(activity).beginWith(importWorker).enqueue()
        observeRestoreWorker()
    }

    private fun startBackupProcess(backupUri: Uri?) {
        if (backupUri == null) {
            Napier.w("uri received from activity result is null, cancel backup process")
            showBackupFailureDialog = true
            return
        }

        BackupHelper.stopVpn(activity)

        Napier.i("invoke worker to initiate the backup process")
        val data = Data.Builder()
        data.putString(DATA_BUILDER_BACKUP_URI, backupUri.toString())
        val downloadWatcher =
            OneTimeWorkRequestBuilder<BackupAgent>()
                .setInputData(data.build())
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(BackupAgent.TAG)
                .build()
        WorkManager.getInstance(activity).beginWith(downloadWatcher).enqueue()
        observeBackupWorker()
    }

    private fun observeBackupWorker() {
        val workManager = WorkManager.getInstance(activity.applicationContext)

        workManager.getWorkInfosByTagLiveData(BackupAgent.TAG).observe(activity) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe

            Napier.i("WorkManager state: ${workInfo.state} for ${BackupAgent.TAG}")
            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    showBackupSuccessUi()
                    workManager.pruneWork()
                }
                WorkInfo.State.CANCELLED, WorkInfo.State.FAILED -> {
                    showBackupFailureDialog = true
                    workManager.pruneWork()
                    workManager.cancelAllWorkByTag(BackupAgent.TAG)
                }
                else -> {
                    // no-op
                }
            }
        }
    }

    private fun observeRestoreWorker() {
        val workManager = WorkManager.getInstance(activity.applicationContext)

        workManager.getWorkInfosByTagLiveData(RestoreAgent.TAG).observe(activity) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            Napier.i("WorkManager state: ${workInfo.state} for ${RestoreAgent.TAG}")
            if (WorkInfo.State.SUCCEEDED == workInfo.state) {
                showRestoreSuccessUi()
                workManager.pruneWork()
            } else if (
                WorkInfo.State.CANCELLED == workInfo.state ||
                    WorkInfo.State.FAILED == workInfo.state
            ) {
                showRestoreFailureDialog = true
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(RestoreAgent.TAG)
            }
        }
    }

    private fun showBackupSuccessUi() {
        Utilities.showToastUiCentered(
            activity,
            activity.getString(R.string.brbs_backup_complete_toast),
            Toast.LENGTH_SHORT
        )
    }

    private fun showRestoreSuccessUi() {
        Utilities.showToastUiCentered(
            activity,
            activity.getString(R.string.brbs_restore_complete_toast),
            Toast.LENGTH_LONG
        )
        delay(TimeUnit.MILLISECONDS.toMillis(1000), activity.lifecycleScope) { restartApp(activity) }
    }

    private fun restartApp(context: Context) {
        val packageManager: PackageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent!!.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        mainIntent.putExtra(INTENT_RESTART_APP, true)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }
}
