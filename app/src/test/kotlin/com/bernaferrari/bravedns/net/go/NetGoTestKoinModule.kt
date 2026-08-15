/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package com.bernaferrari.bravedns.net.go

import com.bernaferrari.bravedns.database.LogDatabase
import io.mockk.mockk
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Satisfies Koin compiler deferred-validation of [LogDatabase] when compiling
 * unit tests (shared module call sites are checked without [com.bernaferrari.bravedns.database.DatabaseModule]).
 */
@Module
internal class NetGoTestKoinModule {
    @Single
    fun logDatabase(): LogDatabase = mockk(relaxed = true)
}
