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

import Logger
import Logger.LOG_TAG_PROXY
import com.bernaferrari.bravedns.data.SsidItem

/**
 * Side-effect-free SSID matching used by WireGuard hop / network eligibility.
 *
 * Empty policy lists fail open (match all). Invalid wildcard regex fails closed.
 * Historical [WireguardManager] quirks are preserved: no empty-SSID short-circuit
 * before item matching, and only [IllegalArgumentException] is caught for wildcards.
 */
internal object WgSsidMatcher {

    fun matchesWildcard(pattern: String, text: String): Boolean {
        // Convert wildcard pattern to regex
        // * matches any sequence of characters
        // ? matches any single character
        val regexPattern = pattern
            .replace(".", "\\.")  // Escape dots
            .replace("*", ".*")   // Convert * to .*
            .replace("?", ".")    // Convert ? to .

        return try {
            text.matches(Regex(regexPattern, RegexOption.IGNORE_CASE)) ||
                text.contains(pattern, ignoreCase = true)
        } catch (e: IllegalArgumentException) {
            Logger.w(LOG_TAG_PROXY, "Invalid wildcard pattern: $pattern, error: ${e.message}", e)
            false
        }
    }

    fun matchesSsidList(ssidList: String, ssid: String, emptyMatchesAll: Boolean = true): Boolean {
        val ssidItems = SsidItem.parseStorageList(ssidList)
        if (ssidItems.isEmpty()) { // treat empty as match all
            return emptyMatchesAll
        }

        val equalItems = ssidItems.filter { it.type.isEqual }
        val notEqualItems = ssidItems.filter { !it.type.isEqual }

        val notEqualMatch = notEqualItems.any { ssidItem ->
            when {
                ssidItem.type.isExact -> {
                    ssidItem.name.equals(ssid, ignoreCase = true)
                }
                else -> {
                    matchesWildcard(ssidItem.name, ssid)
                }
            }
        }

        if (notEqualMatch) {
            return false
        }

        if (equalItems.isEmpty() && notEqualItems.isNotEmpty()) {
            return true
        }

        return equalItems.any { ssidItem ->
            when {
                ssidItem.type.isExact -> {
                    ssidItem.name.equals(ssid, ignoreCase = true)
                }
                else -> {
                    matchesWildcard(ssidItem.name, ssid)
                }
            }
        }
    }
}
