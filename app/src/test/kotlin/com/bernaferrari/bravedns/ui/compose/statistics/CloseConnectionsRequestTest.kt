package com.bernaferrari.bravedns.ui.compose.statistics

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloseConnectionsRequestTest {
    @Test fun `stopped protection never sends a close request`() = runTest {
        var calls = 0
        val request = CloseConnectionsRequest({ false }) { calls++; true }
        assertEquals(CloseConnectionsRequest.Result.STOPPED, request.confirm())
        assertEquals(0, calls)
    }

    @Test fun `confirmation rechecks protection after the dialog was shown`() = runTest {
        var running = true
        var calls = 0
        val request = CloseConnectionsRequest({ running }) { calls++; true }
        running = false
        assertEquals(CloseConnectionsRequest.Result.STOPPED, request.confirm())
        assertEquals(0, calls)
    }

    @Test fun `overlapping confirmations dispatch only once and release busy after acknowledgement`() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val request = CloseConnectionsRequest({ true }) { calls++; release.await(); true }
        val first = async { request.confirm() }
        try {
            runCurrent()
            assertTrue(request.busy.value)
            assertEquals(CloseConnectionsRequest.Result.BUSY, request.confirm())
            assertEquals(1, calls)
        } finally { release.complete(Unit) }
        assertEquals(CloseConnectionsRequest.Result.REQUESTED, first.await())
        assertFalse(request.busy.value)
    }

    @Test fun `stopping between initial guard and dispatch is reported as stopped`() = runTest {
        val request = CloseConnectionsRequest({ true }) { false }
        assertEquals(CloseConnectionsRequest.Result.STOPPED, request.confirm())
        assertFalse(request.busy.value)
    }

    @Test fun `failure releases busy so a user can retry`() = runTest {
        var shouldFail = true
        val request = CloseConnectionsRequest({ true }) { if (shouldFail) throw IllegalStateException("unavailable"); true }
        try { request.confirm(); fail("Expected exception") } catch (_: IllegalStateException) { }
        assertFalse(request.busy.value)
        shouldFail = false
        assertEquals(CloseConnectionsRequest.Result.REQUESTED, request.confirm())
    }
}
