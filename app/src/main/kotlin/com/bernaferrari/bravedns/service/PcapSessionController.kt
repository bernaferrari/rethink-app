package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.util.PcapMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Native capture acknowledgement and persisted state have one ordered owner. */
internal class PcapSessionController(
    private val applyNative: suspend (String) -> Boolean,
    private val persist: (Int, String) -> Unit,
    private val latestPath: () -> String,
) {
    private val mutex = Mutex()

    suspend fun start(mode: Int, path: String): Boolean = mutex.withLock {
        val target = when (PcapMode.getPcapType(mode)) {
            PcapMode.NONE -> ""
            PcapMode.LOGCAT -> "0"
            PcapMode.EXTERNAL_FILE -> path.takeIf(String::isNotBlank) ?: return@withLock false
        }
        if (!applyNative(target)) return@withLock false
        persist(mode, target)
        true
    }

    suspend fun stop(): Boolean = start(PcapMode.NONE.id, "")

    suspend fun refresh() = mutex.withLock {
        // Read inside the same critical section: a delayed preference event cannot restart
        // a previously captured path after a successful stop acknowledgement.
        applyNative(latestPath())
        Unit
    }
}
