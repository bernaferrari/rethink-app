/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.service

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Commit a fully closed, synced ciphertext with one atomic replacement. The staged file keeps
 * the destination basename: AndroidX EncryptedFile authenticates that name, not just its contents.
 * Staging lives outside configuration directories and automatic backups. No keyset is reset.
 */
internal class AtomicEncryptedFileWriter(
    private val stagingRoot: File,
    private val openEncrypted: (File) -> OutputStream,
    private val sync: (File) -> Unit,
    private val replace: (File, File) -> Unit,
    private val cleanup: (File) -> Boolean = { it.deleteRecursively() },
    private val cleanupFailed: (Exception) -> Unit = {},
) {
    fun write(target: File, data: ByteArray) {
        // Fixed stripes bound memory while serializing every replacement of the same canonical
        // path, including calls through different facade/writer instances.
        val stripe = (target.canonicalPath.hashCode() and Int.MAX_VALUE) % locks.size
        synchronized(locks[stripe]) { replaceLocked(target.absoluteFile, data) }
    }

    private fun replaceLocked(target: File, data: ByteArray) {
        val parent = requireNotNull(target.parentFile)
        ensureDirectory(parent)
        ensureDirectory(stagingRoot)
        val directory = File(stagingRoot, "encrypted-write-${UUID.randomUUID()}")
        if (!directory.mkdir()) throw IOException("Unable to create private encrypted staging directory")
        try {
            val staged = File(directory, target.name)
            openEncrypted(staged).use { output -> output.write(data); output.flush() }
            // Closing the encrypted stream finalizes its authentication tag before this sync.
            sync(staged)
            replace(staged, target)
        } finally {
            cleanStaging(directory)
        }
    }

    // Cleanup is ancillary to the atomic commit. Preserve the write/close/rename exception,
    // or successful committed result, even if filesystem cleanup or its diagnostic callback fails.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun cleanStaging(directory: File) {
        val failure = try {
            if (cleanup(directory)) null else IOException("Unable to remove encrypted staging directory")
        } catch (error: Exception) { error }
        if (failure != null) {
            try { cleanupFailed(failure) } catch (_: Exception) {
                // Logging cannot be allowed to change the commit outcome either.
            }
        }
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Unable to create encrypted file directory")
        }
    }

    private companion object {
        val locks = Array(64) { Any() }
    }
}
