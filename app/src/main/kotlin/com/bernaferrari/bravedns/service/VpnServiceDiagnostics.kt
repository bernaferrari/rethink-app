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
package com.bernaferrari.bravedns.service

import android.content.Context
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.util.MemoryUtils
import com.bernaferrari.bravedns.util.Utilities.isAtleastQ
import inet.ipaddr.IPAddressString

/**
 * VPN / builder / rules diagnostic dumps for [BraveVPNService].
 */
class VpnServiceDiagnostics(
    private val context: Context,
    private val persistentState: PersistentState,
    private val appConfig: AppConfig,
) {
    suspend fun vpnStats(builderSection: String): String {
        val stats = StringBuilder()
        stats.append("VPN Stats:\n")
        stats.append("Builder:\n${builderSection}\n")
        stats.append("General:\n${appConfig.stats()}\n")
        stats.append("Firewall:\n${FirewallManager.stats()}\n")
        stats.append("IpRules:\n${IpRulesManager.stats()}\n")
        stats.append("DomainRules:\n${DomainRulesManager.stats()}\n")
        stats.append("Proxy:\n${ProxyManager.stats()}\n")
        stats.append("WireGuard:\n${WireguardManager.stats()}\n")
        stats.append("Memory: \n${MemoryUtils.getMemoryStats(context)}\n")
        return stats.toString()
    }

    fun builderStats(
        n: BraveVPNService.Networks,
        builderRoutes: Pair<Boolean, Boolean>,
        builderStatsText: String,
        fd: Int,
        systemDns: String,
        tunUnderlyingNetworks: String?,
        lockdown: Boolean,
        vpnLockdown: Boolean?,
        ssid: String,
        route4: Boolean,
        route6: Boolean,
        netid: (Long) -> Long,
        isLockdownEnabled: Boolean,
    ): String {
        val ipv4NwHandles = n.underlyingNws?.ipv4Net?.map { netid(it.network.networkHandle) } ?: emptyList()
        val ipv6NwHandles = n.underlyingNws?.ipv6Net?.map { netid(it.network.networkHandle) } ?: emptyList()
        val linkAddresses4 = n.underlyingNws?.ipv4Net?.map { it.linkProperties?.linkAddresses?.filter { i -> IPAddressString(i.address.hostAddress).isIPv4 } } ?: emptyList()
        val linkAddresses6 = n.underlyingNws?.ipv6Net?.map { it.linkProperties?.linkAddresses?.filter { i -> IPAddressString(i.address.hostAddress).isIPv6 } } ?: emptyList()
        val link4Mtu = if (isAtleastQ()) n.underlyingNws?.ipv4Net?.map { it.linkProperties?.mtu ?: 0 } ?: listOf(-1) else listOf(-1)
        val link6Mtu = if (isAtleastQ()) n.underlyingNws?.ipv6Net?.map { it.linkProperties?.mtu ?: 0 } ?: listOf(-1) else listOf(-1)

        val linkAddr4String = if (linkAddresses4.isEmpty()) {
            "N/A"
        } else {
            linkAddresses4.joinToString(", ") { it?.joinToString(", ") { addr -> addr.address.hostAddress.orEmpty() }.orEmpty() }.ifEmpty { "N/A" }
        }
        val linkAddr6String = if (linkAddresses6.isEmpty()) {
            "N/A"
        } else {
            linkAddresses6.joinToString(", ") { it?.joinToString(", ") { addr -> addr.address.hostAddress.orEmpty() }.orEmpty() }.ifEmpty { "N/A" }
        }
        val vpnServiceLockdown = if (isAtleastQ()) {
            isLockdownEnabled
        } else {
            ">Q"
        }
        val sb = StringBuilder()
        sb.append("  $builderStatsText\n")
        sb.append("   builderRoutes: ${builderRoutes}\n")
        sb.append("   fd: ${fd}\n")
        sb.append("   dns: ${systemDns}\n")
        sb.append("   stall: ${persistentState.stallOnNoNetwork}\n")
        sb.append("   setUnderlyingNws: $tunUnderlyingNetworks\n")
        sb.append("   loopback: ${persistentState.routeRethinkInRethink}\n")
        sb.append("   lockdown: ${lockdown}/${vpnLockdown ?: "null"}/$vpnServiceLockdown\n")
        sb.append("   Restart mechanism: ${persistentState.vpnBuilderPolicy}\n")
        sb.append("   Underlay\n")
        sb.append("      4: ${n.underlyingNws?.ipv4Net?.size}\n")
        sb.append("      6: ${n.underlyingNws?.ipv6Net?.size}\n")
        sb.append("      vpnRoutes: ${n.underlyingNws?.vpnRoutes}\n")
        sb.append("      useActive: ${n.underlyingNws?.useActive}\n")
        sb.append("      mtu: ${n.underlyingNws?.minMtu}\n")
        sb.append("   Overlay\n")
        sb.append("      4: ${n.overlayNws.has4}\n")
        sb.append("      6: ${n.overlayNws.has6}\n")
        sb.append("      mtu:${n.overlayNws.mtu}\n")
        sb.append("      determine4: $route4\n")
        sb.append("      determine6: $route6\n")
        sb.append("   Net ID\n")
        sb.append("      4: $ipv4NwHandles\n")
        sb.append("      6: $ipv6NwHandles\n")
        sb.append("   Link Addresses\n")
        sb.append("      4: $linkAddr4String\n")
        sb.append("      6: $linkAddr6String\n")
        sb.append("   Link MTU\n")
        sb.append("      4: $link4Mtu\n")
        sb.append("      6: $link6Mtu\n")
        sb.append("   SSID: $ssid\n")
        return sb.toString()
    }
}
