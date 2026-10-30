package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.util.PcapMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Native capture acknowledgement and persisted state have one ordered owner. */
internal class PcapSessionController(
    private val applyNative: suspend (String) -> Boolean,
    private val persist: (Int, String) -> Unit,
    private val latestPath: () -> String,
    private val backendIdentity: () -> Any?,
    private val isFileEmpty: (String) -> Boolean,
) {
    private val mutex = Mutex()
    private data class Applied(val backend: Any?, val path: String)
    private var applied: Applied? = null

    suspend fun start(mode: Int, path: String): Boolean = mutex.withLock {
        val target = when (PcapMode.getPcapType(mode)) {
            PcapMode.NONE -> ""
            PcapMode.LOGCAT -> "0"
            PcapMode.EXTERNAL_FILE -> path.takeIf(String::isNotBlank) ?: return@withLock false
        }
        if (!applyIfChanged(target)) return@withLock false
        persist(mode, target)
        true
    }

    private suspend fun applyIfChanged(target: String): Boolean {
        val backend = backendIdentity()
        if (applied?.let { it.backend === backend && it.path == target } == true) return true
        // Native SetPcap appends a global header on every open. An existing segment
        // cannot be resumed after restart or an uncertain native failure.
        val safeTarget = if (target.isNotEmpty() && target != "0" && !isFileEmpty(target)) "" else target
        val ownedTarget = captureOwnershipFor(target)
        // A failed or cancelled native call can already have changed the capture writer.
        applied = null
        if (!applyChecked(safeTarget, backend, ownedTarget)) return false
        applied = Applied(backend, safeTarget)
        if (safeTarget != target) persist(PcapMode.NONE.id, "")
        return safeTarget == target
    }

    private fun captureOwnershipFor(target: String): String = target.takeIf(String::isNotEmpty)
        ?: latestPath().takeIf(String::isNotEmpty) ?: applied?.path.orEmpty()

    private suspend fun applyChecked(target: String, backend: Any?, ownedTarget: String): Boolean {
        val acknowledged = try {
            applyNative(target)
        } catch (cancelled: CancellationException) {
            retainUntilStopped(ownedTarget)
            throw cancelled
        }
        if (acknowledged && backend === backendIdentity()) return true
        if (acknowledged || target.isNotEmpty()) retainUntilStopped(ownedTarget)
        return false
    }

    private suspend fun retainUntilStopped(target: String) {
        // Publish ownership before cleanup: if disabling fails or throws, the manager
        // must retain the private file and deadline and continue exposing Stop.
        if (target.isNotEmpty() && latestPath() != target) {
            val mode = if (target == "0") PcapMode.LOGCAT else PcapMode.EXTERNAL_FILE
            persist(mode.id, target)
        }
        withContext(NonCancellable) {
            val backend = backendIdentity()
            if (applyNative("") && backend === backendIdentity()) {
                applied = Applied(backend, "")
                persist(PcapMode.NONE.id, "")
            }
        }
    }

    suspend fun stop(): Boolean = start(PcapMode.NONE.id, "")

    suspend fun refresh() = mutex.withLock {
        // Read inside the same critical section: a delayed preference event cannot restart
        // a previously captured path after a successful stop acknowledgement.
        applyIfChanged(latestPath())
        Unit
    }
}
