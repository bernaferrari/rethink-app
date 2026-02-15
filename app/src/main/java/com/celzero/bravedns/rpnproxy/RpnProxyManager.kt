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
package com.celzero.bravedns.rpnproxy

import android.content.Context
import androidx.preference.PreferenceManager
import com.celzero.bravedns.database.RpnProxy
import com.celzero.bravedns.database.RpnProxyRepository
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.EncryptedFileManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.util.Constants.Companion.RPN_PROXY_FOLDER_NAME
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.time.Instant

object RpnProxyManager : KoinComponent {
    private val applicationContext: Context by inject()
    private val db: RpnProxyRepository by inject()
    private val persistentState: PersistentState by inject()

    private const val WIN_ID = 4
    private const val WIN_NAME = "WIN"
    private const val WIN_STATE_FILE_NAME = "win_state.json"
    const val MAX_WIN_SERVERS = 5

    enum class RpnTunMode(val id: Int) {
        NONE(0),
        ANTI_CENSORSHIP(1),
        HIDE_IP(2);

        companion object {
            fun fromId(id: Int): RpnTunMode = entries.firstOrNull { it.id == id } ?: NONE
        }
    }

    enum class RpnMode(val id: Int) {
        NONE(0),
        ANTI_CENSORSHIP(1),
        HIDE_IP(2);

        companion object {
            fun fromId(id: Int): RpnMode = entries.firstOrNull { it.id == id } ?: NONE

            fun getPreferredId(id: Int): String {
                return when (fromId(id)) {
                    NONE -> ""
                    ANTI_CENSORSHIP -> "auto"
                    HIDE_IP -> "auto"
                }
            }
        }

        fun isNone() = this == NONE
    }

    enum class RpnState(val id: Int) {
        DISABLED(0),
        PAUSED(1),
        ENABLED(2);

        companion object {
            fun fromId(id: Int): RpnState = entries.firstOrNull { it.id == id } ?: DISABLED
        }

        fun isEnabled() = this == ENABLED
    }

    enum class RpnType(val id: Int) {
        WIN(0),
        EXIT(1)
    }

    data class RpnWinServer(
        val names: String,
        val countryCode: String,
        val address: String,
        val isActive: Boolean
    )

    data class RpnProps(
        val id: String,
        val status: Long,
        val type: String,
        val kids: String,
        val addr: String,
        val created: Long,
        val expires: Long,
        val who: String,
        val locations: Any?
    )

    data class WinProxyDetails(
        val countryCode: String,
        val name: String,
        val address: String,
        val who: String,
        val latencyMs: Int?,
        val lastConnectedMs: Long?,
        val isActive: Boolean
    )

    @Volatile private var winServers: List<RpnWinServer> = emptyList()

    fun isRpnActive(): Boolean {
        return isRpnEnabled() && !rpnMode().isNone()
    }

    fun isRpnEnabled(): Boolean {
        return rpnState().isEnabled()
    }

    fun rpnMode(): RpnMode {
        return RpnMode.fromId(persistentState.rpnMode)
    }

    fun rpnState(): RpnState {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        return RpnState.fromId(prefs.getInt(PersistentState.USE_RPN, RpnState.DISABLED.id))
    }

    fun setRpnMode(mode: RpnMode) {
        persistentState.rpnMode = mode.id
    }

    fun deactivateRpn(reason: String = "manual deactivation") {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        prefs.edit().putInt(PersistentState.USE_RPN, RpnState.DISABLED.id).apply()
    }

    suspend fun getSelectedCCs(): Set<String> {
        val fromDomains = DomainRulesManager.getAllUniqueCCs().map { it.uppercase() }
        val fromIps = IpRulesManager.getAllUniqueCCs().map { it.uppercase() }
        return (fromDomains + fromIps).filter { it.length == 2 }.toSet()
    }

    suspend fun getWinServers(): List<RpnWinServer> {
        if (winServers.isNotEmpty()) return winServers

        val parsed = parseWinServersFromState()
        if (parsed.isNotEmpty()) {
            winServers = parsed
            return parsed
        }

        val selected = getSelectedCCs().sorted()
        val fallback = selected.map { cc ->
            RpnWinServer(
                names = cc,
                countryCode = cc,
                address = "",
                isActive = true
            )
        }
        winServers = fallback
        return fallback
    }

    suspend fun getWinProxyDetails(countryCode: String): WinProxyDetails? {
        val normalized = countryCode.trim().uppercase()
        if (normalized.isBlank()) return null

        val server = getWinServers().firstOrNull { it.countryCode.equals(normalized, ignoreCase = true) }
        val row = db.getProxyById(WIN_ID)
        val who = parseWhoFromState().orEmpty()
        val latency = row?.latency?.takeIf { it > 0 }
        val lastConnected = row?.lastRefreshTime?.takeIf { it > 0L }
        val isActive = row?.isActive ?: server?.isActive ?: false

        if (server == null && who.isBlank() && latency == null && lastConnected == null && row == null) {
            return null
        }

        return WinProxyDetails(
            countryCode = server?.countryCode ?: normalized,
            name = server?.names?.ifBlank { WIN_NAME } ?: WIN_NAME,
            address = server?.address.orEmpty(),
            who = who,
            latencyMs = latency,
            lastConnectedMs = lastConnected,
            isActive = isActive
        )
    }

    suspend fun getWinExistingData(): ByteArray? {
        return runCatching {
            val row = db.getProxyById(WIN_ID) ?: return@runCatching null
            if (row.configPath.isBlank()) return@runCatching null
            val cfgFile = File(row.configPath)
            if (!cfgFile.exists()) return@runCatching null
            EncryptedFileManager.readByteArray(applicationContext, cfgFile)
        }.getOrNull()
    }

    suspend fun getWinEntitlement(): ByteArray? {
        return getWinExistingData()
    }

    suspend fun updateWinConfigState(state: ByteArray?): Boolean {
        if (state == null || state.isEmpty()) return false
        return runCatching {
            val file = winStateFile()
            file.parentFile?.mkdirs()
            val writeOk = EncryptedFileManager.write(applicationContext, state, file)
            if (!writeOk) return@runCatching false

            val existing = db.getProxyById(WIN_ID)
            if (existing != null) {
                existing.configPath = file.absolutePath
                existing.modifiedTs = System.currentTimeMillis()
                db.update(existing)
            } else {
                val row = RpnProxy(
                    id = WIN_ID,
                    name = WIN_NAME,
                    configPath = file.absolutePath,
                    serverResPath = "",
                    isActive = true,
                    isLockdown = false,
                    createdTs = System.currentTimeMillis(),
                    modifiedTs = System.currentTimeMillis(),
                    misc = "",
                    tunId = "",
                    latency = 0,
                    lastRefreshTime = System.currentTimeMillis()
                )
                db.insert(row)
            }
            winServers = emptyList()
            true
        }.getOrDefault(false)
    }

    fun getSessionTokenFromPayload(payload: String): String {
        return runCatching {
            val root = JSONObject(payload)
            val ws = root.optJSONObject("ws")
            ws?.optString("sessiontoken").orEmpty()
        }.getOrDefault("")
    }

    fun getExpiryFromPayload(payload: String): Long? {
        return runCatching {
            val root = JSONObject(payload)
            val ws = root.optJSONObject("ws")
            val expiry = ws?.optString("expiry").orEmpty()
            if (expiry.isBlank()) return@runCatching null
            Instant.parse(expiry).toEpochMilli()
        }.getOrNull()
    }

    private suspend fun parseWinServersFromState(): List<RpnWinServer> {
        val bytes = getWinExistingData() ?: return emptyList()
        val raw = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() ?: return emptyList()
        val ccRegex = Regex("\"cc\"\\s*:\\s*\"([A-Za-z]{2})\"")
        val codes = ccRegex.findAll(raw).map { it.groupValues[1].uppercase() }.distinct().toList()
        return codes.map { cc ->
            RpnWinServer(
                names = cc,
                countryCode = cc,
                address = "",
                isActive = true
            )
        }
    }

    private suspend fun parseWhoFromState(): String? {
        val bytes = getWinExistingData() ?: return null
        val raw = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() ?: return null
        val whoRegex = Regex("\"who\"\\s*:\\s*\"([^\"]+)\"")
        return whoRegex.find(raw)?.groupValues?.getOrNull(1)?.trim().takeUnless { it.isNullOrEmpty() }
    }

    private fun winStateFile(): File {
        return File(
            applicationContext.filesDir.absolutePath +
                File.separator +
                RPN_PROXY_FOLDER_NAME +
                File.separator +
                WIN_NAME.lowercase() +
                File.separator +
                WIN_STATE_FILE_NAME
        )
    }
}
