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

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PurchaseHistoryParserTest {

    @Test
    fun `normal subscription history maps line items and times`() {
        val start = "2026-01-15T10:00:00Z"
        val expiry = "2026-02-15T10:00:00Z"
        val mtime = "2026-01-16T12:00:00.000Z"
        val body = envelope(
            txRow(
                token = "tok-sub-1",
                cid = "cid-1",
                mtime = mtime,
                meta = subscriptionMeta(
                    latestOrderId = "GPA.123",
                    startTime = start,
                    subscriptionState = ServerOrderEntry.STATE_ACTIVE,
                    testPurchase = false,
                    productId = "standard.monthly",
                    expiryTime = expiry,
                    basePlanId = "monthly",
                    autoRenew = true,
                ),
            )
        )

        val orders = PurchaseHistoryParser.parseTxBody(body)
        assertEquals(1, orders.size)
        val order = orders.single()
        assertEquals("tok-sub-1", order.purchaseToken)
        assertEquals("cid-1", order.cid)
        assertTrue(order.isSubscription)
        assertEquals(ServerOrderEntry.KIND_SUBSCRIPTION, order.kind)
        assertEquals("GPA.123", order.orderId)
        assertEquals("standard.monthly", order.productId)
        assertEquals("standard.monthly", order.sku)
        assertEquals("monthly", order.planId)
        assertEquals(ServerOrderEntry.STATE_ACTIVE, order.subscriptionState)
        assertTrue(order.autoRenewEnabled)
        assertFalse(order.isTestPurchase)
        assertEquals(Instant.parse(start).toEpochMilli(), order.startTimeMs)
        assertEquals(Instant.parse(expiry).toEpochMilli(), order.expiryTimeMs)
        assertEquals(Instant.parse(mtime).toEpochMilli(), order.mtime)
        assertNull(order.purchaseState)
    }

    @Test
    fun `empty tx array yields no orders`() {
        val body = buildJsonObject {
            put("success", true)
            putJsonArray("tx") { }
        }
        assertTrue(PurchaseHistoryParser.parseTxBody(body).isEmpty())
    }

    @Test
    fun `success false yields no orders`() {
        val body = buildJsonObject {
            put("success", false)
            putJsonArray("tx") {
                add(txRow("tok", "cid", "2026-01-01T00:00:00Z", subscriptionMeta()))
            }
        }
        assertTrue(PurchaseHistoryParser.parseTxBody(body).isEmpty())
    }

    @Test
    fun `missing tx key yields no orders`() {
        val body = buildJsonObject { put("success", true) }
        assertTrue(PurchaseHistoryParser.parseTxBody(body).isEmpty())
    }

    @Test
    fun `malformed entries are skipped and valid ones kept`() {
        val body = envelope(
            buildJsonObject { put("not", "a-row") },
            txRow(token = "", cid = "cid", mtime = "2026-01-01T00:00:00Z", meta = subscriptionMeta()),
            txRow(
                token = "tok-ok",
                cid = "cid-ok",
                mtime = "2026-01-01T00:00:00Z",
                meta = subscriptionMeta(productId = "kept.sku"),
            ),
            buildJsonObject {
                put("purchasetoken", "tok-no-meta")
                put("cid", "cid")
            },
        )

        val orders = PurchaseHistoryParser.parseTxBody(body)
        assertEquals(1, orders.size)
        assertEquals("tok-ok", orders.single().purchaseToken)
        assertEquals("kept.sku", orders.single().productId)
    }

    @Test
    fun `duplicate purchase tokens are deduplicated`() {
        val body = envelope(
            txRow("tok-dup", "cid-a", "2026-01-01T00:00:00Z", subscriptionMeta(productId = "first")),
            txRow("tok-dup", "cid-b", "2026-01-02T00:00:00Z", subscriptionMeta(productId = "second")),
        )
        val orders = PurchaseHistoryParser.parseTxBody(body)
        assertEquals(1, orders.size)
        assertEquals("first", orders.single().productId)
    }

    @Test
    fun `subscription testPurchase object sets test flag`() {
        val body = envelope(
            txRow(
                token = "tok-test",
                cid = "cid",
                mtime = "2026-01-01T00:00:00Z",
                meta = subscriptionMeta(testPurchase = true),
            )
        )
        assertTrue(PurchaseHistoryParser.parseTxBody(body).single().isTestPurchase)
    }

    @Test
    fun `otp testPurchaseContext sets test flag and maps line items`() {
        val purchasedAt = "2026-03-01T08:30:00Z"
        val body = envelope(
            txRow(
                token = "tok-otp",
                cid = "cid-otp",
                mtime = "2026-03-02T00:00:00Z",
                meta = otpMeta(
                    orderId = "GPA.OTP",
                    purchaseCompletionTime = purchasedAt,
                    testPurchase = true,
                    productId = "plus.lifetime",
                    purchaseOptionId = "lifetime-offer",
                    purchaseState = "PURCHASED",
                ),
            )
        )

        val order = PurchaseHistoryParser.parseTxBody(body).single()
        assertFalse(order.isSubscription)
        assertTrue(order.isTestPurchase)
        assertEquals("GPA.OTP", order.orderId)
        assertEquals("plus.lifetime", order.productId)
        assertEquals("plus.lifetime", order.sku)
        assertEquals("lifetime-offer", order.planId)
        assertEquals(0, order.purchaseState)
        assertEquals(Instant.parse(purchasedAt).toEpochMilli(), order.purchaseTimeMs)
        assertEquals(0L, order.expiryTimeMs)
        assertFalse(order.autoRenewEnabled)
    }

    @Test
    fun `otp cancelled and pending purchase states map to ints`() {
        val cancelled = PurchaseHistoryParser.parseTxBody(
            envelope(txRow("t1", "c", "2026-01-01T00:00:00Z", otpMeta(purchaseState = "CANCELLED")))
        ).single()
        val pending = PurchaseHistoryParser.parseTxBody(
            envelope(txRow("t2", "c", "2026-01-01T00:00:00Z", otpMeta(purchaseState = "PENDING")))
        ).single()
        val unknown = PurchaseHistoryParser.parseTxBody(
            envelope(txRow("t3", "c", "2026-01-01T00:00:00Z", otpMeta(purchaseState = "OTHER")))
        ).single()
        assertEquals(1, cancelled.purchaseState)
        assertEquals(2, pending.purchaseState)
        assertNull(unknown.purchaseState)
    }

    @Test
    fun `mapRaw Success parses body`() {
        val raw = FetchOrdersRawResult.Success(
            envelope(txRow("tok", "cid", "2026-01-01T00:00:00Z", subscriptionMeta()))
        )
        val mapped = PurchaseHistoryParser.mapRaw(raw)
        assertTrue(mapped is ServerOrderHistoryRepository.Result.Success)
        assertEquals(1, (mapped as ServerOrderHistoryRepository.Result.Success).orders.size)
    }

    @Test
    fun `mapRaw Error and NoCredentials pass through`() {
        val err = PurchaseHistoryParser.mapRaw(FetchOrdersRawResult.Error("boom"))
        assertTrue(err is ServerOrderHistoryRepository.Result.Error)
        assertEquals("boom", (err as ServerOrderHistoryRepository.Result.Error).message)

        val none = PurchaseHistoryParser.mapRaw(FetchOrdersRawResult.NoCredentials)
        assertTrue(none is ServerOrderHistoryRepository.Result.NoCredentials)
    }

    private fun envelope(vararg rows: kotlinx.serialization.json.JsonElement) = buildJsonObject {
        put("success", true)
        put("tx", buildJsonArray { rows.forEach { add(it) } })
    }

    private fun txRow(
        token: String,
        cid: String,
        mtime: String,
        meta: kotlinx.serialization.json.JsonObject,
    ) = buildJsonObject {
        put("purchasetoken", token)
        put("cid", cid)
        put("mtime", mtime)
        put("meta", meta)
    }

    private fun subscriptionMeta(
        latestOrderId: String = "GPA.1",
        startTime: String = "2026-01-01T00:00:00Z",
        subscriptionState: String = ServerOrderEntry.STATE_ACTIVE,
        testPurchase: Boolean = false,
        productId: String = "standard.monthly",
        expiryTime: String = "2026-02-01T00:00:00Z",
        basePlanId: String = "monthly",
        autoRenew: Boolean = true,
    ) = buildJsonObject {
        put("kind", ServerOrderEntry.KIND_SUBSCRIPTION)
        put("latestOrderId", latestOrderId)
        put("startTime", startTime)
        put("subscriptionState", subscriptionState)
        if (testPurchase) putJsonObject("testPurchase") { } else put("testPurchase", JsonNull)
        putJsonArray("lineItems") {
            add(
                buildJsonObject {
                    put("productId", productId)
                    put("expiryTime", expiryTime)
                    putJsonObject("offerDetails") { put("basePlanId", basePlanId) }
                    putJsonObject("autoRenewingPlan") { put("autoRenewEnabled", autoRenew) }
                }
            )
        }
    }

    private fun otpMeta(
        orderId: String = "GPA.OTP",
        purchaseCompletionTime: String = "2026-03-01T00:00:00Z",
        testPurchase: Boolean = false,
        productId: String = "plus.lifetime",
        purchaseOptionId: String = "lifetime-offer",
        purchaseState: String = "PURCHASED",
    ) = buildJsonObject {
        put("kind", ServerOrderEntry.KIND_OTP)
        put("orderId", orderId)
        put("purchaseCompletionTime", purchaseCompletionTime)
        if (testPurchase) putJsonObject("testPurchaseContext") { } else put("testPurchaseContext", JsonNull)
        putJsonArray("productLineItem") {
            add(
                buildJsonObject {
                    put("productId", productId)
                    putJsonObject("productOfferDetails") { put("purchaseOptionId", purchaseOptionId) }
                }
            )
        }
        putJsonObject("purchaseStateContext") { put("purchaseState", purchaseState) }
    }
}
