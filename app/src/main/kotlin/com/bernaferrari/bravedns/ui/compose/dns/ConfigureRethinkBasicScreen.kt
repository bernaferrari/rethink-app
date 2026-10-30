/*
 * Copyright 2024 RethinkDNS and its authors
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

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.MutableStateFlow
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.data.FileTag
import com.bernaferrari.bravedns.download.AppDownloadManager
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.RethinkBlocklistManager
import com.bernaferrari.bravedns.service.RethinkBlocklistManager.getTagsFromStamp
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkMultiActionDialog
import com.bernaferrari.bravedns.ui.rethink.RethinkBlocklistState
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Constants.Companion.DEFAULT_RDNS_REMOTE_DNS_NAMES
import com.bernaferrari.bravedns.util.Constants.Companion.MAX_ENDPOINT
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.Utilities.getRemoteBlocklistStamp
import com.bernaferrari.bravedns.util.Utilities.hasLocalBlocklists
import com.bernaferrari.bravedns.util.Utilities.hasRemoteBlocklists
import com.bernaferrari.bravedns.util.Utilities.showToastUiCentered
import com.bernaferrari.bravedns.viewmodel.LocalBlocklistPacksMapViewModel
import com.bernaferrari.bravedns.viewmodel.RemoteBlocklistPacksMapViewModel
import com.bernaferrari.bravedns.viewmodel.RethinkEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.RethinkLocalFileTagViewModel
import com.bernaferrari.bravedns.viewmodel.RethinkRemoteFileTagViewModel
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import Logger
import Logger.LOG_TAG_UI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConfigureRethinkScreenType {
    REMOTE,
    LOCAL,
    DB_LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigureRethinkBasicScreen(
    screenType: ConfigureRethinkScreenType,
    remoteName: String = "",
    remoteUrl: String = "",
    uid: Int = Constants.MISSING_UID,
    persistentState: PersistentState,
    appConfig: AppConfig,
    appDownloadManager: AppDownloadManager,
    rethinkEndpointViewModel: RethinkEndpointViewModel,
    remoteFileTagViewModel: RethinkRemoteFileTagViewModel,
    localFileTagViewModel: RethinkLocalFileTagViewModel,
    remoteBlocklistPacksMapViewModel: RemoteBlocklistPacksMapViewModel,
    localBlocklistPacksMapViewModel: LocalBlocklistPacksMapViewModel,
    onEditConfiguration: (ConfigureRethinkScreenType, String, String) -> Unit = { _, _, _ -> },
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localBlocklistVersionTemplate = stringResource(R.string.settings_local_blocklist_version)
    val downloadSuccessMessage = stringResource(R.string.download_update_dialog_message_success)
    val downloadFailureMessage = stringResource(R.string.download_update_dialog_failure_message)
    val filterDescriptionTemplate = stringResource(R.string.rt_filter_desc)
    val filterDescriptionSubgroupsTemplate = stringResource(R.string.rt_filter_desc_subgroups)

    val blocklistType = remember {
        if (screenType == ConfigureRethinkScreenType.LOCAL) {
            RethinkBlocklistManager.RethinkBlocklistType.LOCAL
        } else {
            RethinkBlocklistManager.RethinkBlocklistType.REMOTE
        }
    }

    val filters = remember { MutableStateFlow(RethinkBlocklistState.Filters()) }

    var showDownload by remember { mutableStateOf(false) }
    var showConfigure by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var showRemoteProgress by remember { mutableStateOf(false) }
    var activeView by remember { mutableStateOf(RethinkBlocklistState.BlocklistView.PACKS) }
    var updateAvailable by remember { mutableStateOf(false) }
    var checkUpdateVisible by remember { mutableStateOf(false) }
    var redownloadVisible by remember { mutableStateOf(false) }
    var checkUpdateInProgress by remember { mutableStateOf(false) }
    var updateInProgress by remember { mutableStateOf(false) }
    var isMax by remember { mutableStateOf(false) }
    var filterLabelText by remember { mutableStateOf("") }
    var showLockdownDialog by remember { mutableStateOf(false) }
    var lockdownDialogType by remember { mutableStateOf<RethinkBlocklistManager.RethinkBlocklistType?>(null) }
    var showApplyChangesDialog by remember { mutableStateOf(false) }
    var plusFilterTags by remember { mutableStateOf<List<FileTag>>(emptyList()) }
    var modifiedStamp by remember { mutableStateOf("") }

    fun getStampValue(): String {
        return if (blocklistType.isLocal()) {
            persistentState.localBlocklistStamp
        } else {
            getRemoteBlocklistStamp(remoteUrl)
        }
    }

    fun getRemoteUrl(stamp: String): String {
        return if (remoteUrl.contains(MAX_ENDPOINT)) {
            Constants.RETHINK_BASE_URL_MAX + stamp
        } else {
            Constants.RETHINK_BASE_URL_SKY + stamp
        }
    }

    fun isStampChanged(): Boolean {
        if (DEFAULT_RDNS_REMOTE_DNS_NAMES.contains(remoteName)) {
            return false
        }
        return getStampValue() != modifiedStamp
    }

    fun hasBlocklists(): Boolean {
        return if (blocklistType.isLocal()) {
            hasLocalBlocklists(context, persistentState.localBlocklistTimestamp)
        } else {
            hasRemoteBlocklists(context, persistentState.remoteBlocklistTimestamp)
        }
    }

    fun setStamp(stamp: String?) {
        Logger.i(LOG_TAG_UI, "set stamp for blocklist type: ${blocklistType.name} with $stamp")
        if (stamp == null) {
            Logger.i(LOG_TAG_UI, "stamp is null")
            return
        }

        scope.launch(Dispatchers.IO) {
            val blocklistCount = getTagsFromStamp(stamp, blocklistType).size
            if (blocklistType.isLocal()) {
                persistentState.localBlocklistStamp = stamp
                persistentState.numberOfLocalBlocklists = blocklistCount
                persistentState.blocklistEnabled = true
            } else {
                appConfig.updateRethinkEndpoint(
                    Constants.RETHINK_DNS_PLUS,
                    getRemoteUrl(stamp),
                    blocklistCount
                )
                appConfig.enableRethinkDnsPlus()
            }
        }
    }

    suspend fun processSelectedFileTags(stamp: String) {
        val list = RethinkBlocklistManager.getTagsFromStamp(stamp, blocklistType)
        updateSelectedFileTags(list.toMutableSet(), blocklistType)
    }

    fun onDownloadStart() {
        isDownloading = true
        showDownload = true
        showConfigure = false
    }

    fun onDownloadFail() {
        isDownloading = false
        showDownload = true
        showConfigure = false
        showRemoteProgress = false
    }

    fun onDownloadSuccess() {
        isDownloading = false
        showDownload = false
        showConfigure = true
        showRemoteProgress = false
        showToastUiCentered(
            context,
            downloadSuccessMessage,
            Toast.LENGTH_SHORT
        )
    }

    fun handleDownloadStatus(status: AppDownloadManager.DownloadManagerStatus) {
        when (status) {
            AppDownloadManager.DownloadManagerStatus.IN_PROGRESS -> {}
            AppDownloadManager.DownloadManagerStatus.STARTED -> onDownloadStart()
            AppDownloadManager.DownloadManagerStatus.FAILURE -> onDownloadFail()
            AppDownloadManager.DownloadManagerStatus.NOT_AVAILABLE -> {
                showToastUiCentered(
                    context,
                    "Download latest version to update the blocklists",
                    Toast.LENGTH_SHORT
                )
            }

            else -> {}
        }
    }

    fun proceedWithBlocklistDownload(type: RethinkBlocklistManager.RethinkBlocklistType) {
        scope.launch {
            if (type.isLocal()) {
                val status = withContext(Dispatchers.IO) {
                    appDownloadManager.downloadLocalBlocklist(
                        persistentState.localBlocklistTimestamp,
                        isRedownload = false
                    )
                }
                handleDownloadStatus(status)
            } else {
                withContext(Dispatchers.IO) {
                    appDownloadManager.downloadRemoteBlocklist(
                        persistentState.remoteBlocklistTimestamp,
                        isRedownload = true
                    )
                }
                showRemoteProgress = false
                // Refresh blocklist availability
                val blocklistsExist = withContext(Dispatchers.IO) { hasBlocklists() }
                if (blocklistsExist) {
                    showConfigure = true
                    showDownload = false
                } else {
                    showConfigure = false
                    showDownload = true
                }
            }
        }
    }

    fun downloadBlocklist(type: RethinkBlocklistManager.RethinkBlocklistType) {
        if (VpnController.isVpnLockdown() && !persistentState.useCustomDownloadManager) {
            lockdownDialogType = type
            showLockdownDialog = true
            return
        }
        proceedWithBlocklistDownload(type)
    }

    fun cancelDownload() {
        appDownloadManager.cancelDownload(type = RethinkBlocklistManager.DownloadType.LOCAL)
    }

    fun refreshBlocklistAvailability() {
        scope.launch {
            val blocklistsExist = withContext(Dispatchers.IO) { hasBlocklists() }
            if (blocklistsExist) {
                showConfigure = true
                showDownload = false
                return@launch
            }

            showConfigure = false
            showDownload = true
            if (!blocklistType.isLocal()) {
                showRemoteProgress = true
                downloadBlocklist(blocklistType)
            }
        }
    }

    fun updateMaxSwitchUi() {
        scope.launch {
            var endpointUrl: String? = null
            withContext(Dispatchers.IO) { endpointUrl = appConfig.getRethinkPlusEndpoint()?.url }
            isMax = endpointUrl?.contains(Constants.MAX_ENDPOINT) == true
        }
    }

    fun isBlocklistUpdateAvailable(): Boolean {
        Logger.d(LOG_TAG_UI, "Update available? newest: ${persistentState.newestRemoteBlocklistTimestamp}, available: ${persistentState.remoteBlocklistTimestamp}")
        return (persistentState.newestRemoteBlocklistTimestamp != Constants.INIT_TIME_MS &&
                persistentState.newestRemoteBlocklistTimestamp > persistentState.remoteBlocklistTimestamp)
    }

    fun refreshUpdateUi() {
        if (persistentState.remoteBlocklistTimestamp == Constants.INIT_TIME_MS) {
            updateAvailable = false
            checkUpdateVisible = false
            redownloadVisible = false
            return
        }

        if (isBlocklistUpdateAvailable()) {
            updateAvailable = true
            checkUpdateVisible = false
            redownloadVisible = false
            return
        }

        updateAvailable = false
        checkUpdateVisible = true
        redownloadVisible = false
    }

    fun checkBlocklistUpdate() {
        scope.launch(Dispatchers.IO) {
            appDownloadManager.isDownloadRequired(RethinkBlocklistManager.DownloadType.REMOTE)
        }
    }

    fun download(timestamp: Long, isRedownload: Boolean) {
        scope.launch(Dispatchers.IO) {
            val initiated = appDownloadManager.downloadRemoteBlocklist(timestamp, isRedownload)
            if (!initiated) {
                withContext(Dispatchers.Main) {
                    showToastUiCentered(
                        context,
                        downloadFailureMessage,
                        Toast.LENGTH_SHORT
                    )
                }
            }
        }
    }

    fun applyChangesAndFinish() {
        setStamp(modifiedStamp)
        onBackClick?.invoke()
    }

    fun revertChangesAndFinish() {
        scope.launch {
            val stamp = getStampValue()
            val list = RethinkBlocklistManager.getTagsFromStamp(stamp, blocklistType)
            updateSelectedFileTags(list.toMutableSet(), blocklistType)
            setStamp(stamp)
            onBackClick?.invoke()
        }
    }

    // Back handler for unsaved changes
    if (screenType != ConfigureRethinkScreenType.DB_LIST) {
        BackHandler {
            if (!isStampChanged()) {
                onBackClick?.invoke()
                return@BackHandler
            }
            showApplyChangesDialog = true
        }
    }

    // Dialogs
    if (showLockdownDialog && lockdownDialogType != null) {
        val type = lockdownDialogType ?: return
        RethinkConfirmDialog(
            onDismissRequest = { showLockdownDialog = false },
            title = stringResource(R.string.lockdown_download_enable_inapp),
            message = stringResource(R.string.lockdown_download_message),
            confirmText = stringResource(R.string.lockdown_download_enable_inapp),
            dismissText = stringResource(R.string.lbl_cancel),
            onConfirm = {
                showLockdownDialog = false
                persistentState.useCustomDownloadManager = true
                downloadBlocklist(type)
            },
            onDismiss = {
                showLockdownDialog = false
                proceedWithBlocklistDownload(type)
            }
        )
    }

    if (showApplyChangesDialog) {
        RethinkMultiActionDialog(
            onDismissRequest = { showApplyChangesDialog = false },
            title = stringResource(R.string.rt_dialog_title),
            message = stringResource(R.string.rt_dialog_message),
            primaryText = stringResource(R.string.lbl_apply),
            onPrimary = {
                showApplyChangesDialog = false
                setStamp(modifiedStamp)
                onBackClick?.invoke()
            },
            secondaryText = stringResource(R.string.rt_dialog_neutral),
            onSecondary = { showApplyChangesDialog = false },
            tertiaryText = stringResource(R.string.notif_dialog_pause_dialog_negative),
            onTertiary = {
                showApplyChangesDialog = false
                onBackClick?.invoke()
            }
        )
    }

    val title = when (screenType) {
        ConfigureRethinkScreenType.DB_LIST -> stringResource(R.string.dc_rethink_dns_radio)
        ConfigureRethinkScreenType.LOCAL -> stringResource(R.string.dc_local_block_heading)
        ConfigureRethinkScreenType.REMOTE -> stringResource(R.string.dc_rethink_dns_radio)
    }
    val subtitle = when (screenType) {
        ConfigureRethinkScreenType.LOCAL,
        ConfigureRethinkScreenType.REMOTE -> stringResource(R.string.dns_desc)
        else -> null
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkLargeTopBar(
                title = title,
                subtitle = subtitle,
                scrollBehavior = scrollBehavior,
                onBackClick = if (onBackClick == null) null else {
                    {
                        if (screenType != ConfigureRethinkScreenType.DB_LIST && isStampChanged()) {
                            showApplyChangesDialog = true
                        } else {
                            onBackClick()
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)
        ) {
            when (screenType) {
                ConfigureRethinkScreenType.DB_LIST -> {
                    RethinkListContent(
                        modifier = Modifier.weight(1f),
                        context = context,
                        persistentState = persistentState,
                        appConfig = appConfig,
                        rethinkEndpointViewModel = rethinkEndpointViewModel,
                        uid = uid,
                        updateUi = RethinkListUpdateUi(
                            isMax = isMax,
                            updateAvailable = updateAvailable,
                            checkUpdateVisible = checkUpdateVisible,
                            redownloadVisible = redownloadVisible,
                            checkUpdateInProgress = checkUpdateInProgress,
                            updateInProgress = updateInProgress,
                        ),
                        onMaxChanged = { isMax = it },
                        onUpdateMaxSwitchUi = { updateMaxSwitchUi() },
                        onRefreshUpdateUi = { refreshUpdateUi() },
                        onCheckUpdateInProgressChanged = { checkUpdateInProgress = it },
                        onUpdateInProgressChanged = { updateInProgress = it },
                        onCheckBlocklistUpdate = { checkBlocklistUpdate() },
                        onDownload = { timestamp, isRedownload -> download(timestamp, isRedownload) },
                        localBlocklistVersionTemplate = localBlocklistVersionTemplate,
                        downloadFailureMessage = downloadFailureMessage,
                        onEditConfiguration = onEditConfiguration,
                    )
                }

                else -> {
                    RethinkBlocklistContent(
                        modifier = Modifier.weight(1f),
                        context = context,
                        blocklistType = blocklistType,
                        filters = filters,
                        vms = RethinkBlocklistVms(
                            remoteFileTagViewModel = remoteFileTagViewModel,
                            localFileTagViewModel = localFileTagViewModel,
                            remoteBlocklistPacksMapViewModel = remoteBlocklistPacksMapViewModel,
                            localBlocklistPacksMapViewModel = localBlocklistPacksMapViewModel,
                        ),
                        flags = RethinkBlocklistUiFlags(
                            showDownload = showDownload,
                            showConfigure = showConfigure,
                            isDownloading = isDownloading,
                            showRemoteProgress = showRemoteProgress,
                            activeView = activeView,
                            filterLabelText = filterLabelText,
                            plusFilterTags = plusFilterTags,
                        ),
                        actions = RethinkBlocklistActions(
                            onActiveViewChanged = { activeView = it },
                            onFilterLabelTextChanged = { filterLabelText = it },
                            onPlusFilterTagsChanged = { plusFilterTags = it },
                            onDownloadBlocklist = { downloadBlocklist(blocklistType) },
                            onCancelDownload = { cancelDownload(); onBackClick?.invoke() },
                            onApplyChanges = { applyChangesAndFinish() },
                            onRevertChanges = { revertChangesAndFinish() },
                            onRefreshBlocklistAvailability = { refreshBlocklistAvailability() },
                            onProcessSelectedFileTags = { stamp ->
                                scope.launch { processSelectedFileTags(stamp) }
                            },
                            onModifiedStampChanged = { modifiedStamp = it },
                            getStampValue = { getStampValue() },
                            onDownloadStart = { onDownloadStart() },
                            onDownloadFail = { onDownloadFail() },
                            onDownloadSuccess = { onDownloadSuccess() },
                        ),
                        filterDescriptionTemplate = filterDescriptionTemplate,
                        filterDescriptionSubgroupsTemplate = filterDescriptionSubgroupsTemplate,
                    )
                }
            }
        }
    }

}
