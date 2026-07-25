/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.celzero.bravedns.di

import com.celzero.bravedns.data.DataModule
import com.celzero.bravedns.database.DatabaseModule
import com.celzero.bravedns.service.ServiceModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

/**
 * Android implementation graph for the shared application.
 *
 * Context itself is still supplied by `startKoin { androidContext(...) }`; all application
 * services, Room databases, repositories, and Android Compose view models are generated from
 * this configuration.
 */
@Module(includes = [RethinkSharedModule::class, DatabaseModule::class, DataModule::class, ServiceModule::class])
@Configuration
@ComponentScan("com.celzero.bravedns.ui.compose", "com.celzero.bravedns.viewmodel")
class RethinkAndroidModule
