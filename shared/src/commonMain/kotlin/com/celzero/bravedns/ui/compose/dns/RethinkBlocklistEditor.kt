/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.celzero.bravedns.ui.compose.dns

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.RethinkFilterChip
import com.celzero.bravedns.ui.compose.theme.RethinkFormActionRow
import com.celzero.bravedns.ui.compose.theme.RethinkSearchField
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

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
    Column(
        modifier.fillMaxSize().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        if (showDownload) {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
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
                        if (isDownloading) CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
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
            RethinkBlocklistEditorTabs(activeView, strings, onViewChange)
            if (activeView == RethinkBlocklistEditorView.Advanced) {
                RethinkSearchField(query, onQueryChange, placeholder = strings.search, clearQueryContentDescription = strings.clearSearch, onClearQuery = { onQueryChange("") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RethinkFilterChip(strings.all, selectionFilter == RethinkBlocklistSelectionFilter.All, onClick = { onSelectionFilterChange(RethinkBlocklistSelectionFilter.All) })
                    RethinkFilterChip(strings.selected, selectionFilter == RethinkBlocklistSelectionFilter.Selected, onClick = { onSelectionFilterChange(RethinkBlocklistSelectionFilter.Selected) }, modifier = Modifier.padding(start = 8.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    RethinkBlocklistSubgroupMenu(selectedSubgroups, availableSubgroups, strings.filter, onSubgroupsChange)
                }
                Text(strings.filterHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (activeView == RethinkBlocklistEditorView.Packs) {
                RethinkBlocklistPackList(packs, strings, onPackToggle, Modifier.weight(1f))
            } else {
                RethinkBlocklistFileTagList(fileTags, strings, onFileTagToggle, onOpenUrl, Modifier.weight(1f))
            }
            RethinkFormActionRow(
                confirmLabel = strings.apply,
                onConfirm = onApply,
                dismissLabel = strings.discard,
                onDismiss = onDiscard,
            )
        }
    }
}

@Composable
private fun RethinkBlocklistEditorTabs(selected: RethinkBlocklistEditorView, strings: RethinkBlocklistEditorStrings, onChange: (RethinkBlocklistEditorView) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        val packsSelected = selected == RethinkBlocklistEditorView.Packs
        ToggleButton(
            checked = packsSelected,
            onCheckedChange = { checked -> if (checked && !packsSelected) onChange(RethinkBlocklistEditorView.Packs) },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            colors = RethinkBlocklistTabColors(),
            border = null,
            modifier = Modifier.weight(1f),
        ) { Text(strings.packs) }
        val advancedSelected = selected == RethinkBlocklistEditorView.Advanced
        ToggleButton(
            checked = advancedSelected,
            onCheckedChange = { checked -> if (checked && !advancedSelected) onChange(RethinkBlocklistEditorView.Advanced) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            colors = RethinkBlocklistTabColors(),
            border = null,
            modifier = Modifier.weight(1f),
        ) { Text(strings.advanced) }
    }
}

@Composable
private fun RethinkBlocklistTabColors() = ToggleButtonDefaults.toggleButtonColors(
    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

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
    LazyColumn(modifier, contentPadding = PaddingValues(vertical = SharedDimensions.spacingSm), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(feed.itemCount) { index ->
            val pack = feed[index] ?: return@items
            RethinkBlocklistPackRow(pack, index == 0 || feed[index - 1]?.group?.id != pack.group.id, RethinkBlocklistRowStrings(strings.blocklistCount, strings.entries), onToggle = { onToggle(pack, it) })
        }
    }
}

@Composable
private fun RethinkBlocklistFileTagList(feed: RethinkBlocklistFeed<RethinkBlocklistFileTag>, strings: RethinkBlocklistEditorStrings, onToggle: (RethinkBlocklistFileTag, Boolean) -> Unit, onOpenUrl: (String) -> Unit, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(vertical = SharedDimensions.spacingSm), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(feed.itemCount) { index ->
            val tag = feed[index] ?: return@items
            RethinkBlocklistFileTagRow(tag, index == 0 || feed[index - 1]?.group?.id != tag.group.id, RethinkBlocklistRowStrings(strings.blocklistCount, strings.entries), onToggle = { onToggle(tag, it) }, onOpenUrl = onOpenUrl)
        }
    }
}
