@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormSection
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSwitchListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.RethinkCheckboxListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSheetHeader
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

data class RethinkDnsRecordType(val id: String, val name: String, val description: String)

data class RethinkDnsRecordTypesStrings(
    val title: String,
    val description: String,
    val auto: String,
    val manual: String,
    val selected: (selected: Int, total: Int) -> String,
    val allowed: String,
)

/** Portable record-type selector. Hosts store the selection in their own preferences. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun RethinkDnsRecordTypesSheet(
    types: List<RethinkDnsRecordType>,
    autoMode: Boolean,
    selectedIds: Set<String>,
    strings: RethinkDnsRecordTypesStrings,
    onAutoModeChange: (Boolean) -> Unit,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedCount = if (autoMode) types.size else selectedIds.size
    RethinkModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            RethinkFormSection(strings.title, strings.description) {
                // Auto/Manual is already visible in the connected group; the pill only adds the count.
                RethinkPill(strings.selected(selectedCount, types.size), colors = RethinkPillDefaults.secondary())
                RethinkConnectedChoiceGroup(
                    options = listOf(RethinkChoiceOption(true, strings.auto), RethinkChoiceOption(false, strings.manual)),
                    selected = autoMode,
                    onSelect = onAutoModeChange,
                )
            }
            RethinkFormSection(strings.allowed) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = SharedDimensions.sheetContentMaxHeight),
                    contentPadding = PaddingValues(bottom = SharedDimensions.spacing2xl),
                ) {
                    itemsIndexed(types, key = { _, type -> type.id }) { index, type ->
                        val checked = type.id in selectedIds
                        RethinkCheckboxListItem(
                            headline = type.name,
                            checked = checked,
                            onCheckedChange = { if (!autoMode) onToggle(type.id) },
                            supporting = type.description,
                            position = cardPositionFor(index, types.lastIndex),
                            enabled = !autoMode,
                            highlighted = checked && !autoMode,
                        )
                    }
                }
            }
        }
    }
}

data class RethinkLocalBlocklistState(
    val heading: String,
    val version: String,
    val canConfigure: Boolean,
    val canCopy: Boolean,
    val canSearch: Boolean,
    val showCheckUpdate: Boolean,
    val showDownload: Boolean,
    val showRedownload: Boolean,
    val checking: Boolean,
    val downloading: Boolean,
    val redownloading: Boolean,
    val enabled: Boolean,
)

data class RethinkLocalBlocklistStrings(
    val state: String,
    val enable: String,
    val disable: String,
    val toggleDescription: String,
    val actions: String,
    val configure: String,
    val copy: String,
    val search: String,
    val maintenance: String,
    val checkUpdate: String,
    val download: String,
    val redownload: String,
    val delete: String,
    val deleteMessage: String,
    val cancel: String,
)

/** Shared local-blocklist maintenance sheet; downloads, clipboard and browser actions remain host-owned. */
@Composable
fun RethinkLocalBlocklistSheet(
    state: RethinkLocalBlocklistState,
    strings: RethinkLocalBlocklistStrings,
    onDismiss: () -> Unit,
    onEnableToggle: () -> Unit,
    onConfigure: () -> Unit,
    onCopy: () -> Unit,
    onSearch: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownload: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        RethinkConfirmDialog(
            onDismissRequest = { confirmDelete = false },
            title = strings.delete,
            message = strings.deleteMessage,
            confirmText = strings.delete,
            dismissText = strings.cancel,
            isConfirmDestructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
    RethinkModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = SharedDimensions.sheetContentMaxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            RethinkSheetHeader(title = state.heading, subtitle = state.version)
            RethinkFormSection(strings.state) {
                RethinkListGroup {
                    // It is a toggle, so it reads as one: a switch with the current state.
                    RethinkSwitchListItem(
                        headline = strings.enable,
                        checked = state.enabled,
                        onCheckedChange = { onEnableToggle() },
                        supporting = strings.toggleDescription,
                        leadingIcon = MaterialSymbols.Filled.Security,
                        position = CardPosition.Single,
                    )
                }
            }
            RethinkFormSection(strings.actions) {
                RethinkListGroup {
                    RethinkListItem(headline = strings.configure, leadingIcon = MaterialSymbols.Filled.Settings, position = CardPosition.First, enabled = state.canConfigure, onClick = onConfigure)
                    RethinkListItem(headline = strings.copy, leadingIcon = MaterialSymbols.Filled.ContentCopy, position = CardPosition.Middle, enabled = state.canCopy, onClick = onCopy)
                    RethinkListItem(headline = strings.search, leadingIcon = MaterialSymbols.Filled.Search, position = CardPosition.Last, enabled = state.canSearch, onClick = onSearch)
                }
            }
            RethinkFormSection(strings.maintenance) {
                RethinkListGroup {
                    val maintenance = buildList {
                        if (state.showCheckUpdate) add(RethinkLocalBlocklistAction(strings.checkUpdate, MaterialSymbols.Filled.Refresh, !state.checking, state.checking, onCheckUpdate))
                        if (state.showDownload) add(RethinkLocalBlocklistAction(strings.download, MaterialSymbols.Filled.Download, !state.downloading, state.downloading, onDownload))
                        if (state.showRedownload) add(RethinkLocalBlocklistAction(strings.redownload, MaterialSymbols.Filled.Download, !state.redownloading, state.redownloading, onRedownload))
                        add(RethinkLocalBlocklistAction(strings.delete, MaterialSymbols.Filled.Delete, true, false, { confirmDelete = true }, destructive = true))
                    }
                    maintenance.forEachIndexed { index, item ->
                        RethinkListItem(
                            headline = item.label,
                            leadingIcon = item.icon,
                            destructive = item.destructive,
                            position = cardPositionFor(index, maintenance.lastIndex),
                            enabled = item.enabled,
                            onClick = item.onClick,
                            trailing = if (item.showProgress) ({ LoadingIndicator(Modifier.size(SharedDimensions.iconSizeMd)) }) else null,
                        )
                    }
                }
            }
        }
    }
}

private data class RethinkLocalBlocklistAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val enabled: Boolean,
    val showProgress: Boolean,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)
