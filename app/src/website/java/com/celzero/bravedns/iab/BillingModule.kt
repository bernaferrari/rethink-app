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
 * website flavor stub for [BillingModule].
 *
 * The website flavor currently uses a stub [SubscriptionCheckWorker] and does not
 * exercise the Play-billing server API paths. This empty module satisfies the
 * reference in [com.celzero.bravedns.ServiceModuleProvider] (full source set)
 * without registering any unnecessary bindings.
 *
 * When the website flavor adopts full server-billing integration this stub should
 * be replaced with the real [BillingServerRepository] registration (same as play).
 */
object BillingModule {
    /** Empty module — website billing uses a separate Stripe flow. */
    val billingModules: Module = module { /* no-op for website */ }
}

