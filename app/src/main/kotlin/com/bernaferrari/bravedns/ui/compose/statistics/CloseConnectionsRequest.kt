package com.bernaferrari.bravedns.ui.compose.statistics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/** Dispatch only: this action never starts a service or changes protection/rule preferences. */
internal class CloseConnectionsRequest(
    private val isRunning: () -> Boolean,
    private val close: suspend () -> Boolean,
) {
    enum class Result { REQUESTED, STOPPED, BUSY }
    private val mutex = Mutex()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()

    suspend fun confirm(): Result {
        if (!mutex.tryLock()) return Result.BUSY
        try {
            if (!isRunning()) return Result.STOPPED
            mutableBusy.value = true
            return if (close()) Result.REQUESTED else Result.STOPPED
        } finally {
            mutableBusy.value = false
            mutex.unlock()
        }
    }
}
