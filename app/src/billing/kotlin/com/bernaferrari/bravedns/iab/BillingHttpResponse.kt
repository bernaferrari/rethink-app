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

import kotlinx.serialization.json.JsonObject

/**
 * Client-agnostic billing HTTP envelope, populated by the Ktor transport
 * ([BillingApiFactory]) so mappers stay stable across HTTP engines.
 */
internal data class BillingHttpResponse(
    val code: Int,
    val body: JsonObject?,
    val errorBody: String?,
    val message: String = "",
    val requestUrl: String = "",
) {
    val isSuccessful: Boolean
        get() = code in 200..299
}
