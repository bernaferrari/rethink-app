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

import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.bernaferrari.bravedns.database.SubscriptionStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Builds [PurchaseDetail]s and resolves Play product type / expiry.
 *
 * Product-type resolution and expiry math stay here so [InAppBillingHandler.getProductType]
 * can keep its public signature while the handler stays a façade.
 */
internal class PurchaseDetailFactory(
    private val storeProductDetails: CopyOnWriteArrayList<QueryProductDetail>,
    private val queryUtils: () -> QueryUtils,
    private val getObfuscatedDeviceId: suspend () -> String,
    private val resolveOfferDetails: (Purchase) -> ProductDetails.SubscriptionOfferDetails?,
    private val buildFromSubsProcessor: suspend (Purchase) -> PurchaseDetail?,
    private val buildFromOneTimeProcessor: suspend (Purchase) -> PurchaseDetail?
) {

    private fun logd(methodName: String, msg: String) = BillingHandlerLog.d(methodName, msg)
    private fun logv(methodName: String, msg: String) = BillingHandlerLog.v(methodName, msg)
    private fun loge(methodName: String, msg: String, e: Exception? = null) =
        BillingHandlerLog.e(methodName, msg, e)
    private fun log(methodName: String, msg: String) = BillingHandlerLog.i(methodName, msg)

    suspend fun createPurchaseDetailFromPurchase(purchase: Purchase): PurchaseDetail? {
        return try {
            when (getProductType(purchase)) {
                ProductType.SUBS -> buildFromSubsProcessor(purchase)
                    ?: buildSubsPurchaseDetailInline(purchase)
                ProductType.INAPP -> buildFromOneTimeProcessor(purchase)
                    ?: buildInAppPurchaseDetailInline(purchase)
                else -> {
                    loge("createPurchaseDetailFromPurchase", "unknown product type for ${purchase.products}")
                    null
                }
            }
        } catch (e: IllegalStateException) {
            loge("createPurchaseDetailFromPurchase", "error building PurchaseDetail: ${e.message}", e)
            buildFallbackPurchaseDetail(purchase)
        }
    }

    suspend fun buildSubsPurchaseDetailInline(purchase: Purchase): PurchaseDetail {
        val offerDetails = resolveOfferDetailsInline(purchase)
        val storeEntry = storeProductDetails.find { it.productDetail.productId == purchase.products.firstOrNull() }
        val expiryTime = calculateExpiryTimeInline(purchase, offerDetails)

        val accountId = purchase.accountIdentifiers?.obfuscatedAccountId.orEmpty()
        if (accountId.isBlank()) {
            loge("buildSubsPurchaseDetailInline", "obfuscatedAccountId blank for SUBS token=${purchase.purchaseToken.take(8)}; was launchFlow called?")
        }
        return PurchaseDetail(
            productId = purchase.products.firstOrNull().orEmpty(),
            planId = offerDetails?.basePlanId ?: storeEntry?.productDetail?.planId.orEmpty(),
            productTitle = storeEntry?.productDetail?.productTitle.orEmpty(),
            planTitle = offerDetails?.let { queryUtils().getPlanTitle(it) }.orEmpty(),
            state = purchase.purchaseState,
            purchaseToken = purchase.purchaseToken,
            productType = ProductType.SUBS,
            purchaseTime = purchase.purchaseTime.toFormattedDate(),
            purchaseTimeMillis = purchase.purchaseTime,
            isAutoRenewing = purchase.isAutoRenewing,
            accountId = accountId,
            deviceId = if (getObfuscatedDeviceId().isNotBlank()) SubscriptionStatus.DEVICE_ID_INDICATOR else "",
            payload = purchase.developerPayload,
            expiryTime = expiryTime,
            status = purchase.purchaseState.toSubscriptionStatusId(),
            windowDays = InAppBillingHandler.REVOKE_WINDOW_SUBS_MONTHLY_DAYS,
            orderId = purchase.orderId.orEmpty()
        )
    }

    suspend fun buildInAppPurchaseDetailInline(purchase: Purchase): PurchaseDetail? {
        val productId = purchase.products.firstOrNull().orEmpty()
        val expiryTime = calculateOneTimeExpiryTime(purchase)
        if (expiryTime < System.currentTimeMillis()) return null
        val storeEntry = storeProductDetails.find { it.productDetail.productId == productId }
        val accountId = purchase.accountIdentifiers?.obfuscatedAccountId.orEmpty()
        if (accountId.isBlank()) {
            loge("buildInAppPurchaseDetailInline", "obfuscatedAccountId blank for INAPP token=${purchase.purchaseToken.take(8)}, was launchFlow called?")
        }
        return PurchaseDetail(
            productId = productId,
            planId = storeEntry?.oneTimeOfferDetails?.purchaseOptionId ?: productId,
            productTitle = storeEntry?.productDetail?.productTitle ?: QueryUtils.getPlanTitle(getOneTimeBillingPeriod(productId)),
            planTitle = storeEntry?.productDetail?.productTitle.orEmpty(),
            state = purchase.purchaseState,
            purchaseToken = purchase.purchaseToken,
            productType = ProductType.INAPP,
            purchaseTime = purchase.purchaseTime.toFormattedDate(),
            purchaseTimeMillis = purchase.purchaseTime,
            isAutoRenewing = false,
            accountId = accountId,
            deviceId = if (getObfuscatedDeviceId().isNotBlank()) SubscriptionStatus.DEVICE_ID_INDICATOR else "",
            payload = purchase.developerPayload,
            expiryTime = expiryTime,
            status = purchase.purchaseState.toSubscriptionStatusId(),
            windowDays = resolveOneTimeRevokeDays(productId),
            orderId = purchase.orderId.orEmpty()
        )
    }

    suspend fun buildFallbackPurchaseDetail(purchase: Purchase): PurchaseDetail {
        return PurchaseDetail(
            productId = purchase.products.firstOrNull() ?: "",
            planId = "",
            productTitle = "",
            planTitle = "",
            state = purchase.purchaseState,
            purchaseToken = purchase.purchaseToken,
            productType = getProductType(purchase),
            purchaseTime = purchase.purchaseTime.toFormattedDate(),
            purchaseTimeMillis = purchase.purchaseTime,
            isAutoRenewing = purchase.isAutoRenewing,
            accountId = purchase.accountIdentifiers?.obfuscatedAccountId ?: "",
            deviceId = if (getObfuscatedDeviceId().isNotBlank()) SubscriptionStatus.DEVICE_ID_INDICATOR else "",
            payload = purchase.developerPayload,
            expiryTime = 0L,
            status = purchase.purchaseState.toSubscriptionStatusId(),
            windowDays = InAppBillingHandler.REVOKE_WINDOW_SUBS_MONTHLY_DAYS,
            orderId = purchase.orderId ?: ""
        )
    }

    fun resolveOfferDetailsForPurchase(purchase: Purchase): ProductDetails.SubscriptionOfferDetails? {
        if (getProductType(purchase) == ProductType.INAPP) return null
        return resolveOfferDetails(purchase) ?: resolveOfferDetailsInline(purchase)
    }

    fun resolveOfferDetailsInline(purchase: Purchase): ProductDetails.SubscriptionOfferDetails? {
        val productId = purchase.products.firstOrNull() ?: return null
        val candidates = storeProductDetails.filter {
            it.productDetail.productId == productId && it.offerDetails != null
        }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.offerDetails?.offerId == null }?.offerDetails
            ?: candidates.first().offerDetails
    }

    fun calculateExpiryTime(
        purchase: Purchase,
        offerDetails: ProductDetails.SubscriptionOfferDetails?
    ): Long {
        return when (getProductType(purchase)) {
            ProductType.INAPP -> calculateOneTimeExpiryTime(purchase)
            else -> calculateExpiryTimeInline(purchase, offerDetails)
        }
    }

    fun calculateExpiryTimeInline(
        purchase: Purchase,
        offerDetails: ProductDetails.SubscriptionOfferDetails?
    ): Long {
        if (offerDetails == null) return Long.MAX_VALUE

        val pricingPhases = offerDetails.pricingPhases.pricingPhaseList
        val recurringPhase = pricingPhases
            .lastOrNull { it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING }
            ?: pricingPhases.lastOrNull()

        val billingPeriod = recurringPhase?.billingPeriod
        if (billingPeriod.isNullOrEmpty()) return Long.MAX_VALUE

        val cal = Calendar.getInstance().apply { timeInMillis = purchase.purchaseTime }
        val added = addIso8601Period(cal, billingPeriod)
        return if (added) {
            cal.timeInMillis
        } else {
            val days = QueryUtils.getBillingPeriodDays(billingPeriod)
            if (days > 0) { cal.add(Calendar.DAY_OF_YEAR, days); cal.timeInMillis }
            else Long.MAX_VALUE
        }
    }

    fun calculateOneTimeExpiryTime(purchase: Purchase): Long {
        val productId = purchase.products.firstOrNull() ?: ""
        val cal = Calendar.getInstance().apply { timeInMillis = purchase.purchaseTime }
        return when (productId) {
            InAppBillingHandler.ONE_TIME_PRODUCT_2YRS -> {
                cal.add(Calendar.YEAR, 2)
                cal.timeInMillis
            }
            InAppBillingHandler.ONE_TIME_PRODUCT_5YRS -> {
                cal.add(Calendar.YEAR, 5)
                cal.timeInMillis
            }
            InAppBillingHandler.ONE_TIME_TEST_PRODUCT_ID -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis
            }
            InAppBillingHandler.ONE_TIME_PRODUCT_ID -> {
                cal.add(Calendar.YEAR, 2)
                cal.timeInMillis
            }
            else -> {
                cal.add(Calendar.YEAR, 2)
                cal.timeInMillis
            }
        }
    }

    fun addIso8601Period(cal: Calendar, period: String): Boolean {
        val regex = Regex("""^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?$""")
        val match = regex.matchEntire(period) ?: return false
        val years = match.groupValues[1].toIntOrNull() ?: 0
        val months = match.groupValues[2].toIntOrNull() ?: 0
        val weeks = match.groupValues[3].toIntOrNull() ?: 0
        val days = match.groupValues[4].toIntOrNull() ?: 0
        if (years == 0 && months == 0 && weeks == 0 && days == 0) return false
        if (years > 0) cal.add(Calendar.YEAR, years)
        if (months > 0) cal.add(Calendar.MONTH, months)
        if (weeks > 0) cal.add(Calendar.WEEK_OF_YEAR, weeks)
        if (days > 0) cal.add(Calendar.DAY_OF_YEAR, days)
        return true
    }

    fun getOneTimeBillingPeriod(productId: String): String = when (productId) {
        InAppBillingHandler.ONE_TIME_PRODUCT_2YRS -> "P2Y"
        InAppBillingHandler.ONE_TIME_PRODUCT_5YRS -> "P5Y"
        InAppBillingHandler.ONE_TIME_TEST_PRODUCT_ID -> "P1D"
        InAppBillingHandler.ONE_TIME_PRODUCT_ID -> "P2Y"
        else -> "P2Y"
    }

    fun resolveOneTimeRevokeDays(productId: String): Int = when (productId) {
        InAppBillingHandler.ONE_TIME_PRODUCT_2YRS -> InAppBillingHandler.REVOKE_WINDOW_ONE_TIME_2YRS_DAYS
        InAppBillingHandler.ONE_TIME_PRODUCT_5YRS -> InAppBillingHandler.REVOKE_WINDOW_ONE_TIME_5YRS_DAYS
        InAppBillingHandler.ONE_TIME_PRODUCT_ID -> InAppBillingHandler.REVOKE_WINDOW_ONE_TIME_2YRS_DAYS
        InAppBillingHandler.ONE_TIME_TEST_PRODUCT_ID -> InAppBillingHandler.REVOKE_WINDOW_SUBS_MONTHLY_DAYS
        else -> InAppBillingHandler.REVOKE_WINDOW_ONE_TIME_2YRS_DAYS
    }

    fun Int.toSubscriptionStatusId(): Int = when (this) {
        Purchase.PurchaseState.PURCHASED -> SubscriptionStatus.SubscriptionState.STATE_ACTIVE.id
        Purchase.PurchaseState.PENDING -> SubscriptionStatus.SubscriptionState.STATE_ACK_PENDING.id
        else -> SubscriptionStatus.SubscriptionState.STATE_UNKNOWN.id
    }

    fun isPurchaseStateCompleted(purchase: Purchase?): Boolean {
        val mname = "isPurchaseStateCompleted"
        if (purchase?.purchaseState == null) {
            loge(mname, "purchase or purchase state is null, $purchase")
            return false
        }

        val isPurchased = purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        val isAcknowledged = purchase.isAcknowledged

        purchase.products.forEach {
            logv(mname, "Product in purchase: $it")
        }
        val isSubs = getProductType(purchase) == ProductType.SUBS
        val isOneTime = getProductType(purchase) == ProductType.INAPP
        log(mname, "isPurchaseStateCompleted: isSubs?$isSubs, isOneTime?$isOneTime, isPurchased?$isPurchased, isAcknowledged?$isAcknowledged, isAutoRenewing?${purchase.isAutoRenewing}")
        return if (isSubs) {
            isPurchased && isAcknowledged
        } else if (isOneTime) {
            isPurchased && isAcknowledged
        } else {
            false
        }
    }

    fun getProductType(
        purchase: Purchase,
        queriedProductType: String? = null
    ): String {
        val mname = "getProductType"
        val productId = purchase.products.firstOrNull()

        if (productId == null) {
            loge(mname, "purchase has no productIds, defaulting to SUBS")
            return ProductType.SUBS
        }

        if (queriedProductType != null) {
            logv(mname, "productId=$productId resolved via queriedProductType=$queriedProductType")
            return queriedProductType
        }

        val cachedEntry = storeProductDetails.find { it.productDetail.productId == productId }
        if (cachedEntry != null) {
            val cachedType = cachedEntry.productDetails.productType
            logv(mname, "productId=$productId resolved via cache productType=$cachedType")
            return cachedType
        }

        val anyEntry = storeProductDetails.find { qpd ->
            qpd.productDetail.productId == productId
        }
        if (anyEntry != null) {
            return when {
                anyEntry.offerDetails != null -> {
                    logv(mname, "productId=$productId resolved via offerDetails presence → SUBS")
                    ProductType.SUBS
                }
                anyEntry.oneTimeOfferDetails != null -> {
                    logv(mname, "productId=$productId resolved via oneTimeOfferDetails presence → INAPP")
                    ProductType.INAPP
                }
                else -> {
                    logd(mname, "productId=$productId cache entry has no offer details; failing")
                    resolveProductTypeFromKnownIds(productId)
                }
            }
        }

        return resolveProductTypeFromKnownIds(productId)
    }

    fun resolveProductTypeFromKnownIds(productId: String): String {
        val mname = "resolveProductTypeFromKnownIds"
        return when (productId) {
            InAppBillingHandler.STD_PRODUCT_ID -> {
                logd(mname, "productId=$productId → SUBS (known)")
                ProductType.SUBS
            }
            InAppBillingHandler.ONE_TIME_PRODUCT_ID,
            InAppBillingHandler.ONE_TIME_TEST_PRODUCT_ID,
            InAppBillingHandler.ONE_TIME_PRODUCT_2YRS,
            InAppBillingHandler.ONE_TIME_PRODUCT_5YRS -> {
                logd(mname, "productId=$productId → INAPP (known)")
                ProductType.INAPP
            }
            else -> {
                loge(mname, "unknown productId=$productId, defaulting to SUBS; " +
                    "add this product ID to resolveProductTypeFromKnownIds()")
                ProductType.SUBS
            }
        }
    }

    fun Long.toFormattedDate(
        pattern: String = "MMM dd, yyyy HH:mm:ss",
        locale: Locale = Locale.getDefault()
    ): String {
        val formatter = SimpleDateFormat(pattern, locale)
        return formatter.format(Date(this))
    }
}
