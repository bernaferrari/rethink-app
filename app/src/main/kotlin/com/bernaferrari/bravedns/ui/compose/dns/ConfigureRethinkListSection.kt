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
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.bernaferrari.bravedns.ui.compose.theme.RethinkContainedCard
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.workInfosByTagFlow
import com.bernaferrari.bravedns.viewmodel.RethinkEndpointViewModel
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import Logger
import Logger.LOG_TAG_UI

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
    downloadFailureMessage: String,
    onEditConfiguration: (ConfigureRethinkScreenType, String, String) -> Unit,
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

    // The table sorts the active endpoint first; keep the order the screen opened with so the
    // row you pick stays under your finger.
    val loaded = (0 until pagingItems.itemCount).mapNotNull { pagingItems[it] }
    val byKey = loaded.associateBy { "${it.name}|${it.uid}" }
    val order = rememberStableOrder(loaded.map { "${it.name}|${it.uid}" })

    // The update card scrolls away with the list instead of pinning above it.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingSm,
            bottom = SharedDimensions.spacing2xl,
        ),
        // Endpoint rows carry their own 4dp inset, so this matches every other endpoint list.
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)
    ) {
        item(key = "update") {
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
        }
        items(order, key = { it }) { key ->
            val item = byKey[key] ?: return@items
            RethinkEndpointRow(endpoint = item, appConfig = appConfig, onEditConfiguration = onEditConfiguration)
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
    val actionText = rethinkListActionText(updateUi)
    val actionInProgress = rethinkListActionInProgress(updateUi)

    // Only the blocklist version and its update action: the Sky/Max network moved to
    // DNS › Advanced ("Rethink DNS network"), so the presets are the first thing here.
    if (persistentState.remoteBlocklistTimestamp == Constants.INIT_TIME_MS && actionText == null) return
    RethinkContainedCard(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
        if (persistentState.remoteBlocklistTimestamp != Constants.INIT_TIME_MS || actionText != null) {
            Text(
                text = localBlocklistVersionTemplate.format(
                    Utilities.convertLongToTime(
                        persistentState.remoteBlocklistTimestamp,
                        Constants.TIME_FORMAT_2
                    )
                ),
                style = MaterialTheme.typography.labelLarge,
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
            LoadingIndicator(modifier = Modifier.size(SharedDimensions.iconSizeMd))
            Spacer(modifier = Modifier.width(SharedDimensions.spacingSm))
        }
        Text(text = actionText)
    }
}
