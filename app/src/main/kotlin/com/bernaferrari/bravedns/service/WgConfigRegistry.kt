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

import com.bernaferrari.bravedns.database.WgConfigFilesImmutable
import com.bernaferrari.bravedns.wireguard.Config
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory lifecycle for parsed WireGuard [Config]s and their [WgConfigFilesImmutable]
 * mappings. [WireguardManager] is the single writer; tests may reach these collections
 * via the manager's `registry` field.
 */
internal class WgConfigRegistry {
    // Readers retain a complete snapshot while writers prepare the next one.
    @Volatile
    var mappings: CopyOnWriteArraySet<WgConfigFilesImmutable> = CopyOnWriteArraySet()
        private set
    @Volatile
    var configs: CopyOnWriteArrayList<Config> = CopyOnWriteArrayList()
        private set
    var lastAddedConfigId: Int = 0

    @Synchronized
    fun clear() {
        configs = CopyOnWriteArrayList()
        mappings = CopyOnWriteArraySet()
    }

    @Synchronized
    fun replaceMappings(newMappings: Collection<WgConfigFilesImmutable>) {
        mappings = CopyOnWriteArraySet(newMappings)
    }

    fun findConfig(id: Int): Config? = configs.find { it.getId() == id }

    fun findMapping(id: Int): WgConfigFilesImmutable? = mappings.find { it.id == id }

    fun hasConfigs(): Boolean = configs.isNotEmpty()

    fun configCount(): Int = configs.size

    @Synchronized
    fun addConfigIfAbsent(id: Int, build: () -> Config) {
        if (configs.none { it.getId() == id }) configs = CopyOnWriteArrayList(configs + build())
    }

    @Synchronized
    fun putConfig(cfg: Config) {
        // Config.equals compares contents, not IDs. Distinct imported configurations must
        // remain addressable by ID even when their contents are identical.
        configs = CopyOnWriteArrayList(configs.filterNot { it.getId() == cfg.getId() } + cfg)
    }

    @Synchronized
    fun removeConfig(id: Int) {
        configs = CopyOnWriteArrayList(configs.filterNot { it.getId() == id })
    }

    @Synchronized
    fun removeMapping(id: Int) {
        mappings = CopyOnWriteArraySet(mappings.filterNot { it.id == id })
    }

    @Synchronized
    fun replaceMapping(
        id: Int,
        transform: (WgConfigFilesImmutable) -> WgConfigFilesImmutable,
    ): WgConfigFilesImmutable? {
        val current = mappings.find { it.id == id } ?: return null
        val next = transform(current)
        mappings = CopyOnWriteArraySet(mappings.filterNot { it.id == id } + next)
        return next
    }

    @Synchronized
    fun putMapping(mapping: WgConfigFilesImmutable) {
        mappings = CopyOnWriteArraySet(mappings.filterNot { it.id == mapping.id } + mapping)
    }

    fun addOrUpdateConfigFileMapping(
        cfg: Config,
        file: WgConfigFilesImmutable?,
        path: String,
        serverResponse: String,
    ) {
        if (file == null) {
            putMapping(
                WgConfigFilesImmutable(
                    cfg.getId(),
                    cfg.getName(),
                    path,
                    serverResponse,
                    isActive = false,
                    isCatchAll = false,
                    isLockdown = false,
                    oneWireGuard = false,
                    isDeletable = true,
                    useOnlyOnMetered = false,
                    ssidEnabled = false,
                    ssids = ""
                )
            )
        } else {
            putMapping(file)
        }
    }

    fun allocateId(): Int {
        lastAddedConfigId += 1
        return lastAddedConfigId
    }

    fun allMappings(): List<WgConfigFilesImmutable> = mappings.toList()

    fun mappingCount(): Int = mappings.size

    fun activeCount(): Int = mappings.count { it.isActive }

    fun anyActive(): Boolean = mappings.any { it.isActive }

    fun anyAdvancedActive(): Boolean = mappings.any { it.isActive && !it.oneWireGuard }

    fun oneWireGuardEnabled(): Boolean = mappings.any { it.oneWireGuard && it.isActive }

    fun oneWireGuardId(): Int? = mappings.find { it.oneWireGuard && it.isActive }?.id

    fun anyOtherOneWgEnabled(id: Int): Boolean =
        mappings.any { it.oneWireGuard && it.isActive && it.id != id }

    fun canDisableAllActive(): Boolean = mappings.none { it.isActive && it.isCatchAll }

    fun activeSsidEnabled(): List<WgConfigFilesImmutable> =
        mappings.filter { it.ssidEnabled && it.isActive }

    fun activeCatchAll(): List<WgConfigFilesImmutable> =
        mappings.filter { it.isActive && it.isCatchAll }

    fun activeConfigs(): List<Config> {
        val m = mappings.filter { it.isActive }
        val l = mutableListOf<Config>()
        m.forEach {
            val config = configs.find { it1 -> it1.getId() == it.id }
            if (config != null) {
                l.add(config)
            }
        }
        return l
    }

    fun isMappingActive(id: Int): Boolean = mappings.find { it.id == id }?.isActive == true
}
