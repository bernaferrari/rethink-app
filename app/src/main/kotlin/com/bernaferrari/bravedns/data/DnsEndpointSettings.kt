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

import com.bernaferrari.bravedns.data.AppConfig.DnsType
import com.bernaferrari.bravedns.database.DnsCryptEndpoint
import com.bernaferrari.bravedns.database.DnsCryptEndpointRepository
import com.bernaferrari.bravedns.database.DnsCryptRelayEndpoint
import com.bernaferrari.bravedns.database.DnsCryptRelayEndpointRepository
import com.bernaferrari.bravedns.database.DnsProxyEndpoint
import com.bernaferrari.bravedns.database.DnsProxyEndpointRepository
import com.bernaferrari.bravedns.database.DoHEndpoint
import com.bernaferrari.bravedns.database.DoHEndpointRepository
import com.bernaferrari.bravedns.database.DoTEndpoint
import com.bernaferrari.bravedns.database.DoTEndpointRepository
import com.bernaferrari.bravedns.database.ODoHEndpoint
import com.bernaferrari.bravedns.database.ODoHEndpointRepository
import com.bernaferrari.bravedns.database.RethinkDnsEndpoint
import com.bernaferrari.bravedns.database.RethinkDnsEndpointRepository
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Constants.Companion.MAX_ENDPOINT

interface DnsEndpointSettingsApi {
    suspend fun getDOHDetails(): DoHEndpoint?
    suspend fun getAllDefaultDoHEndpoints(): List<DoHEndpoint>
    suspend fun getAllDefaultDoTEndpoints(): List<DoTEndpoint>
    suspend fun getDOTDetails(): DoTEndpoint?
    suspend fun getODoHDetails(): ODoHEndpoint?
    suspend fun getSelectedDnsProxyDetails(): DnsProxyEndpoint?
    suspend fun getConnectedDnscryptServer(): DnsCryptEndpoint?
    suspend fun getDnscryptRelayServers(): String
    suspend fun getDohCount(): Int
    suspend fun getDoTCount(): Int
    suspend fun getODoHCount(): Int
    suspend fun getDnsProxyCount(): Int
    suspend fun getDnscryptCount(): Int
    suspend fun getDnscryptRelayCount(): Int
    suspend fun isOrbotDns(): Boolean
    suspend fun getOrbotDnsProxyEndpoint(): DnsProxyEndpoint?
    suspend fun getRemoteRethinkEndpoint(): RethinkDnsEndpoint?
    suspend fun getRethinkDefaultEndpoint(): RethinkDnsEndpoint?
    suspend fun getBlockFreeRethinkEndpoint(): String
    suspend fun getRethinkPlusEndpoint(): RethinkDnsEndpoint?
    suspend fun isDnscryptRelaySelectable(): Boolean
    suspend fun isAppWiseDnsEnabled(uid: Int): Boolean
    suspend fun removeAppWiseDns(uid: Int)
    suspend fun insertDnscryptRelayEndpoint(endpoint: DnsCryptRelayEndpoint)
    suspend fun insertDnscryptEndpoint(endpoint: DnsCryptEndpoint)
    suspend fun insertDohEndpoint(endpoint: DoHEndpoint)
    suspend fun insertDoTEndpoint(endpoint: DoTEndpoint)
    suspend fun insertODoHEndpoint(endpoint: ODoHEndpoint)
    suspend fun insertReplaceEndpoint(endpoint: RethinkDnsEndpoint)
    suspend fun updateRethinkEndpoint(name: String, url: String, count: Int)
    suspend fun insertDnsproxyEndpoint(endpoint: DnsProxyEndpoint)
    suspend fun deleteDohEndpoint(id: Int)
    suspend fun deleteDoTEndpoint(id: Int)
    suspend fun deleteODoHEndpoint(id: Int)
    suspend fun deleteDnsProxyEndpoint(id: Int)
    suspend fun deleteDnscryptEndpoint(id: Int)
    suspend fun deleteDnscryptRelayEndpoint(id: Int)
}

class DnsEndpointSettings(
    private val rethinkDnsEndpointRepository: RethinkDnsEndpointRepository,
    private val dnsProxyEndpointRepository: DnsProxyEndpointRepository,
    private val doHEndpointRepository: DoHEndpointRepository,
    private val dnsCryptEndpointRepository: DnsCryptEndpointRepository,
    private val dnsCryptRelayEndpointRepository: DnsCryptRelayEndpointRepository,
    private val doTEndpointRepository: DoTEndpointRepository,
    private val oDoHEndpointRepository: ODoHEndpointRepository,
    private val dnsSettings: DnsSettings,
) : DnsEndpointSettingsApi {
    override suspend fun getDOHDetails(): DoHEndpoint? = doHEndpointRepository.getConnectedDoH()

    override suspend fun getAllDefaultDoHEndpoints(): List<DoHEndpoint> =
        doHEndpointRepository.getAllDefaultDoHEndpoints()

    override suspend fun getAllDefaultDoTEndpoints(): List<DoTEndpoint> =
        doTEndpointRepository.getAllDefaultDoTEndpoints()

    override suspend fun getDOTDetails(): DoTEndpoint? = doTEndpointRepository.getConnectedDoT()

    override suspend fun getODoHDetails(): ODoHEndpoint? = oDoHEndpointRepository.getConnectedODoH()

    override suspend fun getSelectedDnsProxyDetails(): DnsProxyEndpoint? =
        dnsProxyEndpointRepository.getSelectedProxy()

    override suspend fun getConnectedDnscryptServer(): DnsCryptEndpoint? =
        dnsCryptEndpointRepository.getConnectedDNSCrypt()

    override suspend fun getDnscryptRelayServers(): String = dnsCryptRelayEndpointRepository.getServersToAdd()

    override suspend fun getDohCount(): Int = doHEndpointRepository.getCount()

    override suspend fun getDoTCount(): Int = doTEndpointRepository.getCount()

    override suspend fun getODoHCount(): Int = oDoHEndpointRepository.getCount()

    override suspend fun getDnsProxyCount(): Int = dnsProxyEndpointRepository.getCount()

    override suspend fun getDnscryptCount(): Int = dnsCryptEndpointRepository.getCount()

    override suspend fun getDnscryptRelayCount(): Int = dnsCryptRelayEndpointRepository.getCount()

    override suspend fun isOrbotDns(): Boolean {
        if (!dnsSettings.getDnsType().isDnsProxy()) return false
        return dnsProxyEndpointRepository.getSelectedProxy()?.proxyName == ORBOT_DNS
    }

    override suspend fun getOrbotDnsProxyEndpoint(): DnsProxyEndpoint? =
        dnsProxyEndpointRepository.getOrbotDnsEndpoint()

    override suspend fun getRemoteRethinkEndpoint(): RethinkDnsEndpoint? =
        rethinkDnsEndpointRepository.getConnectedEndpoint()

    override suspend fun getRethinkDefaultEndpoint(): RethinkDnsEndpoint? =
        rethinkDnsEndpointRepository.getDefaultRethinkEndpoint()

    override suspend fun getBlockFreeRethinkEndpoint(): String {
        return if (getRemoteRethinkEndpoint()?.url?.contains(MAX_ENDPOINT) == true) {
            Constants.BLOCK_FREE_DNS_MAX
        } else {
            Constants.BLOCK_FREE_DNS_SKY
        }
    }

    override suspend fun getRethinkPlusEndpoint(): RethinkDnsEndpoint? =
        rethinkDnsEndpointRepository.getRethinkPlusEndpoint()

    override suspend fun isDnscryptRelaySelectable(): Boolean =
        dnsCryptEndpointRepository.getConnectedCount() >= 1

    override suspend fun isAppWiseDnsEnabled(uid: Int): Boolean =
        rethinkDnsEndpointRepository.isAppWiseDnsEnabled(uid)

    override suspend fun removeAppWiseDns(uid: Int) {
        rethinkDnsEndpointRepository.removeAppWiseDns(uid)
    }

    override suspend fun insertDnscryptRelayEndpoint(endpoint: DnsCryptRelayEndpoint) {
        dnsCryptRelayEndpointRepository.insertAsync(endpoint)
    }

    override suspend fun insertDnscryptEndpoint(endpoint: DnsCryptEndpoint) {
        dnsCryptEndpointRepository.insertAsync(endpoint)
    }

    override suspend fun insertDohEndpoint(endpoint: DoHEndpoint) {
        doHEndpointRepository.insertAsync(endpoint)
    }

    override suspend fun insertDoTEndpoint(endpoint: DoTEndpoint) {
        doTEndpointRepository.insertAsync(endpoint)
    }

    override suspend fun insertODoHEndpoint(endpoint: ODoHEndpoint) {
        oDoHEndpointRepository.insertAsync(endpoint)
    }

    override suspend fun insertReplaceEndpoint(endpoint: RethinkDnsEndpoint) {
        rethinkDnsEndpointRepository.insertWithReplace(endpoint)
    }

    override suspend fun updateRethinkEndpoint(name: String, url: String, count: Int) {
        rethinkDnsEndpointRepository.updateEndpoint(name, url, count)
    }

    override suspend fun insertDnsproxyEndpoint(endpoint: DnsProxyEndpoint) {
        dnsProxyEndpointRepository.insertAsync(endpoint)
    }

    override suspend fun deleteDohEndpoint(id: Int) {
        doHEndpointRepository.deleteDoHEndpoint(id)
    }

    override suspend fun deleteDoTEndpoint(id: Int) {
        doTEndpointRepository.deleteDoTEndpoint(id)
    }

    override suspend fun deleteODoHEndpoint(id: Int) {
        oDoHEndpointRepository.deleteODoHEndpoint(id)
    }

    override suspend fun deleteDnsProxyEndpoint(id: Int) {
        dnsProxyEndpointRepository.deleteDnsProxyEndpoint(id)
    }

    override suspend fun deleteDnscryptEndpoint(id: Int) {
        dnsCryptEndpointRepository.deleteDNSCryptEndpoint(id)
    }

    override suspend fun deleteDnscryptRelayEndpoint(id: Int) {
        dnsCryptRelayEndpointRepository.deleteDnsCryptRelayEndpoint(id)
    }

    suspend fun switchToMax() {
        rethinkDnsEndpointRepository.switchToMax()
    }

    suspend fun switchToSky() {
        rethinkDnsEndpointRepository.switchToSky()
    }

    suspend fun setRethinkPlus() {
        rethinkDnsEndpointRepository.setRethinkPlus()
    }

    suspend fun updateDoH(doHEndpoint: DoHEndpoint) {
        doHEndpointRepository.update(doHEndpoint)
    }

    suspend fun updateDoT(doTEndpoint: DoTEndpoint) {
        doTEndpointRepository.update(doTEndpoint)
    }

    suspend fun updateODoH(oDoHEndpoint: ODoHEndpoint) {
        oDoHEndpointRepository.update(oDoHEndpoint)
    }

    suspend fun updateRethink(rethinkDnsEndpoint: RethinkDnsEndpoint) {
        rethinkDnsEndpointRepository.update(rethinkDnsEndpoint)
    }

    suspend fun updateDnsProxy(dnsProxyEndpoint: DnsProxyEndpoint) {
        dnsProxyEndpointRepository.update(dnsProxyEndpoint)
    }

    suspend fun updateDnscrypt(dnsCryptEndpoint: DnsCryptEndpoint) {
        dnsCryptEndpointRepository.update(dnsCryptEndpoint)
    }

    suspend fun updateDnsCryptRelay(endpoint: DnsCryptRelayEndpoint) {
        dnsCryptRelayEndpointRepository.update(endpoint)
    }

    suspend fun unselectDnscryptRelay(stamp: String) {
        dnsCryptRelayEndpointRepository.unselectRelay(stamp)
    }

    suspend fun removeConnectionStatus() {
        when (dnsSettings.getDnsType()) {
            DnsType.DOH -> doHEndpointRepository.removeConnectionStatus()
            DnsType.DOT -> doTEndpointRepository.removeConnectionStatus()
            DnsType.ODOH -> oDoHEndpointRepository.removeConnectionStatus()
            DnsType.DNSCRYPT -> {
                dnsCryptEndpointRepository.removeConnectionStatus()
                dnsCryptRelayEndpointRepository.removeConnectionStatus()
            }
            DnsType.DNS_PROXY -> dnsProxyEndpointRepository.removeConnectionStatus()
            DnsType.RETHINK_REMOTE -> rethinkDnsEndpointRepository.removeConnectionStatus()
            DnsType.SYSTEM_DNS -> Unit
            DnsType.SMART_DNS -> Unit
        }
    }

    companion object {
        private const val ORBOT_DNS = "Orbot"
    }
}
