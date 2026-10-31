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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.FileTag
import com.bernaferrari.bravedns.service.RethinkBlocklistManager
import com.bernaferrari.bravedns.ui.rethink.RethinkBlocklistState
import com.bernaferrari.bravedns.util.UIUtils.openUrl
import com.bernaferrari.bravedns.viewmodel.LocalBlocklistPacksMapViewModel
import com.bernaferrari.bravedns.viewmodel.RemoteBlocklistPacksMapViewModel
import com.bernaferrari.bravedns.viewmodel.RethinkLocalFileTagViewModel
import com.bernaferrari.bravedns.viewmodel.RethinkRemoteFileTagViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal data class RethinkBlocklistVms(
    val remoteFileTagViewModel: RethinkRemoteFileTagViewModel,
    val localFileTagViewModel: RethinkLocalFileTagViewModel,
    val remoteBlocklistPacksMapViewModel: RemoteBlocklistPacksMapViewModel,
    val localBlocklistPacksMapViewModel: LocalBlocklistPacksMapViewModel,
)

internal data class RethinkBlocklistUiFlags(
    val showDownload: Boolean,
    val showConfigure: Boolean,
    val isDownloading: Boolean,
    val showRemoteProgress: Boolean,
    val activeView: RethinkBlocklistState.BlocklistView,
    val filterLabelText: String,
    val plusFilterTags: List<FileTag>,
    val downloadFailed: Boolean = false,
)

internal data class RethinkBlocklistActions(
    val onActiveViewChanged: (RethinkBlocklistState.BlocklistView) -> Unit,
    val onFilterLabelTextChanged: (String) -> Unit,
    val onPlusFilterTagsChanged: (List<FileTag>) -> Unit,
    val onDownloadBlocklist: () -> Unit,
    val onCancelDownload: () -> Unit,
    val onApplyChanges: () -> Unit,
    val onRevertChanges: () -> Unit,
    val onRefreshBlocklistAvailability: () -> Unit,
    val onProcessSelectedFileTags: (String) -> Unit,
    val onModifiedStampChanged: (String) -> Unit,
    val getStampValue: () -> String,
    val onDownloadStart: () -> Unit,
    val onDownloadFail: () -> Unit,
    val onDownloadSuccess: () -> Unit,
)

@Composable
internal fun RethinkBlocklistContent(
    modifier: Modifier = Modifier,
    context: Context,
    blocklistType: RethinkBlocklistManager.RethinkBlocklistType,
    filters: MutableStateFlow<RethinkBlocklistState.Filters>,
    vms: RethinkBlocklistVms,
    flags: RethinkBlocklistUiFlags,
    actions: RethinkBlocklistActions,
    filterDescriptionTemplate: String,
    filterDescriptionSubgroupsTemplate: String
) {
    val scope = rememberCoroutineScope()
    val filterState by filters.collectAsState()
    val selectedTags by RethinkBlocklistState.selectedFileTags.collectAsState()

    RethinkBlocklistInitEffect(
        blocklistType = blocklistType,
        vms = vms,
        actions = actions,
    )
    RethinkBlocklistFilterEffect(
        filterState = filterState,
        blocklistType = blocklistType,
        vms = vms,
        selectedTags = selectedTags,
        filterDescriptionTemplate = filterDescriptionTemplate,
        filterDescriptionSubgroupsTemplate = filterDescriptionSubgroupsTemplate,
        onFilterLabelTextChanged = actions.onFilterLabelTextChanged,
        onModifiedStampChanged = actions.onModifiedStampChanged,
    )
    RethinkBlocklistDownloadEffects(
        context = context,
        onDownloadStart = actions.onDownloadStart,
        onDownloadFail = actions.onDownloadFail,
        onDownloadSuccess = actions.onDownloadSuccess,
    )

    RethinkBlocklistEditorPane(
        modifier = modifier,
        context = context,
        scope = scope,
        blocklistType = blocklistType,
        filters = filters,
        filterState = filterState,
        selectedTags = selectedTags,
        vms = vms,
        flags = flags,
        actions = actions,
    )
}

@Composable
private fun RethinkBlocklistEditorPane(
    modifier: Modifier,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    blocklistType: RethinkBlocklistManager.RethinkBlocklistType,
    filters: MutableStateFlow<RethinkBlocklistState.Filters>,
    filterState: RethinkBlocklistState.Filters,
    selectedTags: Set<Int>,
    vms: RethinkBlocklistVms,
    flags: RethinkBlocklistUiFlags,
    actions: RethinkBlocklistActions,
) {
    val feeds = rememberBlocklistFeeds(context, blocklistType, vms, selectedTags)
    val availableSubgroups = flags.plusFilterTags.map { it.subg.trim() }.filter { it.isNotBlank() }.distinct().sorted()

    RethinkBlocklistEditor(
        packs = feeds.first,
        fileTags = feeds.second,
        activeView = editorViewOf(flags.activeView),
        query = filterState.query.replace("%", ""),
        selectionFilter = selectionFilterOf(filterState.filterSelected),
        selectedSubgroups = filterState.subGroups.toSet(),
        availableSubgroups = availableSubgroups,
        showDownload = flags.showDownload,
        showEditor = flags.showConfigure,
        isDownloading = flags.isDownloading || flags.showRemoteProgress,
        downloadFailed = flags.downloadFailed,
        strings = rethinkBlocklistEditorStrings(flags.filterLabelText, blocklistType.isLocal()),
        onViewChange = { actions.onActiveViewChanged(blocklistViewOf(it)) },
        onQueryChange = { query ->
            if (!restoreStampIfPresent(query, context, scope, actions.onProcessSelectedFileTags)) {
                applyFilterChange(filters, filterState) { it.query = formatQuery(query) }
            }
        },
        onSelectionFilterChange = { selection ->
            applyFilterChange(filters, filterState) { it.filterSelected = blocklistSelectionOf(selection) }
        },
        onSubgroupsChange = { groups ->
            applyFilterChange(filters, filterState) {
                it.subGroups.clear()
                it.subGroups.addAll(groups)
            }
        },
        onDownload = actions.onDownloadBlocklist,
        onCancelDownload = actions.onCancelDownload,
        onPackToggle = { pack, isSelected -> updateBlocklistSelection(scope, blocklistType, pack.tagIds, isSelected) },
        onFileTagToggle = { tag, isSelected -> updateBlocklistSelection(scope, blocklistType, tag.tagIds, isSelected) },
        onOpenUrl = { openUrl(context, it) },
        onApply = actions.onApplyChanges,
        onDiscard = actions.onRevertChanges,
        modifier = modifier,
    )
}

@Composable
private fun rememberBlocklistFeeds(
    context: Context,
    blocklistType: RethinkBlocklistManager.RethinkBlocklistType,
    vms: RethinkBlocklistVms,
    selectedTags: Set<Int>,
): Pair<RethinkBlocklistFeed<RethinkBlocklistPack>, RethinkBlocklistFeed<RethinkBlocklistFileTag>> {
    return if (blocklistType.isLocal()) {
        AndroidBlocklistFeed(vms.localBlocklistPacksMapViewModel.simpleTags.collectAsLazyPagingItems()) { it.toEditorPack(context, selectedTags) } to
            AndroidBlocklistFeed(vms.localFileTagViewModel.localFiletags.collectAsLazyPagingItems()) { it.toEditorTag(context, selectedTags) }
    } else {
        AndroidBlocklistFeed(vms.remoteBlocklistPacksMapViewModel.simpleTags.collectAsLazyPagingItems()) { it.toEditorPack(context, selectedTags) } to
            AndroidBlocklistFeed(vms.remoteFileTagViewModel.remoteFileTags.collectAsLazyPagingItems()) { it.toEditorTag(context, selectedTags) }
    }
}

private fun editorViewOf(activeView: RethinkBlocklistState.BlocklistView) =
    if (activeView == RethinkBlocklistState.BlocklistView.PACKS) {
        RethinkBlocklistEditorView.Packs
    } else {
        RethinkBlocklistEditorView.Advanced
    }

private fun blocklistViewOf(view: RethinkBlocklistEditorView) =
    if (view == RethinkBlocklistEditorView.Packs) {
        RethinkBlocklistState.BlocklistView.PACKS
    } else {
        RethinkBlocklistState.BlocklistView.ADVANCED
    }

private fun selectionFilterOf(filter: RethinkBlocklistState.BlocklistSelectionFilter) =
    if (filter == RethinkBlocklistState.BlocklistSelectionFilter.SELECTED) {
        RethinkBlocklistSelectionFilter.Selected
    } else {
        RethinkBlocklistSelectionFilter.All
    }

private fun blocklistSelectionOf(selection: RethinkBlocklistSelectionFilter) =
    if (selection == RethinkBlocklistSelectionFilter.Selected) {
        RethinkBlocklistState.BlocklistSelectionFilter.SELECTED
    } else {
        RethinkBlocklistState.BlocklistSelectionFilter.ALL
    }

private fun applyFilterChange(
    filters: MutableStateFlow<RethinkBlocklistState.Filters>,
    filterState: RethinkBlocklistState.Filters,
    change: (RethinkBlocklistState.Filters) -> Unit,
) {
    val next = RethinkBlocklistState.Filters().also { copy ->
        copy.query = filterState.query
        copy.filterSelected = filterState.filterSelected
        copy.subGroups.addAll(filterState.subGroups)
    }
    change(next)
    filters.value = next
}

private fun updateBlocklistSelection(
    scope: kotlinx.coroutines.CoroutineScope,
    blocklistType: RethinkBlocklistManager.RethinkBlocklistType,
    tagIds: Set<Int>,
    isSelected: Boolean,
) {
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

@Composable
private fun rethinkBlocklistEditorStrings(filterLabelText: String, isLocal: Boolean): RethinkBlocklistEditorStrings {
    val blocklistCountTemplate = stringResource(R.string.rsv_blocklist_count_text)
    val entriesTemplate = stringResource(R.string.dc_entries)
    return RethinkBlocklistEditorStrings(
        // Honest sizes: the on-device lists are about 60 MB; Rethink DNS only needs the list of
        // blocklists (about 50 KB), fetched automatically.
        downloadDescription = stringResource(if (isLocal) R.string.blocklist_fetch_local else R.string.blocklist_fetch_remote),
        downloadingDescription = stringResource(if (isLocal) R.string.blocklist_fetch_local_progress else R.string.blocklist_fetch_remote),
        downloadFailedDescription = stringResource(if (isLocal) R.string.blocklist_fetch_local_failed else R.string.blocklist_fetch_remote_failed),
        retry = stringResource(R.string.lbl_retry),
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
        blocklistCount = { blocklistCountTemplate.format(it.toString()) },
        entries = { entriesTemplate.format(it.toString()) },
        empty = stringResource(R.string.configure_search_empty_title),
        openSource = stringResource(R.string.events_filter_source),
    )
}
