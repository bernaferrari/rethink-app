@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.database

import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWellContainerColor
import com.bernaferrari.bravedns.ui.compose.common.RethinkWindowBreakpoints
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.bernaferrari.bravedns.ui.compose.theme.RethinkContainedCard

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
            )
            if (isLoadingTables) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { RethinkLoadingState(label = "") }
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= RethinkWindowBreakpoints.MediumMax
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
                // Selection uses the same secondaryContainer as connected groups and toolbar toggles.
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                textStyle = MaterialTheme.typography.labelLarge,
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
        shape = RoundedCornerShape(SharedDimensions.groupCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(SharedDimensions.spacingMd),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(title = strings.tables, modifier = Modifier.weight(1f))
                RethinkPill("${tables.size}/$totalCount")
            }
            if (tables.isEmpty()) {
                RethinkDatabaseNoTables(strings, Modifier.fillMaxSize())
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    contentPadding = PaddingValues(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingXs),
                ) {
                    tables.forEachIndexed { index, table ->
                        item(key = table) {
                            val active = selectedTable == table
                            RethinkListItem(
                                headline = table,
                                leadingIcon = MaterialSymbols.Filled.Storage,
                                leadingIconTint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                leadingIconContainerColor = if (active) RethinkIconWellContainerColor else MaterialTheme.colorScheme.surfaceContainerHighest,
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
        shape = RoundedCornerShape(SharedDimensions.groupCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize().padding(SharedDimensions.spacingMd), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
            preview?.let {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(it.table, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    // Refresh + copy as one tonal pair of equal height (40dp), not a bare icon
                    // beside a text button.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                        FilledTonalIconButton(
                            onClick = onRefresh,
                            enabled = !loadingPreview,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Icon(MaterialSymbols.Filled.Refresh, strings.refresh, Modifier.size(SharedDimensions.iconSizeSm))
                        }
                        FilledTonalButton(
                            onClick = onCopy,
                            enabled = selectedTable != null && !loadingCopy,
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        ) {
                            if (loadingCopy) {
                                LoadingIndicator(Modifier.size(ButtonDefaults.IconSize))
                            } else {
                                Icon(MaterialSymbols.Filled.ContentCopy, null, Modifier.size(ButtonDefaults.IconSize))
                            }
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(if (loadingCopy) strings.copying else strings.copyFull, maxLines = 1)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    RethinkPill(strings.rows(it.rowCount))
                    RethinkPill(strings.columns(it.columnCount))
                }
            }
            errorText?.takeIf { it.isNotBlank() }?.let { error ->
                RethinkContainedCard(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .44f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    contentPadding = PaddingValues(SharedDimensions.spacingMd),
                ) {
                    Text(error, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (loadingPreview) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { RethinkLoadingState(label = "") }
            } else if (preview != null) {
                // The dump sits in its own inset well so the monospace text has a clear edge and
                // comfortable margins instead of running into the card's chrome.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(SharedDimensions.cornerRadiusLg),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    SelectionContainer {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(SharedDimensions.cardPadding),
                        ) {
                            Text(
                                preview.dumpPreview,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (preview.isTruncated) {
                                Spacer(Modifier.size(SharedDimensions.spacingMd))
                                Text(strings.previewTruncated, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

