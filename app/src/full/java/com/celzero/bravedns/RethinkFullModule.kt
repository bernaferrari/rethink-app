/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.celzero.bravedns

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import com.celzero.bravedns.di.RethinkAndroidModule
import com.celzero.bravedns.download.AppDownloadManager
import com.celzero.bravedns.iab.BillingModule
import com.celzero.bravedns.rpnproxy.StateMachineDatabaseSyncService
import com.celzero.bravedns.rpnproxy.SubscriptionStateMachineV2
import com.celzero.bravedns.scheduler.ScheduleManager
import com.celzero.bravedns.scheduler.WorkScheduler
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.about.AboutPreferences
import com.celzero.bravedns.ui.compose.about.AboutTokenGenerator
import com.celzero.bravedns.ui.compose.about.AndroidAboutPreferences
import com.celzero.bravedns.ui.compose.about.AndroidAboutTokenGenerator
import com.celzero.bravedns.ui.compose.about.AndroidAppMetadataProvider
import com.celzero.bravedns.ui.compose.about.AndroidBugReportController
import com.celzero.bravedns.ui.compose.about.AppMetadataProvider
import com.celzero.bravedns.ui.compose.about.BugReportController
import com.celzero.bravedns.ui.compose.apps.AndroidAppFirewallRuleMutator
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.OrbotHelper
import com.celzero.bravedns.viewmodel.AppFirewallRuleMutator
import com.celzero.bravedns.viewmodel.NetworkUptimeProvider
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
@ComponentScan("com.celzero.bravedns.viewmodel")
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
        @Provided appConfig: com.celzero.bravedns.data.AppConfig,
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
