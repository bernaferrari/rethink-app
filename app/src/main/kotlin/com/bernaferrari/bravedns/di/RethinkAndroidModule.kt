/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.di

import com.bernaferrari.bravedns.data.DataModule
import com.bernaferrari.bravedns.database.DatabaseModule
import com.bernaferrari.bravedns.service.ServiceModule
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
@ComponentScan("com.bernaferrari.bravedns.ui.compose", "com.bernaferrari.bravedns.viewmodel")
class RethinkAndroidModule
