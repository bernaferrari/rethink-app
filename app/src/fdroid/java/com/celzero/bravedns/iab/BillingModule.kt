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
 * fdroid flavor stub for [BillingModule].
 *
 * The fdroid flavor does not use Google Play Billing or the Rethink billing server APIs,
 * so this module provides an empty (no-op) Koin module. It satisfies the reference in
 * [com.celzero.bravedns.ServiceModuleProvider] (full source set) without pulling in
 * any billing dependencies.
 */
object BillingModule {
    /** Empty module — fdroid has no billing server interactions. */
    val billingModules: Module = module { /* no-op for fdroid */ }
}

