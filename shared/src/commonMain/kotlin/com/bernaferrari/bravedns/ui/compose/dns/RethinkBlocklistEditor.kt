/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFilterChip
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormActionRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

enum class RethinkBlocklistEditorView { Packs, Advanced }
enum class RethinkBlocklistSelectionFilter { All, Selected }

/** Lets Android retain Paging while commonMain owns the lazy-list visual tree. */
interface RethinkBlocklistFeed<T> {
    val itemCount: Int
    operator fun get(index: Int): T?
}

data class RethinkInMemoryBlocklistFeed<T>(val items: List<T>) : RethinkBlocklistFeed<T> {
    override val itemCount: Int get() = items.size
    override fun get(index: Int): T? = items.getOrNull(index)
}

data class RethinkBlocklistEditorStrings(
    val downloadDescription: String,
    val download: String,
    val cancel: String,
    val packs: String,
    val advanced: String,
    val search: String,
    val clearSearch: String,
    val all: String,
    val selected: String,
    val filter: String,
    val filterHint: String,
    val apply: String,
    val discard: String,
    val blocklistCount: (Int) -> String,
    val entries: (Int) -> String,
    val empty: String = "",
    /** Content description for a blocklist's source-link action. */
    val openSource: String,
)

/** Complete target-neutral packs/advanced blocklist editor body. */
@Composable
fun RethinkBlocklistEditor(
    packs: RethinkBlocklistFeed<RethinkBlocklistPack>,
    fileTags: RethinkBlocklistFeed<RethinkBlocklistFileTag>,
    activeView: RethinkBlocklistEditorView,
    query: String,
    selectionFilter: RethinkBlocklistSelectionFilter,
    selectedSubgroups: Set<String>,
    availableSubgroups: List<String>,
    showDownload: Boolean,
    showEditor: Boolean,
    isDownloading: Boolean,
    strings: RethinkBlocklistEditorStrings,
    onViewChange: (RethinkBlocklistEditorView) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectionFilterChange: (RethinkBlocklistSelectionFilter) -> Unit,
    onSubgroupsChange: (Set<String>) -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onPackToggle: (RethinkBlocklistPack, Boolean) -> Unit,
    onFileTagToggle: (RethinkBlocklistFileTag, Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        if (showDownload) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
                shape = RoundedCornerShape(SharedDimensions.groupCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(SharedDimensions.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                    ) {
                        Text(strings.downloadDescription, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (isDownloading) LoadingIndicator(Modifier.size(SharedDimensions.iconSizeMd))
                    }
                    RethinkFormActionRow(
                        confirmLabel = strings.download,
                        onConfirm = onDownload,
                        dismissLabel = strings.cancel,
                        onDismiss = onCancelDownload,
                        confirmEnabled = !isDownloading,
                    )
                }
            }
        } else if (showEditor) {
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = SharedDimensions.screenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            ) {
                RethinkBlocklistEditorTabs(activeView, strings, onViewChange)
                if (activeView == RethinkBlocklistEditorView.Advanced) {
                    RethinkSearchField(query, onQueryChange, placeholder = strings.search, clearQueryContentDescription = strings.clearSearch, modifier = Modifier.fillMaxWidth())
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                    ) {
                        RethinkFilterChip(strings.all, selectionFilter == RethinkBlocklistSelectionFilter.All, onClick = { onSelectionFilterChange(RethinkBlocklistSelectionFilter.All) })
                        RethinkFilterChip(strings.selected, selectionFilter == RethinkBlocklistSelectionFilter.Selected, onClick = { onSelectionFilterChange(RethinkBlocklistSelectionFilter.Selected) })
                        Spacer(Modifier.weight(1f))
                        RethinkBlocklistSubgroupMenu(selectedSubgroups, availableSubgroups, strings.filter, onSubgroupsChange)
                    }
                    Text(strings.filterHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (activeView == RethinkBlocklistEditorView.Packs) {
                    RethinkBlocklistPackList(packs, strings, onPackToggle, Modifier.weight(1f))
                } else {
                    RethinkBlocklistFileTagList(fileTags, strings, onFileTagToggle, onOpenUrl, Modifier.weight(1f))
                }
            }
            // A docked tonal action bar: it sits below the list (never over the last rows) and
            // reads as the screen's commit point rather than another floating row.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                RethinkFormActionRow(
                    confirmLabel = strings.apply,
                    onConfirm = onApply,
                    dismissLabel = strings.discard,
                    onDismiss = onDiscard,
                    modifier = Modifier.padding(
                        horizontal = SharedDimensions.screenPaddingHorizontal,
                        vertical = SharedDimensions.spacingMd,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RethinkBlocklistEditorTabs(selected: RethinkBlocklistEditorView, strings: RethinkBlocklistEditorStrings, onChange: (RethinkBlocklistEditorView) -> Unit) {
    RethinkConnectedChoiceGroup(
        options = listOf(
            RethinkChoiceOption(RethinkBlocklistEditorView.Packs, strings.packs),
            RethinkChoiceOption(RethinkBlocklistEditorView.Advanced, strings.advanced),
        ),
        selected = selected,
        onSelect = onChange,
    )
}

@Composable
private fun RethinkBlocklistSubgroupMenu(selected: Set<String>, all: List<String>, contentDescription: String, onChange: (Set<String>) -> Unit) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { expanded = true }) { Icon(MaterialSymbols.Filled.FilterList, contentDescription) }
        androidx.compose.material3.DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            all.forEach { subgroup ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(subgroup) },
                    trailingIcon = { androidx.compose.material3.Checkbox(subgroup in selected, null) },
                    onClick = { onChange(if (subgroup in selected) selected - subgroup else selected + subgroup) },
                )
            }
        }
    }
}

@Composable
private fun RethinkBlocklistPackList(feed: RethinkBlocklistFeed<RethinkBlocklistPack>, strings: RethinkBlocklistEditorStrings, onToggle: (RethinkBlocklistPack, Boolean) -> Unit, modifier: Modifier) {
    if (feed.itemCount == 0) {
        RethinkEmptyState(title = strings.empty.ifBlank { strings.filterHint }, icon = MaterialSymbols.Filled.FilterList, modifier = modifier)
        return
    }
    val rowStrings = RethinkBlocklistRowStrings(strings.blocklistCount, strings.entries, strings.openSource)
    LazyColumn(modifier, contentPadding = PaddingValues(top = SharedDimensions.spacingXs, bottom = SharedDimensions.spacingLg)) {
        items(feed.itemCount) { index ->
            val pack = feed[index] ?: return@items
            RethinkBlocklistPackRow(
                pack = pack,
                showGroupHeader = index == 0 || feed[index - 1]?.group?.id != pack.group.id,
                strings = rowStrings,
                onToggle = { onToggle(pack, it) },
                position = rethinkBlocklistPosition(index, feed.itemCount) { feed[it]?.group?.id },
            )
        }
    }
}

@Composable
private fun RethinkBlocklistFileTagList(feed: RethinkBlocklistFeed<RethinkBlocklistFileTag>, strings: RethinkBlocklistEditorStrings, onToggle: (RethinkBlocklistFileTag, Boolean) -> Unit, onOpenUrl: (String) -> Unit, modifier: Modifier) {
    if (feed.itemCount == 0) {
        RethinkEmptyState(title = strings.empty.ifBlank { strings.filterHint }, icon = MaterialSymbols.Filled.FilterList, modifier = modifier)
        return
    }
    val rowStrings = RethinkBlocklistRowStrings(strings.blocklistCount, strings.entries, strings.openSource)
    LazyColumn(modifier, contentPadding = PaddingValues(top = SharedDimensions.spacingXs, bottom = SharedDimensions.spacingLg)) {
        items(feed.itemCount) { index ->
            val tag = feed[index] ?: return@items
            RethinkBlocklistFileTagRow(
                tag = tag,
                showGroupHeader = index == 0 || feed[index - 1]?.group?.id != tag.group.id,
                strings = rowStrings,
                onToggle = { onToggle(tag, it) },
                onOpenUrl = onOpenUrl,
                position = rethinkBlocklistPosition(index, feed.itemCount) { feed[it]?.group?.id },
            )
        }
    }
}
