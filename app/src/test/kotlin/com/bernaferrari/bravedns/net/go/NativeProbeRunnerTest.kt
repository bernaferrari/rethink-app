package com.bernaferrari.bravedns.net.go

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
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
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val runner = NativeProbeRunner(timeoutMs = 50, workers = 1)
        try {
            val first = async(start = CoroutineStart.UNDISPATCHED) { runner.probe("proxy:4") { calls.incrementAndGet(); started.countDown(); release.await(); true } }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val second = async { runner.probe("proxy:4") { calls.incrementAndGet(); true } }
            runCurrent()
            advanceTimeBy(51)
            assertFalse(first.await())
            assertFalse(second.await())
            assertEquals(1, calls.get())
        } finally { release.countDown(); runner.close() }
    }
    @Test fun `closing adapter prevents late native success and rejects future work`() = runTest {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val runner = NativeProbeRunner(timeoutMs = 1000, workers = 1)
        val pending = async(start = CoroutineStart.UNDISPATCHED) { runner.probe("proxy:6") { started.countDown(); release.await(); true } }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        runner.close()
        release.countDown()
        assertFalse(pending.await())
        assertFalse(runner.probe("new") { error("closed runner must not schedule") })
    }
    @Test fun `cancellation propagates instead of becoming failed probe`() = runTest {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val runner = NativeProbeRunner(timeoutMs = 1000, workers = 1)
        try {
            val pending = async(start = CoroutineStart.UNDISPATCHED) { runner.probe("proxy:4") { started.countDown(); release.await(); true } }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            pending.cancel()
            pending.join()
            assertTrue(pending.isCancelled)
        } finally { release.countDown(); runner.close() }
    }
}
