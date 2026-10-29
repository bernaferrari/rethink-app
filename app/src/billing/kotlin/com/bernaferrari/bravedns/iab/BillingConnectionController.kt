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

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.GetBillingConfigParams
import com.android.billingclient.api.InAppMessageParams
import com.android.billingclient.api.InAppMessageResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.PurchasesUpdatedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Play [BillingClient] lifecycle: setup, connect, disconnect, in-app messages, billing config.
 *
 * [connectionMutex] is **owned by** [InAppBillingHandler] (outermost lock in the documented
 * order: connectionMutex → SM stateLock → repository mutex). This controller only acquires
 * it for [startConnection]; cancel/revoke keep their acquisitions on the handler.
 */
internal class BillingConnectionController(
    private val connectionMutex: Mutex,
    private val scope: CoroutineScope,
    private val purchasesUpdatedListener: () -> PurchasesUpdatedListener,
    private val onAlreadyConnected: (BillingClient) -> Unit,
    private val onSetupFinished: (BillingClient) -> Unit,
    private val onDisconnected: () -> Unit
) {

    private lateinit var billingClient: BillingClient

    private fun logd(methodName: String, msg: String) = BillingHandlerLog.d(methodName, msg)
    private fun logv(methodName: String, msg: String) = BillingHandlerLog.v(methodName, msg)
    private fun loge(methodName: String, msg: String, e: Exception? = null) =
        BillingHandlerLog.e(methodName, msg, e)
    private fun log(methodName: String, msg: String) = BillingHandlerLog.i(methodName, msg)

    fun clientOrNull(): BillingClient? =
        if (::billingClient.isInitialized) billingClient else null

    fun requireClient(): BillingClient = billingClient

    fun isInitialized(): Boolean = ::billingClient.isInitialized

    fun setupBillingClient(context: Context) {
        if (isBillingClientSetup()) {
            logd("setupBillingClient", "billing client is already setup, skipping setup")
            return
        }
        val mname = "setupBillingClient"
        logv(mname, "setting up billing client")
        billingClient = BillingClient.newBuilder(context)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans()
                    .build()
            )
            .setListener(purchasesUpdatedListener())
            .enableAutoServiceReconnection()
            .build()
    }

    fun isBillingClientSetup(): Boolean {
        val mname = "isBillingClientSetup"
        logv(mname, "checking if billing client is setup")
        if (!::billingClient.isInitialized) {
            logd(mname, "billing client is not initialized")
            return false
        }
        val isReady = billingClient.isReady
        logd(mname, "isInitialized: true, isReady: $isReady")
        return isReady
    }

    fun enableInAppMessaging(activity: Activity, onStatusUpdated: () -> Unit) {
        val mname = "enableInAppMessaging"
        if (!isBillingClientSetup() || !billingClient.isReady) {
            logd(mname, "billing client not ready; skipping in-app messages")
            return
        }
        val inAppParams = InAppMessageParams.newBuilder()
            .addInAppMessageCategoryToShow(InAppMessageParams.InAppMessageCategoryId.TRANSACTIONAL)
            .addAllInAppMessageCategoriesToShow()
            .build()
        billingClient.showInAppMessages(activity, inAppParams) { result ->
            logd(mname, "in-app messaging result: ${result.responseCode}")
            when (result.responseCode) {
                InAppMessageResult.InAppMessageResponseCode.NO_ACTION_NEEDED -> {
                    logd(mname, "no action needed")
                }
                InAppMessageResult.InAppMessageResponseCode.SUBSCRIPTION_STATUS_UPDATED -> {
                    log(mname, "subscription status updated, refreshing purchases")
                    onStatusUpdated()
                }
                else -> {
                    logd(mname, "unknown response code: ${result.responseCode}")
                }
            }
        }
    }

    fun startConnection(callback: (isSuccess: Boolean, message: String) -> Unit) {
        val mname = "startConnection"
        logv(mname, "Starting billing connection")

        scope.launch {
            val lockOwner = Any()
            if (!connectionMutex.tryLock(lockOwner)) {
                logd(mname, "connection attempt already in progress")
                withContext(Dispatchers.Main) {
                    callback.invoke(false, "Connection already in progress")
                }
                return@launch
            }

            val release = BillingConnectionLease(connectionMutex, lockOwner)
            val safetyJob = scope.launch {
                delay(60_000L.milliseconds)
                if (release.release()) {
                    logd(mname, "connection mutex released after safety timeout (60s)")
                }
            }
            val unlock = {
                if (release.release()) safetyJob.cancel()
            }

            try {
                if (::billingClient.isInitialized && billingClient.isReady) {
                    logd(mname, "billing already connected")
                    onAlreadyConnected(billingClient)
                    unlock()
                    withContext(Dispatchers.Main) {
                        callback.invoke(true, "Already connected")
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    billingClient.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(billingResult: BillingResult) {
                            val isOk = BillingResponse(billingResult.responseCode).isOk

                            if (isOk) {
                                logd(mname, "billing client setup finished")
                                onSetupFinished(billingClient)
                            } else {
                                loge(mname, "billing connection failed: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                            }

                            callback.invoke(isOk, if (isOk) "Connected" else billingResult.debugMessage)
                            unlock()
                        }

                        override fun onBillingServiceDisconnected() {
                            log(mname, "billing service disconnected")
                            onDisconnected()
                            callback.invoke(false, "Service disconnected")
                            unlock()
                        }
                    })
                }
            } catch (e: IllegalStateException) {
                loge(mname, "err starting billing connection: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback.invoke(false, "Connection error: ${e.message}")
                }
                unlock()
            }
        }
    }

    fun queryBillingConfig() {
        val mname = "queryBillingConfig"
        val getBillingConfigParams = GetBillingConfigParams.newBuilder().build()
        billingClient.getBillingConfigAsync(getBillingConfigParams) { billingResult, billingConfig ->
            if (billingResult.responseCode == BillingResponseCode.OK
                && billingConfig != null
            ) {
                val countryCode = billingConfig.countryCode
                log(mname, "BillingConfig country code: $countryCode")
            } else {
                log(mname, "err in billing config: ${billingResult.debugMessage}")
            }
        }
    }

    fun endConnection() {
        val mname = "endConnection"
        logd(mname, "ending billing connection")
        if (::billingClient.isInitialized && billingClient.isReady) {
            billingClient.endConnection()
            logd(mname, "billing connection ended")
        } else {
            logd(mname, "billing client is not ready, cannot end connection")
        }
    }
}
