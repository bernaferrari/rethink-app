/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package com.bernaferrari.bravedns.backup

import android.app.Application
import android.content.Context
import androidx.work.WorkerParameters
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.CREATED_TIME
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.AppDatabase
import com.bernaferrari.bravedns.database.LogDatabase
import com.bernaferrari.bravedns.database.WgConfigFilesRepository
import com.bernaferrari.bravedns.service.PersistentState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class BackupAgentTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var persistentState: PersistentState
    private lateinit var appDatabase: AppDatabase
    private lateinit var logDatabase: LogDatabase
    private lateinit var agent: BackupAgent

    @Before
    fun setUp() {
        context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        persistentState = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)
        logDatabase = mockk(relaxed = true)

        every { persistentState.appVersion } returns 30
        every { persistentState.snapshotPreferences() } returns mapOf(PersistentState.APP_VERSION to 30)
        coEvery { appDatabase.checkPoint() } just runs
        coEvery { logDatabase.checkPoint() } just runs

        startKoin {
            modules(
                module {
                    single { persistentState }
                    single { appDatabase }
                    single { logDatabase }
                    single { mockk<AppConfig>(relaxed = true) }
                    single { mockk<WgConfigFilesRepository>(relaxed = true) }
                    single<Application> { context.applicationContext as Application }
                    single<Context> { context }
                }
            )
        }
        agent = BackupAgent(context, mockk<WorkerParameters>(relaxed = true))
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun `CREATED_TIME is wall-clock epoch millis not elapsedRealtime`() {
        val before = System.currentTimeMillis()
        val metadata = invokeBackupMetadata()
        val after = System.currentTimeMillis()

        val created = metadata
            .split("|")
            .map { it.split(":", limit = 2) }
            .first { it[0] == CREATED_TIME }[1]
            .toLong()

        // elapsedRealtime is typically hours/days of uptime; wall clock is post-2020.
        assertTrue("createdTs=$created is not a wall-clock epoch", created > 1_600_000_000_000L)
        assertTrue("createdTs=$created is before the test started", created >= before)
        assertTrue("createdTs=$created is in the future", created <= after + 1_000L)
    }

    @Test
    fun `backup checkpoints Room databases before copying files`() {
        val destDir = tmp.newFolder("backup-dest")
        val method = BackupAgent::class.java.getDeclaredMethod("saveDatabasesToFile", String::class.java)
        method.isAccessible = true
        method.invoke(agent, destDir.path)

        coVerify(exactly = 1) { appDatabase.checkPoint() }
        coVerify(exactly = 1) { logDatabase.checkPoint() }
    }

    private fun invokeBackupMetadata(): String {
        val method = BackupAgent::class.java.getDeclaredMethod("backupMetadata")
        method.isAccessible = true
        return method.invoke(agent) as String
    }
}
