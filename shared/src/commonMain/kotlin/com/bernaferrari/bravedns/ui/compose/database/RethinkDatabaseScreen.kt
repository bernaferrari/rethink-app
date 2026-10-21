@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.database

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFilterChip
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

data class RethinkDatabaseTablePreview(
    val table: String,
    val rowCount: Int,
    val columnCount: Int,
    val dumpPreview: String,
    val isTruncated: Boolean,
)

data class RethinkDatabaseStrings(
    val title: String,
    val searchHint: String,
    val clearSearch: String,
    val copyFull: String,
    val copying: String,
    val refresh: String,
    val tables: String,
    val noTables: String,
    val rows: @Composable (Int) -> String,
    val columns: @Composable (Int) -> String,
    val previewTruncated: String,
)

/** Portable database-inspector renderer. A host supplies the actual database reads and clipboard. */
@Composable
fun RethinkDatabaseScreen(
    tables: List<String>,
    selectedTable: String?,
    preview: RethinkDatabaseTablePreview?,
    isLoadingTables: Boolean,
    isLoadingPreview: Boolean,
    isCopying: Boolean,
    errorText: String?,
    strings: RethinkDatabaseStrings,
    onTableSelected: (String) -> Unit,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val filteredTables = remember(tables, query) {
        val normalized = query.trim()
        if (normalized.isEmpty()) tables else tables.filter { it.contains(normalized, ignoreCase = true) }
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkTopBar(
                title = strings.title,
                // Copy lives once, next to the dump it copies (see RethinkDatabaseDetail).
                onBackClick = onBackClick,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = SharedDimensions.screenPaddingHorizontal)
                .padding(bottom = SharedDimensions.spacingLg),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            RethinkSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = strings.searchHint,
                clearQueryContentDescription = strings.clearSearch,
                onClearQuery = { query = "" },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            )
            if (isLoadingTables) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 860.dp
                    if (wide) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
                            RethinkDatabaseTableList(
                                tables = filteredTables,
                                totalCount = tables.size,
                                selectedTable = selectedTable,
                                strings = strings,
                                onTableSelected = onTableSelected,
                                modifier = Modifier.widthIn(min = 300.dp, max = 380.dp),
                            )
                            RethinkDatabaseDetail(
                                preview, selectedTable, isLoadingPreview, isCopying, errorText, strings,
                                onRefresh, onCopy, Modifier.weight(1f),
                            )
                        }
                    } else if (filteredTables.isEmpty()) {
                        // Narrow layouts have no table pane to host the message, so say it here
                        // instead of leaving a stale preview under an empty chip row.
                        RethinkDatabaseNoTables(strings, Modifier.fillMaxSize())
                    } else {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                            RethinkDatabaseInlineSelector(filteredTables, selectedTable, onTableSelected)
                            RethinkDatabaseDetail(preview, selectedTable, isLoadingPreview, isCopying, errorText, strings, onRefresh, onCopy, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RethinkDatabaseNoTables(strings: RethinkDatabaseStrings, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        RethinkEmptyState(
            title = strings.noTables,
            message = "",
            icon = MaterialSymbols.Filled.Storage,
        )
    }
}

@Composable
private fun RethinkDatabaseInlineSelector(tables: List<String>, selected: String?, onSelect: (String) -> Unit) {
    if (tables.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        tables.forEach { table ->
            RethinkFilterChip(
                label = table,
                selected = selected == table,
                onClick = { if (selected != table) onSelect(table) },
                minHeight = SharedDimensions.touchTargetMin,
                selectedLabelWeight = FontWeight.SemiBold,
                defaultLabelWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun RethinkDatabaseTableList(
    tables: List<String>,
    totalCount: Int,
    selectedTable: String?,
    strings: RethinkDatabaseStrings,
    onTableSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius3xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(SharedDimensions.spacingMd),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(title = strings.tables, modifier = Modifier.weight(1f))
                RethinkDatabaseMetaChip("${tables.size}/$totalCount")
            }
            if (tables.isEmpty()) {
                RethinkDatabaseNoTables(strings, Modifier.fillMaxSize())
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    contentPadding = PaddingValues(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingXs),
                    verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
                ) {
                    tables.forEachIndexed { index, table ->
                        item(key = table) {
                            val active = selectedTable == table
                            RethinkListItem(
                                headline = table,
                                leadingIcon = MaterialSymbols.Filled.Storage,
                                leadingIconTint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                leadingIconContainerColor = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .6f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                position = cardPositionFor(index, tables.lastIndex),
                                highlighted = active,
                                trailing = if (active) { { Icon(MaterialSymbols.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(SharedDimensions.iconSizeSm)) } } else null,
                                onClick = { onTableSelected(table) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RethinkDatabaseDetail(
    preview: RethinkDatabaseTablePreview?,
    selectedTable: String?,
    loadingPreview: Boolean,
    loadingCopy: Boolean,
    errorText: String?,
    strings: RethinkDatabaseStrings,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius3xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize().padding(SharedDimensions.spacingMd), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
            preview?.let {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(it.table, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onRefresh, enabled = !loadingPreview) { Icon(MaterialSymbols.Filled.Refresh, strings.refresh) }
                        TextButton(
                            onClick = onCopy,
                            enabled = selectedTable != null && !loadingCopy,
                            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                        ) {
                            if (loadingCopy) {
                                LoadingIndicator(Modifier.size(ButtonDefaults.IconSize))
                            } else {
                                Icon(MaterialSymbols.Filled.ContentCopy, null, Modifier.size(ButtonDefaults.IconSize))
                            }
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(if (loadingCopy) strings.copying else strings.copyFull)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    RethinkDatabaseMetaChip(strings.rows(it.rowCount))
                    RethinkDatabaseMetaChip(strings.columns(it.columnCount))
                }
            }
            errorText?.takeIf { it.isNotBlank() }?.let { error ->
                Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadiusMd), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .44f)) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(SharedDimensions.spacingMd))
                }
            }
            if (loadingPreview) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            } else if (preview != null) {
                SelectionContainer {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = SharedDimensions.spacingSm, top = SharedDimensions.spacingSm, end = SharedDimensions.spacingSm, bottom = SharedDimensions.spacingXl),
                    ) {
                        Text(preview.dumpPreview, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        if (preview.isTruncated) {
                            Spacer(Modifier.size(SharedDimensions.spacingSm))
                            Text(strings.previewTruncated, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RethinkDatabaseMetaChip(text: String) {
    Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadiusPill), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingXs))
    }
}
