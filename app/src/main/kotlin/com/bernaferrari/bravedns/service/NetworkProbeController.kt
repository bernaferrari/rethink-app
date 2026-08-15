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
package com.bernaferrari.bravedns.service

import Logger
import Logger.LOG_TAG_CONNECTION
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.PROTOCOL_V4
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.PROTOCOL_V6
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.SCHEME_HTTPS
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.SCHEME_IP
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.USE_KOTLIN_REACHABILITY_CHECKS
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.netId
import com.bernaferrari.bravedns.service.ConnectionMonitor.IpsAndUrlToProbe
import com.bernaferrari.bravedns.service.ConnectionMonitor.NetworkAndSsid
import com.bernaferrari.bravedns.service.ConnectionMonitor.NetworkProperties
import com.bernaferrari.bravedns.service.ConnectionMonitor.OpPrefs
import com.bernaferrari.bravedns.util.ConnectivityCheckHelper
import com.bernaferrari.bravedns.util.Utilities.isNetworkSame
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.concurrent.TimeUnit

/**
 * Reachability probing, captive/validated classification, and tracked-network
 * population for [ConnectionMonitor].
 */
class NetworkProbeController(
    private val cm: ConnectivityManager,
    ipsAndUrl: IpsAndUrlToProbe,
    private val redrive: suspend () -> Unit,
) {

    private var reachabilityCount = 0L
    private val maxReachabilityCount = 3L

    val ip4probes = ipsAndUrl.ip4probes
    val ip6probes = ipsAndUrl.ip6probes
    val url4Probe = ipsAndUrl.url4Probe
    val url6Probe = ipsAndUrl.url6Probe

    suspend fun isActiveConnectionMetered(): Boolean {
        return cm.isActiveNetworkMetered
    }

    suspend fun isNetworkCellular(network: Network?): Boolean {
        if (network == null) {
            Logger.d(LOG_TAG_CONNECTION, "isNetworkCellular: network is null")
            return false
        }

        val cap = cm.getNetworkCapabilities(network)
        val hasCellular = cap?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
        val hasWifi = cap?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        Logger.v(LOG_TAG_CONNECTION, "isNetworkCellular: netid: ${netId(network.networkHandle)}, hasCellular? $hasCellular, hasWifi? $hasWifi, metered? ${cm.isActiveNetworkMetered}")
        val isCellular = cap?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        return isCellular
    }

    suspend fun determineVpnProtos(nws: Set<NetworkAndSsid?>): Pair<Boolean, Boolean>? {
        val vpnNw = nws.firstOrNull { isVPN(it?.network) == true }
        if (vpnNw == null) {
            Logger.i(LOG_TAG_CONNECTION, "determineVpnProtos; no vpn networks found")
            return null
        }

        val lp = cm.getLinkProperties(vpnNw.network)
        var has4 = false
        var has6 = false

        if (!has4 && !has6) {
            lp?.routes?.forEach rloop@{
                val hasDefaultRoute4 =
                    (it.isDefaultRoute && it.destination.address is Inet4Address)
                val hasDefaultRoute6 =
                    (it.isDefaultRoute && it.destination.address is Inet6Address)

                has4 = hasDefaultRoute4
                has4 = hasDefaultRoute6

                if (has4 && has6) return@rloop
            }
        }

        Logger.i(LOG_TAG_CONNECTION, "determineVpnProtos; has4? $has4, has6? $has6, $lp")
        return Pair(has4, has6)
    }

    suspend fun repopulateTrackedNetworks(
        opPrefs: OpPrefs,
        nwProps: LinkedHashSet<NetworkProperties>
    ): Pair<LinkedHashSet<NetworkProperties>, LinkedHashSet<NetworkProperties>> {
        val trackedIpv4Networks: LinkedHashSet<NetworkProperties> = linkedSetOf()
        val trackedIpv6Networks: LinkedHashSet<NetworkProperties> = linkedSetOf()
        val activeNetwork = cm.activeNetwork

        nwProps.forEach { prop ->
            classifyNetwork(opPrefs, prop, activeNetwork, trackedIpv4Networks, trackedIpv6Networks)
        }

        if (trackedIpv4Networks.isEmpty() && trackedIpv6Networks.isEmpty() && !opPrefs.stallOnNoNetwork) {
            applyFailOpen(nwProps, trackedIpv4Networks, trackedIpv6Networks)
        }

        redoReachabilityIfNeeded(trackedIpv4Networks, trackedIpv6Networks)

        val ipv4 = rearrangeNetworks(trackedIpv4Networks)
        val ipv6 = rearrangeNetworks(trackedIpv6Networks)
        Logger.d(LOG_TAG_CONNECTION, "repopulate v6: $ipv6,\nv4: $ipv4")
        return Pair(ipv4, ipv6)
    }

    private suspend fun classifyNetwork(
        opPrefs: OpPrefs,
        prop: NetworkProperties,
        activeNetwork: Network?,
        trackedIpv4Networks: LinkedHashSet<NetworkProperties>,
        trackedIpv6Networks: LinkedHashSet<NetworkProperties>,
    ) {
        val network: Network = prop.network
        val lp = cm.getLinkProperties(network)
        if (lp == null) {
            Logger.i(LOG_TAG_CONNECTION, "skipping: ${network.networkHandle}, netid: ${netId(network.networkHandle)}; no link properties")
            return
        }

        val isActiveNull = activeNetwork == null
        val isActive = !isActiveNull && isNetworkSame(network, activeNetwork)
        val isCaptive = isCaptivePortal(network)
        val maybeCaptiveActive = isCaptive && (isActive || isActiveNull)
        val isValidated = isValidated(network)
        val hasInternet = hasInternet(network)
        Logger.d(LOG_TAG_CONNECTION, "processing: ${network.networkHandle}, netid: ${netId(network.networkHandle)}, active? $isActive, activeNull? $isActiveNull, internet? $hasInternet, captive? $isCaptive, validated? $isValidated")
        if (probeAndTrack(opPrefs, prop, network, trackedIpv4Networks, trackedIpv6Networks)) {
            return
        }

        val isCaptivePortal = maybeCaptiveActive && hasInternet
        val relyOnValidation = !opPrefs.testReachability && isValidated && hasInternet
        if (isCaptivePortal || relyOnValidation) {
            addByDefaultRoutes(prop, network, lp, trackedIpv4Networks, trackedIpv6Networks, "nwValidation")
        } else {
            Logger.i(LOG_TAG_CONNECTION, "skip: ${network.networkHandle}, netid: ${netId(network.networkHandle)}, cap: ${prop.capabilities}; no internet capability")
        }
    }

    private suspend fun probeAndTrack(
        opPrefs: OpPrefs,
        prop: NetworkProperties,
        network: Network,
        trackedIpv4Networks: LinkedHashSet<NetworkProperties>,
        trackedIpv6Networks: LinkedHashSet<NetworkProperties>,
    ): Boolean {
        if (opPrefs.testReachability) {
            val has4 = probeConnectivity(opPrefs.useAutoConnectivityChecks, network, SCHEME_HTTPS, PROTOCOL_V4)
            val has6 = probeConnectivity(opPrefs.useAutoConnectivityChecks, network, SCHEME_HTTPS, PROTOCOL_V6)
            if (has4) trackedIpv4Networks.add(prop)
            if (has6) trackedIpv6Networks.add(prop)
            Logger.i(LOG_TAG_CONNECTION, "url probe, nw(${network.networkHandle}, netid: ${netId(network.networkHandle)}): has4? $has4, has6? $has6, $prop")
            if (has4 && has6) return true
        }

        val nwHas4 = trackedIpv4Networks.any { it.network == network }
        val nwHas6 = trackedIpv6Networks.any { it.network == network }
        if (opPrefs.testReachability && (!nwHas4 && !nwHas6)) {
            val has4 = probeConnectivity(opPrefs.useAutoConnectivityChecks, network, SCHEME_IP, PROTOCOL_V4)
            val has6 = probeConnectivity(opPrefs.useAutoConnectivityChecks, network, SCHEME_IP, PROTOCOL_V6)
            if (has4) trackedIpv4Networks.add(prop)
            if (has6) trackedIpv6Networks.add(prop)
            Logger.i(LOG_TAG_CONNECTION, "ip probe, nw(${network.networkHandle}, netid: ${netId(network.networkHandle)}): has4? $has4, has6? $has6, $prop")
            if (has4 && has6) return true
        }

        val nwHas4AfterProbe = trackedIpv4Networks.any { it.network == network }
        val nwHas6AfterProbe = trackedIpv6Networks.any { it.network == network }
        if (nwHas4AfterProbe || nwHas6AfterProbe) {
            Logger.i(LOG_TAG_CONNECTION, "nw(${network.networkHandle}, netid: ${netId(network.networkHandle)}) already has ipv4? $nwHas4AfterProbe, ipv6? $nwHas6AfterProbe, skipping further checks")
            return true
        }
        return false
    }

    private fun applyFailOpen(
        nwProps: LinkedHashSet<NetworkProperties>,
        trackedIpv4Networks: LinkedHashSet<NetworkProperties>,
        trackedIpv6Networks: LinkedHashSet<NetworkProperties>,
    ) {
        Logger.i(LOG_TAG_CONNECTION, "no networks found, but fail-open is enabled")
        nwProps.forEach { prop ->
            val network: Network = prop.network
            val lp = cm.getLinkProperties(network)
            if (lp == null) {
                Logger.i(LOG_TAG_CONNECTION, "skip fail-open: ${network.networkHandle}, netid: ${netId(network.networkHandle)}; no link properties")
                return@forEach
            }
            if (!hasInternet(network)) {
                Logger.i(LOG_TAG_CONNECTION, "skip fail-open: ${network.networkHandle}, netid: ${netId(network.networkHandle)}; no internet capability")
                return@forEach
            }
            addByDefaultRoutes(prop, network, lp, trackedIpv4Networks, trackedIpv6Networks, "fail-open")
        }
    }

    private fun addByDefaultRoutes(
        prop: NetworkProperties,
        network: Network,
        lp: android.net.LinkProperties,
        trackedIpv4Networks: LinkedHashSet<NetworkProperties>,
        trackedIpv6Networks: LinkedHashSet<NetworkProperties>,
        logPrefix: String,
    ) {
        var hasDefaultRoute4 = false
        var hasDefaultRoute6 = false
        lp.routes.forEach rloop@{
            hasDefaultRoute4 =
                hasDefaultRoute4 ||
                        (it.isDefaultRoute && it.destination.address is Inet4Address)
            hasDefaultRoute6 =
                hasDefaultRoute6 ||
                        (it.isDefaultRoute && it.destination.address is Inet6Address)
            if (hasDefaultRoute4 && hasDefaultRoute6) return@rloop
        }
        if (hasDefaultRoute6) trackedIpv6Networks.add(prop)
        if (hasDefaultRoute4) trackedIpv4Networks.add(prop)
        Logger.i(
            LOG_TAG_CONNECTION,
            "$logPrefix, nw(${network.networkHandle}, netid: ${netId(network.networkHandle)}) default4? $hasDefaultRoute4, default6? $hasDefaultRoute6 for $prop"
        )
    }

    suspend fun probeConnectivity(useAuto: Boolean, network: Network, scheme: String, protocol: String): Boolean {
        val ipOrUrls = if (protocol == PROTOCOL_V4 && scheme == SCHEME_HTTPS) {
            url4Probe
        } else if (protocol == PROTOCOL_V6 && scheme == SCHEME_HTTPS) {
            url6Probe
        } else if (protocol == PROTOCOL_V4 && scheme == SCHEME_IP) {
            ip4probes
        } else if (protocol == PROTOCOL_V6 && scheme == SCHEME_IP) {
            ip6probes
        } else {
            Logger.w(LOG_TAG_CONNECTION, "unknown protocol: $protocol, scheme: $scheme")
            url4Probe
        }
        return if (useAuto) {
            ConnectivityCheckHelper.probeConnectivityInAutoMode(
                network,
                scheme,
                protocol,
                ipOrUrls,
                USE_KOTLIN_REACHABILITY_CHECKS
            )
        } else {
            ConnectivityCheckHelper.probeConnectivityInManualMode(ipOrUrls, network, USE_KOTLIN_REACHABILITY_CHECKS)
        }
    }

    private suspend fun redoReachabilityIfNeeded(
        ipv4: LinkedHashSet<NetworkProperties>,
        ipv6: LinkedHashSet<NetworkProperties>
    ) {
        if (ipv4.isEmpty() && ipv6.isEmpty()) {
            reachabilityCount++
            Logger.i(LOG_TAG_CONNECTION, "redo reachability, try: $reachabilityCount")
            if (reachabilityCount > maxReachabilityCount) return

            val delay = TimeUnit.SECONDS.toMillis(10 * reachabilityCount)
            delay(delay)
            redrive()
        } else {
            Logger.d(LOG_TAG_CONNECTION, "reset reachability count, prev: $reachabilityCount")
            reachabilityCount = 0
        }
    }

    private suspend fun rearrangeNetworks(
        networks: LinkedHashSet<NetworkProperties>
    ): LinkedHashSet<NetworkProperties> {
        val newNetworks: LinkedHashSet<NetworkProperties> = linkedSetOf()
        val activeNetwork = cm.activeNetwork
        val n = networks.firstOrNull { isNetworkSame(it.network, activeNetwork) }
        if (n != null) {
            newNetworks.add(n)
        }
        networks
            .filter { isConnectionNotMetered(it.capabilities) }
            .forEach { newNetworks.add(it) }
        networks
            .filter { !isConnectionNotMetered(it.capabilities) }
            .forEach { newNetworks.add(it) }
        return newNetworks
    }

    fun isConnectionNotMetered(capabilities: NetworkCapabilities?): Boolean {
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            ?: false
    }

    fun hasInternet(network: Network?): Boolean {
        if (network == null) return false

        return cm
            .getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun isCaptivePortal(network: Network): Boolean {
        return cm
            .getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
    }

    fun isValidated(network: Network): Boolean {
        return cm
            .getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    fun isVPN(network: Network?): Boolean? {
        if (network == null) return null
        return cm
            .getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}
