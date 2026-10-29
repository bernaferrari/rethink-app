package com.bernaferrari.bravedns.iab

import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingConnectionLeaseTest {
    @Test fun `late disconnect cannot release a subsequent billing operation`() {
        val mutex = Mutex()
        val connection = Any()
        assertTrue(mutex.tryLock(connection))
        val lease = BillingConnectionLease(mutex, connection)
        assertTrue(lease.release())
        val cancellation = Any()
        assertTrue(mutex.tryLock(cancellation))
        assertFalse(lease.release())
        assertTrue(mutex.holdsLock(cancellation))
        mutex.unlock(cancellation)
    }

    @Test fun `completion after timeout only releases once`() {
        val mutex = Mutex()
        val owner = Any()
        assertTrue(mutex.tryLock(owner))
        val lease = BillingConnectionLease(mutex, owner)
        assertTrue(lease.release())
        assertFalse(lease.release())
        assertFalse(mutex.isLocked)
    }
}
