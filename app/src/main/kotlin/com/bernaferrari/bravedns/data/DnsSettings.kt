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

import Logger
import Logger.LOG_TAG_VPN
import com.bernaferrari.bravedns.data.AppConfig.DnsType
import com.bernaferrari.bravedns.data.AppConfig.BraveMode
import com.bernaferrari.bravedns.data.AppConfig.TunDnsMode
import com.bernaferrari.bravedns.service.PersistentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface DnsSettingsApi {
    fun getDnsType(): DnsType
    fun preventDnsLeaks(): Boolean
    fun isDnsProxyActive(): Boolean
    fun connectedDnsFlow(): StateFlow<String>
    fun getDefaultDns(): String
    fun isRethinkDnsConnected(): Boolean
    fun isSystemDns(): Boolean
    fun isSmartDnsEnabled(): Boolean
}

class DnsSettings(private val persistentState: PersistentState) : DnsSettingsApi {
    private val connectedDns: MutableStateFlow<String> = MutableStateFlow("")

    init {
        val dnsName = persistentState.connectedDnsName.split(",").firstOrNull() ?: ""
        connectedDns.value = dnsName
    }

    override fun connectedDnsFlow(): StateFlow<String> = connectedDns

    fun connectedDnsName(): String = persistentState.connectedDnsName

    fun postConnectedDnsName(name: String, url: String = "") {
        connectedDns.value = name
        persistentState.connectedDnsName =
            if (url.isEmpty()) name else "$name,$url"
    }

    override fun getDnsType(): DnsType {
        return when (persistentState.dnsType) {
            DnsType.DOH.type -> DnsType.DOH
            DnsType.DNSCRYPT.type -> DnsType.DNSCRYPT
            DnsType.DNS_PROXY.type -> DnsType.DNS_PROXY
            DnsType.RETHINK_REMOTE.type -> DnsType.RETHINK_REMOTE
            DnsType.SYSTEM_DNS.type -> DnsType.SYSTEM_DNS
            DnsType.DOT.type -> DnsType.DOT
            DnsType.ODOH.type -> DnsType.ODOH
            DnsType.SMART_DNS.type -> DnsType.SMART_DNS
            else -> {
                Logger.w(LOG_TAG_VPN, "Invalid dns type mode: ${persistentState.dnsType}")
                DnsType.DOH
            }
        }
    }

    fun setDnsType(dt: DnsType) {
        persistentState.dnsType = dt.type
    }

    fun getDnsMode(): TunDnsMode {
        return when (persistentState.braveMode) {
            BraveMode.FIREWALL.mode -> TunDnsMode.NONE
            BraveMode.DNS.mode,
            BraveMode.DNS_FIREWALL.mode -> determineTunDnsMode()
            else -> {
                Logger.crash(
                    LOG_TAG_VPN,
                    "invalid brave mode: ${persistentState.braveMode}",
                    Exception()
                )
                TunDnsMode.NONE
            }
        }
    }

    fun determineTunDnsMode(): TunDnsMode {
        return if (DnsType.getDnsType(persistentState.dnsType).isValidDnsType()) {
            if (persistentState.preventDnsLeaks) {
                TunDnsMode.DNS_PORT
            } else {
                TunDnsMode.DNS_IP
            }
        } else {
            TunDnsMode.NONE
        }
    }

    override fun preventDnsLeaks(): Boolean = persistentState.preventDnsLeaks

    override fun isDnsProxyActive(): Boolean = DnsType.DNS_PROXY == getDnsType()

    override fun getDefaultDns(): String = persistentState.defaultDnsUrl

    override fun isRethinkDnsConnected(): Boolean = getDnsType() == DnsType.RETHINK_REMOTE

    override fun isSystemDns(): Boolean = getDnsType().isSystemDns()

    override fun isSmartDnsEnabled(): Boolean = getDnsType().isSmartDns()

    fun isValidDnsType(dt: DnsType): Boolean {
        return (dt == DnsType.DOH ||
            dt == DnsType.DNSCRYPT ||
            dt == DnsType.DNS_PROXY ||
            dt == DnsType.RETHINK_REMOTE ||
            dt == DnsType.SYSTEM_DNS ||
            dt == DnsType.DOT ||
            dt == DnsType.ODOH ||
            dt == DnsType.SMART_DNS)
    }
}
