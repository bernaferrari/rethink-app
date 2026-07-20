package com.celzero.bravedns.database

import androidx.sqlite.SQLiteConnection

internal fun SQLiteConnection.execSQL(sql: String) {
    val stmt = prepare(sql)
    try {
        while (stmt.step()) { }
    } finally {
        stmt.close()
    }
}

internal fun SQLiteConnection.userVersion(): Int {
    val stmt = prepare("PRAGMA user_version")
    try {
        return if (stmt.step()) stmt.getLong(0).toInt() else 0
    } finally {
        stmt.close()
    }
}

private val transactionSuccess = ThreadLocal<Boolean?>()

internal fun SQLiteConnection.beginTransaction() {
    transactionSuccess.set(false)
    execSQL("BEGIN IMMEDIATE")
}

internal fun SQLiteConnection.setTransactionSuccessful() {
    transactionSuccess.set(true)
}

internal fun SQLiteConnection.endTransaction() {
    val ok = transactionSuccess.get() == true
    transactionSuccess.remove()
    execSQL(if (ok) "COMMIT" else "ROLLBACK")
}

internal fun SQLiteConnection.enableWriteAheadLogging() {
    execSQL("PRAGMA journal_mode=WAL")
}

internal fun SQLiteConnection.disableWriteAheadLogging() {
    execSQL("PRAGMA journal_mode=DELETE")
}

internal fun SQLiteConnection.tableExists(table: String): Boolean {
    val leaf = table.substringAfterLast('.')
    val stmt = prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1")
    try {
        stmt.bindText(1, leaf)
        return stmt.step()
    } finally {
        stmt.close()
    }
}

internal fun SQLiteConnection.doesColumnExistInTable(tableName: String, columnName: String): Boolean {
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
