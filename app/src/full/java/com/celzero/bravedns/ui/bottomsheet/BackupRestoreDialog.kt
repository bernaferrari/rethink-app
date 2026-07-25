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
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.fragment.app.FragmentActivity
import com.celzero.bravedns.util.workInfosByTagFlow
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
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.settings.RethinkBackupRestoreDialogCopy
import com.celzero.bravedns.ui.compose.settings.RethinkBackupRestoreFailure
import com.celzero.bravedns.ui.compose.settings.RethinkBackupRestoreSheet
import com.celzero.bravedns.ui.compose.settings.RethinkBackupRestoreStrings
import com.celzero.bravedns.ui.compose.firewall.RethinkRuleSheetModal
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.delay
import Logger
import Logger.LOG_TAG_UI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return
    val workManager = remember { WorkManager.getInstance(activity.applicationContext) }
    var versionText by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<RethinkBackupRestoreFailure?>(null) }

    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleBackupResult(
                activity,
                result,
                onFailure = { failure = RethinkBackupRestoreFailure.Backup },
                onBackup = { uri -> startBackupProcess(activity, uri, workManager) }
            )
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleRestoreResult(
                activity,
                result,
                onFailure = { failure = RethinkBackupRestoreFailure.Restore },
                onRestore = { uri -> startRestoreProcess(activity, uri, workManager) }
            )
        }

    LaunchedEffect(Unit) {
        versionText = showVersion(activity)
    }

    LaunchedEffect(workManager) {
        workManager.workInfosByTagFlow(BackupAgent.TAG).collect { workInfoList ->
            val workInfo = workInfoList.getOrNull(0) ?: return@collect
            Logger.i(LOG_TAG_UI, "WorkManager state: ${workInfo.state} for ${BackupAgent.TAG}")
            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    Utilities.showToastUiCentered(
                        activity,
                        activity.getString(R.string.brbs_backup_complete_toast),
                        Toast.LENGTH_SHORT
                    )
                    workManager.pruneWork()
                }
                WorkInfo.State.CANCELLED, WorkInfo.State.FAILED -> {
                    failure = RethinkBackupRestoreFailure.Backup
                    workManager.pruneWork()
                    workManager.cancelAllWorkByTag(BackupAgent.TAG)
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(workManager) {
        workManager.workInfosByTagFlow(RestoreAgent.TAG).collect { workInfoList ->
            val workInfo = workInfoList.getOrNull(0) ?: return@collect
            Logger.i(LOG_TAG_UI, "WorkManager state: ${workInfo.state} for ${RestoreAgent.TAG}")
            if (WorkInfo.State.SUCCEEDED == workInfo.state) {
                Utilities.showToastUiCentered(
                    activity,
                    activity.getString(R.string.brbs_restore_complete_toast),
                    Toast.LENGTH_LONG
                )
                delay(TimeUnit.MILLISECONDS.toMillis(1000), activity.lifecycleScope) {
                    restartApp(activity)
                }
                workManager.pruneWork()
            } else if (
                WorkInfo.State.CANCELLED == workInfo.state ||
                WorkInfo.State.FAILED == workInfo.state
            ) {
                failure = RethinkBackupRestoreFailure.Restore
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(RestoreAgent.TAG)
            }
        }
    }

    RethinkRuleSheetModal(onDismissRequest = onDismiss) {
        RethinkBackupRestoreSheet(
            versionText = versionText,
            strings = RethinkBackupRestoreStrings(
                title = stringResource(R.string.brbs_title),
                description = stringResource(R.string.brbs_backup_restore_desc),
                backupTitle = stringResource(R.string.brbs_backup_title),
                backupDescription = stringResource(R.string.brbs_backup_desc),
                restoreTitle = stringResource(R.string.brbs_restore_title),
                restoreDescription = stringResource(R.string.brbs_restore_desc),
                backupConfirmation = RethinkBackupRestoreDialogCopy(
                    stringResource(R.string.brbs_backup_dialog_title),
                    stringResource(R.string.brbs_backup_dialog_message),
                    stringResource(R.string.brbs_backup_dialog_positive),
                    stringResource(R.string.lbl_cancel),
                ),
                restoreConfirmation = RethinkBackupRestoreDialogCopy(
                    stringResource(R.string.brbs_restore_dialog_title),
                    stringResource(R.string.brbs_restore_dialog_message),
                    stringResource(R.string.brbs_restore_dialog_positive),
                    stringResource(R.string.lbl_cancel),
                ),
                backupFailure = RethinkBackupRestoreDialogCopy(
                    stringResource(R.string.brbs_backup_dialog_failure_title),
                    stringResource(R.string.brbs_backup_dialog_failure_message),
                    stringResource(R.string.brbs_backup_dialog_failure_positive),
                    stringResource(R.string.lbl_dismiss),
                ),
                restoreFailure = RethinkBackupRestoreDialogCopy(
                    stringResource(R.string.brbs_restore_dialog_failure_title),
                    stringResource(R.string.brbs_restore_dialog_failure_message),
                    stringResource(R.string.brbs_restore_dialog_failure_positive),
                    stringResource(R.string.lbl_dismiss),
                ),
            ),
            failure = failure,
            onBackup = { backup(activity, backupLauncher) },
            onRestore = { restore(activity, restoreLauncher) },
            onFailureDismiss = { failure = null },
            modifier = Modifier.padding(
                horizontal = Dimensions.screenPaddingHorizontal,
                vertical = Dimensions.spacingLg,
            ),
        )
    }
}

private fun showVersion(activity: FragmentActivity): String {
    val version = getVersionName(activity)
    return activity.getString(
        R.string.about_version_install_source,
        version,
        getDownloadSource(activity)
    )
}

private fun getVersionName(activity: FragmentActivity): String {
    val pInfo: PackageInfo? =
        Utilities.getPackageMetadata(activity.packageManager, activity.packageName)
    return pInfo?.versionName ?: ""
}

private fun getDownloadSource(activity: FragmentActivity): String {
    if (Utilities.isFdroidFlavour()) return activity.getString(R.string.build__flavor_fdroid)
    if (Utilities.isPlayStoreFlavour()) return activity.getString(R.string.build__flavor_play_store)
    return activity.getString(R.string.build__flavor_website)
}

private fun backup(
    activity: FragmentActivity,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    try {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = INTENT_TYPE_OCTET
        val sdf = SimpleDateFormat(BACKUP_FILE_NAME_DATETIME, Locale.ROOT)
        val version = getVersionName(activity).replace(' ', '_')
        val zipFileName: String =
            BACKUP_FILE_NAME + version + sdf.format(Date()) + BACKUP_FILE_EXTN

        intent.putExtra(Intent.EXTRA_TITLE, zipFileName)

        try {
            if (intent.resolveActivity(activity.packageManager) != null) {
                launcher.launch(intent)
            } else {
                Logger.e(LOG_TAG_UI, "No activity found to handle CREATE_DOCUMENT intent")
                Utilities.showToastUiCentered(
                    activity,
                    activity.getString(R.string.brbs_backup_dialog_failure_message),
                    Toast.LENGTH_LONG
                )
            }
        } catch (e: android.content.ActivityNotFoundException) {
            Logger.e(LOG_TAG_UI, "Activity not found for CREATE_DOCUMENT: ${e.message}")
            Utilities.showToastUiCentered(
                activity,
                activity.getString(R.string.brbs_backup_dialog_failure_message),
                Toast.LENGTH_LONG
            )
        }
    } catch (e: Exception) {
        Logger.e(LOG_TAG_UI, "err opening file picker for backup: ${e.message}")
        Utilities.showToastUiCentered(
            activity,
            activity.getString(R.string.brbs_backup_dialog_failure_message),
            Toast.LENGTH_LONG
        )
    }
}

private fun restore(
    activity: FragmentActivity,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    try {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        val mimeTypes = arrayOf(INTENT_TYPE_OCTET, INTENT_TYPE_XZIP)
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)

        launcher.launch(intent)
    } catch (e: Exception) {
        Logger.e(LOG_TAG_UI, "err opening file picker: ${e.message}")
        Utilities.showToastUiCentered(
            activity,
            activity.getString(R.string.blocklist_update_check_failure),
            Toast.LENGTH_SHORT
        )
    }
}

private fun handleBackupResult(
    activity: FragmentActivity,
    result: ActivityResult,
    onFailure: () -> Unit,
    onBackup: (Uri?) -> Unit
) {
    when (result.resultCode) {
        Activity.RESULT_OK -> {
            var backupFileUri: Uri? = null
            result.data?.also { uri -> backupFileUri = uri.data }
            Logger.i(LOG_TAG_UI, "activity result for backup process with uri: $backupFileUri")
            onBackup(backupFileUri)
        }
        Activity.RESULT_CANCELED -> {
            onFailure()
        }
        else -> {
            onFailure()
        }
    }
}

private fun handleRestoreResult(
    activity: FragmentActivity,
    result: ActivityResult,
    onFailure: () -> Unit,
    onRestore: (Uri?) -> Unit
) {
    when (result.resultCode) {
        Activity.RESULT_OK -> {
            var fileUri: Uri? = null
            result.data?.also { uri -> fileUri = uri.data }
            Logger.i(LOG_TAG_UI, "activity result for restore process with uri: $fileUri")
            onRestore(fileUri)
        }
        Activity.RESULT_CANCELED -> {
            onFailure()
        }
        else -> {
            onFailure()
        }
    }
}

private fun startRestoreProcess(activity: FragmentActivity, fileUri: Uri?, workManager: WorkManager) {
    if (fileUri == null) {
        Logger.w(LOG_TAG_UI, "uri received from activity result is null, cancel restore process")
        return
    }

    Logger.i(LOG_TAG_UI, "invoke worker to initiate the restore process")
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
    workManager.beginWith(importWorker).enqueue()
}

private fun startBackupProcess(activity: FragmentActivity, backupUri: Uri?, workManager: WorkManager) {
    if (backupUri == null) {
        Logger.w(LOG_TAG_UI, "uri received from activity result is null, cancel backup process")
        return
    }

    BackupHelper.stopVpn(activity)

    Logger.i(LOG_TAG_UI, "invoke worker to initiate the backup process")
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
    workManager.beginWith(downloadWatcher).enqueue()
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
