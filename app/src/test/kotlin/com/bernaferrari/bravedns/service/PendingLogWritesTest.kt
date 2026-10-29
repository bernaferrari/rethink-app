package com.bernaferrari.bravedns.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingLogWritesTest {
    @Test fun `drain waits for suspended accepted transformations and rejects new work`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val writes = PendingLogWrites(CoroutineScope(SupervisorJob() + dispatcher))
        val lookup = CompletableDeferred<Unit>()
        val rows = mutableListOf<String>()
        writes.submit("ip", dispatcher) { lookup.await(); rows.add("accepted") }
        writes.submit("summary", dispatcher) { rows.add("summary") }
        assertTrue(rows.isEmpty())
        val drained = async(dispatcher) { writes.close(); rows.add("batchers may close") }
        assertFalse(drained.isCompleted)
        writes.submit("late", dispatcher) { rows.add("must not run") }
        lookup.complete(Unit)
        drained.await()
        assertEquals(listOf("accepted", "summary", "batchers may close"), rows)
    }

    @Test fun `failed transformation is logged and does not prevent drain or later work`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val errors = mutableListOf<Throwable>()
        val writes = PendingLogWrites(CoroutineScope(SupervisorJob() + dispatcher), errors::add)
        var written = false
        writes.submit("failed", dispatcher) { error("lookup unavailable") }
        writes.submit("next", dispatcher) { written = true }
        writes.close()
        assertTrue(written)
        assertEquals("lookup unavailable", errors.single().message)
    }

}
