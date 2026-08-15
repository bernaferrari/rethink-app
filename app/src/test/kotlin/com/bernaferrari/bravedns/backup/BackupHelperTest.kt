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

import com.bernaferrari.bravedns.database.AppDatabase
import com.bernaferrari.bravedns.database.LogDatabase
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupHelperTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `unzip extracts a normal zip into the target directory`() {
        val dest = tmp.newFolder("extract")
        val payload = "hello-backup"
        val zipBytes = zipOf("notes.txt" to payload.toByteArray())

        assertTrue(BackupHelper.unzip(ByteArrayInputStream(zipBytes), dest.path))
        val extracted = File(dest, "notes.txt")
        assertTrue(extracted.isFile)
        assertEquals(payload, extracted.readText())
    }

    @Test
    fun `unzip allows nested paths that stay inside the target`() {
        val dest = tmp.newFolder("extract")
        val zipBytes = zipOf("wireguard/1.conf" to "[Interface]\n".toByteArray())

        assertTrue(BackupHelper.unzip(ByteArrayInputStream(zipBytes), dest.path))
        val extracted = File(dest, "wireguard/1.conf")
        assertTrue(extracted.isFile)
        assertTrue(extracted.canonicalPath.startsWith(dest.canonicalPath + File.separator))
        assertEquals("[Interface]\n", extracted.readText())
    }

    @Test
    fun `unzip rejects a zip-slip entry with parent segments and writes nothing outside`() {
        val dest = tmp.newFolder("extract")
        val outside = File(dest.parentFile, "pwned.txt")
        if (outside.exists()) outside.delete()
        val zipBytes = zipOf("../pwned.txt" to "owned".toByteArray())

        assertFalse(BackupHelper.unzip(ByteArrayInputStream(zipBytes), dest.path))
        assertFalse(outside.exists())
        assertTrue(dest.walkTopDown().filter { it.isFile }.toList().isEmpty())
    }

    @Test
    fun `unzip rejects an absolute-path entry`() {
        val dest = tmp.newFolder("extract")
        val abs = File(tmp.root, "abs-pwned.txt")
        val zipBytes = zipOf(abs.absolutePath to "owned".toByteArray())

        assertFalse(BackupHelper.unzip(ByteArrayInputStream(zipBytes), dest.path))
        assertFalse(abs.exists())
    }

    @Test
    fun `unzip rejects a blank entry name`() {
        val dest = tmp.newFolder("extract")
        val zipBytes = zipOf("" to "nope".toByteArray())

        assertFalse(BackupHelper.unzip(ByteArrayInputStream(zipBytes), dest.path))
    }

    @Test
    fun `sanitizedZipEntryFile accepts a simple name and nested inside path`() {
        val dest = tmp.newFolder("extract")

        val simple = BackupHelper.sanitizedZipEntryFile(dest, "rethink_backup.txt")
        assertNotNull(simple)
        assertEquals(File(dest, "rethink_backup.txt").canonicalFile, simple)

        val nested = BackupHelper.sanitizedZipEntryFile(dest, "wireguard/1.conf")
        assertNotNull(nested)
        assertTrue(nested!!.canonicalPath.startsWith(dest.canonicalPath + File.separator))
    }

    @Test
    fun `sanitizedZipEntryFile rejects blank names parent segments and absolute paths`() {
        val dest = tmp.newFolder("extract")

        assertNull(BackupHelper.sanitizedZipEntryFile(dest, ""))
        assertNull(BackupHelper.sanitizedZipEntryFile(dest, "   "))
        assertNull(BackupHelper.sanitizedZipEntryFile(dest, "../outside.txt"))
        assertNull(BackupHelper.sanitizedZipEntryFile(dest, "foo/../../outside.txt"))
        assertNull(BackupHelper.sanitizedZipEntryFile(dest, File(tmp.root, "abs.txt").absolutePath))
    }

    @Test
    fun `checkpointRoomDatabases checkpoints app and log databases`() = runTest {
        val appDatabase = mockk<AppDatabase>(relaxed = true)
        val logDatabase = mockk<LogDatabase>(relaxed = true)

        BackupHelper.checkpointRoomDatabases(appDatabase, logDatabase)

        coVerify(exactly = 1) { appDatabase.checkPoint() }
        coVerify(exactly = 1) { logDatabase.checkPoint() }
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            entries.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
