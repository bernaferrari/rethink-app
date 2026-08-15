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

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ServerApiErrorMapperTest {

    @Test
    fun `mapCidResponse success extracts cid`() {
        val body = buildJsonObject { put("cid", "acc-1") }
        val result = ServerApiErrorMapper.mapCidResponse(
            BillingHttpResponse(200, body, null), "prod", "createOrRegisterCid"
        )
        assertTrue(result.isSuccess)
        assertEquals("acc-1", result.accountId)
    }

    @Test
    fun `mapCidResponse 401 and 409 preserve codes`() {
        assertEquals(401, ServerApiErrorMapper.mapCidResponse(
            BillingHttpResponse(401, null, "nope"), "prod", "m"
        ).errorCode)
        assertEquals(409, ServerApiErrorMapper.mapCidResponse(
            BillingHttpResponse(409, null, "conflict"), "prod", "m"
        ).errorCode)
    }

    @Test
    fun `mapDidResponse blank body is EMPTY`() {
        val result = ServerApiErrorMapper.mapDidResponse(
            BillingHttpResponse(200, buildJsonObject { put("did", "  ") }, null),
            "test",
            "createOrRegisterDid",
        )
        assertFalse(result.isSuccess)
        assertEquals(0, result.errorCode)
    }

    @Test
    fun `mapRegisterDeviceResponse 409 is Conflict`() {
        val result = ServerApiErrorMapper.mapRegisterDeviceResponse(
            BillingHttpResponse(409, null, "exists"),
            "prod",
            "registerDevice",
            "acc",
            "dev",
        ) { }
        assertEquals(RegisterDeviceResult.Conflict, result)
    }

    @Test
    fun `mapCancelResponse and mapRevokeResponse success strings`() {
        val ok = BillingHttpResponse(200, buildJsonObject { put("ok", true) }, null)
        assertEquals(Pair(true, "Cancelled"), ServerApiErrorMapper.mapCancelResponse(ok, "prod", "c", "a", "sku"))
        assertEquals(Pair(true, "Revoked"), ServerApiErrorMapper.mapRevokeResponse(ok, "prod", "r", "a", "sku"))
        val unauthorized = BillingHttpResponse(401, null, "no")
        assertEquals(Pair(false, "Unauthorized: 401"), ServerApiErrorMapper.mapCancelResponse(unauthorized, "prod", "c", "a", "sku"))
    }
}
