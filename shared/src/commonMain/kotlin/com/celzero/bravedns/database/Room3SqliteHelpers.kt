package com.celzero.bravedns.database

import androidx.sqlite.SQLiteConnection

internal suspend fun SQLiteConnection.execSQL(sql: String) {
    val stmt = prepare(sql)
    try {
        while (stmt.step()) { }
    } finally {
        stmt.close()
    }
}

internal suspend fun SQLiteConnection.userVersion(): Int {
    val stmt = prepare("PRAGMA user_version")
    try {
        return if (stmt.step()) stmt.getLong(0).toInt() else 0
    } finally {
        stmt.close()
    }
}

// Room invokes these migration helpers serially for a connection. Keying the status by the
// connection is portable to WASM and is safer than the JVM-only ThreadLocal implementation.
private val transactionSuccess = mutableMapOf<SQLiteConnection, Boolean>()

internal suspend fun SQLiteConnection.beginTransaction() {
    transactionSuccess[this] = false
    execSQL("BEGIN IMMEDIATE")
}

internal fun SQLiteConnection.setTransactionSuccessful() {
    transactionSuccess[this] = true
}

internal suspend fun SQLiteConnection.endTransaction() {
    val ok = transactionSuccess.remove(this) == true
    execSQL(if (ok) "COMMIT" else "ROLLBACK")
}

internal suspend fun SQLiteConnection.enableWriteAheadLogging() {
    execSQL("PRAGMA journal_mode=WAL")
}

internal suspend fun SQLiteConnection.disableWriteAheadLogging() {
    execSQL("PRAGMA journal_mode=DELETE")
}

internal suspend fun SQLiteConnection.tableExists(table: String): Boolean {
    val leaf = table.substringAfterLast('.')
    val stmt = prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1")
    try {
        stmt.bindText(1, leaf)
        return stmt.step()
    } finally {
        stmt.close()
    }
}

internal suspend fun SQLiteConnection.doesColumnExistInTable(tableName: String, columnName: String): Boolean {
    val stmt = prepare("PRAGMA table_info(`$tableName`)")
    try {
        while (stmt.step()) {
            if (stmt.getText(1) == columnName) return true
        }
    } finally {
        stmt.close()
    }
    return false
}
