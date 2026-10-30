/*
 * Copyright 2022 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.backup

import Logger
import Logger.LOG_TAG_BACKUP_RESTORE
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.BACKUP_WG_DIR
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.CREATED_TIME
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.DATA_BUILDER_BACKUP_URI
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.METADATA_FILENAME
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.PACKAGE_NAME
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.SHARED_PREFS_BACKUP_FILE_NAME
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.TEMP_ZIP_FILE_NAME
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.VERSION
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.deleteResidue
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.getFileNameFromPath
import com.bernaferrari.bravedns.database.AppDatabase
import com.bernaferrari.bravedns.database.LogDatabase
import com.bernaferrari.bravedns.service.EncryptedFileManager
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.WireguardManager
import com.bernaferrari.bravedns.util.Utilities
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

// ref:
// https://gavingt.medium.com/refactoring-my-backup-and-restore-feature-to-comply-with-scoped-storage-e2b6c792c3b
class BackupAgent(val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams), KoinComponent {

    private var backupPreferences: Map<String, Any?>? = null

    var filesPathToZip: MutableList<String> = ArrayList()
    private val persistentState by inject<PersistentState>()
    private val appDatabase by inject<AppDatabase>()
    private val logDatabase by inject<LogDatabase>()

    companion object {
        const val TAG = "BackupExport"
    }

    override suspend fun doWork(): Result {
        val backupFileUri = inputData.getString(DATA_BUILDER_BACKUP_URI)?.toUri()
        if (backupFileUri == null) {
            Logger.w(LOG_TAG_BACKUP_RESTORE, "backup file uri is null, return failure")
            return Result.failure()
        }

        Logger.d(LOG_TAG_BACKUP_RESTORE, "begin backup process with file uri: $backupFileUri")
        backupPreferences = persistentState.snapshotPreferences()
        return withContext(Dispatchers.IO) {
            if (startBackupProcess(backupFileUri)) Result.success() else Result.failure()
        }
    }

    private suspend fun startBackupProcess(backupFileUri: Uri): Boolean {
        var processCompleted: Boolean
        val tempDir = File(context.cacheDir, "backup-$id")
        try {
            tempDir.deleteRecursively()
            check(tempDir.mkdirs()) { "Cannot create backup staging directory" }

            val prefsBackupFile = File(tempDir, SHARED_PREFS_BACKUP_FILE_NAME)

            Logger.d(
                    LOG_TAG_BACKUP_RESTORE,
                    "backup process, temp file dir: ${tempDir.path}, prefs backup file: ${prefsBackupFile.path}"
                )
            processCompleted = saveSharedPreferencesToFile(prefsBackupFile)

            if (processCompleted) {
                Logger.d(LOG_TAG_BACKUP_RESTORE, "shared pref backup is added to the temp dir")
            } else {
                Logger.w(
                    LOG_TAG_BACKUP_RESTORE,
                    "failed to add shared pref to temp backup dir, return failure"
                )
                return false
            }

            processCompleted = saveDatabasesToFile(tempDir.path)

            if (processCompleted) {
                Logger.d(LOG_TAG_BACKUP_RESTORE, "database backup is added to the temp dir")
            } else {
                Logger.w(
                    LOG_TAG_BACKUP_RESTORE,
                    "failed to add database to temp backup dir, return failure"
                )
                return false
            }

            // no need to check the return value, we can proceed even if the wireguard config backup
            // fails
            currentCoroutineContext().ensureActive()
            backupWireGuardConfig(tempDir)

            processCompleted = createMetaData(tempDir)

            if (processCompleted) {
                Logger.d(LOG_TAG_BACKUP_RESTORE, "metadata is added to the temp dir")
            } else {
                Logger.w(LOG_TAG_BACKUP_RESTORE, "failed to create metadata file, return failure")
                return false
            }

            return zipAndCopyToDestination(tempDir, backupFileUri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Logger.crash(
                LOG_TAG_BACKUP_RESTORE,
                "exception during backup process, reason? ${e.message}",
                e
            )
            return false
        } finally {
            for (filePath in filesPathToZip) {
                val file = File(filePath)
                deleteResidue(file)
            }
            filesPathToZip.clear()
            tempDir.deleteRecursively()
        }
    }

    private fun backupWireGuardConfig(tempDir: File): Boolean {
        // get all the wireguard config from the database,
        // loop through them and get the path from the database
        // create a copy of a encrypted file to normal file in the temp dir
        // add the file to the zip list

        Logger.d(LOG_TAG_BACKUP_RESTORE, "init backup wireguard configs")
        val dir = File(tempDir, BACKUP_WG_DIR)
        if (!dir.exists()) {
            Logger.d(LOG_TAG_BACKUP_RESTORE, "creating wireguard backup dir, ${dir.path}")
            dir.mkdirs()
        }

        try {
            val mappings = WireguardManager.getAllMappings()
            mappings.forEach { m ->
                val file = File(m.configPath)
                val content = EncryptedFileManager.read(context, file)
                if (content.isNotEmpty()) {
                    val tmpWgFile = File(dir, "${m.id}.conf")
                    tmpWgFile.writer().use {
                        writer -> writer.write(content)
                        writer.flush()
                    }
                    filesPathToZip.add(tmpWgFile.absolutePath)
                    Logger.v(LOG_TAG_BACKUP_RESTORE, "wg ${m.id}.conf added to backup, path: ${tmpWgFile.path}")
                } else {
                    Logger.w(LOG_TAG_BACKUP_RESTORE, "empty config for ${m.id}, ${m.configPath}")
                }
            }
            return true
        } catch (e: Exception) {
            Logger.w(LOG_TAG_BACKUP_RESTORE, "err while backing up wg config, ${e.message}", e)
        }
        return false
    }

    private fun createMetaData(backupDir: File): Boolean {
        Logger.d(LOG_TAG_BACKUP_RESTORE, "creating meta data file, path: ${backupDir.path}")
        // check if the file exists already, if yes, delete it
        val file = File(backupDir, METADATA_FILENAME)
        if (file.exists()) {
            Logger.d(LOG_TAG_BACKUP_RESTORE, "metadata file exists, deleting it")
            file.delete()
            filesPathToZip.remove(file.absolutePath)
        }
        val metadata = backupMetadata()
        try {
            val metadataFile = File(backupDir, METADATA_FILENAME)
            metadataFile.writer().use {
                writer -> writer.write(metadata)
                writer.flush()
            }
            // add the metadata file to the list of files to be zipped
            filesPathToZip.add(metadataFile.absolutePath)
            return true
        } catch (e: Exception) {
            Logger.crash(
                LOG_TAG_BACKUP_RESTORE,
                "exception while creating meta data file, ${e.message}",
                e
            )
            return false
        }
    }

    private fun backupMetadata(): String {
        return "$VERSION:${persistentState.appVersion}|$PACKAGE_NAME:${context.packageName}|$CREATED_TIME:${System.currentTimeMillis()}"
    }

    private suspend fun zipAndCopyToDestination(tempDir: File, destUri: Uri): Boolean {
        if (!zip(filesPathToZip, tempDir.path)) return false
        val archive = File(tempDir, TEMP_ZIP_FILE_NAME)
        return copyBackupStream(FileInputStream(archive)) {
            context.contentResolver.openOutputStream(destUri, "wt")
        }
    }

    internal suspend fun saveDatabasesToFile(path: String): Boolean {
        // Room opens lazily; a fresh install still needs schema-bearing backup files.
        appDatabase.appInfoDAO().getAppCount()
        logDatabase.dnsLogDAO().logsCount().first()
        val names = listOf(AppDatabase.DATABASE_NAME, LogDatabase.LOGS_DATABASE_NAME)
        // Each snapshot includes committed WAL contents. The databases have no
        // cross-database transaction contract; neither live WAL is copied.
        val snapshots = names.map { File(path, it) }
        val succeeded = names.zip(snapshots).all { (name, target) ->
            currentCoroutineContext().ensureActive()
            DatabaseSnapshot.create(context.getDatabasePath(name), target)
        }
        if (!succeeded) {
            snapshots.forEach { it.delete() }
            return false
        }
        filesPathToZip.addAll(snapshots.map { it.path })
        return true
    }

    private fun saveSharedPreferencesToFile(prefFile: File): Boolean {
        var output: ObjectOutputStream? = null

        Logger.i(LOG_TAG_BACKUP_RESTORE, "begin shared pref copy, file path:${prefFile.path}")

        try {
            output = ObjectOutputStream(FileOutputStream(prefFile))
            val allPrefs = backupPreferences ?: persistentState.snapshotPreferences()
            output.writeObject(allPrefs)
        } catch (e: Exception) {
            Logger.crash(LOG_TAG_BACKUP_RESTORE, "exception during shared pref backup, ${e.message}", e)
            return false
        } finally {
            try {
                if (output != null) {
                    output.flush()
                    output.close()
                }
            } catch (_: IOException) {
                // no-op
            }
        }
        filesPathToZip.add(prefFile.absolutePath)
        return true
    }


    private suspend fun zip(files: List<String>, zipDirectory: String): Boolean {
        val archive = File(zipDirectory, TEMP_ZIP_FILE_NAME)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(archive))).use { output ->
            for (path in files) {
                currentCoroutineContext().ensureActive()
                output.putNextEntry(ZipEntry(getFileNameFromPath(path)))
                BufferedInputStream(FileInputStream(path)).use { input ->
                    copyBackupBytes(input, output)
                }
                output.closeEntry()
            }
        }
        return true
    }
}
