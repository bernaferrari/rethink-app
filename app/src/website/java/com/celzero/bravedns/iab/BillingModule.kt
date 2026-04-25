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
package com.celzero.bravedns.iab

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module that wires [BillingBackendClient] for the **play** (and website) flavor.
 *
 * [BillingBackendClient] depends on [SecureIdentityStore] which is registered in
 * [ServiceModule] (main). Both are singletons so they
 * share a single instance across [InAppBillingHandler] and [SubscriptionCheckWorker].
 *
 * This module is added to [AppModules] via [ServiceModuleProvider]
 * in the `full` source set, which is shared by `play`, `website`, and `fdroid` builds.
 * The `fdroid` flavor does not use [BillingBackendClient] but including it in the
 * DI graph is harmless because it is only injected by play/website code paths.
 */
object BillingModule {
    val billingModules: Module = module {
        single { BillingBackendClient(get()) }
    }
}

