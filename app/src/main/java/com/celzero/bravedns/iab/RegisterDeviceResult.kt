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
 * Typed result for [BillingBackendClient.registerDevice].
 *
 * Callers pattern-match on this to decide whether to surface a UI error.
 * Shared across all flavors (fdroid stub + play/website real implementations).
 */
sealed class RegisterDeviceResult {
    /** HTTP 2xx: device registered successfully. */
    object Success : RegisterDeviceResult()

    /**
     * HTTP 401: the server refused to authorize this device.
     * Carry the IDs that were used so the caller can show them in the error UI.
     */
    data class Unauthorized(val accountId: String, val deviceId: String) : RegisterDeviceResult()

    /** HTTP 409: device already registered (conflict). */
    object Conflict : RegisterDeviceResult()

    /** Any other non-2xx response or network exception. */
    data class Failure(val httpCode: Int, val message: String? = null) : RegisterDeviceResult()

    val isSuccess: Boolean get() = this is Success
}

