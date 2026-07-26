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
package com.bernaferrari.bravedns.network

import com.bernaferrari.bravedns.iab.stripe.CustomerCreateParams
import com.bernaferrari.bravedns.iab.stripe.PaymentIntentResponse
import com.bernaferrari.bravedns.iab.stripe.PricesResponse
import com.bernaferrari.bravedns.util.JsonHelper
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

object StripeApi {
    private const val BASE_URL = "https://api.stripe.com/"

    suspend fun getPrices(authorization: String, limit: Int = 10): PricesResponse? {
        val client = HttpClientManager.stripeClient()
        val response =
            client.get("${BASE_URL}v1/prices") {
                header(HttpHeaders.Authorization, authorization)
                parameter("limit", limit)
            }
        return if (response.status.isSuccess()) {
            JsonHelper.json.decodeFromString(response.bodyAsText())
        } else {
            null
        }
    }

    suspend fun createPaymentIntent(
        authorization: String,
        amount: Long,
        currency: String,
    ): PaymentIntentResponse? {
        val client = HttpClientManager.stripeClient()
        val response =
            client.post("${BASE_URL}v1/payment_intents") {
                header(HttpHeaders.Authorization, authorization)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("amount", amount.toString())
                            append("currency", currency)
                        },
                    ),
                )
            }
        return if (response.status.isSuccess()) {
            JsonHelper.json.decodeFromString(response.bodyAsText())
        } else {
            null
        }
    }

    suspend fun createCustomer(
        authorization: String,
        params: CustomerCreateParams,
    ): CustomerResponse? {
        val client = HttpClientManager.stripeClient()
        val formParams =
            Parameters.build {
                append("email", params.email)
                append("name", params.name)
                params.description?.let { append("description", it) }
                params.city?.let { append("address[city]", it) }
                params.country?.let { append("address[country]", it) }
            }
        val response =
            client.post("${BASE_URL}v1/customers") {
                header(HttpHeaders.Authorization, authorization)
                setBody(FormDataContent(formParams))
            }
        return if (response.status.isSuccess()) {
            JsonHelper.json.decodeFromString(response.bodyAsText())
        } else {
            null
        }
    }

    @Serializable
    data class CustomerResponse(
        val id: String,
        val email: String,
        val name: String,
        val description: String?,
        val created: Long,
        val address: Address?,
    )

    @Serializable
    data class Address(
        val city: String?,
        val country: String?,
    )
}