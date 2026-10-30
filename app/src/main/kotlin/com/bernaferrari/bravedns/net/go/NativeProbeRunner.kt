package com.bernaferrari.bravedns.net.go

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns a bounded set of JNI probes. A coroutine cannot interrupt a native call;
 * timed-out work stays owned and coalesced until Go returns, never an unbounded IO job.
 * Adapter close cancels queued work and prevents late native results being published.
 */
internal class NativeProbeRunner(
    private val timeoutMs: Long = 5_000,
    workers: Int = 4,
    private val reportError: (Exception) -> Unit = {},
) : AutoCloseable {
    private data class Probe(val result: CompletableDeferred<Boolean>, var task: Future<*>? = null)
    private val probes = mutableMapOf<String, Probe>()
    private var closed = false
    private val executor = ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(16), { task -> Thread(task, "vpn-native-probe").apply { isDaemon = true } })

    suspend fun probe(key: String, block: () -> Boolean): Boolean {
        currentCoroutineContext().ensureActive()
        val probe = synchronized(probes) {
            if (closed) return false
            probes[key] ?: createProbe(key, block)
        }
        val result = withTimeoutOrNull(timeoutMs) { probe.result.await() } ?: false
        currentCoroutineContext().ensureActive()
        return synchronized(probes) { !closed && result }
    }

    private fun createProbe(key: String, block: () -> Boolean): Probe {
        val probe = Probe(CompletableDeferred())
        probes[key] = probe
        try {
            probe.task = executor.submit {
                val answer = try { block() } catch (error: Exception) {
                    reportError(error)
                    false
                }
                synchronized(probes) {
                    if (!closed) probe.result.complete(answer)
                    probes.remove(key, probe)
                }
            }
        } catch (_: RejectedExecutionException) {
            probes.remove(key)
            probe.result.complete(false)
        }
        return probe
    }

    override fun close() {
        synchronized(probes) {
            closed = true
            probes.values.forEach { it.result.complete(false); it.task?.cancel(true) }
            probes.clear()
            executor.shutdownNow()
        }
    }
}
