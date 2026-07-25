/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.database

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.database.Cursor
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.room3.support.getSupportWrapper
import com.celzero.bravedns.R
import com.celzero.bravedns.database.AppDatabase
import com.celzero.bravedns.ui.compose.database.RethinkDatabaseScreen
import com.celzero.bravedns.ui.compose.database.RethinkDatabaseStrings
import com.celzero.bravedns.ui.compose.database.RethinkDatabaseTablePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android Room and clipboard adapter for the portable database inspector. */
@Composable
fun DatabaseScreen(onBackClick: () -> Unit, appDatabase: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tables by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTable by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<RethinkDatabaseTablePreview?>(null) }
    var loadingTables by remember { mutableStateOf(true) }
    var loadingPreview by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun refreshPreview(table: String = selectedTable.orEmpty()) {
        if (table.isBlank()) return
        loadingPreview = true
        errorText = null
        scope.launch(Dispatchers.IO) {
            runCatching { loadTablePreview(appDatabase, table) }
                .onSuccess { result -> withContext(Dispatchers.Main) { preview = result; loadingPreview = false } }
                .onFailure { error -> withContext(Dispatchers.Main) {
                    errorText = error.message ?: context.getString(R.string.blocklist_update_check_failure)
                    loadingPreview = false
                } }
        }
    }

    fun copyTable() {
        val table = selectedTable ?: return
        copying = true
        scope.launch(Dispatchers.IO) {
            val dump = buildTableDump(appDatabase, table)
            withContext(Dispatchers.Main) {
                context.copyToClipboard("db_dump", dump)
                copying = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { readTables(appDatabase) }
        tables = loaded
        selectedTable = loaded.firstOrNull()
        loadingTables = false
    }
    LaunchedEffect(selectedTable) {
        selectedTable?.let { if (preview?.table != it) refreshPreview(it) }
    }

    RethinkDatabaseScreen(
        tables = tables,
        selectedTable = selectedTable,
        preview = preview,
        isLoadingTables = loadingTables,
        isLoadingPreview = loadingPreview,
        isCopying = copying,
        errorText = errorText,
        strings = RethinkDatabaseStrings(
            title = stringResource(R.string.title_database_dump),
            searchHint = stringResource(R.string.database_inspector_search_hint),
            clearSearch = stringResource(R.string.cd_clear_search),
            copyFull = stringResource(R.string.database_inspector_copy_full),
            copying = stringResource(R.string.database_inspector_copying),
            refresh = stringResource(R.string.database_inspector_refresh),
            tables = stringResource(R.string.database_inspector_tables_title),
            noTables = stringResource(R.string.database_inspector_no_tables),
            rows = { count -> stringResource(R.string.database_inspector_rows, count.toString()) },
            columns = { count -> stringResource(R.string.database_inspector_columns, count.toString()) },
            previewTruncated = stringResource(R.string.database_inspector_preview_truncated),
        ),
        onTableSelected = { selectedTable = it },
        onRefresh = { refreshPreview() },
        onCopy = { copyTable() },
        onBackClick = onBackClick,
    )
}

private fun readTables(appDatabase: AppDatabase): List<String> {
    val cursor = appDatabase.getSupportWrapper().query("SELECT name FROM sqlite_master WHERE type='table'")
    return buildList {
        while (cursor.moveToNext()) {
            cursor.getString(0)?.takeUnless { it == "android_metadata" || it == "room_master_table" }?.let(::add)
        }
        cursor.close()
    }
}

private fun loadTablePreview(appDatabase: AppDatabase, table: String, maxRows: Int = 140): RethinkDatabaseTablePreview {
    val rowCount = tableRowCount(appDatabase, table)
    val columnCount = tableColumnCount(appDatabase, table)
    return RethinkDatabaseTablePreview(table, rowCount, columnCount, buildTableDump(appDatabase, table, maxRows), rowCount > maxRows)
}

private fun tableRowCount(appDatabase: AppDatabase, table: String): Int {
    val cursor = appDatabase.getSupportWrapper().query("SELECT COUNT(*) FROM `${table.safeSqlName()}`")
    return (if (cursor.moveToFirst()) cursor.getInt(0) else 0).also { cursor.close() }
}

private fun tableColumnCount(appDatabase: AppDatabase, table: String): Int {
    val cursor = appDatabase.getSupportWrapper().query("SELECT * FROM `${table.safeSqlName()}` LIMIT 1")
    return cursor.columnCount.also { cursor.close() }
}

private fun buildTableDump(appDatabase: AppDatabase, table: String, maxRows: Int? = null): String {
    val cursor = appDatabase.getSupportWrapper().query("SELECT * FROM `${table.safeSqlName()}`")
    val names = cursor.columnNames
    return buildString {
        append("Table: $table\n")
        append(names.joinToString("\t")).append('\n')
        var rows = 0
        while (cursor.moveToNext() && (maxRows == null || rows < maxRows)) {
            names.indices.forEach { index -> append(cursor.valueAsText(index)).append('\t') }
            append('\n')
            rows++
        }
        if (maxRows != null && rows >= maxRows && cursor.moveToNext()) append("…\n")
        cursor.close()
    }
}

private fun String.safeSqlName(): String = replace("`", "``")

private fun Cursor.valueAsText(index: Int): String = when (getType(index)) {
    Cursor.FIELD_TYPE_NULL -> "null"
    Cursor.FIELD_TYPE_BLOB -> "[blob:${getBlob(index)?.size ?: 0}]"
    else -> getString(index).orEmpty()
}

private fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, getString(R.string.copied_clipboard), Toast.LENGTH_SHORT).show()
}
