/*
 * Copyright 2025 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.database

import android.database.Cursor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
private data class DatabaseTablePreview(
    val table: String,
    val rowCount: Int,
    val columnCount: Int,
    val dumpPreview: String,
    val isTruncated: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(
    onBackClick: () -> Unit,
    appDatabase: AppDatabase
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var tables by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTable by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<DatabaseTablePreview?>(null) }
    var loadingPreview by remember { mutableStateOf(false) }
    var loadingCopy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val filteredTables = remember(tables, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            tables
        } else {
            tables.filter { it.contains(q, ignoreCase = true) }
        }
    }

    fun loadDatabaseTables() {
        scope.launch(Dispatchers.IO) {
            val db = appDatabase.openHelper.readableDatabase
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
            val tableList = mutableListOf<String>()
            while (cursor.moveToNext()) {
                val tableName = cursor.getString(0)
                if (tableName != "android_metadata" && tableName != "room_master_table") {
                    tableList.add(tableName)
                }
            }
            cursor.close()
            withContext(Dispatchers.Main) {
                tables = tableList
                isLoading = false
            }
        }
    }

    fun refreshSelection() {
        val table = selectedTable ?: return
        loadingPreview = true
        errorText = null
        scope.launch(Dispatchers.IO) {
            runCatching { loadTablePreview(appDatabase, table) }
                .onSuccess {
                    withContext(Dispatchers.Main) {
                        preview = it
                        loadingPreview = false
                    }
                }
                .onFailure {
                    withContext(Dispatchers.Main) {
                        errorText = it.message ?: context.getString(R.string.blocklist_update_check_failure)
                        loadingPreview = false
                    }
                }
        }
    }

    LaunchedEffect(Unit) {
        loadDatabaseTables()
    }

    LaunchedEffect(selectedTable) {
        val table = selectedTable ?: return@LaunchedEffect
        if (preview?.table != table || preview?.rowCount == -1) {
            refreshSelection()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.title_database_dump),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back_24),
                            contentDescription = stringResource(id = R.string.cd_navigate_back)
                        )
                    }
                },
                actions = {
                    if (selectedTable != null) {
                        TextButton(
                            onClick = {
                                val table = selectedTable ?: return@TextButton
                                loadingCopy = true
                                scope.launch(Dispatchers.IO) {
                                    val fullDump = buildTableDump(appDatabase, table)
                                    withContext(Dispatchers.Main) {
                                        copyToClipboard(context, "db_dump", fullDump)
                                        loadingCopy = false
                                    }
                                }
                            },
                            enabled = preview != null && !loadingCopy
                        ) {
                            Text(
                                text = if (loadingCopy) {
                                    context.getString(R.string.database_inspector_copying)
                                } else {
                                    context.getString(R.string.database_inspector_copy_full)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = stringResource(R.string.database_inspector_search_hint)) },
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val isWide = maxWidth >= 840.dp
                    if (isWide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DatabaseTableListPane(
                                tables = filteredTables,
                                selectedTable = selectedTable,
                                modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
                                onSelect = { selectedTable = it }
                            )
                            DatabaseTableDetailPane(
                                preview = preview,
                                loadingPreview = loadingPreview,
                                errorText = errorText,
                                modifier = Modifier.weight(1f),
                                onRefresh = { refreshSelection() }
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DatabaseTableListPane(
                                tables = filteredTables,
                                selectedTable = selectedTable,
                                modifier = Modifier.weight(0.42f),
                                onSelect = { selectedTable = it }
                            )
                            DatabaseTableDetailPane(
                                preview = preview,
                                loadingPreview = loadingPreview,
                                errorText = errorText,
                                modifier = Modifier.weight(0.58f),
                                onRefresh = { refreshSelection() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatabaseTableListPane(
    tables: List<String>,
    selectedTable: String?,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        if (tables.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.database_inspector_no_tables),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = stringResource(R.string.database_inspector_tables_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(tables) { table ->
                        val isSelected = selectedTable == table
                        val index = tables.indexOf(table)
                        val shape = when (index) {
                            0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                            tables.lastIndex -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                            else -> RoundedCornerShape(4.dp)
                        }
                        val containerColor = when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }

                        ListItem(
                            headlineContent = {
                                Text(
                                    text = table,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            },
                            leadingContent = {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_backup),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            },
                            trailingContent = if (isSelected) {
                                {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_tick),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null,
                            colors = ListItemDefaults.colors(
                                containerColor = containerColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .clickable { onSelect(table) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DatabaseTableDetailPane(
    preview: DatabaseTablePreview?,
    loadingPreview: Boolean,
    errorText: String?,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (preview == null && !loadingPreview) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.database_inspector_select_table),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            if (preview != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = preview.table,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.database_inspector_rows,
                                    preview.rowCount.toString()
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.database_inspector_columns,
                                    preview.columnCount.toString()
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRefresh, enabled = !loadingPreview) {
                        Text(text = stringResource(R.string.database_inspector_refresh))
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
            }

            if (!errorText.isNullOrBlank()) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (loadingPreview) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val text = preview?.dumpPreview.orEmpty()
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                        if (preview?.isTruncated == true) {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.database_inspector_preview_truncated),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun loadTablePreview(appDatabase: AppDatabase, table: String, maxRows: Int = 140): DatabaseTablePreview {
    val rowCount = getTableRowCount(appDatabase, table)
    val columnCount = getTableColumnCount(appDatabase, table)
    val preview = buildTableDump(appDatabase, table, maxRows = maxRows)
    return DatabaseTablePreview(
        table = table,
        rowCount = rowCount,
        columnCount = columnCount,
        dumpPreview = preview,
        isTruncated = rowCount > maxRows
    )
}

private fun getTableRowCount(appDatabase: AppDatabase, table: String): Int {
    val safeTable = table.replace("`", "``")
    val db = appDatabase.openHelper.readableDatabase
    val cursor = db.query("SELECT COUNT(*) FROM `$safeTable`")
    val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
    cursor.close()
    return count
}

private fun getTableColumnCount(appDatabase: AppDatabase, table: String): Int {
    val safeTable = table.replace("`", "``")
    val db = appDatabase.openHelper.readableDatabase
    val cursor = db.query("SELECT * FROM `$safeTable` LIMIT 1")
    val count = cursor.columnCount
    cursor.close()
    return count
}

private fun buildTableDump(appDatabase: AppDatabase, table: String, maxRows: Int? = null): String {
    val safeTable = table.replace("`", "``")
    val db = appDatabase.openHelper.readableDatabase
    val cursor = db.query("SELECT * FROM `$safeTable`")
    val columnNames = cursor.columnNames
    val result = StringBuilder()
    result.append("Table: $table\n")
    result.append(columnNames.joinToString(separator = "\t"))
    result.append("\n")
    var rowCount = 0
    var isTruncated = false
    while (cursor.moveToNext()) {
        if (maxRows != null && rowCount >= maxRows) {
            isTruncated = true
            break
        }
        for (i in columnNames.indices) {
            result.append(cursorValueAsText(cursor, i)).append("\t")
        }
        result.append("\n")
        rowCount++
    }
    cursor.close()
    if (isTruncated) {
        result.append("…\n")
    }
    return result.toString()
}

private fun cursorValueAsText(cursor: Cursor, index: Int): String {
    return when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> "null"
        Cursor.FIELD_TYPE_BLOB -> {
            val size = cursor.getBlob(index)?.size ?: 0
            "[blob:$size]"
        }
        else -> cursor.getString(index).orEmpty()
    }
}

private fun copyToClipboard(context: android.content.Context, label: String, text: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    android.widget.Toast.makeText(context, context.getString(R.string.copied_clipboard), android.widget.Toast.LENGTH_SHORT).show()
}
