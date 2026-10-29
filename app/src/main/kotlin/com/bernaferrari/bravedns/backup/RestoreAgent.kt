package com.bernaferrari.bravedns.backup

import Logger
import Logger.LOG_TAG_BACKUP_RESTORE
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.DATA_BUILDER_RESTORE_URI
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.METADATA_FILENAME
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.SHARED_PREFS_BACKUP_FILE_NAME
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.TEMP_WG_DIR
import com.bernaferrari.bravedns.database.AppDatabase
import com.bernaferrari.bravedns.database.LogDatabase
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.util.Utilities
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RestoreAgent(val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams), KoinComponent {
    private val logDatabase by inject<LogDatabase>()
    private val appDatabase by inject<AppDatabase>()
    private val persistentState by inject<PersistentState>()
    private var restartRequired = false

    companion object {
        const val TAG = "RestoreAgent"
        const val RESTART_REQUIRED = "restartRequired"
    }

    override suspend fun doWork(): Result {
        val uri = inputData.getString(DATA_BUILDER_RESTORE_URI)?.toUri() ?: return Result.failure()
        return if (startRestore(uri)) Result.success()
        else Result.failure(Data.Builder().putBoolean(RESTART_REQUIRED, restartRequired).build())
    }

    // Import boundary: every deserialization/filesystem failure must leave the original state intact.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun startRestore(uri: Uri): Boolean {
        val directory = File(context.filesDir, "restore-$id")
        val wasRunning = VpnController.isOn()
        val originalPreferences = persistentState.snapshotPreferences()
        var stopped = false
        return try {
            directory.deleteRecursively()
            check(directory.mkdirs()) { "Cannot create restore directory" }
            context.contentResolver.openInputStream(uri).use { input ->
                check(BackupHelper.unzip(input, directory.path)) { "Cannot unpack backup" }
            }
            check(validateMetadata(directory.path)) { "Backup metadata is missing" }
            check(validateRestoredSchemaVersions(directory.listFiles() ?: emptyArray())) { "Invalid database schema" }
            validateSchemaIdentities(directory)
            val preferences = readPreferences(directory)
            val transactions = stageFiles(directory)
            stopped = true
            VpnController.stopAndAwait("restore", context)
            installRestore(transactions, preferences, originalPreferences)
            directory.deleteRecursively()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Logger.e(LOG_TAG_BACKUP_RESTORE, "Restore failed; original files retained", error)
            false
        } finally {
            if (!restartRequired) {
                directory.deleteRecursively()
                if (wasRunning && stopped) withContext(NonCancellable) {
                    VpnController.stopAndAwait("restore-recovery", context)
                    BackupHelper.startVpn(context)
                }
            }
        }
    }

    private fun stageFiles(directory: File): List<RestoreFileTransaction> {
        val databaseNames = listOf(AppDatabase.DATABASE_NAME, LogDatabase.LOGS_DATABASE_NAME)
            .flatMap { name -> listOf(name, "$name-wal", "$name-shm", "$name-journal") }
        val databaseDirectory = requireNotNull(context.getDatabasePath(AppDatabase.DATABASE_NAME).parentFile)
        val databases = RestoreFileTransaction(databaseDirectory, File(directory, "database-stage"), databaseNames)
        databases.stage(directory)
        val wireguardSource = File(directory, TEMP_WG_DIR)
        check(wireguardSource.mkdirs()) { "Cannot stage WireGuard files" }
        directory.listFiles().orEmpty().filter { it.isFile && it.extension == "conf" }.forEach {
            it.copyTo(File(wireguardSource, it.name))
        }
        val wireguard = RestoreFileTransaction(context.filesDir, File(directory, "wireguard-stage"), listOf(TEMP_WG_DIR))
        wireguard.stage(directory)
        return listOf(databases, wireguard)
    }

    // Transaction boundary must roll back for any failure after Room closes.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun installRestore(
        transactions: List<RestoreFileTransaction>,
        preferences: Map<String, Any?>,
        originalPreferences: Map<String, Any?>,
    ) =
        withContext(NonCancellable) {
            BackupHelper.checkpointRoomDatabases(appDatabase, logDatabase)
            restartRequired = true
            File(context.filesDir, RestoreRestart.MARKER).writeText("restart")
            appDatabase.close()
            logDatabase.close()
            try {
                transactions.forEach { it.install() }
                @Suppress("DEPRECATION")
                val currentVersion = Utilities.getPackageMetadata(context.packageManager, context.packageName)?.versionCode
                val restoredPreferences = if (currentVersion == null) preferences
                    else preferences + (PersistentState.APP_VERSION to currentVersion)
                persistentState.restorePreferencesAndAwait(restoredPreferences)
                transactions.forEach { it.commit() }
            } catch (error: Exception) {
                transactions.asReversed().forEach { it.rollback() }
                persistentState.restorePreferencesAndAwait(originalPreferences)
                throw error
            }
        }

    private fun validateRestoredSchemaVersions(files: Array<File>): Boolean {
        val required = setOf(AppDatabase.DATABASE_NAME, LogDatabase.LOGS_DATABASE_NAME)
        if (!files.map { it.name }.containsAll(required)) return false
        return files.filter { it.name in required }.all { file ->
            val expected = if (file.name == AppDatabase.DATABASE_NAME) AppDatabase.SCHEMA_VERSION
                else LogDatabase.SCHEMA_VERSION
            readSqliteUserVersion(file) == expected
        }
    }

    private suspend fun validateSchemaIdentities(directory: File) {
        // v0 schemas all use version 1; Room's identity distinguishes incompatible v1 backups.
        BackupHelper.checkpointRoomDatabases(appDatabase, logDatabase)
        for (name in listOf(AppDatabase.DATABASE_NAME, LogDatabase.LOGS_DATABASE_NAME)) {
            val expected = readSchemaIdentity(context.getDatabasePath(name))
            check(expected != null && readSchemaIdentity(File(directory, name)) == expected) {
                "Backup schema does not match this build: $name"
            }
        }
    }

    private fun readSchemaIdentity(file: File): String? = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.roomIdentity()
        }
    } catch (error: android.database.SQLException) {
        Logger.e(LOG_TAG_BACKUP_RESTORE, "Missing Room identity in ${file.name}", error)
        null
    }

    private fun readSqliteUserVersion(file: File): Int? = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
    } catch (error: android.database.SQLException) {
        Logger.e(LOG_TAG_BACKUP_RESTORE, "Cannot validate database ${file.name}", error)
        null
    }

    private fun validateMetadata(directory: String?): Boolean = File(directory, METADATA_FILENAME).isFile

    private fun readPreferences(directory: File): Map<String, Any?> {
        ObjectInputStream(FileInputStream(File(directory, SHARED_PREFS_BACKUP_FILE_NAME))).use { input ->
            val values = input.readObject() as? Map<*, *> ?: error("Invalid preference backup")
            return values.entries.associate { (key, value) ->
                require(key is String) { "Invalid preference name" }
                require(value == null || value is Boolean || value is Int || value is Long || value is String) {
                    "Invalid preference type"
                }
                key to value
            }
        }
    }
}

private fun SQLiteDatabase.roomIdentity(): String? =
    rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
