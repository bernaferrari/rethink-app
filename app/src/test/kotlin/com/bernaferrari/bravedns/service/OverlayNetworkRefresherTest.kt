package com.bernaferrari.bravedns.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OverlayNetworkRefresherTest {
    @Test fun `burst during snapshot coalesces into one latest rerun`() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val refresher = OverlayNetworkRefresher(backgroundScope) {
            calls++
            if (calls == 1) release.await()
        }
        refresher.request()
        runCurrent()
        repeat(100) { refresher.request() }
        runCurrent()
        assertEquals(1, calls)
        release.complete(Unit)
        runCurrent()
        assertEquals(2, calls)
        refresher.close()
        refresher.request()
        runCurrent()
        assertEquals(2, calls)
    }
    @Test fun `native failure is logged and does not kill future refreshes`() = runTest {
        var calls = 0
        var reported = 0
        val refresher = OverlayNetworkRefresher(backgroundScope, { reported++ }) {
            calls++
            if (calls == 1) throw IllegalStateException("native unavailable")
        }
        refresher.request()
        runCurrent()
        refresher.request()
        runCurrent()
        assertEquals(2, calls)
        assertEquals(1, reported)
        refresher.close()
    }
    @Test fun `close cancels in-flight work and drops queued rerun`() = runTest {
        val blocked = CompletableDeferred<Unit>()
        var calls = 0
        val refresher = OverlayNetworkRefresher(backgroundScope) { calls++; blocked.await() }
        refresher.request()
        runCurrent()
        refresher.request()
        refresher.close()
        blocked.complete(Unit)
        runCurrent()
        assertEquals(1, calls)
    }
}
