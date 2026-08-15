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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bernaferrari.bravedns.customdownloader.LocalBlocklistCoordinator.Companion.CUSTOM_DOWNLOAD
import com.bernaferrari.bravedns.download.DownloadConstants.Companion.DOWNLOAD_TAG
import com.bernaferrari.bravedns.download.DownloadConstants.Companion.FILE_TAG
import com.bernaferrari.bravedns.service.RethinkBlocklistManager
import com.bernaferrari.bravedns.service.RethinkBlocklistManager.getStamp
import com.bernaferrari.bravedns.ui.rethink.RethinkBlocklistState
import com.bernaferrari.bravedns.util.workInfosByTagFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun RethinkBlocklistInitEffect(
    blocklistType: RethinkBlocklistManager.RethinkBlocklistType,
    vms: RethinkBlocklistVms,
    actions: RethinkBlocklistActions,
) {
    LaunchedEffect(Unit) {
        val stamp = actions.getStampValue()
        actions.onModifiedStampChanged(stamp)
        actions.onProcessSelectedFileTags(stamp)
        actions.onRefreshBlocklistAvailability()
        actions.onPlusFilterTagsChanged(
            withContext(Dispatchers.IO) {
                if (blocklistType.isLocal()) vms.localFileTagViewModel.allFileTags() else vms.remoteFileTagViewModel.allFileTags()
            }
        )
    }
}

@Composable
internal fun RethinkBlocklistFilterEffect(
    filterState: RethinkBlocklistState.Filters,
    blocklistType: RethinkBlocklistManager.RethinkBlocklistType,
    vms: RethinkBlocklistVms,
    selectedTags: Set<Int>,
    filterDescriptionTemplate: String,
    filterDescriptionSubgroupsTemplate: String,
    onFilterLabelTextChanged: (String) -> Unit,
    onModifiedStampChanged: (String) -> Unit,
) {
    LaunchedEffect(filterState) {
        val filter = filterState
        if (blocklistType.isRemote()) {
            vms.remoteFileTagViewModel.setFilter(filter)
        } else {
            vms.localFileTagViewModel.setFilter(filter)
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
}

@Composable
internal fun RethinkBlocklistDownloadEffects(
    context: Context,
    onDownloadStart: () -> Unit,
    onDownloadFail: () -> Unit,
    onDownloadSuccess: () -> Unit,
) {
    val workManager = WorkManager.getInstance(context)
    val customDownload by workManager.workInfosByTagFlow(CUSTOM_DOWNLOAD)
        .collectAsState(initial = emptyList())
    val downloadTag by workManager.workInfosByTagFlow(DOWNLOAD_TAG)
        .collectAsState(initial = emptyList())
    val fileTag by workManager.workInfosByTagFlow(FILE_TAG)
        .collectAsState(initial = emptyList())

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
}
