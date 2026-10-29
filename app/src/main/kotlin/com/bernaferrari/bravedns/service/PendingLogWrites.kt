package com.bernaferrari.bravedns.service

import Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Accepted log transformations must finish before their batchers can be sealed and flushed. */
internal class PendingLogWrites(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val onFailure: (Throwable) -> Unit = { error ->
        Logger.e(Logger.LOG_TAG_VPN, "Log transformation failed", error as? Exception)
    },
) {
    private val lock = Any()
    private val pending = mutableSetOf<Job>()
    private val lastByDispatcher = mutableMapOf<CoroutineDispatcher, Job>()
    private var closed = false
    private val errors = CoroutineExceptionHandler { _, error -> onFailure(error) }

    fun submit(name: String, dispatcher: CoroutineDispatcher, write: suspend () -> Unit) = synchronized(lock) {
        if (closed) return@synchronized
        val previous = lastByDispatcher[dispatcher]
        val job = scope.launch(CoroutineName(name) + dispatcher + errors, start = CoroutineStart.LAZY) {
            previous?.join()
            write()
        }
        lastByDispatcher[dispatcher] = job
        pending.add(job)
        job.invokeOnCompletion {
            synchronized(lock) {
                pending.remove(job)
                if (lastByDispatcher[dispatcher] === job) lastByDispatcher.remove(dispatcher)
            }
        }
        job.start()
        Unit
    }

    suspend fun close() = withContext(NonCancellable) {
        val accepted = synchronized(lock) {
            closed = true
            pending.toList()
        }
        try {
            accepted.joinAll()
        } finally {
            scope.cancel()
        }
    }
}
