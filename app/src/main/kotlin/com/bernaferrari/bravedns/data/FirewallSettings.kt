/*
 * Copyright 2020 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.data

import com.bernaferrari.bravedns.data.AppConfig.BraveMode
import com.bernaferrari.bravedns.data.AppConfig.TunFirewallMode
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.util.Utilities.isAtleastQ
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface FirewallSettingsApi {
    fun braveModeFlow(): StateFlow<Int>
    fun changeBraveMode(braveMode: Int)
    fun getBraveMode(): BraveMode
    fun determineFirewallMode(): TunFirewallMode
}

class FirewallSettings(private val persistentState: PersistentState) : FirewallSettingsApi {
    private val braveModeObserver: MutableStateFlow<Int> =
        MutableStateFlow(BraveMode.DNS_FIREWALL.mode)

    override fun braveModeFlow(): StateFlow<Int> = braveModeObserver

    override fun changeBraveMode(braveMode: Int) {
        persistentState.braveMode = braveMode
        braveModeObserver.value = braveMode
    }

    override fun getBraveMode(): BraveMode {
        return when (persistentState.braveMode) {
            BraveMode.DNS.mode -> BraveMode.DNS
            BraveMode.FIREWALL.mode -> BraveMode.FIREWALL
            BraveMode.DNS_FIREWALL.mode -> BraveMode.DNS_FIREWALL
            else -> BraveMode.DNS_FIREWALL
        }
    }

    override fun determineFirewallMode(): TunFirewallMode {
        if (persistentState.braveMode == BraveMode.DNS.mode) {
            return TunFirewallMode.NONE
        }

        return if (isAtleastQ()) {
            TunFirewallMode.FILTER_ANDROID9_ABOVE
        } else {
            TunFirewallMode.FILTER_ANDROID8_BELOW
        }
    }
}
