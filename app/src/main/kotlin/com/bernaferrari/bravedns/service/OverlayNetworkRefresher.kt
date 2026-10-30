package com.bernaferrari.bravedns.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

/** One in-flight native snapshot and one pending rerun, owned by the VPN lifecycle. */
internal class OverlayNetworkRefresher(
    scope: CoroutineScope,
    private val reportError: (Exception) -> Unit = {},
    refresh: suspend () -> Unit,
) : AutoCloseable {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val worker = scope.launch {
        requests.consumeEach { runRefresh(refresh) }
    }

    // Generated native accessors can throw Exception; one failure must not disable future refreshes.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runRefresh(refresh: suspend () -> Unit) {
        try { refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { reportError(error) }
    }

    fun request() { requests.trySend(Unit) }

    override fun close() {
        requests.close()
        worker.cancel()
    }
}
