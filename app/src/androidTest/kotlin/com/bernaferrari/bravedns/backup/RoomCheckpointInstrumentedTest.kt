package com.bernaferrari.bravedns.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.database.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Uses the production Room factories and default Bundled driver, in isolated database files. */
@RunWith(AndroidJUnit4::class)
class RoomCheckpointInstrumentedTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private lateinit var app: AppDatabase
    private lateinit var logs: LogDatabase

    @Before fun openIsolatedDatabases() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        directory = File(base.cacheDir, "checkpoint-test-${UUID.randomUUID()}").apply { check(mkdirs()) }
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(directory, name)
        }
        app = buildConfiguredAppDatabase(context)
        logs = buildLogDatabase(context)
    }

    @After fun closeIsolatedDatabases() {
        if (::app.isInitialized) app.close()
        if (::logs.isInitialized) logs.close()
        if (::directory.isInitialized) directory.deleteRecursively()
    }

    @Test fun defaultBundledRoomFactoriesCheckpointBothDatabases() = runBlocking {
        assertEquals(7, app.rethinkEndpointDao().getCount())
        logs.eventDao().insert(Event(timestamp = 1, eventType = EventType.VPN_START,
            severity = Severity.LOW, message = "bundled-checkpoint", source = EventSource.SYSTEM))
        BackupHelper.checkpointRoomDatabases(app, logs)
        val appCopy = context.getDatabasePath(AppDatabase.DATABASE_NAME).copyTo(File(directory, "app-copy.db"))
        val logCopy = context.getDatabasePath(LogDatabase.LOGS_DATABASE_NAME).copyTo(File(directory, "log-copy.db"))
        assertEquals(7L, scalar(appCopy, "SELECT COUNT(*) FROM RethinkDnsEndpoint"))
        assertEquals(1L, scalar(logCopy, "SELECT COUNT(*) FROM Events"))
        assertEquals("bundled-checkpoint", logs.eventDao().getLatest(1).single().message)
    }

    private fun scalar(file: File, sql: String): Long = BundledSQLiteDriver().open(file.path).use { db ->
        db.prepare(sql).use { statement -> assertTrue(statement.step()); statement.getLong(0) }
    }
}
