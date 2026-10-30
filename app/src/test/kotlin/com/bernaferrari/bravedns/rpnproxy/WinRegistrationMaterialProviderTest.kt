package com.bernaferrari.bravedns.rpnproxy

import com.bernaferrari.bravedns.iab.PurchaseDetail
import com.bernaferrari.bravedns.iab.QueryEntitlementResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WinRegistrationMaterialProviderTest {
    private val current = """{"sessiontoken":"verified","expiry":"2030-01-01T00:00:00Z"}""".toByteArray()

    @Test fun `restored unverified purchase is recovered through server not imported expiry`() = runTest {
        val source = mockk<WinEntitlementSource>()
        val restored = purchase(payload = "")
        val verified = purchase(payload = "server-payload")
        coEvery { source.purchase() } returns restored
        coEvery { source.deviceId("account") } returns "this-device"
        coEvery { source.localEntitlement() } returns null
        coEvery { source.query(restored, "this-device") } returns QueryEntitlementResult.Success(verified)
        coEvery { source.persist("server-payload") } returns Unit
        val provider = WinRegistrationMaterialProvider(source) { if (it == "server-payload") current else null }
        assertTrue((provider.resolve(false) as WinRegistrationMaterial).serverVerified)
        coVerify { source.query(restored, "this-device") }
    }

    @Test fun `transient verification cannot authorize restored metadata`() = runTest {
        val source = mockk<WinEntitlementSource>()
        val restored = purchase(payload = "")
        coEvery { source.purchase() } returns restored
        coEvery { source.deviceId("account") } returns "this-device"
        coEvery { source.localEntitlement() } returns null
        coEvery { source.query(restored, "this-device") } returns QueryEntitlementResult.Transient(restored)
        assertNull(WinRegistrationMaterialProvider(source) { null }.resolve(false))
        coVerify(exactly = 0) { source.persist(any()) }
    }

    private fun purchase(payload: String): PurchaseDetail = mockk<PurchaseDetail>().also {
        every { it.accountId } returns "account"
        every { it.purchaseToken } returns "recoverable-token"
        every { it.payload } returns payload
    }
}
