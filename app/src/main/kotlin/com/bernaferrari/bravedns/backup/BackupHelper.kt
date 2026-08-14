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
import android.content.Context
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.AppDatabase
import com.bernaferrari.bravedns.database.LogDatabase
import com.bernaferrari.bravedns.service.VpnController
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class BackupHelper {

    companion object {
        // MIME type is used for unknown binary files (if stored locally)
        const val INTENT_TYPE_OCTET = "application/octet-stream"

        // google drive will have its own mimetypes changed to "x-zip"
        const val INTENT_TYPE_XZIP = "application/x-zip"

        // file name for the backup file
        const val BACKUP_FILE_NAME = "Rethink_"

        // date format for backup  file name
        const val BACKUP_FILE_NAME_DATETIME = "yyMMddHHmmss"

        // backup file extension
        const val BACKUP_FILE_EXTN = ".rbk"
        const val BACKUP_FILE_EXTN_ALPHA = ".rbka"

        // backup shared pref file name
        const val SHARED_PREFS_BACKUP_FILE_NAME = "shared_prefs_backup.txt"

        // metadata file name
        const val METADATA_FILENAME = "rethink_backup.txt"

        // data builder uri string for restore worker
        const val DATA_BUILDER_RESTORE_URI = "restoreFileUri"

        // data builder uri string for backup worker
        const val DATA_BUILDER_BACKUP_URI = "backupFileUri"

        // temp zip  file name
        const val TEMP_ZIP_FILE_NAME = "temp.rbk"

        // intent scheme
        const val INTENT_SCHEME = "content"

        // restart app after database restore
        const val INTENT_RESTART_APP = "restartApp"

        // wireguard dir to backup the wireguard configs
        const val BACKUP_WG_DIR = "wireguard"

        // while restoring the wireguard config, the temp dir to store the wireguard config
        const val TEMP_WG_DIR = "temp_wireguard"

        // metadata constants
        // version
        const val VERSION = "version"
        // package name
        const val PACKAGE_NAME = "package"
        // time when the backup  is taken
        const val CREATED_TIME = "createdTs"

        fun getTempDir(context: Context): File {
            // temp dir (files/Rethink/)
            val backupDirectoryPath: String =
                context.filesDir.absolutePath +
                    File.separator +
                    context.getString(R.string.app_name)
            val file = File(backupDirectoryPath)
            if (!file.exists()) {
                file.mkdir()
            }

            return file
        }

        fun getRethinkDatabase(context: Context): File? {
            val path =
                (context.getDatabasePath(AppDatabase.DATABASE_NAME).parentFile?.path
                    ?: return null) + File.separator
            return File(path)
        }

        fun stopVpn(context: Context) {
            Logger.i(Logger.LOG_TAG_BACKUP_RESTORE, "calling vpn stop from backup helper")
            VpnController.stop("bkup", context)
        }

        fun startVpn(context: Context) {
            Logger.i(Logger.LOG_TAG_BACKUP_RESTORE, "calling vpn start from backup helper")
            VpnController.start(context)
        }

        fun deleteResidue(backupFile: File) {
            if (backupFile.exists()) {
                backupFile.delete()
            }
        }

        suspend fun checkpointRoomDatabases(
            appDatabase: AppDatabase,
            logDatabase: LogDatabase
        ) {
            appDatabase.checkPoint()
            logDatabase.checkPoint()
        }

        fun unzip(inputStream: InputStream?, path: String): Boolean {
            var zis: ZipInputStream? = null
            try {
                val destDir = File(path).canonicalFile
                zis = ZipInputStream(BufferedInputStream(inputStream))
                var ze: ZipEntry? = zis.nextEntry
                while (ze != null) {
                    val filename: String = ze.name
                    val outFile = sanitizedZipEntryFile(destDir, filename) ?: return false
                    if (ze.isDirectory) {
                        if (!outFile.exists() && !outFile.mkdirs()) {
                            Logger.w(
                                Logger.LOG_TAG_BACKUP_RESTORE,
                                "failed to create zip directory entry: $filename"
                            )
                            return false
                        }
                        zis.closeEntry()
                        ze = zis.nextEntry
                        continue
                    }
                    outFile.parentFile?.let { parent ->
                        if (!parent.exists() && !parent.mkdirs()) {
                            Logger.w(
                                Logger.LOG_TAG_BACKUP_RESTORE,
                                "failed to create parent for zip entry: $filename"
                            )
                            return false
                        }
                    }
                    val baos = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    var count: Int
                    FileOutputStream(outFile).use { fout ->
                        while (zis.read(buffer).also { r -> count = r } != -1) {
                            baos.write(buffer, 0, count)
                            fout.write(baos.toByteArray())
                            baos.reset()
                        }
                    }
                    zis.closeEntry()
                    ze = zis.nextEntry
                }
            } catch (e: Exception) {
                Logger.e(Logger.LOG_TAG_BACKUP_RESTORE, "unzip failed: ${e.message}", e)
                return false
            } finally {
                zis?.close()
            }
            return true
        }

        internal fun sanitizedZipEntryFile(destDir: File, filename: String): File? {
            if (filename.isBlank() || filename.contains("..") || File(filename).isAbsolute) {
                Logger.w(
                    Logger.LOG_TAG_BACKUP_RESTORE,
                    "rejected zip entry with unsafe name: $filename"
                )
                return null
            }
            val destPath = destDir.canonicalPath
            val outFile = File(destDir, filename).canonicalFile
            val outPath = outFile.path
            if (outPath != destPath && !outPath.startsWith(destPath + File.separator)) {
                Logger.w(
                    Logger.LOG_TAG_BACKUP_RESTORE,
                    "rejected zip-slip entry: $filename -> $outPath"
                )
                return null
            }
            return outFile
        }

        fun getFileNameFromPath(file: String): String {
            return file.substring(file.lastIndexOf("/") + 1)
        }
    }
}
