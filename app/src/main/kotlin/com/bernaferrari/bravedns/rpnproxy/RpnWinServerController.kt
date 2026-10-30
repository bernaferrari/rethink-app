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
import com.bernaferrari.bravedns.database.CountryConfig
import com.bernaferrari.bravedns.database.CountryConfigRepository
import com.celzero.firestack.backend.Backend
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory WIN server cache plus enable/disable/setting mutations.
 * Tunnel and proxy-mapping side effects are constructor-injected.
 */
internal class RpnWinServerController(
    private val countryConfigRepo: CountryConfigRepository,
    private val autoServerId: String,
    private val autoCountryCode: String,
    private val addWinServer: suspend (String) -> Pair<Boolean, String>,
    private val removeWinServer: suspend (String) -> Pair<Boolean, String>,
    private val handleRpnHop: suspend (String, Boolean) -> Pair<Boolean, String>,
    private val refreshProxies: suspend () -> Unit,
    private val notifyConnectionMonitor: suspend (Boolean) -> Unit,
    private val notifyServerAdded: (String) -> Unit,
    private val clearServerMeta: (String) -> Unit,
    private val removeProxyMapping: suspend (String) -> Unit,
    private val fetchLocations: suspend () -> Pair<Set<CountryConfig>, List<String>>,
    private val retryLocationFetch: suspend () -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val tag: String = "RpnMgr",
) {
    private val winServersCache = mutableListOf<CountryConfig>()
    private val winCacheMutex = Mutex()

    suspend fun <T> withCache(block: suspend (MutableList<CountryConfig>) -> T): T {
        return winCacheMutex.withLock { block(winServersCache) }
    }

    suspend fun snapshot(): List<CountryConfig> = winCacheMutex.withLock { winServersCache.toList() }

    suspend fun replaceCache(servers: List<CountryConfig>) {
        winCacheMutex.withLock {
            winServersCache.clear()
            winServersCache.addAll(servers)
        }
    }

    suspend fun clearUserSelectionsInCache() {
        winCacheMutex.withLock {
            winServersCache.forEach { server ->
                if (!server.id.equals(autoServerId, true)) {
                    server.isEnabled = false
                    server.isFavourite = false
                }
            }
        }
    }

    suspend fun getWinServers(): List<CountryConfig> {
        // Return cached list only if it contains real (non-AUTO) servers.
        winCacheMutex.withLock {
            val realCached = winServersCache.filter { !it.id.equals(autoServerId, true) }
            if (realCached.isNotEmpty()) {
                Logger.v(LOG_TAG_PROXY, "$tag; returning cached win servers, size: ${winServersCache.size} (real=${realCached.size})")
                return winServersCache.toList()
            }
        }

        // Try DB, same rule: only use it when real (non-AUTO) rows exist.
        val dbServers = countryConfigRepo.getAllConfigs()
        val realDbServers = dbServers.filter { !it.id.equals(autoServerId, true) }
        if (realDbServers.isNotEmpty()) {
            winCacheMutex.withLock {
                winServersCache.clear()
                winServersCache.addAll(dbServers)
            }
            Logger.v(LOG_TAG_PROXY, "$tag; loaded ${dbServers.size} win servers from DB into cache (real=${realDbServers.size})")
            return dbServers
        }

        // Cache and DB contain only AUTO (or are completely empty) -  fetch real data from tunnel.
        Logger.w(LOG_TAG_PROXY, "$tag; no real servers in cache/DB (only AUTO or empty), fetching win servers from tun")
        val (apiServers, removedSelectedIds) = fetchLocations()
        if (apiServers.isNotEmpty()) {
            syncWinServers(apiServers)
            if (removedSelectedIds.isNotEmpty()) {
                Logger.w(LOG_TAG_PROXY, "$tag; getWinServers: ${removedSelectedIds.size} selected servers were removed")
            }
        } else {
            Logger.w(LOG_TAG_PROXY, "$tag; getWinServers: tunnel returned no servers (win props null/empty)")
        }
        // Return whatever is now in the cache (may still be AUTO-only if tunnel failed).
        // The caller (ServerSelectionFragment) will detect the absence of real servers
        // and show an appropriate error to the user.
        return winCacheMutex.withLock { winServersCache.toList() }
    }

    suspend fun setCountryFavourite(cc: String, favourite: Boolean) {
        if (cc.isBlank()) return
        countryConfigRepo.updateFavourite(cc, favourite)
        winCacheMutex.withLock {
            winServersCache.filter { it.cc.equals(cc, ignoreCase = true) }.forEach {
                it.isFavourite = favourite
            }
        }
    }

    suspend fun setHopForWinServer(key: String, enabled: Boolean) {
        if (key.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$tag; setHopForWinServer: key is empty")
            return
        }

        val config = winCacheMutex.withLock {
            winServersCache.find { it.key == key }
        }

        if (config == null) {
            Logger.w(LOG_TAG_PROXY, "$tag; setHopForWinServer: server with key $key not found")
            return
        }

        val oldValue = config.hopEnabled
        config.hopEnabled = enabled
        winCacheMutex.withLock {
            winServersCache.filter { w -> w.key == key }.forEach { w ->
                w.hopEnabled = enabled
            }
        }

        try {
            countryConfigRepo.update(config)
            Logger.i(LOG_TAG_PROXY, "$tag; setHopForWinServer: set hopEnabled=$enabled for server: $key")
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; setHopForWinServer: failed to update DB for $key: ${e.message}", e)
            // Revert on DB failure
            config.hopEnabled = oldValue
            winCacheMutex.withLock {
                winServersCache.filter { w -> w.key == key }.forEach { w ->
                    w.hopEnabled = oldValue
                }
            }
        }

        try {
            val res = handleRpnHop(config.key, oldValue != enabled)
            if (res.first) {
                Logger.i(LOG_TAG_PROXY, "$tag; setHopForWinServer: tunnel updated hop for server $key successfully")
            } else {
                Logger.w(LOG_TAG_PROXY, "$tag; setHopForWinServer: tunnel failed to update hop for server $key, ${if (res.second != null) "error: ${res.second}" else "no error message"}")
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; setHopForWinServer: error updating tunnel for $key: ${e.message}", e)
        }
        refreshProxies() // Relay changes which saved automation policy governs this server.
    }

    suspend fun getSelectedCCs(): Set<CountryConfig> {
        winCacheMutex.withLock {
            return winServersCache.filter { it.isEnabled }.toSet()
        }
    }

    suspend fun getEnabledConfigs(): Set<CountryConfig> {
        return winCacheMutex.withLock {
            val es = winServersCache.filter { it.isEnabled }.toSet()
            es
        }
    }

    suspend fun enabledNonAutoConfigs(): List<CountryConfig> {
        return winCacheMutex.withLock {
            winServersCache.filter { it.isEnabled && !it.id.equals(autoServerId, true) }.toList()
        }
    }

    suspend fun markDisabledInCache(key: String) {
        winCacheMutex.withLock {
            winServersCache.firstOrNull { it.key == key }?.isEnabled = false
        }
    }

    suspend fun enableWinServer(key: String): Pair<Boolean, String> {
        if (key.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$tag; enableWinServer: key is empty")
            return Pair(false, "Server key is empty")
        }

        val config = winCacheMutex.withLock {
            winServersCache.find { it.key == key }
        }

        if (config == null) {
            Logger.w(LOG_TAG_PROXY, "$tag; enableWinServer: server with key $key not found in cache")
            return Pair(false, "Server with key $key not found")
        }

        // Check if already enabled
        if (config.isEnabled) {
            Logger.i(LOG_TAG_PROXY, "$tag; enableWinServer: server $key already enabled")
            return Pair(true, "Server already enabled")
        }

        val res = try {
            addWinServer(config.key)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; enableWinServer: exception calling VpnController for $key: ${e.message}", e)
            return Pair(false, "Failed to add server: ${e.message}")
        }

        if (res.first) {
            winCacheMutex.withLock {
                winServersCache.filter { it.key == key }.forEach { it.isEnabled = true }
            }
            config.isEnabled = true
            try {
                countryConfigRepo.update(config)
                // Record the selection for frequent-country tracking and refresh chips.
                countryConfigRepo.incrementSelectionCount(config.key)
                // Mark when this server was added to the tunnel so the UI can show uptime.
                notifyServerAdded(key)
                Logger.i(LOG_TAG_PROXY, "$tag; enableWinServer: enabled rpn: $key")
            } catch (e: Exception) {
                Logger.e(LOG_TAG_PROXY, "$tag; enableWinServer: failed to update DB for $key: ${e.message}", e)
                // Revert cache change on DB failure
                winCacheMutex.withLock {
                    winServersCache.filter { it.key == key }.forEach { it.isEnabled = false }
                }
                config.isEnabled = false
                return Pair(false, "Failed to update database: ${e.message}")
            }
        } else {
            Logger.w(LOG_TAG_PROXY, "$tag; enableWinServer: failed to enable server with key $key, error: ${res.second}")
        }
        return res
    }

    suspend fun setLockdownForWinServer(key: String, lockdown: Boolean) {
        if (key.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$tag; setLockdownForWinServer: key is empty")
            return
        }

        val config = winCacheMutex.withLock {
            winServersCache.find { it.key == key }
        }

        if (config == null) {
            Logger.w(LOG_TAG_PROXY, "$tag; setLockdownForWinServer: server with key $key not found")
            return
        }

        val oldValue = config.lockdown
        config.lockdown = lockdown
        winCacheMutex.withLock {
            winServersCache.filter { w -> w.key == key }.forEach { w ->
                w.lockdown = lockdown
            }
        }

        try {
            countryConfigRepo.update(config)
            Logger.i(LOG_TAG_PROXY, "$tag; setLockdownForWinServer: set lockdown=$lockdown for server: $key")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; setLockdownForWinServer: failed to update DB for $key: ${e.message}", e)
            // Revert on DB failure
            config.lockdown = oldValue
            winCacheMutex.withLock {
                winServersCache.filter { w -> w.key == key }.forEach { w ->
                    w.lockdown = oldValue
                }
            }
        }
    }

    suspend fun setCatchAllForWinServer(key: String, catchAll: Boolean) {
        if (key.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$tag; setCatchAllForWinServer: key is empty")
            return
        }

        val config = winCacheMutex.withLock {
            winServersCache.find { it.key == key }
        }

        if (config == null) {
            Logger.w(LOG_TAG_PROXY, "$tag; setCatchAllForWinServer: server with key $key not found")
            return
        }

        val oldCatchAll = config.catchAll
        val oldEnabled = config.isEnabled

        if (!config.isEnabled && catchAll) {
            Logger.w(LOG_TAG_PROXY, "$tag; setCatchAllForWinServer: enabling inactive server $key for catch-all")
            val res = try {
                addWinServer(config.key)
            } catch (e: Exception) {
                Logger.e(LOG_TAG_PROXY, "$tag; setCatchAllForWinServer: exception enabling server $key: ${e.message}", e)
                return
            }

            if (res.first) {
                config.isEnabled = true
                config.catchAll = true
                Logger.i(LOG_TAG_PROXY, "$tag; setCatchAllForWinServer: enabled server $key for catch-all")
                winCacheMutex.withLock {
                    winServersCache.filter { w -> w.key == key }.forEach { w ->
                        w.isEnabled = true
                        w.catchAll = true
                    }
                }
            } else {
                Logger.w(LOG_TAG_PROXY, "$tag; setCatchAllForWinServer: failed to enable server $key for catch-all, error: ${res.second}")
                return
            }
        } else {
            config.catchAll = catchAll
            Logger.i(LOG_TAG_PROXY, "$tag; setCatchAllForWinServer: set catchAll=$catchAll for server: $key")
            winCacheMutex.withLock {
                winServersCache.filter { w -> w.key == key }.forEach { w ->
                    w.catchAll = catchAll
                }
            }
        }

        try {
            countryConfigRepo.update(config)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; setCatchAllForWinServer: failed to update DB for $key: ${e.message}", e)
            // Revert changes on DB failure
            config.isEnabled = oldEnabled
            config.catchAll = oldCatchAll
            winCacheMutex.withLock {
                winServersCache.filter { w -> w.key == key }.forEach { w ->
                    w.isEnabled = oldEnabled
                    w.catchAll = oldCatchAll
                }
            }
        }
    }

    suspend fun setMobileOnlyForWinServer(key: String, mobileOnly: Boolean) {
        if (key.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$tag; setMobileOnlyForWinServer: key is empty")
            return
        }

        val config = winCacheMutex.withLock {
            winServersCache.find { it.key == key }
        }

        if (config == null) {
            Logger.w(LOG_TAG_PROXY, "$tag; setMobileOnlyForWinServer: server with key $key not found")
            return
        }

        val oldValue = config.mobileOnly
        config.mobileOnly = mobileOnly
        winCacheMutex.withLock {
            winServersCache.filter { w -> w.key == key }.forEach { w ->
                w.mobileOnly = mobileOnly
            }
        }

        try {
            countryConfigRepo.update(config)
            Logger.i(LOG_TAG_PROXY, "$tag; setMobileOnlyForWinServer: set mobileOnly=$mobileOnly for server: $key")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; setMobileOnlyForWinServer: failed to update DB for $key: ${e.message}", e)
            // Revert on DB failure
            config.mobileOnly = oldValue
            winCacheMutex.withLock {
                winServersCache.filter { w -> w.key == key }.forEach { w ->
                    w.mobileOnly = oldValue
                }
            }
        }

        if (config.isEnabled) {
            refreshProxies()
        }
    }

    suspend fun setSsidEnabledForWinServer(key: String, ssidEnabled: Boolean) {
        if (key.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$tag; setSsidEnabledForWinServer: key is empty")
            return
        }

        val config = winCacheMutex.withLock {
            winServersCache.find { it.key == key }
        }

        if (config == null) {
            Logger.w(LOG_TAG_PROXY, "$tag; setSsidEnabledForWinServer: server with key $key not found")
            return
        }

        val oldValue = config.ssidBased
        config.ssidBased = ssidEnabled
        winCacheMutex.withLock {
            winServersCache.filter { w -> w.key == key }.forEach { w ->
                w.ssidBased = ssidEnabled
            }
        }

        try {
            countryConfigRepo.update(config)
            Logger.i(LOG_TAG_PROXY, "$tag; setSsidEnabledForWinServer: set ssidEnabled=$ssidEnabled for server: $key")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; setSsidEnabledForWinServer: failed to update DB for $key: ${e.message}", e)
            // Revert on DB failure
            config.ssidBased = oldValue
            winCacheMutex.withLock {
                winServersCache.filter { w -> w.key == key }.forEach { w ->
                    w.ssidBased = oldValue
                }
            }
        }

        if (config.isEnabled) {
            refreshProxies()
        }
    }

    suspend fun disableWinServer(key: String): Pair<Boolean, String> {
        if (key.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$tag; disableWinServer: key is empty")
            return Pair(false, "Server key is empty")
        }

        val config = winCacheMutex.withLock {
            winServersCache.find { it.key == key }
        }

        if (config == null) {
            Logger.w(LOG_TAG_PROXY, "$tag; disableWinServer: server with key $key not found in cache")
            return Pair(false, "Server with key $key not found")
        }

        // Check if already disabled
        if (!config.isEnabled) {
            Logger.i(LOG_TAG_PROXY, "$tag; disableWinServer: server $key already disabled")
            return Pair(true, "Server already disabled")
        }

        val res = try {
            removeWinServer(config.key)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; disableWinServer: exception calling VpnController for $key: ${e.message}", e)
            return Pair(false, "Failed to remove server: ${e.message}")
        }

        if (res.first) {
            config.isEnabled = false
            winCacheMutex.withLock {
                winServersCache.filter { it.key == key }.forEach { it.isEnabled = false }
            }
            try {
                countryConfigRepo.update(config)
                // Clear the in-memory tunnel timestamp for this server.
                clearServerMeta(key)
                Logger.i(LOG_TAG_PROXY, "$tag; disableWinServer: disabled rpn: $key")
            } catch (e: Exception) {
                Logger.e(LOG_TAG_PROXY, "$tag; disableWinServer: failed to update DB for $key: ${e.message}", e)
                // Revert cache change on DB failure
                winCacheMutex.withLock {
                    winServersCache.filter { it.key == key }.forEach { it.isEnabled = true }
                }
                config.isEnabled = true
                return Pair(false, "Failed to update database: ${e.message}")
            }
        } else {
            Logger.w(LOG_TAG_PROXY, "$tag; disableWinServer: failed to disable server with key $key, error: ${res.second}")
        }
        return res
    }

    suspend fun ensureAutoServerExists() {
        try {
            val autoServer = countryConfigRepo.getById(autoServerId) ?: countryConfigRepo.getById(autoServerId.lowercase())
            if (autoServer == null) {
                Logger.i(LOG_TAG_PROXY, "$tag; ensureAutoServerExists: AUTO server not found, creating...")
                val newAutoServer = createAutoServer()
                countryConfigRepo.insert(newAutoServer)
                // Add to cache
                winCacheMutex.withLock {
                    winServersCache.add(newAutoServer)
                }
                Logger.i(LOG_TAG_PROXY, "$tag; ensureAutoServerExists: AUTO server created successfully")
            } else {
                Logger.v(LOG_TAG_PROXY, "$tag; ensureAutoServerExists: AUTO server already exists")
                // Ensure it's in the cache
                winCacheMutex.withLock {
                    if (winServersCache.none { it.id.equals(autoServerId, true) }) {
                        winServersCache.add(autoServer)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; ensureAutoServerExists: error - ${e.message}", e)
        }
    }

    fun createAutoServer(): CountryConfig {
        return RpnWinAutoServer.create(autoServerId, autoCountryCode, now())
    }

    suspend fun getAutoServer(): CountryConfig? {
        return try {
            val cached = winCacheMutex.withLock {
                winServersCache.find { it.id.equals(autoServerId, true) }
            }
            if (cached != null) return cached

            val fromDb = countryConfigRepo.getById(autoServerId) ?: countryConfigRepo.getById(autoServerId.lowercase())
            if (fromDb != null) {
                // Opportunistically warm the cache so subsequent calls hit the fast path.
                winCacheMutex.withLock {
                    if (winServersCache.none { it.id.equals(autoServerId, true) }) {
                        winServersCache.add(fromDb)
                    }
                }
                return fromDb
            }

            Logger.w(LOG_TAG_PROXY, "$tag; getAutoServer: AUTO server missing from cache and DB, creating via ensureAutoServerExists")
            ensureAutoServerExists()

            winCacheMutex.withLock {
                winServersCache.find { it.id.equals(autoServerId, true) }
            } ?: countryConfigRepo.getById(autoServerId) ?: countryConfigRepo.getById(autoServerId.lowercase())
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; getAutoServer: err: ${e.message}", e)
            null
        }
    }

    suspend fun updateAutoServerState(autoServer: CountryConfig) {
        try {
            countryConfigRepo.update(autoServer)
            // Update cache
            winCacheMutex.withLock {
                val index = winServersCache.indexOfFirst { it.key == autoServerId }
                if (index >= 0) {
                    winServersCache[index] = autoServer
                }
            }
            Logger.i(LOG_TAG_PROXY, "$tag; updateAutoServerState: AUTO server updated, isEnabled=${autoServer.isEnabled}")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; updateAutoServerState: error - ${e.message}", e)
        }
    }

    suspend fun syncWinServers(servers: Set<CountryConfig>) {
        try {
            // Ensure AUTO server exists before syncing
            ensureAutoServerExists()

            // get the current server list before sync so we can find removed servers
            // and clean up their ProxyApplicationMapping entries.
            val serversBefore = try {
                countryConfigRepo.getAllConfigs()
            } catch (e: Exception) {
                Logger.w(LOG_TAG_PROXY, "$tag; syncWinServers: could not read pre-sync servers: ${e.message}")
                emptyList()
            }

            // Sync to database (this handles insertions, updates, and deletions)
            val syncServerList = if (servers.isEmpty()) {
                Logger.w(LOG_TAG_PROXY, "$tag; syncWinServers: empty server list, clearing DB except AUTO")
                emptyList()
            } else {
                servers.toList()
            }

            // Sync to database (AUTO server is protected in the repository method)
            countryConfigRepo.syncServers(syncServerList)

            // clean up proxy-app mappings for every server that was removed from the DB.
            val newServerKeys = servers.map { it.key }.toSet()
            val removedServers = RpnWinLocationMapper.staleServers(serversBefore, newServerKeys, autoServerId)
            if (removedServers.isNotEmpty()) {
                removedServers.forEach { removed ->
                    removeProxyMapping(Backend.RpnWin + removed.key)
                    Logger.i(LOG_TAG_PROXY, "$tag; syncWinServers: removed proxy mapping for stale server key=${removed.key}")
                }
            }

            // Read back from DB to ensure consistency
            val existingServers = try {
                countryConfigRepo.getAllConfigs()
            } catch (e: Exception) {
                Logger.e(LOG_TAG_PROXY, "$tag; syncWinServers: failed to read from DB after sync: ${e.message}", e)
                emptyList()
            }

            // Update cache - clear and refill with new data
            winCacheMutex.withLock {
                winServersCache.clear()
                if (existingServers.isNotEmpty()) {
                    winServersCache.addAll(existingServers)
                }
            }

            Logger.i(LOG_TAG_PROXY, "$tag; syncWinServers: synced ${servers.size} servers to DB, ${existingServers.size} in cache, ${removedServers.size} proxy mappings cleaned")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; syncWinServers: error - ${e.message}", e)
        }
    }

    suspend fun refreshWinServers(): Pair<List<CountryConfig>, List<CountryConfig>> {
        try {
            val existingServers = countryConfigRepo.getAllConfigs()
            val (newServers, removedSelectedIds) = fetchLocations()

            if (newServers.isEmpty()) {
                retryLocationFetch()
                return Pair(emptyList(), emptyList())
            }

            // Sync to DB and cache
            syncWinServers(newServers)

            // Find the actual removed server objects for notification
            val removedServers = existingServers.filter { removedSelectedIds.contains(it.id) && it.id.equals(autoServerId, true) }

            Logger.i(LOG_TAG_PROXY, "$tag; refreshWinServers: refreshed ${newServers.size} servers, ${removedServers.size} selected servers removed")
            // Return the cache populated by syncWinServers (read from DB) instead of the
            // freshly-constructed API objects.  API objects are built with default field values
            // (e.g. isFavourite = false), so returning them would silently clear every country's
            // favourite flag in the in-memory allServers list inside ServerSelectionFragment.
            val freshList = winCacheMutex.withLock { winServersCache.toList() }
            return Pair(freshList, removedServers)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; refreshWinServers: error - ${e.message}", e)
            return Pair(emptyList(), emptyList())
        }
    }

    suspend fun updateSsidBased(key: String, ssidBased: Boolean) {
        if (key.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$tag; updateSsidBased: cc is empty")
            return
        }

        val config = winCacheMutex.withLock {
            winServersCache.find { it.key == key }
        }

        if (config == null) {
            Logger.w(LOG_TAG_PROXY, "$tag; updateSsidBased: config not found in cache for key: $key")
            return
        }

        val oldValue = config.ssidBased

        try {
            countryConfigRepo.updateSsidBased(key, ssidBased)

            // Update cache
            winCacheMutex.withLock {
                val cachedConfig = winServersCache.find { it.key == key }
                if (cachedConfig != null) {
                    winServersCache.remove(cachedConfig)
                    winServersCache.add(cachedConfig.copy(ssidBased = ssidBased, lastModified = now()))
                } else {
                    Logger.w(LOG_TAG_PROXY, "$tag; updateSsidBased: config disappeared from cache for key: $key")
                }
            }

            Logger.i(LOG_TAG_PROXY, "$tag; updateSsidBased: $key = $ssidBased")

            // Trigger connection monitor to update SSID info if country is selected
            val keyExists = winCacheMutex.withLock { winServersCache.any { it.key == key } }
            if (keyExists) {
                try {
                    if (oldValue != ssidBased) {
                        notifyConnectionMonitor(true)
                    }
                } catch (e: Exception) {
                    Logger.e(LOG_TAG_PROXY, "$tag; updateSsidBased: failed to refresh proxies: ${e.message}", e)
                    // Don't revert DB change if only the VPN controller refresh failed
                }
            }
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; error updating SSID based for $key: ${e.message}", e)
            // Revert cache on DB failure
            winCacheMutex.withLock {
                val cachedConfig = winServersCache.find { it.key == key }
                if (cachedConfig != null) {
                    winServersCache.remove(cachedConfig)
                    winServersCache.add(cachedConfig.copy(ssidBased = oldValue))
                }
            }
        }

        if (config.isEnabled) {
            // Refresh proxies to immediately pause/resume based on new SSID setting
            refreshProxies()
        }
    }

    suspend fun updateSsids(key: String, ssids: String) {
        if (key.isEmpty()) {
            Logger.w(LOG_TAG_PROXY, "$tag; updateSsids: cc is empty")
            return
        }

        val config = winCacheMutex.withLock {
            winServersCache.find { it.key == key }
        }

        if (config == null) {
            Logger.w(LOG_TAG_PROXY, "$tag; updateSsids: config not found in cache for key: $key")
            return
        }

        val oldSsids = config.ssids

        try {
            countryConfigRepo.updateSsids(key, ssids)

            // Update cache
            winCacheMutex.withLock {
                val cachedConfig = winServersCache.find { it.key == key }
                if (cachedConfig != null) {
                    winServersCache.remove(cachedConfig)
                    winServersCache.add(cachedConfig.copy(ssids = ssids, lastModified = now()))
                } else {
                    Logger.w(LOG_TAG_PROXY, "$tag; updateSsids: config disappeared from cache for key: $key")
                }
            }

            Logger.i(LOG_TAG_PROXY, "$tag; updateSsids: $key, ssids length=${ssids.length}")

            // If country is selected and SSID based enabled, refresh proxies.
            // Read both pieces of state in one lock to avoid a bare read.
            val (keyExists, isSsidBased) = winCacheMutex.withLock {
                val c = winServersCache.find { it.key == key }
                Pair(c != null, c?.ssidBased == true)
            }
            if (keyExists && isSsidBased) {
                try {
                    notifyConnectionMonitor(true)
                } catch (e: Exception) {
                    Logger.e(LOG_TAG_PROXY, "$tag; updateSsids: failed to refresh proxies: ${e.message}", e)
                    // Don't revert DB change if only the VPN controller refresh failed
                }
            }
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; error updating SSIDs for $key: ${e.message}", e)
            // Revert cache on DB failure
            winCacheMutex.withLock {
                val cachedConfig = winServersCache.find { it.key == key }
                if (cachedConfig != null) {
                    winServersCache.remove(cachedConfig)
                    winServersCache.add(cachedConfig.copy(ssids = oldSsids))
                }
            }
        }

        if (config.isEnabled) {
            // refresh proxies to immediately pause/resume based on new SSID setting
            refreshProxies()
        }
    }

    suspend fun getCountryConfigByKey(key: String): CountryConfig? {
        return try {
            winCacheMutex.withLock {
                winServersCache.find { it.key == key }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; error getting country config for $key: ${e.message}", e)
            null
        }
    }
}
