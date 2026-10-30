package com.bernaferrari.bravedns.backup

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One process-wide owner for live database/preferences/WireGuard snapshot and replacement. */
internal object BackupRestoreFileGate {
    private val mutex = Mutex()

    suspend fun <T> withSourceFiles(action: suspend () -> T): T = mutex.withLock { action() }

    /** WorkManager can publish cancellation before a non-cancellable rollback finishes. */
    suspend fun awaitIdle() { mutex.withLock { } }
}
