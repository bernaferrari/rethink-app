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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
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
    fun `failed snapshot leaves running VPN untouched`() = kotlinx.coroutines.test.runTest {
        io.mockk.mockkObject(DatabaseSnapshot)
        io.mockk.mockkObject(com.bernaferrari.bravedns.service.VpnController)
        every { DatabaseSnapshot.create(any(), any(), any()) } returns false
        every { com.bernaferrari.bravedns.service.VpnController.isOn() } returns true
        every { logDatabase.dnsLogDAO().logsCount() } returns kotlinx.coroutines.flow.flowOf(0L)
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.inputData } returns androidx.work.Data.Builder().putString(
            BackupHelper.DATA_BUILDER_BACKUP_URI, android.net.Uri.fromFile(java.io.File(tmp.root, "backup.zip")).toString()
        ).build()
        val worker = BackupAgent(context, params)
        org.junit.Assert.assertEquals(androidx.work.ListenableWorker.Result.failure(), worker.doWork())
        coVerify(exactly = 0) {
            com.bernaferrari.bravedns.service.VpnController.stopAndAwait(any(), any())
        }
        io.mockk.verify(exactly = 0) { com.bernaferrari.bravedns.service.VpnController.start(any(), any()) }
    }

    @Test
    fun `overlapping exports use isolated staging and clean failures independently`() = kotlinx.coroutines.test.runTest {
        io.mockk.mockkObject(DatabaseSnapshot)
        val directories = java.util.concurrent.CopyOnWriteArrayList<java.io.File>()
        every { logDatabase.dnsLogDAO().logsCount() } returns kotlinx.coroutines.flow.flowOf(0L)
        every { DatabaseSnapshot.create(any(), any(), any()) } answers {
            directories += secondArg<java.io.File>().parentFile
            false
        }
        fun worker(): BackupAgent {
            val params = mockk<WorkerParameters>(relaxed = true)
            every { params.id } returns java.util.UUID.randomUUID()
            every { params.inputData } returns androidx.work.Data.Builder().putString(
                BackupHelper.DATA_BUILDER_BACKUP_URI, android.net.Uri.fromFile(java.io.File(tmp.root, "backup.zip")).toString()
            ).build()
            return BackupAgent(context, params)
        }
        val workers = listOf(worker(), worker())
        workers.map { async { it.doWork() } }.awaitAll()
        org.junit.Assert.assertEquals(2, directories.distinct().size)
        directories.forEach { org.junit.Assert.assertFalse(it.exists()) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `backup waits for restore ownership before capturing preferences or databases`() = kotlinx.coroutines.test.runTest {
        val dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        io.mockk.mockkStatic(kotlinx.coroutines.Dispatchers::class)
        every { kotlinx.coroutines.Dispatchers.IO } returns dispatcher
        io.mockk.mockkObject(DatabaseSnapshot)
        every { DatabaseSnapshot.create(any(), any(), any()) } returns false
        every { logDatabase.dnsLogDAO().logsCount() } returns kotlinx.coroutines.flow.flowOf(0L)
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finish = kotlinx.coroutines.CompletableDeferred<Unit>()
        val restore = launch(dispatcher) {
            BackupRestoreFileGate.withSourceFiles { entered.complete(Unit); finish.await() }
        }
        entered.await()
        val parameters = mockk<WorkerParameters>(relaxed = true)
        every { parameters.id } returns java.util.UUID.randomUUID()
        every { parameters.inputData } returns androidx.work.workDataOf(
            BackupHelper.DATA_BUILDER_BACKUP_URI to "content://backup/wait.zip")
        val backup = async(dispatcher) { BackupAgent(context, parameters).doWork() }
        try {
            org.junit.Assert.assertFalse(backup.isCompleted)
            io.mockk.verify(exactly = 0) { persistentState.snapshotPreferences() }
            coVerify(exactly = 0) { appDatabase.appInfoDAO().getAppCount() }
            finish.complete(Unit)
            backup.await()
            io.mockk.verify(exactly = 1) { persistentState.snapshotPreferences() }
            coVerify(exactly = 1) { appDatabase.appInfoDAO().getAppCount() }
        } finally {
            finish.complete(Unit)
            restore.cancel()
            backup.cancel()
            restore.join()
            backup.join()
        }
    }

    private fun invokeBackupMetadata(): String {
        val method = BackupAgent::class.java.getDeclaredMethod("backupMetadata")
        method.isAccessible = true
        return method.invoke(agent) as String
    }
}
