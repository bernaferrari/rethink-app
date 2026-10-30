package com.bernaferrari.bravedns.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetainedJournalDriverTest {
    @Test fun preservesTheUnderlyingPoolOwnershipContract() {
        for (pooled in listOf(false, true)) {
            val driver = object : SQLiteDriver {
                override val hasConnectionPool: Boolean = pooled
                override fun open(fileName: String): SQLiteConnection = error("Not opened")
            }
            assertEquals(pooled, RetainedJournalDriver(driver).hasConnectionPool)
        }
    }

    @Test fun configuresEveryNewRealBundledConnectionBeforeAnyRoomTransaction() {
        withFile { path ->
            val driver = RetainedJournalDriver(BundledSQLiteDriver())
            assertFalse(driver.hasConnectionPool)
            driver.open(path).use { first ->
                driver.open(path).use { second ->
                    assertEquals(LIMIT, scalar(first, "PRAGMA journal_size_limit"))
                    assertEquals(LIMIT, scalar(second, "PRAGMA journal_size_limit"))
                    execute(first, "BEGIN IMMEDIATE")
                    execute(first, "CREATE TABLE messages(value TEXT)")
                    execute(first, "COMMIT")
                }
            }
        }
    }

    @Test fun largeActiveWalCanExceedPolicyButIsReducedWhenResetAfterCheckpoint() {
        withFile { path ->
            RetainedJournalDriver(BundledSQLiteDriver()).open(path).use { connection ->
                execute(connection, "PRAGMA journal_mode=WAL")
                execute(connection, "PRAGMA wal_autocheckpoint=0")
                execute(connection, "CREATE TABLE payload(value BLOB)")
                execute(connection, "BEGIN IMMEDIATE")
                execute(connection, "INSERT INTO payload VALUES (zeroblob(41943040))")
                execute(connection, "COMMIT")
                val wal = java.io.File("$path-wal")
                assertTrue(wal.length() > LIMIT, "Policy must not be mistaken for an active-WAL hard cap")
                execute(connection, "PRAGMA wal_checkpoint(FULL)")
                execute(connection, "INSERT INTO payload VALUES (zeroblob(1))")
                assertTrue(wal.length() <= LIMIT, "Reset WAL allocation should respect retained-size policy")
                assertEquals(2L, scalar(connection, "SELECT COUNT(*) FROM payload"))
            }
        }
    }

    private fun execute(connection: SQLiteConnection, sql: String) {
        connection.prepare(sql).use { it.step() }
    }
    private fun scalar(connection: SQLiteConnection, sql: String): Long = connection.prepare(sql).use {
        assertTrue(it.step()); it.getLong(0)
    }
    private fun withFile(block: (String) -> Unit) {
        val directory = Files.createTempDirectory("retained-journal-test").toFile()
        try { block(java.io.File(directory, "test.db").path) } finally { directory.deleteRecursively() }
    }
    private companion object { const val LIMIT = 32L * 1024 * 1024 }
}
