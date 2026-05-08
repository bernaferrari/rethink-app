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

/**
 * Stub: purchase / order history is not available on the F-Droid build.
 *
 * The class is registered in Koin via [DatabaseModule] so that injection points
 * in `full` source-set code ([ServerOrderHistoryViewModel]) resolve without
 * errors at compile time. [fetchOrders] always returns [Result.NoCredentials].
 */
class ServerOrderHistoryRepository(
    @Suppress("UNUSED_PARAMETER") private val billingBackendClient: BillingBackendClient,
) {
    sealed class Result {
        data class Success(val orders: List<ServerOrderEntry>) : Result()
        data class Error(val message: String) : Result()
        object NoCredentials : Result()
    }

    suspend fun fetchOrders(): Result = Result.NoCredentials
}

