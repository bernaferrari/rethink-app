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
package com.bernaferrari.bravedns.rpnproxy

import Logger
import Logger.LOG_TAG_PROXY
import android.content.Context
import com.bernaferrari.bravedns.database.RpnProxy
import com.bernaferrari.bravedns.database.RpnProxyRepository
import com.bernaferrari.bravedns.service.EncryptedFileManager
import com.bernaferrari.bravedns.service.EncryptionException
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * Persists WIN entitlement and tunnel-state blobs to encrypted files and the
 * [RpnProxy] row. Constructor-injected so [RpnProxyManager] stays a thin facade.
 */
internal class RpnWinEntitlementStore(
    private val context: Context,
    private val db: RpnProxyRepository,
    private val winId: Int,
    private val winName: String,
    private val stateFileName: String,
    private val entitlementFileName: String,
    private val folderName: String,
    private val extractWs: (String) -> ByteArray?,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val tag: String = "RpnMgr",
) {

    fun configFileName(id: Int): String {
        return when (id) {
            winId -> stateFileName
            else -> ""
        }
    }

    fun jsonResponseFileName(id: Int): String {
        return when (id) {
            winId -> entitlementFileName
            else -> ""
        }
    }

    suspend fun storeWinEntitlement(payload: String) {
        // Store the win entitlement in a file and the path in serverResponsePath
        if (payload.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$tag; storeWinEntitlement: payload is empty, skipping")
            return
        }

        try {
            // Use extractWsObject to handle both payload shapes:
            // Shape 1: full purchase originalJson  → ws nested inside developerPayload
            // Shape 2: already-extracted payload   → ws is top-level
            val ws = extractWs(payload)
            if (ws == null) {
                Logger.e(LOG_TAG_PROXY, "$tag; storeWinEntitlement: ws object not found in payload")

                return
            }

            val fileName = jsonResponseFileName(winId)
            val folder = context.getExternalFilesDir(folderName)
            if (folder == null) {
                Logger.e(LOG_TAG_PROXY, "$tag; storeWinEntitlement: failed to get external files dir")
                return
            }

            if (!folder.exists()) {
                val created = try {
                    folder.mkdirs()
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    Logger.e(LOG_TAG_PROXY, "$tag; storeWinEntitlement: exception creating folder: ${e.message}", e)
                    false
                }
                if (!created) {
                    Logger.e(LOG_TAG_PROXY, "$tag; storeWinEntitlement: failed to create folder: ${folder.absolutePath}")
                    return
                }
            }

            val file = File(folder, fileName)
            val res = try {
                EncryptedFileManager.write(context, ws, file)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Logger.e(LOG_TAG_PROXY, "$tag; storeWinEntitlement: exception writing file: ${e.message}", e)
                false
            }

            if (!res) {
                Logger.e(LOG_TAG_PROXY, "$tag; storeWinEntitlement: failed to write file: ${file.absolutePath}")
                return
            }

            // update the winConfig with the file path
            val winProxy = try {
                db.getProxyById(winId)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Logger.e(LOG_TAG_PROXY, "$tag; storeWinEntitlement: failed to get win proxy from DB: ${e.message}", e)
                null
            }

            val ll = if (winProxy != null) {
                winProxy.serverResPath = file.absolutePath
                try {
                    db.update(winProxy)
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    Logger.e(LOG_TAG_PROXY, "$tag; storeWinEntitlement: failed to update win proxy: ${e.message}", e)
                    -1
                }
            } else {
                // insert a new proxy entry if it doesn't exist
                val newWinProxy = RpnProxy(
                    id = winId,
                    name = winName,
                    configPath = "",
                    serverResPath = file.absolutePath,
                    isActive = true,
                    isLockdown = false,
                    createdTs = now(),
                    modifiedTs = now(),
                    misc = "",
                    tunId = "",
                    latency = 0,
                    lastRefreshTime = now()
                )
                try {
                    db.insert(newWinProxy).toInt()
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    Logger.e(LOG_TAG_PROXY, "$tag; storeWinEntitlement: failed to insert win proxy: ${e.message}", e)
                    -1
                }
            }
            if (ll < 0) {
                Logger.w(LOG_TAG_PROXY, "$tag; error updating win proxy in db, result: $ll")
            } else {
                Logger.i(LOG_TAG_PROXY, "$tag; win proxy updated in db, result: $ll")
            }
            Logger.i(LOG_TAG_PROXY, "$tag; win entitlement stored in file: $fileName, result: $res")
        } catch (e: Exception) {
            e.rethrowCancellation()
            Logger.e(LOG_TAG_PROXY, "$tag; error storing win configs: ${e.message}", e)
        }
    }

    suspend fun getWinEntitlement(): ByteArray? {
        val proxy = db.getProxyById(winId) ?: return null
        val file = File(proxy.serverResPath)
        if (!file.exists()) return null
        return try {
            val bytes = EncryptedFileManager.readByteArray(context, file)
            if (bytes.isNotEmpty()) {

                bytes
            } else null
        } catch (e: EncryptionException) {
            // File is corrupted, encrypted with an invalidated key, or unreadable.
            // Return null so the caller falls back to fetching fresh entitlement from the API.
            Logger.w(LOG_TAG_PROXY, "$tag; getWinEntitlement: encrypted file unreadable (${e::class.simpleName}), returning null", e)
            null
        }
    }

    suspend fun getWinExistingData(): ByteArray? {
        val proxy = db.getProxyById(winId) ?: return null
        val cfg = proxy.configPath
        if (cfg.isEmpty()) return null
        val file = File(cfg)
        if (!file.exists()) return null
        return try {
            val bytes = EncryptedFileManager.readByteArray(context, file)
            if (bytes.isNotEmpty()) bytes else null
        } catch (e: EncryptionException) {
            Logger.w(LOG_TAG_PROXY, "$tag; getWinExistingData: encrypted file unreadable (${e::class.simpleName}), returning null", e)
            null
        }
    }

    suspend fun updateWinConfigState(byteArray: ByteArray?): Boolean {
        if (byteArray == null || byteArray.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$tag; updateWinConfigState: byteArray is null or empty")
            return false
        }
        return try {
            val fileName = configFileName(winId)
            val folder = context.getExternalFilesDir(folderName)
            if (folder == null) {
                Logger.e(LOG_TAG_PROXY, "$tag; updateWinConfigState: failed to get external files dir")
                return false
            }

            if (!folder.exists()) {
                val created = try {
                    folder.mkdirs()
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    Logger.e(LOG_TAG_PROXY, "$tag; updateWinConfigState: exception creating folder: ${e.message}", e)
                    false
                }
                if (!created) {
                    Logger.e(LOG_TAG_PROXY, "$tag; updateWinConfigState: failed to create folder: ${folder.absolutePath}")
                    return false
                }
            }

            val file = File(folder, fileName)
            if (file.exists()) file.delete()
            val ok = try {
                EncryptedFileManager.write(context, byteArray, file)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Logger.e(LOG_TAG_PROXY, "$tag; updateWinConfigState: exception writing file: ${e.message}", e)
                false
            }

            if (!ok) {
                Logger.e(LOG_TAG_PROXY, "$tag; updateWinConfigState: failed to write file: ${file.absolutePath}")
                return false
            }

            val proxy = try {
                db.getProxyById(winId)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Logger.e(LOG_TAG_PROXY, "$tag; updateWinConfigState: failed to get proxy from DB: ${e.message}", e)
                null
            }

            val updateResult = if (proxy != null) {
                proxy.configPath = file.absolutePath
                proxy.modifiedTs = now()
                try {
                    db.update(proxy)
                    true
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    Logger.e(LOG_TAG_PROXY, "$tag; updateWinConfigState: failed to update proxy: ${e.message}", e)
                    false
                }
            } else {
                val newProxy = RpnProxy(
                    id = winId,
                    name = winName,
                    configPath = file.absolutePath,
                    serverResPath = "",
                    isActive = true,
                    isLockdown = false,
                    createdTs = now(),
                    modifiedTs = now(),
                    misc = "",
                    tunId = "",
                    latency = 0,
                    lastRefreshTime = now()
                )
                try {
                    db.insert(newProxy)
                    true
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    Logger.e(LOG_TAG_PROXY, "$tag; updateWinConfigState: failed to insert proxy: ${e.message}", e)
                    false
                }
            }

            if (updateResult) {
                Logger.i(LOG_TAG_PROXY, "$tag; updateWinConfigState: successfully updated config, size: ${byteArray.size}")
            } else {
                Logger.w(LOG_TAG_PROXY, "$tag; updateWinConfigState: file written, DB update failed")
            }

            updateResult
        } catch (e: Exception) {
            e.rethrowCancellation()
            Logger.e(LOG_TAG_PROXY, "$tag; err updating win config: ${e.message}", e)
            false
        }
    }
}

private fun Exception.rethrowCancellation() {
    if (this is CancellationException) throw this
}
