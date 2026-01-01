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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.LocalAdvancedViewAdapter
import com.celzero.bravedns.adapter.LocalSimpleViewAdapter
import com.celzero.bravedns.adapter.RemoteAdvancedViewAdapter
import com.celzero.bravedns.adapter.RemoteSimpleViewAdapter
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
import com.celzero.bravedns.ui.bottomsheet.RethinkPlusFilterDialog
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.rethink.RethinkBlocklistFilterHost
import com.celzero.bravedns.ui.rethink.RethinkBlocklistState
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.DEAD_PACK
import com.celzero.bravedns.util.Constants.Companion.DEFAULT_RDNS_REMOTE_DNS_NAMES
import com.celzero.bravedns.util.Constants.Companion.MAX_ENDPOINT
import com.celzero.bravedns.util.Constants.Companion.RETHINK_STAMP_VERSION
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getRemoteBlocklistStamp
import com.celzero.bravedns.util.Utilities.hasLocalBlocklists
import com.celzero.bravedns.util.Utilities.hasRemoteBlocklists
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.LocalBlocklistPacksMapViewModel
import com.celzero.bravedns.viewmodel.RemoteBlocklistPacksMapViewModel
import com.celzero.bravedns.viewmodel.RethinkEndpointViewModel
import com.celzero.bravedns.viewmodel.RethinkLocalFileTagViewModel
import com.celzero.bravedns.viewmodel.RethinkRemoteFileTagViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.regex.Pattern

class ConfigureRethinkBasicActivity : AppCompatActivity(), RethinkBlocklistFilterHost {
    private val persistentState by inject<PersistentState>()
    private val appDownloadManager by inject<AppDownloadManager>()
    private val appConfig by inject<AppConfig>()

    private val rethinkEndpointViewModel: RethinkEndpointViewModel by viewModel()
    private val remoteFileTagViewModel: RethinkRemoteFileTagViewModel by viewModel()
    private val localFileTagViewModel: RethinkLocalFileTagViewModel by viewModel()
    private val remoteBlocklistPacksMapViewModel: RemoteBlocklistPacksMapViewModel by viewModel()
    private val localBlocklistPacksMapViewModel: LocalBlocklistPacksMapViewModel by viewModel()

    private var blocklistType: RethinkBlocklistManager.RethinkBlocklistType =
        RethinkBlocklistManager.RethinkBlocklistType.REMOTE
    private var remoteName: String = ""
    private var remoteUrl: String = ""
    private var modifiedStamp: String = ""

    private val filters = MutableLiveData<RethinkBlocklistState.Filters>()

    private var showDownload by mutableStateOf(false)
    private var showConfigure by mutableStateOf(false)
    private var isDownloading by mutableStateOf(false)
    private var showRemoteProgress by mutableStateOf(false)
    private var activeView by mutableStateOf(RethinkBlocklistState.BlocklistView.PACKS)
    private var updateAvailable by mutableStateOf(false)
    private var checkUpdateVisible by mutableStateOf(false)
    private var redownloadVisible by mutableStateOf(false)
    private var checkUpdateInProgress by mutableStateOf(false)
    private var updateInProgress by mutableStateOf(false)
    private var isMax by mutableStateOf(false)
    private var filterLabelText by mutableStateOf("")

    private var uid: Int = Constants.MISSING_UID

    enum class FragmentLoader {
        REMOTE,
        LOCAL,
        DB_LIST
    }

    companion object {
        const val INTENT = "RethinkDns_Intent"
        const val RETHINK_BLOCKLIST_TYPE = "RethinkBlocklistType"
        const val RETHINK_BLOCKLIST_NAME = "RethinkBlocklistName"
        const val RETHINK_BLOCKLIST_URL = "RethinkBlocklistUrl"
        const val UID = "UID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        val type = intent.getIntExtra(INTENT, FragmentLoader.REMOTE.ordinal)
        val screen = FragmentLoader.entries[type]

        uid = intent.getIntExtra(UID, Constants.MISSING_UID)
        remoteName = intent.getStringExtra(RETHINK_BLOCKLIST_NAME) ?: ""
        remoteUrl = intent.getStringExtra(RETHINK_BLOCKLIST_URL) ?: ""

        blocklistType =
            if (screen == FragmentLoader.LOCAL) {
                RethinkBlocklistManager.RethinkBlocklistType.LOCAL
            } else {
                RethinkBlocklistManager.RethinkBlocklistType.REMOTE
            }

        filters.value = RethinkBlocklistState.Filters()

        if (screen != FragmentLoader.DB_LIST) {
            onBackPressedDispatcher.addCallback(this) {
                if (!isStampChanged()) {
                    finish()
                    return@addCallback
                }
                showApplyChangesDialog()
            }
        }

        setContent {
            RethinkTheme {
                RethinkBasicContent(screen)
            }
        }
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @Composable
    private fun RethinkBasicContent(screen: FragmentLoader) {
        val lifecycleOwner = LocalLifecycleOwner.current

        when (screen) {
            FragmentLoader.DB_LIST -> RethinkListContent(lifecycleOwner)
            else -> RethinkBlocklistContent(lifecycleOwner)
        }
    }

    @Composable
    private fun RethinkListContent(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        val pagingItems = rethinkEndpointViewModel.rethinkEndpointList.asFlow().collectAsLazyPagingItems()
        val workInfos by
            WorkManager.getInstance(applicationContext)
                .getWorkInfosByTagLiveData(RemoteBlocklistCoordinator.REMOTE_DOWNLOAD_WORKER)
                .asFlow()
                .collectAsState(initial = emptyList())

        LaunchedEffect(Unit) {
            rethinkEndpointViewModel.setFilter(uid)
            updateMaxSwitchUi()
            refreshUpdateUi()
        }

        LaunchedEffect(workInfos) {
            val workInfo = workInfos?.getOrNull(0) ?: return@LaunchedEffect
            Napier.i("Remote blocklist worker state: ${workInfo.state}")
            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    checkUpdateInProgress = false
                    updateInProgress = false
                    refreshUpdateUi()
                }
                WorkInfo.State.CANCELLED,
                WorkInfo.State.FAILED -> {
                    checkUpdateInProgress = false
                    updateInProgress = false
                    refreshUpdateUi()
                    Utilities.showToastUiCentered(
                        this@ConfigureRethinkBasicActivity,
                        getString(R.string.download_update_dialog_failure_message),
                        Toast.LENGTH_SHORT
                    )
                }
                else -> {
                    // no-op
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (getDownloadTimeStamp() != Constants.INIT_TIME_MS) {
                    Text(
                        text =
                            getString(
                                R.string.settings_local_blocklist_version,
                                Utilities.convertLongToTime(getDownloadTimeStamp(), Constants.TIME_FORMAT_2)
                            ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (updateAvailable) {
                    Button(
                        onClick = {
                            updateInProgress = true
                            download(getDownloadTimeStamp(), isRedownload = false)
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
                            checkUpdateInProgress = true
                            checkBlocklistUpdate()
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
                            updateInProgress = true
                            download(getDownloadTimeStamp(), isRedownload = true)
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
                        lifecycleScope.launch(Dispatchers.IO) { appConfig.switchRethinkDnsToSky() }
                        isMax = false
                    },
                    label = { Text(text = stringResource(id = R.string.radio_sky_btn)) }
                )
                FilterChip(
                    selected = isMax,
                    onClick = {
                        lifecycleScope.launch(Dispatchers.IO) { appConfig.switchRethinkDnsToMax() }
                        isMax = true
                    },
                    label = { Text(text = stringResource(id = R.string.radio_max_btn)) }
                )
            }

            Text(
                text =
                    if (isMax) {
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun RethinkBlocklistContent(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        val filterState by filters.asFlow().collectAsState(initial = filters.value)
        val selectedTags by
            RethinkBlocklistState.selectedFileTags.asFlow().collectAsState(
                initial = RethinkBlocklistState.selectedFileTags.value
            )

        LaunchedEffect(Unit) {
            modifiedStamp = getStamp()
            processSelectedFileTags(modifiedStamp)
            refreshBlocklistAvailability()
        }

        LaunchedEffect(filterState) {
            val filter = filterState ?: return@LaunchedEffect
            if (blocklistType.isRemote()) {
                remoteFileTagViewModel.setFilter(filter)
            } else {
                localFileTagViewModel.setFilter(filter)
            }
            filterLabelText = buildFilterDescription(filter)
        }

        LaunchedEffect(selectedTags) {
            val tags = selectedTags ?: emptySet<Int>()
            modifiedStamp = getStamp(tags, blocklistType)
        }

        ObserveBlocklistDownloadState(lifecycleOwner)

        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showDownload) {
                Text(text = stringResource(id = R.string.rt_download_desc))
                if (showRemoteProgress || isDownloading) {
                    CircularProgressIndicator()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            isDownloading = true
                            downloadBlocklist(blocklistType)
                        },
                        enabled = !isDownloading
                    ) {
                        Text(text = stringResource(id = R.string.rt_download))
                    }
                    TextButton(onClick = { cancelDownload(); finish() }) {
                        Text(text = stringResource(id = R.string.lbl_cancel))
                    }
                }
            }

            if (showConfigure) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = activeView == RethinkBlocklistState.BlocklistView.PACKS,
                        onClick = { activeView = RethinkBlocklistState.BlocklistView.PACKS },
                        label = { Text(text = stringResource(id = R.string.rt_list_simple_btn_txt)) }
                    )
                    FilterChip(
                        selected = activeView == RethinkBlocklistState.BlocklistView.ADVANCED,
                        onClick = { activeView = RethinkBlocklistState.BlocklistView.ADVANCED },
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
                            selected =
                                filterState?.filterSelected ==
                                    RethinkBlocklistState.BlocklistSelectionFilter.ALL,
                            onClick = { applyFilter(RethinkBlocklistState.BlocklistSelectionFilter.ALL.id) },
                            label = { Text(text = stringResource(id = R.string.lbl_all)) }
                        )
                        FilterChip(
                            selected =
                                filterState?.filterSelected ==
                                    RethinkBlocklistState.BlocklistSelectionFilter.SELECTED,
                            onClick =
                                {
                                    applyFilter(
                                        RethinkBlocklistState.BlocklistSelectionFilter.SELECTED.id
                                    )
                                },
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
                        val advancedAdapter = remember { LocalAdvancedViewAdapter(this@ConfigureRethinkBasicActivity) }
                        val advancedItems =
                            localFileTagViewModel.localFiletags.asFlow().collectAsLazyPagingItems()
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(count = advancedItems.itemCount) { index ->
                                val item = advancedItems[index] ?: return@items
                                val previous = if (index > 0) advancedItems.peek(index - 1) else null
                                val showHeader = previous?.group != item.group
                                advancedAdapter.BlocklistRow(
                                    filetag = item,
                                    showHeader = showHeader
                                ) { isSelected ->
                                    toggleLocalFiletag(item, isSelected)
                                }
                            }
                        }
                    } else {
                        val advancedAdapter = remember { RemoteAdvancedViewAdapter(this@ConfigureRethinkBasicActivity) }
                        val advancedItems =
                            remoteFileTagViewModel.remoteFileTags.asFlow().collectAsLazyPagingItems()
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(count = advancedItems.itemCount) { index ->
                                val item = advancedItems[index] ?: return@items
                                val previous = if (index > 0) advancedItems.peek(index - 1) else null
                                val showHeader = previous?.group != item.group
                                advancedAdapter.BlocklistRow(
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
                        val simpleAdapter = remember { LocalSimpleViewAdapter(this@ConfigureRethinkBasicActivity) }
                        val simpleItems =
                            localBlocklistPacksMapViewModel.simpleTags.asFlow().collectAsLazyPagingItems()
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(count = simpleItems.itemCount) { index ->
                                val item = simpleItems[index] ?: return@items
                                val previous = if (index > 0) simpleItems.peek(index - 1) else null
                                val showHeader = previous?.group != item.group
                                val valid = !item.pack.contains(DEAD_PACK) && item.pack.isNotEmpty()
                                if (!valid) return@items
                                simpleAdapter.BlocklistRow(
                                    map = item,
                                    showHeader = showHeader
                                ) { isSelected ->
                                    toggleLocalSimplePack(item, isSelected)
                                }
                            }
                        }
                    } else {
                        val simpleAdapter = remember { RemoteSimpleViewAdapter(this@ConfigureRethinkBasicActivity) }
                        val simpleItems =
                            remoteBlocklistPacksMapViewModel.simpleTags.asFlow().collectAsLazyPagingItems()
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(count = simpleItems.itemCount) { index ->
                                val item = simpleItems[index] ?: return@items
                                val previous = if (index > 0) simpleItems.peek(index - 1) else null
                                val showHeader = previous?.group != item.group
                                val valid = !item.pack.contains(DEAD_PACK) && item.pack.isNotEmpty()
                                if (!valid) return@items
                                simpleAdapter.BlocklistRow(
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
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                        onClick = { revertChangesAndFinish() }
                    ) {
                        Text(text = stringResource(id = R.string.notif_dialog_pause_dialog_negative))
                    }
                    Button(modifier = Modifier.weight(1f), onClick = { applyChangesAndFinish() }) {
                        Text(text = stringResource(id = R.string.lbl_apply))
                    }
                }
            }
        }
    }

    @Composable
    private fun ObserveBlocklistDownloadState(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        val workManager = WorkManager.getInstance(applicationContext)
        val customDownload by
            workManager.getWorkInfosByTagLiveData(CUSTOM_DOWNLOAD).asFlow()
                .collectAsState(initial = emptyList())
        val downloadTag by
            workManager.getWorkInfosByTagLiveData(DOWNLOAD_TAG).asFlow()
                .collectAsState(initial = emptyList())
        val fileTag by
            workManager.getWorkInfosByTagLiveData(FILE_TAG).asFlow()
                .collectAsState(initial = emptyList())

        LaunchedEffect(customDownload) {
            val workInfo = customDownload?.getOrNull(0) ?: return@LaunchedEffect
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
            val workInfo = downloadTag?.getOrNull(0) ?: return@LaunchedEffect
            when (workInfo.state) {
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.RUNNING -> onDownloadStart()
                WorkInfo.State.CANCELLED,
                WorkInfo.State.FAILED -> onDownloadFail()
                else -> Unit
            }
        }

        LaunchedEffect(fileTag) {
            val workInfo = fileTag?.getOrNull(0) ?: return@LaunchedEffect
            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> onDownloadSuccess()
                WorkInfo.State.CANCELLED,
                WorkInfo.State.FAILED -> onDownloadFail()
                else -> Unit
            }
        }
    }

    override fun filterObserver(): MutableLiveData<RethinkBlocklistState.Filters> {
        return filters
    }

    private fun refreshBlocklistAvailability() {
        lifecycleScope.launch {
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

    private fun hasBlocklists(): Boolean {
        return if (blocklistType.isLocal()) {
            hasLocalBlocklists(this, persistentState.localBlocklistTimestamp)
        } else {
            hasRemoteBlocklists(this, persistentState.remoteBlocklistTimestamp)
        }
    }

    private fun isStampChanged(): Boolean {
        if (DEFAULT_RDNS_REMOTE_DNS_NAMES.contains(remoteName)) {
            return false
        }

        return getStamp() != modifiedStamp
    }

    private fun cancelDownload() {
        appDownloadManager.cancelDownload(type = RethinkBlocklistManager.DownloadType.LOCAL)
    }

    private fun downloadBlocklist(type: RethinkBlocklistManager.RethinkBlocklistType) {
        if (VpnController.isVpnLockdown() && !persistentState.useCustomDownloadManager) {
            showLockdownDownloadDialog(type)
            return
        }

        proceedWithBlocklistDownload(type)
    }

    private fun showLockdownDownloadDialog(type: RethinkBlocklistManager.RethinkBlocklistType) {
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.lockdown_download_enable_inapp)
        builder.setMessage(R.string.lockdown_download_message)
        builder.setCancelable(true)
        builder.setPositiveButton(R.string.lockdown_download_enable_inapp) { _, _ ->
            persistentState.useCustomDownloadManager = true
            downloadBlocklist(type)
        }
        builder.setNegativeButton(R.string.lbl_cancel) { dialog, _ ->
            dialog.dismiss()
            proceedWithBlocklistDownload(type)
        }
        builder.create().show()
    }

    private fun proceedWithBlocklistDownload(type: RethinkBlocklistManager.RethinkBlocklistType) {
        lifecycleScope.launch {
            if (type.isLocal()) {
                val status =
                    withContext(Dispatchers.IO) {
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
                refreshBlocklistAvailability()
            }
        }
    }

    private fun handleDownloadStatus(status: AppDownloadManager.DownloadManagerStatus) {
        when (status) {
            AppDownloadManager.DownloadManagerStatus.IN_PROGRESS -> {
                // no-op
            }
            AppDownloadManager.DownloadManagerStatus.STARTED -> {
                onDownloadStart()
            }
            AppDownloadManager.DownloadManagerStatus.FAILURE -> {
                onDownloadFail()
            }
            AppDownloadManager.DownloadManagerStatus.NOT_AVAILABLE -> {
                showToastUiCentered(
                    this,
                    "Download latest version to update the blocklists",
                    Toast.LENGTH_SHORT
                )
            }
            else -> {
                // no-op
            }
        }
    }

    private fun onDownloadStart() {
        isDownloading = true
        showDownload = true
        showConfigure = false
    }

    private fun onDownloadFail() {
        isDownloading = false
        showDownload = true
        showConfigure = false
        showRemoteProgress = false
    }

    private fun onDownloadSuccess() {
        isDownloading = false
        showDownload = false
        showConfigure = true
        showRemoteProgress = false
        Utilities.showToastUiCentered(
            this,
            getString(R.string.download_update_dialog_message_success),
            Toast.LENGTH_SHORT
        )
    }

    private fun showApplyChangesDialog() {
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        builder.setTitle(getString(R.string.rt_dialog_title))
        builder.setMessage(getString(R.string.rt_dialog_message))
        builder.setCancelable(true)
        builder.setPositiveButton(getString(R.string.lbl_apply)) { _, _ ->
            setStamp(modifiedStamp)
            finish()
        }
        builder.setNeutralButton(getString(R.string.rt_dialog_neutral)) { _, _ ->
            // no-op
        }
        builder.setNegativeButton(getString(R.string.notif_dialog_pause_dialog_negative)) { _, _ ->
            finish()
        }
        builder.create().show()
    }

    private fun applyChangesAndFinish() {
        setStamp(modifiedStamp)
        finish()
    }

    private fun revertChangesAndFinish() {
        lifecycleScope.launch {
            val stamp = getStamp()
            val list = RethinkBlocklistManager.getTagsFromStamp(stamp, blocklistType)
            updateSelectedFileTags(list.toMutableSet())
            setStamp(stamp)
            finish()
        }
    }

    private fun setStamp(stamp: String?) {
        Napier.i("set stamp for blocklist type: ${blocklistType.name} with $stamp")
        if (stamp == null) {
            Napier.i("stamp is null")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
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

    private fun getRemoteUrl(stamp: String): String {
        return if (remoteUrl.contains(MAX_ENDPOINT)) {
            Constants.RETHINK_BASE_URL_MAX + stamp
        } else {
            Constants.RETHINK_BASE_URL_SKY + stamp
        }
    }

    private fun getStamp(): String {
        return if (blocklistType.isLocal()) {
            persistentState.localBlocklistStamp
        } else {
            getRemoteBlocklistStamp(remoteUrl)
        }
    }

    private fun isRethinkStampSearch(t: String): Boolean {
        if (!t.contains(Constants.RETHINKDNS_DOMAIN)) return false

        val split = t.split("/")
        split.forEach {
            if (it.contains("$RETHINK_STAMP_VERSION:") && isBase64(it)) {
                lifecycleScope.launch(Dispatchers.IO) { processSelectedFileTags(it) }
                showToastUiCentered(this, "Blocklists restored", Toast.LENGTH_SHORT)
                return true
            }
        }

        return false
    }

    private fun isBase64(stamp: String): Boolean {
        val whitespaceRegex = "\\s"
        val pattern =
            Pattern.compile(
                "^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$"
            )

        val versionSplit = stamp.split(":").getOrNull(1) ?: return false

        if (versionSplit.isEmpty()) return false

        val result = versionSplit.replace(whitespaceRegex, "")
        return pattern.matcher(result).matches()
    }

    private fun addQueryToFilters(query: String) {
        val a = filterObserver()
        if (a.value == null) {
            val temp = RethinkBlocklistState.Filters()
            temp.query = formatQuery(query)
            filters.postValue(temp)
            return
        }

        a.value!!.query = formatQuery(query)
        filters.postValue(a.value)
    }

    private fun formatQuery(q: String): String {
        return "%$q%"
    }

    private fun applyFilter(tag: Any) {
        val a = filterObserver().value ?: RethinkBlocklistState.Filters()

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

    private fun openFilterBottomSheet() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dialog =
                RethinkPlusFilterDialog(
                    this@ConfigureRethinkBasicActivity,
                    this@ConfigureRethinkBasicActivity,
                    getAllList(),
                    persistentState
                )
            launch(Dispatchers.Main) { dialog.show() }
        }
    }

    private suspend fun getAllList(): List<FileTag> {
        return if (blocklistType.isLocal()) {
            localFileTagViewModel.allFileTags()
        } else {
            remoteFileTagViewModel.allFileTags()
        }
    }

    private suspend fun processSelectedFileTags(stamp: String) {
        val list = RethinkBlocklistManager.getTagsFromStamp(stamp, blocklistType)
        updateSelectedFileTags(list.toMutableSet())
    }

    private suspend fun updateSelectedFileTags(selectedTags: MutableSet<Int>) {
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

    private fun toggleRemoteFiletag(filetag: RethinkRemoteFileTag, selected: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            filetag.isSelected = selected
            RethinkBlocklistManager.updateFiletagRemote(filetag)
            val list = RethinkBlocklistManager.getSelectedFileTagsRemote().toSet()
            RethinkBlocklistState.updateFileTagList(list)
        }
    }

    private fun toggleLocalFiletag(filetag: RethinkLocalFileTag, selected: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            filetag.isSelected = selected
            RethinkBlocklistManager.updateFiletagLocal(filetag)
            val list = RethinkBlocklistManager.getSelectedFileTagsLocal().toSet()
            RethinkBlocklistState.updateFileTagList(list)
        }
    }

    private fun toggleLocalSimplePack(map: LocalBlocklistPacksMap, selected: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            RethinkBlocklistManager.updateFiletagsLocal(map.blocklistIds.toSet(), if (selected) 1 else 0)
            val list = RethinkBlocklistManager.getSelectedFileTagsLocal().toSet()
            RethinkBlocklistState.updateFileTagList(list)
        }
    }

    private fun toggleRemoteSimplePack(map: RemoteBlocklistPacksMap, selected: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            RethinkBlocklistManager.updateFiletagsRemote(map.blocklistIds.toSet(), if (selected) 1 else 0)
            val list = RethinkBlocklistManager.getSelectedFileTagsRemote().toSet()
            RethinkBlocklistState.updateFileTagList(list)
        }
    }

    private fun updateMaxSwitchUi() {
        lifecycleScope.launch {
            var endpointUrl: String? = null
            withContext(Dispatchers.IO) { endpointUrl = appConfig.getRethinkPlusEndpoint()?.url }
            isMax = endpointUrl?.contains(Constants.MAX_ENDPOINT) == true
        }
    }

    private fun refreshUpdateUi() {
        if (getDownloadTimeStamp() == Constants.INIT_TIME_MS) {
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

    private fun isBlocklistUpdateAvailable(): Boolean {
        Napier.d(
            "Update available? newest: ${persistentState.newestRemoteBlocklistTimestamp}, available: ${persistentState.remoteBlocklistTimestamp}"
        )
        return (persistentState.newestRemoteBlocklistTimestamp != Constants.INIT_TIME_MS &&
            persistentState.newestRemoteBlocklistTimestamp > persistentState.remoteBlocklistTimestamp)
    }

    private fun checkBlocklistUpdate() {
        lifecycleScope.launch(Dispatchers.IO) {
            appDownloadManager.isDownloadRequired(RethinkBlocklistManager.DownloadType.REMOTE)
        }
    }

    private fun getDownloadTimeStamp(): Long {
        return persistentState.remoteBlocklistTimestamp
    }

    private fun download(timestamp: Long, isRedownload: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val initiated = appDownloadManager.downloadRemoteBlocklist(timestamp, isRedownload)
            if (!initiated) {
                launch(Dispatchers.Main) { onRemoteDownloadFailure() }
            }
        }
    }

    private fun onRemoteDownloadFailure() {
        showToastUiCentered(
            this,
            getString(R.string.download_update_dialog_failure_message),
            Toast.LENGTH_SHORT
        )
    }

    private fun buildFilterDescription(filter: RethinkBlocklistState.Filters): String {
        val text =
            if (filter.subGroups.isEmpty()) {
                getString(R.string.rt_filter_desc, filter.filterSelected.name.lowercase())
            } else {
                getString(
                    R.string.rt_filter_desc_subgroups,
                    filter.filterSelected.name.lowercase(),
                    "",
                    filter.subGroups
                )
            }
        return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    }
}
