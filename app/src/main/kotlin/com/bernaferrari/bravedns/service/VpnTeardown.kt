package com.bernaferrari.bravedns.service

import Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Cleanup has its own lifetime so service-work cancellation cannot skip native disconnect. */
internal class VpnTeardown(
    private val scope: CoroutineScope,
    disconnect: suspend () -> Unit,
    private val drain: suspend () -> Unit,
    private val cancelServiceWork: () -> Unit,
    private val stopService: suspend () -> Unit,
) {
    private val completed = CompletableDeferred<Unit>()
    suspend fun awaitCompletion() = completed.await()

    private val stopping = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)
    private val disconnected = scope.async(start = CoroutineStart.LAZY) { disconnect() }
    val isStopping: Boolean get() = stopping.get()

    // Native disconnect can throw arbitrary JNI exceptions; every failure must still stop the service.
    @Suppress("TooGenericExceptionCaught")
    fun requestStop() {
        if (!stopping.compareAndSet(false, true)) return
        scope.launch {
            try {
                disconnected.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(Logger.LOG_TAG_VPN, "Native VPN shutdown failed", e)
            } finally {
                stopService()
            }
        }
    }

    // Native/driver failures must be reported to awaiters while service cancellation still completes.
    @Suppress("TooGenericExceptionCaught")
    fun onDestroyed() {
        stopping.set(true)
        if (!destroyed.compareAndSet(false, true)) return
        scope.launch {
            try {
                try {
                    disconnected.await()
                } finally {
                    drain()
                }
            } catch (e: CancellationException) {
                completed.completeExceptionally(e)
                throw e
            } catch (e: Exception) {
                Logger.e(Logger.LOG_TAG_VPN, "VPN teardown failed", e)
                completed.completeExceptionally(e)
            } finally {
                cancelServiceWork()
                completed.complete(Unit)
                scope.cancel()
            }
        }
    }
}
