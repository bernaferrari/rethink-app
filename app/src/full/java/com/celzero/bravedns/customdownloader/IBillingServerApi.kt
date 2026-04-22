/*
 * Copyright 2023 RethinkDNS and its authors
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
package com.celzero.bravedns.customdownloader

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface IBillingServerApi {

    /*
     * Get the public key for the app version.
     * response: {"minvcode":"30","pubkey":"...","status":"ok"}
    */
    @GET("/p/{appVersion}")
    suspend fun getPublicKey(@Path("appVersion") appVersion: String): Response<JsonObject?>?

    /*
      * Register or resolve a customer (account + device) with the server.
      * URL shape: /d/acc?cid=xxx&did=xxx
      *
      * accountId and deviceId are passed as query parameters (empty strings for new users).
      * The meta JSON object is sent as the request body.
      *
      * There is existingCid and existingDid in the query parameters, but it is not used
      * by the client now
      *
      * Request:
      *   Query: cid=<accountId>, did=<deviceId>  (empty strings for first-time registration)
      *   Body (JSON): well-formed JSON is enough to register a new customer
      *   {
      *     "deviceId": "device identifier of some sort",
      *     "appVersion": <int>
      *   }
      *
      * Response (JSON):
      * {
      *   "accountId": "<server-assigned or confirmed account id>",
      *   "deviceId":  "<server-assigned or confirmed device id>"
      * }
      */
    @POST("/d/acc")
    suspend fun registerCustomer(
        @Query("cid") accountId: String,
        @Query("did") deviceId: String,
        @Body meta: JsonObject
    ): Response<JsonObject?>?

    /*
      * Register a device with the given account ID.
      * URL shape: /g/device?cid=xxx&did=xxx[&test]
      * `test` is a bare string param omitted for production entitlements.
      * The `meta` body carries appVersion, productId, planId, orderId.
     */
    @POST("/d/reg")
    suspend fun registerDevice(
        @Query("cid") accountId: String,
        @Query("did") deviceId: String,
        @Body meta: JsonObject? = null
    ): Response<JsonObject?>?


    /*
      * Cancel the subscription for the given account ID.
      * URL shape: /g/stop?cid=xxx&sku=xxx&purchaseToken=xxx[&test]
      * `test` is a bare string param appended only for test entitlements —
      * e.g. https://nile.workers.com/g/stop?cid=xxx&test
      * Omit (pass null) for production entitlements.
      * response: {"message":"canceled subscription","purchaseId":"..."}
     */
    @POST("/g/stop")
    suspend fun cancelPurchase(
        @Query("cid") accountId: String,
        @Query("did") deviceId: String,
        @Query("sku") sku: String,
        @Query("purchaseToken") purchaseToken: String
    ): Response<JsonObject?>?

    /*
      * Refund / revoke the subscription for the given account ID.
      * URL shape: /g/refund?cid=xxx&sku=xxx&purchaseToken=xxx[&test]
      * `test` is a bare string param omitted for production entitlements.
      * response: {"message":"canceled subscription","purchaseId":"..."}
     */
    @POST("/g/refund")
    suspend fun revokeSubscription(
        @Query("cid") accountId: String,
        @Query("did") deviceId: String,
        @Query("sku") sku: String,
        @Query("purchaseToken") purchaseToken: String
    ): Response<JsonObject?>?

    /*
      * Acknowledge a purchase.
      * URL shape: /g/ack?cid=xxx&sku=xxx&purchaseToken=xxx[&force][&test]
      * `test` is a bare string param omitted for production entitlements.
     */
    @POST("/g/ack")
    suspend fun acknowledgePurchase(
        @Query("cid") accountId: String,
        @Query("did") deviceId: String,
        @Query("sku") sku: String,
        @Query("purchaseToken") purchaseToken: String,
    ): Response<JsonObject?>?


    /*
      * Consume an expired one-time (INAPP) purchase server-side.
      * URL shape: /g/con?cid=xxx&sku=xxx&purchaseToken=xxx[&test]
      * `test` is a bare string param omitted for production entitlements.
      * Called only when: purchase exists locally + productType == INAPP + server entitlement == expired.
      * response: {"message":"consumed","purchaseId":"..."}
      *           {"error":"already consumed",...}
     */
    @POST("/g/con")
    suspend fun consumePurchase(
        @Query("cid") accountId: String,
        @Query("did") deviceId: String,
        @Query("sku") sku: String,
        @Query("purchaseToken") purchaseToken: String
    ): Response<JsonObject?>?
}
