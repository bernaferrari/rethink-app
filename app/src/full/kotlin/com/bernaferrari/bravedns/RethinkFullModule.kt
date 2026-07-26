/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import com.bernaferrari.bravedns.di.RethinkAndroidModule
import com.bernaferrari.bravedns.download.AppDownloadManager
import com.bernaferrari.bravedns.iab.BillingModule
import com.bernaferrari.bravedns.rpnproxy.StateMachineDatabaseSyncService
import com.bernaferrari.bravedns.rpnproxy.SubscriptionStateMachineV2
import com.bernaferrari.bravedns.scheduler.ScheduleManager
import com.bernaferrari.bravedns.scheduler.WorkScheduler
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.ui.compose.about.AboutPreferences
import com.bernaferrari.bravedns.ui.compose.about.AboutTokenGenerator
import com.bernaferrari.bravedns.ui.compose.about.AndroidAboutPreferences
import com.bernaferrari.bravedns.ui.compose.about.AndroidAboutTokenGenerator
import com.bernaferrari.bravedns.ui.compose.about.AndroidAppMetadataProvider
import com.bernaferrari.bravedns.ui.compose.about.AndroidBugReportController
import com.bernaferrari.bravedns.ui.compose.about.AppMetadataProvider
import com.bernaferrari.bravedns.ui.compose.about.BugReportController
import com.bernaferrari.bravedns.ui.compose.apps.AndroidAppFirewallRuleMutator
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.OrbotHelper
import com.bernaferrari.bravedns.viewmodel.AppFirewallRuleMutator
import com.bernaferrari.bravedns.viewmodel.NetworkUptimeProvider
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * Full-app generated bindings that cannot exist in the browser demo: Android scheduling, download,
 * package metadata, billing state machinery, and the flavor-specific update/billing module.
 */
@Module(includes = [RethinkAndroidModule::class, BillingModule::class])
@Configuration
@ComponentScan("com.bernaferrari.bravedns.viewmodel")
class RethinkFullModule {
    @Single
    fun application(@Provided context: Context): Application = context.applicationContext as Application

    @Single
    fun contentResolver(@Provided context: Context): ContentResolver = context.contentResolver

    @Single
    fun nonStoreAppUpdater(@Provided persistentState: PersistentState): NonStoreAppUpdater =
        NonStoreAppUpdater(Constants.RETHINK_APP_UPDATE_CHECK, persistentState)

    @Single
    fun orbotHelper(
        @Provided context: Context,
        @Provided persistentState: PersistentState,
        @Provided appConfig: com.bernaferrari.bravedns.data.AppConfig,
    ): OrbotHelper = OrbotHelper(context, persistentState, appConfig)

    @Single
    fun appDownloadManager(
        @Provided context: Context,
        @Provided persistentState: PersistentState,
    ): AppDownloadManager = AppDownloadManager(context, persistentState)

    @Single
    fun workScheduler(@Provided context: Context): WorkScheduler = WorkScheduler(context)

    @Single
    fun scheduleManager(@Provided context: Context): ScheduleManager = ScheduleManager(context)

    @Single
    fun subscriptionStateMachine(): SubscriptionStateMachineV2 = SubscriptionStateMachineV2()

    @Single
    fun stateMachineDatabaseSyncService(): StateMachineDatabaseSyncService =
        StateMachineDatabaseSyncService()

    @Single
    fun metadataProvider(@Provided context: Context): AppMetadataProvider =
        AndroidAppMetadataProvider(context)

    @Single
    fun aboutPreferences(@Provided persistentState: PersistentState): AboutPreferences =
        AndroidAboutPreferences(persistentState)

    @Single
    fun bugReportController(
        @Provided context: Context,
        workScheduler: WorkScheduler,
    ): BugReportController = AndroidBugReportController(context, workScheduler)

    @Single
    fun aboutTokenGenerator(): AboutTokenGenerator = AndroidAboutTokenGenerator

    @Single
    fun appFirewallRuleMutator(): AppFirewallRuleMutator = AndroidAppFirewallRuleMutator

    @Single
    fun networkUptimeProvider(): NetworkUptimeProvider = NetworkUptimeProvider { VpnController.uptimeMs() }
}
