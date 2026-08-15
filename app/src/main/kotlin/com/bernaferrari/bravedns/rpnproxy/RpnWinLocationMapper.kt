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

import com.bernaferrari.bravedns.database.CountryConfig

/**
 * Pure country/SID helpers for WIN location sync.
 *
 * Server IDs are `"$cc-$name-$key"`. Removed-selected detection is the set
 * difference of existing vs new IDs, restricted to keys currently in the tunnel.
 */
internal object RpnWinLocationMapper {

    fun serverId(cc: String?, name: String?, key: String?): String = "$cc-$name-$key"

    fun hasEssentialFields(cc: String?, name: String?, key: String?): Boolean {
        return !cc.isNullOrEmpty() && !name.isNullOrEmpty() && !key.isNullOrEmpty()
    }

    fun removedIds(existingIds: Set<String>, newIds: Set<String>): Set<String> = existingIds - newIds

    data class RemovedSelected(
        val selectedIds: List<String>,
        val keysToRemove: Set<String>,
    )

    fun removedSelected(
        existing: List<CountryConfig>,
        removedIds: Set<String>,
        isInTunnel: (key: String) -> Boolean,
    ): RemovedSelected {
        if (removedIds.isEmpty()) return RemovedSelected(emptyList(), emptySet())
        val selectedIds = mutableListOf<String>()
        val keysToRemove = mutableSetOf<String>()
        for (removedId in removedIds) {
            val removed = existing.firstOrNull { it.id == removedId }
            if (removed != null && isInTunnel(removed.key)) {
                selectedIds.add(removedId)
                keysToRemove.add(removed.key)
            }
        }
        return RemovedSelected(selectedIds, keysToRemove)
    }

    fun staleServers(
        serversBefore: List<CountryConfig>,
        newServerKeys: Set<String>,
        autoServerId: String,
    ): List<CountryConfig> {
        return serversBefore.filter {
            it.id != autoServerId && !newServerKeys.contains(it.key)
        }
    }
}
