/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.celzero.bravedns.viewmodel

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** View models that only exist in the full UI source set. */
object FullViewModelModule {
    val module = module {
        viewModel { BlockFreeDnsViewModel(get(), get(), get(), get(), get(), get()) }
        viewModel { ManagePurchaseViewModel() }
        viewModel { PurchaseHistoryViewModel(get()) }
        viewModel { ServerOrderHistoryViewModel(get()) }
        viewModel { ServerSelectionViewModel() }
    }
}
