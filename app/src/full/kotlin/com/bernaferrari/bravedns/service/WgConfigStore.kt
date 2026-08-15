/*
 * Copyright 2026 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.service

import Logger
import Logger.LOG_TAG_PROXY
import android.content.Context
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.TEMP_WG_DIR
import com.bernaferrari.bravedns.database.WgConfigFiles
import com.bernaferrari.bravedns.database.WgConfigFilesRepository
import com.bernaferrari.bravedns.util.Constants.Companion.WIREGUARD_FOLDER_NAME
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.wireguard.BadConfigException
import com.bernaferrari.bravedns.wireguard.Config
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.charset.StandardCharsets

internal data class WgPersistResult(
    val path: String,
    val existing: WgConfigFiles?,
)

/**
 * File + Room persistence for WireGuard configs.
 *
 * Owns encrypted-to-plaintext migration, in-place decrypt, write/delete of
 * `.conf` files, and backup restore from [TEMP_WG_DIR]. Does not own the
 * in-memory registry.
 */
internal class WgConfigStore(
    private val context: Context,
    private val db: WgConfigFilesRepository,
    private val persistentState: PersistentState,
) {
    private val migrationMutex = Mutex()

    fun configFileName(id: Int): String = "wg$id.conf"

    fun configDirPath(): String {
        return context.filesDir.absolutePath +
            File.separator +
            WIREGUARD_FOLDER_NAME +
            File.separator
    }

    /**
     * Migration that converts encrypted wg config files created by older app versions into plain
     * text files. Files already containing a valid Interface section are skipped,
     * completion is tracked by [PersistentState.wireguardPlainFileMigrationDone].
     *
     * If a file cannot be decrypted the config is marked inactive in the database and
     * removed from the tunnel. The encrypted file is deleted to prevent repeated failures.
     */
    suspend fun migrateEncryptedConfigsIfNeeded() {
        if (persistentState.wireguardPlainFileMigrationDone) {
            return
        }

        migrationMutex.withLock {
            if (persistentState.wireguardPlainFileMigrationDone) {
                return@withLock
            }

            Logger.i(LOG_TAG_PROXY, "migrate: starting encrypted wg config migration")
            val configs = db.getWgConfigs()
            var migratedCount = 0
            var failedCount = 0

            configs.forEach { entry ->
                val file = File(entry.configPath)
                if (!file.exists()) {
                    Logger.w(LOG_TAG_PROXY, "migrate: file missing for config ${entry.id}: ${entry.configPath}")
                    return@forEach
                }

                if (WireguardConfigFileManager.isPlaintextConfig(file)) {
                    Logger.v(LOG_TAG_PROXY, "migrate: config ${entry.id} is already plaintext")
                    return@forEach
                }

                try {
                    val bytes = EncryptedFileManager.readByteArray(context, file)
                    file.writeBytes(bytes)
                    migratedCount++
                    Logger.i(LOG_TAG_PROXY, "migrate: converted encrypted config ${entry.id} to plaintext")
                } catch (e: EncryptionException) {
                    failedCount++
                    markMigrateFailed(entry.id, entry.isActive, file, e)
                } catch (e: java.io.IOException) {
                    failedCount++
                    markMigrateFailed(entry.id, entry.isActive, file, e)
                } catch (e: SecurityException) {
                    failedCount++
                    markMigrateFailed(entry.id, entry.isActive, file, e)
                }
            }

            persistentState.wireguardPlainFileMigrationDone = true
            Logger.i(LOG_TAG_PROXY, "migrate: wg config migration complete: migrated=$migratedCount, failed=$failedCount")
        }
    }

    private suspend fun markMigrateFailed(id: Int, isActive: Boolean, file: File, cause: Exception) {
        Logger.e(LOG_TAG_PROXY, "migrate: failed to decrypt config $id, marking inactive", cause)
        if (isActive) {
            db.disableConfig(id)
            VpnController.removeWireGuardProxy(id)
        }
        if (file.exists() && !file.delete()) {
            Logger.w(LOG_TAG_PROXY, "migrate: could not delete unreadable file ${file.absolutePath}")
        }
    }

    /**
     * Ensures the file at [path] is a plaintext WireGuard config.
     *
     * @return true if the file exists and is plaintext (either originally or after decryption),
     *         false if the file is missing or could not be decrypted.
     */
    suspend fun ensurePlaintextConfigFile(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        if (WireguardConfigFileManager.isPlaintextConfig(file)) return true

        return try {
            Logger.i(LOG_TAG_PROXY, "found encrypted wg config at $path, decrypting in place")
            val bytes = EncryptedFileManager.readByteArray(context, file)
            file.writeBytes(bytes)
            Logger.i(LOG_TAG_PROXY, "decrypted wg config in place: $path")
            true
        } catch (e: EncryptionException) {
            Logger.e(LOG_TAG_PROXY, "failed to decrypt wg config at $path", e)
            false
        } catch (e: java.io.IOException) {
            Logger.e(LOG_TAG_PROXY, "unexpected error decrypting wg config at $path", e)
            false
        } catch (e: SecurityException) {
            Logger.e(LOG_TAG_PROXY, "unexpected error decrypting wg config at $path", e)
            false
        }
    }

    fun parseConfigFile(path: String): Result<Config> {
        return try {
            val bytes = WireguardConfigFileManager.read(File(path))
            Result.success(Config.parse(bytes.toString(StandardCharsets.UTF_8)))
        } catch (e: BadConfigException) {
            Logger.e(LOG_TAG_PROXY, "Config parse failure for wg config: $path", e)
            Result.failure(e)
        } catch (e: java.io.IOException) {
            Logger.e(LOG_TAG_PROXY, "Config parse failure for wg config: $path", e)
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Logger.e(LOG_TAG_PROXY, "Config parse failure for wg config: $path", e)
            Result.failure(e)
        }
    }

    suspend fun persist(cfg: Config, serverResponse: String = ""): WgPersistResult {
        val parsedCfg = cfg.toWgQuickString()
        val fileName = configFileName(cfg.getId())
        try {
            WireguardConfigFileManager.write(context, parsedCfg, fileName)
        } catch (e: java.io.IOException) {
            Logger.e(LOG_TAG_PROXY, "I/O failure writing wg config: ${cfg.getId()}", e)
            throw e
        }
        val path = configDirPath() + fileName
        Logger.i(LOG_TAG_PROXY, "writing wg config to file: $path")
        val file = db.isConfigAdded(cfg.getId())
        if (file == null) {
            val wgf =
                WgConfigFiles(
                    cfg.getId(),
                    cfg.getName(),
                    path,
                    serverResponse,
                    isActive = false,
                    isCatchAll = false,
                    isLockdown = false,
                    oneWireGuard = false,
                    useOnlyOnMetered = false,
                    isDeletable = true,
                    ssidEnabled = false,
                    ssids = ""
                )
            db.insert(wgf)
        } else {
            file.name = cfg.getName()
            file.configPath = path
            file.serverResponse = serverResponse
            db.update(file)
        }
        return WgPersistResult(path, file)
    }

    fun deleteConfigFile(id: Int) {
        val fileName = configFileName(id)
        val file = File(configDirPath(), fileName)
        WireguardConfigFileManager.delete(file)
    }

    suspend fun performRestore() {
        try {
            val tempDir = File(context.filesDir, TEMP_WG_DIR)
            val dbconfs = db.getWgConfigs()

            val tempFiles = tempDir.listFiles()?.filter { it.name.endsWith(".conf") } ?: emptyList()
            Logger.i(
                LOG_TAG_PROXY,
                "performRestore: temp dir .conf files: ${tempFiles.size} (${tempFiles.joinToString { it.name }}), db size: ${dbconfs.size}"
            )

            if (tempFiles.isEmpty() && dbconfs.isEmpty()) {
                Logger.i(LOG_TAG_PROXY, "performRestore: no temp files and no db entries, nothing to restore")
                return
            }

            if (tempFiles.isEmpty()) {
                Logger.w(LOG_TAG_PROXY, "performRestore: db has ${dbconfs.size} entries but no temp .conf files found, cannot restore")
                return
            }

            if (dbconfs.isEmpty()) {
                Logger.w(
                    LOG_TAG_PROXY,
                    "performRestore: ${tempFiles.size} temp .conf files found but db has 0 entries. Files cannot be restored without db metadata. Preserving temp files for manual recovery."
                )
                return
            }

            val restoredIds = mutableSetOf<Int>()
            val unmatchedFiles = mutableListOf<String>()

            dbconfs.forEach { c ->
                if (restoreOneConfig(c, tempDir)) {
                    restoredIds.add(c.id)
                }
            }

            tempFiles.forEach { tf ->
                val id = tf.nameWithoutExtension.toIntOrNull()
                if (id == null || id !in restoredIds) {
                    unmatchedFiles.add(tf.name)
                }
            }

            if (unmatchedFiles.isNotEmpty()) {
                Logger.w(
                    LOG_TAG_PROXY,
                    "performRestore: ${unmatchedFiles.size} temp .conf files unmatched to any db entry: ${unmatchedFiles.joinToString()}. Preserving temp dir for manual recovery."
                )
                return
            }

            Logger.i(LOG_TAG_PROXY, "performRestore: successfully restored ${restoredIds.size} configs, cleaning up temp dir")
            val isResidueDeleted = Utilities.deleteRecursive(tempDir)
            if (isResidueDeleted) {
                Logger.i(LOG_TAG_PROXY, "deleted residue temp wg files: ${tempDir.absolutePath}")
            } else {
                Logger.w(
                    LOG_TAG_PROXY,
                    "failed to delete residue temp wg files: ${tempDir.absolutePath}"
                )
                tempDir.deleteRecursively()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            Logger.e(LOG_TAG_PROXY, "performRestore: err restoring wg configs, ${e.message}", e)
        } catch (e: SecurityException) {
            Logger.e(LOG_TAG_PROXY, "performRestore: err restoring wg configs, ${e.message}", e)
        } catch (e: IllegalStateException) {
            Logger.e(LOG_TAG_PROXY, "performRestore: err restoring wg configs, ${e.message}", e)
        }
    }

    private suspend fun restoreOneConfig(c: WgConfigFiles, tempDir: File): Boolean {
        val file = File(tempDir, "${c.id}.conf")
        if (file.exists()) {
            Logger.i(LOG_TAG_PROXY, "performRestore: file exists: ${file.absolutePath} (${file.length()} bytes), proceed restore for config ${c.id}/${c.name}")
        } else {
            Logger.w(LOG_TAG_PROXY, "performRestore: no matching wg file for db entry ${c.id}/${c.name}, expected: ${file.absolutePath}, deleting config entry")
            db.deleteConfig(c.id)
            return false
        }
        val bytes = file.readBytes()
        Logger.i(LOG_TAG_PROXY, "performRestore: read ${bytes.size} bytes from ${file.name}")
        val targetFile = File(c.configPath)
        val parentDir = targetFile.parentFile
        if (parentDir == null) {
            Logger.e(LOG_TAG_PROXY, "performRestore: wg restore failed, invalid path: ${c.configPath}")
            db.deleteConfig(c.id)
            return false
        }
        val created = runCatching {
            if (!targetFile.exists()) {
                parentDir.mkdirs()
                targetFile.createNewFile()
            } else {
                true
            }
        }.getOrElse { ex ->
            Logger.w(
                LOG_TAG_PROXY,
                "performRestore: wg restore failed, unable to create file: ${targetFile.absolutePath}, err: ${ex.message}"
            )
            db.deleteConfig(c.id)
            return false
        }
        if (!created) {
            Logger.e(
                LOG_TAG_PROXY,
                "performRestore: wg restore failed, createNewFile returned false: ${targetFile.absolutePath}"
            )
            db.deleteConfig(c.id)
            return false
        }
        return try {
            targetFile.writeBytes(bytes)
            Logger.i(LOG_TAG_PROXY, "performRestore: restored wg config: ${c.id}, ${c.name} -> ${targetFile.absolutePath}")
            true
        } catch (e: java.io.IOException) {
            Logger.e(
                LOG_TAG_PROXY,
                "performRestore: I/O failure restoring wg config: ${c.id}, ${c.name}",
                e
            )
            false
        }
    }
}
