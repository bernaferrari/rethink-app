package com.bernaferrari.bravedns.service

import Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

/** Coalesces restart requests while old native resources and queued log writes are draining. */
internal class VpnStartGate(private val scope: CoroutineScope) {
    private val lock = Any()
    private var pending: Job? = null

    // Awaiting native teardown may report any JNI failure; restarting after one is never safe.
    @Suppress("TooGenericExceptionCaught")
    fun afterStopped(awaitStopped: suspend () -> Unit, onFailure: () -> Unit = {}, start: () -> Unit) = synchronized(lock) {
        pending?.cancel()
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try {
                awaitStopped()
                synchronized(lock) {
                    coroutineContext.ensureActive()
                    start()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(lock) {
                    if (pending === coroutineContext[Job]) onFailure()
                }
                Logger.e(Logger.LOG_TAG_VPN, "Cannot restart before VPN teardown completes", e)
            }
        }
        pending = next
        next.start()
        Unit
    }

    fun cancelPending() = synchronized(lock) {
        pending?.cancel()
        pending = null
    }
}
