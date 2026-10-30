package com.bernaferrari.bravedns.iab

import com.bernaferrari.bravedns.service.PersistentState
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class, sdk = [28])
class BillingBackendIdentityTest {
    @Test fun `missing device identity refreshes without reacquiring mutex`() = runTest {
        val store = mockk<SecureIdentityStore>(relaxed = true)
        val preferences = mockk<PersistentState>(relaxed = true)
        coEvery { store.get(any()) } returns (null to null)
        coEvery { store.hasLegacyIdentity() } returns false
        val client = spyk(BillingBackendClient(store, preferences, kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)))
        coEvery { client.createOrRegisterCid(any()) } returns CidResult("account")
        coEvery { client.createOrRegisterDid(any(), any()) } returns DidResult("device")
        assertEquals("device", withTimeout(5000) { client.getDeviceId() })
        coVerify { store.save(SecureIdentityStore.Env.PROD, "account", "device") }
    }

    @Test fun `failed account reconciliation never returns previous accounts device`() = runTest {
        val store = mockk<SecureIdentityStore>(relaxed = true)
        coEvery { store.get(any()) } returns ("old-account" to "old-device")
        val client = spyk(BillingBackendClient(store, mockk(relaxed = true), kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)))
        coEvery { client.createOrRegisterDid("new-account", "") } returns DidResult.error(409)
        assertEquals("", client.getDeviceId("new-account"))
        coVerify(exactly = 0) { store.save(any(), any(), any()) }
        coVerify(exactly = 0) { client.createOrRegisterDid(any(), any()) }
    }
}
