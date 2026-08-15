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
import com.bernaferrari.bravedns.data.AppConfig.ProtoTranslationMode
import com.bernaferrari.bravedns.data.AppConfig.TunnelOptions
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.util.InternetProtocol
import com.bernaferrari.bravedns.util.InternetProtocol.Companion.getInternetProtocol
import com.bernaferrari.bravedns.util.PcapMode
import com.celzero.firestack.intra.Bridge
import java.io.File

interface TunnelSettingsApi {
    fun getInternetProtocol(): InternetProtocol
    fun getProtocolTranslationMode(): ProtoTranslationMode
    fun setPcap(mode: Int, path: String = PcapMode.DISABLE_PCAP)
    fun getPcapFilePath(): String
    fun newTunnelOptions(
        bridge: Bridge,
        fakeDns: String,
        ptMode: ProtoTranslationMode
    ): TunnelOptions
}

class TunnelSettings(
    private val persistentState: PersistentState,
    private val dnsSettings: DnsSettings,
    private val firewallSettings: FirewallSettings,
    private val proxySettings: ProxySettings,
) : TunnelSettingsApi {
    private var pcapFilePath: String = ""

    init {
        pcapFilePath = persistentState.pcapFilePath

        if (PcapMode.getPcapType(persistentState.pcapMode) == PcapMode.EXTERNAL_FILE) {
            if (pcapFilePath.isEmpty() || !File(pcapFilePath).exists()) {
                Logger.w(LOG_TAG_VPN, "Pcap file path invalid or missing, resetting pcap mode to NONE")
                persistentState.pcapMode = PcapMode.NONE.id
                persistentState.pcapFilePath = ""
                pcapFilePath = ""
            }
        }
    }

    override fun getInternetProtocol(): InternetProtocol {
        return getInternetProtocol(persistentState.internetProtocolType)
    }

    override fun getProtocolTranslationMode(): ProtoTranslationMode {
        if (persistentState.protocolTranslationType && getInternetProtocol().isIPv6()) {
            return ProtoTranslationMode.PTMODEFORCE64
        }

        return ProtoTranslationMode.PTMODEAUTO
    }

    override fun setPcap(mode: Int, path: String) {
        pcapFilePath =
            when (PcapMode.getPcapType(mode)) {
                PcapMode.NONE -> {
                    ""
                }
                PcapMode.LOGCAT -> {
                    "0"
                }
                PcapMode.EXTERNAL_FILE -> {
                    path
                }
            }
        persistentState.pcapMode = mode
        persistentState.pcapFilePath = pcapFilePath
    }

    override fun getPcapFilePath(): String = pcapFilePath

    override fun newTunnelOptions(
        bridge: Bridge,
        fakeDns: String,
        ptMode: ProtoTranslationMode
    ): TunnelOptions {
        return TunnelOptions(
            dnsSettings.getDnsMode(),
            firewallSettings.determineFirewallMode(),
            proxySettings.getTunProxyMode(),
            ptMode,
            bridge,
            dnsSettings.getDefaultDns(),
            fakeDns
        )
    }
}
