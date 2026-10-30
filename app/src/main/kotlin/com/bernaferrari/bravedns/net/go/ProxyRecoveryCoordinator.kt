package com.bernaferrari.bravedns.net.go

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes native mutations; stale recovery cannot win over disable, replacement, or teardown. */
internal class ProxyRecoveryCoordinator(private val reportCleanupFailure: (Exception) -> Unit = {}) {
    private val mutex = Mutex()
    private val generation = AtomicLong()
    @Volatile private var closed = false

    fun invalidate(): Long = generation.incrementAndGet()
    fun isCurrent(token: Long): Boolean = !closed && generation.get() == token

    suspend fun <T> mutate(action: suspend () -> T): T {
        invalidate()
        return mutex.withLock { action() }
    }

    suspend fun close(action: suspend () -> Unit) {
        closed = true
        mutate(action)
    }

    @Suppress("TooGenericExceptionCaught") // Native rollback may throw Exception; preserve the primary failure.
    suspend fun recover(
        token: Long,
        wanted: suspend () -> Boolean,
        exists: suspend () -> Boolean,
        install: suspend () -> Unit,
        discard: suspend () -> Unit,
    ) = mutex.withLock {
        currentCoroutineContext().ensureActive()
        if (!isCurrent(token) || !wanted() || exists()) return@withLock
        // Reading configuration/status may suspend; validate again immediately before JNI.
        if (!isCurrent(token) || !wanted()) return@withLock
        var primaryFailure: Throwable? = null
        try {
            install()
            currentCoroutineContext().ensureActive()
        } catch (primary: Throwable) {
            primaryFailure = primary
        }
        try {
            withContext(NonCancellable) {
                if (primaryFailure != null || !isCurrent(token) || !wanted()) discard()
            }
        } catch (cleanup: Exception) {
            reportRollbackFailure(cleanup)
            if (primaryFailure == null) primaryFailure = cleanup
            else if (primaryFailure !== cleanup) primaryFailure.addSuppressed(cleanup)
        }
        primaryFailure?.let { throw it }
        Unit
    }

    @Suppress("TooGenericExceptionCaught") // A failing reporter cannot replace the native failure either.
    private fun reportRollbackFailure(cleanup: Exception) {
        try { reportCleanupFailure(cleanup) } catch (reporting: Exception) {
            if (cleanup !== reporting) cleanup.addSuppressed(reporting)
        }
    }

    suspend fun applyIfCurrent(token: Long, action: suspend () -> Unit) = mutex.withLock {
        if (isCurrent(token)) action()
    }
}
