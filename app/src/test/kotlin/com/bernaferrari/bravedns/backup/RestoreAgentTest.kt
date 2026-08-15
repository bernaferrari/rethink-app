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
import android.database.sqlite.SQLiteDatabase
import androidx.work.WorkerParameters
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.CREATED_TIME
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.METADATA_FILENAME
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.PACKAGE_NAME
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.SHARED_PREFS_BACKUP_FILE_NAME
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.VERSION
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.AppDatabase
import com.bernaferrari.bravedns.database.LogDatabase
import com.bernaferrari.bravedns.service.PersistentState
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
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
import java.io.File
import java.io.ObjectOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RestoreAgentTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var persistentState: PersistentState
    private lateinit var agent: RestoreAgent

    @Before
    fun setUp() {
        context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        persistentState = mockk(relaxed = true)
        every { persistentState.appVersion } returns 30

        startKoin {
            modules(
                module {
                    single { persistentState }
                    single { mockk<AppDatabase>(relaxed = true) }
                    single { mockk<LogDatabase>(relaxed = true) }
                    single { mockk<AppConfig>(relaxed = true) }
                    single<Application> { context.applicationContext as Application }
                    single<Context> { context }
                }
            )
        }
        agent = RestoreAgent(context, mockk<WorkerParameters>(relaxed = true))
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun `prefs appVersion 24 is compatible`() {
        val dir = prefsDir(mapOf(PersistentState.APP_VERSION to 24))
        assertTrue(isMetadataCompatible(dir))
    }

    @Test
    fun `prefs appVersion greater than 24 is compatible`() {
        val dir = prefsDir(mapOf(PersistentState.APP_VERSION to 30))
        assertTrue(isMetadataCompatible(dir))
    }

    @Test
    fun `prefs appVersion below 24 is incompatible`() {
        val dir = prefsDir(mapOf(PersistentState.APP_VERSION to 23))
        assertFalse(isMetadataCompatible(dir))
    }

    @Test
    fun `prefs without appVersion is incompatible`() {
        val dir = prefsDir(mapOf("unrelated" to true))
        assertFalse(isMetadataCompatible(dir))
    }

    @Test
    fun `metadata version 24 is compatible when installed app is newer`() {
        every { persistentState.appVersion } returns 30
        assertTrue(isVersionSupported(metadata(version = 24)))
    }

    @Test
    fun `metadata version below 24 is incompatible`() {
        assertFalse(isVersionSupported(metadata(version = 23)))
    }

    @Test
    fun `metadata version newer than installed app is incompatible`() {
        every { persistentState.appVersion } returns 24
        assertFalse(isVersionSupported(metadata(version = 25)))
    }

    @Test
    fun `metadata without version key is incompatible`() {
        assertFalse(isVersionSupported("package:com.bernaferrari.bravedns|createdTs:1700000000000"))
    }

    @Test
    fun `validateMetadata accepts prefs at min version`() {
        val dir = prefsDir(mapOf(PersistentState.APP_VERSION to 24))
        assertTrue(validateMetadata(dir))
    }

    @Test
    fun `validateMetadata falls back to metadata file when prefs are too old`() {
        val dir = prefsDir(mapOf(PersistentState.APP_VERSION to 23))
        File(dir, METADATA_FILENAME).writeText(metadata(version = 24))
        every { persistentState.appVersion } returns 30
        assertTrue(validateMetadata(dir))
    }

    @Test
    fun `validateMetadata rejects prefs and metadata both below 24`() {
        val dir = prefsDir(mapOf(PersistentState.APP_VERSION to 23))
        File(dir, METADATA_FILENAME).writeText(metadata(version = 23))
        assertFalse(validateMetadata(dir))
    }

    @Test
    fun `restore rejects app database whose user_version is newer than SCHEMA_VERSION`() {
        val db = sqliteDb(AppDatabase.DATABASE_NAME, AppDatabase.SCHEMA_VERSION + 1)
        assertFalse(validateRestoredSchemaVersions(arrayOf(db)))
    }

    @Test
    fun `restore rejects log database whose user_version is newer than SCHEMA_VERSION`() {
        val db = sqliteDb(LogDatabase.LOGS_DATABASE_NAME, LogDatabase.SCHEMA_VERSION + 1)
        assertFalse(validateRestoredSchemaVersions(arrayOf(db)))
    }

    @Test
    fun `restore accepts app and log databases at current schema versions`() {
        val app = sqliteDb(AppDatabase.DATABASE_NAME, AppDatabase.SCHEMA_VERSION)
        val logs = sqliteDb(LogDatabase.LOGS_DATABASE_NAME, LogDatabase.SCHEMA_VERSION)
        assertTrue(validateRestoredSchemaVersions(arrayOf(app, logs)))
    }

    @Test
    fun `restore accepts older schemas that this build can migrate`() {
        val app = sqliteDb(AppDatabase.DATABASE_NAME, AppDatabase.SCHEMA_VERSION - 1)
        val logs = sqliteDb(LogDatabase.LOGS_DATABASE_NAME, LogDatabase.SCHEMA_VERSION - 1)
        assertTrue(validateRestoredSchemaVersions(arrayOf(app, logs)))
    }

    @Test
    fun `sqlite sidecars are ignored by the schema version gate`() {
        val app = sqliteDb(AppDatabase.DATABASE_NAME, AppDatabase.SCHEMA_VERSION)
        val wal = File(tmp.newFolder(), "${AppDatabase.DATABASE_NAME}-wal").apply { writeText("not-sqlite") }
        val shm = File(tmp.newFolder(), "${AppDatabase.DATABASE_NAME}-shm").apply { writeText("not-sqlite") }
        val journal = File(tmp.newFolder(), "${AppDatabase.DATABASE_NAME}-journal").apply { writeText("not-sqlite") }
        assertTrue(validateRestoredSchemaVersions(arrayOf(app, wal, shm, journal)))
    }

    @Test
    fun `unreadable sqlite file is rejected`() {
        val bogus = File(tmp.newFolder(), AppDatabase.DATABASE_NAME).apply { writeText("not a database") }
        assertFalse(validateRestoredSchemaVersions(arrayOf(bogus)))
    }

    private fun isMetadataCompatible(dir: File): Boolean {
        return invoke("isMetadataCompatible", String::class.java, dir.path) as Boolean
    }

    private fun isVersionSupported(metadata: String): Boolean {
        return invoke("isVersionSupported", String::class.java, metadata) as Boolean
    }

    private fun validateMetadata(dir: File): Boolean {
        return invoke("validateMetadata", String::class.java, dir.path) as Boolean
    }

    private fun validateRestoredSchemaVersions(files: Array<File>): Boolean {
        val method =
            RestoreAgent::class.java.getDeclaredMethod(
                "validateRestoredSchemaVersions",
                Array<File>::class.java,
            )
        method.isAccessible = true
        return method.invoke(agent, files) as Boolean
    }

    private fun invoke(name: String, paramType: Class<*>, arg: Any?): Any? {
        val method = RestoreAgent::class.java.getDeclaredMethod(name, paramType)
        method.isAccessible = true
        return method.invoke(agent, arg)
    }

    private fun prefsDir(prefs: Map<String, Any?>): File {
        val dir = tmp.newFolder()
        ObjectOutputStream(File(dir, SHARED_PREFS_BACKUP_FILE_NAME).outputStream()).use { out ->
            out.writeObject(HashMap(prefs))
        }
        return dir
    }

    private fun metadata(version: Int): String {
        return "$VERSION:$version|$PACKAGE_NAME:com.bernaferrari.bravedns|$CREATED_TIME:1700000000000"
    }

    private fun sqliteDb(name: String, userVersion: Int): File {
        val file = File(tmp.newFolder(), name)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.version = userVersion
            db.execSQL("CREATE TABLE IF NOT EXISTS t (id INTEGER PRIMARY KEY)")
        }
        return file
    }
}
