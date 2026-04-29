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
 * Registers a no-op [BillingBackendClient] stub so that `main` source-set services
 * The fdroid flavor does not have billing module as of now v055v
 */
object BillingModule {
    val billingModules: Module = module {
        single { BillingBackendClient(get()) }
    }
}

