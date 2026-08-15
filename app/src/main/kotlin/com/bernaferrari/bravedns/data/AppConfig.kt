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
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.database.DnsCryptEndpointRepository
import com.bernaferrari.bravedns.database.DnsCryptRelayEndpointRepository
import com.bernaferrari.bravedns.database.DnsLogRepository
import com.bernaferrari.bravedns.database.DnsProxyEndpointRepository
import com.bernaferrari.bravedns.database.DoHEndpointRepository
import com.bernaferrari.bravedns.database.DoTEndpointRepository
import com.bernaferrari.bravedns.database.ODoHEndpointRepository
import com.bernaferrari.bravedns.database.ProxyEndpointRepository
import com.bernaferrari.bravedns.database.RethinkDnsEndpointRepository
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import com.celzero.firestack.intra.Bridge
import com.celzero.firestack.settings.Settings

class AppConfig
internal constructor(
    private val dnsSettings: DnsSettings,
    dnsEndpointSettings: DnsEndpointSettings,
    dnsConnectionSettings: DnsConnectionSettings,
    firewallSettings: FirewallSettings,
    private val proxySettings: ProxySettings,
    tunnelSettings: TunnelSettings,
    logSettings: LogSettings,
) : DnsSettingsApi by dnsSettings,
    DnsEndpointSettingsApi by dnsEndpointSettings,
    DnsConnectionSettingsApi by dnsConnectionSettings,
    FirewallSettingsApi by firewallSettings,
    ProxySettingsApi by proxySettings,
    TunnelSettingsApi by tunnelSettings,
    LogSettingsApi by logSettings {
    internal constructor(
        context: Context,
        rethinkDnsEndpointRepository: RethinkDnsEndpointRepository,
        dnsProxyEndpointRepository: DnsProxyEndpointRepository,
        doHEndpointRepository: DoHEndpointRepository,
        dnsCryptEndpointRepository: DnsCryptEndpointRepository,
        dnsCryptRelayEndpointRepository: DnsCryptRelayEndpointRepository,
        doTEndpointRepository: DoTEndpointRepository,
        oDoHEndpointRepository: ODoHEndpointRepository,
        proxyEndpointRepository: ProxyEndpointRepository,
        persistentState: PersistentState,
        networkLogs: ConnectionTrackerRepository,
        dnsLogs: DnsLogRepository,
        eventLogger: EventLogger,
    ) : this(
        AppConfigCollaborators(
            context,
            rethinkDnsEndpointRepository,
            dnsProxyEndpointRepository,
            doHEndpointRepository,
            dnsCryptEndpointRepository,
            dnsCryptRelayEndpointRepository,
            doTEndpointRepository,
            oDoHEndpointRepository,
            proxyEndpointRepository,
            persistentState,
            networkLogs,
            dnsLogs,
            eventLogger,
        )
    )

    private constructor(
        collaborators: AppConfigCollaborators
    ) : this(
        collaborators.dnsSettings,
        collaborators.dnsEndpointSettings,
        collaborators.dnsConnectionSettings,
        collaborators.firewallSettings,
        collaborators.proxySettings,
        collaborators.tunnelSettings,
        collaborators.logSettings,
    )

    companion object {
        const val FALLBACK_DNS_IF_NET_DNS_EMPTY = "9.9.9.9,2620:fe::fe"
        const val DOH_INDEX = '1'
        const val DOT_INDEX = '2'
        const val ODOH_INDEX = '3'
        const val DNS_CRYPT_INDEX = '4'
    }

    data class TunnelOptions(
        val tunDnsMode: TunDnsMode,
        val tunFirewallMode: TunFirewallMode,
        val tunProxyMode: TunProxyMode,
        val ptMode: ProtoTranslationMode,
        val bridge: Bridge,
        val defaultDns: String,
        val fakeDns: String
    )

    enum class BraveMode(val mode: Int) {
        DNS(0),
        FIREWALL(1),
        DNS_FIREWALL(2);

        fun isFirewallActive(): Boolean = isFirewallMode() || isDnsFirewallMode()

        fun isDnsActive(): Boolean = isDnsMode() || isDnsFirewallMode()

        fun isFirewallMode(): Boolean = mode == FIREWALL.mode

        fun isDnsMode(): Boolean = mode == DNS.mode

        fun isDnsFirewallMode(): Boolean = mode == DNS_FIREWALL.mode

        companion object {
            fun getMode(id: Int): BraveMode {
                return when (id) {
                    DNS.mode -> DNS
                    FIREWALL.mode -> FIREWALL
                    DNS_FIREWALL.mode -> DNS_FIREWALL
                    else -> DNS_FIREWALL
                }
            }
        }
    }

    enum class TunFirewallMode(val mode: Int) {
        FILTER_ANDROID9_ABOVE(Settings.BlockModeFilter),
        SINK(Settings.BlockModeSink),
        FILTER_ANDROID8_BELOW(Settings.BlockModeFilterProc),
        NONE(Settings.BlockModeNone);

        fun isFirewallSinkMode(): Boolean = mode == SINK.mode
    }

    enum class DnsType(val type: Int) {
        DOH(1),
        DNSCRYPT(2),
        DNS_PROXY(3),
        RETHINK_REMOTE(4),
        SYSTEM_DNS(5),
        DOT(6),
        ODOH(7),
        SMART_DNS(8);

        fun isDnsProxy(): Boolean = this == DNS_PROXY

        fun isRethinkRemote(): Boolean = this == RETHINK_REMOTE

        fun isSystemDns(): Boolean = this == SYSTEM_DNS

        fun isSmartDns(): Boolean = this == SMART_DNS

        fun isValidDnsType(): Boolean {
            return this == DOH ||
                this == DNSCRYPT ||
                this == DNS_PROXY ||
                this == RETHINK_REMOTE ||
                this == SYSTEM_DNS ||
                this == DOT ||
                this == ODOH ||
                this == SMART_DNS
        }

        companion object {
            fun getDnsType(id: Int): DnsType {
                return when (id) {
                    DOH.type -> DOH
                    DNSCRYPT.type -> DNSCRYPT
                    DNS_PROXY.type -> DNS_PROXY
                    RETHINK_REMOTE.type -> RETHINK_REMOTE
                    SYSTEM_DNS.type -> SYSTEM_DNS
                    DOT.type -> DOT
                    ODOH.type -> ODOH
                    SMART_DNS.type -> SMART_DNS
                    else -> DOH
                }
            }
        }
    }

    enum class TunDnsMode(val mode: Long) {
        NONE(Settings.DNSModeNone.toLong()),
        DNS_IP(Settings.DNSModeIP.toLong()),
        DNS_PORT(Settings.DNSModePort.toLong())
    }

    enum class TunProxyMode {
        NONE,
        HTTPS,
        SOCKS5,
        ORBOT,
        TCP,
        WIREGUARD;

        fun isTunProxyOrbot(): Boolean = this == ORBOT

        fun isTunProxySocks5(): Boolean = this == SOCKS5

        fun isTunProxyHttps(): Boolean = this == HTTPS

        fun isTunProxyWireguard(): Boolean = this == WIREGUARD

        fun isTunProxyTcp(): Boolean = this == TCP
    }

    enum class ProxyProvider {
        NONE,
        CUSTOM,
        ORBOT,
        TCP,
        WIREGUARD;

        fun isProxyProviderCustom(): Boolean = CUSTOM.name == name

        fun isProxyProviderNone(): Boolean = NONE.name == name

        fun isProxyProviderOrbot(): Boolean = ORBOT.name == name

        fun isProxyProviderWireguard(): Boolean = WIREGUARD.name == name

        fun isProxyProviderTcp(): Boolean = TCP.name == name

        companion object {
            fun getProxyProvider(name: String): ProxyProvider {
                return when (name) {
                    WIREGUARD.name -> WIREGUARD
                    CUSTOM.name -> CUSTOM
                    ORBOT.name -> ORBOT
                    TCP.name -> TCP
                    else -> NONE
                }
            }
        }
    }

    enum class ProxyType {
        NONE,
        HTTP,
        SOCKS5,
        TCP,
        HTTP_SOCKS5,
        WIREGUARD;

        fun isProxyTypeHttp(): Boolean = HTTP.name == name

        fun isProxyTypeSocks5(): Boolean = SOCKS5.name == name

        fun isProxyTypeHttpSocks5(): Boolean = HTTP_SOCKS5.name == name

        fun isProxyTypeTcp(): Boolean = TCP.name == name

        fun isProxyTypeNone(): Boolean = NONE.name == name

        fun isProxyTypeWireguard(): Boolean = WIREGUARD.name == name

        fun isProxyTypeHasHttp(): Boolean = isProxyTypeHttp() || isProxyTypeHttpSocks5()

        fun isSocks5Enabled(): Boolean = isProxyTypeSocks5() || isProxyTypeHttpSocks5()

        fun isAnyProxyEnabled(): Boolean {
            return isProxyTypeHttp() ||
                isProxyTypeSocks5() ||
                isProxyTypeHttpSocks5() ||
                isProxyTypeTcp() ||
                isProxyTypeWireguard()
        }

        companion object {
            fun of(name: String): ProxyType {
                return when (name) {
                    HTTP.name -> HTTP
                    SOCKS5.name -> SOCKS5
                    HTTP_SOCKS5.name -> HTTP_SOCKS5
                    WIREGUARD.name -> WIREGUARD
                    TCP.name -> TCP
                    else -> NONE
                }
            }
        }
    }

    enum class ProtoTranslationMode(val id: Int) {
        PTMODEAUTO(Settings.PtModeAuto),
        PTMODEFORCE64(Settings.PtModeForce64),
        PTMODENO46(Settings.PtModeNo46)
    }

    fun stats(): String {
        return buildString {
            append("   Brave mode: ${getBraveMode()}\n")
            append("   DNS type: ${getDnsType()}\n")
            append("   Proxy type: ${proxySettings.statsTypeName()}\n")
            append("   Proxy provider: ${proxySettings.statsProviderName()}\n")
            append("   Pcap mode: ${getPcapFilePath()}\n")
            append("   Connected DNS: ${dnsSettings.connectedDnsName()}\n")
            append("   Prevent DNS leaks: ${dnsSettings.preventDnsLeaks()}\n")
            append("   Internet protocol: ${getInternetProtocol()}\n")
            append("   Protocol translation mode: ${getProtocolTranslationMode()}\n")
        }
    }
}
