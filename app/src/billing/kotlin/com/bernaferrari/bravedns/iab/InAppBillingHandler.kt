/*
 * Copyright 2024 RethinkDNS and its authors
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

import Logger
import Logger.LOG_IAB
import android.app.Activity
import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager.getEntitlementDetails
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager.getExpiryFromPayload
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager.storeWinEntitlement
import com.bernaferrari.bravedns.rpnproxy.SubscriptionStateMachineV2
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Play Billing façade. Connection, purchase launch, ack/entitlement, product catalog,
 * and SM dispatch live in same-package collaborators. Public API is unchanged.
 *
 * Lock order (do not invert): [connectionMutex] → SM stateLock → repository mutex.
 * [connectionMutex] is owned here. [BillingConnectionController] acquires it only for
 * startConnection; cancel/revoke acquire it in the wrappers below.
 */
object InAppBillingHandler : KoinComponent {

    private var billingListener: BillingListener? = null

    @Volatile
    private var appContext: Context? = null

    const val TAG = "IABHandler"

    private val persistentState by inject<PersistentState>()
    private val billingBackendClient by inject<BillingBackendClient>()
    private val secureIdentityStore by inject<SecureIdentityStore>()
    private val eventLogger by inject<EventLogger>()

    const val PLAY_SUBS_LINK = "https://play.google.com/store/account/subscriptions?sku=$1&package=$2"

    const val STD_PRODUCT_ID = "standard.tier"
    const val ONE_TIME_PRODUCT_ID = "onetime.tier"
    const val ONE_TIME_TEST_PRODUCT_ID = "test_product"

    const val ONE_TIME_PRODUCT_2YRS = "proxy-yearly-2"
    const val ONE_TIME_PRODUCT_5YRS = "proxy-yearly-5"

    const val SUBS_PRODUCT_MONTHLY = "proxy-monthly"
    const val SUBS_PRODUCT_YEARLY = "proxy-yearly"

    const val PRODUCT_TYPE_SUBS = "subs"
    const val PRODUCT_TYPE_INAPP = "inapp"

    const val REVOKE_WINDOW_SUBS_MONTHLY_DAYS = 3
    const val REVOKE_WINDOW_SUBS_YEARLY_DAYS = 7
    const val REVOKE_WINDOW_ONE_TIME_2YRS_DAYS = 2 * 7
    const val REVOKE_WINDOW_ONE_TIME_5YRS_DAYS = 5 * 7

    private lateinit var queryUtils: QueryUtils
    private val productDetails: CopyOnWriteArrayList<ProductDetail> = CopyOnWriteArrayList()
    private val storeProductDetails: CopyOnWriteArrayList<QueryProductDetail> =
        CopyOnWriteArrayList()

    private var subsProcessor: SubscriptionPurchaseProcessor? = null
    private var oneTimeProcessor: OneTimePurchaseProcessor? = null

    val productDetailsLiveData = MutableLiveData<List<ProductDetail>>()
    val purchasesLiveData = MutableLiveData<List<PurchaseDetail>>()
    val transactionErrorLiveData = MutableLiveData<BillingResult?>()

    val serverApiErrorLiveData = MutableLiveData<ServerApiError?>()

    private val _oneTimePurchaseCompletedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val oneTimePurchaseCompletedFlow: SharedFlow<Unit> = _oneTimePurchaseCompletedFlow.asSharedFlow()

    private val subscriptionStateMachine: SubscriptionStateMachineV2 by inject()

    private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Outermost lock in the billing/SM/repo order. Cancel/revoke acquire it here.
     * [BillingConnectionController.startConnection] also acquires this same instance.
     */
    private val connectionMutex = kotlinx.coroutines.sync.Mutex()

    @Volatile private var isInitialized = false

    const val UNACK_ESCALATION_THRESHOLD = 3

    enum class Priority(val value: Int) {
        HIGH(0),
        MEDIUM(1),
        LOW(2)
    }

    internal data class BestPlan(
        val trialDays: Int,
        val pricingPhase: ProductDetails.PricingPhase?
    )

    enum class RecurringMode {
        FREE,
        DISCOUNTED,
        ORIGINAL
    }

    private fun logd(methodName: String, msg: String) {
        Logger.d(LOG_IAB, "$TAG $methodName: $msg")
    }

    private fun logv(methodName: String, msg: String) {
        Logger.v(LOG_IAB, "$TAG $methodName: $msg")
    }

    private fun loge(methodName: String, msg: String, e: Exception? = null) {
        Logger.e(LOG_IAB, "$TAG $methodName: $msg", e)
    }

    private fun log(methodName: String, msg: String) {
        Logger.i(LOG_IAB, "$TAG $methodName: $msg")
    }

    private val ackController: PurchaseAckController by lazy {
        PurchaseAckController(
            billingBackendClient = billingBackendClient,
            persistentState = { persistentState },
            eventLogger = { eventLogger },
            serverApiErrorLiveData = serverApiErrorLiveData,
            appContext = { appContext }
        )
    }

    private val detailFactory: PurchaseDetailFactory by lazy {
        PurchaseDetailFactory(
            storeProductDetails = storeProductDetails,
            queryUtils = { queryUtils },
            getObfuscatedDeviceId = { ackController.getObfuscatedDeviceId() },
            resolveOfferDetails = { purchase -> subsProcessor?.resolveOfferDetails(purchase) },
            buildFromSubsProcessor = { purchase -> subsProcessor?.buildPurchaseDetail(purchase) },
            buildFromOneTimeProcessor = { purchase -> oneTimeProcessor?.buildPurchaseDetail(purchase) }
        )
    }

    private val catalog: BillingProductCatalog by lazy {
        BillingProductCatalog(
            productDetails = productDetails,
            storeProductDetails = storeProductDetails,
            productDetailsLiveData = productDetailsLiveData,
            billingListener = { billingListener },
            billingClient = { connection.clientOrNull() },
            queryUtils = { queryUtils },
            getOneTimeBillingPeriod = { detailFactory.getOneTimeBillingPeriod(it) }
        )
    }

    private val resultProcessor: PurchaseResultProcessor by lazy {
        PurchaseResultProcessor(
            scope = billingScope,
            subscriptionStateMachine = subscriptionStateMachine,
            storeProductDetails = storeProductDetails,
            transactionErrorLiveData = transactionErrorLiveData,
            oneTimePurchaseCompletedFlow = _oneTimePurchaseCompletedFlow,
            billingClient = { connection.clientOrNull() },
            isBillingClientSetup = { connection.isBillingClientSetup() },
            detailFactory = detailFactory,
            subsProcessor = { subsProcessor },
            oneTimeProcessor = { oneTimeProcessor },
            queryEntitlementFromServer = { acc, did, pd ->
                ackController.queryEntitlementFromServer(acc, did, pd)
            },
            getObfuscatedAccountId = { ackController.getObfuscatedAccountId() },
            getObfuscatedDeviceId = { ackController.getObfuscatedDeviceId() },
            resolveDeviceId = { caller -> ackController.resolveDeviceId(caller) },
            registerDevice = { acc, did -> ackController.registerDevice(acc, did) },
            reconcileDidForCid = { cid -> billingBackendClient.reconcileDidForCid(cid) },
            handleUnauthorized401 = { op, acc, did -> ackController.handleUnauthorized401(op, acc, did) },
            handleConflict409 = { op, acc, did, tok, sku, msg ->
                ackController.handleConflict409(op, acc, did, tok, sku, msg)
            }
        )
    }

    private val connection: BillingConnectionController by lazy {
        BillingConnectionController(
            connectionMutex = connectionMutex,
            scope = billingScope,
            purchasesUpdatedListener = { resultProcessor.purchasesUpdatedListener },
            onAlreadyConnected = { client ->
                if (!::queryUtils.isInitialized) {
                    queryUtils = QueryUtils(client)
                }
                setupProcessors()
            },
            onSetupFinished = { client ->
                queryUtils = QueryUtils(client)
                setupProcessors()
                connection.queryBillingConfig()
                billingScope.launch(Dispatchers.IO) { catalog.queryProductDetails() }
            },
            onDisconnected = {
                catalog.clearCaches()
                billingScope.launch {
                    try {
                        subscriptionStateMachine.systemCheck()
                    } catch (e: IllegalStateException) {
                        loge("startConnection", "err during system check on disconnect: ${e.message}", e)
                    }
                }
            }
        )
    }

    private val purchaseFlow: PurchaseFlowController by lazy {
        PurchaseFlowController(
            storeProductDetails = storeProductDetails,
            subscriptionStateMachine = subscriptionStateMachine,
            billingListener = { billingListener },
            transactionErrorLiveData = transactionErrorLiveData,
            billingClient = { connection.clientOrNull() },
            queryProductDetails = { catalog.queryProductDetails() },
            fetchOrEnsureCustomerIds = { ackController.fetchOrEnsureCustomerIds() }
        )
    }

    private val stateDispatcher: BillingStateDispatcher by lazy {
        BillingStateDispatcher(
            scope = billingScope,
            subscriptionStateMachine = subscriptionStateMachine,
            purchasesLiveData = purchasesLiveData,
            transactionErrorLiveData = transactionErrorLiveData
        )
    }

    fun initiate(context: Context, billingListener: BillingListener? = null) {
        val mname = this::initiate.name
        this.billingListener = billingListener
        if (appContext == null) {
            appContext = context.applicationContext
        }

        connection.setupBillingClient(context)

        billingScope.launch {
            if (!isInitialized) {
                try {
                    subscriptionStateMachine.initialize()
                    stateDispatcher.startStateObserver()
                    isInitialized = true
                    logd(mname, "state machine initialized")
                } catch (e: IllegalStateException) {
                    loge(mname, "failed to initialize state machine: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        billingListener?.onConnectionResult(false, "State machine initialization failed: ${e.message}")
                    }
                    return@launch
                }
            }

            connection.startConnection { isSuccess, message ->
                if (isSuccess) {
                    logd(mname, "billing connected, fetching initial state")
                    resultProcessor.resetEmptyQueryCounters()
                    resultProcessor.fetchPurchases(listOf(ProductType.SUBS, ProductType.INAPP))
                } else {
                    loge(mname, "billing connection failed: $message")
                }
                billingListener?.onConnectionResult(isSuccess, message)
            }
        }
    }

    fun registerListener(billingListener: BillingListener?) {
        val mname = this::registerListener.name
        this.billingListener = billingListener
        log(mname, "listener registered")
    }

    fun enableInAppMessaging(activity: Activity) {
        connection.enableInAppMessaging(activity) {
            resultProcessor.fetchPurchases(listOf(ProductType.SUBS, ProductType.INAPP))
        }
    }

    fun isBillingClientSetup(): Boolean = connection.isBillingClientSetup()

    fun isListenerRegistered(l: BillingListener?): Boolean {
        val mname = this::isListenerRegistered.name
        logv(mname, "checking if listener is registered")
        val isRegistered = billingListener == l
        log(mname, "isRegistered: $isRegistered")
        return isRegistered
    }

    private fun setupProcessors() {
        if (subsProcessor != null && oneTimeProcessor != null) return
        val activateRpnFn: suspend (PurchaseDetail) -> Unit = { pd ->
            RpnProxyManager.activateRpn(pd)
        }

        val getProductTypeFn: (Purchase) -> String = { p -> getProductType(p) }

        val getDeviceIdFn: suspend () -> String = { getObfuscatedDeviceId() }

        val onInAppAckSuccessFn: suspend (Purchase) -> Unit = { purchase ->
            val accountId = purchase.accountIdentifiers?.obfuscatedAccountId
            if (accountId.isNullOrBlank()) {
                loge("onInAppAckSuccess", "obfuscatedAccountId missing from INAPP purchase ${purchase.purchaseToken.take(8)}; skipping registerDevice")
            } else {
                val deviceId = ackController.resolveDeviceId("onInAppAckSuccess")
                if (deviceId == null) {
                    // error already logged inside resolveDeviceId
                } else {
                    try {
                        logd("onInAppAckSuccess", "ack succeeded; registerDevice token=${purchase.purchaseToken.take(8)}, accountId-len=${accountId.length}, deviceId-len=${deviceId.length}")
                        registerDevice(accountId, deviceId)
                    } catch (e: IllegalStateException) {
                        loge("onInAppAckSuccess", "registerDevice failed (non-fatal): ${e.message}", e)
                    }
                }
            }
        }

        subsProcessor = SubscriptionPurchaseProcessor(
            storeProductDetails = storeProductDetails,
            subscriptionStateMachine = subscriptionStateMachine,
            queryUtils = { queryUtils },
            acknowledgePurchase = ::acknowledgePurchaseFromServer,
            activateRpn = activateRpnFn,
            getProductType = getProductTypeFn,
            getDeviceId = getDeviceIdFn,
            reconcileCidDid = ::reconcileCidDidFromPurchase,
            validatePayloadAndFetchIfRequired = ::validatePayloadAndFetchIfRequired
        )
        oneTimeProcessor = OneTimePurchaseProcessor(
            storeProductDetails = storeProductDetails,
            subscriptionStateMachine = subscriptionStateMachine,
            acknowledgePurchase = ::acknowledgePurchaseFromServer,
            activateRpn = activateRpnFn,
            getProductType = getProductTypeFn,
            getDeviceId = getDeviceIdFn,
            onAckSuccess = onInAppAckSuccessFn,
            reconcileCidDid = ::reconcileCidDidFromPurchase,
            validatePayloadAndFetchIfRequired = ::validatePayloadAndFetchIfRequired
        )
        logd("setupProcessors", "SubscriptionPurchaseProcessor and InAppPurchaseProcessor created")
    }

    private suspend fun validatePayloadAndFetchIfRequired(purchaseDtl: PurchaseDetail): PurchaseDetail {
        val mname = this::validatePayloadAndFetchIfRequired.name
        val now = System.currentTimeMillis()
        val tunnelExpiry: Long? = getExpiryFromPayload(purchaseDtl.payload)
        if (tunnelExpiry != null && tunnelExpiry > now) {
            return purchaseDtl
        }
        Logger.i(mname, "Payload validation failed or expired for token=${purchaseDtl.purchaseToken.take(8)}; fetching updated entitlement from server")
        return queryEntitlementFromServer(getObfuscatedAccountId(), getObfuscatedDeviceId(), purchaseDtl)
    }

    suspend fun reconcileCidDidFromPurchase(cid: String) {
        ackController.reconcileCidDidFromPurchase(cid, secureIdentityStore, ::currentEnv)
    }

    suspend fun reconcilePurchase() {
        val mname = this::reconcilePurchase.name
        val thresholdToCheckWinExpiryTs: Long = TimeUnit.DAYS.toMillis(10)
        val expiryDifferenceTs: Long = TimeUnit.DAYS.toMillis(30)

        val activePurchase = getActivePurchasesSnapshot()
        val entitlement = getEntitlementDetails()
        val entitlementExpiry = entitlement?.expiry()

        if (activePurchase.isEmpty()) return

        if (entitlementExpiry == null) {
            logd(mname, "missing entitlement expiry, skipping reconcile")
            return
        }

        val windscribeExpiry = VpnController.getWinExpiryTs()

        if (windscribeExpiry == null) {
            logd(mname, "missing entitlement or win expiry, skipping reconcile")
            return
        }

        if (windscribeExpiry > entitlementExpiry) {
            logd(mname, "win expiry is ahead of entitlement expiry, skipping reconcile")
            return
        }

        val now = System.currentTimeMillis()
        if (windscribeExpiry - now > expiryDifferenceTs) {
            logd(mname, "win expiry is beyond threshold, skipping reconcile")
            return
        }

        if (entitlementExpiry - windscribeExpiry < thresholdToCheckWinExpiryTs) {
            logd(mname, "expiry gap is below reconciliation threshold, skipping reconcile")
            return
        }

        val purchaseDtl = subscriptionStateMachine.getSubscriptionData()?.purchaseDetail
        if (purchaseDtl == null) {
            loge(mname, "missing purchase detail, skipping reconcile")
            return
        }

        val newPurchaseDtl = queryEntitlementFromServer(getObfuscatedAccountId(), getObfuscatedDeviceId(), purchaseDtl)
        try {
            storeWinEntitlement(newPurchaseDtl.payload)
            log(mname, "new entitlement updated")
        } catch (e: IllegalStateException) {
            loge(mname, "storeWinEntitlement failed: ${e.message}", e)
            return
        }

        try {
            val subsData = subscriptionStateMachine.getSubscriptionData()
            if (subsData != null) {
                subsData.subscriptionStatus.developerPayload = newPurchaseDtl.payload
                val updatedSubsData = subsData.copy(purchaseDetail = newPurchaseDtl)
                subscriptionStateMachine.stateMachine.updateData(updatedSubsData)
                log(mname, "state machine payload updated")
            }
        } catch (e: IllegalStateException) {
            loge(mname, "state machine update failed (non-fatal): ${e.message}")
        }
    }

    private fun currentEnv(): SecureIdentityStore.Env =
        if (persistentState.appTestMode) SecureIdentityStore.Env.TEST
        else SecureIdentityStore.Env.PROD

    fun getProductType(
        purchase: Purchase,
        queriedProductType: String? = null
    ): String = detailFactory.getProductType(purchase, queriedProductType)

    fun fetchPurchases(productType: List<String>) {
        resultProcessor.fetchPurchases(productType)
    }

    suspend fun queryProductDetailsWithTimeout(timeout: Long = 10_000) {
        catalog.queryProductDetailsWithTimeout(timeout)
    }

    suspend fun purchaseSubs(
        activity: Activity,
        productId: String,
        planId: String,
        forceResubscribe: Boolean = false
    ) {
        purchaseFlow.purchaseSubs(activity, productId, planId, forceResubscribe)
    }

    suspend fun purchaseOneTime(activity: Activity, productId: String, planId: String, forceExtend: Boolean = false) {
        purchaseFlow.purchaseOneTime(activity, productId, planId, forceExtend)
    }

    suspend fun getObfuscatedAccountId(): String = ackController.getObfuscatedAccountId()

    suspend fun getObfuscatedDeviceId(accountId: String? = null): String =
        if (accountId == null) ackController.getObfuscatedDeviceId()
        else billingBackendClient.getDeviceIdForCid(accountId)

    internal suspend fun handleUnauthorized401(
        operation: ServerApiError.Operation,
        accountId: String,
        deviceId: String
    ) {
        ackController.handleUnauthorized401(operation, accountId, deviceId)
    }

    fun getActivePurchasesSnapshot(): List<PurchaseDetail> {
        val pd = subscriptionStateMachine.getSubscriptionData()?.purchaseDetail
        return if (pd != null) listOf(pd).toList() else emptyList()
    }

    suspend fun registerDevice(
        accountId: String,
        deviceId: String,
        meta: JsonObject? = null
    ) {
        ackController.registerDevice(accountId, deviceId, meta)
    }

    fun queryBillingConfig() {
        connection.queryBillingConfig()
    }

    fun hasValidSubscription(): Boolean {
        return RpnProxyManager.hasValidSubscription()
    }

    fun endConnection() {
        connection.endConnection()
    }

    suspend fun cancelOneTimePurchase(
        accountId: String,
        deviceId: String,
        purchaseToken: String,
        productId: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            ackController.cancelOrRevokeUnderLock(
                mname = "cancelOneTimePurchase",
                accountId = accountId,
                deviceId = deviceId,
                purchaseToken = purchaseToken,
                sku = productId,
                operation = ServerApiError.Operation.CANCEL,
                backendCall = { acc, did, sku, tok -> billingBackendClient.cancelPurchase(acc, did, sku, tok) },
                updateLocal = { acc, tok -> RpnProxyManager.updateCancelledSubscription(acc, tok) },
                notifyStateMachine = { subscriptionStateMachine.userCancelled(purchaseToken) },
                successMessage = "One-time purchase cancelled successfully",
                logSuccessMessage = "One-time purchase cancelled successfully",
                stateSyncFailMessage = "Cancelled on server but state sync failed"
            )
        }
    }

    suspend fun revokeOneTimePurchase(
        accountId: String,
        deviceId: String,
        purchaseToken: String,
        productId: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            ackController.cancelOrRevokeUnderLock(
                mname = "revokeOneTimePurchase",
                accountId = accountId,
                deviceId = deviceId,
                purchaseToken = purchaseToken,
                sku = productId,
                operation = ServerApiError.Operation.REVOKE,
                backendCall = { acc, did, sku, tok -> billingBackendClient.revokePurchase(acc, did, sku, tok) },
                updateLocal = { acc, tok -> RpnProxyManager.updateRevokedSubscription(acc, tok) },
                notifyStateMachine = { subscriptionStateMachine.subscriptionRevoked(purchaseToken) },
                successMessage = "One-time purchase revoked successfully",
                logSuccessMessage = "One-time purchase revoked successfully",
                stateSyncFailMessage = "Revoked on server but state sync failed"
            )
        }
    }

    fun getRemainingDaysForInApp(): Long? {
        val sub = subscriptionStateMachine.getSubscriptionData()?.subscriptionStatus ?: return null
        val isInApp = SubscriptionStateMachineV2.isInAppProduct(sub.productId)
        if (!isInApp) return null
        val expiry = sub.billingExpiry
        if (expiry <= 0L || expiry == Long.MAX_VALUE) return null
        val nowMs = System.currentTimeMillis()
        return (expiry - nowMs) / (24L * 60 * 60 * 1000)
    }

    suspend fun getRemainingDaysForInAppSuspend(): Long? {
        val mname = "getRemainingDaysForInAppSuspend"
        val sub = subscriptionStateMachine.getSubscriptionData()?.subscriptionStatus
        if (sub == null) {
            loge(mname, "no subscription data available in state machine; cannot calculate remaining days for INAPP")
            return null
        }
        val isInApp = SubscriptionStateMachineV2.isInAppProduct(sub.productId)
        if (!isInApp) {
            loge(mname, "Current subscription is not an INAPP product; cannot calculate remaining days for INAPP")
            return null
        }
        val effectiveExpiry = subscriptionStateMachine.getEffectiveInAppExpiryMs()
        if (effectiveExpiry == null) {
            loge(mname, "Effective INAPP expiry is null; cannot calculate remaining days")
            return null
        }
        if (effectiveExpiry <= 0L || effectiveExpiry == Long.MAX_VALUE) {
            loge(mname, "Effective INAPP expiry is invalid (expiry=$effectiveExpiry); cannot calculate remaining days")
            return null
        }
        val nowMs = System.currentTimeMillis()
        return (effectiveExpiry - nowMs) / (24L * 60 * 60 * 1000)
    }

    suspend fun cancelPlaySubscription(accountId: String, deviceId: String, purchaseToken: String, sku: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                ackController.cancelOrRevokeUnderLock(
                    mname = "cancelPlaySubscription",
                    accountId = accountId,
                    deviceId = deviceId,
                    purchaseToken = purchaseToken,
                    sku = sku,
                    operation = ServerApiError.Operation.CANCEL,
                    backendCall = { acc, did, skuArg, tok -> billingBackendClient.cancelPurchase(acc, did, skuArg, tok) },
                    updateLocal = { acc, tok -> RpnProxyManager.updateCancelledSubscription(acc, tok) },
                    notifyStateMachine = { subscriptionStateMachine.userCancelled(purchaseToken) },
                    successMessage = "Subscription cancelled successfully",
                    logSuccessMessage = "subscription cancelled successfully",
                    stateSyncFailMessage = "Cancelled on server but state sync failed"
                )
            }
        }

    suspend fun revokeSubscription(accountId: String, deviceId: String, purchaseToken: String, sku: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                ackController.cancelOrRevokeUnderLock(
                    mname = "revokeSubscription",
                    accountId = accountId,
                    deviceId = deviceId,
                    purchaseToken = purchaseToken,
                    sku = sku,
                    operation = ServerApiError.Operation.REVOKE,
                    backendCall = { acc, did, skuArg, tok -> billingBackendClient.revokePurchase(acc, did, skuArg, tok) },
                    updateLocal = { acc, tok -> RpnProxyManager.updateRevokedSubscription(acc, tok) },
                    notifyStateMachine = { subscriptionStateMachine.subscriptionRevoked(purchaseToken) },
                    successMessage = "Subscription revoked successfully",
                    logSuccessMessage = "Subscription revoked successfully",
                    stateSyncFailMessage = "Revoked on server but state sync failed"
                )
            }
        }

    suspend fun queryEntitlementFromServer(accountId: String, deviceId: String, purchase: PurchaseDetail): PurchaseDetail =
        ackController.queryEntitlementFromServer(accountId, deviceId, purchase)

    suspend fun acknowledgePurchaseFromServer(
        accountId: String,
        deviceId: String,
        purchaseToken: String,
        productType: String
    ): Pair<Boolean, String> =
        ackController.acknowledgePurchaseFromServer(accountId, deviceId, purchaseToken, productType)

    fun getSubscriptionState(): SubscriptionStateMachineV2.SubscriptionState {
        return subscriptionStateMachine.getCurrentState()
    }

    fun getSubscriptionStateFlow(): StateFlow<SubscriptionStateMachineV2.SubscriptionState> {
        return subscriptionStateMachine.currentState
    }
}
