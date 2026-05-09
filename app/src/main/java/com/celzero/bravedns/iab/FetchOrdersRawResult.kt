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
package com.celzero.bravedns.iab

import com.google.gson.JsonObject

/**
 * Typed result for [BillingBackendClient.fetchPurchaseHistory].
 *
 * Carries the raw [JsonObject] response body on success so that callers
 * (e.g. [ServerOrderHistoryRepository]) can perform their own parsing and
 * domain-model mapping without any HTTP/network concerns leaking out.
 *
 * Shared across all flavors (fdroid stub + play/website real implementations).
 */
sealed class FetchOrdersRawResult {

    /**
     * HTTP 2xx: the `/g/tx` endpoint responded successfully.
     * [body] is the raw Gson [JsonObject] of the full response; callers are
     * responsible for interpreting the `tx` array and mapping it to domain objects.
     */
    data class Success(val body: JsonObject) : FetchOrdersRawResult()

    /**
     * HTTP error or network / parse exception.
     * [message] is a human-readable description suitable for logging or display.
     */
    data class Error(val message: String) : FetchOrdersRawResult()

    /**
     * The caller's credentials (accountId / purchaseToken) were not available,
     * so no network request was made.
     */
    object NoCredentials : FetchOrdersRawResult()
}

