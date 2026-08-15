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

/** Factory for the synthetic AUTO WIN server row. */
internal object RpnWinAutoServer {

    fun create(
        serverId: String,
        countryCode: String,
        now: Long,
    ): CountryConfig {
        return CountryConfig(
            id = serverId,
            cc = countryCode,
            name = serverId,
            address = serverId,
            city = serverId,
            key = serverId,
            load = 0,
            link = 0,
            count = 1,
            premium = true,
            isActive = true,
            isEnabled = false, // Not enabled by default
            catchAll = true,
            lockdown = false,
            mobileOnly = false,
            ssidBased = false,
            priority = 999, // Highest priority so it appears first
            ssids = "",
            lastModified = now
        )
    }
}
