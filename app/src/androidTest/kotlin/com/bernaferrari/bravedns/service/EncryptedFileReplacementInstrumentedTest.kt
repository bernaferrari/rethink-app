@file:Suppress("DEPRECATION")
package com.bernaferrari.bravedns.service

import android.content.Context
import android.system.Os
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real Android Keystore/AndroidX encryption, never a plaintext stub or a shared-keyset reset. */
@RunWith(AndroidJUnit4::class)
class EncryptedFileReplacementInstrumentedTest {
    private lateinit var directory: File
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun prepare() { directory = File(context.filesDir, "encrypted-test-${UUID.randomUUID()}").apply { check(mkdirs()) } }
    @After fun remove() { directory.deleteRecursively() }

    @Test fun repeatedFacadeReplacementsDecryptUnderTheFinalLogicalFilename() {
        val target = File(directory, "identity.json")
        assertTrue(EncryptedFileManager.write(context, "original", target))
        assertEquals("original", EncryptedFileManager.read(context, target))
        val oldCiphertext = target.readBytes()
        val independent = File(directory, "independent.json")
        EncryptedFileManager.write(context, "independent-state", independent)
        assertTrue(EncryptedFileManager.write(context, "replacement", target))
        assertEquals("replacement", EncryptedFileManager.read(context, target))
        assertEquals("independent-state", EncryptedFileManager.read(context, independent))
        assertFalse(oldCiphertext.contentEquals(target.readBytes()))
        assertFalse(String(target.readBytes()).contains("replacement"))
    }

    @Test fun interruptedEncryptedWriteCloseAndRenameLeaveTheOldCiphertextReadable() {
        val target = File(directory, "state.json")
        EncryptedFileManager.write(context, "old-state", target)
        val oldCiphertext = target.readBytes()
        for (failure in listOf("write", "close", "rename")) {
            val writer = AtomicEncryptedFileWriter(
                stagingRoot = File(directory, "private-staging"),
                openEncrypted = { file -> failingOutput(file, failure) },
                sync = { FileOutputStream(it, true).use { output -> output.fd.sync() } },
                replace = { from, to ->
                    if (failure == "rename") throw IOException("Injected rename failure")
                    Os.rename(from.path, to.path)
                },
            )
            try { writer.write(target, "new-state".toByteArray()); fail("Expected injected $failure failure") }
            catch (_: IOException) { }
            assertArrayEquals(failure, oldCiphertext, target.readBytes())
            assertEquals(failure, "old-state", EncryptedFileManager.read(context, target))
            assertTrue(requireNotNull(File(directory, "private-staging").list()).isEmpty())
        }
    }

    private fun failingOutput(file: File, failure: String): OutputStream {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val encrypted = EncryptedFile.Builder(context, file, key,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build().openFileOutput()
        return object : OutputStream() {
            override fun write(value: Int) {
                encrypted.write(value)
                if (failure == "write") throw IOException("Injected encrypted write failure")
            }
            override fun close() {
                encrypted.close()
                if (failure == "close") throw IOException("Injected encrypted close failure")
            }
        }
    }
}
