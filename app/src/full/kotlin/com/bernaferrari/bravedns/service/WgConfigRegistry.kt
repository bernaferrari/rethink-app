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

/**
 * In-memory lifecycle for parsed WireGuard [Config]s and their [WgConfigFilesImmutable]
 * mappings. [WireguardManager] is the single writer; tests may reach these collections
 * via the manager's `registry` field.
 */
internal class WgConfigRegistry {
    val mappings: CopyOnWriteArraySet<WgConfigFilesImmutable> = CopyOnWriteArraySet()
    val configs: CopyOnWriteArraySet<Config> = CopyOnWriteArraySet()
    var lastAddedConfigId: Int = 0

    fun clear() {
        configs.clear()
        mappings.clear()
    }

    fun replaceMappings(newMappings: Collection<WgConfigFilesImmutable>) {
        mappings.clear()
        mappings.addAll(newMappings)
    }

    fun findConfig(id: Int): Config? = configs.find { it.getId() == id }

    fun findMapping(id: Int): WgConfigFilesImmutable? = mappings.find { it.id == id }

    fun hasConfigs(): Boolean = configs.isNotEmpty()

    fun configCount(): Int = configs.size

    fun addConfigIfAbsent(id: Int, build: () -> Config) {
        if (configs.none { it.getId() == id }) {
            configs.add(build())
        }
    }

    fun putConfig(cfg: Config) {
        val existing = configs.find { it.getId() == cfg.getId() }
        if (existing != null) configs.remove(existing)
        configs.add(cfg)
    }

    fun removeConfig(id: Int) {
        configs.find { it.getId() == id }?.let { configs.remove(it) }
    }

    fun removeMapping(id: Int) {
        mappings.find { it.id == id }?.let { mappings.remove(it) }
    }

    fun replaceMapping(
        id: Int,
        transform: (WgConfigFilesImmutable) -> WgConfigFilesImmutable,
    ): WgConfigFilesImmutable? {
        val current = mappings.find { it.id == id } ?: return null
        mappings.remove(current)
        val next = transform(current)
        mappings.add(next)
        return next
    }

    fun putMapping(mapping: WgConfigFilesImmutable) {
        mappings.find { it.id == mapping.id }?.let { mappings.remove(it) }
        mappings.add(mapping)
    }

    fun addOrUpdateConfigFileMapping(
        cfg: Config,
        file: WgConfigFilesImmutable?,
        path: String,
        serverResponse: String,
    ) {
        if (file == null) {
            mappings.add(
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
            val configFile = mappings.find { it.id == cfg.getId() }
            mappings.remove(configFile)
            mappings.add(file)
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
