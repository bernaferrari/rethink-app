package com.bernaferrari.bravedns.iab

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex

/** Releases only this connection attempt, even if Play delivers a late callback. */
internal class BillingConnectionLease(private val mutex: Mutex, private val owner: Any) {
    private val released = AtomicBoolean(false)

    fun release(): Boolean {
        if (!released.compareAndSet(false, true)) return false
        mutex.unlock(owner)
        return true
    }
}
