package com.bernaferrari.bravedns.rpnproxy

import com.bernaferrari.bravedns.iab.PurchaseDetail
import com.bernaferrari.bravedns.iab.QueryEntitlementResult

internal interface WinEntitlementSource {
    suspend fun purchase(): PurchaseDetail?
    suspend fun deviceId(account: String): String
    suspend fun localEntitlement(): ByteArray?
    suspend fun query(purchase: PurchaseDetail, deviceId: String): QueryEntitlementResult
    suspend fun persist(payload: String)
}

internal class WinRegistrationMaterialProvider(
    private val source: WinEntitlementSource,
    private val extractWs: (String) -> ByteArray?,
) {
    suspend fun resolve(forceServer: Boolean): WinRegistrationMaterialResult? {
        val purchase = source.purchase() ?: return null
        if (purchase.accountId.isBlank() || purchase.purchaseToken.isBlank()) return null
        val device = source.deviceId(purchase.accountId).takeIf { it.isNotBlank() } ?: return null
        val local = source.localEntitlement()?.takeIf { WinEntitlementFreshness.isCurrent(it) }
            ?: extractWs(purchase.payload)?.takeIf { WinEntitlementFreshness.isCurrent(it) }
        if (!forceServer && local != null) return WinRegistrationMaterial(local, device, false)
        return when (val response = source.query(purchase, device)) {
            is QueryEntitlementResult.Success -> {
                val updated = response.purchase
                if (updated.accountId != purchase.accountId || updated.purchaseToken != purchase.purchaseToken) return null
                val ws = extractWs(updated.payload)?.takeIf { WinEntitlementFreshness.isCurrent(it) } ?: return null
                source.persist(updated.payload)
                // Prefer verified bytes in memory even if encrypted persistence failed.
                WinRegistrationMaterial(ws, device, true)
            }
            is QueryEntitlementResult.Expired -> WinRegistrationExpired(purchase.purchaseToken)
            else -> null
        }
    }
}
