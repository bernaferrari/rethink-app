/*
 * Copyright 2025 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.rpnproxy

import Logger
import Logger.LOG_IAB
import Logger.LOG_TAG_PROXY
import android.content.Context
import android.text.format.DateUtils
import com.android.billingclient.api.BillingClient
import com.bernaferrari.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.bernaferrari.bravedns.database.CountryConfig
import com.bernaferrari.bravedns.database.CountryConfigRepository
import com.bernaferrari.bravedns.database.RpnProxyRepository
import com.bernaferrari.bravedns.database.SubscriptionStatus
import com.bernaferrari.bravedns.database.SubscriptionStatusRepository
import com.bernaferrari.bravedns.iab.BillingBackendClient
import com.bernaferrari.bravedns.iab.InAppBillingHandler
import com.bernaferrari.bravedns.iab.PurchaseDetail
import com.bernaferrari.bravedns.service.EncryptedFileManager
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.ProxyManager
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Constants.Companion.RPN_PROXY_FOLDER_NAME
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.util.Utilities
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.IPMetadata
import com.celzero.firestack.backend.RouterStats
import com.celzero.firestack.backend.RpnEntitlement
import com.celzero.firestack.backend.RpnServers
import com.celzero.firestack.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

object RpnProxyManager : KoinComponent {
    private val applicationContext: Context by inject()

    private const val TAG = "RpnMgr"

    private val db: RpnProxyRepository by inject()
    private val countryConfigRepo: CountryConfigRepository by inject()
    private val persistentState by inject<PersistentState>()
    private val billingBackendClient by inject<BillingBackendClient>()
    private val subscriptionStatusRepository: SubscriptionStatusRepository by inject()

    private const val WIN_ID = 4
    private const val WIN_NAME = "WIN"
    private const val WIN_ENTITLEMENT_FILE_NAME = "win_response.json"
    private const val WIN_STATE_FILE_NAME = "win_state.json"
    const val MAX_WIN_SERVERS = 5
    const val AUTO_SERVER_ID   = "AUTO"
    const val AUTO_COUNTRY_CODE = "AUTO"

    private val winRegistrationMutex = Mutex()

    private val ssidController by lazy { RpnSsidController() }
    private val hopSelector by lazy {
        RpnHopSelector(
            rpnWinPrefix = Backend.RpnWin,
            autoServerId = AUTO_SERVER_ID,
            matchesSsid = ssidController::matchesSsidList,
        )
    }
    private val entitlementStore by lazy {
        RpnWinEntitlementStore(
            context = applicationContext,
            db = db,
            winId = WIN_ID,
            winName = WIN_NAME,
            stateFileName = WIN_STATE_FILE_NAME,
            entitlementFileName = WIN_ENTITLEMENT_FILE_NAME,
            folderName = RPN_PROXY_FOLDER_NAME,
            extractWs = ::extractWsObject,
        )
    }
    private val winServerController by lazy {
        RpnWinServerController(
            countryConfigRepo = countryConfigRepo,
            autoServerId = AUTO_SERVER_ID,
            autoCountryCode = AUTO_COUNTRY_CODE,
            addWinServer = { VpnController.addNewWinServer(it) },
            removeWinServer = { VpnController.removeWinServer(it) },
            handleRpnHop = { key, changed -> VpnController.handleRpnHop(key, changed) },
            refreshProxies = { VpnController.refreshOrPauseOrResumeOrReAddProxies() },
            notifyConnectionMonitor = { VpnController.notifyConnectionMonitor(enforcePolicyChange = it) },
            notifyServerAdded = ::notifyServerAddedToTun,
            clearServerMeta = ::clearServerMeta,
            removeProxyMapping = { ProxyManager.removeProxyId(it) },
            fetchLocations = { fetchAndConstructWinLocations() },
            retryLocationFetch = { retryLocationFetch() },
        )
    }

    /**
     * Emits a list of [CountryConfig] objects that were selected by the user but are
     * no longer present in the server list returned by the tunnel after an update.
     */
    private val _serverRemovedEvent = MutableSharedFlow<List<CountryConfig>>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val serverRemovedEvent: SharedFlow<List<CountryConfig>> = _serverRemovedEvent.asSharedFlow()

    data class ServerKeyMeta(
        val selectedAt: Long
    )

    // Tracks per-server-key metadata (selection time, last tunnel-start ts, cached client IPs).
    // Populated from CountryConfig.lastModified on startup; overwritten on every runtime
    // enable/disable; sinceTs / ip[46]Meta updated by RpnConfigDetailActivity after each poll.
    private val serverKeyMeta = ConcurrentHashMap<String, ServerKeyMeta>()

    private val subscriptionStateMachine: SubscriptionStateMachineV2 by inject()
    private val stateObserverJob = SupervisorJob()
    private val stateObserverScope = CoroutineScope(Dispatchers.IO + stateObserverJob)
    private const val CD_DEFAULT_DNS = "tls://p0.freedns.controld.com"
    private const val CD_PRIVACY_DNS = "tls://p2.freedns.controld.com"
    private const val CD_PARENTAL_FILTER_DNS = "tls://family.freedns.controld.com"
    private const val CD_SECURITY_FILTER_DNS = "tls://p1.freedns.controld.com"

    init {
        io {
            try {
                load()
                startStateObserver()
                Logger.i(
                    LOG_TAG_PROXY,
                    "$TAG; RpnProxyManager initialized with state machine integration"
                )
            } catch (e: Exception) {
                Logger.e(LOG_TAG_PROXY, "$TAG; error during initialization: ${e.message}", e)
            }
        }
    }

    enum class RpnTunMode(val id: Int) {
        NONE(Settings.AutoModeLocal),
        ANTI_CENSORSHIP(Settings.AutoModeHybrid),
        HIDE_IP(Settings.AutoModeRemote);

        companion object {
            fun fromId(id: Int) = entries.first { it.id == id }

            fun getTunModeForAuto(): Int {
                return when (rpnMode()) {
                    RpnMode.NONE -> NONE.id
                    RpnMode.ANTI_CENSORSHIP -> ANTI_CENSORSHIP.id
                    RpnMode.HIDE_IP -> HIDE_IP.id
                }
            }
        }
    }

    enum class RpnMode(val id: Int) {
        NONE(0),
        ANTI_CENSORSHIP(1),
        HIDE_IP(2);

        companion object {
            fun fromId(id: Int) = RpnMode.entries.first { it.id == id }
        }

        fun isAntiCensorship() = this == ANTI_CENSORSHIP

        fun isHideIp() = this == HIDE_IP

        fun isNone() = this == NONE
    }

    enum class RpnState(val id: Int) {
        DISABLED(0),
        PAUSED(1), // not used in the app, but kept for future use
        ENABLED(2);

        companion object {
            fun fromId(id: Int) = RpnState.entries.first { it.id == id }
        }

        fun isEnabled() = this == ENABLED
        fun isPaused() = this == PAUSED
        fun isInactive() = this == DISABLED
    }

    /**
     * Server-side DNS filter options. [id] is stable (persisted); do not reorder.
     *
     * The Go tun SetDNSConfig accepts a CSV of filter presets:
     *   "family", "security", "privacy", "default"
     * Use [tunTypesFromSet] to build the CSV for a multi-select combination and
     * [setFromCsv] to restore a persisted CSV back to a [Set] of modes.
     */
    enum class DnsMode(val id: Int, val url: String, val tunType: String) {
        // presets from go-tun, see below
        // SetDNSConfig sets the DNS filter preset configuration. v is a csv of filter presets:
        // "family", "security", "social", "privacy", "default".
        DEFAULT(0,  CD_DEFAULT_DNS, "default"),
        PRIVACY(1,  CD_PRIVACY_DNS, "privacy"),
        PARENTAL(2, CD_PARENTAL_FILTER_DNS, "family"),
        SECURITY(3, CD_SECURITY_FILTER_DNS, "security");

        companion object {
            fun fromId(id: Int): DnsMode  = entries.firstOrNull { it.id == id }  ?: DEFAULT
            fun fromUrl(url: String): DnsMode = entries.firstOrNull { it.url == url } ?: DEFAULT

            /**
             * Parses a comma-separated string of [DnsMode.tunType] values (e.g.`"privacy,family"`)
             * back into a [Set] of [DnsMode] entries. Unknown tokens are silently ignored.
             * Returns a set containing [DEFAULT] if [csv] is blank or yields no valid entries.
             */
            fun setFromCsv(csv: String): Set<DnsMode> =
                RpnDnsCsv.setFromCsv(csv, entries, DnsMode::tunType, DEFAULT)

            /**
             * Converts a [Set] of [DnsMode] entries to a comma-separated string of their
             * [tunType] values suitable for [PersistentState.rpnDnsTunTypes] and for passing
             * to the Go tunnels SetDNSConfig.
             *
             * The entries are sorted by [id] so the string is deterministic.
             *
             * An empty [modes] set is treated as [DEFAULT].
             */
            fun tunTypesFromSet(modes: Set<DnsMode>): String =
                RpnDnsCsv.tunTypesFromSet(modes, DnsMode::id, DnsMode::tunType, DEFAULT)
        }
    }

    fun isRpnActive(): Boolean {
        val isEnabled = rpnState().isEnabled()
        val isActive = !rpnMode().isNone()
        return isEnabled && isActive
    }

    fun isRpnEnabled() = RpnState.fromId(persistentState.rpnState).isEnabled()

    fun rpnMode() = RpnMode.fromId(persistentState.rpnMode)

    fun rpnState() = RpnState.fromId(persistentState.rpnState)

    fun setRpnMode(mode: RpnMode) {
        if (rpnMode() == mode) {
            Logger.i(LOG_TAG_PROXY, "$TAG; rpn mode already set to ${mode.name}, skipping")
            return
        }
        persistentState.rpnMode = mode.id
        Logger.i(LOG_TAG_PROXY, "$TAG; rpn mode set to ${mode.name}")
    }

    fun setRpnState(state: RpnState) {
        if (rpnState() == state) {
            Logger.i(LOG_TAG_PROXY, "$TAG; rpn state already set to ${state.name}, skipping")
            return
        }
        persistentState.rpnState = state.id
        Logger.i(LOG_TAG_PROXY, "$TAG; rpn state set to ${state.name}")
    }

    fun deactivateRpn(reason: String = "manual deactivation") {
        Logger.i(LOG_TAG_PROXY, "$TAG; deactivating RPN, reason: $reason, current state: ${rpnState().name}")
        if (persistentState.rpnState == RpnState.DISABLED.id) {
            Logger.i(LOG_TAG_PROXY, "$TAG; rpn already deactivated, skipping")
            return
        }

        // no need to check the state, as user can manually deactivate RPN
        persistentState.rpnState = RpnState.DISABLED.id
        serverKeyMeta.clear()
    }

    /**
     * Stops proxying by setting the RPN mode to [RpnMode.NONE].
     *
     * This is a soft-stop: the RPN remains in [RpnState.ENABLED] so it can be
     * restarted without re-authentication. Use [deactivateRpn] for a full
     * tear-down when the subscription is revoked.
     */
    suspend fun stopProxy() {
        Logger.i(LOG_TAG_PROXY, "$TAG; stopProxy: current mode=${rpnMode()}, state=${rpnState()}")
        if (!isRpnActive()) {
            Logger.i(LOG_TAG_PROXY, "$TAG; stopProxy: already stopped, skipping")
            return
        }
        setRpnMode(RpnMode.NONE)
        VpnController.unregisterWin()
        Logger.i(LOG_TAG_PROXY, "$TAG; stopProxy: proxy routing stopped (mode=NONE, state=${rpnState()})")
    }

    /**
     * Restarts proxying after a [stopProxy] call.
     *
     * Returns `true` if the proxy was started, `false` if there is no valid
     * subscription (caller should surface an appropriate error).
     *
     * The mode is restored to [RpnMode.ANTI_CENSORSHIP] (the default active
     * routing mode).  This mirrors what [activateRpn] sets on first activation.
     */
    suspend fun startProxy() {
        Logger.i(LOG_TAG_PROXY, "$TAG; startProxy: current mode=${rpnMode()}, state=${rpnState()}")
        if (isRpnActive()) {
            Logger.i(LOG_TAG_PROXY, "$TAG; startProxy: proxy already running (mode=${rpnMode()})")
            VpnController.handleRpnProxies()
            return
        }
        setRpnMode(RpnMode.ANTI_CENSORSHIP)
        setRpnState(RpnState.ENABLED)
        VpnController.handleRpnProxies()
        Logger.i(LOG_TAG_PROXY, "$TAG; startProxy: proxy routing started (mode=ANTI_CENSORSHIP)")
    }


    /**
     * Activates the RPN proxy for the given [purchase].
     *
     * @param purchase    Purchase from Play Billing that carries the developer payload.
     * @param newPayload  Optional override payload (e.g. from a fresh server entitlement query).
     */
    suspend fun activateRpn(purchase: PurchaseDetail, newPayload: String? = null) {

        val alreadyEnabled = persistentState.rpnState == RpnState.ENABLED.id

        // Priority: explicit newPayload > purchase.payload > DB developerPayload
        val candidatePayload = when {
            !newPayload.isNullOrEmpty() -> {
                Logger.i(LOG_TAG_PROXY, "$TAG; activateRpn: using explicit newPayload")
                newPayload
            }
            purchase.payload.isNotEmpty() -> {
                Logger.i(LOG_TAG_PROXY, "$TAG; activateRpn: using purchase.payload")
                if (DEBUG) Logger.d(LOG_TAG_PROXY, "$TAG; activateRpn: purchase.payload content: ${purchase.payload}")
                purchase.payload
            }
            else -> {
                // Last resort: pull from DB developerPayload stored by the state machine
                Logger.w(LOG_TAG_PROXY, "$TAG; activateRpn: purchase.payload is empty, trying DB developerPayload")
                try {
                    subscriptionStatusRepository.getCurrentSubscription()?.developerPayload.orEmpty()
                } catch (e: Exception) {
                    Logger.e(LOG_TAG_PROXY, "$TAG; activateRpn: failed to read DB developerPayload: ${e.message}", e)
                    ""
                }
            }
        }

        // Even if rpnState is already ENABLED, the entitlement file or in-memory
        // winConfig may be absent (app restart, storage cleared, first login on new device).
        // storeWinEntitlement is idempotent; calling it again with the same data is safe.
        if (candidatePayload.isNotEmpty()) {
            try {
                storeWinEntitlement(candidatePayload)
            } catch (e: Exception) {
                Logger.e(LOG_TAG_PROXY, "$TAG; activateRpn: storeWinEntitlement threw: ${e.message}", e)
                // Non-fatal: continue activation; registerProxy will use getWinEntitlement as fallback
            }
        } else {
            Logger.w(LOG_TAG_PROXY, "$TAG; activateRpn: no payload available, entitlement file will NOT be updated")
        }

        if (alreadyEnabled) {
            // RPN is already enabled; entitlement refresh was the only needed action.
            Logger.i(LOG_TAG_PROXY, "$TAG; activateRpn: rpn already activated, entitlement refreshed, skipping state change")
            return
        }

        // Check if current state allows RPN activation
        if (!subscriptionStateMachine.hasValidSubscription()) {
            val currentState = subscriptionStateMachine.getCurrentState()
            Logger.w(LOG_TAG_PROXY, "$TAG; activateRpn: cannot activate RPN - no valid subscription, current state: ${currentState.name}")
            return
        }

        // Additional validation, ensure purchase has required fields
        if (purchase.productId.isEmpty()) {
            Logger.e(LOG_TAG_PROXY, "$TAG; activateRpn: cannot activate RPN - purchase has empty productId")
            return
        }

        try {
            persistentState.rpnState = RpnState.ENABLED.id
            VpnController.handleRpnProxies()
            Logger.i(
                LOG_TAG_PROXY,
                "$TAG; activateRpn: rpn activated, mode: ${rpnMode()}, product: ${purchase.productId}"
            )
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; activateRpn: error activating RPN: ${e.message}", e)
            persistentState.rpnState = RpnState.DISABLED.id
            throw e
        }
    }

    suspend fun getSessionTokenFromPayload(payload: String): String {
        // "developerPayload":"{\"ws\":{\"cid\":\"aa95f04efcb19a54c7605a02e5dd0b435906b993d12bec031a60f3f1272f4f0e\",\"sessiontoken\":\"22695:4:1752256088:524537c17**c146ba3404af:023f958b6c194**fe6885d3e57322\",\"expiry\":\"2025-08-11T00:00:00.000Z\",\"status\":\"valid\"}}"
        try {
            val ws = extractWsObject(payload) ?: return ""
            val ent = VpnController.getEntitlementDetails(ws, billingBackendClient.getDeviceId())
            val sessionToken = ent?.token()
            Logger.i(LOG_TAG_PROXY, "$TAG; session token parsed from payload? ${sessionToken?.isNotEmpty()}")
            return sessionToken ?: ""
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; error parsing session token from payload($payload): ${e.message}", e)
        }
        return ""
    }

    suspend fun getExpiryFromPayload(payload: String): Long? {
        if (payload.isEmpty()) return null
        try {
            val ws = extractWsObject(payload) ?: return null
            val ent = VpnController.getEntitlementDetails(ws, billingBackendClient.getDeviceId())
            val timestamp = ent?.expiry()
            Logger.i(LOG_TAG_PROXY, "$TAG; expiry parsed from payload: $timestamp")
            return timestamp
        } catch (e: Exception) {
            Logger.w(LOG_TAG_PROXY, "$TAG; error parsing expiry from payload: ${e.message}")
        }
        return null
    }

    suspend fun getEntitlementDetails(): RpnEntitlement? {
        val entitlement = getWinEntitlement()
        if (entitlement == null) {
            Logger.d(LOG_TAG_PROXY, "$TAG; getEntitlementDetails: null entitlement, querying server for refresh")
            return null
        }

        return try {
            val entitlement = VpnController.getEntitlementDetails(getWinEntitlement(), billingBackendClient.getDeviceId())
            return entitlement
        } catch (e: Exception) {
            Logger.w(LOG_TAG_PROXY, "$TAG; getEntitlementDetails: ${e.message}")
            null
        }
    }

    suspend fun getIsTestEntitlement(retryAttempt: Int = 0): Boolean {
        return try {
            val entitlement = VpnController.getEntitlementDetails(getWinEntitlement(), billingBackendClient.getDeviceId())
            // RpnEntitlement.test() returns Boolean: true = test, false = production
            if (entitlement == null) {
                Logger.d(LOG_TAG_PROXY, "$TAG; getIsTestEntitlement: null entitlement, querying server for refresh, attempt: $retryAttempt")
                return persistentState.appTestMode
            }
            entitlement.test()
        } catch (e: Exception) {
            Logger.w(LOG_TAG_PROXY, "$TAG; getIsTestEntitlement: ${e.message}")
            persistentState.appTestMode
        }
    }


    suspend fun consumePurchaseIfTest() {
        val mname = "consumePurchaseInTest"
        val purchase = subscriptionStateMachine.getSubscriptionData()?.purchaseDetail
        if (purchase == null) {
            Logger.w(LOG_IAB, "$TAG $mname: no purchase to consume")
            return
        }
        Logger.i(LOG_IAB, "$TAG $mname: consuming purchase token=${purchase.purchaseToken.take(8)}")
        if (!getIsTestEntitlement()) {
            Logger.i(LOG_IAB, "$TAG; $mname: not test entitlement - skipping")
            return
        }

        callConsumeApi(purchase)
    }

    private suspend fun callConsumeApi(purchase: PurchaseDetail) {
        val mname = "callConsumeApi"
        if (purchase.accountId.isBlank()) {
            Logger.e(LOG_IAB, "$TAG; $mname: accountId blank for token=${purchase.purchaseToken.take(8)}, skipping")
            return
        }
        val sku = purchase.productId
        Logger.i(LOG_IAB, "$TAG; $mname: delegating to BillingServerRepository for " +
                "token=${purchase.purchaseToken.take(8)}, sku=$sku, accLen=${purchase.accountId.length}")
        // purchase.deviceId holds only the sentinel indicator, always fetch the real ID from SecureIdentityStore.
        val deviceId = billingBackendClient.getDeviceId()
        val success = billingBackendClient.consumePurchase(purchase.accountId, deviceId, sku, purchase.purchaseToken)
        if (success) {
            Logger.i(LOG_IAB, "$TAG; $mname: consume succeeded for token=${purchase.purchaseToken.take(8)}")
        } else {
            Logger.w(LOG_IAB, "$TAG; $mname: consume failed for token=${purchase.purchaseToken.take(8)} (will retry next cycle)")
        }
    }

    /**
     * Represents the outcome of a full RPN reset operation.
     */
    sealed class ResetResult {
        /** All steps completed successfully. */
        object Success : ResetResult()
        /** One of the required steps failed; [reason] is a human-readable description. */
        data class Failure(val reason: String) : ResetResult()
    }

    /**
     * Performs a full RPN reset:
     *  1. Unregisters the current WIN registration from the tunnel.
     *  2. Fetches a fresh entitlement from the server.
     *  3. Stores the new entitlement (replacing the old one).
     *  4. Re-registers WIN in the tunnel with the fresh entitlement.
     *  5. Force-fetches the server list and syncs it to DB/cache.
     *
     * Returns [ResetResult.Success] when all steps complete, or
     * [ResetResult.Failure] with a reason string if a fatal step fails.
     */
    suspend fun resetAndRefetchRpn(): ResetResult {
        Logger.i(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: starting full reset")

        try {
            VpnController.unregisterWin()
            Logger.i(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: unregistered from tunnel")
        } catch (e: Exception) {
            Logger.w(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: unregister failed (non-fatal): ${e.message}")
        }

        val sub = try {
            subscriptionStatusRepository.getCurrentSubscription()
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: failed to read subscription: ${e.message}", e)
            return ResetResult.Failure("Failed to read subscription details")
        }

        if (sub == null) {
            Logger.w(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: no active subscription found")
            return ResetResult.Failure("No active subscription found")
        }

        val accountId = sub.accountId
        val purchaseToken = sub.purchaseToken
        if (accountId.isEmpty() || purchaseToken.isEmpty()) {
            Logger.w(
                LOG_TAG_PROXY,
                "$TAG; resetAndRefetchRpn: incomplete account info " +
                    "(accountEmpty=${accountId.isEmpty()}, tokenEmpty=${purchaseToken.isEmpty()})"
            )
            return ResetResult.Failure("Account information is incomplete")
        }

        val deviceId = billingBackendClient.getDeviceId(accountId)

        val fakePurchase = PurchaseDetail(
            productId = sub.productId,
            planId = sub.planId,
            productTitle = sub.productTitle,
            planTitle = sub.productTitle,
            state = sub.state,
            purchaseToken = purchaseToken,
            productType = if (sub.productId.contains("onetime", ignoreCase = true))
                BillingClient.ProductType.INAPP else BillingClient.ProductType.SUBS,
            purchaseTime = "",
            purchaseTimeMillis = sub.purchaseTime,
            isAutoRenewing = false,
            accountId = accountId,
            deviceId = if (deviceId.isNotBlank()) SubscriptionStatus.DEVICE_ID_INDICATOR else "",
            payload = sub.developerPayload,
            expiryTime = sub.billingExpiry,
            status = sub.status,
            windowDays = sub.windowDays,
            orderId = sub.orderId
        )

        val updatedPurchase = try {
            InAppBillingHandler.queryEntitlementFromServer(accountId, deviceId, fakePurchase)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: entitlement query failed: ${e.message}", e)
            return ResetResult.Failure("Failed to fetch entitlement from server")
        }

        if (updatedPurchase.payload.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: server returned empty payload")
            return ResetResult.Failure("Server returned an empty entitlement")
        }

        try {
            storeWinEntitlement(updatedPurchase.payload)
            Logger.i(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: new entitlement stored")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: storeWinEntitlement failed: ${e.message}", e)
            return ResetResult.Failure("Failed to store new entitlement")
        }

        // Update state machine with fresh payload (non-fatal)
        try {
            val subsData = subscriptionStateMachine.getSubscriptionData()
            if (subsData != null) {
                subsData.subscriptionStatus.developerPayload = updatedPurchase.payload
                val updatedSubsData = subsData.copy(purchaseDetail = updatedPurchase)
                subscriptionStateMachine.stateMachine.updateData(updatedSubsData)
                Logger.i(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: state machine payload updated")
            }
        } catch (e: Exception) {
            Logger.w(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: state machine update failed (non-fatal): ${e.message}")
        }

        val entitlementBytes = getWinEntitlement()
        val prevRegistrationBytes = getWinExistingData()
        val registrationBytes = try {
            VpnController.registerAndFetchWinConfig(entitlementBytes, billingBackendClient.getDeviceId())
                ?: VpnController.registerAndFetchWinConfig(prevRegistrationBytes, billingBackendClient.getDeviceId())
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: tunnel registration failed: ${e.message}", e)
            return ResetResult.Failure("Failed to register with tunnel")
        }
        updateWinConfigState(registrationBytes)
        Logger.i(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: re-registered with tunnel")

        // Clear all user-specific server state (selections, favourites, selection counts)
        // before syncing so the rebuilt cache starts from a clean slate.
        try {
            countryConfigRepo.resetUserSelections()
            // Mirror the DB reset into the in-memory cache immediately so nothing races
            // between the DB write and the cache rebuild inside syncWinServers().
            winServerController.clearUserSelectionsInCache()
            Logger.i(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: user selections cleared from DB and cache")
        } catch (e: Exception) {
            Logger.w(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: resetUserSelections failed (non-fatal): ${e.message}")
        }

        // Restore configuration persistent-state values to their defaults.
        persistentState.rpnDnsTunTypes = DnsMode.DEFAULT.tunType
        persistentState.rpnConfigHandlingManual = false
        persistentState.rpnAlwaysChangeIdentity = false
        persistentState.rpnPort = 0
        Logger.i(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: persistent-state config restored to defaults")

        val (servers, _) = fetchAndConstructWinLocations()

        if (servers.isNotEmpty()) {
            syncWinServers(servers)
            Logger.i(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: synced ${servers.size} servers")
        } else {
            Logger.w(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: no servers returned after reset")
        }

        Logger.i(LOG_TAG_PROXY, "$TAG; resetAndRefetchRpn: complete, servers=${servers.size}")
        return ResetResult.Success
    }

    /**
     * Update both billingExpiry and accountExpiry for the current active subscription.
     *
     * Use this after a successful acknowledgement from the server that returns updated
     * expiry values for the billing period and the account-access period.
     *
     * @param newBillingExpiry  New billing-period expiry epoch-millis (0 = no change).
     * @param newAccountExpiry  New account-access expiry epoch-millis (0 = no change).
     * @return `true` if all requested updates succeeded.
     */
    suspend fun updateBillingAndAccountExpiry(newBillingExpiry: Long, newAccountExpiry: Long): Boolean {
        if (newBillingExpiry <= 0L && newAccountExpiry <= 0L) {
            Logger.w(LOG_TAG_PROXY, "$TAG; updateBillingAndAccountExpiry: both values are 0, skipping")
            return false
        }
        return try {
            val current = subscriptionStatusRepository.getCurrentSubscription()
            if (current == null) {
                Logger.w(LOG_TAG_PROXY, "$TAG; updateBillingAndAccountExpiry: no active subscription found")
                return false
            }
            val now = System.currentTimeMillis()
            var ok = true
            if (newBillingExpiry > 0L) {
                val r = subscriptionStatusRepository.updateBillingExpiry(current.id, newBillingExpiry, now)
                if (r <= 0) {
                    Logger.e(LOG_TAG_PROXY, "$TAG; updateBillingAndAccountExpiry: billingExpiry update failed for id=${current.id}")
                    ok = false
                }
            }
            if (newAccountExpiry > 0L) {
                val r = subscriptionStatusRepository.updateAccountExpiry(current.id, newAccountExpiry, now)
                if (r <= 0) {
                    Logger.e(LOG_TAG_PROXY, "$TAG; updateBillingAndAccountExpiry: accountExpiry update failed for id=${current.id}")
                    ok = false
                }
            }
            if (ok) Logger.i(LOG_TAG_PROXY, "$TAG; updateBillingAndAccountExpiry: billing=$newBillingExpiry, account=$newAccountExpiry for id=${current.id}")
            ok
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; updateBillingAndAccountExpiry: error: ${e.message}", e)
            false
        }
    }

    /**
     * Extracts the "ws" JSON object from a payload string.
     *
     * The payload can arrive in two shapes:
     *
     * Shape 1 – full purchase originalJson (from Play Billing's Purchase.getOriginalJson()).
     *   The "ws" object is nested inside the "developerPayload" string value which is itself
     *   a JSON string that must be parsed a second time:
     *   { ..., "developerPayload": "{\"ws\":{...}}", ... }
     *
     * Shape 2 – already-extracted developerPayload string stored in the DB.
     *   The "ws" object is top-level inside this string:
     *   { "ws": { "cid": "...", "sessiontoken": "...", "expiry": "...", ... } }
     *
     * Both shapes are handled: try top-level "ws" first; if absent, look inside
     * "developerPayload" for a nested "ws".
     */
    fun extractWsObject(payload: String): ByteArray? {
        if (payload.isEmpty()) return null
        return try {
            val json = JSONObject(payload)

            // Shape 2: "ws" is top-level (already-extracted developerPayload).
            val ws = json.optJSONObject("ws")
            if (ws != null) return ws.toString().toByteArray()

            // Shape 1: "ws" is embedded inside the "developerPayload" string value.
            val devPayloadStr = json.optString("developerPayload", "")
            if (devPayloadStr.isNotEmpty()) {
                val devPayloadJson = JSONObject(devPayloadStr)
                val wsFromDev = devPayloadJson.optJSONObject("ws")
                if (wsFromDev != null) return wsFromDev.toString().toByteArray()
            }

            Logger.w(LOG_TAG_PROXY, "$TAG; extractWsObject: ws not found in payload")
            null
        } catch (e: Exception) {
            Logger.w(LOG_TAG_PROXY, "$TAG; extractWsObject: failed to parse payload ($payload): ${e.message}")
            null
        }
    }

    suspend fun storeWinEntitlement(payload: String) {
        entitlementStore.storeWinEntitlement(payload)
    }

    fun getRpnProductId(): String {
        return subscriptionStateMachine.getSubscriptionData()?.purchaseDetail?.productId.orEmpty()
    }

    enum class RpnType(val id: Int) {
        EXIT(0),
        WIN(1);

        companion object {
            fun fromId(id: Int) = entries.first { it.id == id }
        }
    }

    data class RpnProps(val id: String, val status: Int, val type: String, val addr: String, val created: Long, val expires: Long, val who: String, val locations: RpnServers) {
        override fun toString(): String {
            val cts = getTime(created)
            val ets = getTime(expires)
            val s = applicationContext.getString(UIUtils.getProxyStatusStringRes(status))
            return "id = $id\nstatus = $s\ntype = $type\naddr = $addr\ncreated = $cts\nexpires = $ets\nwho = $who\nlocations = $locations"
        }
    }

    data class WinProxyDetails(
        val countryCode: String,
        val name: String,
        val address: String,
        val who: String,
        val latencyMs: Int?,
        val lastConnectedMs: Long?,
        val isActive: Boolean
    )

    suspend fun getWinProxyDetails(countryCode: String): WinProxyDetails? {
        val normalized = countryCode.trim().uppercase()
        if (normalized.isBlank()) return null
        val server = getWinServers().firstOrNull { it.cc.equals(normalized, ignoreCase = true) }
            ?: return null
        val row = db.getProxyById(WIN_ID)
        return WinProxyDetails(
            countryCode = server.cc,
            name = server.name.ifBlank { server.city.ifBlank { WIN_NAME } },
            address = server.address,
            who = "",
            latencyMs = row?.latency?.takeIf { it > 0 },
            lastConnectedMs = row?.lastRefreshTime?.takeIf { it > 0L },
            isActive = row?.isActive ?: server.isActive
        )
    }

    private fun getTime(time: Long): String {
        return  Utilities.convertLongToTime(time, Constants.TIME_FORMAT_4)
    }

    suspend fun load(): Int {
        // need to read the filepath from database and load the file
        // there will be an entry in the database for each RPN proxy
        val rp = try {
            db.getAllProxies()
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; init load, failed to get proxies from DB: ${e.message}", e)
            emptyList()
        }
        Logger.i(LOG_TAG_PROXY, "$TAG; init load, db size: ${rp.size}")
        rp.forEach {
            try {
                if (it.configPath.isEmpty() && it.id != WIN_ID) {
                    Logger.w(LOG_TAG_PROXY, "$TAG; load, config path is empty for ${it.name}")
                    return@forEach
                }

                val cfgFile = File(it.configPath)
                if (!cfgFile.exists() && it.id != WIN_ID) { // win proxy is handled differently
                    Logger.w(LOG_TAG_PROXY, "$TAG; load, file not found: ${it.configPath} for ${it.name}")
                    return@forEach
                }
                when (it.id) {
                    WIN_ID -> {
                        // read the win entitlement file
                        if (it.serverResPath.isEmpty()) {
                            Logger.w(LOG_TAG_PROXY, "$TAG; load, win serverResPath is empty")
                            return@forEach
                        }
                        val entitlementFile = File(it.serverResPath)
                        val entitlement = if (entitlementFile.exists()) {
                            try {
                                EncryptedFileManager.readByteArray(applicationContext, entitlementFile)
                            } catch (e: Exception) {
                                Logger.e(LOG_TAG_PROXY, "$TAG; load, error reading win entitlement file: ${e.message}")
                                byteArrayOf()
                            }
                        } else {
                            Logger.w(LOG_TAG_PROXY, "$TAG; load, win entitlement file not found: ${it.serverResPath}")
                            byteArrayOf()
                        }

                        Logger.i(LOG_TAG_PROXY, "$TAG; load, win entitlement loaded, size: ${entitlement.size} bytes")

                        val state = if (cfgFile.exists()) {
                            try {
                                EncryptedFileManager.readByteArray(applicationContext, cfgFile)
                            } catch (e: Exception) {
                                Logger.e(LOG_TAG_PROXY, "$TAG; load, error reading win state file (${cfgFile.absolutePath}): ${e.message}")
                                byteArrayOf()
                            }
                        } else {
                            Logger.d(LOG_TAG_PROXY, "$TAG; load, win state file not found: ${it.configPath}")
                            byteArrayOf()
                        }

                        Logger.i(LOG_TAG_PROXY, "$TAG; load, win state loaded, size: ${state.size} bytes")
                    }
                }
            } catch (e: Exception) {
                Logger.w(LOG_TAG_PROXY, "$TAG; err loading rpn proxy: ${it.name}, ${e.message}")
            }
        }

        // Populate WIN servers cache from DB at startup
        try {
            // Ensure AUTO server exists in database
            ensureAutoServerExists()

            val dbServers = countryConfigRepo.getAllConfigs()
            winServerController.replaceCache(dbServers)
            Logger.i(LOG_TAG_PROXY, "$TAG; load: cached ${dbServers.size}, WIN servers from DB (AUTO server ensured)")
        } catch (e: Exception) {
            Logger.w(LOG_TAG_PROXY, "$TAG; load: failed to cache WIN servers: ${e.message}")
        }

        return rp.size
    }

    // This function is called from RpnProxiesUpdateWorker
    suspend fun registerProxy(type: RpnType): Boolean {
        // in case of update failure, call register with null
        when (type) {
            RpnType.WIN -> {
                // check if state is there if not, fetch the entitlement
                var bytes = getWinExistingData() // fetch existing win state
                if (bytes == null) {
                    Logger.i(LOG_TAG_PROXY, "$TAG; win state is null, fetching entitlement")
                    bytes = getWinEntitlement()
                }
                if (bytes == null || bytes.isEmpty()) {
                    // This handles the case where:
                    //   (a) the entitlement file was never written (race between activateRpn
                    //       and registerProxy on first launch), OR
                    //   (b) external storage was cleared / the file was deleted.
                    // We pull the developerPayload from the DB subscription row (written by
                    // the state machine during handlePaymentSuccessful) and re-store it,
                    // which populates winConfig so the tunnel can be registered.
                    Logger.w(LOG_TAG_PROXY, "$TAG; registerProxy: both state and entitlement files absent, attempting DB payload recovery")
                    val dbPayload = try {
                        subscriptionStatusRepository.getCurrentSubscription()?.developerPayload.orEmpty()
                    } catch (e: Exception) {
                        Logger.e(LOG_TAG_PROXY, "$TAG; registerProxy: failed to read DB developerPayload: ${e.message}", e)
                        ""
                    }
                    if (dbPayload.isNotEmpty()) {
                        Logger.i(LOG_TAG_PROXY, "$TAG; registerProxy: found DB payload (len=${dbPayload.length}), re-storing entitlement")
                        try {
                            storeWinEntitlement(dbPayload)
                            bytes = getWinEntitlement()
                        } catch (e: Exception) {
                            Logger.e(LOG_TAG_PROXY, "$TAG; registerProxy: error re-storing entitlement from DB: ${e.message}", e)
                        }
                    }

                    if (bytes == null || bytes.isEmpty()) {
                        // DB also had no usable payload.  Try a live server query as last resort.
                        Logger.w(LOG_TAG_PROXY, "$TAG; registerProxy: DB payload also empty, querying server entitlement")
                        val sub = try { subscriptionStatusRepository.getCurrentSubscription() } catch (e: Exception) { null }
                        val accountId = sub?.accountId.orEmpty()
                        // sub.deviceId holds only the sentinel indicator "pip/identity.json"
                        val deviceId = billingBackendClient.getDeviceId(accountId)
                        val purchaseToken = sub?.purchaseToken.orEmpty()
                        if (accountId.isNotEmpty() && purchaseToken.isNotEmpty()) {
                            try {
                                val fakePurchase = PurchaseDetail(
                                    productId = sub?.productId.orEmpty(),
                                    planId = sub?.planId.orEmpty(),
                                    productTitle = sub?.productTitle.orEmpty(),
                                    planTitle = sub?.productTitle.orEmpty(),
                                    state = sub?.state ?: 0,
                                    purchaseToken = purchaseToken,
                                    productType = if ((sub?.productId.orEmpty()).contains("onetime", ignoreCase = true)) BillingClient.ProductType.INAPP else BillingClient.ProductType.SUBS,
                                    purchaseTime = "",
                                    purchaseTimeMillis = sub?.purchaseTime ?: 0L,
                                    isAutoRenewing = false,
                                    accountId = accountId,
                                    // Store only the sentinel
                                    // Callers that need the actual ID use billingBackendClient.getDeviceId().
                                    deviceId = if (deviceId.isNotBlank()) SubscriptionStatus.DEVICE_ID_INDICATOR else "",
                                    payload = "",
                                    expiryTime = sub?.billingExpiry ?: 0L,
                                    status = sub?.status ?: 0,
                                    windowDays = sub?.windowDays ?: 0,
                                    orderId = sub?.orderId.orEmpty()
                                )
                                val updated = InAppBillingHandler.queryEntitlementFromServer(accountId, deviceId, fakePurchase)
                                if (updated.payload.isNotEmpty()) {
                                    Logger.i(LOG_TAG_PROXY, "$TAG; registerProxy: server query succeeded, storing entitlement")
                                    storeWinEntitlement(updated.payload)
                                    bytes = getWinEntitlement()
                                }
                            } catch (e: Exception) {
                                Logger.e(LOG_TAG_PROXY, "$TAG; registerProxy: server entitlement query failed: ${e.message}", e)
                            }
                        }
                    }

                    if (bytes == null || bytes.isEmpty()) {
                        Logger.e(LOG_TAG_PROXY, "$TAG; registerProxy: win entitlement unavailable after all recovery attempts, cannot register")
                        return false
                    }
                }

                var wasAlreadyRegisteredByConcurrent = false
                val currBytes: ByteArray? = winRegistrationMutex.withLock {
                    if (VpnController.isWinRegistered()) {
                        Logger.i(LOG_TAG_PROXY, "$TAG; registerProxy: WIN already registered (concurrent-safe check), skipping tunnel call")
                        wasAlreadyRegisteredByConcurrent = true
                        null // early-exit sentinel; not treated as failure because flag is set
                    } else {
                        var regBytes = VpnController.registerAndFetchWinConfig(bytes, billingBackendClient.getDeviceId())
                        if (regBytes == null) {
                            // try registering with prev stored bytes
                            val prevRegistrationBytes = getWinExistingData()
                            Logger.w(LOG_TAG_PROXY, "$TAG; win registration failed with existing bytes, trying with prev bytes")
                            regBytes = VpnController.registerAndFetchWinConfig(prevRegistrationBytes, billingBackendClient.getDeviceId())
                        }
                        regBytes
                    }
                }
                if (wasAlreadyRegisteredByConcurrent) {
                    // A concurrent coroutine finished registration first; this call is a no-op.
                    Logger.i(LOG_TAG_PROXY, "$TAG; registerProxy: WIN registered by concurrent call, returning true")
                    return true
                }
                val ok = updateWinConfigState(currBytes)
                // Fetch servers from API and sync to database and cache
                val (servers, removedSelectedIds) = fetchAndConstructWinLocations()
                if (servers.isEmpty()) {
                    Logger.w(LOG_TAG_PROXY, "$TAG; no win servers found, retry")
                    retryLocationFetch()
                } else {
                    syncWinServers(servers)
                    // Notify about removed servers if any were selected
                    if (removedSelectedIds.isNotEmpty()) {
                        Logger.w(LOG_TAG_PROXY, "$TAG; ${removedSelectedIds.size} selected servers were removed from the list")
                        // Notification will be shown by ServerSelectionFragment when it detects the change
                    }
                }
                return ok
            }
            else -> {
                Logger.e(LOG_TAG_PROXY, "$TAG; err; invalid type for register: $type")
                return false
            }
        }
    }

    suspend fun retryLocationFetch() {
        for (i in 1..5) {
            Logger.i(LOG_TAG_PROXY, "$TAG; retrying to fetch win properties, attempt: $i")
            val (servers, removedSelectedIds) = fetchAndConstructWinLocations()
            if (servers.isNotEmpty()) {
                Logger.i(LOG_TAG_PROXY, "$TAG; win servers found after retry, attempt: $i, size: ${servers.size}")
                syncWinServers(servers)
                // Notify about removed servers if any were selected
                if (removedSelectedIds.isNotEmpty()) {
                    Logger.w(LOG_TAG_PROXY, "$TAG; retryLocationFetch: ${removedSelectedIds.size} selected servers were removed")
                }
                break
            }
            delay(1000L.milliseconds)
        }
    }

    /**
     * Get all WIN servers.
     * Prefers in-memory cache; falls back to DB; if empty (or only the synthetic AUTO entry
     * exists), fetches from the tunnel API, syncs DB, and updates cache.
     *
     * **Important:** AUTO is a local synthetic entry created by [ensureAutoServerExists].
     */
    suspend fun getWinServers(): List<CountryConfig> = winServerController.getWinServers()

    /** Persist a country-level favourite and keep every server in that country in sync. */
    suspend fun setCountryFavourite(cc: String, favourite: Boolean) {
        winServerController.setCountryFavourite(cc, favourite)
    }

    /** Most-used country codes, in descending user-selection order. */
    suspend fun getFrequentCountryCodes(limit: Int = 5): List<String> =
        countryConfigRepo.getTopFrequentCcs(limit)

    suspend fun getWinEntitlement(): ByteArray? = entitlementStore.getWinEntitlement()

    /**
     * Get current subscription data including purchase details and status.
     * Used by UI fragments to display subscription information.
     */
    fun getSubscriptionData(): SubscriptionStateMachineV2.SubscriptionData? {
        return try {
            subscriptionStateMachine.getSubscriptionData()
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; error getting subscription data: ${e.message}", e)
            null
        }
    }

    /**
     * Get current subscription state.
     * Used by UI to display the current state of the subscription.
     */
    fun getSubscriptionState(): SubscriptionStateMachineV2.SubscriptionState {
        return subscriptionStateMachine.getCurrentState()
    }

    fun getCurrentSubscription(): SubscriptionStateMachineV2.SubscriptionData? {
        return subscriptionStateMachine.getSubscriptionData()
    }

    /**
     * Returns the best available [PurchaseDetail] for the current subscription.
     * Returns `null` only when there is no subscription data at all.
     */
    fun getEffectivePurchaseDetail(): PurchaseDetail? {
        val data = subscriptionStateMachine.getSubscriptionData() ?: return null
        return data.purchaseDetail
            ?: data.subscriptionStatus
                .takeIf { it.purchaseToken.isNotEmpty() }
                ?.let {
                    Logger.i(LOG_TAG_PROXY, "$TAG; getEffectivePurchaseDetail: reconstructing from subscriptionStatus (token=${it.purchaseToken.take(8)})")
                    subscriptionStateMachine.createPurchaseDetailFromSubscription(it)
                }
    }

    suspend fun updateCancelledSubscription(accountId: String, purchaseToken: String): Boolean {
        if (purchaseToken.isEmpty() || accountId.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$TAG; err; purchaseToken is empty")
            return false
        }
        // TODO: perform the validation of purchaseToken and accountId with the subscription data
        handleUserCancellation()
        return true
    }

    suspend fun updateRevokedSubscription(accountId: String, purchaseToken: String): Boolean {
        if (purchaseToken.isEmpty() || accountId.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$TAG; err; purchaseToken is empty")
            return false
        }
        handleSubscriptionRevoked()
        return true
    }

    /**
     * Called when a purchase revocation error from the server includes a [linkedToken]
     * (the Google Play purchase token of an older, superseded purchase).
     *
     * This method:
     * 1. Looks up [linkedToken] in the local DB.
     * 2. Issues a `/g/ack` query for [linkedToken].
     * 3. If the server confirms the linked purchase is valid, reactivates it by driving
     *    [paymentSuccessful] in the state machine and re-enabling RPN access.
     *
     * The caller's original purchase remains unchanged regardless of the outcome; this
     * operation only acts on the linked purchase row.
     *
     * @return `true` if the linked purchase was found and successfully reactivated.
     */
    suspend fun tryReactivateLinkedPurchase(
        accountId: String,
        deviceId: String,
        linkedToken: String,
    ): Boolean {
        val mname = "tryReactivateLinkedPurchase"
        if (linkedToken.isBlank() || accountId.isBlank()) {
            Logger.w(LOG_TAG_PROXY, "$TAG; $mname: blank accountId or linkedToken, skipping")
            return false
        }

        // 1. Look up the linked purchase token in the local DB.
        val linkedSub = try {
            subscriptionStatusRepository.getByPurchaseToken(linkedToken)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; $mname: DB lookup failed for linkedToken=${linkedToken.take(8)}: ${e.message}", e)
            return false
        }

        if (linkedSub == null) {
            Logger.w(LOG_TAG_PROXY, "$TAG; $mname: linkedToken=${linkedToken.take(8)} not found in DB, cannot reactivate")
            return false
        }

        // 2. Build a PurchaseDetail from the DB row and query /g/ack for the linked token.
        val linkedPurchaseDetail = subscriptionStateMachine.createPurchaseDetailFromSubscription(linkedSub)
        val result = try {
            billingBackendClient.queryEntitlement(accountId, deviceId, linkedPurchaseDetail, linkedToken)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; $mname: entitlement query failed for linkedToken=${linkedToken.take(8)}: ${e.message}", e)
            return false
        }

        // 3. If the server confirms the linked purchase, reactivate it.
        return when (result) {
            is com.bernaferrari.bravedns.iab.QueryEntitlementResult.Success -> {
                val updatedPurchase = result.purchase
                Logger.i(LOG_TAG_PROXY, "$TAG; $mname: linkedToken=${linkedToken.take(8)} confirmed valid by server; reactivating")
                try {
                    subscriptionStateMachine.paymentSuccessful(updatedPurchase)
                    activateRpn(updatedPurchase)
                    Logger.i(LOG_TAG_PROXY, "$TAG; $mname: reactivation complete for linkedToken=${linkedToken.take(8)}, product=${updatedPurchase.productId}")
                    true
                } catch (e: Exception) {
                    Logger.e(LOG_TAG_PROXY, "$TAG; $mname: reactivation state update failed for linkedToken=${linkedToken.take(8)}: ${e.message}", e)
                    false
                }
            }
            is com.bernaferrari.bravedns.iab.QueryEntitlementResult.Failure -> {
                Logger.w(LOG_TAG_PROXY, "$TAG; $mname: linkedToken=${linkedToken.take(8)} also invalid per server (no linkedPurchaseId in response)")
                false
            }
            is com.bernaferrari.bravedns.iab.QueryEntitlementResult.Transient -> {
                Logger.w(LOG_TAG_PROXY, "$TAG; $mname: transient failure querying linkedToken=${linkedToken.take(8)}, not reactivating (fail-safe)")
                false
            }
            is com.bernaferrari.bravedns.iab.QueryEntitlementResult.Unauthorized -> {
                Logger.e(LOG_TAG_PROXY, "$TAG; $mname: 401 unauthorized for linkedToken=${linkedToken.take(8)}")
                false
            }
            is com.bernaferrari.bravedns.iab.QueryEntitlementResult.Conflict -> {
                Logger.w(LOG_TAG_PROXY, "$TAG; $mname: 409 conflict for linkedToken=${linkedToken.take(8)}")
                false
            }
            else -> {
                Logger.w(LOG_TAG_PROXY, "$TAG; $mname: unhandled result type for linkedToken=${linkedToken.take(8)}")
                false
            }
        }
    }

    /**
     * Called from [SubscriptionStateMachineV2.handlePaymentSuccessful] after the DB row is
     * written and the state machine has been updated.
     *
     * Responsibilities:
     *  1. Ensure a valid developer payload (entitlement token) is stored on disk so that
     *     [registerProxy] can find it even on subsequent app launches.
     *  2. If the purchase payload is absent or stale, query a fresh one from the server.
     *  3. Always call [activateRpn] to persist the entitlement file and set rpnState.
     */
    suspend fun processRpnPurchase(purchase: PurchaseDetail?, existingSubs: SubscriptionStatus): Boolean {
        if (purchase == null) {
            Logger.w(LOG_TAG_PROXY, "$TAG; processRpnPurchase: no purchase to process")
            try { subscriptionStateMachine.subscriptionExpired() } catch (e: Exception) {
                Logger.e(LOG_TAG_PROXY, "$TAG; processRpnPurchase: error notifying state machine: ${e.message}", e)
            }
            return false
        }

        if (purchase.productId.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$TAG; processRpnPurchase: productId empty")
            try { subscriptionStateMachine.purchaseFailed("Empty product ID", null) } catch (e: Exception) {
                Logger.e(LOG_TAG_PROXY, "$TAG; processRpnPurchase: error notifying state machine: ${e.message}", e)
            }
            return false
        }

        val accExpiry     = existingSubs.accountExpiry
        val billingExpiry = existingSubs.billingExpiry

        Logger.d(LOG_TAG_PROXY, "$TAG; processRpnPurchase: billingExpiry=$billingExpiry, accExpiry=$accExpiry, " +
                "payload?=${purchase.payload.isNotEmpty()}, dbPayload?=${existingSubs.developerPayload.isNotEmpty()}")

        // A valid payload must parse to a JSON object containing a non-empty "sessiontoken"
        // inside the "ws" block.  Empty / unparseable payloads must trigger a server query.
        val existingPayloadValid = isPayloadUsable(purchase.payload)
        val dbPayloadValid       = isPayloadUsable(existingSubs.developerPayload)

        val tenDaysMs = 10L * 24 * 60 * 60 * 1000   // 10 days in ms

        // We need a fresh server entitlement when:
        //   (a) purchase payload is not usable (empty or no sessiontoken), AND
        //   (b) DB developerPayload is also not usable, OR accountExpiry hasn't been confirmed
        //       from the server yet (0 = never queried, or lags > 10 days behind billingExpiry).
        val needsFreshEntitlement = !existingPayloadValid && !dbPayloadValid
                || (accExpiry == 0L && billingExpiry > 0L)
                || (billingExpiry > 0L && billingExpiry != Long.MAX_VALUE && accExpiry < billingExpiry - tenDaysMs)

        var effectivePurchase = purchase

        if (needsFreshEntitlement) {
            Logger.i(LOG_TAG_PROXY, "$TAG; processRpnPurchase: needs fresh entitlement " +
                    "(existingPayloadValid=$existingPayloadValid, dbPayloadValid=$dbPayloadValid, " +
                    "accExpiry=$accExpiry, billingExpiry=$billingExpiry)")

            val accountId = purchase.accountId.ifEmpty { existingSubs.accountId }
            // purchase.deviceId holds only the sentinel indicator (or is empty)
            // Always resolve the actual device ID from SecureIdentityStore via billingBackendClient.
            val deviceId = billingBackendClient.getDeviceId()
            if (accountId.isEmpty()) {
                Logger.w(LOG_TAG_PROXY, "$TAG; processRpnPurchase: accountId empty, cannot query server, using best available payload")
                // Fall through to activateRpn with whatever we have (may also check DB below)
            } else {
                try {
                    val updatedPurchase = InAppBillingHandler.queryEntitlementFromServer(accountId, deviceId, purchase)
                    val serverPayloadValid = isPayloadUsable(updatedPurchase.payload)

                    if (serverPayloadValid) {
                        Logger.i(LOG_TAG_PROXY, "$TAG; processRpnPurchase: server entitlement received for ${updatedPurchase.productId}")
                        effectivePurchase = updatedPurchase

                        // Persist the server-returned expiry
                        val serverExpiry = updatedPurchase.expiryTime
                        if (serverExpiry > 0L && serverExpiry != Long.MAX_VALUE && serverExpiry != accExpiry) {
                            Logger.i(LOG_TAG_PROXY, "$TAG; processRpnPurchase: persisting server expiry=$serverExpiry (was accExpiry=$accExpiry)")
                            updateBillingAndAccountExpiry(
                                newBillingExpiry = serverExpiry,
                                newAccountExpiry = serverExpiry
                            )
                        }

                        // Update state machine and DB with fresh payload
                        val subsData = subscriptionStateMachine.getSubscriptionData()
                        if (subsData != null) {
                            subsData.subscriptionStatus.developerPayload = updatedPurchase.payload
                            val updatedSubsData = subsData.copy(purchaseDetail = updatedPurchase)
                            subscriptionStateMachine.stateMachine.updateData(updatedSubsData)
                            subscriptionStatusRepository.updateDeveloperPayload(
                                subsData.subscriptionStatus.id,
                                updatedPurchase.payload,
                                System.currentTimeMillis()
                            )
                        } else {
                            Logger.w(LOG_TAG_PROXY, "$TAG; processRpnPurchase: subscription data is null, cannot update payload in state machine")
                        }
                    } else {
                        Logger.w(LOG_TAG_PROXY, "$TAG; processRpnPurchase: server entitlement returned but payload unusable, using DB payload as fallback")
                        // Construct a purchase with the DB payload so activateRpn has something to store
                        if (dbPayloadValid) {
                            effectivePurchase = purchase.copy(payload = existingSubs.developerPayload)
                        }
                    }
                } catch (e: Exception) {
                    Logger.w(LOG_TAG_PROXY, "$TAG; processRpnPurchase: server entitlement query failed: ${e.message}")
                    // Fall through, use DB payload if available
                    if (dbPayloadValid && !existingPayloadValid) {
                        Logger.i(LOG_TAG_PROXY, "$TAG; processRpnPurchase: falling back to DB developerPayload")
                        effectivePurchase = purchase.copy(payload = existingSubs.developerPayload)
                    }
                }
            }
        } else if (!existingPayloadValid && dbPayloadValid) {
            // purchase.payload is empty but DB has a valid payload from a previous session
            Logger.i(LOG_TAG_PROXY, "$TAG; processRpnPurchase: purchase.payload empty, using DB developerPayload")
            effectivePurchase = purchase.copy(payload = existingSubs.developerPayload)
        }

        // This is the single point that writes the entitlement file and sets rpnState.
        // Calling it even when already-enabled ensures the file is present after app restart.
        activateRpn(effectivePurchase)

        val storedOk = effectivePurchase.payload.isNotEmpty()
        Logger.i(LOG_TAG_PROXY, "$TAG; processRpnPurchase: complete, payloadStored=$storedOk, product=${purchase.productId}")
        return storedOk
    }

    /**
     * Returns `true` if [payload] contains a valid `ws.sessiontoken` that can be passed to
     * the tunnel for WIN registration.
     *
     * An empty string, a payload that fails JSON parsing, or one that lacks a non-empty
     * `sessiontoken` inside the `ws` block is considered unusable, the caller must obtain
     * a fresh entitlement from the server before proceeding.
     */
    private suspend fun isPayloadUsable(payload: String): Boolean {
        if (payload.isEmpty()) return false
        return try {
            val ws = extractWsObject(payload) ?: return false
            val ent = VpnController.getEntitlementDetails(ws, billingBackendClient.getDeviceId())
            Logger.i(LOG_TAG_PROXY, "$TAG; isPayloadUsable: extracted entitlement from payload, checking session token usability")
            if (DEBUG) {
                Logger.d(LOG_TAG_PROXY, "$TAG; isPayloadUsable: payload ws block: $ws")
                Logger.d(LOG_TAG_PROXY, "$TAG; isPayloadUsable: entitlement details: ${ent?.expiry()}, ${ent?.token()}, ${ent?.status()}, cidLen=${ent?.cid()?.length}, didLen=${ent?.did()?.length}, ${ent?.test()}, ${ent?.json()}")
            }
            val sessionToken = ent?.token() ?: ""
            val isUsable = sessionToken.isNotEmpty()
            if (!isUsable) Logger.d(LOG_TAG_PROXY, "$TAG; isPayloadUsable: ws found but sessiontoken is empty")
            isUsable
        } catch (e: Exception) {
            Logger.w(LOG_TAG_PROXY, "$TAG; isPayloadUsable: failed to parse payload: ${e.message}")
            false
        }
    }

    private fun startStateObserver() {
        stateObserverScope.launch {
            try {
                subscriptionStateMachine.currentState.collect { state ->
                    Logger.d(LOG_TAG_PROXY, "$TAG; collect; initial subscription state: ${state.name}")
                    io { handleStateChange(state) }
                }
            } catch (e: Exception) {
                Logger.e(LOG_TAG_PROXY, "$TAG; collect; error in state observer: ${e.message}", e)
            }
        }
    }

    private suspend fun handleStateChange(state: SubscriptionStateMachineV2.SubscriptionState) {
        when (state) {
            is SubscriptionStateMachineV2.SubscriptionState.Entitled -> {
                if (state is SubscriptionStateMachineV2.SubscriptionState.Cancelled) {
                    Logger.i(LOG_TAG_PROXY, "$TAG; subscription cancelled, checking if should disable RPN")
                    try {
                        val subs = subscriptionStateMachine.getSubscriptionData()
                        val status = subs?.subscriptionStatus
                        val currTs = System.currentTimeMillis()
                        if ((status != null && status.billingExpiry > currTs) || DEBUG) {
                            Logger.i(LOG_TAG_PROXY, "$TAG; subscription cancelled but still valid until ${status?.billingExpiry}, not deactivating RPN yet")
                        } else {
                            deactivateRpn("Subscription cancelled and expired")
                        }
                    } catch (e: Exception) {
                        Logger.e(LOG_TAG_PROXY, "$TAG; error handling cancelled state: ${e.message}", e)
                    }
                    return
                }
                Logger.i(LOG_TAG_PROXY, "$TAG; subscription activated, ensuring RPN is enabled and entitlement refreshed")
                val subs = subscriptionStateMachine.getSubscriptionData()
                // Use getEffectivePurchaseDetail() which tries in-memory purchaseDetail first,
                // then falls back to reconstructing from subscriptionStatus (DB-restored cold-start).
                val purchaseDetail = getEffectivePurchaseDetail()
                if (purchaseDetail == null) {
                    Logger.w(LOG_TAG_PROXY, "$TAG; handleStateChange.Active: no purchase detail and no DB subscription, cannot ensure entitlement")
                    return
                }
                if (subs?.purchaseDetail == null) {
                    Logger.i(LOG_TAG_PROXY, "$TAG; handleStateChange.Active: purchaseDetail reconstructed from subscriptionStatus (token=${purchaseDetail.purchaseToken.take(8)})")
                }
                // Always call activateRpn here
                // Even if rpnState is already ENABLED, the entitlement file may be absent
                // after an app restart; activateRpn will re-store it from the DB payload.
                try {
                    activateRpn(purchaseDetail)
                } catch (e: Exception) {
                    Logger.e(LOG_TAG_PROXY, "$TAG; handleStateChange.Active: error activating RPN: ${e.message}", e)
                }
            }
            is SubscriptionStateMachineV2.SubscriptionState.Expired -> {
                Logger.w(LOG_TAG_PROXY, "$TAG; subscription expired, disabling RPN")
                try {
                    deactivateRpn("Subscription expired")
                } catch (e: Exception) {
                    Logger.e(LOG_TAG_PROXY, "$TAG; error deactivating RPN on expiry: ${e.message}", e)
                }
            }
            is SubscriptionStateMachineV2.SubscriptionState.Revoked -> {
                Logger.w(LOG_TAG_PROXY, "$TAG; subscription revoked, immediately disabling RPN")
                try {
                    deactivateRpn("Subscription revoked")
                } catch (e: Exception) {
                    Logger.e(LOG_TAG_PROXY, "$TAG; error deactivating RPN on revocation: ${e.message}", e)
                }
            }
            is SubscriptionStateMachineV2.SubscriptionState.Error -> {
                Logger.e(LOG_TAG_PROXY, "$TAG; subscription state machine in error state: ${state.name}")
            }
            else -> {
                Logger.d(LOG_TAG_PROXY, "$TAG; subscription state: ${state.name}")
            }
        }
    }

    fun collectSubscriptionState(): Flow<SubscriptionStateMachineV2.SubscriptionState> {
        return subscriptionStateMachine.currentState
    }

    fun hasValidSubscription(): Boolean {
        val valid = subscriptionStateMachine.hasValidSubscription()
        Logger.i(LOG_TAG_PROXY, "$TAG; using state machine for subscription check, valid: $valid")
        return valid
    }

    suspend fun handleUserCancellation() {
        try {
            subscriptionStateMachine.userCancelled()
            Logger.i(LOG_TAG_PROXY, "$TAG; user cancellation handled")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; error handling user cancellation: ${e.message}", e)
        }
    }

    suspend fun handleSubscriptionRevoked() {
        try {
            subscriptionStateMachine.subscriptionRevoked()
            Logger.w(LOG_TAG_PROXY, "$TAG; subscription revocation handled")
            deactivateRpn()
            Logger.i(LOG_TAG_PROXY, "$TAG; RPN deactivated due to revocation")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; error handling subscription revocation: ${e.message}", e)
        }
    }

    suspend fun setHopForWinServer(key: String, enabled: Boolean) {
        winServerController.setHopForWinServer(key, enabled)
    }

    suspend fun getSelectedCCs(): Set<CountryConfig> = winServerController.getSelectedCCs()

    /**
     * Returns the epoch-millisecond timestamp at which [key] was last added to the tunnel
     * Returns 0 if the server has not been added to the tunnel since the last process start.
     */
    fun getSelectedSinceTs(key: String): Long {
        return serverKeyMeta[key]?.selectedAt ?: 0L
    }

    /**
     * Records the epoch-ms timestamp at which [key] was last added to the VPN tunnel.
     *
     * Must be called immediately after every successful [VpnController.addNewWinServer] call,
     * regardless of whether it is the user explicitly enabling a server ([enableWinServer]),
     * the tunnel being re-established on a phone reboot / VPN reconnect
     * ([BraveVPNService.handleRpnProxies]), or a periodic refresh ([updateWinProxy]).
     */
    fun notifyServerAddedToTun(key: String) {
        if (key.isEmpty()) return
        val now = System.currentTimeMillis()
        serverKeyMeta[key] = ServerKeyMeta(selectedAt = now)
        if (DEBUG) Logger.d(LOG_TAG_PROXY, "$TAG; notifyServerAddedToTun: key=$key, ts=$now")
    }

    /**
     * Removes the in-memory tunnel timestamp for [key].
     * Called when a server is explicitly removed from the tunnel (e.g. [disableWinServer]).
     */
    private fun clearServerMeta(key: String) {
        serverKeyMeta.remove(key)
    }

    suspend fun getEnabledConfigs(): Set<CountryConfig> = winServerController.getEnabledConfigs()

    /**
     * Full update pipeline:
     *  1. Ask the tunnel to refresh and return updated WIN state bytes
     *     ([VpnController.updateWin]).
     *  2a. If the tunnel returns bytes → persist them with [updateWinConfigState].
     *  2b. If the tunnel returns null → fall back to [registerProxy] (full re-register).
     *     If that also fails return null so callers can show an error.
     *  3. Fetch server locations from the tunnel ([fetchAndConstructWinLocations]).
     *     If empty, retry up to 3 times with 1 s back-off.
     *  4. Sync fetched locations to DB + in-memory cache ([syncWinServers]).
     *  5. Re-add every currently-enabled server key back into the tunnel
     *     ([VpnController.addNewWinServer]) so WireGuard peers are refreshed.
     *  6. If any previously-selected servers are absent from the new location list,
     *     emit them on [serverRemovedEvent] for the UI to surface a notification.
     *
     * Return contract
     *  - `null` → hard failure; caller should show an error / retry.
     *  - empty list → update succeeded but tunnel reported no locations yet; treat as
     *                 success, show retry / await next cycle.
     *  - non-empty → full success; contains the refreshed [CountryConfig] list.
     */
    suspend fun updateWinProxy(): List<CountryConfig>? {
        Logger.i(LOG_TAG_PROXY, "$TAG; updateWinProxy: starting WIN proxy update")


        val isRpnRegistered = VpnController.isWinRegistered()
        if (!isRpnRegistered) {
            Logger.w(LOG_TAG_PROXY, "$TAG; updateWinProxy: WIN not registered, delegating to registerProxy")
            val registered = registerProxy(RpnType.WIN)
            if (!registered) {
                Logger.e(LOG_TAG_PROXY, "$TAG; updateWinProxy: registration attempt failed, cannot proceed with update")
                return null
            }
            Logger.i(LOG_TAG_PROXY, "$TAG; updateWinProxy: WIN registered successfully, falling through to server sync")
            // registerProxy already synced the server list; fall through so enabled
            // servers are re-added to the tunnel by the loop below.
        } else {
            val bytes: ByteArray? = VpnController.updateWin()

            if (bytes == null) {
                Logger.w(LOG_TAG_PROXY, "$TAG; updateWinProxy: updateWin() returned null")
                return null
            }

            val persisted = updateWinConfigState(bytes)
            if (!persisted) {
                // Log the failure but do NOT return null; the in-memory state is already
                // updated by updateWinConfigState before it touches the DB.
                Logger.w(
                    LOG_TAG_PROXY,
                    "$TAG; updateWinProxy: failed to persist WIN config state (continuing)"
                )
            }
        }

        var fetchResult: Pair<Set<CountryConfig>, List<String>> = Pair(emptySet(), emptyList())
        val maxFetchRetries = 3
        for (attempt in 1..maxFetchRetries) {
            fetchResult = try {
                fetchAndConstructWinLocations()
            } catch (e: Exception) {
                Logger.e(LOG_TAG_PROXY, "$TAG; updateWinProxy: fetchAndConstructWinLocations threw on attempt $attempt: ${e.message}", e)
                Pair(emptySet(), emptyList())
            }

            if (fetchResult.first.isNotEmpty()) {
                Logger.i(LOG_TAG_PROXY, "$TAG; updateWinProxy: fetched ${fetchResult.first.size} locations on attempt $attempt")
                break
            }

            if (attempt < maxFetchRetries) {
                Logger.w(LOG_TAG_PROXY, "$TAG; updateWinProxy: no locations on attempt $attempt, retrying in 1s…")
                delay(2_000L)
            }
        }

        val (newServers, removedSelectedIds) = fetchResult

        if (newServers.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$TAG; updateWinProxy: no locations after $maxFetchRetries attempts; cache unchanged")
            // Return empty list (not null) bytes persisted successfully, just no locations yet
            return emptyList()
        }

        try {
            syncWinServers(newServers)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; updateWinProxy: syncWinServers threw: ${e.message}", e)
            // Non-fatal; continue to re-add servers and notify UI
        }

        // Read enabled configs from the freshly-synced cache so we never re-add stale keys.
        val enabledConfigs = winServerController.enabledNonAutoConfigs()

        for (config in enabledConfigs) {
            try {
                val result = VpnController.addNewWinServer(config.key)
                if (result.first) {
                    // Update the in-memory timestamp so uptime display shows the last
                    // time this server was (re-)added to the tunnel.
                    notifyServerAddedToTun(config.key)
                    Logger.i(LOG_TAG_PROXY, "$TAG; updateWinProxy: re-added server key=${config.key}")
                } else {
                    Logger.w(LOG_TAG_PROXY, "$TAG; updateWinProxy: failed to re-add server key=${config.key}: ${result.second}")
                    // Mark as disabled in cache + DB so the UI reflects the real state
                    winServerController.markDisabledInCache(config.key)
                    try {
                        val dbConfig = countryConfigRepo.getById(config.id)
                        if (dbConfig != null) {
                            dbConfig.isEnabled = false
                            countryConfigRepo.update(dbConfig)
                        }
                    } catch (dbErr: Exception) {
                        Logger.w(LOG_TAG_PROXY, "$TAG; updateWinProxy: could not mark server disabled in DB: ${dbErr.message}")
                    }
                }
            } catch (e: Exception) {
                Logger.e(LOG_TAG_PROXY, "$TAG; updateWinProxy: exception re-adding server key=${config.key}: ${e.message}", e)
            }
        }

        if (removedSelectedIds.isNotEmpty()) {
            // Resolve full CountryConfig objects for the removed IDs so the UI can
            // display country names / flags in ServerRemovalNotificationBottomSheet.
            val removedConfigs = try {
                countryConfigRepo.getAllConfigs()
                    .filter { removedSelectedIds.contains(it.id) && !it.id.equals(AUTO_SERVER_ID, true) }
            } catch (e: Exception) {
                Logger.w(LOG_TAG_PROXY, "$TAG; updateWinProxy: could not resolve removed configs: ${e.message}")
                emptyList()
            }

            if (removedConfigs.isNotEmpty()) {
                Logger.w(LOG_TAG_PROXY, "$TAG; updateWinProxy: emitting removal event for ${removedConfigs.size} server(s)")
                // Emit on the shared-flow so any active ServerSelectionFragment shows the sheet
                _serverRemovedEvent.tryEmit(removedConfigs)
            }
        }

        val currentServers = winServerController.snapshot()
        Logger.i(LOG_TAG_PROXY, "$TAG; updateWinProxy: complete, ${currentServers.size} servers in cache, " +
                "${enabledConfigs.size} re-added to tunnel, ${removedSelectedIds.size} removed-selected notified")
        return currentServers
    }

    private fun io(f: suspend () -> Unit) {
        stateObserverScope.launch { f() }
    }

    suspend fun getWinExistingData(): ByteArray? = entitlementStore.getWinExistingData()

    suspend fun updateWinConfigState(byteArray: ByteArray?): Boolean =
        entitlementStore.updateWinConfigState(byteArray)

    /**
     * Fetches WIN server locations from the API and syncs with database and cache.
     * Returns a Pair of:
     * - First: Set of new/updated servers from API
     * - Second: List of removed server IDs that were in the selected list (for notifications)
     */
    suspend fun fetchAndConstructWinLocations(): Pair<Set<CountryConfig>, List<String>> {
         // Fetch from API
         val locationsRes = try {
             VpnController.getRpnLocations(RpnType.WIN)
         } catch (e: Exception) {
             Logger.e(LOG_TAG_PROXY, "$TAG; fetchAndConstructWinLocations: exception getting RPN props: ${e.message}", e)
             return Pair(emptySet(), emptyList())
         }

         val locations = locationsRes.first
         if (locations == null) {
             Logger.w(LOG_TAG_PROXY, "$TAG; err; win props is null")
             return Pair(emptySet(), emptyList())
         }

         val count = try {
             locations.len()
         } catch (e: Exception) {
             Logger.e(LOG_TAG_PROXY, "$TAG; fetchAndConstructWinLocations: exception getting locations count: ${e.message}", e)
             return Pair(emptySet(), emptyList())
         }

         if (count == 0L) {
             Logger.w(LOG_TAG_PROXY, "$TAG; err; no locations found in win props")
             return Pair(emptySet(), emptyList())
         }

         val newServers = mutableSetOf<CountryConfig>()
         for(i in 0 until count) {
             try {
                 val loc = locations.get(i)
                 if (loc == null) {
                     Logger.w(LOG_TAG_PROXY, "$TAG; err; location is null at index $i")
                     continue
                 }

                  // Validate essential fields
                  if (!RpnWinLocationMapper.hasEssentialFields(loc.cc, loc.name, loc.key)) {
                      Logger.w(LOG_TAG_PROXY, "$TAG; err; location has null or empty essential fields at index $i, cc=${loc.cc}, name=${loc.name}, key=${loc.key}")
                      continue
                  }

                 // Additional validation for address
                 val address = loc.addrs ?: ""
                 if (address.isEmpty()) {
                     Logger.w(LOG_TAG_PROXY, "$TAG; warning; location at index $i has empty address, cc=${loc.cc}, name=${loc.name}")
                 }

                  val id = RpnWinLocationMapper.serverId(loc.cc, loc.name, loc.key)
                 val cfg = CountryConfig(
                     id = id,
                     cc = loc.cc,
                     name = loc.name,
                     address = address,
                     city = loc.city,
                     key = loc.key,
                     load = loc.load,
                     link = loc.link,
                     premium = loc.premium,
                     count = loc.count,
                     isActive = true
                 )
                 newServers.add(cfg)
             } catch (e: Exception) {
                 Logger.w(LOG_TAG_PROXY, "$TAG; err; error processing location at index $i: ${e.message}", e)
             }
         }

         // Get existing servers from DB to identify removed ones
         val existingServers = try {
             countryConfigRepo.getAllConfigs()
         } catch (e: Exception) {
             Logger.e(LOG_TAG_PROXY, "$TAG; err; failed to get existing servers: ${e.message}", e)
             emptyList()
         }

          val existingIds = existingServers.map { it.id }.toSet()
          val auto = existingServers.firstOrNull { it.id.contains(AUTO_SERVER_ID, ignoreCase = true) }
              ?: RpnWinAutoServer.create(AUTO_SERVER_ID, AUTO_COUNTRY_CODE, System.currentTimeMillis())
          newServers.add(auto)
          val newIds = newServers.map { it.id }.toSet()
          val removedIds = RpnWinLocationMapper.removedIds(existingIds, newIds)

          // Check if any removed servers were in the selected list
          val removedSelected = RpnWinLocationMapper.removedSelected(
              existing = existingServers,
              removedIds = removedIds,
              isInTunnel = { serverKeyMeta.containsKey(it) },
          )
          val removedSelectedIds = removedSelected.selectedIds
          if (removedSelected.keysToRemove.isNotEmpty()) {
              removedSelected.keysToRemove.forEach { key ->
                  Logger.w(LOG_TAG_PROXY, "$TAG; removed server (key=$key) was in tunnel")
                  serverKeyMeta.remove(key)
              }
              Logger.i(LOG_TAG_PROXY, "$TAG; cleared ${removedSelected.keysToRemove.size} server keys from serverKeyMeta")
          }

         Logger.i(LOG_TAG_PROXY, "$TAG; fetchAndConstructWinLocations: new=${newServers.size}, existing=${existingIds.size}, removed=${removedIds.size}, removedSelected=${removedSelectedIds.size}")

         return Pair(newServers, removedSelectedIds)
    }

    /**
     * Enables a WIN server by its key.
     * Updates the database and in-memory cache upon successful activation.
     */
    suspend fun enableWinServer(key: String): Pair<Boolean, String> =
        winServerController.enableWinServer(key)

    suspend fun setLockdownForWinServer(key: String, lockdown: Boolean) {
        winServerController.setLockdownForWinServer(key, lockdown)
    }

    suspend fun setCatchAllForWinServer(key: String, catchAll: Boolean) {
        winServerController.setCatchAllForWinServer(key, catchAll)
    }

    suspend fun setMobileOnlyForWinServer(key: String, mobileOnly: Boolean) {
        winServerController.setMobileOnlyForWinServer(key, mobileOnly)
    }

    suspend fun setSsidEnabledForWinServer(key: String, ssidEnabled: Boolean) {
        winServerController.setSsidEnabledForWinServer(key, ssidEnabled)
    }

    suspend fun disableWinServer(key: String): Pair<Boolean, String> =
        winServerController.disableWinServer(key)

    /**
     * Ensures AUTO server exists in database. Creates it if it doesn't exist.
     * AUTO server is a special server that represents automatic server selection.
     */
    suspend fun ensureAutoServerExists() {
        winServerController.ensureAutoServerExists()
    }

    /**
     * Gets AUTO server from database/cache.
     *
     * If the AUTO server is absent from both the in-memory cache and the database (e.g. on first
     * launch or if [load] hasn't been called yet), [ensureAutoServerExists] is invoked to create
     * and persist it, and the newly-created entry is returned.  This makes the function
     * self-healing so that callers never see a spurious null.
     */
    suspend fun getAutoServer(): CountryConfig? = winServerController.getAutoServer()

    /**
     * Updates AUTO server state in database and cache
     */
    suspend fun updateAutoServerState(autoServer: CountryConfig) {
        winServerController.updateAutoServerState(autoServer)
    }

    /**
     * Syncs fetched servers with database and cache.
     * This should be called after fetchAndConstructWinLocations.
     * AUTO server is protected and never deleted.
     */
    suspend fun syncWinServers(servers: Set<CountryConfig>) {
        winServerController.syncWinServers(servers)
    }

    /**
     * Refreshes WIN servers from API and returns information about removed servers.
     * This is useful for UI to detect and notify users about removed selected servers.
     * Returns a Pair of:
     * - First: List of all current servers after refresh
     * - Second: List of CountryConfig objects that were removed and were in the selected list
     */
    suspend fun refreshWinServers(): Pair<List<CountryConfig>, List<CountryConfig>> =
        winServerController.refreshWinServers()

    suspend fun getAllPossibleConfigIdsForApp(
        uid: Int,
        ip: String,
        port: Int,
        domain: String,
        usesMobileNw: Boolean,
        ssid: String
    ): List<String> {
        val allProxyIdsForApp = try {
            ProxyManager.getProxyIdsForApp(uid)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; error getting proxy ids for app $uid: ${e.message}", e)
            emptyList()
        }
        val allIds = allProxyIdsForApp.toList()
        val rpnProxyIdsForApp = allIds.filter { it.startsWith(Backend.RpnWin) }
        val servers = winServerController.snapshot()
        return hopSelector.collectProxyIds(
            uid = uid,
            ip = ip,
            port = port,
            domain = domain,
            usesMobileNw = usesMobileNw,
            ssid = ssid,
            allProxyIdsForApp = allIds,
            rpnProxyIdsForApp = rpnProxyIdsForApp,
            servers = servers,
            resolveAutoId = { VpnController.getWinByKey("")?.id() ?: Backend.Block },
        )
    }

    fun matchesSsidList(ssidList: String, ssid: String): Boolean =
        ssidController.matchesSsidList(ssidList, ssid)

    /**
     * Update SSID based status for a country
     * @param cc Country code
     * @param ssidBased Whether SSID-based connection is enabled
     */
    suspend fun updateSsidBased(key: String, ssidBased: Boolean) {
        winServerController.updateSsidBased(key, ssidBased)
    }

    /**
     * Update SSID list for a country
     * @param cc Country code
     * @param ssids JSON string of SSID items
     */
    suspend fun updateSsids(key: String, ssids: String) {
        winServerController.updateSsids(key, ssids)
    }

    suspend fun getCountryConfigByKey(key: String): CountryConfig? =
        winServerController.getCountryConfigByKey(key)

    /**
     * Cleanup method to cancel observer job and release resources.
     * Should be called when the manager is no longer needed.
     */
    fun cleanup() {
        try {
            stateObserverJob.cancel()
            Logger.i(LOG_TAG_PROXY, "$TAG; cleanup: observer job cancelled")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$TAG; cleanup: error cancelling observer job: ${e.message}", e)
        }
    }

    data class RpnStats(val routerStats: RouterStats?, val mtu: Long?, val status: Int?, val ip4: Boolean?, val ip6: Boolean?, val clientV4: IPMetadata?)
    suspend fun stats(): String {
        val sb = StringBuilder()
        sb.append("   Rpn active: ${isRpnActive()}\n\n")

        if (!isRpnActive()) return sb.toString()

        val enabledServers = winServerController.snapshot().filter { it.isEnabled }
        enabledServers.forEach {
            val id = if (it.key.equals(AUTO_SERVER_ID, true)) {
                Backend.RpnWin
            } else {
                it.key
            }
            val stats = VpnController.getRpnStats(id)
            val routerStats = stats?.routerStats
            sb.append("   id: ${it.id}, name: ${it.name}\n")
            sb.append("   addr: ${routerStats?.addrs}").append("\n")
            sb.append("   mtu: ${stats?.mtu}\n")
            sb.append("   status: ${routerStats?.status}\n")
            sb.append("   statusReason: ${routerStats?.statusReason}\n")
            sb.append("   ip4: ${stats?.ip4}\n")
            sb.append("   ip6: ${stats?.ip6}\n")
            sb.append("   rx: ${routerStats?.rx}\n")
            sb.append("   tx: ${routerStats?.tx}\n")
            sb.append("   lastRx: ${getRelativeTimeSpan(routerStats?.lastRx)}\n")
            sb.append("   lastTx: ${getRelativeTimeSpan(routerStats?.lastTx)}\n")
            sb.append("   lastGoodRx: ${getRelativeTimeSpan(routerStats?.lastGoodRx)}\n")
            sb.append("   lastGoodTx: ${getRelativeTimeSpan(routerStats?.lastGoodTx)}\n")
            sb.append("   lastRxErr: ${routerStats?.lastRxErr}\n")
            sb.append("   lastTxErr: ${routerStats?.lastTxErr}\n")
            sb.append("   lastOk: ${getRelativeTimeSpan(routerStats?.lastOK)}\n")
            sb.append("   lastOpen: ${getRelativeTimeSpan(routerStats?.lastOpen)}\n")
            sb.append("   hdl: ${routerStats?.hdl}\n")
            sb.append("   since: ${getRelativeTimeSpan(routerStats?.since)}\n")
            sb.append("   errRx: ${routerStats?.errRx}\n")
            sb.append("   errTx: ${routerStats?.errTx}\n")
            sb.append("   extra: ${routerStats?.extra}\n")
            sb.append("   client4: ${stats?.clientV4}\n\n")
            val s = sb.toString()
            Logger.d(LOG_TAG_PROXY, "$TAG; id: $id stats:\n$s")
        }
        if (sb.isEmpty()) {
            sb.append("   N/A\n\n")
        }
        return sb.toString()
    }

    data class ActiveRpnAddlInfo(val key: String, val name: String, val cc: String, val city: String, val addr: String, val pubPub: String, val load: Int, val allowed: String, val count: Int, val excluded: Boolean, val link: Int, val premium: Boolean)

    private fun getRelativeTimeSpan(t: Long?): CharSequence? {
        if (t == null) return "0"

        if (t < 0) return "-1"

        val now = System.currentTimeMillis()
        // returns a string describing 'time' as a time relative to 'now'
        return DateUtils.getRelativeTimeSpanString(
            t,
            now,
            DateUtils.SECOND_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )
    }

}
