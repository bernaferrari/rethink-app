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

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.celzero.bravedns.R
import com.celzero.bravedns.customdownloader.LocalBlocklistCoordinator
import com.celzero.bravedns.download.AppDownloadManager
import com.celzero.bravedns.download.DownloadConstants
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.RethinkBlocklistManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.activity.ConfigureRethinkBasicActivity
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.INIT_TIME_MS
import com.celzero.bravedns.util.Constants.Companion.LOCAL_BLOCKLIST_DOWNLOAD_FOLDER_NAME
import com.celzero.bravedns.util.Constants.Companion.RETHINK_SEARCH_URL
import com.celzero.bravedns.util.Themes.Companion.getBottomsheetCurrentTheme
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.clipboardCopy
import com.celzero.bravedns.util.UIUtils.openUrl
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.blocklistCanonicalPath
import com.celzero.bravedns.util.Utilities.convertLongToTime
import com.celzero.bravedns.util.Utilities.deleteRecursive
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.aakira.napier.Napier
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
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()
    private val appDownloadManager by inject<AppDownloadManager>()

    private var showDownloadDialog by mutableStateOf(false)
    private var downloadDialogIsRedownload by mutableStateOf(false)
    private var showDeleteDialog by mutableStateOf(false)
    private var showLockdownDialog by mutableStateOf(false)

    private var enableLabel by mutableStateOf("")
    private var enableColor by mutableStateOf(Color.Unspecified)
    private var headingText by mutableStateOf("")
    private var versionText by mutableStateOf("")
    private var canConfigure by mutableStateOf(false)
    private var canCopy by mutableStateOf(false)
    private var canSearch by mutableStateOf(false)
    private var showCheckDownload by mutableStateOf(true)
    private var showDownload by mutableStateOf(false)
    private var showRedownload by mutableStateOf(false)
    private var isChecking by mutableStateOf(false)
    private var isDownloading by mutableStateOf(false)
    private var isRedownloading by mutableStateOf(false)

    companion object {
        private const val BUTTON_ALPHA_DISABLED = 0.5f
    }

    init {
        val composeView = ComposeView(activity)
        composeView.setContent {
            RethinkTheme {
                LocalBlocklistsContent()
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

        updateLocalBlocklistUi()
        init()
        initializeObservers()
        observeWorkManager()
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
            showCheckUpdateUi()
            versionText = ""
            return
        }

        versionText =
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

        if (persistentState.newestLocalBlocklistTimestamp > persistentState.localBlocklistTimestamp) {
            showUpdateUi()
            return
        }

        showCheckUpdateUi()
    }

    private fun initializeObservers() {
        appDownloadManager.downloadRequired.observe(activity) {
            Napier.i("Check for blocklist update, status: $it")
            if (it == null) return@observe

            handleDownloadStatus(it)
        }
    }

    @Composable
    private fun LocalBlocklistsContent() {
        val borderColor = Color(UIUtils.fetchColor(activity, R.attr.border))

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(60.dp)
                        .height(3.dp)
                        .background(borderColor, RoundedCornerShape(2.dp))
            )

            Text(
                text = headingText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = enableLabel,
                    color = enableColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { enableBlocklist() }) {
                    Text(text = activity.getString(R.string.lbl_apply))
                }
            }

            if (versionText.isNotEmpty()) {
                Text(
                    text = versionText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    text = activity.getString(R.string.lbl_configure),
                    enabled = canConfigure,
                    modifier = Modifier.weight(1f)
                ) {
                    invokeRethinkActivity()
                }
                ActionButton(
                    text = activity.getString(R.string.lbbs_copy),
                    enabled = canCopy,
                    modifier = Modifier.weight(1f)
                ) {
                    val url = Constants.RETHINK_BASE_URL_MAX + persistentState.localBlocklistStamp
                    clipboardCopy(activity, url, activity.getString(R.string.copy_clipboard_label))
                    Utilities.showToastUiCentered(
                        activity,
                        activity.getString(R.string.info_dialog_rethink_toast_msg),
                        Toast.LENGTH_SHORT
                    )
                }
                ActionButton(
                    text = activity.getString(R.string.lbl_search),
                    enabled = canSearch,
                    modifier = Modifier.weight(1f)
                ) {
                    dialog.dismiss()
                    val url = RETHINK_SEARCH_URL + Uri.encode(persistentState.localBlocklistStamp)
                    openUrl(activity, url)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showCheckDownload) {
                    DownloadRow(
                        label = activity.getString(R.string.lbbs_update_check),
                        isLoading = isChecking,
                        enabled = !isChecking
                    ) {
                        isChecking = true
                        isBlocklistUpdateAvailable()
                    }
                }
                if (showDownload) {
                    DownloadRow(
                        label = activity.getString(R.string.local_blocklist_download),
                        isLoading = isDownloading,
                        enabled = !isDownloading
                    ) {
                        downloadDialogIsRedownload = false
                        showDownloadDialog = true
                    }
                }
                if (showRedownload) {
                    DownloadRow(
                        label = activity.getString(R.string.local_blocklist_redownload),
                        isLoading = isRedownloading,
                        enabled = !isRedownloading
                    ) {
                        downloadDialogIsRedownload = true
                        showDownloadDialog = true
                    }
                }
                DownloadRow(
                    label = activity.getString(R.string.lbl_delete),
                    isLoading = false,
                    enabled = true
                ) {
                    showDeleteDialog = true
                }
            }
        }

        if (showDownloadDialog) {
            val title =
                if (downloadDialogIsRedownload) {
                    activity.getString(R.string.local_blocklist_redownload)
                } else {
                    activity.getString(R.string.local_blocklist_download)
                }
            val message =
                if (downloadDialogIsRedownload) {
                    activity.getString(
                        R.string.local_blocklist_redownload_desc,
                        convertLongToTime(
                            persistentState.localBlocklistTimestamp,
                            Constants.TIME_FORMAT_2
                        )
                    )
                } else {
                    activity.getString(R.string.local_blocklist_download_desc)
                }
            AlertDialog(
                onDismissRequest = { showDownloadDialog = false },
                title = { Text(text = title) },
                text = { Text(text = message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDownloadDialog = false
                            downloadLocalBlocklist(downloadDialogIsRedownload)
                        }
                    ) {
                        Text(text = activity.getString(R.string.settings_local_blocklist_dialog_positive))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadDialog = false }) {
                        Text(text = activity.getString(R.string.lbl_cancel))
                    }
                }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(text = activity.getString(R.string.lbl_delete)) },
                text = { Text(text = activity.getString(R.string.local_blocklist_delete_desc)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            deleteLocalBlocklist()
                        }
                    ) {
                        Text(text = activity.getString(R.string.lbl_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(text = activity.getString(R.string.lbl_cancel))
                    }
                }
            )
        }

        if (showLockdownDialog) {
            AlertDialog(
                onDismissRequest = { showLockdownDialog = false },
                title = { Text(text = activity.getString(R.string.lockdown_download_enable_inapp)) },
                text = { Text(text = activity.getString(R.string.lockdown_download_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLockdownDialog = false
                            persistentState.useCustomDownloadManager = true
                            downloadLocalBlocklist(downloadDialogIsRedownload)
                        }
                    ) {
                        Text(text = activity.getString(R.string.lockdown_download_enable_inapp))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showLockdownDialog = false
                            proceedWithDownload(downloadDialogIsRedownload)
                        }
                    ) {
                        Text(text = activity.getString(R.string.lbl_cancel))
                    }
                }
            )
        }
    }

    @Composable
    private fun DownloadRow(label: String, isLoading: Boolean, enabled: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                TextButton(onClick = onClick, enabled = enabled) {
                    Text(text = activity.getString(R.string.lbl_proceed))
                }
            }
        }
    }

    @Composable
    private fun ActionButton(
        text: String,
        enabled: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        val alpha = if (enabled) 1f else BUTTON_ALPHA_DISABLED
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(36.dp).alpha(alpha),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Text(text = text, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }

    private fun showCheckUpdateUi() {
        showCheckDownload = true
        showDownload = false
        showRedownload = false
        isChecking = false
        isDownloading = false
        isRedownloading = false
    }

    private fun showUpdateUi() {
        showCheckDownload = false
        showDownload = true
        showRedownload = false
        isChecking = false
        isDownloading = false
        isRedownloading = false
    }

    private fun showRedownloadUi() {
        showCheckDownload = false
        showDownload = false
        showRedownload = true
        isChecking = false
        isDownloading = false
        isRedownloading = false
    }

    private fun downloadLocalBlocklist(isRedownload: Boolean) {
        if (VpnController.isVpnLockdown() && !persistentState.useCustomDownloadManager) {
            showLockdownDialog = true
            return
        }

        proceedWithDownload(isRedownload)
    }

    private fun proceedWithDownload(isRedownload: Boolean) {
        ui {
            var status = AppDownloadManager.DownloadManagerStatus.NOT_STARTED
            isDownloading = !isRedownload
            isRedownloading = isRedownload
            val currentTs = persistentState.localBlocklistTimestamp
            ioCtx { status = appDownloadManager.downloadLocalBlocklist(currentTs, isRedownload) }
            handleDownloadStatus(status)
        }
    }

    private fun deleteLocalBlocklist() {
        ui {
            ioCtx {
                val path = blocklistCanonicalPath(activity, LOCAL_BLOCKLIST_DOWNLOAD_FOLDER_NAME)
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
                isChecking = true
            }
            AppDownloadManager.DownloadManagerStatus.STARTED -> {
                isChecking = true
            }
            AppDownloadManager.DownloadManagerStatus.NOT_STARTED -> {
                // no-op
            }
            AppDownloadManager.DownloadManagerStatus.SUCCESS -> {
                showUpdateUi()
                isChecking = false
                isDownloading = false
                isRedownloading = false
                appDownloadManager.downloadRequired.postValue(
                    AppDownloadManager.DownloadManagerStatus.NOT_STARTED
                )
            }
            AppDownloadManager.DownloadManagerStatus.FAILURE -> {
                isChecking = false
                isDownloading = false
                isRedownloading = false
                Utilities.showToastUiCentered(
                    activity,
                    activity.getString(R.string.blocklist_update_check_failure),
                    Toast.LENGTH_SHORT
                )
                appDownloadManager.downloadRequired.postValue(
                    AppDownloadManager.DownloadManagerStatus.NOT_STARTED
                )
            }
            AppDownloadManager.DownloadManagerStatus.NOT_REQUIRED -> {
                showRedownloadUi()
                isChecking = false
                Utilities.showToastUiCentered(
                    activity,
                    activity.getString(R.string.blocklist_update_check_not_required),
                    Toast.LENGTH_SHORT
                )
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
        enableLabel = activity.getString(R.string.lbbs_enabled)
        enableColor = Color(UIUtils.fetchToggleBtnColors(activity, R.color.accentGood))
        headingText =
            activity.getString(
                R.string.settings_local_blocklist_in_use,
                persistentState.numberOfLocalBlocklists.toString()
            )

        canConfigure = true
        canCopy = true
        canSearch = true
    }

    private fun disableBlocklistUi() {
        enableLabel = activity.getString(R.string.lbl_disabled)
        enableColor = Color(UIUtils.fetchToggleBtnColors(activity, R.color.accentBad))
        headingText = activity.getString(R.string.lbbs_heading)

        canConfigure = false
        canCopy = false
        canSearch = false
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

    private fun observeWorkManager() {
        val workManager = WorkManager.getInstance(activity.applicationContext)

        workManager.getWorkInfosByTagLiveData(LocalBlocklistCoordinator.CUSTOM_DOWNLOAD).observe(
            activity
        ) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            Napier.i("WorkManager state: ${workInfo.state} for ${LocalBlocklistCoordinator.CUSTOM_DOWNLOAD}")
            if (workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING) {
                isDownloading = true
            } else if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                isDownloading = false
                showUpdateUi()
                workManager.pruneWork()
            } else if (workInfo.state == WorkInfo.State.CANCELLED || workInfo.state == WorkInfo.State.FAILED) {
                isDownloading = false
                Utilities.showToastUiCentered(
                    activity,
                    activity.getString(R.string.blocklist_update_check_failure),
                    Toast.LENGTH_SHORT
                )
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(LocalBlocklistCoordinator.CUSTOM_DOWNLOAD)
            }
        }

        workManager.getWorkInfosByTagLiveData(DownloadConstants.DOWNLOAD_TAG).observe(
            activity
        ) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            Napier.i("WorkManager state: ${workInfo.state} for ${DownloadConstants.DOWNLOAD_TAG}")
            if (workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING) {
                isDownloading = true
            } else if (workInfo.state == WorkInfo.State.CANCELLED || workInfo.state == WorkInfo.State.FAILED) {
                isDownloading = false
                Utilities.showToastUiCentered(
                    activity,
                    activity.getString(R.string.blocklist_update_check_failure),
                    Toast.LENGTH_SHORT
                )
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(DownloadConstants.DOWNLOAD_TAG)
                workManager.cancelAllWorkByTag(DownloadConstants.FILE_TAG)
            }
        }

        workManager.getWorkInfosByTagLiveData(DownloadConstants.FILE_TAG).observe(
            activity
        ) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                isDownloading = false
                showUpdateUi()
                workManager.pruneWork()
            } else if (workInfo.state == WorkInfo.State.CANCELLED || workInfo.state == WorkInfo.State.FAILED) {
                isDownloading = false
                Utilities.showToastUiCentered(
                    activity,
                    activity.getString(R.string.blocklist_update_check_failure),
                    Toast.LENGTH_SHORT
                )
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(DownloadConstants.FILE_TAG)
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
