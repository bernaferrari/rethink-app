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
import android.net.NetworkRequest
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.bernaferrari.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.CONNECTIVITY_CHECK_INTERVAL_MS
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.EVENT_ON_AVAILABLE
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.EVENT_ON_CAPABILITIES_CHANGED
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.EVENT_ON_LINK_PROPERTIES_CHANGED
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.EVENT_ON_LOST
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.MSG_ADD_ACTIVE_NETWORK
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.MSG_ADD_ALL_NETWORKS
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.netId
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.networkType
import com.bernaferrari.bravedns.service.ConnectionMonitor.IpsAndUrlToProbe
import com.bernaferrari.bravedns.service.ConnectionMonitor.NetworkAndSsid
import com.bernaferrari.bravedns.service.ConnectionMonitor.NetworkListener
import com.bernaferrari.bravedns.service.ConnectionMonitor.NetworkProperties
import com.bernaferrari.bravedns.service.ConnectionMonitor.OpPrefs
import com.bernaferrari.bravedns.service.ConnectionMonitor.UnderlyingNetworks
import com.bernaferrari.bravedns.service.VpnBuilderPolicy.Companion.getNetworkBehaviourDuration
import com.bernaferrari.bravedns.util.InternetProtocol
import com.bernaferrari.bravedns.util.Utilities.isAtleastO
import com.bernaferrari.bravedns.util.Utilities.isAtleastQ
import com.bernaferrari.bravedns.util.Utilities.isAtleastR
import com.bernaferrari.bravedns.util.Utilities.isAtleastS
import com.bernaferrari.bravedns.util.Utilities.isNetworkSame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Callback registration, [UnderlyingNetworks] mutation, and network-change
 * diffing for [ConnectionMonitor].
 */
class UnderlyingNetworkTracker(
    private val persistentState: PersistentState,
    private val serializer: CoroutineDispatcher,
    private val scope: CoroutineScope,
) {

    private val networkSet: MutableSet<NetworkAndSsid> = ConcurrentHashMap.newKeySet()

    private data class ConnectivityCheckState(
        val lastCheckTime: Long,
        val networkHandles: Set<Long>
    )

    private var lastConnectivityCheckState: ConnectivityCheckState? = null
    private var networkCallbackThread: HandlerThread? = null
    private lateinit var channel: Channel<OpPrefs>
    private lateinit var cm: ConnectivityManager

    val networkRequest: NetworkRequest =
        NetworkRequest.Builder()
            .apply { if (isAtleastR()) clearCapabilities() else removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) }
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .apply { if (isAtleastS()) setIncludeOtherUidNetworks(true) }
            .build()

    val networkRequestWithTransports: NetworkRequest =
        NetworkRequest.Builder()
            .apply { if (isAtleastR()) clearCapabilities() else removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) }
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .apply { if (isAtleastS()) setIncludeOtherUidNetworks(true) }
            .build()

    fun attachConnectivityManager(manager: ConnectivityManager) {
        cm = manager
    }

    fun shouldPerformConnectivityCheck(eventType: String): Boolean {
        val currentTime = SystemClock.elapsedRealtime()
        val currentNetworkHandles = networkSet.map { it.network.networkHandle }.toSet()

        when (eventType) {
            EVENT_ON_AVAILABLE, EVENT_ON_LOST, EVENT_ON_LINK_PROPERTIES_CHANGED -> {
                updateLastConnectivityCheckState(currentTime, currentNetworkHandles)
                Logger.d(LOG_TAG_CONNECTION, "Connectivity check approved for $eventType")
                return true
            }
            EVENT_ON_CAPABILITIES_CHANGED -> {
                val lastState = lastConnectivityCheckState
                val timeSinceLastCheck = if (lastState != null) {
                    currentTime - lastState.lastCheckTime
                } else {
                    Long.MAX_VALUE
                }
                val networkHandlesChanged = if (lastState != null) {
                    lastState.networkHandles != currentNetworkHandles
                } else {
                    true
                }
                val shouldCheck = timeSinceLastCheck >= CONNECTIVITY_CHECK_INTERVAL_MS || networkHandlesChanged
                if (shouldCheck) {
                    updateLastConnectivityCheckState(currentTime, currentNetworkHandles)
                    Logger.d(LOG_TAG_CONNECTION, "Connectivity check approved for $eventType - time since last: ${timeSinceLastCheck}ms, network changed: $networkHandlesChanged")
                } else {
                    Logger.d(LOG_TAG_CONNECTION, "Connectivity check skipped for $eventType - time since last: ${timeSinceLastCheck}ms, network changed: $networkHandlesChanged")
                }
                return shouldCheck
            }
            else -> {
                Logger.w(LOG_TAG_CONNECTION, "Unknown event type: $eventType")
                return false
            }
        }
    }

    fun updateLastConnectivityCheckState(time: Long, networkHandles: Set<Long>) {
        lastConnectivityCheckState = ConnectivityCheckState(time, networkHandles)
    }

    fun addToNwSet(network: Network, ssid: String? = null) {
        val old = networkSet.find { it.network == network }
        val networkSsid = NetworkAndSsid(network, ssid ?: old?.ssid)
        networkSet.removeIf { it.network == network }
        networkSet.add(networkSsid)
    }

    fun removeFromNwSet(network: Network) {
        networkSet.removeIf { it.network == network }
    }

    fun clearNetworks() {
        networkSet.clear()
    }

    fun startHandler(listener: NetworkListener, ips: IpsAndUrlToProbe, behaviour: VpnBuilderPolicy.ConnectionMonitorBehaviour) {
        channel = Channel(Channel.CONFLATED)
        val probe = NetworkProbeController(cm, ips, ::sendNetworkChanges)
        val hdl = NetworkRequestHandler(cm, listener, probe)
        scope.launch(CoroutineName("nwHdl") + serializer) {
            sendNetworkChanges()
            for (m in channel) {
                val deferred = async { hdl.handleMessage(m) }
                deferred.await()
                delay(getNetworkBehaviourDuration(behaviour))
            }
        }
    }

    fun closeChannel() {
        if (::channel.isInitialized) {
            channel.close()
        }
    }

    fun networkCallbackHandler(): Handler {
        val existing = networkCallbackThread
        val thread =
            if (existing != null && existing.isAlive) {
                existing
            } else {
                HandlerThread("cm-net-cb").apply {
                    isDaemon = true
                    start()
                }.also { networkCallbackThread = it }
            }
        return Handler(thread.looper)
    }

    fun quitNetworkCallbackThread() {
        networkCallbackThread?.quitSafely()
        networkCallbackThread = null
    }

    fun registerNetworkCallback(req: NetworkRequest, callback: ConnectivityManager.NetworkCallback): Boolean {
        return try {
            if (isAtleastO()) {
                cm.registerNetworkCallback(req, callback, networkCallbackHandler())
            } else {
                cm.registerNetworkCallback(req, callback)
            }
            true
        } catch (e: SecurityException) {
            Logger.w(LOG_TAG_CONNECTION, "err registering network callback", e)
            false
        } catch (e: IllegalArgumentException) {
            Logger.w(LOG_TAG_CONNECTION, "err registering network callback", e)
            false
        }
    }

    suspend fun sendNetworkChanges() {
        val dualStack =
            InternetProtocol.getInternetProtocol(persistentState.internetProtocolType).isIPv46()
        val testReachability = dualStack && persistentState.connectivityChecks
        val failOpenOnNoNetwork = !persistentState.stallOnNoNetwork
        val useAutoConnectivityChecks = persistentState.performAutoNetworkConnectivityChecks
        val msg =
            OpPrefs(
                if (persistentState.useMultipleNetworks) MSG_ADD_ALL_NETWORKS
                else MSG_ADD_ACTIVE_NETWORK,
                networkSet.toSet(),
                testReachability,
                failOpenOnNoNetwork,
                useAutoConnectivityChecks
            )

        if (!::channel.isInitialized) {
            Logger.e(LOG_TAG_CONNECTION, "sendNetworkChanges, channel is not initialized")
            return
        }
        @Suppress("OPT_IN_USAGE")
        if (DEBUG) Logger.v(LOG_TAG_CONNECTION, "sendNetworkChanges, channel closed? ${channel.isClosedForSend} msg: ${msg.msgType}, test: ${msg.testReachability}, stall: ${msg.stallOnNoNetwork}, useAutoChecks: ${msg.useAutoConnectivityChecks}, networks: ${msg.networkSet.size}")
        try {
            channel.send(msg)
        } catch (e: ClosedSendChannelException) {
            Logger.e(LOG_TAG_CONNECTION, "sendNetworkChanges, err while sending message to channel", e)
        }
    }

    private class NetworkRequestHandler(
        val cm: ConnectivityManager,
        val listener: NetworkListener,
        val probe: NetworkProbeController,
    ) {
        companion object {
            private const val DEFAULT_MTU = 1500
            private const val MIN_MTU = 1280
        }

        var currentNetworks: LinkedHashSet<NetworkProperties> = linkedSetOf()
        var trackedIpv4Networks: LinkedHashSet<NetworkProperties> = linkedSetOf()
        var trackedIpv6Networks: LinkedHashSet<NetworkProperties> = linkedSetOf()

        suspend fun handleMessage(opPrefs: OpPrefs) {
            when (opPrefs.msgType) {
                MSG_ADD_ACTIVE_NETWORK -> processActiveNetwork(opPrefs)
                MSG_ADD_ALL_NETWORKS -> processAllNetworks(opPrefs)
            }
        }

        private suspend fun processActiveNetwork(opPrefs: OpPrefs) {
            val newActiveNetwork = cm.activeNetwork
            val newActiveNetworkCap = cm.getNetworkCapabilities(newActiveNetwork)
            val isActiveNetworkMetered = probe.isActiveConnectionMetered()
            val isActiveNetworkCellular = probe.isNetworkCellular(newActiveNetwork)
            val newNetworks = createNetworksSet(newActiveNetwork, opPrefs.networkSet)
            val isNewNetwork = hasDifference(currentNetworks, newNetworks)
            val vpnRoutes = probe.determineVpnProtos(opPrefs.networkSet)
            val isDnsChanged = hasNwDnsChanged(currentNetworks, newNetworks)
            val isLinkAddressChanged = hasLinkAddrChanged(currentNetworks, newNetworks)
            var activeSsid = newNetworks.firstOrNull { it.network.networkHandle == newActiveNetwork?.networkHandle }?.ssid
            if (activeSsid == null) {
                activeSsid = newNetworks.firstOrNull { it.ssid != null }?.ssid
            }

            Logger.i(LOG_TAG_CONNECTION, "process message active nws, currNws: $newNetworks")
            Logger.i(
                LOG_TAG_CONNECTION,
                "Connected network: ${newActiveNetwork?.networkHandle} ${
                    networkType(newActiveNetworkCap)
                }, netid: ${netId(newActiveNetwork?.networkHandle)}, new? $isNewNetwork, test? ${opPrefs.testReachability}," +
                 "cellular? $isActiveNetworkCellular, metered? $isActiveNetworkMetered, dns-changed? $isDnsChanged, link-address-changed? $isLinkAddressChanged, activeSsid: $activeSsid"
            )

            currentNetworks = newNetworks
            val tracked = probe.repopulateTrackedNetworks(opPrefs, currentNetworks)
            trackedIpv4Networks = tracked.first
            trackedIpv6Networks = tracked.second
            informListener(true, isActiveNetworkMetered, isActiveNetworkCellular, activeSsid, vpnRoutes)
        }

        private suspend fun hasNwDnsChanged(currNws: Set<NetworkProperties>, newNws: Set<NetworkProperties>): Boolean {
            val currDnsServers = currNws.map { it.linkProperties }.mapNotNull { it?.dnsServers }.flatMap { it }.map { it.address }.toSet()
            val newDnsServers = newNws.map { it.linkProperties }.mapNotNull { it?.dnsServers }.flatMap { it }.map { it.address }.toSet()
            return newDnsServers == currDnsServers
        }

        private suspend fun hasLinkAddrChanged(currNws: Set<NetworkProperties>, newNws: Set<NetworkProperties>): Boolean {
            val currLinkAddresses = currNws.map { it.linkProperties }.mapNotNull { it?.linkAddresses }.flatMap { it }.map { it.address.address }.toSet()
            val newLinkAddresses = newNws.map { it.linkProperties }.mapNotNull { it?.linkAddresses }.flatMap { it }.map { it.address.address }.toSet()
            return newLinkAddresses == currLinkAddresses
        }

        private suspend fun processAllNetworks(opPrefs: OpPrefs) {
            val newActiveNetwork = cm.activeNetwork
            val isActiveNetworkMetered = probe.isActiveConnectionMetered()
            val newNetworks = createNetworksSet(newActiveNetwork, opPrefs.networkSet)
            val isNewNetwork = hasDifference(currentNetworks, newNetworks)
            val vpnRoutes = probe.determineVpnProtos(opPrefs.networkSet)
            val isActiveNetworkCellular = probe.isNetworkCellular(newActiveNetwork)
            val isDnsChanged = hasNwDnsChanged(currentNetworks, newNetworks)
            val isLinkAddressChanged = hasLinkAddrChanged(currentNetworks, newNetworks)
            var activeSsid = newNetworks.firstOrNull { it.network.networkHandle == newActiveNetwork?.networkHandle }?.ssid
            if (activeSsid == null) {
                activeSsid = newNetworks.firstOrNull { it.ssid != null }?.ssid
            }

            Logger.i(LOG_TAG_CONNECTION, "process message all nws, currNws: $newNetworks")
            Logger.i(LOG_TAG_CONNECTION, "process message all nws, newNws: $newNetworks \nnew? $isNewNetwork, test? ${opPrefs.testReachability}, cellular? $isActiveNetworkCellular, metered? $isActiveNetworkMetered, dns-changed? $isDnsChanged, link-addr-changed? $isLinkAddressChanged, activeSsid: $activeSsid")

            currentNetworks = newNetworks
            val tracked = probe.repopulateTrackedNetworks(opPrefs, currentNetworks)
            trackedIpv4Networks = tracked.first
            trackedIpv6Networks = tracked.second
            informListener(false, isActiveNetworkMetered, isActiveNetworkCellular, activeSsid, vpnRoutes)
        }

        private suspend fun informListener(
            useActiveNetwork: Boolean = false,
            isActiveNetworkMetered: Boolean,
            isActiveNetworkCellular: Boolean,
            activeSsid: String?,
            vpnRoutes: Pair<Boolean, Boolean>?
        ) {
            val sz = trackedIpv4Networks.size + trackedIpv6Networks.size
            Logger.i(
                LOG_TAG_CONNECTION,
                "inform network change: ${sz}, useActive? $useActiveNetwork, metered? $isActiveNetworkMetered"
            )
            val dns4 = getDnsServers(trackedIpv4Networks)
            val dns6 = getDnsServers(trackedIpv6Networks)
            val dnsServers: LinkedHashMap<InetAddress, Network> = LinkedHashMap()
            dnsServers.putAll(dns4)
            dnsServers.putAll(dns6)

            if (sz > 0) {
                trackedIpv4Networks.forEach {
                    Logger.d(LOG_TAG_CONNECTION, "inform4: ${it.network}, ${it.networkType}, $sz")
                }
                trackedIpv6Networks.forEach {
                    Logger.d(LOG_TAG_CONNECTION, "inform6: ${it.network}, ${it.networkType}, $sz")
                }
                val underlyingNetworks =
                    UnderlyingNetworks(
                        trackedIpv4Networks.map { it },
                        trackedIpv6Networks.map { it },
                        vpnRoutes,
                        useActiveNetwork,
                        determineMtu(useActiveNetwork),
                        isActiveNetworkMetered,
                        isActiveNetworkCellular,
                        activeSsid,
                        SystemClock.elapsedRealtimeNanos(),
                        Collections.unmodifiableMap(dnsServers)
                    )
                listener.onNetworkChange(underlyingNetworks)
            } else {
                val underlyingNetworks =
                    UnderlyingNetworks(
                        emptyList(),
                        emptyList(),
                        vpnRoutes,
                        useActiveNetwork,
                        DEFAULT_MTU,
                        isActiveNetworkMetered = false,
                        isActiveNetworkCellular = false,
                        null,
                        SystemClock.elapsedRealtimeNanos(),
                        LinkedHashMap()
                    )
                listener.onNetworkChange(underlyingNetworks)
            }
        }

        private suspend fun getDnsServers(
            nws: LinkedHashSet<NetworkProperties>
        ): LinkedHashMap<InetAddress, Network> {
            val dnsServers = LinkedHashMap<InetAddress, Network>()
            nws.forEach {
                it.linkProperties?.dnsServers?.forEach v@{ dns ->
                    val address = dns ?: return@v
                    dnsServers[address] = it.network
                }
            }
            return dnsServers
        }

        private suspend fun determineMtu(useActiveNetwork: Boolean): Int {
            var minMtu4: Int = -1
            var minMtu6: Int = -1
            if (!isAtleastQ()) {
                return MIN_MTU
            }
            if (useActiveNetwork) {
                cm.activeNetwork?.let {
                    val lp = cm.getLinkProperties(it)
                    minMtu4 = minNonZeroMtu(lp?.mtu, minMtu4)
                    minMtu6 = minNonZeroMtu(lp?.mtu, minMtu6)
                    Logger.v(LOG_TAG_CONNECTION, "active network mtu: ${lp?.mtu}, minMtu4: $minMtu4, minMtu6: $minMtu6")
                }
                    ?: run {
                        val lp4 = trackedIpv4Networks.firstOrNull()?.linkProperties
                        val lp6 = trackedIpv6Networks.firstOrNull()?.linkProperties
                        Logger.v(LOG_TAG_CONNECTION, "tracked network mtu: ${lp4?.mtu}, ${lp6?.mtu}")
                        minMtu4 = minNonZeroMtu(lp4?.mtu, minMtu4)
                        minMtu6 = minNonZeroMtu(lp6?.mtu, minMtu6)
                    }
            } else {
                trackedIpv4Networks.forEach {
                    val c = it.linkProperties
                    minMtu4 = minNonZeroMtu(c?.mtu, minMtu4)
                    Logger.v(LOG_TAG_CONNECTION, "tracked network4 mtu: ${c?.mtu}, using $minMtu4")
                }
                trackedIpv6Networks.forEach {
                    val c = it.linkProperties
                    minMtu6 = minNonZeroMtu(c?.mtu, minMtu6)
                    Logger.v(LOG_TAG_CONNECTION, "tracked network6 mtu: ${c?.mtu}, using $minMtu6")
                }
            }
            if (minMtu4 <= 0 && minMtu6 <= 0) {
                Logger.i(LOG_TAG_CONNECTION, "Both MTUs are invalid, using MIN_MTU: $DEFAULT_MTU")
                return DEFAULT_MTU
            }
            val mtu = max(min(minMtu4.takeIf { it > 0 } ?: DEFAULT_MTU, minMtu6.takeIf { it > 0 } ?: DEFAULT_MTU), MIN_MTU)
            Logger.i(LOG_TAG_CONNECTION, "mtu4: $minMtu4, mtu6: $minMtu6; final mtu: $mtu")
            return mtu
        }

        private suspend fun minNonZeroMtu(m1: Int?, m2: Int): Int {
            val mtu1 = if (m1 == 0) 1500 else m1
            val mtu2 = if (m2 == 0) 1500 else m2
            return if (mtu1 != null && mtu1 > 0) {
                if (mtu2 <= 0) mtu1 else min(mtu1, mtu2)
            } else {
                if (mtu2 <= 0) {
                    DEFAULT_MTU
                } else {
                    mtu2
                }
            }
        }

        private suspend fun hasDifference(
            currentNetworks: LinkedHashSet<NetworkProperties>,
            newNetworks: LinkedHashSet<NetworkProperties>
        ): Boolean {
            if (currentNetworks.size != newNetworks.size) {
                return true
            }
            val cn = currentNetworks.map { it.network.networkHandle }.toHashSet()
            val nn = newNetworks.map { it.network.networkHandle }.toHashSet()
            return cn != nn
        }

        private suspend fun createNetworksSet(
            activeNetwork: Network?,
            networkSet: Set<NetworkAndSsid>
        ): LinkedHashSet<NetworkProperties> {
            val newNetworks: LinkedHashSet<NetworkProperties> = linkedSetOf()
            activeNetwork?.let {
                val activeCap = cm.getNetworkCapabilities(activeNetwork)
                val activeLp = cm.getLinkProperties(activeNetwork)
                val nwType =
                    networkType(activeCap) + ", NotMetered?" + probe.isConnectionNotMetered(activeCap)
                val activeProp =
                    if (activeCap != null) {
                        val ssid = networkSet.firstOrNull { ns -> ns.network.networkHandle == it.networkHandle }?.ssid
                        NetworkProperties(it, activeCap, activeLp, nwType, ssid)
                    } else {
                        null
                    }
                if (activeProp != null && probe.isVPN(it) == false) {
                    newNetworks.add(activeProp)
                }
            }
            val networks =
                if (networkSet.isEmpty()) {
                    Logger.d(LOG_TAG_CONNECTION, "networkSet is empty")
                    @Suppress("DEPRECATION")
                    cm.allNetworks
                } else {
                    Logger.d(LOG_TAG_CONNECTION, "networkSet size: ${networkSet.size}")
                    networkSet.map { it.network }.toTypedArray()
                }

            networks.forEach {
                val cap = cm.getNetworkCapabilities(it)
                val lp = cm.getLinkProperties(it)
                val prop =
                    if (cap != null) {
                        val nwType =
                            networkType(cap) + ", NotMetered?" + probe.isConnectionNotMetered(cap)
                        val ssid = networkSet.firstOrNull { ns -> ns.network == it }?.ssid
                        NetworkProperties(it, cap, lp, nwType, ssid)
                    } else {
                        null
                    }
                if (isNetworkSame(it, activeNetwork)) {
                    return@forEach
                }

                if (prop != null && probe.isVPN(it) == false) {
                    newNetworks.add(prop)
                }
            }

            return newNetworks
        }
    }
}
