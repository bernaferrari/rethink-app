package com.bernaferrari.bravedns.iab.stripe

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Price(
    val id: String,
    val unit_amount: Long,
    val currency: String,
    val product: String,
)

@Serializable
data class PricesResponse(
    val data: List<Price>,
)

@Serializable
data class PaymentIntentRequest(
    val amount: Long,
    val currency: String,
)

@Serializable
data class PaymentIntentResponse(
    val id: String,
    @SerialName("client_secret") val clientSecret: String,
    val amount: Long,
    val currency: String,
)

data class CustomerCreateParams(
    val email: String,
    val name: String,
    val description: String? = null,
    val city: String? = null,
    val country: String? = null,
)