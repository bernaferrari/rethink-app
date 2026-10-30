/* Copyright 2026 RethinkDNS and its authors */
@file:Suppress("DEPRECATION")
package com.bernaferrari.bravedns.service

import Logger
import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.bernaferrari.bravedns.util.Constants.Companion.WIREGUARD_FOLDER_NAME
import com.bernaferrari.bravedns.wireguard.Config
import com.bernaferrari.bravedns.wireguard.ConfigIo
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import com.bernaferrari.bravedns.wireguard.BadConfigException

sealed class EncryptionException(message: String, cause: Throwable) : Exception(message, cause) {
    class ReadFailed(cause: Throwable) : EncryptionException("Encrypted file read failed", cause)
    class WriteFailed(cause: Throwable) : EncryptionException("Encrypted file write failed", cause)
}

object EncryptedFileManager {
    fun readWireguardConfig(ctx: Context, fileToRead: String): Config? = try {
        ByteArrayInputStream(readByteArray(ctx, File(fileToRead))).use { ConfigIo.parse(it) }
    } catch (error: EncryptionException) {
        wireguardReadFailure(error)
    } catch (error: BadConfigException) {
        wireguardReadFailure(error)
    } catch (error: IOException) {
        wireguardReadFailure(error)
    }

    private fun wireguardReadFailure(error: Exception): Config? {
        Logger.w(Logger.LOG_TAG_PROXY, "Unable to read encrypted WireGuard configuration", error)
        return null
    }

    fun read(ctx: Context, file: File): String = readByteArray(ctx, file).toString(StandardCharsets.UTF_8)

    @Throws(EncryptionException::class)
    fun readByteArray(ctx: Context, file: File): ByteArray = try {
        encryptedFile(ctx, file).openFileInput().use { it.readBytes() }
    } catch (error: GeneralSecurityException) {
        readFailure(file, error)
    } catch (error: ProviderException) {
        readFailure(file, error)
    } catch (error: IOException) {
        readFailure(file, error)
    } catch (error: SecurityException) {
        readFailure(file, error)
    }

    private fun readFailure(file: File, error: Exception): Nothing {
        Logger.w(Logger.LOG_TAG_PROXY, "Unable to read encrypted file: ${file.path}", error)
        throw EncryptionException.ReadFailed(error)
    }

    fun writeWireguardConfig(ctx: Context, cfg: String, fileName: String): Boolean =
        writeConfiguration(ctx, cfg, File(ctx.filesDir, WIREGUARD_FOLDER_NAME), fileName)

    fun writeTcpConfig(ctx: Context, cfg: String, fileName: String): Boolean = writeConfiguration(
        ctx, cfg, File(File(ctx.filesDir, TcpProxyHelper.TCP_FOLDER_NAME), TcpProxyHelper.getFolderName()), fileName,
    )

    private fun writeConfiguration(ctx: Context, cfg: String, directory: File, fileName: String): Boolean = try {
        // Do not pre-create the destination: failed first saves must not leave an empty config.
        write(ctx, cfg, File(directory, fileName))
    } catch (error: EncryptionException) {
        Logger.w(Logger.LOG_TAG_PROXY, "Unable to save encrypted configuration", error)
        false
    }

    fun write(ctx: Context, data: String, file: File): Boolean = write(ctx, data.toByteArray(StandardCharsets.UTF_8), file)

    @Throws(EncryptionException::class)
    fun write(ctx: Context, data: ByteArray, file: File): Boolean = try {
        atomicWriter(ctx).write(file, data)
        true
    } catch (error: GeneralSecurityException) {
        writeFailure(file, error)
    } catch (error: ProviderException) {
        writeFailure(file, error)
    } catch (error: IOException) {
        writeFailure(file, error)
    } catch (error: ErrnoException) {
        writeFailure(file, error)
    } catch (error: SecurityException) {
        writeFailure(file, error)
    }

    private fun writeFailure(file: File, error: Exception): Nothing {
        Logger.w(Logger.LOG_TAG_PROXY, "Unable to replace encrypted file: ${file.path}", error)
        throw EncryptionException.WriteFailed(error)
    }

    internal fun atomicWriter(ctx: Context): AtomicEncryptedFileWriter = AtomicEncryptedFileWriter(
        stagingRoot = ctx.noBackupFilesDir,
        openEncrypted = { encryptedFile(ctx, it).openFileOutput() },
        sync = { file -> FileOutputStream(file, true).use { it.fd.sync() } },
        replace = { staging, target ->
            Os.rename(staging.absolutePath, target.absolutePath)
            // The rename is the commit point. A directory-sync failure must not report that
            // replacement failed (or try to restore/delete the now committed ciphertext).
            try {
                val directory = Os.open(requireNotNull(target.parent), OsConstants.O_RDONLY, 0)
                try { Os.fsync(directory) } finally { Os.close(directory) }
            } catch (error: ErrnoException) {
                Logger.w(Logger.LOG_TAG_PROXY, "Encrypted replacement committed; directory sync failed", error)
            }
        },
        cleanupFailed = { Logger.w(Logger.LOG_TAG_PROXY, "Unable to remove private encrypted staging directory", it) },
    )

    private fun encryptedFile(ctx: Context, file: File): EncryptedFile {
        val masterKey = MasterKey.Builder(ctx.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedFile.Builder(ctx.applicationContext, file, masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
    }
}
