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
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.annotation.RequiresApi
import com.bernaferrari.bravedns.util.Utilities.isAtleastR
import com.bernaferrari.bravedns.util.Utilities.isAtleastS
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.InetAddress

class ConnectionMonitor(private val context: Context, private val networkListener: NetworkListener, private val serializer: CoroutineDispatcher, private val scope: CoroutineScope) : KoinComponent, DiagnosticsManager.DiagnosticsListener {

    data class NetworkAndSsid(val network: Network, val ssid: String?)

    private val persistentState by inject<PersistentState>()
    private val tracker = UnderlyingNetworkTracker(persistentState, serializer, scope)
    private val ssidLookup = NetworkSsidLookup(context, persistentState) { wm }

    private lateinit var cm: ConnectivityManager
    private lateinit var wm: WifiManager
    private var diagsMgr: DiagnosticsManager? = null

    private val internetValidatedNetworkCallback: ConnectivityManager.NetworkCallback by lazy {
        if (isAtleastS()) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    scope.launch(CoroutineName("cmIntCap") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onCapabilitiesChanged(1S), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        val ssid = getNetworkSSID(network, capabilities)
                        addToNwSet(network, ssid)
                        if (shouldPerformConnectivityCheck(EVENT_ON_CAPABILITIES_CHANGED)) {
                            sendNetworkChanges()
                        }
                    }
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    scope.launch(CoroutineName("cmIntLink") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onLinkPropertiesChanged(1S), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        addToNwSet(network)
                        if (shouldPerformConnectivityCheck(EVENT_ON_LINK_PROPERTIES_CHANGED)) {
                            sendNetworkChanges()
                        }
                    }
                }

                override fun onAvailable(network: Network) {
                    val behaviour = getConnectionMonitorBehaviour()
                    if (behaviour != VpnBuilderPolicy.ConnectionMonitorBehaviour.VALIDATED_NETWORKS) {
                        Logger.d(LOG_TAG_CONNECTION, "onAvailable(1S), aggressive policy, ignoring networks from net-validated callback")
                        return
                    }
                    scope.launch(CoroutineName("cmIntAvl") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onAvailable(1S), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        addToNwSet(network)
                        if (shouldPerformConnectivityCheck(EVENT_ON_AVAILABLE)) {
                            sendNetworkChanges()
                        }
                    }
                }

                override fun onLost(network: Network) {
                    val behaviour = getConnectionMonitorBehaviour()
                    if (behaviour != VpnBuilderPolicy.ConnectionMonitorBehaviour.VALIDATED_NETWORKS) {
                        Logger.d(LOG_TAG_CONNECTION, "onLost(1S), aggressive policy, ignoring networks from net-cap callback")
                        return
                    }
                    scope.launch(CoroutineName("cmIntLost") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onLost(1S), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        removeFromNwSet(network)
                        if (shouldPerformConnectivityCheck(EVENT_ON_LOST)) {
                            sendNetworkChanges()
                        }
                    }
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    scope.launch(CoroutineName("cmTransCap") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onCapabilitiesChanged(2), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        val ssid = getNetworkSSID(network, capabilities)
                        addToNwSet(network, ssid)
                        if (shouldPerformConnectivityCheck(EVENT_ON_CAPABILITIES_CHANGED)) {
                            sendNetworkChanges()
                        }
                    }
                }

                override fun onAvailable(network: Network) {
                    scope.launch(CoroutineName("cmTransAvl") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onAvailable(2), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        addToNwSet(network)
                        if (shouldPerformConnectivityCheck(EVENT_ON_AVAILABLE)) {
                            sendNetworkChanges()
                        }
                    }
                }

                override fun onLost(network: Network) {
                    scope.launch(CoroutineName("cmTransLost") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onLost(2), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        removeFromNwSet(network)
                        if (shouldPerformConnectivityCheck(EVENT_ON_LOST)) {
                            sendNetworkChanges()
                        }
                    }
                }

                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties
                ) {
                    scope.launch(CoroutineName("cmTransLink") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onLinkPropertiesChanged(2), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        addToNwSet(network)
                        if (shouldPerformConnectivityCheck(EVENT_ON_LINK_PROPERTIES_CHANGED)) {
                            sendNetworkChanges()
                        }
                    }
                }
            }
        }
    }

    fun internetValidatedCallback(): ConnectivityManager.NetworkCallback = internetValidatedNetworkCallback

    private val transportNetworkCallback: ConnectivityManager.NetworkCallback by lazy {
        if (isAtleastS()) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    scope.launch(CoroutineName("cmTransCap") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onCapabilitiesChanged(2S), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        val ssid = getNetworkSSID(network, capabilities)
                        addToNwSet(network, ssid)
                        if (shouldPerformConnectivityCheck(EVENT_ON_CAPABILITIES_CHANGED)) {
                            sendNetworkChanges()
                        }
                    }
                }

                override fun onAvailable(network: Network) {
                    scope.launch(CoroutineName("cmTransAvl") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onAvailable(2S), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        addToNwSet(network)
                        if (shouldPerformConnectivityCheck(EVENT_ON_AVAILABLE)) {
                            sendNetworkChanges()
                        }
                    }
                }

                override fun onLost(network: Network) {
                    scope.launch(CoroutineName("cmTransLost") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onLost(2S), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        removeFromNwSet(network)
                        if (shouldPerformConnectivityCheck(EVENT_ON_LOST)) {
                            sendNetworkChanges()
                        }
                    }
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    scope.launch(CoroutineName("cmTransLink") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onLinkPropertiesChanged(2S), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        addToNwSet(network)
                        if (shouldPerformConnectivityCheck(EVENT_ON_LINK_PROPERTIES_CHANGED)) {
                            sendNetworkChanges()
                        }
                    }
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    scope.launch(CoroutineName("cmTransCap") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onCapabilitiesChanged(2), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        val ssid = getNetworkSSID(network, capabilities)
                        addToNwSet(network, ssid)
                        if (shouldPerformConnectivityCheck(EVENT_ON_CAPABILITIES_CHANGED)) {
                            sendNetworkChanges()
                        }
                    }
                }

                override fun onAvailable(network: Network) {
                    scope.launch(CoroutineName("cmTransAvl") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onAvailable(2), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        addToNwSet(network)
                        if (shouldPerformConnectivityCheck(EVENT_ON_AVAILABLE)) {
                            sendNetworkChanges()
                        }
                    }
                }

                override fun onLost(network: Network) {
                    scope.launch(CoroutineName("cmTransLost") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onLost(2), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        removeFromNwSet(network)
                        if (shouldPerformConnectivityCheck(EVENT_ON_LOST)) {
                            sendNetworkChanges()
                        }
                    }
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    scope.launch(CoroutineName("cmTransLink") + serializer) {
                        Logger.d(LOG_TAG_CONNECTION, "onLinkPropertiesChanged(2), ${network.networkHandle}, netId: ${netId(network.networkHandle)}")
                        addToNwSet(network)
                        if (shouldPerformConnectivityCheck(EVENT_ON_LINK_PROPERTIES_CHANGED)) {
                            sendNetworkChanges()
                        }
                    }
                }
            }
        }
    }

    fun transportCallback(): ConnectivityManager.NetworkCallback = transportNetworkCallback

    fun getNetworkSSID(network: Network?, cap: NetworkCapabilities?): String? {
        return ssidLookup.getNetworkSSID(network, cap)
    }

    companion object {
        const val MSG_ADD_ACTIVE_NETWORK = 1
        const val MSG_ADD_ALL_NETWORKS = 2
        const val PROTOCOL_V4 = "v4"
        const val PROTOCOL_V6 = "v6"
        const val SCHEME_HTTP = "http"
        const val SCHEME_HTTPS = "https"
        const val SCHEME_IP = "ip"
        const val NOTIF_ID_SSID_LOCATION_PERMISSION = 105
        const val USE_KOTLIN_REACHABILITY_CHECKS = false

        fun networkType(netCap: NetworkCapabilities?): String {
            val a =
                if (netCap?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                    "VPN"
                } else if (
                    netCap?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                ) {
                    "WiFi"
                } else if (
                    netCap?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ==
                    true
                ) {
                    "Cellular"
                } else {
                    "Unknown"
                }
            return a
        }

        fun netId(nwHandle: Long?): Long {
            if (nwHandle == null) {
                return -1L
            }
            return nwHandle shr (32)
        }

        const val CONNECTIVITY_CHECK_INTERVAL_MS = 15_000L
        const val EVENT_ON_CAPABILITIES_CHANGED = "onCapabilitiesChanged"
        const val EVENT_ON_AVAILABLE = "onAvailable"
        const val EVENT_ON_LOST = "onLost"
        const val EVENT_ON_LINK_PROPERTIES_CHANGED = "onLinkPropertiesChanged"
    }

    data class OpPrefs(
        val msgType: Int,
        val networkSet: Set<NetworkAndSsid>,
        val testReachability: Boolean,
        val stallOnNoNetwork: Boolean,
        val useAutoConnectivityChecks: Boolean
    )

    data class ProbeResult(val ip: String, val ok: Boolean, val capabilities: NetworkCapabilities?)

    interface NetworkListener {
        suspend fun onNetworkRegistrationFailed()

        suspend fun maybeNetworkStall()

        suspend fun onNetworkChange(networks: UnderlyingNetworks)
    }

    private fun addToNwSet(network: Network, ssid: String? = null) {
        tracker.addToNwSet(network, ssid)
    }

    private fun removeFromNwSet(network: Network) {
        tracker.removeFromNwSet(network)
    }

    private fun shouldPerformConnectivityCheck(eventType: String): Boolean {
        return tracker.shouldPerformConnectivityCheck(eventType)
    }

    private fun updateLastConnectivityCheckState(time: Long, networkHandles: Set<Long>) {
        tracker.updateLastConnectivityCheckState(time, networkHandles)
    }

    private suspend fun sendNetworkChanges() {
        tracker.sendNetworkChanges()
    }

    private fun getConnectionMonitorBehaviour(): VpnBuilderPolicy.ConnectionMonitorBehaviour {
        val policyId = persistentState.vpnBuilderPolicy
        return VpnBuilderPolicy.fromOrdinalOrDefault(policyId).connectionMonitorBehaviour
    }

    fun onUserPreferenceChanged() {
        Logger.d(LOG_TAG_CONNECTION, "onUserPreferenceChanged")
        scope.launch(CoroutineName("cmPref") + serializer) { sendNetworkChanges() }
    }

    fun onPolicyChanged() {
        Logger.d(LOG_TAG_CONNECTION, "onPolicyChanged")
        scope.launch(CoroutineName("cmPolicy") + serializer) {
            if (::cm.isInitialized) {
                try {
                    cm.unregisterNetworkCallback(internetValidatedCallback())
                } catch (e: IllegalArgumentException) {
                    Logger.w(LOG_TAG_CONNECTION, "err unregistering internetValidatedCallBack, ${e.message}", e)
                }
                try {
                    cm.unregisterNetworkCallback(transportCallback())
                } catch (e: IllegalArgumentException) {
                    Logger.w(LOG_TAG_CONNECTION, "err unregistering transportCallback, ${e.message}", e)
                }
            }
            val success = registerCallbackBasedOnPolicy()

            if (!success) {
                networkListener.onNetworkRegistrationFailed()
            }
        }
    }

    suspend fun onVpnStart(context: Context): Boolean  {
        val deferred = scope.async {
            val isNewVpn = !::cm.isInitialized

            if (!isNewVpn) {
                Logger.w(LOG_TAG_CONNECTION, "connection monitor is already running")
                return@async false
            }

            cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            tracker.attachConnectivityManager(cm)

            val networkBehaviour = getConnectionMonitorBehaviour()
            Logger.i(LOG_TAG_CONNECTION, "new vpn is created force update the network, policy: $networkBehaviour")
            val success = registerCallbackBasedOnPolicy()

            if (!success) {
                networkListener.onNetworkRegistrationFailed()
                return@async false
            }

            if (isAtleastR()) {
                registerDiags(context)
            }

            val ips = IpsAndUrlToProbe(
                persistentState.pingv4Ips.split(",").map { it.trim() }
                    .filter { it.isNotEmpty() },
                persistentState.pingv6Ips.split(",").map { it.trim() }
                    .filter { it.isNotEmpty() },
                persistentState.pingv4Url.split(",").map { it.trim() }
                    .filter { it.isNotEmpty() },
                persistentState.pingv6Url.split(",").map { it.trim() }
                    .filter { it.isNotEmpty() }
            )
            tracker.startHandler(networkListener, ips, networkBehaviour)

            return@async isNewVpn
        }

        return deferred.await()
    }

    private fun registerCallbackBasedOnPolicy(): Boolean {
        val behaviour = getConnectionMonitorBehaviour()
        Logger.i(LOG_TAG_CONNECTION, "register nw callback/s, policy: ${behaviour.name}")
        return when (behaviour) {
            VpnBuilderPolicy.ConnectionMonitorBehaviour.TRANSPORTS -> {
                tracker.registerNetworkCallback(tracker.networkRequestWithTransports, transportCallback())
            }

            VpnBuilderPolicy.ConnectionMonitorBehaviour.VALIDATED_NETWORKS -> {
                tracker.registerNetworkCallback(tracker.networkRequest, internetValidatedCallback())
            }

            VpnBuilderPolicy.ConnectionMonitorBehaviour.VALIDATED_NETWORKS_AND_TRANSPORTS -> {
                tracker.registerNetworkCallback(tracker.networkRequestWithTransports, transportCallback()) &&
                        tracker.registerNetworkCallback(tracker.networkRequest, internetValidatedCallback())
            }
        }
    }

    @RequiresApi(30)
    fun registerDiags(context: Context) {
        if (isAtleastR()) {
            try {
                val diagnosticMgr = DiagnosticsManager(context, scope, this)
                diagnosticMgr.register()
            } catch (e: SecurityException) {
                Logger.w(LOG_TAG_CONNECTION, "DiagnosticsManager; err while getting connectivity diagnostics manager", e)
            } catch (e: IllegalStateException) {
                Logger.w(LOG_TAG_CONNECTION, "DiagnosticsManager; err while getting connectivity diagnostics manager", e)
            } catch (e: ClassCastException) {
                Logger.w(LOG_TAG_CONNECTION, "DiagnosticsManager; err while getting connectivity diagnostics manager", e)
            }
        }
    }

    @RequiresApi(30)
    fun unregisterDiags() {
        if (isAtleastR()) {
            try {
                diagsMgr?.unregister()
                diagsMgr = null
            } catch (e: IllegalArgumentException) {
                Logger.w(LOG_TAG_CONNECTION, "DiagnosticsManager; err while unregistering diag network callback", e)
            } catch (e: IllegalStateException) {
                Logger.w(LOG_TAG_CONNECTION, "DiagnosticsManager; err while unregistering diag network callback", e)
            }
        }
    }

    fun onVpnStop() {
        try {
            if (::cm.isInitialized) {
                try {
                    cm.unregisterNetworkCallback(internetValidatedCallback())
                } catch (e: IllegalArgumentException) {
                    Logger.w(LOG_TAG_CONNECTION, "err unregistering internetValidatedCallBack, ${e.message}", e)
                }
                try {
                    cm.unregisterNetworkCallback(transportCallback())
                } catch (e: IllegalArgumentException) {
                    Logger.w(LOG_TAG_CONNECTION, "err unregistering transportCallback, ${e.message}", e)
                }
            }
            if (isAtleastR()) {
                unregisterDiags()
            }
            tracker.clearNetworks()
            tracker.closeChannel()
            tracker.quitNetworkCallbackThread()
        } catch (e: IllegalStateException) {
            Logger.w(LOG_TAG_CONNECTION, "err while unregistering; ${e.message}", e)
            tracker.quitNetworkCallbackThread()
        }
    }

    override suspend fun maybeNetworkStall() {
        Logger.i(LOG_TAG_CONNECTION, "onNetworkStallDetected")
        networkListener.maybeNetworkStall()
    }

    data class NetworkProperties(
        val network: Network,
        val capabilities: NetworkCapabilities,
        val linkProperties: LinkProperties?,
        val networkType: String,
        val ssid: String?
    )

    data class UnderlyingNetworks(
        val ipv4Net: List<NetworkProperties>,
        val ipv6Net: List<NetworkProperties>,
        val vpnRoutes: Pair<Boolean, Boolean>?,
        val useActive: Boolean,
        val minMtu: Int,
        var isActiveNetworkMetered: Boolean,
        var isActiveNetworkCellular: Boolean,
        val activeSsid: String?,
        var lastUpdated: Long,
        val dnsServers: Map<InetAddress, Network>,
        var vpnLockdown: Boolean = false
    )

    data class IpsAndUrlToProbe(
        val ip4probes: Collection<String>,
        val ip6probes: Collection<String>,
        val url4Probe: Collection<String>,
        val url6Probe: Collection<String>
    )
}
