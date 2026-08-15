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
import com.bernaferrari.bravedns.data.SsidItem

/**
 * Side-effect-free SSID matching used by RPN hop selection.
 *
 * Empty policy lists fail open (match all). An empty current SSID never matches.
 * Unparseable storage strings also fail open, matching the historical manager.
 */
internal class RpnSsidController(private val tag: String = "RpnMgr") {

    fun matchesWildcard(pattern: String, text: String): Boolean {
        if (pattern.isEmpty() || text.isEmpty()) {
            Logger.v(LOG_TAG_PROXY, "$tag; matchesWildcard: empty pattern or text")
            return false
        }

        // Convert wildcard pattern to regex
        // * matches any sequence of characters
        // ? matches any single character
        return try {
            val regexPattern = pattern
                .replace(".", "\\.")  // Escape dots
                .replace("*", ".*")   // Convert * to .*
                .replace("?", ".")    // Convert ? to .

            val matches = text.matches(Regex(regexPattern, RegexOption.IGNORE_CASE))
            val contains = text.contains(pattern, ignoreCase = true)

            matches || contains
        } catch (e: Exception) {
            Logger.w(LOG_TAG_PROXY, "$tag; matchesWildcard: invalid wildcard pattern: $pattern, error: ${e.message}")
            // Fallback to simple contains check
            try {
                text.contains(pattern, ignoreCase = true)
            } catch (e2: Exception) {
                Logger.e(LOG_TAG_PROXY, "$tag; matchesWildcard: fallback also failed: ${e2.message}", e2)
                false
            }
        }
    }

    fun matchesSsidList(ssidList: String, ssid: String): Boolean {
        if (ssidList.isEmpty()) {
            // Empty list means match all
            Logger.v(LOG_TAG_PROXY, "$tag; matchesSsidList: empty ssidList, match all")
            return true
        }

        if (ssid.isEmpty()) {
            Logger.v(LOG_TAG_PROXY, "$tag; matchesSsidList: empty ssid, no match")
            return false
        }

        val ssidItems = try {
            SsidItem.parseStorageList(ssidList)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; matchesSsidList: failed to parse ssidList: ${e.message}", e)
            return true // Fail open
        }

        if (ssidItems.isEmpty()) { // treat empty as match all
            return true
        }

        // Separate EQUAL items from NOT_EQUAL items
        val equalItems = ssidItems.filter { it.type.isEqual }
        val notEqualItems = ssidItems.filter { !it.type.isEqual }

        // Check NOT_EQUAL items first - if any match, return false
        val notEqualMatch = notEqualItems.any { ssidItem ->
            try {
                when {
                    ssidItem.type.isExact -> {
                        ssidItem.name.equals(ssid, ignoreCase = true)
                    }
                    else -> { // wildcard
                        matchesWildcard(ssidItem.name, ssid)
                    }
                }
            } catch (e: Exception) {
                Logger.w(LOG_TAG_PROXY, "$tag; matchesSsidList: error matching NOT_EQUAL item: ${e.message}")
                false
            }
        }

        if (notEqualMatch) {
            return false
        }

        // If there are only NOT_EQUAL items and none matched, return true
        if (equalItems.isEmpty() && notEqualItems.isNotEmpty()) {
            return true
        }

        // Check EQUAL items (exact or wildcard)
        return equalItems.any { ssidItem ->
            try {
                when {
                    ssidItem.type.isExact -> {
                        ssidItem.name.equals(ssid, ignoreCase = true)
                    }
                    else -> { // wildcard
                        matchesWildcard(ssidItem.name, ssid)
                    }
                }
            } catch (e: Exception) {
                Logger.w(LOG_TAG_PROXY, "$tag; matchesSsidList: error matching EQUAL item: ${e.message}")
                false
            }
        }
    }
}
