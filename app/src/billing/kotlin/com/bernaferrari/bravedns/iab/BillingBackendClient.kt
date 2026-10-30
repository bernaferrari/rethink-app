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

import Logger
import Logger.LOG_IAB
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.IOException

/**
 * Facade for all Rethink backend billing API calls.
 *
 * HTTP lives in [BillingEntitlementApi]; Retrofit wiring in [BillingApiFactory];
 * `/g/tx` JSON in [PurchaseHistoryParser]. This class owns identity resolution
 * against [SecureIdentityStore] and forwards the public API unchanged.
 */
class BillingBackendClient(
    private val identityStore: SecureIdentityStore,
    private val persistentState: PersistentState,
    private val identityDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val apiFactory by lazy { BillingApiFactory(persistentState) }
    private val entitlementApi by lazy { BillingEntitlementApi(persistentState, apiFactory) }

    companion object {
        private const val TAG = "BillingBackendClient"
    }

    /**
     * Serializes [refreshIdentity] and [getAccountId]/[getDeviceId]/[resolveIdentity] calls
     * so that concurrent callers never race to create competing CID/DID pairs.
     */
    private val identityMutex = Mutex()

    /**
     * Returns the [SecureIdentityStore.Env] that corresponds to the current app mode.
     *
     * **All** identity store reads and writes in this class must use this helper so that
     * test identities are never accidentally served for production requests, and vice-versa.
     */
    private fun currentEnv(): SecureIdentityStore.Env =
        if (persistentState.appTestMode) SecureIdentityStore.Env.TEST
        else SecureIdentityStore.Env.PROD

    /**
     * Returns a server-driven **account** identifier (CID).
     *
     * Resolution order:
     * 1. [SecureIdentityStore] (AES-256-GCM encrypted file, env-scoped via [currentEnv])
     * 2. [refreshIdentity] - calls `POST /d/acc` and `POST /d/reg` to obtain and persist
     *    both CID and DID atomically; the CID is returned on success.
     *
     * Returns an empty string if all sources fail. Callers must treat a blank return
     * as "IDs not yet available" and abort or retry rather than proceeding with empty IDs.
     */
    suspend fun getAccountId(): String {
        val mname = this::getAccountId.name
        val env = currentEnv()
        val (storedAcc, _) = identityStore.get(env)
        if (!storedAcc.isNullOrBlank()) {
            Logger.v(LOG_IAB, "$TAG $mname [${env.label}]: loaded from SecureIdentityStore (len=${storedAcc.length})")
            return storedAcc
        }
        return when (val result = refreshIdentity()) {
            is RefreshIdentityResult.Success -> {
                Logger.i(LOG_IAB, "$TAG $mname [${env.label}]: received from server (len=${result.cid.length})")
                result.cid
            }
            is RefreshIdentityResult.Unauthorized -> {
                Logger.e(LOG_IAB, "$TAG $mname [${env.label}]: 401 unauthorized; cid unavailable")
                ""
            }
            is RefreshIdentityResult.Conflict -> {
                Logger.e(LOG_IAB, "$TAG $mname [${env.label}]: 409 conflict; cid unavailable")
                ""
            }
            is RefreshIdentityResult.Failure -> {
                Logger.w(LOG_IAB, "$TAG $mname [${env.label}]: cid unavailable (transient)")
                ""
            }
        }
    }

    /**
     * Returns a server-driven **device** identifier (DID).
     *
     * Resolution order:
     * 1. CID reconciliation: if [recvCid] is provided and differs from the stored CID,
     *    a fresh DID is obtained via [createOrRegisterDid] for the recvCid.
     * 2. [SecureIdentityStore]: returned directly when no reconciliation is needed.
     * 3. [refreshIdentity]: obtains and persists both CID and DID from the server.
     *
     * Returns an empty string if all sources fail.
     */
    suspend fun getDeviceId(recvCid: String = ""): String = identityMutex.withLock {
        if (recvCid.isNotBlank()) return@withLock resolveDidForCidLocked(recvCid).deviceId
        val (storedCid, storedDid) = identityStore.get(currentEnv())
        if (!storedCid.isNullOrBlank() && !storedDid.isNullOrBlank()) return@withLock storedDid
        when (val result = refreshIdentityLocked()) {
            is RefreshIdentityResult.Success -> result.did
            else -> ""
        }
    }

    suspend fun reconcileDidForCid(cid: String): DidResult = identityMutex.withLock {
        resolveDidForCidLocked(cid)
    }

    private suspend fun resolveDidForCidLocked(cid: String): DidResult {
        if (cid.isBlank()) return DidResult.error(-1)
        val env = currentEnv()
        val (storedCid, storedDid) = identityStore.get(env)
        if (!storedCid.isNullOrBlank() && storedCid != cid) {
            Logger.e(LOG_IAB, "$TAG: refusing to rebind device identity to another account")
            return DidResult.error(409)
        }
        if (storedCid == cid && !storedDid.isNullOrBlank()) return DidResult(storedDid)
        val result = createOrRegisterDid(cid, storedDid.orEmpty())
        if (result.isSuccess) identityStore.save(env, cid, result.deviceId)
        return result
    }

    suspend fun getDeviceIdForCid(cid: String): String = identityMutex.withLock {
        if (cid.isBlank()) return@withLock ""
        val (storedCid, storedDid) = identityStore.get(currentEnv())
        if (storedCid == cid) storedDid.orEmpty() else ""
    }

    /**
     * Returns the current (accountId, deviceId) pair from the store, or [refreshIdentity].
     */
    suspend fun resolveIdentity(): RefreshIdentityResult {
        val mname = this::resolveIdentity.name
        val env = currentEnv()
        val (storedAcc, storedDev) = identityStore.get(env)
        if (!storedAcc.isNullOrBlank() && !storedDev.isNullOrBlank()) {
            Logger.d(LOG_IAB, "$TAG $mname [${env.label}]: loaded from SecureIdentityStore (cidLen=${storedAcc.length}, didLen=${storedDev.length})")
            return RefreshIdentityResult.Success(storedAcc, storedDev)
        }
        val result = refreshIdentity()
        when (result) {
            is RefreshIdentityResult.Success -> Logger.i(LOG_IAB, "$TAG $mname [${env.label}]: obtained from server and persisted (cidLen=${result.cid.length}, didLen=${result.did.length})")
            is RefreshIdentityResult.Unauthorized -> Logger.e(LOG_IAB, "$TAG $mname [${env.label}]: 401 unauthorized; identity unavailable")
            is RefreshIdentityResult.Conflict -> Logger.e(LOG_IAB, "$TAG $mname [${env.label}]: 409 conflict; identity unavailable")
            is RefreshIdentityResult.Failure -> Logger.w(LOG_IAB, "$TAG $mname [${env.label}]: identity unavailable (transient)")
        }
        return result
    }

    suspend fun createOrRegisterCid(existingCid: String = ""): CidResult =
        entitlementApi.createOrRegisterCid(existingCid)

    suspend fun createOrRegisterDid(
        accountId: String,
        existingDeviceId: String = ""
    ): DidResult = entitlementApi.createOrRegisterDid(accountId, existingDeviceId)

    /**
     * One-time migration for users upgrading from a build that stored the identity in the
     * legacy unscoped `identity.json` file.
     *
     * Env determination: live entitlement via [VpnController.getEntitlementDetails] when
     * the tunnel is up; otherwise [PersistentState.appTestMode]. Idempotent once the
     * legacy file is gone.
     */
    private suspend fun migrateIdentityIfNeeded() = withContext(identityDispatcher) {
        if (!identityStore.hasLegacyIdentity()) return@withContext

        val (legacyCid, legacyDid) = identityStore.readLegacy()
        if (legacyCid.isNullOrBlank() || legacyDid.isNullOrBlank()) {
            Logger.w(LOG_IAB, "$TAG migrateIdentityIfNeeded: legacy identity blank/corrupt; deleting without migration")
            identityStore.migrateFromLegacy(currentEnv())
            return@withContext
        }

        val env = try {
            val entitlement = VpnController.getEntitlementDetails(null, legacyDid)
            if (entitlement != null) {
                val isTest = entitlement.test()
                Logger.i(LOG_IAB, "$TAG migrateIdentityIfNeeded: entitlement resolved, isTest=$isTest " +
                    "(cidLen=${legacyCid.length}, didLen=${legacyDid.length})")
                if (isTest) SecureIdentityStore.Env.TEST else SecureIdentityStore.Env.PROD
            } else {
                val isTest = persistentState.appTestMode
                Logger.i(LOG_IAB, "$TAG migrateIdentityIfNeeded: tunnel unavailable, using appTestMode=$isTest as env " +
                    "(cidLen=${legacyCid.length}, didLen=${legacyDid.length})")
                if (isTest) SecureIdentityStore.Env.TEST else SecureIdentityStore.Env.PROD
            }
        } catch (e: IOException) {
            val isTest = persistentState.appTestMode
            Logger.w(LOG_IAB, "$TAG migrateIdentityIfNeeded: entitlement lookup failed (${e.message}); falling back to appTestMode=$isTest", e)
            if (isTest) SecureIdentityStore.Env.TEST else SecureIdentityStore.Env.PROD
        } catch (e: IllegalStateException) {
            val isTest = persistentState.appTestMode
            Logger.w(LOG_IAB, "$TAG migrateIdentityIfNeeded: entitlement lookup failed (${e.message}); falling back to appTestMode=$isTest", e)
            if (isTest) SecureIdentityStore.Env.TEST else SecureIdentityStore.Env.PROD
        }

        identityStore.migrateFromLegacy(env)
    }

    /**
     * Ensures both CID and DID are present and up-to-date (heartbeat / identity sync).
     *
     * @return [RefreshIdentityResult.Success] with both IDs on success.
     *   [RefreshIdentityResult.Unauthorized] when the server returns HTTP 401 on either step.
     *   [RefreshIdentityResult.Conflict] when the server returns HTTP 409 on either step.
     *   [RefreshIdentityResult.Failure] for all other failures (transient, network, etc.).
     */
    suspend fun refreshIdentity(): RefreshIdentityResult =
        identityMutex.withLock { refreshIdentityLocked() }

    // Caller holds identityMutex. Mutex is deliberately not reentrant.
    private suspend fun refreshIdentityLocked(): RefreshIdentityResult {
        migrateIdentityIfNeeded()
        val env = currentEnv()
        val (storedCid, storedDid) = identityStore.get(env)
        val cid = if (storedCid.isNullOrBlank()) createOrRegisterCid() else CidResult(storedCid)
        if (!cid.isSuccess) return identityFailure(cid.errorCode)
        val did = if (storedCid == cid.accountId && !storedDid.isNullOrBlank()) {
            DidResult(storedDid)
        } else {
            createOrRegisterDid(cid.accountId, "")
        }
        if (!did.isSuccess) return identityFailure(did.errorCode)
        if (storedCid != cid.accountId || storedDid != did.deviceId) {
            identityStore.save(env, cid.accountId, did.deviceId)
        }
        return RefreshIdentityResult.Success(cid.accountId, did.deviceId)
    }

    private fun identityFailure(errorCode: Int): RefreshIdentityResult = when (errorCode) {
        401 -> RefreshIdentityResult.Unauthorized
        409 -> RefreshIdentityResult.Conflict
        else -> RefreshIdentityResult.Failure
    }

    suspend fun registerDevice(
        accountId: String,
        deviceId: String,
        m: JsonObject? = null
    ): RegisterDeviceResult = entitlementApi.registerDevice(accountId, deviceId, m)

    suspend fun registerDeviceWithDeviceMeta(
        accountId: String,
        deviceId: String
    ): RegisterDeviceResult = entitlementApi.registerDeviceWithDeviceMeta(accountId, deviceId)

    suspend fun acknowledgePurchase(
        accountId: String,
        deviceId: String,
        purchaseToken: String,
        productType: String
    ): Pair<Boolean, String> = entitlementApi.acknowledgePurchase(accountId, deviceId, purchaseToken, productType)

    suspend fun queryEntitlement(
        accountId: String,
        deviceId: String,
        purchase: PurchaseDetail,
        purchaseToken: String
    ): QueryEntitlementResult = entitlementApi.queryEntitlement(accountId, deviceId, purchase, purchaseToken)

    suspend fun cancelPurchase(
        accountId: String,
        deviceId: String,
        sku: String,
        purchaseToken: String
    ): Pair<Boolean, String> = entitlementApi.cancelPurchase(accountId, deviceId, sku, purchaseToken)

    suspend fun revokePurchase(
        accountId: String,
        deviceId: String,
        sku: String,
        purchaseToken: String
    ): Pair<Boolean, String> = entitlementApi.revokePurchase(accountId, deviceId, sku, purchaseToken)

    suspend fun fetchPurchaseHistory(
        accountId: String,
        deviceId: String,
        purchaseToken: String,
        total: Int,
    ): FetchOrdersRawResult = entitlementApi.fetchPurchaseHistory(accountId, deviceId, purchaseToken, total)

    suspend fun consumePurchase(
        accountId: String,
        deviceId: String,
        sku: String,
        purchaseToken: String
    ): Boolean = entitlementApi.consumePurchase(accountId, deviceId, sku, purchaseToken)

    fun buildDeviceMeta(prodId: String = ""): JsonObject = entitlementApi.buildDeviceMeta(prodId)

    fun buildCustomerMeta(): JsonObject = entitlementApi.buildCustomerMeta()
}
