package com.bernaferrari.bravedns.database

import androidx.room3.RoomDatabase
import androidx.room3.executeSQL
import androidx.room3.useWriterConnection

/** Compaction and WAL checkpointing both mutate SQLite and must not use a reader or a transaction. */
internal suspend fun RoomDatabase.compactAndCheckpoint() = useWriterConnection { connection ->
    check(!connection.inTransaction()) { "Database maintenance requires a connection outside a transaction" }
    connection.executeSQL("VACUUM")
    // VACUUM itself can write WAL pages; checkpoint after it so main-file copies are complete.
    connection.usePrepared("PRAGMA wal_checkpoint(FULL)") { statement ->
        check(statement.step()) { "Database WAL checkpoint returned no result" }
        val busy = statement.getLong(0)
        val frames = statement.getLong(1)
        val checkpointed = statement.getLong(2)
        check(busy == 0L && frames == checkpointed) {
            "Incomplete WAL checkpoint: busy=$busy, frames=$frames, checkpointed=$checkpointed"
        }
    }
}
