package com.bernaferrari.bravedns.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VpnStartGateTest {
    @Test fun `new start waits for teardown and repeated requests coalesce`() = runTest {
        val gate = VpnStartGate(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        val stopped = CompletableDeferred<Unit>()
        var starts = 0
        gate.afterStopped({ stopped.await() }) { starts++ }
        gate.afterStopped({ stopped.await() }) { starts++ }
        assertEquals(0, starts)
        stopped.complete(Unit)
        assertEquals(1, starts)
    }

    @Test fun `stop cancels restart pending behind teardown`() = runTest {
        val gate = VpnStartGate(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        val stopped = CompletableDeferred<Unit>()
        var starts = 0
        gate.afterStopped({ stopped.await() }) { starts++ }
        gate.cancelPending()
        stopped.complete(Unit)
        assertEquals(0, starts)
    }
}
