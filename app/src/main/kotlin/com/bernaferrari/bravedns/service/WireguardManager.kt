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
package com.bernaferrari.bravedns.service

import Logger
import Logger.LOG_TAG_PROXY
import android.content.Context
import android.text.format.DateUtils
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.WgConfigFiles
import com.bernaferrari.bravedns.database.WgConfigFilesImmutable
import com.bernaferrari.bravedns.database.WgConfigFilesRepository
import com.bernaferrari.bravedns.service.ProxyManager.ID_WG_BASE
import com.bernaferrari.bravedns.wireguard.Config
import com.bernaferrari.bravedns.wireguard.Peer
import com.bernaferrari.bravedns.wireguard.WgHopManager
import com.bernaferrari.bravedns.wireguard.WgInterface
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.IPMetadata
import com.celzero.firestack.backend.RouterStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object WireguardManager : KoinComponent {

    private val db: WgConfigFilesRepository by inject()
    private val applicationContext: Context by inject()
    private val appConfig: AppConfig by inject()
    private val persistentState: PersistentState by inject()

    internal val registry = WgConfigRegistry()
    private val hopSelector = WgHopSelector()
    private val store by lazy {
        WgConfigStore(applicationContext, db, persistentState)
    }

    // 120 sec + 5 sec buffer
    const val WG_HANDSHAKE_TIMEOUT = 125 * DateUtils.SECOND_IN_MILLIS
    const val WG_UPTIME_THRESHOLD = 5 * DateUtils.SECOND_IN_MILLIS

    const val NOTIF_CHANNEL_ID_WIREGUARD_ALERTS = "WireGuard_Alerts"

    const val ERR_CODE_VPN_NOT_ACTIVE = "1"
    const val ERR_CODE_VPN_NOT_FULL = "2"
    const val ERR_CODE_OTHER_WG_ACTIVE = "3"
    const val ERR_CODE_WG_INVALID = "4"

    const val INVALID_CONF_ID = -1

    const val WARP_ID = 1
    const val WARP_NAME = "WARP"

    const val SEC_WARP_ID = 0
    const val SEC_WARP_NAME = "SEC_WARP"

    init {
        io { load(forceRefresh = false) }
    }

    suspend fun load(forceRefresh: Boolean): Int {
        store.migrateEncryptedConfigsIfNeeded()

        if (!forceRefresh && registry.hasConfigs()) {
            Logger.i(LOG_TAG_PROXY, "configs already loaded; returning...")
            return registry.configCount()
        }
        registry.lastAddedConfigId = db.getLastAddedConfigId()
        registry.replaceMappings(db.getWgConfigs().map { it.toImmutable() })
        for (it in registry.mappings) {
            val path = it.configPath
            if (!store.ensurePlaintextConfigFile(path)) {
                Logger.w(LOG_TAG_PROXY, "wg config missing or undecryptable: $path")
                if (it.isActive) {
                    val inactive = it.copy(isActive = false, oneWireGuard = false)
                    registry.putMapping(inactive)
                    db.disableConfig(it.id)
                    VpnController.removeWireGuardProxy(it.id)
                }
                continue
            }
            val parsed = store.parseConfigFile(path)
            if (parsed.isFailure) {
                continue
            }
            val config = parsed.getOrNull()
            if (config == null) {
                Logger.e(LOG_TAG_PROXY, "err loading wg config: $path, invalid config")
                if ((it.id == WARP_ID && it.name == WARP_NAME) || (it.id == SEC_WARP_ID && it.name == SEC_WARP_NAME)) {
                    deleteConfig(it.id)
                }
                continue
            }
            registry.putConfig(WgConfigParser.withIdentity(config, it.id, it.name))
            refreshSplitProxyInfo(ID_WG_BASE + it.id)
            Logger.d(LOG_TAG_PROXY, "read wg config: ${it.id}, ${it.name}")
        }
        return registry.configCount()
    }

    private fun clearLoadedConfigs() {
        registry.clear()
    }

    fun getConfigById(id: Int): Config? {
        val config = registry.findConfig(id)
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "getConfigById: wg not found: $id, ${registry.configCount()}")
        }
        return config
    }

    fun getConfigFilesById(id: Int): WgConfigFilesImmutable? {
        val config = registry.findMapping(id)
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "getConfigFilesById: wg not found: $id, ${registry.configCount()}")
        }
        return config
    }

    fun refreshSplitProxyInfo(proxyId: String) {
        val id = proxyId.removePrefix(ID_WG_BASE).toIntOrNull() ?: return
        val ips = registry.findConfig(id)?.getPeers().orEmpty().flatMap { it.getAllowedIps() }.map { it.toString() }
        updateSplitProxyInfo(proxyId, WireguardRoutePolicy.isSplit(ips))
    }

    fun updateSplitProxyInfo(proxyId: String, isSplitProxy: Boolean) {
        val id = proxyId.removePrefix(ID_WG_BASE).toIntOrNull() ?: return
        registry.replaceMapping(id) { it.copy(isSplitProxy = isSplitProxy) }
    }

    fun isAnyWgActive(): Boolean = registry.anyActive()

    fun isAdvancedWgActive(): Boolean = registry.anyAdvancedActive()

    fun getAllMappings(): List<WgConfigFilesImmutable> = registry.allMappings()

    fun getNumberOfMappings(): Int = registry.mappingCount()

    fun getActiveWgCount() = registry.activeCount()

    fun getActiveConfigs(): List<Config> = registry.activeConfigs()

    fun isConfigActive(configId: String): Boolean {
        val id = configId.split(ID_WG_BASE).last().toIntOrNull() ?: return false
        return registry.isMappingActive(id)
    }

    private val activationMutex = Mutex()

    suspend fun enableConfig(unmapped: WgConfigFilesImmutable) = activationMutex.withLock {
        enableConfigLocked(unmapped)
    }

    private suspend fun enableConfigLocked(unmapped: WgConfigFilesImmutable) {
        val map = registry.findMapping(unmapped.id)
        if (map == null) {
            Logger.e(
                LOG_TAG_PROXY,
                "enableConfig: wg not found, id: ${unmapped.id}, ${registry.mappingCount()}"
            )
            return
        }

        val config = registry.findConfig(map.id)
        if (config == null) {
            Logger.w(LOG_TAG_PROXY, "config not found: ${map.id}")
            return
        }

        if (getConflictingActiveConfigId(map.id) != null) {
            Logger.w(LOG_TAG_PROXY, "refusing duplicate active WireGuard identity for config ${map.id}")
            return
        }
        refreshSplitProxyInfo(ID_WG_BASE + map.id)
        val newMap = registry.replaceMapping(map.id) { it.copy(isActive = true) } ?: return
        val dbMap = WgConfigFiles.fromImmutable(newMap)
        db.update(dbMap)
        val proxyType = AppConfig.ProxyType.WIREGUARD
        val proxyProvider = AppConfig.ProxyProvider.WIREGUARD
        appConfig.addProxy(proxyType, proxyProvider)
        VpnController.addWireGuardProxy(ID_WG_BASE + map.id)
        Logger.i(LOG_TAG_PROXY, "enable wg config: ${map.id}, ${map.name}")
        return
    }

    fun getConflictingActiveConfigId(id: Int): Int? {
        val candidate = registry.findConfig(id) ?: return null
        return registry.activeConfigs().firstOrNull {
            it.getId() != id && WireguardIdentity.conflicts(candidate, it)
        }?.getId()
    }

    fun canEnableProxy(): Boolean {
        return appConfig.canEnableProxy()
    }

    fun isValidConfig(id: Int): Boolean {
        val config = registry.findConfig(id)
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "canEnableConfig: wg not found, id: ${id}, ${registry.configCount()}")
            return false
        }
        return true
    }

    fun isAnyOtherOneWgEnabled(id: Int): Boolean = registry.anyOtherOneWgEnabled(id)

    fun canDisableConfig(map: WgConfigFilesImmutable): Boolean {
        return when {
            map.isCatchAll -> false
            WgHopManager.isWgEitherHopOrSrc(map.id) -> false
            else -> true
        }
    }

    fun canDisableAllActiveConfigs(): Boolean = registry.canDisableAllActive()

    fun getConfigName(id: Int): String {
        val config = registry.findConfig(id)
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "getConfigName: wg not found, id: ${id}, ${registry.configCount()}")
            return ""
        }
        return config.getName()
    }

    suspend fun disableAllActiveConfigs() {
        val activeConfigs = registry.mappings.filter { it.isActive }
        activeConfigs.forEach {
            disableConfig(it)
            updateOneWireGuardConfig(it.id, false)
        }
    }

    fun disableConfig(unmapped: WgConfigFilesImmutable) {
        val m = registry.findMapping(unmapped.id)
        if (m == null) {
            Logger.e(
                LOG_TAG_PROXY,
                "disableConfig: wg not found, id: ${unmapped.id}, ${registry.mappingCount()}"
            )
            return
        }

        val config = registry.findConfig(unmapped.id)
        if (config == null) {
            Logger.w(LOG_TAG_PROXY, "config not found: ${unmapped.id}")
            return
        }

        val newMap = registry.replaceMapping(m.id) {
            it.copy(isActive = false, oneWireGuard = false)
        } ?: return

        io { db.disableConfig(newMap.id) }
        if (registry.mappings.none { it.isActive }) {
            val proxyType = AppConfig.ProxyType.WIREGUARD
            val proxyProvider = AppConfig.ProxyProvider.WIREGUARD
            appConfig.removeProxy(proxyType, proxyProvider)
        }
        io { VpnController.removeWireGuardProxy(newMap.id) }
        Logger.i(LOG_TAG_PROXY, "disable wg config: ${newMap.id}, ${newMap.name}")
        return
    }

    suspend fun getAllPossibleConfigIdsForApp(
        uid: Int,
        ip: String,
        port: Int,
        domain: String,
        usesMobileNw: Boolean,
        ssid: String,
        default: String,
    ): List<String> {
        val oneWgId = if (oneWireGuardEnabled()) {
            getOneWireGuardProxyId() ?: INVALID_CONF_ID
        } else {
            null
        }
        val wgProxyIdsForApp = ProxyManager.getProxyIdsForApp(uid).filter { it.startsWith(ID_WG_BASE) }
        return hopSelector.collectProxyIds(
            uid = uid,
            ip = ip,
            port = port,
            domain = domain,
            usesMobileNw = usesMobileNw,
            ssid = ssid,
            default = default,
            oneWireGuardId = oneWgId,
            wgProxyIdsForApp = wgProxyIdsForApp,
            mappings = registry.mappings,
        )
    }

    fun matchesSsidList(ssidList: String, ssid: String): Boolean {
        return WgSsidMatcher.matchesSsidList(ssidList, ssid)
    }

    suspend fun addConfig(config: Config?, name: String = ""): Config? {
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "err adding config")
            return null
        }
        val id = registry.allocateId()
        val n = name.ifEmpty { "${Backend.WG}$id" }
        val cfg = WgConfigParser.withIdentity(config, id, n)
        writeConfigAndUpdateDb(cfg)
        Logger.d(LOG_TAG_PROXY, "config added: ${config.getId()}, ${config.getName()}")
        return config
    }

    suspend fun addOrUpdateInterface(
        configId: Int,
        configName: String,
        wgInterface: WgInterface
    ): Config? {
        return if (configId <= 0) {
            addInterface(configName, wgInterface)
        } else {
            updateInterface(configId, configName, wgInterface)
        }
    }

    private suspend fun addInterface(configName: String, wgInterface: WgInterface): Config {
        val id = registry.allocateId()
        val name = configName.ifEmpty { "wg$id" }
        val cfg = WgConfigParser.rebuild(id, name, wgInterface, peers = null)
        writeConfigAndUpdateDb(cfg)
        Logger.d(LOG_TAG_PROXY, "interface added for config: $id, $name")
        return cfg
    }

    private suspend fun updateInterface(
        configId: Int,
        configName: String,
        wgInterface: WgInterface
    ): Config? {
        val config = registry.findConfig(configId)
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "updateInterface: wg not found, id: $configId, ${registry.configCount()}")
            return null
        }
        val cfg = WgConfigParser.rebuild(config.getId(), configName, wgInterface, config.getPeers())
        Logger.i(LOG_TAG_PROXY, "updating interface for config: $configId, ${config.getName()}")
        val cfgId = ID_WG_BASE + configId
        if (configName != config.getName()) {
            ProxyManager.updateProxyNameForProxyId(cfgId, configName)
        }
        writeConfigAndUpdateDb(cfg)
        return cfg
    }

    suspend fun deleteConfig(id: Int) {
        val cf = registry.findMapping(id)
        Logger.i(LOG_TAG_PROXY, "deleteConfig start: $id, ${cf?.name}, ${cf?.configPath}")

        val config = registry.findConfig(id)
        if (cf?.isActive == true) {
            Logger.e(LOG_TAG_PROXY, "wg config is active for id: $id")
            disableConfig(cf)
        }

        withContext(Dispatchers.IO) {
            store.deleteConfigFile(id)
            db.deleteConfig(id)
            val proxyId = ID_WG_BASE + id
            ProxyManager.removeProxyId(proxyId)
            registry.removeMapping(id)
            if (config != null) registry.removeConfig(id)
            WgHopManager.handleWgDelete(id)
        }
    }

    suspend fun deleteAllConfigs() {
        val allIds = registry.configs.map { it.getId() }
        allIds.forEach { id ->
            deleteConfig(id)
        }
        appConfig.removeAllProxies()
    }

    suspend fun updateLockdownConfig(id: Int, isLockdown: Boolean) {
        val config = registry.findConfig(id)
        val map = registry.findMapping(id)
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "updateLockdownConfig: wg not found, id: $id, ${registry.configCount()}")
            return
        }
        Logger.i(LOG_TAG_PROXY, "updating lockdown for config: $id, ${config.getPeers()}")
        db.updateLockdownConfig(id, isLockdown)
        registry.replaceMapping(id) { it.copy(name = config.getName(), isLockdown = isLockdown) } ?: return
        if (map?.isActive == true) {
            VpnController.addWireGuardProxy(id = ID_WG_BASE + config.getId())
            VpnController.notifyConnectionMonitor()
        }
    }

    suspend fun updateCatchAllConfig(id: Int, isEnabled: Boolean) {
        val config = registry.findConfig(id)
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "updateCatchAllConfig: wg not found, id: $id, ${registry.configCount()}")
            return
        }
        Logger.i(LOG_TAG_PROXY, "updating catch all for config: $id, ${config.getName()}")
        db.updateCatchAllConfig(id, isEnabled)
        val newMap = registry.replaceMapping(id) { it.copy(name = config.getName(), isCatchAll = isEnabled) } ?: return
        enableConfig(newMap)
    }

    suspend fun updateOneWireGuardConfig(id: Int, owg: Boolean) {
        val config = registry.findConfig(id)
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "update one wg: id($id) not found, size: ${registry.configCount()}")
            return
        }
        Logger.i(LOG_TAG_PROXY, "update one wg, id: $id, ${config.getName()} to $owg")
        db.updateOneWireGuardConfig(id, owg)
        registry.replaceMapping(id) { it.copy(name = config.getName(), oneWireGuard = owg) } ?: return
    }

    suspend fun updateUseOnMobileNetworkConfig(id: Int, useMobileNw: Boolean) {
        val config = registry.findConfig(id)
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "update useMobileNw: wg not found, id: $id, ${registry.configCount()}")
            return
        }
        Logger.i(LOG_TAG_PROXY, "updating useMobileNw as $useMobileNw for config: $id, ${config.getName()}")
        db.updateMobileConfig(id, useMobileNw)
        val m = registry.findMapping(id) ?: return
        registry.replaceMapping(id) { it.copy(name = config.getName(), useOnlyOnMetered = useMobileNw) }
        if (m.isActive) {
            VpnController.addWireGuardProxy(id = ID_WG_BASE + id)
            VpnController.refreshOrPauseOrResumeOrReAddProxies()
        }
    }

    suspend fun updateSsidEnabled(id: Int, ssidEnabled: Boolean) {
        val config = registry.findConfig(id)
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "update ssid: wg not found, id: $id, ${registry.configCount()}")
            return
        }
        val m = registry.findMapping(id) ?: return
        if (m.ssidEnabled == ssidEnabled) {
            Logger.i(LOG_TAG_PROXY, "ssidEnabled is already $ssidEnabled for config: $id, ${config.getName()}")
            return
        }

        db.updateSsidEnabled(id, ssidEnabled)
        registry.replaceMapping(id) { it.copy(name = config.getName(), ssidEnabled = ssidEnabled) }
        Logger.i(LOG_TAG_PROXY, "updated ssidEnabled as $ssidEnabled for config: $id, ${config.getName()}")
        if (m.isActive) {
            if (ssidEnabled) VpnController.notifyConnectionMonitor(enforcePolicyChange = true)
            VpnController.addWireGuardProxy(id = ID_WG_BASE + id)
            VpnController.refreshOrPauseOrResumeOrReAddProxies()
        }
    }

    suspend fun updateSsids(id: Int, ssids: String) {
        val config = registry.findConfig(id)
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "update ssids: wg not found, id: $id, ${registry.configCount()}")
            return
        }
        Logger.i(LOG_TAG_PROXY, "updating ssids as $ssids for config: $id, ${config.getName()}")
        db.updateSsids(id, ssids)
        val m = registry.findMapping(id) ?: return
        registry.replaceMapping(id) { it.copy(name = config.getName(), ssids = ssids) }
        if (m.isActive) {
            VpnController.addWireGuardProxy(id = ID_WG_BASE + id)
            VpnController.refreshOrPauseOrResumeOrReAddProxies()
        }
    }

    suspend fun addPeer(id: Int, peer: Peer) {
        val config = registry.findConfig(id)
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "addPeer: wg not found, id: $id, ${registry.configCount()}")
            return
        }
        val peers = config.getPeers() ?: mutableListOf()
        val newPeers = peers.toMutableList()
        newPeers.add(peer)
        val cfg = WgConfigParser.rebuild(config.getId(), config.getName(), config.getInterface(), newPeers)
        Logger.i(LOG_TAG_PROXY, "adding peer for config: $id, ${cfg.getName()}, ${newPeers.size}")
        writeConfigAndUpdateDb(cfg)
    }

    suspend fun deletePeer(id: Int, peer: Peer) {
        val config = registry.findConfig(id)
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "deletePeer: wg not found, id: $id, ${registry.configCount()}")
            return
        }
        val peers = config.getPeers()?.toMutableList()
        if (peers == null) {
            Logger.e(LOG_TAG_PROXY, "peers not found for config: $id")
            return
        }
        val isRemoved =
            peers.removeIf {
                it.getPublicKey() == peer.getPublicKey() &&
                    it.getEndpoint() == peer.getEndpoint() &&
                    it.getAllowedIps() == peer.getAllowedIps() &&
                    it.getPreSharedKey() == peer.getPreSharedKey()
            }
        Logger.d(
            LOG_TAG_PROXY,
            "new peers: ${peers.size}, ${peer.getPublicKey().base64()} is removed? $isRemoved"
        )
        val cfg = WgConfigParser.rebuild(config.getId(), config.getName(), config.getInterface(), peers)
        Logger.i(LOG_TAG_PROXY, "deleting peer for config: $id, ${cfg.getName()}")
        writeConfigAndUpdateDb(cfg)
    }

    private suspend fun writeConfigAndUpdateDb(cfg: Config, serverResponse: String = "") {
        val result = store.persist(cfg, serverResponse)
        if (result.existing == null) {
            registry.addOrUpdateConfigFileMapping(cfg, null, result.path, serverResponse)
        } else {
            registry.addOrUpdateConfigFileMapping(
                cfg,
                result.existing.toImmutable(),
                result.path,
                serverResponse
            )
        }
        registry.putConfig(cfg)
        refreshSplitProxyInfo(ID_WG_BASE + cfg.getId())
        if (result.existing?.isActive == true) {
            VpnController.addWireGuardProxy(id = ID_WG_BASE + cfg.getId(), force = true)
        }
    }

    data class WgStats(
        val routerStats: RouterStats?,
        val mtu: Long?,
        val status: Int?,
        val ip4: Boolean?,
        val ip6: Boolean?,
        val clientV4: IPMetadata? = null,
        val clientV6: IPMetadata? = null
    )

    suspend fun stats(): String {
        val s = WgStatsFormatter.formatActive(
            mappings = registry.mappings,
            statsFor = { proxyId -> VpnController.getWireGuardStats(proxyId) },
        )
        Logger.d(LOG_TAG_PROXY, "wg stats: $s")
        return s
    }

    fun getPeers(id: Int): MutableList<Peer> {
        return registry.findConfig(id)?.getPeers()?.toMutableList() ?: mutableListOf()
    }

    suspend fun restoreProcessRetrieveWireGuardConfigs() {
        val count = db.getWgConfigs().size
        Logger.i(LOG_TAG_PROXY, "restoreProcessRetrieveWireGuardConfigs: db wg entries count: $count")
        clearLoadedConfigs()
        performRestore()
        val loadedCount = load(forceRefresh = true)
        Logger.i(LOG_TAG_PROXY, "restoreProcessRetrieveWireGuardConfigs: completed, loaded $loadedCount configs")
    }

    suspend fun performRestore() {
        store.performRestore()
    }

    fun oneWireGuardEnabled(): Boolean = registry.oneWireGuardEnabled()

    fun getActiveSsidEnabledConfigs(): List<WgConfigFilesImmutable> = registry.activeSsidEnabled()

    fun getOneWireGuardProxyId(): Int? = registry.oneWireGuardId()

    fun getActiveCatchAllConfig(): List<WgConfigFilesImmutable> = registry.activeCatchAll()

    private fun io(f: suspend () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch { f() }
    }
}
