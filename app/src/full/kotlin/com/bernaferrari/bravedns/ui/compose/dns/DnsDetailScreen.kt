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
package com.bernaferrari.bravedns.ui.compose.dns


import Logger
import Logger.LOG_TAG_UI
import Logger.LOG_TAG_DNS
import android.content.Context
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bernaferrari.bravedns.util.workInfosByTagFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.customdownloader.LocalBlocklistCoordinator
import com.bernaferrari.bravedns.data.AppConfig.Companion.DOH_INDEX
import com.bernaferrari.bravedns.data.AppConfig.Companion.DOT_INDEX
import com.bernaferrari.bravedns.download.AppDownloadManager
import com.bernaferrari.bravedns.download.DownloadConstants
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.Dimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkBottomSheetCard
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkMultiActionDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTwoOptionSegmentedRow
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Constants.Companion.INIT_TIME_MS
import com.bernaferrari.bravedns.util.Constants.Companion.LOCAL_BLOCKLIST_DOWNLOAD_FOLDER_NAME
import com.bernaferrari.bravedns.util.Constants.Companion.RETHINK_SEARCH_URL
import com.bernaferrari.bravedns.util.ResourceRecordTypes
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.Utilities.blocklistCanonicalPath
import com.bernaferrari.bravedns.util.Utilities.convertLongToTime
import com.bernaferrari.bravedns.util.Utilities.deleteRecursive
import com.bernaferrari.bravedns.util.Utilities.tos
import com.celzero.firestack.backend.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    initialFocusKey: String? = null,
    onCustomDnsClick: () -> Unit,
    onRethinkPlusDnsClick: () -> Unit,
    onLocalBlocklistConfigureClick: () -> Unit,
    onBlockFreeDnsClick: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
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
    val localBlocklistInUseText = stringResource(
        R.string.settings_local_blocklist_in_use,
        persistentState.numberOfLocalBlocklists.toString(),
    )
    val localBlocklistHeadingText = stringResource(R.string.lbbs_heading)
    val localBlocklistVersionText =
        if (persistentState.localBlocklistTimestamp == INIT_TIME_MS) {
            ""
        } else {
            stringResource(
                R.string.settings_local_blocklist_version,
                convertLongToTime(
                    persistentState.localBlocklistTimestamp,
                    Constants.TIME_FORMAT_2,
                ),
            )
        }
    val blocklistUpdateFailureText = stringResource(R.string.blocklist_update_check_failure)
    val blocklistUpdateNotRequiredText = stringResource(R.string.blocklist_update_check_not_required)
    val blocklistNotAvailableToastText = stringResource(R.string.blocklist_not_available_toast)
    val configAddSuccessToastText = stringResource(R.string.config_add_success_toast)
    val ssvToastStartRethinkText = stringResource(R.string.ssv_toast_start_rethink)
    val smartDnsDescriptionText = stringResource(R.string.smart_dns_desc)
    val symbolStarText = stringResource(R.string.symbol_star)
    val copyClipboardLabelText = stringResource(R.string.copy_clipboard_label)
    val infoDialogUrlCopyToastText = stringResource(R.string.info_dialog_url_copy_toast_msg)
    val infoDialogRethinkToastText = stringResource(R.string.info_dialog_rethink_toast_msg)
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
        headingText = localBlocklistInUseText
        canConfigure = true
        canCopy = true
        canSearch = true
    }

    fun disableBlocklistUi() {
        headingText = localBlocklistHeadingText
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

        versionText = localBlocklistVersionText

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
                appDownloadManager.downloadRequired.value =
                    AppDownloadManager.DownloadManagerStatus.NOT_STARTED
            }
            AppDownloadManager.DownloadManagerStatus.FAILURE -> {
                isChecking = false
                isDownloading = false
                isRedownloading = false
                Utilities.showToastUiCentered(
                    context,
                    blocklistUpdateFailureText,
                    Toast.LENGTH_SHORT
                )
                appDownloadManager.downloadRequired.value =
                    AppDownloadManager.DownloadManagerStatus.NOT_STARTED
            }
            AppDownloadManager.DownloadManagerStatus.NOT_REQUIRED -> {
                showRedownloadUi()
                isChecking = false
                Utilities.showToastUiCentered(
                    context,
                    blocklistUpdateNotRequiredText,
                    Toast.LENGTH_SHORT
                )
                appDownloadManager.downloadRequired.value =
                    AppDownloadManager.DownloadManagerStatus.NOT_STARTED
            }
            AppDownloadManager.DownloadManagerStatus.NOT_AVAILABLE -> {
                Utilities.showToastUiCentered(
                    context,
                    blocklistNotAvailableToastText,
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
                configAddSuccessToastText,
                Toast.LENGTH_SHORT
            )
        }
    }

    fun isBlocklistUpdateAvailable() {
        scope.launch(Dispatchers.IO) {
            appDownloadManager.isDownloadRequired(
                com.bernaferrari.bravedns.service.RethinkBlocklistManager.DownloadType.LOCAL
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
                ssvToastStartRethinkText,
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
                ssvToastStartRethinkText,
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
                val desc = smartDnsDescriptionText
                stringBuilder.append(desc).append("\n\n")
                dnsList.forEach {
                    val txt = "$symbolStarText $it"
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
        .collectAsStateWithLifecycle(initialValue = AppDownloadManager.DownloadManagerStatus.NOT_STARTED)
    val customDownloadWorkInfos by workManager
        .workInfosByTagFlow(LocalBlocklistCoordinator.CUSTOM_DOWNLOAD)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadTagWorkInfos by workManager
        .workInfosByTagFlow(DownloadConstants.DOWNLOAD_TAG)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val fileTagWorkInfos by workManager
        .workInfosByTagFlow(DownloadConstants.FILE_TAG)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(downloadRequiredStatus) {
        Logger.i(LOG_TAG_UI, "Check for blocklist update, status: $downloadRequiredStatus")
        if (downloadRequiredStatus != AppDownloadManager.DownloadManagerStatus.NOT_STARTED) {
            handleDownloadStatus(downloadRequiredStatus)
        }
    }

    LaunchedEffect(customDownloadWorkInfos) {
        val workInfo = customDownloadWorkInfos.getOrNull(0) ?: return@LaunchedEffect
        Logger.i(LOG_TAG_UI, "WorkManager state: ${workInfo.state} for ${LocalBlocklistCoordinator.CUSTOM_DOWNLOAD}")
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
                blocklistUpdateFailureText,
                Toast.LENGTH_SHORT
            )
            workManager.pruneWork()
            workManager.cancelAllWorkByTag(LocalBlocklistCoordinator.CUSTOM_DOWNLOAD)
        }
    }

    LaunchedEffect(downloadTagWorkInfos) {
        val workInfo = downloadTagWorkInfos.getOrNull(0) ?: return@LaunchedEffect
        Logger.i(LOG_TAG_UI, "WorkManager state: ${workInfo.state} for ${DownloadConstants.DOWNLOAD_TAG}")
        if (workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING) {
            isDownloading = true
        } else if (workInfo.state == WorkInfo.State.CANCELLED || workInfo.state == WorkInfo.State.FAILED) {
            isDownloading = false
            Utilities.showToastUiCentered(
                context,
                blocklistUpdateFailureText,
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
                blocklistUpdateFailureText,
                Toast.LENGTH_SHORT
            )
            workManager.pruneWork()
            workManager.cancelAllWorkByTag(DownloadConstants.FILE_TAG)
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.updateUiState()
        onPauseOrDispose { }
    }

    // Main content
    DnsSettingsScreen(
        uiState = uiState,
        initialFocusKey = initialFocusKey,
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
        onBlockFreeDnsModeChange = { viewModel.setBlockFreeDnsMode(it) },
        onBlockFreeDnsClick = onBlockFreeDnsClick,
        onPreventLeaksChange = { viewModel.setPreventDnsLeaksEnabled(it) }
    )

    // DNS Record Types Sheet
    if (showRecordTypesSheet) {
        AndroidDnsRecordTypesSheet(
            persistentState = persistentState,
            onDismiss = { showRecordTypesSheet = false }
        )
    }

    // System DNS Dialog
    if (showSystemDnsDialog) {
        RethinkMultiActionDialog(
            onDismissRequest = { showSystemDnsDialog = false },
            title = stringResource(R.string.network_dns),
            message = systemDnsDialogText,
            primaryText = stringResource(R.string.ada_noapp_dialog_positive),
            onPrimary = { showSystemDnsDialog = false },
            secondaryText = stringResource(R.string.dns_info_neutral),
            onSecondary = {
                UIUtils.clipboardCopy(
                    context,
                    systemDnsDialogText,
                    copyClipboardLabelText
                )
                Utilities.showToastUiCentered(
                    context,
                    infoDialogUrlCopyToastText,
                    Toast.LENGTH_SHORT
                )
                showSystemDnsDialog = false
            }
        )
    }

    // Smart DNS Dialog
    if (showSmartDnsDialog) {
        RethinkMultiActionDialog(
            onDismissRequest = { showSmartDnsDialog = false },
            title = stringResource(R.string.smart_dns),
            message = smartDnsDialogText,
            primaryText = stringResource(R.string.ada_noapp_dialog_positive),
            onPrimary = { showSmartDnsDialog = false },
            secondaryText = stringResource(R.string.dns_info_neutral),
            onSecondary = {
                UIUtils.clipboardCopy(
                    context,
                    smartDnsDialogText,
                    copyClipboardLabelText
                )
                Utilities.showToastUiCentered(
                    context,
                    infoDialogUrlCopyToastText,
                    Toast.LENGTH_SHORT
                )
                showSmartDnsDialog = false
            }
        )
    }

    // Local Blocklists Sheet
    if (showLocalBlocklistsSheet) {
        AndroidLocalBlocklistsSheet(
            headingText = headingText,
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
            isBlocklistEnabled = persistentState.blocklistEnabled,
            onDismiss = { dismissLocalBlocklistsSheet() },
            onEnableBlocklist = { enableBlocklist() },
            onConfigure = { invokeLocalBlocklistActivity() },
            onCopy = {
                val url = Constants.RETHINK_BASE_URL_MAX + persistentState.localBlocklistStamp
                UIUtils.clipboardCopy(
                    context,
                    url,
                    copyClipboardLabelText
                )
                Utilities.showToastUiCentered(
                    context,
                    infoDialogRethinkToastText,
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
        RethinkConfirmDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = title,
            message = message,
            confirmText = stringResource(R.string.settings_local_blocklist_dialog_positive),
            dismissText = stringResource(R.string.lbl_cancel),
            onConfirm = {
                showDownloadDialog = false
                downloadLocalBlocklist(downloadDialogIsRedownload)
            },
            onDismiss = { showDownloadDialog = false }
        )
    }

    // Delete Dialog
    if (showDeleteDialog) {
        RethinkConfirmDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = stringResource(R.string.lbl_delete),
            message = stringResource(R.string.local_blocklist_delete_desc),
            confirmText = stringResource(R.string.lbl_delete),
            dismissText = stringResource(R.string.lbl_cancel),
            isConfirmDestructive = true,
            onConfirm = {
                showDeleteDialog = false
                deleteLocalBlocklist()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // Lockdown Dialog
    if (showLockdownDialog) {
        RethinkConfirmDialog(
            onDismissRequest = { showLockdownDialog = false },
            title = stringResource(R.string.lockdown_download_enable_inapp),
            message = stringResource(R.string.lockdown_download_message),
            confirmText = stringResource(R.string.lockdown_download_enable_inapp),
            dismissText = stringResource(R.string.lbl_cancel),
            onConfirm = {
                showLockdownDialog = false
                persistentState.useCustomDownloadManager = true
                downloadLocalBlocklist(downloadDialogIsRedownload)
            },
            onDismiss = {
                showLockdownDialog = false
                proceedWithDownload(downloadDialogIsRedownload)
            }
        )
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

/** Android preference adapter for the shared DNS record-type sheet. */
@Composable
private fun AndroidDnsRecordTypesSheet(
    persistentState: PersistentState,
    onDismiss: () -> Unit,
) {
    var autoMode by remember { mutableStateOf(persistentState.dnsRecordTypesAutoMode) }
    val selected = remember {
        mutableStateListOf<String>().apply { addAll(getInitialRecordSelection(persistentState)) }
    }
    val types = remember {
        ResourceRecordTypes.entries
            .filter { it != ResourceRecordTypes.UNKNOWN }
            .sortedBy { it.name }
            .map { RethinkDnsRecordType(it.name, it.name, it.desc) }
    }
    val selectedLabel = stringResource(R.string.rt_filter_parent_selected)
    RethinkDnsRecordTypesSheet(
        types = types,
        autoMode = autoMode,
        selectedIds = selected.toSet(),
        strings = RethinkDnsRecordTypesStrings(
            title = stringResource(R.string.cd_allowed_dns_record_types_heading),
            description = stringResource(R.string.cd_allowed_dns_record_types_desc),
            auto = stringResource(R.string.settings_ip_text_ipv46),
            manual = stringResource(R.string.lbl_manual),
            selected = { count, total -> "$selectedLabel $count/$total" },
            allowed = stringResource(R.string.lbl_allowed),
        ),
        onAutoModeChange = { enabled ->
            autoMode = enabled
            persistentState.dnsRecordTypesAutoMode = enabled
        },
        onToggle = { id ->
            if (id in selected) selected.remove(id) else selected.add(id)
            persistentState.setAllowedDnsRecordTypes(selected.toSet())
        },
        onDismiss = onDismiss,
    )
}

/** Android side-effect adapter for the shared local-blocklist maintenance sheet. */
@Composable
private fun AndroidLocalBlocklistsSheet(
    headingText: String,
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
    isBlocklistEnabled: Boolean,
    onDismiss: () -> Unit,
    onEnableBlocklist: () -> Unit,
    onConfigure: () -> Unit,
    onCopy: () -> Unit,
    onSearch: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownload: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
) {
    RethinkLocalBlocklistSheet(
        state = RethinkLocalBlocklistState(
            heading = headingText,
            version = versionText,
            canConfigure = canConfigure,
            canCopy = canCopy,
            canSearch = canSearch,
            showCheckUpdate = showCheckDownload,
            showDownload = showDownload,
            showRedownload = showRedownload,
            checking = isChecking,
            downloading = isDownloading,
            redownloading = isRedownloading,
            enabled = isBlocklistEnabled,
        ),
        strings = RethinkLocalBlocklistStrings(
            state = stringResource(R.string.lbbs_state_header),
            enable = stringResource(R.string.lbbs_toggle_on),
            disable = stringResource(R.string.lbbs_toggle_off),
            toggleDescription = stringResource(R.string.lbbs_toggle_desc),
            actions = stringResource(R.string.lbbs_actions_header),
            configure = stringResource(R.string.lbbs_configure),
            copy = stringResource(R.string.lbbs_copy),
            search = stringResource(R.string.lbbs_search),
            maintenance = stringResource(R.string.lbbs_maintenance_header),
            checkUpdate = stringResource(R.string.lbbs_update_check),
            download = stringResource(R.string.local_blocklist_download),
            redownload = stringResource(R.string.local_blocklist_redownload),
            delete = stringResource(R.string.lbl_delete),
        ),
        onDismiss = onDismiss,
        onEnableToggle = onEnableBlocklist,
        onConfigure = onConfigure,
        onCopy = onCopy,
        onSearch = onSearch,
        onCheckUpdate = onCheckUpdate,
        onDownload = onDownload,
        onRedownload = onRedownload,
        onDelete = onDelete,
    )
}
