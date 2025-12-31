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

import Logger
import Logger.LOG_TAG_DNS
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.celzero.bravedns.R
import com.celzero.bravedns.customdownloader.LocalBlocklistCoordinator
import com.celzero.bravedns.databinding.BottomSheetLocalBlocklistsBinding
import com.celzero.bravedns.download.AppDownloadManager
import com.celzero.bravedns.download.DownloadConstants
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.RethinkBlocklistManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.activity.ConfigureRethinkBasicActivity
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.INIT_TIME_MS
import com.celzero.bravedns.util.Constants.Companion.LOCAL_BLOCKLIST_DOWNLOAD_FOLDER_NAME
import com.celzero.bravedns.util.Constants.Companion.RETHINK_SEARCH_URL
import com.celzero.bravedns.util.Themes.Companion.getBottomsheetCurrentTheme
import com.celzero.bravedns.util.UIUtils.clipboardCopy
import com.celzero.bravedns.util.UIUtils.fetchToggleBtnColors
import com.celzero.bravedns.util.UIUtils.openUrl
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.blocklistCanonicalPath
import com.celzero.bravedns.util.Utilities.convertLongToTime
import com.celzero.bravedns.util.Utilities.deleteRecursive
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class LocalBlocklistsDialog(
    private val activity: FragmentActivity,
    private val onDismiss: () -> Unit
) : KoinComponent {
    private val b = BottomSheetLocalBlocklistsBinding.inflate(LayoutInflater.from(activity))
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()
    private val appDownloadManager by inject<AppDownloadManager>()

    companion object {
        private const val BUTTON_ALPHA_DISABLED = 0.5f
    }

    init {
        dialog.setContentView(b.root)
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

        updateLocalBlocklistUi()
        init()
        initializeObservers()
        initializeClickListeners()
    }

    fun show() {
        dialog.show()
    }

    private fun getThemeId(): Int {
        val isDark =
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return getBottomsheetCurrentTheme(isDark, persistentState.theme)
    }

    private fun init() {
        if (persistentState.localBlocklistTimestamp == INIT_TIME_MS) {
            b.lbbsDownloadLl.visibility = View.GONE
            showCheckUpdateUi()
            return
        }

        b.lbbsDownloadLl.visibility = View.VISIBLE
        b.lbbsVersion.text =
            activity.getString(
                R.string.settings_local_blocklist_version,
                convertLongToTime(
                    persistentState.localBlocklistTimestamp,
                    Constants.TIME_FORMAT_2
                )
            )

        if (persistentState.newestRemoteBlocklistTimestamp == INIT_TIME_MS) {
            showCheckUpdateUi()
            return
        }

        if (
            persistentState.newestLocalBlocklistTimestamp > persistentState.localBlocklistTimestamp
        ) {
            showUpdateUi()
            return
        }

        showCheckUpdateUi()
    }

    private fun initializeObservers() {
        appDownloadManager.downloadRequired.observe(activity) {
            Logger.i(LOG_TAG_DNS, "Check for blocklist update, status: $it")
            if (it == null) return@observe

            handleDownloadStatus(it)
        }
    }

    private fun showCheckDownloadProgressUi() {
        b.lbbsCheckDownloadProgress.visibility = View.VISIBLE
        b.lbbsCheckDownloadImg.visibility = View.GONE
    }

    private fun onDownloadProgress() {
        b.lbbsDownloadProgress.visibility = View.VISIBLE
        b.lbbsRedownloadProgress.visibility = View.VISIBLE
        b.lbbsDownloadImg.visibility = View.GONE
        b.lbbsRedownloadImg.visibility = View.GONE
    }

    private fun onDownloadSuccess() {
        b.lbbsDownload.isEnabled = true
        b.lbbsRedownload.isEnabled = true
        b.lbbsDownloadProgress.visibility = View.GONE
        b.lbbsRedownloadProgress.visibility = View.GONE
        b.lbbsDownloadImg.visibility = View.VISIBLE
        b.lbbsRedownloadImg.visibility = View.VISIBLE
        Utilities.showToastUiCentered(
            activity,
            activity.getString(R.string.download_update_dialog_message_success),
            Toast.LENGTH_SHORT
        )
    }

    private fun onDownloadFail() {
        b.lbbsDownload.isEnabled = true
        b.lbbsRedownload.isEnabled = true
        b.lbbsDownloadProgress.visibility = View.GONE
        b.lbbsRedownloadProgress.visibility = View.GONE
        b.lbbsDownloadImg.visibility = View.VISIBLE
        b.lbbsRedownloadImg.visibility = View.VISIBLE
        Utilities.showToastUiCentered(
            activity,
            activity.getString(R.string.blocklist_update_check_failure),
            Toast.LENGTH_SHORT
        )
    }

    private fun showCheckUpdateUi() {
        b.lbbsCheckDownload.visibility = View.VISIBLE
        b.lbbsDownload.visibility = View.GONE
        b.lbbsRedownload.visibility = View.GONE

        b.lbbsCheckDownloadImg.visibility = View.VISIBLE
        b.lbbsCheckDownloadProgress.visibility = View.GONE
        b.lbbsDownloadProgress.visibility = View.GONE
        b.lbbsRedownloadProgress.visibility = View.GONE
    }

    private fun showUpdateUi() {
        b.lbbsCheckDownload.visibility = View.GONE
        b.lbbsDownload.visibility = View.VISIBLE
        b.lbbsRedownload.visibility = View.GONE

        b.lbbsDownloadImg.visibility = View.VISIBLE
        b.lbbsCheckDownloadProgress.visibility = View.GONE
        b.lbbsDownloadProgress.visibility = View.GONE
        b.lbbsRedownloadProgress.visibility = View.GONE
    }

    private fun showRedownloadUi() {
        b.lbbsCheckDownload.visibility = View.GONE
        b.lbbsDownload.visibility = View.GONE
        b.lbbsRedownload.visibility = View.VISIBLE

        b.lbbsRedownloadImg.visibility = View.VISIBLE
        b.lbbsCheckDownloadProgress.visibility = View.GONE
        b.lbbsDownloadProgress.visibility = View.GONE
        b.lbbsRedownloadProgress.visibility = View.GONE
    }

    private fun showDownloadDialog(isRedownload: Boolean) {
        val builder = MaterialAlertDialogBuilder(activity, R.style.App_Dialog_NoDim)
        if (isRedownload) {
            builder.setTitle(R.string.local_blocklist_redownload)
            builder.setMessage(
                activity.getString(
                    R.string.local_blocklist_redownload_desc,
                    convertLongToTime(
                        persistentState.localBlocklistTimestamp,
                        Constants.TIME_FORMAT_2
                    )
                )
            )
        } else {
            builder.setTitle(R.string.local_blocklist_download)
            builder.setMessage(R.string.local_blocklist_download_desc)
        }
        builder.setCancelable(false)
        builder.setPositiveButton(activity.getString(R.string.settings_local_blocklist_dialog_positive)) {
                _,
                _ ->
            downloadLocalBlocklist(isRedownload)
        }
        builder.setNegativeButton(activity.getString(R.string.lbl_cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    private fun showDeleteDialog() {
        val builder = MaterialAlertDialogBuilder(activity, R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.lbl_delete)
        builder.setMessage(activity.getString(R.string.local_blocklist_delete_desc))
        builder.setCancelable(false)
        builder.setPositiveButton(activity.getString(R.string.lbl_delete)) { _: DialogInterface, _: Int ->
            deleteLocalBlocklist()
        }
        builder.setNegativeButton(activity.getString(R.string.lbl_cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
    }

    private fun downloadLocalBlocklist(isRedownload: Boolean) {
        if (VpnController.isVpnLockdown() && !persistentState.useCustomDownloadManager) {
            showLockdownDownloadDialog(isRedownload)
            return
        }

        proceedWithDownload(isRedownload)
    }

    private fun showLockdownDownloadDialog(isRedownload: Boolean) {
        val builder = MaterialAlertDialogBuilder(activity, R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.lockdown_download_enable_inapp)
        builder.setMessage(R.string.lockdown_download_message)
        builder.setCancelable(true)
        builder.setPositiveButton(R.string.lockdown_download_enable_inapp) { _, _ ->
            persistentState.useCustomDownloadManager = true
            downloadLocalBlocklist(isRedownload)
        }
        builder.setNegativeButton(R.string.lbl_cancel) { dialog, _ ->
            dialog.dismiss()
            proceedWithDownload(isRedownload)
        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
    }

    private fun proceedWithDownload(isRedownload: Boolean) {
        ui {
            var status = AppDownloadManager.DownloadManagerStatus.NOT_STARTED
            b.lbbsDownload.isEnabled = false
            b.lbbsRedownload.isEnabled = false
            val currentTs = persistentState.localBlocklistTimestamp
            ioCtx { status = appDownloadManager.downloadLocalBlocklist(currentTs, isRedownload) }

            handleDownloadStatus(status)
        }
    }

    private fun deleteLocalBlocklist() {
        ui {
            b.lbbsDelete.isEnabled = false
            b.lbbsDownload.isEnabled = false
            b.lbbsRedownload.isEnabled = false
            b.lbbsCheckDownload.isEnabled = false

            ioCtx {
                val path =
                    blocklistCanonicalPath(activity, LOCAL_BLOCKLIST_DOWNLOAD_FOLDER_NAME)
                val dir = File(path)
                deleteRecursive(dir)
                persistentState.localBlocklistTimestamp = INIT_TIME_MS
                persistentState.localBlocklistStamp = ""
                persistentState.newestLocalBlocklistTimestamp = INIT_TIME_MS
            }

            updateLocalBlocklistUi()
            showCheckUpdateUi()
            Utilities.showToastUiCentered(
                activity,
                activity.getString(R.string.config_add_success_toast),
                Toast.LENGTH_SHORT
            )
        }
    }

    private fun handleDownloadStatus(status: AppDownloadManager.DownloadManagerStatus) {
        when (status) {
            AppDownloadManager.DownloadManagerStatus.IN_PROGRESS -> {
                ui { showCheckDownloadProgressUi() }
            }
            AppDownloadManager.DownloadManagerStatus.STARTED -> {
                ui {
                    observeWorkManager()
                    showCheckDownloadProgressUi()
                }
            }
            AppDownloadManager.DownloadManagerStatus.NOT_STARTED -> {
                // no-op
            }
            AppDownloadManager.DownloadManagerStatus.SUCCESS -> {
                ui {
                    showUpdateUi()
                    b.lbbsCheckDownload.isEnabled = true
                }
                appDownloadManager.downloadRequired.postValue(
                    AppDownloadManager.DownloadManagerStatus.NOT_STARTED
                )
            }
            AppDownloadManager.DownloadManagerStatus.FAILURE -> {
                ui {
                    b.lbbsCheckDownload.isEnabled = true
                    Utilities.showToastUiCentered(
                        activity,
                        activity.getString(R.string.blocklist_update_check_failure),
                        Toast.LENGTH_SHORT
                    )
                }
                appDownloadManager.downloadRequired.postValue(
                    AppDownloadManager.DownloadManagerStatus.NOT_STARTED
                )
                onDownloadFail()
            }
            AppDownloadManager.DownloadManagerStatus.NOT_REQUIRED -> {
                ui {
                    showRedownloadUi()
                    b.lbbsCheckDownload.isEnabled = true
                    Utilities.showToastUiCentered(
                        activity,
                        activity.getString(R.string.blocklist_update_check_not_required),
                        Toast.LENGTH_SHORT
                    )
                }
                appDownloadManager.downloadRequired.postValue(
                    AppDownloadManager.DownloadManagerStatus.NOT_STARTED
                )
            }
            AppDownloadManager.DownloadManagerStatus.NOT_AVAILABLE -> {
                Utilities.showToastUiCentered(
                    activity,
                    activity.getString(R.string.blocklist_not_available_toast),
                    Toast.LENGTH_SHORT
                )
            }
        }
    }

    private fun updateLocalBlocklistUi() {
        if (Utilities.isPlayStoreFlavour()) {
            return
        }

        if (persistentState.blocklistEnabled) {
            enableBlocklistUi()
            return
        }

        disableBlocklistUi()
    }

    private fun enableBlocklistUi() {
        b.lbbsEnable.text = activity.getString(R.string.lbbs_enabled)
        b.lbbsEnable.setTextColor(fetchToggleBtnColors(activity, R.color.accentGood))
        b.lbbsHeading.text =
            activity.getString(
                R.string.settings_local_blocklist_in_use,
                persistentState.numberOfLocalBlocklists.toString()
            )
        setDrawable(R.drawable.ic_tick, b.lbbsEnable)

        b.lbbsConfigure.isEnabled = true
        b.lbbsCopy.isEnabled = true
        b.lbbsSearch.isEnabled = true

        b.lbbsConfigure.alpha = 1f
        b.lbbsCopy.alpha = 1f
        b.lbbsSearch.alpha = 1f
    }

    private fun disableBlocklistUi() {
        b.lbbsEnable.text = activity.getString(R.string.lbl_disabled)
        b.lbbsEnable.setTextColor(fetchToggleBtnColors(activity, R.color.accentBad))
        b.lbbsHeading.text = activity.getString(R.string.lbbs_heading)
        setDrawable(R.drawable.ic_cross_accent, b.lbbsEnable)

        b.lbbsConfigure.isEnabled = false
        b.lbbsCopy.isEnabled = false
        b.lbbsSearch.isEnabled = false

        b.lbbsConfigure.alpha = BUTTON_ALPHA_DISABLED
        b.lbbsCopy.alpha = BUTTON_ALPHA_DISABLED
        b.lbbsSearch.alpha = BUTTON_ALPHA_DISABLED
    }

    private fun initializeClickListeners() {
        b.lbbsEnable.setOnClickListener { enableBlocklist() }

        b.lbbsConfigure.setOnClickListener { invokeRethinkActivity() }

        b.lbbsCopy.setOnClickListener {
            val url = Constants.RETHINK_BASE_URL_MAX + persistentState.localBlocklistStamp
            clipboardCopy(
                activity,
                url,
                activity.getString(R.string.copy_clipboard_label)
            )
            Utilities.showToastUiCentered(
                activity,
                activity.getString(R.string.info_dialog_rethink_toast_msg),
                Toast.LENGTH_SHORT
            )
        }

        b.lbbsSearch.setOnClickListener {
            dialog.dismiss()
            val url = RETHINK_SEARCH_URL + Uri.encode(persistentState.localBlocklistStamp)
            openUrl(activity, url)
        }

        b.lbbsDownload.setOnClickListener { showDownloadDialog(isRedownload = false) }

        b.lbbsCheckDownload.setOnClickListener {
            b.lbbsCheckDownload.isEnabled = false
            isBlocklistUpdateAvailable()
        }

        b.lbbsRedownload.setOnClickListener { showDownloadDialog(isRedownload = true) }

        b.lbbsDelete.setOnClickListener { showDeleteDialog() }
    }

    private fun isBlocklistUpdateAvailable() {
        io { appDownloadManager.isDownloadRequired(RethinkBlocklistManager.DownloadType.LOCAL) }
    }

    private fun enableBlocklist() {
        if (persistentState.blocklistEnabled) {
            removeBraveDnsLocal()
            updateLocalBlocklistUi()
            return
        }

        if (!VpnController.hasTunnel()) {
            Utilities.showToastUiCentered(
                activity,
                activity.getString(R.string.ssv_toast_start_rethink),
                Toast.LENGTH_SHORT
            )
            return
        }

        ui {
            val blocklistsExist =
                withContext(Dispatchers.Default) {
                    Utilities.hasLocalBlocklists(
                        activity,
                        persistentState.localBlocklistTimestamp
                    )
                }

            if (blocklistsExist) {
                setBraveDnsLocal()
                if (isLocalBlocklistStampAvailable()) {
                    updateLocalBlocklistUi()
                } else {
                    invokeRethinkActivity()
                }
            } else {
                invokeRethinkActivity()
            }
        }
    }

    private fun invokeRethinkActivity() {
        if (!VpnController.hasTunnel()) {
            Utilities.showToastUiCentered(
                activity,
                activity.getString(R.string.ssv_toast_start_rethink),
                Toast.LENGTH_SHORT
            )
            return
        }

        dialog.dismiss()
        val intent = Intent(activity, ConfigureRethinkBasicActivity::class.java)
        intent.putExtra(
            ConfigureRethinkBasicActivity.INTENT,
            ConfigureRethinkBasicActivity.FragmentLoader.LOCAL.ordinal
        )
        activity.startActivity(intent)
    }

    private fun isLocalBlocklistStampAvailable(): Boolean {
        return persistentState.localBlocklistStamp.isNotEmpty()
    }

    private fun setBraveDnsLocal() {
        persistentState.blocklistEnabled = true
    }

    private fun removeBraveDnsLocal() {
        persistentState.blocklistEnabled = false
    }

    private fun setDrawable(drawable: Int, txt: AppCompatTextView) {
        val end = ContextCompat.getDrawable(activity, drawable)
        txt.setCompoundDrawablesWithIntrinsicBounds(null, null, end, null)
    }

    private fun observeWorkManager() {
        val workManager = WorkManager.getInstance(activity.applicationContext)

        workManager.getWorkInfosByTagLiveData(LocalBlocklistCoordinator.CUSTOM_DOWNLOAD).observe(
            activity
        ) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            Logger.i(
                Logger.LOG_TAG_DOWNLOAD,
                "WorkManager state: ${workInfo.state} for ${LocalBlocklistCoordinator.CUSTOM_DOWNLOAD}"
            )
            if (
                WorkInfo.State.ENQUEUED == workInfo.state ||
                    WorkInfo.State.RUNNING == workInfo.state
            ) {
                onDownloadProgress()
            } else if (WorkInfo.State.SUCCEEDED == workInfo.state) {
                onDownloadSuccess()
                workManager.pruneWork()
            } else if (
                WorkInfo.State.CANCELLED == workInfo.state ||
                    WorkInfo.State.FAILED == workInfo.state
            ) {
                onDownloadFail()
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(LocalBlocklistCoordinator.CUSTOM_DOWNLOAD)
            }
        }

        workManager.getWorkInfosByTagLiveData(DownloadConstants.DOWNLOAD_TAG).observe(
            activity
        ) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            Logger.i(
                Logger.LOG_TAG_DOWNLOAD,
                "WorkManager state: ${workInfo.state} for ${DownloadConstants.DOWNLOAD_TAG}"
            )
            if (
                WorkInfo.State.ENQUEUED == workInfo.state ||
                    WorkInfo.State.RUNNING == workInfo.state
            ) {
                onDownloadProgress()
            } else if (
                WorkInfo.State.CANCELLED == workInfo.state ||
                    WorkInfo.State.FAILED == workInfo.state
            ) {
                onDownloadFail()
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(DownloadConstants.DOWNLOAD_TAG)
                workManager.cancelAllWorkByTag(DownloadConstants.FILE_TAG)
            }
        }

        workManager.getWorkInfosByTagLiveData(DownloadConstants.FILE_TAG).observe(
            activity
        ) { workInfoList ->
            if (workInfoList != null && workInfoList.isNotEmpty()) {
                val workInfo = workInfoList[0]
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    Logger.i(
                        Logger.LOG_TAG_DOWNLOAD,
                        "AppDownloadManager Work Manager completed - ${DownloadConstants.FILE_TAG}"
                    )
                    onDownloadSuccess()
                    workManager.pruneWork()
                } else if (
                    workInfo.state == WorkInfo.State.CANCELLED ||
                    workInfo.state == WorkInfo.State.FAILED
                ) {
                    onDownloadFail()
                    workManager.pruneWork()
                    workManager.cancelAllWorkByTag(DownloadConstants.FILE_TAG)
                    Logger.i(
                        Logger.LOG_TAG_DOWNLOAD,
                        "AppDownloadManager Work Manager failed - ${DownloadConstants.FILE_TAG}"
                    )
                } else {
                    Logger.i(
                        Logger.LOG_TAG_DOWNLOAD,
                        "AppDownloadManager Work Manager - ${DownloadConstants.FILE_TAG}, ${workInfo.state}"
                    )
                }
            }
        }
    }

    private fun ui(f: suspend () -> Unit) {
        activity.lifecycleScope.launch(Dispatchers.Main) { f() }
    }

    private suspend fun ioCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.IO) { f() }
    }

    private fun io(f: suspend () -> Unit) {
        activity.lifecycleScope.launch(Dispatchers.IO) { f() }
    }
}
