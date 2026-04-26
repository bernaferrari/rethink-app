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
 * Typed result for [BillingBackendClient.createOrRegisterCid].
 *
 * @param accountId Server-assigned or confirmed account ID; blank on error.
 * @param errorCode 0 = success; non-zero = HTTP error code (401, 409, …).
 */
data class CidResult(
    val accountId: String,
    val errorCode: Int = 0
) {
    val isSuccess: Boolean get() = errorCode == 0 && accountId.isNotBlank()

    companion object {
        val EMPTY = CidResult("", 0)
        fun error(code: Int) = CidResult("", code)
    }
}

/**
 * Typed result for [BillingBackendClient.createOrRegisterDid].
 *
 * @param deviceId Server-assigned or confirmed device ID; blank on error.
 * @param errorCode 0 = success; non-zero = HTTP error code (401, 409, …).
 */
data class DidResult(
    val deviceId: String,
    val errorCode: Int = 0
) {
    val isSuccess: Boolean get() = errorCode == 0 && deviceId.isNotBlank()

    companion object {
        val EMPTY = DidResult("", 0)
        fun error(code: Int) = DidResult("", code)
    }
}

