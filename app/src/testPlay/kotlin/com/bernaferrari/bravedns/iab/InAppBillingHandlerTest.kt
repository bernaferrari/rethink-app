/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bernaferrari.bravedns.iab

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.Purchase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Focused contract tests for behavior owned by the Play billing handler.
 *
 * End-to-end purchase processing is covered by the state-machine, processor, and repository
 * suites. Keeping this class at the handler boundary avoids duplicating their implementation
 * details and makes flavor-specific failures actionable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InAppBillingHandlerTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val backend: BillingBackendClient = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runCatching { stopKoin() }
        startKoin {
            modules(module { single { backend } })
        }
        InAppBillingHandler.serverApiErrorLiveData.value = null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
        unmockkAll()
    }

    @Test
    fun `known subscription product resolves to subscriptions`() {
        assertEquals(
            ProductType.SUBS,
            InAppBillingHandler.getProductType(purchase(InAppBillingHandler.STD_PRODUCT_ID))
        )
    }

    @Test
    fun `all known one-time products resolve to in-app`() {
        val ids = listOf(
            InAppBillingHandler.ONE_TIME_PRODUCT_ID,
            InAppBillingHandler.ONE_TIME_TEST_PRODUCT_ID,
            InAppBillingHandler.ONE_TIME_PRODUCT_2YRS,
            InAppBillingHandler.ONE_TIME_PRODUCT_5YRS
        )

        ids.forEach { id ->
            assertEquals(id, ProductType.INAPP, InAppBillingHandler.getProductType(purchase(id)))
        }
    }

    @Test
    fun `unknown product conservatively resolves to subscriptions`() {
        assertEquals(
            ProductType.SUBS,
            InAppBillingHandler.getProductType(purchase("future.product"))
        )
    }

    @Test
    fun `empty product list conservatively resolves to subscriptions`() {
        assertEquals(ProductType.SUBS, InAppBillingHandler.getProductType(purchase(null)))
    }

    @Test
    fun `query source product type takes precedence over product id fallback`() {
        assertEquals(
            ProductType.INAPP,
            InAppBillingHandler.getProductType(
                purchase(InAppBillingHandler.STD_PRODUCT_ID),
                ProductType.INAPP
            )
        )
    }

    @Test
    fun `server operations retain their backend endpoint contracts`() {
        assertEquals("/acc", ServerApiError.Operation.CUSTOMER.endpoint)
        assertEquals("/g/stop", ServerApiError.Operation.CANCEL.endpoint)
        assertEquals("/g/refund", ServerApiError.Operation.REVOKE.endpoint)
        assertEquals("/g/ack", ServerApiError.Operation.ACKNOWLEDGE.endpoint)
        assertEquals("/g/con", ServerApiError.Operation.CONSUME.endpoint)
        assertEquals("/g/reg", ServerApiError.Operation.DEVICE.endpoint)
    }

    @Test
    fun `only actionable conflicts offer refund recovery`() {
        assertFalse(ServerApiError.Operation.CUSTOMER.canRefund)
        assertTrue(ServerApiError.Operation.CANCEL.canRefund)
        assertTrue(ServerApiError.Operation.REVOKE.canRefund)
        assertTrue(ServerApiError.Operation.ACKNOWLEDGE.canRefund)
        assertTrue(ServerApiError.Operation.CONSUME.canRefund)
        assertTrue(ServerApiError.Operation.DEVICE.canRefund)
    }

    @Test
    fun `revoke unauthorized publishes a redacted authorization error`() = runTest {
        coEvery { backend.revokePurchase(any(), any(), any(), any()) } returns
            Pair(false, "Unauthorized: invalid device")

        val result = InAppBillingHandler.revokeSubscription(
            accountId = "account-1",
            deviceId = "device-secret",
            purchaseToken = "token-1",
            sku = InAppBillingHandler.STD_PRODUCT_ID
        )

        assertFalse(result.first)
        val error = InAppBillingHandler.serverApiErrorLiveData.value
        assertTrue(error is ServerApiError.Unauthorized401)
        error as ServerApiError.Unauthorized401
        assertEquals(ServerApiError.Operation.REVOKE, error.operation)
        assertEquals("account-1", error.accountId)
        assertEquals("device", error.deviceIdPrefix)
    }

    private fun purchase(productId: String?): Purchase = mockk(relaxed = true) {
        every { products } returns productId?.let(::listOf).orEmpty()
    }
}
