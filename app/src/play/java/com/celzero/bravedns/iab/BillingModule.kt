/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.celzero.bravedns.iab

import android.content.Context
import com.celzero.bravedns.StoreAppUpdater
import com.celzero.bravedns.service.AppUpdater
import com.celzero.bravedns.service.InAppMessageProvider
import com.celzero.bravedns.service.PlayInAppMessageProvider
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/** Generated Play bindings for Billing, store updates, and Play recovery messages. */
@Module
class BillingModule {
    @Single
    fun billingBackendClient(identityStore: SecureIdentityStore): BillingBackendClient =
        BillingBackendClient(identityStore)

    @Single
    fun serverOrderHistoryRepository(client: BillingBackendClient): ServerOrderHistoryRepository =
        ServerOrderHistoryRepository(client)

    @Single
    fun storeAppUpdater(@Provided context: Context): StoreAppUpdater = StoreAppUpdater(context)

    @Single
    fun appUpdater(updater: StoreAppUpdater): AppUpdater = updater

    @Single
    fun inAppMessageProvider(): InAppMessageProvider = PlayInAppMessageProvider()
}
