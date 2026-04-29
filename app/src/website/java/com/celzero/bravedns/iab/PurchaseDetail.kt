package com.celzero.bravedns.iab


data class PurchaseDetail(
    val productId: String,
    var planId: String,
    var productTitle: String,
    var state: Int,
    var planTitle: String,
    val purchaseToken: String,
    val productType: String,
    val purchaseTime: String,
    val purchaseTimeMillis: Long,
    val isAutoRenewing: Boolean,
    val accountId: String,
    /**
     * Holds ONLY a sentinel indicator ([com.celzero.bravedns.database.SubscriptionStatus.DEVICE_ID_INDICATOR])
     * when a device ID has been persisted to the encrypted identity store, or an empty string when none
     * has been stored yet.  The real device ID is NEVER stored here.
     *
     * To obtain the actual device ID, use [com.celzero.bravedns.iab.InAppBillingHandler.getObfuscatedDeviceId]
     * or [com.celzero.bravedns.iab.BillingBackendClient.getDeviceId], both of which read from
     * [com.celzero.bravedns.iab.SecureIdentityStore].
     */
    val deviceId: String = "",
    val payload: String,
    val expiryTime: Long,
    val status: Int,
    val windowDays: Int,
    val orderId: String = ""  // Google Play order ID for refund/chargeback correlation
)
