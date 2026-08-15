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
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the exact JSON bodies and query strings the billing backend sends.
 * These strings are the wire contract — Ktor must emit them byte-for-byte.
 */
class BillingWireFormatTest {

    private val model = "Google Pixel 8"
    private val appVersion = 42
    private val cid = "cid-abc"
    private val did = "did-xyz"
    private val vcode = "42"
    private val sku = InAppBillingHandler.STD_PRODUCT_ID
    private val tokenSpecial = "abc+def ghi/x?y=&z"

    @Test
    fun `buildCustomerMeta JSON is compact with insertion order`() {
        val body = BillingApiJson.encode(
            BillingApiJson.customerMeta(model, appVersion)
        )
        assertEquals("""{"model":"Google Pixel 8","appVersion":42}""", body)
    }

    @Test
    fun `buildDeviceMeta JSON includes empty purchasedProdId`() {
        val body = BillingApiJson.encode(
            BillingApiJson.deviceMeta(model, appVersion, purchasedProdId = "")
        )
        assertEquals(
            """{"deviceName":"Google Pixel 8","appVersion":42,"purchasedProdId":""}""",
            body,
        )
    }

    @Test
    fun `buildDeviceMeta JSON includes purchasedProdId`() {
        val body = BillingApiJson.encode(
            BillingApiJson.deviceMeta(model, appVersion, purchasedProdId = "standard.tier")
        )
        assertEquals(
            """{"deviceName":"Google Pixel 8","appVersion":42,"purchasedProdId":"standard.tier"}""",
            body,
        )
    }

    @Test
    fun `register customer POST body and headers`() {
        val meta = BillingApiJson.customerMeta(model, appVersion)
        val spec = BillingRequestCodec.registerCustomer(
            accountId = null,
            deviceId = "",
            vcode = vcode,
            meta = meta,
        )
        assertEquals("POST", spec.method)
        assertEquals("/d/acc", spec.path)
        assertEquals("vcode=42", spec.encodedQuery())
        assertEquals("/d/acc?vcode=42", spec.pathAndQuery())
        assertEquals("""{"model":"Google Pixel 8","appVersion":42}""", spec.jsonBody)
        assertEquals("first-primary", spec.headers[BillingRequestCodec.HEADER_SESSION])
        assertNull(spec.headers[BillingRequestCodec.HEADER_CID])
        assertEquals("", spec.headers[BillingRequestCodec.HEADER_DID])
        assertNull(spec.headers[BillingRequestCodec.HEADER_SESSION_TEST])
    }

    @Test
    fun `register customer test path adds empty test query`() {
        val spec = BillingRequestCodec.registerCustomer(
            accountId = cid,
            deviceId = "",
            vcode = vcode,
            meta = BillingApiJson.customerMeta(model, appVersion),
            test = "",
        )
        assertEquals("vcode=42&test=", spec.encodedQuery())
        assertEquals("first-primary", spec.headers[BillingRequestCodec.HEADER_SESSION_TEST])
        assertEquals(cid, spec.headers[BillingRequestCodec.HEADER_CID])
        assertNull(spec.headers[BillingRequestCodec.HEADER_SESSION])
    }

    @Test
    fun `register device POST uses customer meta and omits blank did`() {
        val spec = BillingRequestCodec.registerDevice(
            accountId = cid,
            deviceId = null,
            vcode = vcode,
            meta = BillingApiJson.customerMeta(model, appVersion),
        )
        assertEquals("POST", spec.method)
        assertEquals("/d/reg", spec.path)
        assertEquals("vcode=42", spec.encodedQuery())
        assertEquals("""{"model":"Google Pixel 8","appVersion":42}""", spec.jsonBody)
        assertEquals(cid, spec.headers[BillingRequestCodec.HEADER_CID])
        assertNull(spec.headers[BillingRequestCodec.HEADER_DID])
    }

    @Test
    fun `register device with device meta body`() {
        val spec = BillingRequestCodec.registerDevice(
            accountId = cid,
            deviceId = did,
            vcode = vcode,
            meta = BillingApiJson.deviceMeta(model, appVersion, "standard.tier"),
        )
        assertEquals(
            """{"deviceName":"Google Pixel 8","appVersion":42,"purchasedProdId":"standard.tier"}""",
            spec.jsonBody,
        )
        assertEquals(did, spec.headers[BillingRequestCodec.HEADER_DID])
    }

    @Test
    fun `ack POST has no JSON body and double-encodes purchase token`() {
        val spec = BillingRequestCodec.acknowledge(cid, did, sku, tokenSpecial, vcode)
        assertEquals("POST", spec.method)
        assertEquals("/g/ack", spec.path)
        assertNull(spec.jsonBody)
        assertEquals(
            "sku=standard.tier&purchaseToken=abc%252Bdef+ghi%252Fx%253Fy%253D%2526z&vcode=42",
            spec.encodedQuery(),
        )
        assertEquals("first-primary", spec.headers[BillingRequestCodec.HEADER_SESSION])
        assertEquals(cid, spec.headers[BillingRequestCodec.HEADER_CID])
        assertEquals(did, spec.headers[BillingRequestCodec.HEADER_DID])
    }

    @Test
    fun `entitlement GET has no JSON body and double-encodes purchase token`() {
        val spec = BillingRequestCodec.queryEntitlement(cid, did, sku, tokenSpecial, vcode)
        assertEquals("GET", spec.method)
        assertEquals("/g/ack", spec.path)
        assertNull(spec.jsonBody)
        assertEquals(
            "sku=standard.tier&purchaseToken=abc%252Bdef+ghi%252Fx%253Fy%253D%2526z&vcode=42",
            spec.encodedQuery(),
        )
        assertEquals("first-unconstrained", spec.headers[BillingRequestCodec.HEADER_SESSION])
    }

    @Test
    fun `cancel POST encodes purchase token once`() {
        val spec = BillingRequestCodec.cancel(cid, did, sku, tokenSpecial, vcode)
        assertEquals("POST", spec.method)
        assertEquals("/g/stop", spec.path)
        assertNull(spec.jsonBody)
        assertEquals(
            "sku=standard.tier&purchaseToken=abc+def%20ghi%2Fx%3Fy%3D%26z&vcode=42",
            spec.encodedQuery(),
        )
    }

    @Test
    fun `revoke POST encodes purchase token once`() {
        val spec = BillingRequestCodec.revoke(cid, did, sku, tokenSpecial, vcode)
        assertEquals("POST", spec.method)
        assertEquals("/g/refund", spec.path)
        assertNull(spec.jsonBody)
        assertEquals(
            "sku=standard.tier&purchaseToken=abc+def%20ghi%2Fx%3Fy%3D%26z&vcode=42",
            spec.encodedQuery(),
        )
    }

    @Test
    fun `consume POST double-encodes purchase token`() {
        val spec = BillingRequestCodec.consume(
            cid, did, InAppBillingHandler.ONE_TIME_PRODUCT_ID, tokenSpecial, vcode
        )
        assertEquals("POST", spec.method)
        assertEquals("/g/con", spec.path)
        assertNull(spec.jsonBody)
        assertEquals(
            "sku=onetime.tier&purchaseToken=abc%252Bdef+ghi%252Fx%253Fy%253D%2526z&vcode=42",
            spec.encodedQuery(),
        )
    }

    @Test
    fun `history GET omits tot when null and does not pre-encode token`() {
        val spec = BillingRequestCodec.purchaseHistory(
            accountId = cid,
            deviceId = did,
            purchaseToken = tokenSpecial,
            total = null,
            vcode = vcode,
        )
        assertEquals("GET", spec.method)
        assertEquals("/g/tx", spec.path)
        assertNull(spec.jsonBody)
        assertEquals(
            "purchaseToken=abc+def%20ghi%2Fx%3Fy%3D%26z&vcode=42",
            spec.encodedQuery(),
        )
        assertEquals("first-unconstrained", spec.headers[BillingRequestCodec.HEADER_SESSION])
    }

    @Test
    fun `history GET includes tot and test query in Retrofit parameter order`() {
        val spec = BillingRequestCodec.purchaseHistory(
            accountId = cid,
            deviceId = did,
            purchaseToken = "tok",
            total = 20,
            vcode = vcode,
            test = "",
        )
        assertEquals("purchaseToken=tok&tot=20&test=&vcode=42", spec.encodedQuery())
        assertEquals("first-unconstrained", spec.headers[BillingRequestCodec.HEADER_SESSION_TEST])
    }

    @Test
    fun `plain purchase token is unchanged by either encoding path`() {
        val ack = BillingRequestCodec.acknowledge(cid, did, sku, "simpletoken", vcode)
        assertEquals("sku=standard.tier&purchaseToken=simpletoken&vcode=42", ack.encodedQuery())
        val cancel = BillingRequestCodec.cancel(cid, did, sku, "simpletoken", vcode)
        assertEquals("sku=standard.tier&purchaseToken=simpletoken&vcode=42", cancel.encodedQuery())
    }

    @Test
    fun `safe JSON parser matches former converter semantics`() {
        assertNull(BillingApiJson.parseObjectOrNull(null))
        assertNull(BillingApiJson.parseObjectOrNull(""))
        assertNull(BillingApiJson.parseObjectOrNull("   "))
        assertNull(BillingApiJson.parseObjectOrNull("<html>502</html>"))
        assertNull(BillingApiJson.parseObjectOrNull("error: nope"))
        val obj = BillingApiJson.parseObjectOrNull("""{"cid":"abc"}""")
        assertEquals("abc", obj?.get("cid")?.toString()?.trim('"'))
    }
}
