package com.bernaferrari.bravedns.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VpnTeardownTest {
    @Test fun `destruction waits for native disconnect and log drain before cancelling service work`() = runTest {
        val disconnectGate = CompletableDeferred<Unit>()
        val drainGate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val work = Job()
        val cleanupScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val teardown = VpnTeardown(cleanupScope,
            disconnect = { events.add("disconnect"); disconnectGate.await() },
            drain = { events.add("drain"); drainGate.await() },
            cancelServiceWork = { events.add("cancel"); work.cancel() },
            stopService = { events.add("stop") },
        )
        teardown.requestStop()
        teardown.requestStop()
        teardown.onDestroyed()
        val completion = async { teardown.awaitCompletion() }
        assertFalse(completion.isCompleted)
        assertEquals(listOf("disconnect"), events)
        assertTrue(work.isActive)
        disconnectGate.complete(Unit)
        assertTrue(events.contains("drain"))
        assertTrue(work.isActive)
        assertFalse(completion.isCompleted)
        drainGate.complete(Unit)
        completion.await()
        assertEquals("cancel", events.last())
        assertFalse(work.isActive)
        assertEquals(1, events.count { it == "disconnect" })
        assertFalse(cleanupScope.coroutineContext[Job]!!.isActive)
    }

    @Test fun `direct destruction also disconnects native tunnel`() = runTest {
        val events = mutableListOf<String>()
        val cleanupScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val teardown = VpnTeardown(cleanupScope,
            disconnect = { events.add("disconnect") },
            drain = { events.add("drain") },
            cancelServiceWork = { events.add("cancel") },
            stopService = { events.add("stop") },
        )
        teardown.onDestroyed()
        assertEquals(listOf("disconnect", "drain", "cancel"), events)
    }
}
