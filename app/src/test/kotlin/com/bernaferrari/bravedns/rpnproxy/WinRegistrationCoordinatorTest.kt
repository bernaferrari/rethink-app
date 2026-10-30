package com.bernaferrari.bravedns.rpnproxy

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WinRegistrationCoordinatorTest {
    @Test fun `current tunnel does not fetch or replace entitlement`() = runTest {
        val backend = mockk<WinRegistrationBackend>()
        coEvery { backend.isRegistered() } returns true
        coEvery { backend.isStale() } returns false
        assertTrue(WinRegistrationCoordinator(backend).register())
        coVerify(exactly = 0) { backend.material(any()) }
        coVerify(exactly = 0) { backend.unregister() }
    }

    @Test fun `unverified refresh leaves stale registration intact`() = runTest {
        val backend = mockk<WinRegistrationBackend>()
        coEvery { backend.isRegistered() } returns true
        coEvery { backend.isStale() } returns true
        coEvery { backend.material(true) } returns WinRegistrationMaterial(byteArrayOf(1), "device", false)
        assertFalse(WinRegistrationCoordinator(backend).register())
        coVerify(exactly = 0) { backend.unregister() }
    }

    @Test fun `verified refresh cannot be overwritten by saved state`() = runTest {
        val backend = mockk<WinRegistrationBackend>()
        val material = WinRegistrationMaterial(byteArrayOf(2), "device", true)
        coEvery { backend.isRegistered() } returns true
        coEvery { backend.isStale() } returnsMany listOf(true, false)
        coEvery { backend.material(true) } returns material
        coEvery { backend.unregister() } returns true
        coEvery { backend.register(material, null) } returns byteArrayOf(3)
        coEvery { backend.persistState(any()) } returns true
        assertTrue(WinRegistrationCoordinator(backend).register())
        coVerify(exactly = 0) { backend.savedState() }
        coVerify { backend.register(material, null) }
    }

    @Test fun `failed refreshed registration never reports success`() = runTest {
        val backend = mockk<WinRegistrationBackend>()
        coEvery { backend.isRegistered() } returns true
        coEvery { backend.isStale() } returns true
        coEvery { backend.material(true) } returns WinRegistrationMaterial(byteArrayOf(2), "device", true)
        coEvery { backend.unregister() } returns true
        coEvery { backend.register(any(), null) } returns null
        assertFalse(WinRegistrationCoordinator(backend).register())
        coVerify(exactly = 0) { backend.persistState(any()) }
    }
    @Test fun `expiry is published outside registration lock`() = runTest {
        val backend = mockk<WinRegistrationBackend>()
        val mutex = kotlinx.coroutines.sync.Mutex()
        coEvery { backend.isRegistered() } returns false
        coEvery { backend.material(false) } returns WinRegistrationExpired("old")
        coEvery { backend.expired("old") } coAnswers {
            assertTrue(mutex.tryLock())
            mutex.unlock()
        }
        assertFalse(WinRegistrationCoordinator(backend, mutex).register())
        coVerify { backend.expired("old") }
    }
    @Test fun `recovery invalidated during material fetch cannot register`() = runTest {
        val backend = mockk<WinRegistrationBackend>(relaxed = true)
        var current = true
        coEvery { backend.isRegistered() } returns false
        coEvery { backend.material(false) } coAnswers {
            current = false
            WinRegistrationMaterial(byteArrayOf(1), "device", true)
        }
        assertFalse(WinRegistrationCoordinator(backend).register(isCurrent = { current }))
        coVerify(exactly = 0) { backend.register(any(), any()) }
    }
    @Test fun `recovery invalidated by native completion discards its registration`() = runTest {
        val backend = mockk<WinRegistrationBackend>(relaxed = true)
        var current = true
        coEvery { backend.isRegistered() } returns false
        coEvery { backend.material(false) } returns WinRegistrationMaterial(byteArrayOf(1), "device", true)
        coEvery { backend.register(any(), any()) } coAnswers { current = false; byteArrayOf(2) }
        assertFalse(WinRegistrationCoordinator(backend).register(isCurrent = { current }))
        coVerify(exactly = 1) { backend.unregister() }
        coVerify(exactly = 0) { backend.persistState(any()) }
    }
}
