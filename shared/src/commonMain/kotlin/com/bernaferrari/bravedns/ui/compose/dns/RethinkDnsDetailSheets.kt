/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

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
            RethinkDnsSheetCard {
                Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd), verticalAlignment = Alignment.Top) {
                    Surface(shape = RoundedCornerShape(SharedDimensions.iconContainerRadius), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(SharedDimensions.iconContainerMd)) {
                        Box(contentAlignment = Alignment.Center) { Icon(MaterialSymbols.Filled.Security, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                        Text(strings.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(strings.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    RethinkDnsSheetPill(if (autoMode) strings.auto else strings.manual)
                    RethinkDnsSheetPill(strings.selected(selectedCount, types.size))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                    ToggleButton(
                        checked = autoMode,
                        onCheckedChange = { if (it && !autoMode) onAutoModeChange(true) },
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.weight(1f),
                    ) { Text(strings.auto, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                    ToggleButton(
                        checked = !autoMode,
                        onCheckedChange = { if (it && autoMode) onAutoModeChange(false) },
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.weight(1f),
                    ) { Text(strings.manual, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                }
            }
            SectionHeader(strings.allowed)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                contentPadding = PaddingValues(bottom = SharedDimensions.spacing2xl),
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
            ) {
                itemsIndexed(types, key = { _, type -> type.id }) { index, type ->
                    val checked = type.id in selectedIds
                    RethinkListItem(
                        headline = type.name,
                        supporting = type.description,
                        position = dnsCardPosition(index, types.lastIndex),
                        enabled = !autoMode,
                        defaultContainerColor = if (checked && !autoMode) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        onClick = { if (!autoMode) onToggle(type.id) },
                        trailing = {
                            Box(Modifier.width(SharedDimensions.touchTargetMin), contentAlignment = Alignment.CenterEnd) {
                                Checkbox(checked = checked, enabled = !autoMode, onCheckedChange = { if (!autoMode) onToggle(type.id) })
                            }
                        },
                    )
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
    RethinkModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            RethinkDnsSheetCard {
                Text(state.heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (state.version.isNotBlank()) Text(state.version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionHeader(strings.state)
            RethinkListGroup {
                RethinkListItem(
                    headline = if (state.enabled) strings.disable else strings.enable,
                    supporting = strings.toggleDescription,
                    leadingIcon = MaterialSymbols.Filled.Security,
                    position = CardPosition.Single,
                    onClick = onEnableToggle,
                )
            }
            SectionHeader(strings.actions)
            RethinkListGroup {
                RethinkListItem(headline = strings.configure, leadingIcon = MaterialSymbols.Filled.Settings, position = CardPosition.First, enabled = state.canConfigure, onClick = onConfigure)
                RethinkListItem(headline = strings.copy, leadingIcon = MaterialSymbols.Filled.ContentCopy, position = CardPosition.Middle, enabled = state.canCopy, onClick = onCopy)
                RethinkListItem(headline = strings.search, leadingIcon = MaterialSymbols.Filled.Search, position = CardPosition.Last, enabled = state.canSearch, onClick = onSearch)
            }
            SectionHeader(strings.maintenance)
            RethinkListGroup {
                val maintenance = buildList {
                    if (state.showCheckUpdate) add(RethinkLocalBlocklistAction(strings.checkUpdate, MaterialSymbols.Filled.Refresh, !state.checking, state.checking, onCheckUpdate))
                    if (state.showDownload) add(RethinkLocalBlocklistAction(strings.download, MaterialSymbols.Filled.Download, !state.downloading, state.downloading, onDownload))
                    if (state.showRedownload) add(RethinkLocalBlocklistAction(strings.redownload, MaterialSymbols.Filled.Download, !state.redownloading, state.redownloading, onRedownload))
                    add(RethinkLocalBlocklistAction(strings.delete, MaterialSymbols.Filled.Delete, true, false, onDelete))
                }
                maintenance.forEachIndexed { index, item ->
                    RethinkListItem(
                        headline = item.label,
                        leadingIcon = item.icon,
                        leadingIconTint = if (item.label == strings.delete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        position = dnsCardPosition(index, maintenance.lastIndex),
                        enabled = item.enabled,
                        onClick = item.onClick,
                        trailing = if (item.showProgress) ({ CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }) else null,
                    )
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
)

@Composable
private fun RethinkDnsSheetCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius3xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(SharedDimensions.dividerThicknessBold, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
    ) {
        Column(Modifier.padding(SharedDimensions.cardPadding), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), content = content)
    }
}

@Composable
private fun RethinkDnsSheetPill(text: String) {
    Surface(shape = RoundedCornerShape(SharedDimensions.chipCornerRadius), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingXs))
    }
}

private fun dnsCardPosition(index: Int, lastIndex: Int) = when {
    lastIndex <= 0 -> CardPosition.Single
    index == 0 -> CardPosition.First
    index == lastIndex -> CardPosition.Last
    else -> CardPosition.Middle
}
