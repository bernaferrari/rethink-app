/*
 * Copyright 2021 RethinkDNS and its authors
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
package com.celzero.bravedns.util

import com.celzero.bravedns.util.Utilities.isAtleastS

// Application themes enum
enum class Themes(val id: Int) {
    SYSTEM_DEFAULT(0),
    LIGHT(1),
    DARK(2),
    TRUE_BLACK(3),
    LIGHT_PLUS(4),
    DARK_PLUS(5),
    DARK_FROST(6);

    companion object {
        fun getThemeCount(): Int {
            return entries.count()
        }

        fun getAvailableThemeCount(): Int {
            return if (isAtleastS()) {
                entries.count()
            } else {
                // Exclude DARK_FROST for pre-Android S devices
                entries.count() - 1
            }
        }

        fun isFrostTheme(id: Int): Boolean {
            return id == DARK_FROST.id
        }

        fun isThemeAvailable(id: Int): Boolean {
            if (isFrostTheme(id)) {
                return isAtleastS()
            }
            return true
        }

        fun resolveThemePreference(isDarkThemeOn: Boolean, preference: Int): Int {
            if (isFrostTheme(preference) && !isAtleastS()) {
                return DARK_PLUS.id
            }

            return when (preference) {
                SYSTEM_DEFAULT.id -> if (isDarkThemeOn) DARK_PLUS.id else LIGHT_PLUS.id
                LIGHT.id,
                DARK.id,
                TRUE_BLACK.id,
                LIGHT_PLUS.id,
                DARK_PLUS.id,
                DARK_FROST.id -> preference
                else -> if (isDarkThemeOn) DARK_PLUS.id else LIGHT_PLUS.id
            }
        }

        fun useDynamicColor(preference: Int): Boolean {
            return preference == SYSTEM_DEFAULT.id
        }
    }
}
