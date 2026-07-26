/*
 * Stub: F-Droid flavor does not include Google Play Billing.
 * This file provides just enough of the Purchase surface to let
 * SubscriptionStateMachineV2 (main source set) compile.
 */
package com.android.billingclient.api

class Purchase(
    val purchaseToken: String = "",
    val products: List<String> = emptyList(),
    val purchaseState: Int = PurchaseState.UNSPECIFIED_STATE,
    val isAcknowledged: Boolean = false,
    val isAutoRenewing: Boolean = false,
    val accountIdentifiers: AccountIdentifiers? = null,
    val developerPayload: String = "",
    val originalJson: String = "",
    val purchaseTime: Long = 0L,
    val orderId: String? = null,
    val quantity: Int = 1,
    val signature: String = ""
) {
    object PurchaseState {
        const val UNSPECIFIED_STATE = 0
        const val PURCHASED = 1
        const val PENDING = 2
    }
}

