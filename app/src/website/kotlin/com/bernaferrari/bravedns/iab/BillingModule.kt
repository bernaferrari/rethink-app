/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.iab

import com.bernaferrari.bravedns.NonStoreAppUpdater
import com.bernaferrari.bravedns.service.AppUpdater
import com.bernaferrari.bravedns.service.InAppMessageProvider
import com.bernaferrari.bravedns.service.PlayInAppMessageProvider
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** Generated website bindings: direct updates with the billing recovery message bridge. */
@Module
class BillingModule {
    @Single
    fun billingBackendClient(identityStore: SecureIdentityStore): BillingBackendClient =
        BillingBackendClient(identityStore)

    @Single
    fun serverOrderHistoryRepository(client: BillingBackendClient): ServerOrderHistoryRepository =
        ServerOrderHistoryRepository(client)

    @Single
    fun appUpdater(updater: NonStoreAppUpdater): AppUpdater = updater

    @Single
    fun inAppMessageProvider(): InAppMessageProvider = PlayInAppMessageProvider()
}
