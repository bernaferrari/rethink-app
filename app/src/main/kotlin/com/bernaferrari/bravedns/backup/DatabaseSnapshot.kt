package com.bernaferrari.bravedns.backup

import Logger
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/** Read-consistent single-database snapshot, including committed WAL contents. */
internal object DatabaseSnapshot {
    fun create(source: File, destination: File, driver: SQLiteDriver? = null): Boolean {
        if (!source.isFile || source.absoluteFile.normalize() == destination.absoluteFile.normalize()) return false
        return try {
            destination.delete()
            (driver ?: BundledSQLiteDriver()).open(source.path).use { database ->
                database.prepare("VACUUM INTO ?").use { statement ->
                    statement.bindText(1, destination.absolutePath)
                    statement.step()
                }
            }
            destination.isFile
        } catch (error: SQLiteException) {
            Logger.w(Logger.LOG_TAG_BACKUP_RESTORE, "SQLite snapshot failed; protection remains running", error)
            destination.delete()
            false
        }
    }
}
