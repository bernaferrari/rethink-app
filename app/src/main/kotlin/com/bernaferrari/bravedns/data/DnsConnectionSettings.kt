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

import android.content.Context
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig.DnsType
import com.bernaferrari.bravedns.database.DnsCryptEndpoint
import com.bernaferrari.bravedns.database.DnsCryptRelayEndpoint
import com.bernaferrari.bravedns.database.DnsProxyEndpoint
import com.bernaferrari.bravedns.database.DoHEndpoint
import com.bernaferrari.bravedns.database.DoTEndpoint
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.ODoHEndpoint
import com.bernaferrari.bravedns.database.RethinkDnsEndpoint
import com.bernaferrari.bravedns.service.PersistentState

interface DnsConnectionSettingsApi {
    suspend fun switchRethinkDnsToMax()
    suspend fun switchRethinkDnsToSky()
    suspend fun handleDoHChanges(doHEndpoint: DoHEndpoint)
    suspend fun handleDoTChanges(doTEndpoint: DoTEndpoint)
    suspend fun handleODoHChanges(oDoHEndpoint: ODoHEndpoint)
    suspend fun handleRethinkChanges(rethinkDnsEndpoint: RethinkDnsEndpoint)
    suspend fun handleDnsProxyChanges(dnsProxyEndpoint: DnsProxyEndpoint)
    suspend fun handleDnscryptChanges(dnsCryptEndpoint: DnsCryptEndpoint)
    suspend fun handleDnsrelayChanges(endpoint: DnsCryptRelayEndpoint)
    suspend fun removeDnscryptRelay(stamp: String)
    suspend fun enableRethinkDnsPlus()
    suspend fun enableSystemDns()
    suspend fun enableSmartDns()
}

class DnsConnectionSettings(
    private val context: Context,
    private val persistentState: PersistentState,
    private val dnsSettings: DnsSettings,
    private val dnsEndpointSettings: DnsEndpointSettings,
    private val logSettings: LogSettings,
) : DnsConnectionSettingsApi {
    suspend fun onDnsChange(dt: DnsType) {
        if (!dnsSettings.isValidDnsType(dt)) return

        dnsSettings.setDnsType(dt)
        when (dt) {
            DnsType.DOH -> {
                val endpoint = dnsEndpointSettings.getDOHDetails() ?: return
                dnsSettings.postConnectedDnsName(endpoint.dohName, endpoint.dohURL)
            }
            DnsType.DOT -> {
                val endpoint = dnsEndpointSettings.getDOTDetails() ?: return
                dnsSettings.postConnectedDnsName(endpoint.name, endpoint.url)
            }
            DnsType.ODOH -> {
                val endpoint = dnsEndpointSettings.getODoHDetails() ?: return
                dnsSettings.postConnectedDnsName(endpoint.name, endpoint.resolver)
            }
            DnsType.DNSCRYPT -> {
                val endpoint = dnsEndpointSettings.getConnectedDnscryptServer() ?: return
                dnsSettings.postConnectedDnsName(endpoint.dnsCryptName, endpoint.dnsCryptURL)
            }
            DnsType.DNS_PROXY -> {
                val endpoint = dnsEndpointSettings.getSelectedDnsProxyDetails() ?: return
                val ip = endpoint.proxyIP?.split(",")?.firstOrNull() ?: ""
                val url = ip + ":" + endpoint.proxyPort
                dnsSettings.postConnectedDnsName(endpoint.proxyName, url)
            }
            DnsType.RETHINK_REMOTE -> {
                val endpoint = dnsEndpointSettings.getRemoteRethinkEndpoint() ?: return
                persistentState.setRemoteBlocklistCount(endpoint.blocklistCount)
                dnsSettings.postConnectedDnsName(endpoint.name, endpoint.url)
            }
            DnsType.SYSTEM_DNS -> {
                dnsSettings.postConnectedDnsName(context.getString(R.string.network_dns))
            }
            DnsType.SMART_DNS -> {
                dnsSettings.postConnectedDnsName(context.getString(R.string.smart_dns))
            }
        }
    }

    override suspend fun switchRethinkDnsToMax() {
        dnsEndpointSettings.switchToMax()
        if (dnsSettings.isRethinkDnsConnected()) {
            onDnsChange(DnsType.RETHINK_REMOTE)
        }
        logSettings.logEvent(
            EventType.DNS_SERVER_CHANGE,
            "RethinkDNS switched to MAX",
            "RethinkDNS endpoint switched to MAX, prev: ${persistentState.connectedDnsName}"
        )
    }

    override suspend fun switchRethinkDnsToSky() {
        dnsEndpointSettings.switchToSky()
        if (dnsSettings.isRethinkDnsConnected()) {
            onDnsChange(DnsType.RETHINK_REMOTE)
        }
        logSettings.logEvent(
            EventType.DNS_SERVER_CHANGE,
            "RethinkDNS switched to SKY",
            "RethinkDNS endpoint switched to SKY, prev: ${persistentState.connectedDnsName}"
        )
    }

    override suspend fun handleDoHChanges(doHEndpoint: DoHEndpoint) {
        if (dnsSettings.getDnsType() != DnsType.DOH) {
            dnsEndpointSettings.removeConnectionStatus()
        }

        dnsEndpointSettings.updateDoH(doHEndpoint)
        onDnsChange(DnsType.DOH)
        logSettings.logEvent(
            EventType.DNS_SERVER_CHANGE,
            "DoH endpoint changed",
            "DoH endpoint changed to ${doHEndpoint.dohName}, prev: ${persistentState.connectedDnsName}"
        )
    }

    override suspend fun handleDoTChanges(doTEndpoint: DoTEndpoint) {
        if (dnsSettings.getDnsType() != DnsType.DOT) {
            dnsEndpointSettings.removeConnectionStatus()
        }

        val prev = persistentState.connectedDnsName
        dnsEndpointSettings.updateDoT(doTEndpoint)
        onDnsChange(DnsType.DOT)
        logSettings.logEvent(
            EventType.DNS_SERVER_CHANGE,
            "DoT endpoint changed",
            "DoT endpoint changed to ${doTEndpoint.name}, prev: $prev"
        )
    }

    override suspend fun handleODoHChanges(oDoHEndpoint: ODoHEndpoint) {
        if (dnsSettings.getDnsType() != DnsType.ODOH) {
            dnsEndpointSettings.removeConnectionStatus()
        }

        val prev = persistentState.connectedDnsName
        dnsEndpointSettings.updateODoH(oDoHEndpoint)
        onDnsChange(DnsType.ODOH)
        logSettings.logEvent(
            EventType.DNS_SERVER_CHANGE,
            "ODoH endpoint changed",
            "ODoH endpoint changed to ${oDoHEndpoint.name}, prev: $prev"
        )
    }

    override suspend fun handleRethinkChanges(rethinkDnsEndpoint: RethinkDnsEndpoint) {
        if (dnsSettings.getDnsType() != DnsType.RETHINK_REMOTE) {
            dnsEndpointSettings.removeConnectionStatus()
        }

        val prev = persistentState.connectedDnsName
        dnsEndpointSettings.updateRethink(rethinkDnsEndpoint)
        onDnsChange(DnsType.RETHINK_REMOTE)
        logSettings.logEvent(
            EventType.DNS_SERVER_CHANGE,
            "RethinkDNS endpoint changed",
            "RethinkDNS endpoint changed to ${rethinkDnsEndpoint.name}, prev: $prev"
        )
    }

    override suspend fun handleDnsProxyChanges(dnsProxyEndpoint: DnsProxyEndpoint) {
        if (dnsSettings.getDnsType() != DnsType.DNS_PROXY) {
            dnsEndpointSettings.removeConnectionStatus()
        }

        val prev = persistentState.connectedDnsName
        dnsEndpointSettings.updateDnsProxy(dnsProxyEndpoint)
        onDnsChange(DnsType.DNS_PROXY)
        logSettings.logEvent(
            EventType.DNS_SERVER_CHANGE,
            "DNS Proxy endpoint changed",
            "DNS Proxy endpoint changed to ${dnsProxyEndpoint.proxyName}, prev: $prev"
        )
    }

    override suspend fun handleDnscryptChanges(dnsCryptEndpoint: DnsCryptEndpoint) {
        if (dnsSettings.getDnsType() != DnsType.DNSCRYPT) {
            dnsEndpointSettings.removeConnectionStatus()
        }

        val prev = persistentState.connectedDnsName
        dnsEndpointSettings.updateDnscrypt(dnsCryptEndpoint)
        onDnsChange(DnsType.DNSCRYPT)
        logSettings.logEvent(
            EventType.DNS_SERVER_CHANGE,
            "DNSCrypt endpoint changed",
            "DNSCrypt endpoint changed to ${dnsCryptEndpoint.dnsCryptName}, prev: $prev"
        )
    }

    override suspend fun handleDnsrelayChanges(endpoint: DnsCryptRelayEndpoint) {
        dnsEndpointSettings.updateDnsCryptRelay(endpoint)
        persistentState.dnsCryptRelays.value =
            PersistentState.DnsCryptRelayDetails(endpoint, endpoint.isSelected)

        logSettings.logEvent(
            EventType.DNS_SERVER_CHANGE,
            "DNSCrypt Relay endpoint changed",
            "DNSCrypt Relay endpoint changed to ${endpoint.dnsCryptRelayURL}, isSelected: ${endpoint.isSelected}"
        )
    }

    override suspend fun removeDnscryptRelay(stamp: String) {
        dnsEndpointSettings.unselectDnscryptRelay(stamp)
    }

    override suspend fun enableRethinkDnsPlus() {
        if (dnsSettings.getDnsType() != DnsType.RETHINK_REMOTE) {
            dnsEndpointSettings.removeConnectionStatus()
        }

        val prev = persistentState.connectedDnsName
        dnsEndpointSettings.setRethinkPlus()
        onDnsChange(DnsType.RETHINK_REMOTE)
        logSettings.logEvent(
            EventType.DNS_SERVER_CHANGE,
            "RethinkDNS Plus enabled",
            "RethinkDNS Plus enabled, prev: $prev"
        )
    }

    override suspend fun enableSystemDns() {
        if (dnsSettings.getDnsType() != DnsType.SYSTEM_DNS) {
            dnsEndpointSettings.removeConnectionStatus()
        }

        val prev = persistentState.connectedDnsName
        onDnsChange(DnsType.SYSTEM_DNS)
        logSettings.logEvent(
            EventType.DNS_SERVER_CHANGE,
            "System DNS enabled",
            "System DNS enabled, prev: $prev"
        )
    }

    override suspend fun enableSmartDns() {
        if (dnsSettings.getDnsType() != DnsType.SMART_DNS) {
            dnsEndpointSettings.removeConnectionStatus()
        }

        val prev = persistentState.connectedDnsName
        onDnsChange(DnsType.SMART_DNS)
        logSettings.logEvent(
            EventType.DNS_SERVER_CHANGE,
            "Smart DNS enabled",
            "Smart DNS enabled, prev: $prev"
        )
    }
}
