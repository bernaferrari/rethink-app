package com.bernaferrari.bravedns.backup

import android.app.Application
import android.content.Context
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import com.bernaferrari.bravedns.database.*
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RoomCheckpointTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var app: AppDatabase
    private lateinit var logs: LogDatabase

    // Bundled uses Room's pool; AndroidSQLiteDriver otherwise bypasses it using its own pool.
    // Keep the real host SQLite engine while exercising the production reader/writer routing.
    private val roomPooledDriver = object : SQLiteDriver {
        override val hasConnectionPool: Boolean = false
        override fun open(fileName: String) = AndroidSQLiteDriver().open(fileName)
    }

    @Before fun openProductionFactories() {
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        context.deleteDatabase(LogDatabase.LOGS_DATABASE_NAME)
        app = buildConfiguredAppDatabase(context, roomPooledDriver)
        logs = buildLogDatabase(context, roomPooledDriver)
        startKoin { modules(module { single { app }; single { logs } }) }
    }

    @After fun closeDatabases() {
        stopKoin()
        app.close()
        logs.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        context.deleteDatabase(LogDatabase.LOGS_DATABASE_NAME)
    }

    private suspend fun writeRealRoomData() {
        assertEquals(7, app.rethinkEndpointDao().getCount())
        logs.eventDao().insert(Event(timestamp = 1, eventType = EventType.VPN_START,
            severity = Severity.LOW, message = "checkpoint-data", source = EventSource.SYSTEM))
    }

    @Test fun `production Room checkpoint flushes both databases to standalone main files`() = runTest {
        writeRealRoomData()
        BackupHelper.checkpointRoomDatabases(app, logs)
        val appCopy = context.getDatabasePath(AppDatabase.DATABASE_NAME).copyTo(File(temporary.root, "app.db"))
        val logCopy = context.getDatabasePath(LogDatabase.LOGS_DATABASE_NAME).copyTo(File(temporary.root, "logs.db"))
        assertEquals(7L, scalar(appCopy, "SELECT COUNT(*) FROM RethinkDnsEndpoint"))
        assertEquals(1L, scalar(logCopy, "SELECT COUNT(*) FROM Events"))
        assertEquals("checkpoint-data", logs.eventDao().getLatest(1).single().message)
    }

    @Test fun `current schema restore preflight succeeds with real live Room databases`() = runTest {
        writeRealRoomData()
        val directory = temporary.newFolder()
        for (name in listOf(AppDatabase.DATABASE_NAME, LogDatabase.LOGS_DATABASE_NAME)) {
            assertTrue(DatabaseSnapshot.create(context.getDatabasePath(name), File(directory, name), AndroidSQLiteDriver()))
        }
        RestoreAgent(context, mockk<WorkerParameters>(relaxed = true)).validateSchemaIdentities(directory)
        assertEquals(7, app.rethinkEndpointDao().getCount())
        assertEquals(1, logs.eventDao().getLatest(10).size)
    }

    private fun scalar(file: File, sql: String): Long = AndroidSQLiteDriver().open(file.path).use { db ->
        db.prepare(sql).use { statement -> assertTrue(statement.step()); statement.getLong(0) }
    }
}
