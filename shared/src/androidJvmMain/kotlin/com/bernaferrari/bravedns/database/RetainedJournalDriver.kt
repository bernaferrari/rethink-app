/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver

/**
 * Keep at most 32 MiB of journal allocation after SQLite resets/checkpoints it. This is not a
 * cap on an active WAL: readers can still hold a checkpoint back. The pragma is connection-local,
 * so configure each newly opened connection before Room installs transactions or query-only mode.
 */
internal class RetainedJournalDriver(private val delegate: SQLiteDriver) : SQLiteDriver {
    override val hasConnectionPool: Boolean get() = delegate.hasConnectionPool

    // This resource handoff must close on every failure (including fatal errors), then rethrow
    // the original failure with any close error suppressed. No failure is swallowed here.
    @Suppress("TooGenericExceptionCaught")
    override fun open(fileName: String): SQLiteConnection {
        val connection = delegate.open(fileName)
        try {
            connection.prepare("PRAGMA journal_size_limit=$RETAINED_JOURNAL_BYTES").use { statement ->
                check(statement.step() && statement.getLong(0) == RETAINED_JOURNAL_BYTES) {
                    "SQLite did not accept the retained journal size policy"
                }
            }
            return connection
        } catch (error: Throwable) {
            try { connection.close() } catch (closing: Throwable) { error.addSuppressed(closing) }
            throw error
        }
    }

    companion object {
        const val RETAINED_JOURNAL_BYTES = 32L * 1024 * 1024
    }
}
