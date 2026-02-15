/*
 * Copyright 2020 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.dns


import Logger
import Logger.LOG_TAG_DNS
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.celzero.bravedns.R
import com.celzero.bravedns.customdownloader.LocalBlocklistCoordinator
import com.celzero.bravedns.data.AppConfig.Companion.DOH_INDEX
import com.celzero.bravedns.data.AppConfig.Companion.DOT_INDEX
import com.celzero.bravedns.download.AppDownloadManager
import com.celzero.bravedns.download.DownloadConstants
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.INIT_TIME_MS
import com.celzero.bravedns.util.Constants.Companion.LOCAL_BLOCKLIST_DOWNLOAD_FOLDER_NAME
import com.celzero.bravedns.util.Constants.Companion.RETHINK_SEARCH_URL
import com.celzero.bravedns.util.ResourceRecordTypes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.blocklistCanonicalPath
import com.celzero.bravedns.util.Utilities.convertLongToTime
import com.celzero.bravedns.util.Utilities.deleteRecursive
import com.celzero.bravedns.util.Utilities.tos
import com.celzero.firestack.backend.Backend
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val BUTTON_ALPHA_DISABLED = 0.5f

/**
 * DNS Detail Screen - A composable screen that shows DNS settings and configuration.
 * This is the Compose equivalent of DnsDetailActivity.
 *
 * @param viewModel The DnsSettingsViewModel for managing DNS settings state
 * @param persistentState The PersistentState for accessing app preferences
 * @param appDownloadManager The AppDownloadManager for handling blocklist downloads
 * @param onCustomDnsClick Callback when custom DNS is clicked (navigates to DNS list)
 * @param onRethinkPlusDnsClick Callback when Rethink Plus DNS is clicked
 * @param onLocalBlocklistConfigureClick Callback when local blocklist configure is clicked
 * @param onBackClick Optional callback for back navigation
 */
@Composable
fun DnsDetailScreen(
    viewModel: DnsSettingsViewModel,
    persistentState: PersistentState,
    appDownloadManager: AppDownloadManager,
    onCustomDnsClick: () -> Unit,
    onRethinkPlusDnsClick: () -> Unit,
    onLocalBlocklistConfigureClick: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Dialog/Sheet state
    var showRecordTypesSheet by remember { mutableStateOf(false) }
    var showSystemDnsDialog by remember { mutableStateOf(false) }
    var systemDnsDialogText by remember { mutableStateOf("") }
    var showSmartDnsDialog by remember { mutableStateOf(false) }
    var smartDnsDialogText by remember { mutableStateOf("") }
    var showLocalBlocklistsSheet by remember { mutableStateOf(false) }

    // Local blocklist state
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadDialogIsRedownload by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLockdownDialog by remember { mutableStateOf(false) }

    var enableLabel by remember { mutableStateOf("") }
    var enableColor by remember { mutableStateOf(Color.Unspecified) }
    var headingText by remember { mutableStateOf("") }
    var versionText by remember { mutableStateOf("") }
    var canConfigure by remember { mutableStateOf(false) }
    var canCopy by remember { mutableStateOf(false) }
    var canSearch by remember { mutableStateOf(false) }
    var showCheckDownload by remember { mutableStateOf(true) }
    var showDownload by remember { mutableStateOf(false) }
    var showRedownload by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isRedownloading by remember { mutableStateOf(false) }
    val enabledBlocklistColor = MaterialTheme.colorScheme.tertiary
    val disabledBlocklistColor = MaterialTheme.colorScheme.error

    // Helper functions for local blocklist UI state
    fun showCheckUpdateUi() {
        showCheckDownload = true
        showDownload = false
        showRedownload = false
        isChecking = false
        isDownloading = false
        isRedownloading = false
    }

    fun showUpdateUi() {
        showCheckDownload = false
        showDownload = true
        showRedownload = false
        isChecking = false
        isDownloading = false
        isRedownloading = false
    }

    fun showRedownloadUi() {
        showCheckDownload = false
        showDownload = false
        showRedownload = true
        isChecking = false
        isDownloading = false
        isRedownloading = false
    }

    fun enableBlocklistUi() {
        enableLabel = context.resources.getString(R.string.lbbs_enabled)
        enableColor = enabledBlocklistColor
        headingText = context.resources.getString(
            R.string.settings_local_blocklist_in_use,
            persistentState.numberOfLocalBlocklists.toString()
        )
        canConfigure = true
        canCopy = true
        canSearch = true
    }

    fun disableBlocklistUi() {
        enableLabel = context.resources.getString(R.string.lbl_disabled)
        enableColor = disabledBlocklistColor
        headingText = context.resources.getString(R.string.lbbs_heading)
        canConfigure = false
        canCopy = false
        canSearch = false
    }

    fun updateLocalBlocklistUi() {
        if (Utilities.isPlayStoreFlavour()) {
            return
        }

        if (persistentState.blocklistEnabled) {
            enableBlocklistUi()
            return
        }

        disableBlocklistUi()
    }

    fun initLocalBlocklistVersion() {
        if (persistentState.localBlocklistTimestamp == INIT_TIME_MS) {
            showCheckUpdateUi()
            versionText = ""
            return
        }

        versionText = context.resources.getString(
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

    fun handleDownloadStatus(status: AppDownloadManager.DownloadManagerStatus) {
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
                    context,
                    context.resources.getString(R.string.blocklist_update_check_failure),
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
                    context,
                    context.resources.getString(R.string.blocklist_update_check_not_required),
                    Toast.LENGTH_SHORT
                )
                appDownloadManager.downloadRequired.postValue(
                    AppDownloadManager.DownloadManagerStatus.NOT_STARTED
                )
            }
            AppDownloadManager.DownloadManagerStatus.NOT_AVAILABLE -> {
                Utilities.showToastUiCentered(
                    context,
                    context.resources.getString(R.string.blocklist_not_available_toast),
                    Toast.LENGTH_SHORT
                )
            }
        }
    }

    fun dismissLocalBlocklistsSheet() {
        showLocalBlocklistsSheet = false
        viewModel.updateUiState()
    }

    fun proceedWithDownload(isRedownload: Boolean) {
        scope.launch(Dispatchers.Main) {
            var status = AppDownloadManager.DownloadManagerStatus.NOT_STARTED
            isDownloading = !isRedownload
            isRedownloading = isRedownload
            val currentTs = persistentState.localBlocklistTimestamp
            withContext(Dispatchers.IO) {
                status = appDownloadManager.downloadLocalBlocklist(currentTs, isRedownload)
            }
            handleDownloadStatus(status)
        }
    }

    fun downloadLocalBlocklist(isRedownload: Boolean) {
        if (VpnController.isVpnLockdown() && !persistentState.useCustomDownloadManager) {
            showLockdownDialog = true
            return
        }
        proceedWithDownload(isRedownload)
    }

    fun deleteLocalBlocklist() {
        scope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                val path = blocklistCanonicalPath(context, LOCAL_BLOCKLIST_DOWNLOAD_FOLDER_NAME)
                val dir = File(path)
                deleteRecursive(dir)
                persistentState.localBlocklistTimestamp = INIT_TIME_MS
                persistentState.localBlocklistStamp = ""
                persistentState.newestLocalBlocklistTimestamp = INIT_TIME_MS
            }

            updateLocalBlocklistUi()
            showCheckUpdateUi()
            Utilities.showToastUiCentered(
                context,
                context.resources.getString(R.string.config_add_success_toast),
                Toast.LENGTH_SHORT
            )
        }
    }

    fun isBlocklistUpdateAvailable() {
        scope.launch(Dispatchers.IO) {
            appDownloadManager.isDownloadRequired(
                com.celzero.bravedns.service.RethinkBlocklistManager.DownloadType.LOCAL
            )
        }
    }

    fun isLocalBlocklistStampAvailable(): Boolean {
        return persistentState.localBlocklistStamp.isNotEmpty()
    }

    fun setBraveDnsLocal() {
        persistentState.blocklistEnabled = true
    }

    fun removeBraveDnsLocal() {
        persistentState.blocklistEnabled = false
    }

    fun enableBlocklist() {
        if (persistentState.blocklistEnabled) {
            removeBraveDnsLocal()
            updateLocalBlocklistUi()
            return
        }

        if (!VpnController.hasTunnel()) {
            Utilities.showToastUiCentered(
                context,
                context.resources.getString(R.string.ssv_toast_start_rethink),
                Toast.LENGTH_SHORT
            )
            return
        }

        scope.launch(Dispatchers.Main) {
            val blocklistsExist = withContext(Dispatchers.Default) {
                Utilities.hasLocalBlocklists(
                    context,
                    persistentState.localBlocklistTimestamp
                )
            }

            if (blocklistsExist) {
                setBraveDnsLocal()
                if (isLocalBlocklistStampAvailable()) {
                    updateLocalBlocklistUi()
                } else {
                    dismissLocalBlocklistsSheet()
                    onLocalBlocklistConfigureClick()
                }
            } else {
                dismissLocalBlocklistsSheet()
                onLocalBlocklistConfigureClick()
            }
        }
    }

    fun invokeLocalBlocklistActivity() {
        if (!VpnController.hasTunnel()) {
            Utilities.showToastUiCentered(
                context,
                context.resources.getString(R.string.ssv_toast_start_rethink),
                Toast.LENGTH_SHORT
            )
            return
        }

        dismissLocalBlocklistsSheet()
        onLocalBlocklistConfigureClick()
    }

    fun openLocalBlocklist() {
        updateLocalBlocklistUi()
        initLocalBlocklistVersion()
        showLocalBlocklistsSheet = true
    }

    fun showSystemDnsDialog(dns: String) {
        systemDnsDialogText = dns
        showSystemDnsDialog = true
    }

    fun showSmartDnsInfoDialog() {
        scope.launch(Dispatchers.IO) {
            val ids = VpnController.getPlusResolvers()
            val dnsList: MutableList<String> = mutableListOf()
            ids.forEach {
                val index = it.substringAfter(Backend.Plus).getOrNull(0)
                if (index == null) {
                    Logger.w(LOG_TAG_DNS, "smart(plus) dns resolver id is empty: $it")
                    return@forEach
                }
                if (index != DOH_INDEX && index != DOT_INDEX) {
                    Logger.w(LOG_TAG_DNS, "smart(plus) dns resolver id is not doh or dot: $it")
                    return@forEach
                }
                val transport = VpnController.getPlusTransportById(it)
                val address = transport?.addr?.tos() ?: ""
                if (address.isNotEmpty()) dnsList.add(address)
            }

            Logger.i(LOG_TAG_DNS, "smart(plus) dns list size: ${dnsList.size}")
            withContext(Dispatchers.Main) {
                val stringBuilder = StringBuilder()
                val desc = context.resources.getString(R.string.smart_dns_desc)
                stringBuilder.append(desc).append("\n\n")
                dnsList.forEach {
                    val txt = context.resources.getString(R.string.symbol_star) + " " + it
                    stringBuilder.append(txt).append("\n")
                }
                smartDnsDialogText = stringBuilder.toString()
                showSmartDnsDialog = true
            }
        }
    }

    // Initialize local blocklist state
    LaunchedEffect(Unit) {
        updateLocalBlocklistUi()
        initLocalBlocklistVersion()
    }

    val workManager = WorkManager.getInstance(context)
    val downloadRequiredStatus by appDownloadManager.downloadRequired
        .asFlow()
        .collectAsStateWithLifecycle(initialValue = AppDownloadManager.DownloadManagerStatus.NOT_STARTED)
    val customDownloadWorkInfos by workManager
        .getWorkInfosByTagLiveData(LocalBlocklistCoordinator.CUSTOM_DOWNLOAD)
        .asFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadTagWorkInfos by workManager
        .getWorkInfosByTagLiveData(DownloadConstants.DOWNLOAD_TAG)
        .asFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val fileTagWorkInfos by workManager
        .getWorkInfosByTagLiveData(DownloadConstants.FILE_TAG)
        .asFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(downloadRequiredStatus) {
        Napier.i("Check for blocklist update, status: $downloadRequiredStatus")
        if (downloadRequiredStatus != AppDownloadManager.DownloadManagerStatus.NOT_STARTED) {
            handleDownloadStatus(downloadRequiredStatus)
        }
    }

    LaunchedEffect(customDownloadWorkInfos) {
        val workInfo = customDownloadWorkInfos.getOrNull(0) ?: return@LaunchedEffect
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
                context,
                context.resources.getString(R.string.blocklist_update_check_failure),
                Toast.LENGTH_SHORT
            )
            workManager.pruneWork()
            workManager.cancelAllWorkByTag(LocalBlocklistCoordinator.CUSTOM_DOWNLOAD)
        }
    }

    LaunchedEffect(downloadTagWorkInfos) {
        val workInfo = downloadTagWorkInfos.getOrNull(0) ?: return@LaunchedEffect
        Napier.i("WorkManager state: ${workInfo.state} for ${DownloadConstants.DOWNLOAD_TAG}")
        if (workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING) {
            isDownloading = true
        } else if (workInfo.state == WorkInfo.State.CANCELLED || workInfo.state == WorkInfo.State.FAILED) {
            isDownloading = false
            Utilities.showToastUiCentered(
                context,
                context.resources.getString(R.string.blocklist_update_check_failure),
                Toast.LENGTH_SHORT
            )
            workManager.pruneWork()
            workManager.cancelAllWorkByTag(DownloadConstants.DOWNLOAD_TAG)
            workManager.cancelAllWorkByTag(DownloadConstants.FILE_TAG)
        }
    }

    LaunchedEffect(fileTagWorkInfos) {
        val workInfo = fileTagWorkInfos.getOrNull(0) ?: return@LaunchedEffect
        if (workInfo.state == WorkInfo.State.SUCCEEDED) {
            isDownloading = false
            showUpdateUi()
            workManager.pruneWork()
        } else if (workInfo.state == WorkInfo.State.CANCELLED || workInfo.state == WorkInfo.State.FAILED) {
            isDownloading = false
            Utilities.showToastUiCentered(
                context,
                context.resources.getString(R.string.blocklist_update_check_failure),
                Toast.LENGTH_SHORT
            )
            workManager.pruneWork()
            workManager.cancelAllWorkByTag(DownloadConstants.FILE_TAG)
        }
    }

    // Observe lifecycle for onResume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateUiState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Main content
    DnsSettingsScreen(
        uiState = uiState,
        onRefreshClick = { viewModel.refreshDns() },
        onSystemDnsClick = { viewModel.enableSystemDns() },
        onSystemDnsInfoClick = {
            scope.launch(Dispatchers.IO) {
                val sysDns = VpnController.getSystemDns()
                withContext(Dispatchers.Main) {
                    showSystemDnsDialog(sysDns)
                }
            }
        },
        onCustomDnsClick = onCustomDnsClick,
        onRethinkPlusDnsClick = onRethinkPlusDnsClick,
        onSmartDnsClick = { viewModel.enableSmartDns() },
        onSmartDnsInfoClick = { showSmartDnsInfoDialog() },
        onLocalBlocklistClick = { openLocalBlocklist() },
        onCustomDownloaderChange = { viewModel.setUseCustomDownloadManager(it) },
        onPeriodicUpdateChange = { viewModel.setPeriodicallyCheckBlocklistUpdate(it) },
        onDnsAlgChange = { viewModel.setDnsAlgEnabled(it) },
        onSplitDnsChange = { viewModel.setSplitDns(it) },
        onBypassDnsBlockChange = { viewModel.setBypassBlockInDns(it) },
        onAllowedRecordTypesClick = { showRecordTypesSheet = true },
        onFavIconChange = { viewModel.setFavIconEnabled(it) },
        onDnsCacheChange = { viewModel.setEnableDnsCache(it) },
        onProxyDnsChange = { viewModel.setProxyDns(it) },
        onUndelegatedDomainsChange = { viewModel.setUseSystemDnsForUndelegatedDomains(it) },
        onFallbackChange = { viewModel.setUseFallbackDnsToBypass(it) },
        onPreventLeaksChange = { viewModel.setPreventDnsLeaksEnabled(it) }
    )

    // DNS Record Types Sheet
    if (showRecordTypesSheet) {
        DnsRecordTypesSheet(
            persistentState = persistentState,
            onDismiss = { showRecordTypesSheet = false }
        )
    }

    // System DNS Dialog
    if (showSystemDnsDialog) {
        AlertDialog(
            onDismissRequest = { showSystemDnsDialog = false },
            title = { Text(text = stringResource(R.string.network_dns)) },
            text = { Text(text = systemDnsDialogText) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showSystemDnsDialog = false }) {
                        Text(text = stringResource(R.string.ada_noapp_dialog_positive))
                    }
                    TextButton(
                        onClick = {
                            UIUtils.clipboardCopy(
                                context,
                                systemDnsDialogText,
                                context.resources.getString(R.string.copy_clipboard_label)
                            )
                            Utilities.showToastUiCentered(
                                context,
                                context.resources.getString(R.string.info_dialog_url_copy_toast_msg),
                                Toast.LENGTH_SHORT
                            )
                            showSystemDnsDialog = false
                        }
                    ) {
                        Text(text = stringResource(R.string.dns_info_neutral))
                    }
                }
            }
        )
    }

    // Smart DNS Dialog
    if (showSmartDnsDialog) {
        AlertDialog(
            onDismissRequest = { showSmartDnsDialog = false },
            title = { Text(text = stringResource(R.string.smart_dns)) },
            text = { Text(text = smartDnsDialogText) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showSmartDnsDialog = false }) {
                        Text(text = stringResource(R.string.ada_noapp_dialog_positive))
                    }
                    TextButton(
                        onClick = {
                            UIUtils.clipboardCopy(
                                context,
                                smartDnsDialogText,
                                context.resources.getString(R.string.copy_clipboard_label)
                            )
                            Utilities.showToastUiCentered(
                                context,
                                context.resources.getString(R.string.info_dialog_url_copy_toast_msg),
                                Toast.LENGTH_SHORT
                            )
                            showSmartDnsDialog = false
                        }
                    ) {
                        Text(text = stringResource(R.string.dns_info_neutral))
                    }
                }
            }
        )
    }

    // Local Blocklists Sheet
    if (showLocalBlocklistsSheet) {
        LocalBlocklistsSheet(
            context = context,
            headingText = headingText,
            enableLabel = enableLabel,
            enableColor = enableColor,
            versionText = versionText,
            canConfigure = canConfigure,
            canCopy = canCopy,
            canSearch = canSearch,
            showCheckDownload = showCheckDownload,
            showDownload = showDownload,
            showRedownload = showRedownload,
            isChecking = isChecking,
            isDownloading = isDownloading,
            isRedownloading = isRedownloading,
            persistentState = persistentState,
            onDismiss = { dismissLocalBlocklistsSheet() },
            onEnableBlocklist = { enableBlocklist() },
            onConfigure = { invokeLocalBlocklistActivity() },
            onCopy = {
                val url = Constants.RETHINK_BASE_URL_MAX + persistentState.localBlocklistStamp
                UIUtils.clipboardCopy(
                    context,
                    url,
                    context.resources.getString(R.string.copy_clipboard_label)
                )
                Utilities.showToastUiCentered(
                    context,
                    context.resources.getString(R.string.info_dialog_rethink_toast_msg),
                    Toast.LENGTH_SHORT
                )
            },
            onSearch = {
                dismissLocalBlocklistsSheet()
                val url = RETHINK_SEARCH_URL + Uri.encode(persistentState.localBlocklistStamp)
                UIUtils.openUrl(context, url)
            },
            onCheckUpdate = {
                isChecking = true
                isBlocklistUpdateAvailable()
            },
            onDownload = {
                downloadDialogIsRedownload = false
                showDownloadDialog = true
            },
            onRedownload = {
                downloadDialogIsRedownload = true
                showDownloadDialog = true
            },
            onDelete = { showDeleteDialog = true }
        )
    }

    // Download Dialog
    if (showDownloadDialog) {
        val title = if (downloadDialogIsRedownload) {
            stringResource(R.string.local_blocklist_redownload)
        } else {
            stringResource(R.string.local_blocklist_download)
        }
        val message = if (downloadDialogIsRedownload) {
            stringResource(
                R.string.local_blocklist_redownload_desc,
                convertLongToTime(
                    persistentState.localBlocklistTimestamp,
                    Constants.TIME_FORMAT_2
                )
            )
        } else {
            stringResource(R.string.local_blocklist_download_desc)
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
                    Text(text = stringResource(R.string.settings_local_blocklist_dialog_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text(text = stringResource(R.string.lbl_cancel))
                }
            }
        )
    }

    // Delete Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = stringResource(R.string.lbl_delete)) },
            text = { Text(text = stringResource(R.string.local_blocklist_delete_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        deleteLocalBlocklist()
                    }
                ) {
                    Text(text = stringResource(R.string.lbl_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(R.string.lbl_cancel))
                }
            }
        )
    }

    // Lockdown Dialog
    if (showLockdownDialog) {
        AlertDialog(
            onDismissRequest = { showLockdownDialog = false },
            title = { Text(text = stringResource(R.string.lockdown_download_enable_inapp)) },
            text = { Text(text = stringResource(R.string.lockdown_download_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLockdownDialog = false
                        persistentState.useCustomDownloadManager = true
                        downloadLocalBlocklist(downloadDialogIsRedownload)
                    }
                ) {
                    Text(text = stringResource(R.string.lockdown_download_enable_inapp))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLockdownDialog = false
                        proceedWithDownload(downloadDialogIsRedownload)
                    }
                ) {
                    Text(text = stringResource(R.string.lbl_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsRecordTypesSheet(
    persistentState: PersistentState,
    onDismiss: () -> Unit
) {
    var isAutoMode by remember { mutableStateOf(persistentState.dnsRecordTypesAutoMode) }
    val selected = remember {
        mutableStateListOf<String>().apply {
            addAll(getInitialRecordSelection(persistentState))
        }
    }

    val allTypes = remember {
        ResourceRecordTypes.entries.filter { it != ResourceRecordTypes.UNKNOWN }
    }

    val sortedTypes by remember(isAutoMode, selected.toList()) {
        derivedStateOf {
            allTypes.sortedWith(
                compareByDescending<ResourceRecordTypes> {
                    if (isAutoMode) true else selected.contains(it.name)
                }.thenBy { it.name }
            )
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(3.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp)
                    )
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.cd_allowed_dns_record_types_heading),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp)
            )
            Text(
                text = stringResource(R.string.cd_allowed_dns_record_types_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            )

            RecordModeToggleRow(
                isAutoMode = isAutoMode,
                onAutoSelected = {
                    isAutoMode = true
                    persistentState.dnsRecordTypesAutoMode = true
                },
                onManualSelected = {
                    isAutoMode = false
                    persistentState.dnsRecordTypesAutoMode = false
                }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp)
            ) {
                items(sortedTypes) { type ->
                    RecordTypeRow(
                        type = type,
                        isAutoMode = isAutoMode,
                        isSelected = if (isAutoMode) true else selected.contains(type.name),
                        onToggle = {
                            if (isAutoMode) return@RecordTypeRow
                            if (selected.contains(type.name)) {
                                selected.remove(type.name)
                            } else {
                                selected.add(type.name)
                            }
                            persistentState.setAllowedDnsRecordTypes(selected.toSet())
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordModeToggleRow(
    isAutoMode: Boolean,
    onAutoSelected: () -> Unit,
    onManualSelected: () -> Unit
) {
    val selectedBg = MaterialTheme.colorScheme.primaryContainer
    val unselectedBg = MaterialTheme.colorScheme.surfaceVariant
    val selectedText = MaterialTheme.colorScheme.onPrimaryContainer
    val unselectedText = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            onClick = onAutoSelected,
            modifier = Modifier
                .weight(1f)
                .background(
                    if (isAutoMode) selectedBg else unselectedBg,
                    RoundedCornerShape(16.dp)
                )
        ) {
            Text(
                text = stringResource(R.string.settings_ip_text_ipv46),
                color = if (isAutoMode) selectedText else unselectedText
            )
        }
        TextButton(
            onClick = onManualSelected,
            modifier = Modifier
                .weight(1f)
                .background(
                    if (!isAutoMode) selectedBg else unselectedBg,
                    RoundedCornerShape(16.dp)
                )
        ) {
            Text(
                text = stringResource(R.string.lbl_manual),
                color = if (!isAutoMode) selectedText else unselectedText
            )
        }
    }
}

@Composable
private fun RecordTypeRow(
    type: ResourceRecordTypes,
    isAutoMode: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .alpha(if (isAutoMode) 0.6f else 1f)
            .clickable(enabled = !isAutoMode) { onToggle() }
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = type.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                text = type.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = if (isAutoMode) null else { _ -> onToggle() },
            modifier = Modifier.padding(start = 8.dp)
        )
    }
    if (isAutoMode) {
        Spacer(modifier = Modifier.height(2.dp))
    }
}

private fun getInitialRecordSelection(persistentState: PersistentState): List<String> {
    if (!persistentState.dnsRecordTypesAutoMode) {
        return persistentState.getAllowedDnsRecordTypes().toList()
    }
    val storedSelection = persistentState.allowedDnsRecordTypesString
    if (storedSelection.isNotEmpty()) {
        return storedSelection.split(",").filter { it.isNotEmpty() }
    }
    return listOf(
        ResourceRecordTypes.A.name,
        ResourceRecordTypes.AAAA.name,
        ResourceRecordTypes.CNAME.name,
        ResourceRecordTypes.HTTPS.name,
        ResourceRecordTypes.SVCB.name
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalBlocklistsSheet(
    context: Context,
    headingText: String,
    enableLabel: String,
    enableColor: Color,
    versionText: String,
    canConfigure: Boolean,
    canCopy: Boolean,
    canSearch: Boolean,
    showCheckDownload: Boolean,
    showDownload: Boolean,
    showRedownload: Boolean,
    isChecking: Boolean,
    isDownloading: Boolean,
    isRedownloading: Boolean,
    persistentState: PersistentState,
    onDismiss: () -> Unit,
    onEnableBlocklist: () -> Unit,
    onConfigure: () -> Unit,
    onCopy: () -> Unit,
    onSearch: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownload: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outline

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = enableLabel,
                    color = enableColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onEnableBlocklist) {
                    Text(text = stringResource(R.string.lbl_apply))
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    text = stringResource(R.string.lbl_configure),
                    enabled = canConfigure,
                    modifier = Modifier.weight(1f),
                    onClick = onConfigure
                )
                ActionButton(
                    text = stringResource(R.string.lbbs_copy),
                    enabled = canCopy,
                    modifier = Modifier.weight(1f),
                    onClick = onCopy
                )
                ActionButton(
                    text = stringResource(R.string.lbl_search),
                    enabled = canSearch,
                    modifier = Modifier.weight(1f),
                    onClick = onSearch
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showCheckDownload) {
                    DownloadRow(
                        label = stringResource(R.string.lbbs_update_check),
                        isLoading = isChecking,
                        enabled = !isChecking,
                        onClick = onCheckUpdate
                    )
                }
                if (showDownload) {
                    DownloadRow(
                        label = stringResource(R.string.local_blocklist_download),
                        isLoading = isDownloading,
                        enabled = !isDownloading,
                        onClick = onDownload
                    )
                }
                if (showRedownload) {
                    DownloadRow(
                        label = stringResource(R.string.local_blocklist_redownload),
                        isLoading = isRedownloading,
                        enabled = !isRedownloading,
                        onClick = onRedownload
                    )
                }
                DownloadRow(
                    label = stringResource(R.string.lbl_delete),
                    isLoading = false,
                    enabled = true,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    label: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
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
                Text(text = stringResource(R.string.lbl_proceed))
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
        modifier = modifier
            .height(36.dp)
            .alpha(alpha),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = text, modifier = Modifier.padding(horizontal = 6.dp))
    }
}
