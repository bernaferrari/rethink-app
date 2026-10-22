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

import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.compose.dns.RethinkEndpointRow
import com.bernaferrari.bravedns.customdownloader.RemoteBlocklistCoordinator
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormSection
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.workInfosByTagFlow
import com.bernaferrari.bravedns.viewmodel.RethinkEndpointViewModel
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import Logger
import Logger.LOG_TAG_UI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun RethinkListContent(
    modifier: Modifier = Modifier,
    context: Context,
    persistentState: PersistentState,
    appConfig: AppConfig,
    rethinkEndpointViewModel: RethinkEndpointViewModel,
    uid: Int,
    updateUi: RethinkListUpdateUi,
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
    val pagingItems = rethinkEndpointViewModel.rethinkEndpointList.collectAsLazyPagingItems()
    val workInfos by WorkManager.getInstance(context)
        .workInfosByTagFlow(RemoteBlocklistCoordinator.REMOTE_DOWNLOAD_WORKER)
        .collectAsState(initial = emptyList())

    RethinkListWorkEffects(
        rethinkEndpointViewModel = rethinkEndpointViewModel,
        uid = uid,
        workInfos = workInfos,
        context = context,
        downloadFailureMessage = downloadFailureMessage,
        onUpdateMaxSwitchUi = onUpdateMaxSwitchUi,
        onRefreshUpdateUi = onRefreshUpdateUi,
        onCheckUpdateInProgressChanged = onCheckUpdateInProgressChanged,
        onUpdateInProgressChanged = onUpdateInProgressChanged,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)
    ) {
        RethinkListUpdateCard(
            persistentState = persistentState,
            appConfig = appConfig,
            updateUi = updateUi,
            onMaxChanged = onMaxChanged,
            onCheckUpdateInProgressChanged = onCheckUpdateInProgressChanged,
            onUpdateInProgressChanged = onUpdateInProgressChanged,
            onCheckBlocklistUpdate = onCheckBlocklistUpdate,
            onDownload = onDownload,
            localBlocklistVersionTemplate = localBlocklistVersionTemplate,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = SharedDimensions.spacing2xl),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)
        ) {
            items(count = pagingItems.itemCount) { index ->
                val item = pagingItems[index] ?: return@items
                RethinkEndpointRow(endpoint = item, appConfig = appConfig)
            }
        }
    }
}

@Composable
private fun RethinkListWorkEffects(
    rethinkEndpointViewModel: RethinkEndpointViewModel,
    uid: Int,
    workInfos: List<WorkInfo>,
    context: Context,
    downloadFailureMessage: String,
    onUpdateMaxSwitchUi: () -> Unit,
    onRefreshUpdateUi: () -> Unit,
    onCheckUpdateInProgressChanged: (Boolean) -> Unit,
    onUpdateInProgressChanged: (Boolean) -> Unit,
) {
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
}

@Composable
private fun RethinkListUpdateCard(
    persistentState: PersistentState,
    appConfig: AppConfig,
    updateUi: RethinkListUpdateUi,
    onMaxChanged: (Boolean) -> Unit,
    onCheckUpdateInProgressChanged: (Boolean) -> Unit,
    onUpdateInProgressChanged: (Boolean) -> Unit,
    onCheckBlocklistUpdate: () -> Unit,
    onDownload: (Long, Boolean) -> Unit,
    localBlocklistVersionTemplate: String,
) {
    val scope = rememberCoroutineScope()
    val actionText = rethinkListActionText(updateUi)
    val actionInProgress = rethinkListActionInProgress(updateUi)

    RethinkFormSection(
        title = stringResource(R.string.dc_rethink_dns_radio),
        description = if (updateUi.isMax) {
            stringResource(id = R.string.rethink_max_desc)
        } else {
            stringResource(id = R.string.rethink_sky_desc)
        },
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
            RethinkListUpdateActionButton(
                actionText = actionText,
                actionInProgress = actionInProgress,
                updateUi = updateUi,
                timestamp = persistentState.remoteBlocklistTimestamp,
                onCheckUpdateInProgressChanged = onCheckUpdateInProgressChanged,
                onUpdateInProgressChanged = onUpdateInProgressChanged,
                onCheckBlocklistUpdate = onCheckBlocklistUpdate,
                onDownload = onDownload,
            )
        }

        RethinkSkyMaxToggleRow(
            skyLabel = stringResource(id = R.string.radio_sky_btn),
            maxLabel = stringResource(id = R.string.radio_max_btn),
            isMax = updateUi.isMax,
            onSky = {
                scope.launch(Dispatchers.IO) { appConfig.switchRethinkDnsToSky() }
                onMaxChanged(false)
            },
            onMax = {
                scope.launch(Dispatchers.IO) { appConfig.switchRethinkDnsToMax() }
                onMaxChanged(true)
            },
        )
    }
}

@Composable
private fun rethinkListActionText(updateUi: RethinkListUpdateUi): String? =
    when {
        updateUi.updateAvailable -> stringResource(id = R.string.rt_chip_update_available)
        updateUi.checkUpdateVisible -> stringResource(id = R.string.rt_chip_check_update)
        updateUi.redownloadVisible -> stringResource(id = R.string.rt_re_download)
        else -> null
    }

private fun rethinkListActionInProgress(updateUi: RethinkListUpdateUi): Boolean =
    when {
        updateUi.updateAvailable || updateUi.redownloadVisible -> updateUi.updateInProgress
        updateUi.checkUpdateVisible -> updateUi.checkUpdateInProgress
        else -> false
    }

@Composable
private fun RethinkListUpdateActionButton(
    actionText: String,
    actionInProgress: Boolean,
    updateUi: RethinkListUpdateUi,
    timestamp: Long,
    onCheckUpdateInProgressChanged: (Boolean) -> Unit,
    onUpdateInProgressChanged: (Boolean) -> Unit,
    onCheckBlocklistUpdate: () -> Unit,
    onDownload: (Long, Boolean) -> Unit,
) {
    FilledTonalButton(
        onClick = {
            when {
                updateUi.updateAvailable -> {
                    onUpdateInProgressChanged(true)
                    onDownload(timestamp, false)
                }
                updateUi.checkUpdateVisible -> {
                    onCheckUpdateInProgressChanged(true)
                    onCheckBlocklistUpdate()
                }
                updateUi.redownloadVisible -> {
                    onUpdateInProgressChanged(true)
                    onDownload(timestamp, true)
                }
            }
        },
        enabled = !actionInProgress
    ) {
        if (actionInProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(SharedDimensions.iconSizeSm),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(SharedDimensions.spacingSm))
        }
        Text(text = actionText)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RethinkSkyMaxToggleRow(
    skyLabel: String,
    maxLabel: String,
    isMax: Boolean,
    onSky: () -> Unit,
    onMax: () -> Unit,
) {
    RethinkConnectedChoiceGroup(
        options = listOf(RethinkChoiceOption(false, skyLabel), RethinkChoiceOption(true, maxLabel)),
        selected = isMax,
        onSelect = { max -> if (max) onMax() else onSky() },
    )
}
