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

/**
 * CSV helpers for persisted DNS tun-type lists (`"privacy,family"`).
 * Unknown tokens are ignored. Blank / empty-after-filter input yields [default].
 */
internal object RpnDnsCsv {

    fun parseTokens(csv: String): List<String> {
        if (csv.isBlank()) return emptyList()
        return csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun <T> setFromCsv(
        csv: String,
        entries: Collection<T>,
        tunType: (T) -> String,
        default: T,
    ): Set<T> {
        if (csv.isBlank()) return setOf(default)
        val result = parseTokens(csv)
            .mapNotNull { token -> entries.firstOrNull { tunType(it) == token } }
            .toSet()
        return result.ifEmpty { setOf(default) }
    }

    fun <T> tunTypesFromSet(
        modes: Set<T>,
        id: (T) -> Int,
        tunType: (T) -> String,
        default: T,
    ): String {
        val effective = modes.ifEmpty { setOf(default) }
        return effective.sortedBy(id).joinToString(",") { tunType(it) }
    }
}
