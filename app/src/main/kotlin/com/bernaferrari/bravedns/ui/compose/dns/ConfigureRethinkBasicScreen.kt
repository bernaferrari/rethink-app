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

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.flow.MutableStateFlow
import com.bernaferrari.bravedns.util.workInfosByTagFlow
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.components.RethinkEndpointRow
import com.bernaferrari.bravedns.ui.components.toRethinkGroup
import com.bernaferrari.bravedns.customdownloader.LocalBlocklistCoordinator.Companion.CUSTOM_DOWNLOAD
import com.bernaferrari.bravedns.customdownloader.RemoteBlocklistCoordinator
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.data.FileTag
import com.bernaferrari.bravedns.database.LocalBlocklistPacksMap
import com.bernaferrari.bravedns.database.RemoteBlocklistPacksMap
import com.bernaferrari.bravedns.database.RethinkLocalFileTag
import com.bernaferrari.bravedns.database.RethinkRemoteFileTag
import com.bernaferrari.bravedns.download.AppDownloadManager
import com.bernaferrari.bravedns.download.DownloadConstants.Companion.DOWNLOAD_TAG
import com.bernaferrari.bravedns.download.DownloadConstants.Companion.FILE_TAG
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.RethinkBlocklistManager
import com.bernaferrari.bravedns.service.RethinkBlocklistManager.getStamp
import com.bernaferrari.bravedns.service.RethinkBlocklistManager.getTagsFromStamp
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkMultiActionDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTwoOptionSegmentedRow
import com.bernaferrari.bravedns.ui.rethink.RethinkBlocklistState
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Constants.Companion.DEAD_PACK
import com.bernaferrari.bravedns.util.Constants.Companion.DEFAULT_RDNS_REMOTE_DNS_NAMES
import com.bernaferrari.bravedns.util.Constants.Companion.MAX_ENDPOINT
import com.bernaferrari.bravedns.util.Constants.Companion.RETHINK_STAMP_VERSION
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.Utilities.getRemoteBlocklistStamp
import com.bernaferrari.bravedns.util.Utilities.hasLocalBlocklists
import com.bernaferrari.bravedns.util.Utilities.hasRemoteBlocklists
import com.bernaferrari.bravedns.util.Utilities.showToastUiCentered
import com.bernaferrari.bravedns.util.UIUtils.openUrl
import com.bernaferrari.bravedns.viewmodel.LocalBlocklistPacksMapViewModel
import com.bernaferrari.bravedns.viewmodel.RemoteBlocklistPacksMapViewModel
import com.bernaferrari.bravedns.viewmodel.RethinkEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.RethinkLocalFileTagViewModel
import com.bernaferrari.bravedns.viewmodel.RethinkRemoteFileTagViewModel
import com.bernaferrari.bravedns.ui.compose.theme.Dimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import Logger
import Logger.LOG_TAG_UI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkLargeTopBar(
                title = title,
                subtitle = subtitle,
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
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
        ) {
            when (screenType) {
                ConfigureRethinkScreenType.DB_LIST -> {
                    RethinkListContent(
                        modifier = Modifier.weight(1f),
                        context = context,
                        persistentState = persistentState,
                        appConfig = appConfig,
                        appDownloadManager = appDownloadManager,
                        rethinkEndpointViewModel = rethinkEndpointViewModel,
                        uid = uid,
                        isMax = isMax,
                        updateAvailable = updateAvailable,
                        checkUpdateVisible = checkUpdateVisible,
                        redownloadVisible = redownloadVisible,
                        checkUpdateInProgress = checkUpdateInProgress,
                        updateInProgress = updateInProgress,
                        onMaxChanged = { isMax = it },
                        onUpdateMaxSwitchUi = { updateMaxSwitchUi() },
                        onRefreshUpdateUi = { refreshUpdateUi() },
                        onCheckUpdateInProgressChanged = { checkUpdateInProgress = it },
                        onUpdateInProgressChanged = { updateInProgress = it },
                        onCheckBlocklistUpdate = { checkBlocklistUpdate() },
                        onDownload = { timestamp, isRedownload -> download(timestamp, isRedownload) },
                        localBlocklistVersionTemplate = localBlocklistVersionTemplate,
                        downloadFailureMessage = downloadFailureMessage
                    )
                }

                else -> {
                    RethinkBlocklistContent(
                        modifier = Modifier.weight(1f),
                        context = context,
                        blocklistType = blocklistType,
                        filters = filters,
                        remoteFileTagViewModel = remoteFileTagViewModel,
                        localFileTagViewModel = localFileTagViewModel,
                        remoteBlocklistPacksMapViewModel = remoteBlocklistPacksMapViewModel,
                        localBlocklistPacksMapViewModel = localBlocklistPacksMapViewModel,
                        showDownload = showDownload,
                        showConfigure = showConfigure,
                        isDownloading = isDownloading,
                        showRemoteProgress = showRemoteProgress,
                        activeView = activeView,
                        filterLabelText = filterLabelText,
                        plusFilterTags = plusFilterTags,
                        onActiveViewChanged = { activeView = it },
                        onFilterLabelTextChanged = { filterLabelText = it },
                        onPlusFilterTagsChanged = { plusFilterTags = it },
                        onDownloadBlocklist = { downloadBlocklist(blocklistType) },
                        onCancelDownload = { cancelDownload(); onBackClick?.invoke() },
                        onApplyChanges = { applyChangesAndFinish() },
                        onRevertChanges = { revertChangesAndFinish() },
                        onRefreshBlocklistAvailability = { refreshBlocklistAvailability() },
                        filterDescriptionTemplate = filterDescriptionTemplate,
                        filterDescriptionSubgroupsTemplate = filterDescriptionSubgroupsTemplate,
                        onProcessSelectedFileTags = { stamp ->
                            scope.launch { processSelectedFileTags(stamp) }
                        },
                        onModifiedStampChanged = { modifiedStamp = it },
                        getStampValue = { getStampValue() },
                        onDownloadStart = { onDownloadStart() },
                        onDownloadFail = { onDownloadFail() },
                        onDownloadSuccess = { onDownloadSuccess() }
                    )
                }
            }
        }
    }

}

@Composable
private fun RethinkListContent(
    modifier: Modifier = Modifier,
    context: Context,
    persistentState: PersistentState,
    appConfig: AppConfig,
    appDownloadManager: AppDownloadManager,
    rethinkEndpointViewModel: RethinkEndpointViewModel,
    uid: Int,
    isMax: Boolean,
    updateAvailable: Boolean,
    checkUpdateVisible: Boolean,
    redownloadVisible: Boolean,
    checkUpdateInProgress: Boolean,
    updateInProgress: Boolean,
    onMaxChanged: (Boolean) -> Unit,
    onUpdateMaxSwitchUi: () -> Unit,
    onRefreshUpdateUi: () -> Unit,
    onCheckUpdateInProgressChanged: (Boolean) -> Unit,
    onUpdateInProgressChanged: (Boolean) -> Unit,
    onCheckBlocklistUpdate: () -> Unit,
    onDownload: (Long, Boolean) -> Unit,
    localBlocklistVersionTemplate: String,
    downloadFailureMessage: String
) {
    val scope = rememberCoroutineScope()
    val pagingItems = rethinkEndpointViewModel.rethinkEndpointList.collectAsLazyPagingItems()
    val workInfos by WorkManager.getInstance(context)
        .workInfosByTagFlow(RemoteBlocklistCoordinator.REMOTE_DOWNLOAD_WORKER)
        .collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        rethinkEndpointViewModel.setFilter(uid)
        onUpdateMaxSwitchUi()
        onRefreshUpdateUi()
    }

    LaunchedEffect(workInfos) {
        val workInfo = workInfos.getOrNull(0) ?: return@LaunchedEffect
        Logger.i(LOG_TAG_UI, "Remote blocklist worker state: ${workInfo.state}")
        when (workInfo.state) {
            WorkInfo.State.SUCCEEDED -> {
                onCheckUpdateInProgressChanged(false)
                onUpdateInProgressChanged(false)
                onRefreshUpdateUi()
            }

            WorkInfo.State.CANCELLED,
            WorkInfo.State.FAILED -> {
                onCheckUpdateInProgressChanged(false)
                onUpdateInProgressChanged(false)
                onRefreshUpdateUi()
                Utilities.showToastUiCentered(
                    context,
                    downloadFailureMessage,
                    Toast.LENGTH_SHORT
                )
            }

            else -> {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimensions.screenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
    ) {
        val actionText =
            when {
                updateAvailable -> stringResource(id = R.string.rt_chip_update_available)
                checkUpdateVisible -> stringResource(id = R.string.rt_chip_check_update)
                redownloadVisible -> stringResource(id = R.string.rt_re_download)
                else -> null
            }
        val actionInProgress =
            when {
                updateAvailable || redownloadVisible -> updateInProgress
                checkUpdateVisible -> checkUpdateInProgress
                else -> false
            }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimensions.cornerRadius3xl),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (persistentState.remoteBlocklistTimestamp != Constants.INIT_TIME_MS || actionText != null) {
                    Text(
                        text = localBlocklistVersionTemplate.format(
                            Utilities.convertLongToTime(
                                persistentState.remoteBlocklistTimestamp,
                                Constants.TIME_FORMAT_2
                            )
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (actionText != null) {
                    FilledTonalButton(
                        onClick = {
                            when {
                                updateAvailable -> {
                                    onUpdateInProgressChanged(true)
                                    onDownload(persistentState.remoteBlocklistTimestamp, false)
                                }
                                checkUpdateVisible -> {
                                    onCheckUpdateInProgressChanged(true)
                                    onCheckBlocklistUpdate()
                                }
                                redownloadVisible -> {
                                    onUpdateInProgressChanged(true)
                                    onDownload(persistentState.remoteBlocklistTimestamp, true)
                                }
                            }
                        },
                        enabled = !actionInProgress
                    ) {
                        if (actionInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp).width(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(text = actionText)
                    }
                }
            }
        }

        RethinkTwoOptionSegmentedRow(
            leftLabel = stringResource(id = R.string.radio_sky_btn),
            rightLabel = stringResource(id = R.string.radio_max_btn),
            leftSelected = !isMax,
            onLeftClick = {
                scope.launch(Dispatchers.IO) { appConfig.switchRethinkDnsToSky() }
                onMaxChanged(false)
            },
            onRightClick = {
                scope.launch(Dispatchers.IO) { appConfig.switchRethinkDnsToMax() }
                onMaxChanged(true)
            }
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimensions.cornerRadiusXl),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Text(
                text = if (isMax) {
                    stringResource(id = R.string.rethink_max_desc)
                } else {
                    stringResource(id = R.string.rethink_sky_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        Text(
            text = stringResource(R.string.dc_rethink_dns_radio),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 2.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Dimensions.spacing2xl),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(count = pagingItems.itemCount) { index ->
                val item = pagingItems[index] ?: return@items
                RethinkEndpointRow(endpoint = item, appConfig = appConfig)
            }
        }
    }
}

@Composable
private fun RethinkBlocklistContent(
    modifier: Modifier = Modifier,
    context: Context,
    blocklistType: RethinkBlocklistManager.RethinkBlocklistType,
    filters: MutableStateFlow<RethinkBlocklistState.Filters>,
    remoteFileTagViewModel: RethinkRemoteFileTagViewModel,
    localFileTagViewModel: RethinkLocalFileTagViewModel,
    remoteBlocklistPacksMapViewModel: RemoteBlocklistPacksMapViewModel,
    localBlocklistPacksMapViewModel: LocalBlocklistPacksMapViewModel,
    showDownload: Boolean,
    showConfigure: Boolean,
    isDownloading: Boolean,
    showRemoteProgress: Boolean,
    activeView: RethinkBlocklistState.BlocklistView,
    filterLabelText: String,
    plusFilterTags: List<FileTag>,
    onActiveViewChanged: (RethinkBlocklistState.BlocklistView) -> Unit,
    onFilterLabelTextChanged: (String) -> Unit,
    onPlusFilterTagsChanged: (List<FileTag>) -> Unit,
    onDownloadBlocklist: () -> Unit,
    onCancelDownload: () -> Unit,
    onApplyChanges: () -> Unit,
    onRevertChanges: () -> Unit,
    onRefreshBlocklistAvailability: () -> Unit,
    onProcessSelectedFileTags: (String) -> Unit,
    onModifiedStampChanged: (String) -> Unit,
    getStampValue: () -> String,
    onDownloadStart: () -> Unit,
    onDownloadFail: () -> Unit,
    onDownloadSuccess: () -> Unit,
    filterDescriptionTemplate: String,
    filterDescriptionSubgroupsTemplate: String
) {
    val scope = rememberCoroutineScope()
    val filterState by filters.collectAsState()
    val selectedTags by RethinkBlocklistState.selectedFileTags
        .collectAsState(initial = RethinkBlocklistState.selectedFileTags.value)

    // Observe blocklist download state
    val workManager = WorkManager.getInstance(context)
    val customDownload by workManager.workInfosByTagFlow(CUSTOM_DOWNLOAD)
        .collectAsState(initial = emptyList())
    val downloadTag by workManager.workInfosByTagFlow(DOWNLOAD_TAG)
        .collectAsState(initial = emptyList())
    val fileTag by workManager.workInfosByTagFlow(FILE_TAG)
        .collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        val stamp = getStampValue()
        onModifiedStampChanged(stamp)
        onProcessSelectedFileTags(stamp)
        onRefreshBlocklistAvailability()
        onPlusFilterTagsChanged(
            withContext(Dispatchers.IO) {
                if (blocklistType.isLocal()) localFileTagViewModel.allFileTags() else remoteFileTagViewModel.allFileTags()
            }
        )
    }

    LaunchedEffect(filterState) {
        val filter = filterState
        if (blocklistType.isRemote()) {
            remoteFileTagViewModel.setFilter(filter)
        } else {
            localFileTagViewModel.setFilter(filter)
        }
        onFilterLabelTextChanged(
            buildFilterDescription(
                filter,
                filterDescriptionTemplate,
                filterDescriptionSubgroupsTemplate
            )
        )
    }

    LaunchedEffect(selectedTags) {
        val stamp = getStamp(selectedTags, blocklistType)
        onModifiedStampChanged(stamp)
    }

    LaunchedEffect(customDownload) {
        val workInfo = customDownload.getOrNull(0) ?: return@LaunchedEffect
        when (workInfo.state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.RUNNING -> onDownloadStart()

            WorkInfo.State.SUCCEEDED -> onDownloadSuccess()
            WorkInfo.State.CANCELLED,
            WorkInfo.State.FAILED -> onDownloadFail()

            else -> Unit
        }
    }

    LaunchedEffect(downloadTag) {
        val workInfo = downloadTag.getOrNull(0) ?: return@LaunchedEffect
        when (workInfo.state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.RUNNING -> onDownloadStart()

            WorkInfo.State.CANCELLED,
            WorkInfo.State.FAILED -> onDownloadFail()

            else -> Unit
        }
    }

    LaunchedEffect(fileTag) {
        val workInfo = fileTag.getOrNull(0) ?: return@LaunchedEffect
        when (workInfo.state) {
            WorkInfo.State.SUCCEEDED -> onDownloadSuccess()
            WorkInfo.State.CANCELLED,
            WorkInfo.State.FAILED -> onDownloadFail()

            else -> Unit
        }
    }

    fun updateFilters(change: (RethinkBlocklistState.Filters) -> Unit) {
        val next = RethinkBlocklistState.Filters().also { copy ->
            copy.query = filterState.query
            copy.filterSelected = filterState.filterSelected
            copy.subGroups.addAll(filterState.subGroups)
        }
        change(next)
        filters.value = next
    }

    fun restoreStampIfPresent(query: String): Boolean {
        if (!query.contains(Constants.RETHINKDNS_DOMAIN)) return false
        val stamp = query.split("/").firstOrNull { it.contains("$RETHINK_STAMP_VERSION:") && isBase64(it) } ?: return false
        scope.launch(Dispatchers.IO) { onProcessSelectedFileTags(stamp) }
        showToastUiCentered(context, "Blocklists restored", Toast.LENGTH_SHORT)
        return true
    }

    fun updateBlocklistSelection(tagIds: Set<Int>, isSelected: Boolean) {
        if (tagIds.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            if (blocklistType.isLocal()) {
                RethinkBlocklistManager.updateFiletagsLocal(tagIds, if (isSelected) 1 else 0)
                RethinkBlocklistState.updateFileTagList(RethinkBlocklistManager.getSelectedFileTagsLocal().toSet())
            } else {
                RethinkBlocklistManager.updateFiletagsRemote(tagIds, if (isSelected) 1 else 0)
                RethinkBlocklistState.updateFileTagList(RethinkBlocklistManager.getSelectedFileTagsRemote().toSet())
            }
        }
    }

    val selected = selectedTags
    val packs: RethinkBlocklistFeed<RethinkBlocklistPack>
    val tags: RethinkBlocklistFeed<RethinkBlocklistFileTag>
    if (blocklistType.isLocal()) {
        packs = AndroidBlocklistFeed(localBlocklistPacksMapViewModel.simpleTags.collectAsLazyPagingItems()) { it.toEditorPack(context, selected) }
        tags = AndroidBlocklistFeed(localFileTagViewModel.localFiletags.collectAsLazyPagingItems()) { it.toEditorTag(context, selected) }
    } else {
        packs = AndroidBlocklistFeed(remoteBlocklistPacksMapViewModel.simpleTags.collectAsLazyPagingItems()) { it.toEditorPack(context, selected) }
        tags = AndroidBlocklistFeed(remoteFileTagViewModel.remoteFileTags.collectAsLazyPagingItems()) { it.toEditorTag(context, selected) }
    }
    val availableSubgroups = plusFilterTags.map { it.subg.trim() }.filter { it.isNotBlank() }.distinct().sorted()

    RethinkBlocklistEditor(
        packs = packs,
        fileTags = tags,
        activeView = if (activeView == RethinkBlocklistState.BlocklistView.PACKS) RethinkBlocklistEditorView.Packs else RethinkBlocklistEditorView.Advanced,
        query = filterState.query.replace("%", ""),
        selectionFilter = if (filterState.filterSelected == RethinkBlocklistState.BlocklistSelectionFilter.SELECTED) RethinkBlocklistSelectionFilter.Selected else RethinkBlocklistSelectionFilter.All,
        selectedSubgroups = filterState.subGroups.toSet(),
        availableSubgroups = availableSubgroups,
        showDownload = showDownload,
        showEditor = showConfigure,
        isDownloading = isDownloading || showRemoteProgress,
        strings = RethinkBlocklistEditorStrings(
            downloadDescription = stringResource(R.string.rt_download_desc),
            download = stringResource(R.string.rt_download),
            cancel = stringResource(R.string.lbl_cancel),
            packs = stringResource(R.string.rt_list_simple_btn_txt),
            advanced = stringResource(R.string.lbl_advanced),
            search = stringResource(R.string.lbl_search),
            clearSearch = stringResource(R.string.cd_clear_search),
            all = stringResource(R.string.lbl_all),
            selected = stringResource(R.string.rt_filter_parent_selected),
            filter = stringResource(R.string.cd_filter),
            filterHint = filterLabelText.ifEmpty { stringResource(R.string.rt_filter_hint) },
            apply = stringResource(R.string.lbl_apply),
            discard = stringResource(R.string.notif_dialog_pause_dialog_negative),
            blocklistCount = { context.getString(R.string.rsv_blocklist_count_text, it.toString()) },
            entries = { context.getString(R.string.dc_entries, it.toString()) },
        ),
        onViewChange = { onActiveViewChanged(if (it == RethinkBlocklistEditorView.Packs) RethinkBlocklistState.BlocklistView.PACKS else RethinkBlocklistState.BlocklistView.ADVANCED) },
        onQueryChange = { query -> if (!restoreStampIfPresent(query)) updateFilters { it.query = formatQuery(query) } },
        onSelectionFilterChange = { selection -> updateFilters { it.filterSelected = if (selection == RethinkBlocklistSelectionFilter.Selected) RethinkBlocklistState.BlocklistSelectionFilter.SELECTED else RethinkBlocklistState.BlocklistSelectionFilter.ALL } },
        onSubgroupsChange = { groups -> updateFilters { it.subGroups.clear(); it.subGroups.addAll(groups) } },
        onDownload = onDownloadBlocklist,
        onCancelDownload = onCancelDownload,
        onPackToggle = { pack, isSelected -> updateBlocklistSelection(pack.tagIds, isSelected) },
        onFileTagToggle = { tag, isSelected -> updateBlocklistSelection(tag.tagIds, isSelected) },
        onOpenUrl = { openUrl(context, it) },
        onApply = onApplyChanges,
        onDiscard = onRevertChanges,
        modifier = modifier,
    )
}

/** Bridges Android Paging to the target-neutral lazy editor without exposing Paging to commonMain. */
private class AndroidBlocklistFeed<Source : Any, Ui>(
    private val items: LazyPagingItems<Source>,
    private val map: (Source) -> Ui?,
) : RethinkBlocklistFeed<Ui> {
    override val itemCount: Int get() = items.itemCount
    override fun get(index: Int): Ui? = items[index]?.let(map)
}

private fun LocalBlocklistPacksMap.toEditorPack(context: Context, selectedTags: Set<Int>): RethinkBlocklistPack? {
    if (pack.isBlank() || pack.contains(DEAD_PACK)) return null
    return RethinkBlocklistPack(
        id = "$pack:$level",
        group = toRethinkGroup(context),
        name = pack,
        blocklistCount = blocklistIds.size,
        selected = selectedTags.containsAll(blocklistIds),
        tagIds = blocklistIds.toSet(),
    )
}

private fun RemoteBlocklistPacksMap.toEditorPack(context: Context, selectedTags: Set<Int>): RethinkBlocklistPack? {
    if (pack.isBlank() || pack.contains(DEAD_PACK)) return null
    return RethinkBlocklistPack(
        id = "$pack:$level",
        group = toRethinkGroup(context),
        name = pack,
        blocklistCount = blocklistIds.size,
        selected = selectedTags.containsAll(blocklistIds),
        tagIds = blocklistIds.toSet(),
    )
}

private fun RethinkLocalFileTag.toEditorTag(context: Context, selectedTags: Set<Int>) = RethinkBlocklistFileTag(
    id = value.toString(),
    group = toRethinkGroup(context),
    subgroup = subg,
    name = vname,
    entries = entries,
    level = level?.firstOrNull(),
    url = url.firstOrNull(),
    selected = value in selectedTags,
    tagIds = setOf(value),
)

private fun RethinkRemoteFileTag.toEditorTag(context: Context, selectedTags: Set<Int>) = RethinkBlocklistFileTag(
    id = value.toString(),
    group = toRethinkGroup(context),
    subgroup = subg,
    name = vname,
    entries = entries,
    level = level?.firstOrNull(),
    url = url.firstOrNull(),
    selected = value in selectedTags,
    tagIds = setOf(value),
)

private suspend fun updateSelectedFileTags(
    selectedTags: MutableSet<Int>,
    blocklistType: RethinkBlocklistManager.RethinkBlocklistType
) {
    if (selectedTags.isEmpty()) {
        if (blocklistType.isLocal()) {
            RethinkBlocklistManager.clearTagsSelectionLocal()
        } else {
            RethinkBlocklistManager.clearTagsSelectionRemote()
        }
        return
    }

    if (blocklistType.isLocal()) {
        RethinkBlocklistManager.clearTagsSelectionLocal()
        RethinkBlocklistManager.updateFiletagsLocal(selectedTags, 1)
        val list = RethinkBlocklistManager.getSelectedFileTagsLocal().toSet()
        RethinkBlocklistState.updateFileTagList(list)
    } else {
        RethinkBlocklistManager.clearTagsSelectionRemote()
        RethinkBlocklistManager.updateFiletagsRemote(selectedTags, 1)
        val list = RethinkBlocklistManager.getSelectedFileTagsRemote().toSet()
        RethinkBlocklistState.updateFileTagList(list)
    }
}

private fun formatQuery(q: String): String {
    return "%$q%"
}

private fun isBase64(stamp: String): Boolean {
    val whitespaceRegex = "\\s"
    val pattern = Pattern.compile(
        "^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$"
    )

    val versionSplit = stamp.split(":").getOrNull(1) ?: return false
    if (versionSplit.isEmpty()) return false

    val result = versionSplit.replace(whitespaceRegex, "")
    return pattern.matcher(result).matches()
}

private fun buildFilterDescription(
    filter: RethinkBlocklistState.Filters,
    filterDescriptionTemplate: String,
    filterDescriptionSubgroupsTemplate: String
): String {
    val text = if (filter.subGroups.isEmpty()) {
        filterDescriptionTemplate.format(filter.filterSelected.name.lowercase())
    } else {
        filterDescriptionSubgroupsTemplate.format(
            filter.filterSelected.name.lowercase(),
            "",
            filter.subGroups
        )
    }
    return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
}
