/*
 * Copyright 2023 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("DEPRECATION")
package com.bernaferrari.bravedns.service

import Logger
import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.bernaferrari.bravedns.util.Constants.Companion.WIREGUARD_FOLDER_NAME
import com.bernaferrari.bravedns.wireguard.Config
import com.bernaferrari.bravedns.wireguard.ConfigIo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

sealed class EncryptionException(message: String, cause: Throwable) : Exception(message, cause) {
    class ReadFailed(cause: Throwable) : EncryptionException("Encrypted file read failed", cause)
    class WriteFailed(cause: Throwable) : EncryptionException("Encrypted file write failed", cause)
}

object EncryptedFileManager {
    // Although you can define your own key generation parameter specification, it's
    // recommended that you use the value specified here.
    // private val keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC
    // private val mainKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)

    fun readWireguardConfig(ctx: Context, fileToRead: String): Config? {
        var config: Config? = null
        var inputStream: InputStream? = null
        var ist: ByteArrayInputStream? = null
        var bos: ByteArrayOutputStream? = null
        try {
            val dir = File(fileToRead)
            val masterKey =
                MasterKey.Builder(ctx.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            val encryptedFile =
                EncryptedFile.Builder(
                        ctx.applicationContext,
                        dir,
                        masterKey,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    )
                    .build()

            Logger.d(Logger.LOG_TAG_PROXY, "Encrypted File Read: ${dir.absolutePath}")
            inputStream = encryptedFile.openFileInput()
            bos = ByteArrayOutputStream()
            var nextByte: Int = inputStream.read()
            while (nextByte != -1) {
                bos.write(nextByte)
                nextByte = inputStream.read()
            }


            val plaintext: ByteArray = bos.toByteArray()
            // convert output stream to input stream
            ist = ByteArrayInputStream(plaintext)

            config = ConfigIo.parse(ist)
        } catch (e: Exception) {
            Logger.w(Logger.LOG_TAG_PROXY, "Encrypted File Read: ${e.message}")
        } finally {
            try {
                inputStream?.close()
                ist?.close()
                bos?.flush()
                bos?.close()
            } catch (_: Exception) { } // no-op
        }

        return config
    }

    fun read(ctx: Context, file: File): String {
        return readByteArray(ctx, file).toString(Charset.defaultCharset())
    }

    @Throws(EncryptionException::class)
    fun readByteArray(ctx: Context, file: File): ByteArray {
        try {
            val masterKey =
                MasterKey.Builder(ctx.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            val encryptedFile =
                EncryptedFile.Builder(
                        ctx.applicationContext,
                        file,
                        masterKey,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    )
                    .build()

            return encryptedFile.openFileInput().use { it.readBytes() }
        } catch (e: Exception) {
            Logger.w(Logger.LOG_TAG_PROXY, "Encrypted File Read: ${e.message}")
            throw EncryptionException.ReadFailed(e)
        }
    }

    fun writeWireguardConfig(ctx: Context, cfg: String, fileName: String): Boolean {
        try {
            val dir =
                File(
                    ctx.filesDir.canonicalPath +
                        File.separator +
                        WIREGUARD_FOLDER_NAME +
                        File.separator
                )
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val fileToWrite = File(dir, fileName)
            if (!fileToWrite.exists()) {
                fileToWrite.createNewFile()
            }
            val bytes = cfg.toByteArray(StandardCharsets.UTF_8)
            return write(ctx, bytes, fileToWrite)
        } catch (e: Exception) {
            Logger.w(Logger.LOG_TAG_PROXY, "err write wg file: ${e.message}")
        }
        return false
    }

    fun writeTcpConfig(ctx: Context, cfg: String, fileName: String): Boolean {
        try {
            val dir =
                File(
                    ctx.filesDir.canonicalPath +
                        File.separator +
                        TcpProxyHelper.TCP_FOLDER_NAME +
                        File.separator +
                        TcpProxyHelper.getFolderName()
                )
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val fileToWrite = File(dir, fileName)
            if (!fileToWrite.exists()) {
                fileToWrite.createNewFile()
            }
            return write(ctx, cfg, fileToWrite)
        } catch (e: Exception) {
            Logger.w(Logger.LOG_TAG_PROXY, "err write tcp config: ${e.message}")
        }
        return false
    }

    fun write(ctx: Context, data: String, file: File): Boolean {
        return write(ctx, data.toByteArray(StandardCharsets.UTF_8), file)
    }


    fun write(ctx: Context, data: ByteArray, file: File): Boolean {
        try {
            Logger.d(Logger.LOG_TAG_PROXY, "write into $file")
            val masterKey =
                MasterKey.Builder(ctx.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            val encryptedFile =
                EncryptedFile.Builder(
                        ctx.applicationContext,
                        file,
                        masterKey,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    )
                    .build()

            if (file.exists()) {
                file.delete()
            }

            encryptedFile.openFileOutput().apply {
                write(data)
                flush()
                close()
            }
            return true
        } catch (e: Exception) {
            Logger.w(Logger.LOG_TAG_PROXY, "err writing into file ${file.path}, ${e.message}")
            throw EncryptionException.WriteFailed(e)
        }
    }
}
