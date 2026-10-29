package com.bernaferrari.bravedns.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One timer session, measured against a monotonic clock rather than accumulated wakeups. */
internal class PauseCountdown(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val onExpired: () -> Unit,
) {
    private val lock = Any()
    private var job: Job? = null
    private var generation = 0L
    private var deadline = 0L
    private val remaining = MutableStateFlow(0L)
    val state: StateFlow<Long> = remaining

    fun start(durationMs: Long) = synchronized(lock) {
        job?.cancel()
        val session = ++generation
        deadline = now() + durationMs.coerceAtLeast(0L)
        remaining.value = durationMs.coerceAtLeast(0L)
        job = scope.launch {
            while (true) {
                delay(1_000L)
                val finished = synchronized(lock) tick@{
                    if (session != generation) return@tick true
                    remaining.value = (deadline - now()).coerceAtLeast(0L)
                    if (remaining.value == 0L) {
                        onExpired()
                        true
                    } else false
                }
                if (finished) break
            }
        }
    }

    fun stop() = synchronized(lock) {
        ++generation
        job?.cancel()
        job = null
        remaining.value = 0L
    }

    fun adjust(durationMs: Long) = synchronized(lock) {
        if (job?.isActive != true) return@synchronized
        deadline += durationMs
        remaining.value = (deadline - now()).coerceAtLeast(0L)
    }
}
