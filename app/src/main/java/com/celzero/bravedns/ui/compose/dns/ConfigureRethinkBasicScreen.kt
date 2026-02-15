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
package com.celzero.bravedns.ui.compose.dns

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.LocalAdvancedBlocklistRow
import com.celzero.bravedns.adapter.LocalSimpleBlocklistRow
import com.celzero.bravedns.adapter.RemoteAdvancedBlocklistRow
import com.celzero.bravedns.adapter.RemoteSimpleBlocklistRow
import com.celzero.bravedns.adapter.RethinkEndpointRow
import com.celzero.bravedns.customdownloader.LocalBlocklistCoordinator.Companion.CUSTOM_DOWNLOAD
import com.celzero.bravedns.customdownloader.RemoteBlocklistCoordinator
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.data.FileTag
import com.celzero.bravedns.database.LocalBlocklistPacksMap
import com.celzero.bravedns.database.RemoteBlocklistPacksMap
import com.celzero.bravedns.database.RethinkLocalFileTag
import com.celzero.bravedns.database.RethinkRemoteFileTag
import com.celzero.bravedns.download.AppDownloadManager
import com.celzero.bravedns.download.DownloadConstants.Companion.DOWNLOAD_TAG
import com.celzero.bravedns.download.DownloadConstants.Companion.FILE_TAG
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.RethinkBlocklistManager
import com.celzero.bravedns.service.RethinkBlocklistManager.getStamp
import com.celzero.bravedns.service.RethinkBlocklistManager.getTagsFromStamp
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.rethink.RethinkBlocklistState
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.DEAD_PACK
import com.celzero.bravedns.util.Constants.Companion.DEFAULT_RDNS_REMOTE_DNS_NAMES
import com.celzero.bravedns.util.Constants.Companion.MAX_ENDPOINT
import com.celzero.bravedns.util.Constants.Companion.RETHINK_STAMP_VERSION
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getRemoteBlocklistStamp
import com.celzero.bravedns.util.Utilities.hasLocalBlocklists
import com.celzero.bravedns.util.Utilities.hasRemoteBlocklists
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.viewmodel.LocalBlocklistPacksMapViewModel
import com.celzero.bravedns.viewmodel.RemoteBlocklistPacksMapViewModel
import com.celzero.bravedns.viewmodel.RethinkEndpointViewModel
import com.celzero.bravedns.viewmodel.RethinkLocalFileTagViewModel
import com.celzero.bravedns.viewmodel.RethinkRemoteFileTagViewModel
import io.github.aakira.napier.Napier
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

    val blocklistType = remember {
        if (screenType == ConfigureRethinkScreenType.LOCAL) {
            RethinkBlocklistManager.RethinkBlocklistType.LOCAL
        } else {
            RethinkBlocklistManager.RethinkBlocklistType.REMOTE
        }
    }

    val filters = remember { MutableLiveData(RethinkBlocklistState.Filters()) }

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
    var showPlusFilterSheet by remember { mutableStateOf(false) }
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
        Napier.i("set stamp for blocklist type: ${blocklistType.name} with $stamp")
        if (stamp == null) {
            Napier.i("stamp is null")
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
            context.getString(R.string.download_update_dialog_message_success),
            Toast.LENGTH_SHORT
        )
    }

    fun handleDownloadStatus(status: AppDownloadManager.DownloadManagerStatus) {
        when (status) {
            AppDownloadManager.DownloadManagerStatus.IN_PROGRESS -> { }
            AppDownloadManager.DownloadManagerStatus.STARTED -> onDownloadStart()
            AppDownloadManager.DownloadManagerStatus.FAILURE -> onDownloadFail()
            AppDownloadManager.DownloadManagerStatus.NOT_AVAILABLE -> {
                showToastUiCentered(
                    context,
                    "Download latest version to update the blocklists",
                    Toast.LENGTH_SHORT
                )
            }
            else -> { }
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
        Napier.d(
            "Update available? newest: ${persistentState.newestRemoteBlocklistTimestamp}, available: ${persistentState.remoteBlocklistTimestamp}"
        )
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
                        context.getString(R.string.download_update_dialog_failure_message),
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
        AlertDialog(
            onDismissRequest = { showLockdownDialog = false },
            title = { Text(text = stringResource(R.string.lockdown_download_enable_inapp)) },
            text = { Text(text = stringResource(R.string.lockdown_download_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLockdownDialog = false
                        persistentState.useCustomDownloadManager = true
                        downloadBlocklist(type)
                    }
                ) {
                    Text(text = stringResource(R.string.lockdown_download_enable_inapp))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLockdownDialog = false
                        proceedWithBlocklistDownload(type)
                    }
                ) {
                    Text(text = stringResource(R.string.lbl_cancel))
                }
            }
        )
    }

    if (showApplyChangesDialog) {
        AlertDialog(
            onDismissRequest = { showApplyChangesDialog = false },
            title = { Text(text = stringResource(R.string.rt_dialog_title)) },
            text = { Text(text = stringResource(R.string.rt_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showApplyChangesDialog = false
                        setStamp(modifiedStamp)
                        onBackClick?.invoke()
                    }
                ) {
                    Text(text = stringResource(R.string.lbl_apply))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showApplyChangesDialog = false }) {
                        Text(text = stringResource(R.string.rt_dialog_neutral))
                    }
                    TextButton(
                        onClick = {
                            showApplyChangesDialog = false
                            onBackClick?.invoke()
                        }
                    ) {
                        Text(text = stringResource(R.string.notif_dialog_pause_dialog_negative))
                    }
                }
            }
        )
    }

    val title = when (screenType) {
        ConfigureRethinkScreenType.DB_LIST -> stringResource(R.string.dc_rethink_dns_radio)
        ConfigureRethinkScreenType.LOCAL -> stringResource(R.string.dc_local_block_heading)
        ConfigureRethinkScreenType.REMOTE -> stringResource(R.string.dc_rethink_dns_radio)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = {
                            if (screenType != ConfigureRethinkScreenType.DB_LIST && isStampChanged()) {
                                showApplyChangesDialog = true
                            } else {
                                onBackClick()
                            }
                        }) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_arrow_back_24),
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when (screenType) {
            ConfigureRethinkScreenType.DB_LIST -> {
                RethinkListContent(
                    modifier = Modifier.padding(paddingValues),
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
                    onDownload = { timestamp, isRedownload -> download(timestamp, isRedownload) }
                )
            }
            else -> {
                RethinkBlocklistContent(
                    modifier = Modifier.padding(paddingValues),
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
                    showPlusFilterSheet = showPlusFilterSheet,
                    plusFilterTags = plusFilterTags,
                    onActiveViewChanged = { activeView = it },
                    onFilterLabelTextChanged = { filterLabelText = it },
                    onShowPlusFilterSheetChanged = { showPlusFilterSheet = it },
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
                    onDownloadSuccess = { onDownloadSuccess() }
                )
            }
        }
    }

    if (showPlusFilterSheet) {
        RethinkPlusFilterSheet(
            context = context,
            fileTags = plusFilterTags,
            filters = filters,
            onDismiss = { showPlusFilterSheet = false }
        )
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
    onDownload: (Long, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagingItems = rethinkEndpointViewModel.rethinkEndpointList.asFlow().collectAsLazyPagingItems()
    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosByTagLiveData(RemoteBlocklistCoordinator.REMOTE_DOWNLOAD_WORKER)
        .asFlow()
        .collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        rethinkEndpointViewModel.setFilter(uid)
        onUpdateMaxSwitchUi()
        onRefreshUpdateUi()
    }

    LaunchedEffect(workInfos) {
        val workInfo = workInfos.getOrNull(0) ?: return@LaunchedEffect
        Napier.i("Remote blocklist worker state: ${workInfo.state}")
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
                    context.getString(R.string.download_update_dialog_failure_message),
                    Toast.LENGTH_SHORT
                )
            }
            else -> { }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (persistentState.remoteBlocklistTimestamp != Constants.INIT_TIME_MS) {
                Text(
                    text = context.getString(
                        R.string.settings_local_blocklist_version,
                        Utilities.convertLongToTime(
                            persistentState.remoteBlocklistTimestamp,
                            Constants.TIME_FORMAT_2
                        )
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (updateAvailable) {
                Button(
                    onClick = {
                        onUpdateInProgressChanged(true)
                        onDownload(persistentState.remoteBlocklistTimestamp, false)
                    },
                    enabled = !updateInProgress
                ) {
                    if (updateInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp).width(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text = stringResource(id = R.string.rt_chip_update_available))
                }
            } else if (checkUpdateVisible) {
                Button(
                    onClick = {
                        onCheckUpdateInProgressChanged(true)
                        onCheckBlocklistUpdate()
                    },
                    enabled = !checkUpdateInProgress
                ) {
                    if (checkUpdateInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp).width(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text = stringResource(id = R.string.rt_chip_check_update))
                }
            } else if (redownloadVisible) {
                Button(
                    onClick = {
                        onUpdateInProgressChanged(true)
                        onDownload(persistentState.remoteBlocklistTimestamp, true)
                    },
                    enabled = !updateInProgress
                ) {
                    if (updateInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp).width(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text = stringResource(id = R.string.rt_re_download))
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = !isMax,
                onClick = {
                    scope.launch(Dispatchers.IO) { appConfig.switchRethinkDnsToSky() }
                    onMaxChanged(false)
                },
                label = { Text(text = stringResource(id = R.string.radio_sky_btn)) }
            )
            FilterChip(
                selected = isMax,
                onClick = {
                    scope.launch(Dispatchers.IO) { appConfig.switchRethinkDnsToMax() }
                    onMaxChanged(true)
                },
                label = { Text(text = stringResource(id = R.string.radio_max_btn)) }
            )
        }

        Text(
            text = if (isMax) {
                stringResource(id = R.string.rethink_max_desc)
            } else {
                stringResource(id = R.string.rethink_sky_desc)
            },
            style = MaterialTheme.typography.bodySmall
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
    filters: MutableLiveData<RethinkBlocklistState.Filters>,
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
    showPlusFilterSheet: Boolean,
    plusFilterTags: List<FileTag>,
    onActiveViewChanged: (RethinkBlocklistState.BlocklistView) -> Unit,
    onFilterLabelTextChanged: (String) -> Unit,
    onShowPlusFilterSheetChanged: (Boolean) -> Unit,
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
    onDownloadSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val filterState by filters.asFlow().collectAsState(initial = filters.value)
    val selectedTags by RethinkBlocklistState.selectedFileTags.asFlow()
        .collectAsState(initial = RethinkBlocklistState.selectedFileTags.value)

    // Observe blocklist download state
    val workManager = WorkManager.getInstance(context)
    val customDownload by workManager.getWorkInfosByTagLiveData(CUSTOM_DOWNLOAD).asFlow()
        .collectAsState(initial = emptyList())
    val downloadTag by workManager.getWorkInfosByTagLiveData(DOWNLOAD_TAG).asFlow()
        .collectAsState(initial = emptyList())
    val fileTag by workManager.getWorkInfosByTagLiveData(FILE_TAG).asFlow()
        .collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        val stamp = getStampValue()
        onModifiedStampChanged(stamp)
        onProcessSelectedFileTags(stamp)
        onRefreshBlocklistAvailability()
    }

    LaunchedEffect(filterState) {
        val filter = filterState ?: return@LaunchedEffect
        if (blocklistType.isRemote()) {
            remoteFileTagViewModel.setFilter(filter)
        } else {
            localFileTagViewModel.setFilter(filter)
        }
        onFilterLabelTextChanged(buildFilterDescription(context, filter))
    }

    LaunchedEffect(selectedTags) {
        val tags = selectedTags ?: emptySet<Int>()
        val stamp = getStamp(tags, blocklistType)
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

    fun isRethinkStampSearch(t: String): Boolean {
        if (!t.contains(Constants.RETHINKDNS_DOMAIN)) return false

        val split = t.split("/")
        split.forEach {
            if (it.contains("$RETHINK_STAMP_VERSION:") && isBase64(it)) {
                scope.launch(Dispatchers.IO) { onProcessSelectedFileTags(it) }
                showToastUiCentered(context, "Blocklists restored", Toast.LENGTH_SHORT)
                return true
            }
        }
        return false
    }

    fun addQueryToFilters(query: String) {
        val current = filters.value
        if (current == null) {
            val temp = RethinkBlocklistState.Filters()
            temp.query = formatQuery(query)
            filters.postValue(temp)
            return
        }
        current.query = formatQuery(query)
        filters.postValue(current)
    }

    fun applyFilter(tag: Any) {
        val a = filters.value ?: RethinkBlocklistState.Filters()
        when (tag) {
            RethinkBlocklistState.BlocklistSelectionFilter.ALL.id -> {
                a.filterSelected = RethinkBlocklistState.BlocklistSelectionFilter.ALL
            }
            RethinkBlocklistState.BlocklistSelectionFilter.SELECTED.id -> {
                a.filterSelected = RethinkBlocklistState.BlocklistSelectionFilter.SELECTED
            }
        }
        filters.postValue(a)
    }

    fun openFilterBottomSheet() {
        scope.launch {
            val tags = withContext(Dispatchers.IO) {
                if (blocklistType.isLocal()) {
                    localFileTagViewModel.allFileTags()
                } else {
                    remoteFileTagViewModel.allFileTags()
                }
            }
            onPlusFilterTagsChanged(tags)
            onShowPlusFilterSheetChanged(true)
        }
    }

    fun toggleRemoteFiletag(filetag: RethinkRemoteFileTag, selected: Boolean) {
        scope.launch(Dispatchers.IO) {
            filetag.isSelected = selected
            RethinkBlocklistManager.updateFiletagRemote(filetag)
            val list = RethinkBlocklistManager.getSelectedFileTagsRemote().toSet()
            RethinkBlocklistState.updateFileTagList(list)
        }
    }

    fun toggleLocalFiletag(filetag: RethinkLocalFileTag, selected: Boolean) {
        scope.launch(Dispatchers.IO) {
            filetag.isSelected = selected
            RethinkBlocklistManager.updateFiletagLocal(filetag)
            val list = RethinkBlocklistManager.getSelectedFileTagsLocal().toSet()
            RethinkBlocklistState.updateFileTagList(list)
        }
    }

    fun toggleLocalSimplePack(map: LocalBlocklistPacksMap, selected: Boolean) {
        scope.launch(Dispatchers.IO) {
            RethinkBlocklistManager.updateFiletagsLocal(map.blocklistIds.toSet(), if (selected) 1 else 0)
            val list = RethinkBlocklistManager.getSelectedFileTagsLocal().toSet()
            RethinkBlocklistState.updateFileTagList(list)
        }
    }

    fun toggleRemoteSimplePack(map: RemoteBlocklistPacksMap, selected: Boolean) {
        scope.launch(Dispatchers.IO) {
            RethinkBlocklistManager.updateFiletagsRemote(map.blocklistIds.toSet(), if (selected) 1 else 0)
            val list = RethinkBlocklistManager.getSelectedFileTagsRemote().toSet()
            RethinkBlocklistState.updateFileTagList(list)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showDownload) {
            Text(text = stringResource(id = R.string.rt_download_desc))
            if (showRemoteProgress || isDownloading) {
                CircularProgressIndicator()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDownloadBlocklist,
                    enabled = !isDownloading
                ) {
                    Text(text = stringResource(id = R.string.rt_download))
                }
                TextButton(onClick = onCancelDownload) {
                    Text(text = stringResource(id = R.string.lbl_cancel))
                }
            }
        }

        if (showConfigure) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = activeView == RethinkBlocklistState.BlocklistView.PACKS,
                    onClick = { onActiveViewChanged(RethinkBlocklistState.BlocklistView.PACKS) },
                    label = { Text(text = stringResource(id = R.string.rt_list_simple_btn_txt)) }
                )
                FilterChip(
                    selected = activeView == RethinkBlocklistState.BlocklistView.ADVANCED,
                    onClick = { onActiveViewChanged(RethinkBlocklistState.BlocklistView.ADVANCED) },
                    label = { Text(text = stringResource(id = R.string.lbl_advanced)) }
                )
            }

            if (activeView == RethinkBlocklistState.BlocklistView.ADVANCED) {
                OutlinedTextField(
                    value = filterState?.query?.replace("%", "") ?: "",
                    onValueChange = { query ->
                        if (!isRethinkStampSearch(query)) {
                            addQueryToFilters(query)
                        }
                    },
                    label = { Text(text = stringResource(id = R.string.search_rethinkplus_file_tag)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterState?.filterSelected == RethinkBlocklistState.BlocklistSelectionFilter.ALL,
                        onClick = { applyFilter(RethinkBlocklistState.BlocklistSelectionFilter.ALL.id) },
                        label = { Text(text = stringResource(id = R.string.lbl_all)) }
                    )
                    FilterChip(
                        selected = filterState?.filterSelected == RethinkBlocklistState.BlocklistSelectionFilter.SELECTED,
                        onClick = { applyFilter(RethinkBlocklistState.BlocklistSelectionFilter.SELECTED.id) },
                        label = { Text(text = stringResource(id = R.string.rt_filter_parent_selected)) }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { openFilterBottomSheet() }) {
                        Icon(imageVector = Icons.Filled.FilterList, contentDescription = null)
                    }
                }
                Text(
                    text = filterLabelText.ifEmpty { stringResource(id = R.string.rt_filter_hint) },
                    style = MaterialTheme.typography.bodySmall
                )

                if (blocklistType.isLocal()) {
                    val advancedItems = localFileTagViewModel.localFiletags.asFlow().collectAsLazyPagingItems()
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(count = advancedItems.itemCount) { index ->
                            val item = advancedItems[index] ?: return@items
                            val previous = if (index > 0) advancedItems.peek(index - 1) else null
                            val showHeader = previous?.group != item.group
                            LocalAdvancedBlocklistRow(
                                filetag = item,
                                showHeader = showHeader
                            ) { isSelected ->
                                toggleLocalFiletag(item, isSelected)
                            }
                        }
                    }
                } else {
                    val advancedItems = remoteFileTagViewModel.remoteFileTags.asFlow().collectAsLazyPagingItems()
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(count = advancedItems.itemCount) { index ->
                            val item = advancedItems[index] ?: return@items
                            val previous = if (index > 0) advancedItems.peek(index - 1) else null
                            val showHeader = previous?.group != item.group
                            RemoteAdvancedBlocklistRow(
                                filetag = item,
                                showHeader = showHeader
                            ) { isSelected ->
                                toggleRemoteFiletag(item, isSelected)
                            }
                        }
                    }
                }
            } else {
                if (blocklistType.isLocal()) {
                    val simpleItems = localBlocklistPacksMapViewModel.simpleTags.asFlow().collectAsLazyPagingItems()
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(count = simpleItems.itemCount) { index ->
                            val item = simpleItems[index] ?: return@items
                            val previous = if (index > 0) simpleItems.peek(index - 1) else null
                            val showHeader = previous?.group != item.group
                            val valid = !item.pack.contains(DEAD_PACK) && item.pack.isNotEmpty()
                            if (!valid) return@items
                            LocalSimpleBlocklistRow(
                                map = item,
                                showHeader = showHeader
                            ) { isSelected ->
                                toggleLocalSimplePack(item, isSelected)
                            }
                        }
                    }
                } else {
                    val simpleItems = remoteBlocklistPacksMapViewModel.simpleTags.asFlow().collectAsLazyPagingItems()
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(count = simpleItems.itemCount) { index ->
                            val item = simpleItems[index] ?: return@items
                            val previous = if (index > 0) simpleItems.peek(index - 1) else null
                            val showHeader = previous?.group != item.group
                            val valid = !item.pack.contains(DEAD_PACK) && item.pack.isNotEmpty()
                            if (!valid) return@items
                            RemoteSimpleBlocklistRow(
                                map = item,
                                showHeader = showHeader
                            ) { isSelected ->
                                toggleRemoteSimplePack(item, isSelected)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = onRevertChanges
                ) {
                    Text(text = stringResource(id = R.string.notif_dialog_pause_dialog_negative))
                }
                Button(modifier = Modifier.weight(1f), onClick = onApplyChanges) {
                    Text(text = stringResource(id = R.string.lbl_apply))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RethinkPlusFilterSheet(
    context: Context,
    fileTags: List<FileTag>,
    filters: MutableLiveData<RethinkBlocklistState.Filters>,
    onDismiss: () -> Unit
) {
    val subGroups = remember(fileTags) {
        fileTags.map { it.subg }.filter { it.isNotBlank() }.distinct()
    }
    val initialFilters = filters.value
    var selectedSubgroups by remember { mutableStateOf(initialFilters?.subGroups?.toSet() ?: emptySet()) }
    val borderColor = MaterialTheme.colorScheme.outline

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
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
                text = stringResource(R.string.bsrf_sub_group_heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 5.dp, end = 5.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subGroups.forEach { label ->
                    val selected = selectedSubgroups.contains(label)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedSubgroups = if (selected) {
                                selectedSubgroups - label
                            } else {
                                selectedSubgroups + label
                            }
                        },
                        label = { Text(text = label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        filters.postValue(RethinkBlocklistState.Filters())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.bsrf_clear_filter))
                }

                Button(
                    onClick = {
                        val updated = initialFilters ?: RethinkBlocklistState.Filters()
                        updated.subGroups.clear()
                        updated.subGroups.addAll(selectedSubgroups)
                        filters.postValue(updated)
                        onDismiss()
                    },
                    modifier = Modifier.weight(2f)
                ) {
                    Text(text = stringResource(R.string.lbl_apply))
                }
            }

            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}

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

private fun buildFilterDescription(context: Context, filter: RethinkBlocklistState.Filters): String {
    val text = if (filter.subGroups.isEmpty()) {
        context.getString(R.string.rt_filter_desc, filter.filterSelected.name.lowercase())
    } else {
        context.getString(
            R.string.rt_filter_desc_subgroups,
            filter.filterSelected.name.lowercase(),
            "",
            filter.subGroups
        )
    }
    return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
}
