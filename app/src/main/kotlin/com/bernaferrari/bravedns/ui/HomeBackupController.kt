/*
 * Copyright 2026 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.ui

import Logger
import Logger.LOG_TAG_BACKUP_RESTORE
import Logger.LOG_TAG_UI
import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.backup.BackupHelper
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.BACKUP_FILE_EXTN
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.INTENT_RESTART_APP
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.INTENT_SCHEME
import com.bernaferrari.bravedns.backup.RestoreAgent
import com.bernaferrari.bravedns.backup.RestoreWork
import com.bernaferrari.bravedns.util.QrCodeFromFileScanner
import com.bernaferrari.bravedns.util.TunnelImporter
import com.bernaferrari.bravedns.util.Utilities.showToastUiCentered
import com.google.zxing.qrcode.QRCodeReader
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Backup/restore deep-link handling, restore worker, and WireGuard file/QR import.
 *
 * File pickers live here (Activity Result API). Restore confirmation is an Activity
 * dialog; this controller starts the worker after the user confirms.
 */
internal class HomeBackupController(
    private val activity: HomeScreenActivity,
    private val onRestorePrompt: (Uri) -> Unit,
    private val onRestartRefresh: () -> Unit,
) {

    private val tunnelFileImportResultLauncher =
        activity.registerForActivityResult(ActivityResultContracts.GetContent()) { data ->
            if (data == null) return@registerForActivityResult
            val contentResolver = activity.contentResolver ?: return@registerForActivityResult
            activity.lifecycleScope.launch {
                if (QrCodeFromFileScanner.validContentType(contentResolver, data)) {
                    try {
                        val qrCodeFromFileScanner =
                            QrCodeFromFileScanner(contentResolver, QRCodeReader())
                        val result = qrCodeFromFileScanner.scan(data)
                        if (result != null) {
                            withContext(Dispatchers.Main) {
                                TunnelImporter.importTunnel(result.text) {
                                    showToastUiCentered(
                                        activity,
                                        it.toString(),
                                        Toast.LENGTH_LONG
                                    )
                                }
                            }
                        } else {
                            val message = activity.resources.getString(R.string.invalid_file_error)
                            showToastUiCentered(activity, message, Toast.LENGTH_LONG)
                        }
                    } catch (e: Exception) {
                        Logger.e(LOG_TAG_UI, "wg file qr scan failed: ${e.message}", e)
                        val message = activity.resources.getString(R.string.invalid_file_error)
                        showToastUiCentered(activity, message, Toast.LENGTH_LONG)
                    }
                } else {
                    TunnelImporter.importTunnel(contentResolver, data) {
                        showToastUiCentered(
                            activity,
                            it.toString(),
                            Toast.LENGTH_LONG
                        )
                    }
                }
            }
        }

    private val qrImportResultLauncher =
        activity.registerForActivityResult(ScanContract()) { result ->
            val qrCode = result.contents
            if (qrCode != null) {
                activity.lifecycleScope.launch {
                    TunnelImporter.importTunnel(qrCode) {
                        showToastUiCentered(
                            activity,
                            it.toString(),
                            Toast.LENGTH_LONG
                        )
                    }
                }
            }
        }

    fun handleIncomingIntent() {
        val intent = activity.intent ?: return
        if (
            intent.scheme?.equals(INTENT_SCHEME) == true &&
            intent.data?.path?.contains(BACKUP_FILE_EXTN) == true
        ) {
            handleRestoreProcess(intent.data)
        } else if (intent.scheme?.equals(INTENT_SCHEME) == true) {
            showToastUiCentered(
                activity,
                activity.getString(R.string.brbs_restore_no_uri_toast),
                Toast.LENGTH_SHORT
            )
        } else if (intent.getBooleanExtra(INTENT_RESTART_APP, false)) {
            Logger.i(LOG_TAG_UI, "Restart from restore, so refreshing app database...")
            onRestartRefresh()
        }
    }

    fun startRestore(fileUri: Uri) {
        Logger.i(LOG_TAG_BACKUP_RESTORE, "invoke worker to initiate the restore process")
        RestoreWork.enqueue(WorkManager.getInstance(activity), fileUri)
    }

    fun observeRestoreWorker() {
        val workManager = WorkManager.getInstance(activity.applicationContext)

        activity.lifecycleScope.launch {
            RestoreWork.observe(workManager).collect { workInfo ->
                workInfo ?: return@collect
                Logger.i(
                    LOG_TAG_BACKUP_RESTORE,
                    "WorkManager state: ${workInfo.state} for ${RestoreAgent.TAG}"
                )
                if (WorkInfo.State.SUCCEEDED == workInfo.state) {
                    if (!com.bernaferrari.bravedns.backup.RestoreRestart.required(activity)) return@collect
                    showToastUiCentered(
                        activity,
                        activity.getString(R.string.brbs_restore_complete_toast),
                        Toast.LENGTH_SHORT
                    )
                    workManager.pruneWork()
                    com.bernaferrari.bravedns.backup.RestoreRestart.restart(activity)
                } else if (
                    WorkInfo.State.CANCELLED == workInfo.state ||
                    WorkInfo.State.FAILED == workInfo.state
                ) {
                    showToastUiCentered(
                        activity,
                        activity.getString(R.string.brbs_restore_no_uri_toast),
                        Toast.LENGTH_SHORT
                    )
                    workManager.pruneWork()
                    workManager.cancelAllWorkByTag(RestoreAgent.TAG)
                    if (com.bernaferrari.bravedns.backup.RestoreRestart.required(activity) || workInfo.outputData.getBoolean(RestoreAgent.RESTART_REQUIRED, false)) {
                        com.bernaferrari.bravedns.backup.RestoreRestart.restart(activity)
                    }
                }
            }
        }
    }

    fun launchFileImport() {
        try {
            tunnelFileImportResultLauncher.launch("*/*")
        } catch (e: ActivityNotFoundException) {
            Logger.e(LOG_TAG_UI, "no activity for wg file import: ${e.message}", e)
            showToastUiCentered(
                activity,
                activity.getString(R.string.blocklist_update_check_failure),
                Toast.LENGTH_SHORT
            )
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "wg file import failed: ${e.message}", e)
            showToastUiCentered(
                activity,
                activity.getString(R.string.blocklist_update_check_failure),
                Toast.LENGTH_SHORT
            )
        }
    }

    fun launchQrScanner() {
        try {
            qrImportResultLauncher.launch(
                ScanOptions()
                    .setOrientationLocked(false)
                    .setBeepEnabled(false)
                    .setPrompt(activity.resources.getString(R.string.lbl_qr_code))
            )
        } catch (e: ActivityNotFoundException) {
            Logger.e(LOG_TAG_UI, "no activity for wg qr scan: ${e.message}", e)
            showToastUiCentered(
                activity,
                activity.getString(R.string.blocklist_update_check_failure),
                Toast.LENGTH_SHORT
            )
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "wg qr scan failed: ${e.message}", e)
            showToastUiCentered(
                activity,
                activity.getString(R.string.blocklist_update_check_failure),
                Toast.LENGTH_SHORT
            )
        }
    }

    private fun handleRestoreProcess(uri: Uri?) {
        if (uri == null) {
            showToastUiCentered(
                activity,
                activity.getString(R.string.brbs_restore_no_uri_toast),
                Toast.LENGTH_SHORT
            )
            return
        }
        onRestorePrompt(uri)
    }
}
