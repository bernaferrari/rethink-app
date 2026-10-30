package com.bernaferrari.bravedns.backup

import androidx.sqlite.driver.AndroidSQLiteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class, sdk = [28])
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.NATIVE)
class DatabaseSnapshotTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun `snapshot includes committed WAL and remains stable while source changes`() {
        val source = File(directory.root, "source.db")
        val target = File(directory.root, "snapshot.db")
        AndroidSQLiteDriver().open(source.path).use { connection ->
            connection.prepare("PRAGMA journal_mode=WAL").use { it.step() }
            connection.prepare("CREATE TABLE sample(value INTEGER)").use { it.step() }
            connection.prepare("INSERT INTO sample VALUES(7)").use { it.step() }
            assertTrue(DatabaseSnapshot.create(source, target, AndroidSQLiteDriver()))
            connection.prepare("INSERT INTO sample VALUES(9)").use { it.step() }
            AndroidSQLiteDriver().open(target.path).use { snapshot ->
                snapshot.prepare("SELECT SUM(value) FROM sample").use {
                    assertTrue(it.step())
                    assertEquals(7L, it.getLong(0))
                }
            }
        }
    }
}
