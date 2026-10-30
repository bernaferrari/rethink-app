package com.bernaferrari.bravedns.net.go

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NativeProbeRunnerTest {
    @Test fun `timeout bounds wait and identical running probes are coalesced`() = runTest {
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val runner = NativeProbeRunner(timeoutMs = 50, workers = 1)
        try {
            val first = async { runner.probe("proxy:4") { calls.incrementAndGet(); started.complete(Unit); release.await(); true } }
            started.await()
            val second = async { runner.probe("proxy:4") { calls.incrementAndGet(); true } }
            runCurrent()
            advanceTimeBy(51)
            assertFalse(first.await())
            assertFalse(second.await())
            assertEquals(1, calls.get())
        } finally { release.countDown(); runner.close() }
    }
    @Test fun `closing adapter prevents late native success and rejects future work`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val runner = NativeProbeRunner(timeoutMs = 1000, workers = 1)
        val pending = async { runner.probe("proxy:6") { started.complete(Unit); release.await(); true } }
        started.await()
        runner.close()
        release.countDown()
        assertFalse(pending.await())
        assertFalse(runner.probe("new") { error("closed runner must not schedule") })
    }
    @Test fun `cancellation propagates instead of becoming failed probe`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val runner = NativeProbeRunner(timeoutMs = 1000, workers = 1)
        try {
            val pending = async { runner.probe("proxy:4") { started.complete(Unit); release.await(); true } }
            started.await()
            pending.cancel()
            pending.join()
            assertTrue(pending.isCancelled)
        } finally { release.countDown(); runner.close() }
    }
}
