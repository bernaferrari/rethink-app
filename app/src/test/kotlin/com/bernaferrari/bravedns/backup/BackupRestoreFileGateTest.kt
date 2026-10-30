package com.bernaferrari.bravedns.backup

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BackupRestoreFileGateTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun `cancellation keeps snapshot and restart observers out until rollback cleanup finishes`() = runTest {
        val live = directory.newFolder("live")
        val incoming = directory.newFolder("incoming")
        val names = listOf("app.db", "logs.db")
        names.forEach { File(live, it).writeText("original"); File(incoming, it).writeText("replacement") }
        val staging = File(directory.root, "transaction")
        val transaction = RestoreFileTransaction(live, staging, names)
        val installed = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val restore = launch {
            BackupRestoreFileGate.withSourceFiles {
                transaction.stage(incoming)
                transaction.install()
                try {
                    withContext(NonCancellable) { installed.complete(Unit); finish.await() }
                } finally {
                    transaction.rollback()
                    transaction.commit()
                    events += "cleanup"
                }
            }
        }
        installed.await()
        try {
            restore.cancel()
            val snapshot = async {
                BackupRestoreFileGate.withSourceFiles {
                    events += "snapshot"
                    names.map { File(live, it).readText() }
                }
            }
            val restart = async { BackupRestoreFileGate.awaitIdle(); events += "restart" }
            runCurrent()
            assertFalse(snapshot.isCompleted)
            assertFalse(restart.isCompleted)
            finish.complete(Unit)
            restore.join()
            assertEquals(listOf("original", "original"), snapshot.await())
            restart.await()
            assertEquals(listOf("cleanup", "snapshot", "restart"), events)
            assertFalse(staging.exists())
        } finally { finish.complete(Unit); restore.cancelAndJoin() }
    }

    @Test fun `private snapshot remains exportable while restore replaces live files`() = runTest {
        val live = File(directory.root, "live.db").apply { writeText("original") }
        val staged = File(directory.root, "snapshot.db")
        val snapshotReady = CompletableDeferred<Unit>()
        val finishExport = CompletableDeferred<Unit>()
        val backup = async {
            BackupRestoreFileGate.withSourceFiles { live.copyTo(staged) }
            snapshotReady.complete(Unit)
            finishExport.await()
            staged.readText()
        }
        snapshotReady.await()
        try {
            val restore = async { BackupRestoreFileGate.withSourceFiles { live.writeText("replacement") } }
            runCurrent()
            assertTrue(restore.isCompleted)
            assertFalse(backup.isCompleted)
            assertEquals("replacement", live.readText())
            finishExport.complete(Unit)
            assertEquals("original", backup.await())
        } finally { finishExport.complete(Unit); backup.cancelAndJoin() }
    }

    @Test fun `queued restore captures originals only after the previous restore commits`() = runTest {
        val live = directory.newFolder("restore-live")
        val firstSource = directory.newFolder("first-source")
        val secondSource = directory.newFolder("second-source")
        val names = listOf("app.db", "logs.db")
        names.forEach {
            File(live, it).writeText("original")
            File(firstSource, it).writeText("first")
            File(secondSource, it).writeText("second")
        }
        val firstInstalled = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Unit>()
        val first = launch {
            BackupRestoreFileGate.withSourceFiles {
                val transaction = RestoreFileTransaction(live, File(directory.root, "first-stage"), names)
                transaction.stage(firstSource)
                transaction.install()
                firstInstalled.complete(Unit)
                finishFirst.await()
                transaction.commit()
            }
        }
        firstInstalled.await()
        try {
            val second = async {
                BackupRestoreFileGate.withSourceFiles {
                    val originals = names.map { File(live, it).readText() }
                    val transaction = RestoreFileTransaction(live, File(directory.root, "second-stage"), names)
                    transaction.stage(secondSource)
                    transaction.install()
                    transaction.commit()
                    originals
                }
            }
            runCurrent()
            assertFalse(second.isCompleted)
            finishFirst.complete(Unit)
            first.join()
            assertEquals(listOf("first", "first"), second.await())
            assertEquals(listOf("second", "second"), names.map { File(live, it).readText() })
        } finally { finishFirst.complete(Unit); first.cancelAndJoin() }
    }

}
