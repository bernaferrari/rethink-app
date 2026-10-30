package com.bernaferrari.bravedns.backup

import android.app.Application
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.mockk.*
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class RestoreWorkTest {
    @Test fun `KEEP observes existing accepted work instead of ignored newly generated ids`() = runTest {
        val manager = mockk<WorkManager>()
        val requests = mutableListOf<OneTimeWorkRequest>()
        val operation = mockk<Operation>()
        val accepted = info(WorkInfo.State.RUNNING)
        every { manager.enqueueUniqueWork(RestoreWork.NAME, ExistingWorkPolicy.KEEP, capture(requests)) } returns operation
        every { manager.getWorkInfosForUniqueWorkFlow(RestoreWork.NAME) } returns MutableStateFlow(listOf(accepted))
        RestoreWork.enqueue(manager, Uri.parse("content://backup/first.zip"))
        RestoreWork.enqueue(manager, Uri.parse("content://backup/second.zip"))
        assertEquals(2, requests.size)
        assertTrue(requests.none { it.id == accepted.id })
        assertSame(accepted, RestoreWork.observe(manager).filterNotNull().first())
        verify(exactly = 2) { manager.enqueueUniqueWork(RestoreWork.NAME, ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>()) }
        verify(exactly = 1) { manager.getWorkInfosForUniqueWorkFlow(RestoreWork.NAME) }
    }

    @Test fun `terminal cancellation is not exposed before live-file cleanup finishes`() = runTest {
        val manager = mockk<WorkManager>()
        val cancelled = info(WorkInfo.State.CANCELLED)
        every { manager.getWorkInfosForUniqueWorkFlow(RestoreWork.NAME) } returns MutableStateFlow(listOf(cancelled))
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var cleaned = false
        val owner = launch {
            BackupRestoreFileGate.withSourceFiles {
                try { withContext(NonCancellable) { entered.complete(Unit); finish.await() } }
                finally { cleaned = true }
            }
        }
        entered.await()
        try {
            owner.cancel()
            val observed = async { RestoreWork.observe(manager).filterNotNull().first() }
            runCurrent()
            assertFalse(observed.isCompleted)
            finish.complete(Unit)
            owner.join()
            assertSame(cancelled, observed.await())
            assertTrue(cleaned)
        } finally { finish.complete(Unit); owner.cancelAndJoin() }
    }

    @Test fun `new accepted work supersedes a stale terminal event waiting for cleanup`() = runTest {
        val manager = mockk<WorkManager>()
        val states = MutableStateFlow(listOf(info(WorkInfo.State.CANCELLED)))
        every { manager.getWorkInfosForUniqueWorkFlow(RestoreWork.NAME) } returns states
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val owner = launch { BackupRestoreFileGate.withSourceFiles { entered.complete(Unit); finish.await() } }
        entered.await()
        try {
            val observed = async { RestoreWork.observe(manager).filterNotNull().first() }
            runCurrent()
            assertFalse(observed.isCompleted)
            val accepted = info(WorkInfo.State.RUNNING)
            states.value = listOf(accepted)
            runCurrent()
            assertSame(accepted, observed.await())
        } finally { finish.complete(Unit); owner.cancelAndJoin() }
    }

    private fun info(state: WorkInfo.State): WorkInfo = mockk {
        val identity = UUID.randomUUID()
        every { id } returns identity
        every { this@mockk.state } returns state
    }
}
