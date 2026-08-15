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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RpnPurchaseAckServerResponseTest {

    @Test
    fun `entitlement 200 success maps payload fields`() {
        val body = """
            {
              "success": true,
              "developerPayload": "sess-token-1",
              "expiry": "2026-02-15T10:00:00Z",
              "windowDays": 7,
              "status": "active",
              "cid": "cid-1",
              "sku": "standard.tier"
            }
        """.trimIndent()
        val result = RpnPurchaseAckServerResponse.from(body, 200)
        assertTrue(result is RpnPurchaseAckServerResponse.Ok)
        val ok = (result as RpnPurchaseAckServerResponse.Ok).payload
        assertEquals("sess-token-1", ok.developerPayload)
        assertEquals("2026-02-15T10:00:00Z", ok.expiry)
        assertEquals(7, ok.windowDays)
        assertTrue(ok.hasEntitlement)
        assertTrue(ok.isActive)
        assertEquals("cid-1", ok.cid)
        assertEquals("standard.tier", ok.sku)
    }

    @Test
    fun `entitlement 200 with success false is Err`() {
        val body = """{"success":false,"error":"not entitled","state":"SUBSCRIPTION_STATE_EXPIRED"}"""
        val result = RpnPurchaseAckServerResponse.from(body, 200)
        assertTrue(result is RpnPurchaseAckServerResponse.Err)
        val err = (result as RpnPurchaseAckServerResponse.Err).payload
        assertEquals("not entitled", err.error)
        assertTrue(err.isSubscriptionExpired)
        assertEquals(200, err.httpCode)
    }

    @Test
    fun `entitlement 409 with linkedPurchaseId is Err`() {
        val body = """{"error":"conflict","linkedPurchaseId":"old-tok"}"""
        val result = RpnPurchaseAckServerResponse.from(body, 409)
        assertTrue(result is RpnPurchaseAckServerResponse.Err)
        val err = (result as RpnPurchaseAckServerResponse.Err).payload
        assertEquals("conflict", err.error)
        assertEquals("old-tok", err.linkedPurchaseId)
        assertEquals(409, err.httpCode)
        assertFalse(err.isSubscriptionExpired)
    }

    @Test
    fun `non-2xx with empty body is Err with http code`() {
        val result = RpnPurchaseAckServerResponse.from(null, 500)
        assertTrue(result is RpnPurchaseAckServerResponse.Err)
        assertEquals(500, (result as RpnPurchaseAckServerResponse.Err).payload.httpCode)
        assertEquals("", result.payload.error)
    }

    @Test
    fun `invalid JSON on 200 is treated as empty Ok`() {
        val result = RpnPurchaseAckServerResponse.from("not-json", 200)
        assertTrue(result is RpnPurchaseAckServerResponse.Ok)
        assertNull((result as RpnPurchaseAckServerResponse.Ok).payload.developerPayload)
        assertFalse(result.payload.hasEntitlement)
    }
}
