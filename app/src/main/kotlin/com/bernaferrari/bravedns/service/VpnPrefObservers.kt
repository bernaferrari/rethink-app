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

import Logger
import Logger.LOG_TAG_VPN
import android.content.Context
import android.os.SystemClock.elapsedRealtime
import android.widget.Toast
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.AppInfo
import com.bernaferrari.bravedns.net.go.GoVpnAdapter
import com.bernaferrari.bravedns.util.InternetProtocol
import com.bernaferrari.bravedns.util.Utilities.isAtleastQ
import com.bernaferrari.bravedns.util.Utilities.isPlayStoreFlavour
import com.bernaferrari.bravedns.util.Utilities.showToastUiCentered
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * PersistentState / app-list / Orbot / DNSCrypt-relay collectors for [BraveVPNService].
 */
class VpnPrefObservers(
    private val context: Context,
    private val persistentState: PersistentState,
    private val appConfig: AppConfig,
    private val scope: CoroutineScope,
    private val adapter: () -> GoVpnAdapter?,
    private val excludedApps: () -> MutableSet<String>,
    private val requestRestart: (String) -> Unit,
    private val dnsTransport: VpnDnsTransportController,
    private val notifyForeground: () -> Unit,
    private val registerAccessibility: () -> Unit,
    private val unregisterAccessibility: () -> Unit,
    private val notifyConnectionMonitor: () -> Unit,
    private val onTunPolicyChanged: () -> Unit,
    private val currentMtu: () -> Int,
    private val handleBubbleIfNeeded: () -> Unit,
    private val newTunnelOptions: () -> AppConfig.TunnelOptions,
    private val onOrbotStatus: (Boolean) -> Unit,
    private val logd: (String) -> Unit,
) {
    companion object {
        private const val MAX_MTU: Int = 10000
    }

    private var appInfoCollectorJob: Job? = null
    private var orbotStartStatusCollectorJob: Job? = null
    private var dnscryptRelayCollectorJob: Job? = null
    private var preferenceKeyCollectorJob: Job? = null
    private var coreObserversRegistered = false

    fun register() {
        if (coreObserversRegistered) return
        appInfoCollectorJob = FirewallManager.appListFlow()
            .onEach { handleAppInfoChanges(it) }
            .launchIn(scope)
        preferenceKeyCollectorJob = persistentState.preferenceKeyChanges()
            .onEach { handlePreferenceKeyChanged(it) }
            .launchIn(scope)
        orbotStartStatusCollectorJob = persistentState.orbotConnectionStatus
            .onEach { onOrbotStatus(it) }
            .launchIn(scope)
        dnscryptRelayCollectorJob = persistentState.dnsCryptRelays
            .onEach { handleDnsCryptRelayChange(it) }
            .launchIn(scope)
        coreObserversRegistered = true
        Logger.i(LOG_TAG_VPN, "observe pref, dnscrypt relay, app list changes")
    }

    fun unregister() {
        appInfoCollectorJob?.cancel()
        appInfoCollectorJob = null
        orbotStartStatusCollectorJob?.cancel()
        orbotStartStatusCollectorJob = null
        dnscryptRelayCollectorJob?.cancel()
        dnscryptRelayCollectorJob = null
        preferenceKeyCollectorJob?.cancel()
        preferenceKeyCollectorJob = null
        coreObserversRegistered = false
    }

    private fun handleDnsCryptRelayChange(t: PersistentState.DnsCryptRelayDetails?) {
        if (t == null) return
        io("dnscryptRelay") {
            if (t.added) {
                adapter()?.addDnscryptRelay(t.relay)
            } else {
                adapter()?.removeDnscryptRelay(t.relay)
            }
        }
    }

    private fun handleAppInfoChanges(t: Collection<AppInfo>) {
        try {
            var latestExcludedApps: Set<String>
            // adding synchronized block, found a case of concurrent modification
            // exception that happened once when trying to filter the received object (t).
            // creating a copy of the received value in a synchronized block.
            synchronized(t) {
                val copy: List<AppInfo> = mutableListOf<AppInfo>().apply { addAll(t) }
                latestExcludedApps =
                    copy
                        .filter {
                            it.firewallStatus == FirewallManager.FirewallStatus.EXCLUDE.id
                        }
                        .map(AppInfo::packageName)
                        .toSet()
            }

            if (excludedApps() == latestExcludedApps) return

            Logger.i(LOG_TAG_VPN, "excluded-apps list changed, restart vpn")

            val reason = "excludeApps: ${latestExcludedApps.size} apps, at: ${elapsedRealtime()}"
            requestRestart(reason)
        } catch (e: ConcurrentModificationException) {
            Logger.e(
                LOG_TAG_VPN,
                "error retrieving value from appInfos observer ${e.message}",
                e
            )
        } catch (e: NoSuchElementException) {
            Logger.e(
                LOG_TAG_VPN,
                "error retrieving value from appInfos observer ${e.message}",
                e
            )
        }
    }

    private fun handlePreferenceKeyChanged(key: String) {
        /* TODO Check on the Persistent State variable
        Check on updating the values for Package change and for mode change.
        As of now handled manually */
        logd("on pref change, key: $key")
        when (key) {
            PersistentState.BRAVE_MODE -> {
                io("braveModeChange") {
                    // change in brave mode, requires restart of the vpn (to set routes in vpn),
                    // tunMode (to set the tun mode), and dnsAlg (to update the dns alg) in go
                    val reason = "braveMode: ${appConfig.getBraveMode()}}"
                    requestRestart(reason)
                    setTunMode()
                    updateDnsAlg()
                }
                notifyForeground()
            }

            PersistentState.LOCAL_BLOCK_LIST -> {
                io("localBlocklistEnable") { setRDNS() }
            }

            PersistentState.LOCAL_BLOCK_LIST_UPDATE -> {
                io("localBlocklistDownload") { setRDNS() }
            }

            PersistentState.BACKGROUND_MODE -> {
                if (persistentState.getBlockAppWhenBackground()) {
                    registerAccessibility()
                } else {
                    unregisterAccessibility()
                }
            }

            PersistentState.LOCAL_BLOCK_LIST_STAMP -> { // update on local blocklist stamp change
                spawnLocalBlocklistStampUpdate()
            }

            PersistentState.REMOTE_BLOCKLIST_UPDATE -> {
                io("remoteBlocklistUpdate") {
                    dnsTransport.addTransport()
                    setRDNS()
                }
            }

            PersistentState.DNS_CHANGE -> {
                io("dnsChange") {
                    dnsTransport.onDnsTypeChanged()
                }
            }

            PersistentState.PREVENT_DNS_LEAKS -> {
                io("preventDnsLeaks") { setTunMode() }
            }

            PersistentState.ALLOW_BYPASS -> {
                val reason = "allowBypass: ${persistentState.allowBypass}"
                requestRestart(reason)
            }

            PersistentState.PROXY_TYPE -> {
                io("proxy") {
                    handleProxyChange()
                    // if any proxy is set, then disable builder.allowByPass as false
                    disableAllowBypassIfNeeded()
                }
                notifyForeground()
            }

            PersistentState.NETWORK -> {
                Logger.i(LOG_TAG_VPN, "network change, ${persistentState.useMultipleNetworks}")
                io("useAllNetworks") { notifyConnectionMonitor() }
            }

            PersistentState.NOTIFICATION_ACTION -> {
                notifyForeground()
            }

            PersistentState.BIOMETRIC_AUTH -> {
                // update the notification builder to show the action buttons based on the biometric
                notifyForeground()
            }

            PersistentState.INTERNET_PROTOCOL -> {
                io("chooseIpVersion") { handleIPProtoChanges() }
            }

            PersistentState.PROTOCOL_TRANSLATION -> {
                io("forceV4Egress") { setTunMode() }
            }

            PersistentState.DEFAULT_DNS_SERVER -> {
                io("defaultDnsServer") {
                    logd(
                        "default transport server changed, change: ${persistentState.defaultDnsUrl}"
                    )
                    if (!dnsTransport.isDefaultDnsNone()) {
                        adapter()?.addDefaultTransport(persistentState.defaultDnsUrl)
                    } else {
                        dnsTransport.setNetworkAndDefaultDnsIfNeeded(forceUpdate = true)
                    }
                }
            }

            PersistentState.PCAP_MODE -> {
                io("pcap") { setPcapMode() }
            }

            PersistentState.DNS_ALG -> {
                io("dnsAlg") { updateDnsAlg() }
            }

            PersistentState.PRIVATE_IPS -> {
                // restart vpn to enable/disable route lan traffic
                val reason = "routeLanTraffic: ${persistentState.privateIps}"
                requestRestart(reason)
            }

            PersistentState.RETHINK_IN_RETHINK -> {
                // restart vpn to allow/disallow rethink traffic in rethink
                io("routeRethinkInRethink") {
                    val reason = "routeRethinkInRethink: ${persistentState.routeRethinkInRethink}"
                    requestRestart(reason)
                    adapter()?.notifyLoopback()
                    dnsTransport.setNetworkAndDefaultDnsIfNeeded(forceUpdate = true)
                }
            }

            PersistentState.CONNECTIVITY_CHECKS -> {
                Logger.i(
                    LOG_TAG_VPN,
                    "connectivity checks changed, ${persistentState.connectivityChecks}"
                )
                io("connectivityChecks") { notifyConnectionMonitor() }
            }

            PersistentState.NOTIFICATION_PERMISSION -> {
                if (persistentState.shouldRequestNotificationPermission) {
                    Logger.i(LOG_TAG_VPN, "notification permission allowed, show notification")
                    notifyForeground()
                } else {
                    // no-op
                }
            }

            PersistentState.EXCLUDE_APPS_IN_PROXY -> {
                // restart vpn to exclude apps if either proxy or dns proxy is enabled
                if (appConfig.isProxyEnabled() || appConfig.isDnsProxyActive()) {
                    val reason = "excludeAppsInProxy: ${persistentState.excludeAppsInProxy}"
                    requestRestart(reason)
                } else {
                    // no-op, no need to restart vpn as no proxy/dns proxy is enabled
                }
            }

            PersistentState.ANTI_CENSORSHIP_TYPE -> {
                io("antiCensorship") {
                    setDialStrategy()
                }
            }

            PersistentState.RETRY_STRATEGY -> {
                io("retryStrategy") {
                    setDialStrategy()
                }
            }
            PersistentState.ENDPOINT_INDEPENDENCE -> {
                io("endpointIndependence") {
                    setTransparency()
                }
            }
            PersistentState.TCP_KEEP_ALIVE -> {
                io("tcpKeepAlive") {
                    setDialStrategy()
                }
            }
            PersistentState.USE_SYSTEM_DNS_FOR_UNDELEGATED_DOMAINS -> {
                io("useSystemDnsForUndelegatedDomains") {
                    undelegatedDomains()
                }
            }
            PersistentState.NETWORK_ENGINE_EXPERIMENTAL -> {
                io("networkEngineExperimental") {
                    setExperimentalSettings(persistentState.nwEngExperimentalFeatures)
                }
            }
            PersistentState.USE_RPN -> {
                io("rpnUpdated") { }
            }
            PersistentState.RPN_MODE -> {
                io("rpnMode") { }
            }
            PersistentState.DIAL_TIMEOUT_SEC -> {
                io("tunTimeout") {
                    setDialStrategy()
                }
            }
            PersistentState.AUTO_DIALS_PARALLEL -> {
                io("autoDialsParallel") {
                    setAutoDialsParallel()
                }
            }
            PersistentState.STALL_ON_NO_NETWORK -> {
                io("stallOnNoNetwork") {
                    notifyConnectionMonitor()
                }
                val reason = "stallOnNoNetwork: ${persistentState.stallOnNoNetwork}"
                requestRestart(reason)
            }
            PersistentState.TUN_NETWORK_POLICY -> {
                io("tunNetworkPolicy") {
                    // notify connection monitor to update the network policy
                    Logger.i(LOG_TAG_VPN, "tun network policy changed, notify connection monitor")
                    onTunPolicyChanged()
                }
            }
            PersistentState.USE_MAX_MTU -> {
                io("useMaxMtu") {
                    val newMtu = if (persistentState.useMaxMtu && !persistentState.routeRethinkInRethink) {
                        MAX_MTU
                    } else {
                        currentMtu()
                    }
                    Logger.i(LOG_TAG_VPN, "use max mtu changed, new mtu: $newMtu")
                    val reason = "useMaxMtu: ${persistentState.useMaxMtu}"
                    requestRestart(reason)
                }
            }
            PersistentState.SET_VPN_BUILDER_TO_METERED -> {
                io("setVpnBuilderToMetered") {
                    Logger.i(LOG_TAG_VPN, "set vpn builder to metered: ${persistentState.setVpnBuilderToMetered}")
                    val reason = "setVpnBuilderToMetered: ${persistentState.setVpnBuilderToMetered}"
                    requestRestart(reason)
                }
            }
            PersistentState.PANIC_RANDOM -> {
                io("panicRandom") {
                    Logger.i(LOG_TAG_VPN, "panic random is unavailable in this Firestack build")
                }
            }
            PersistentState.AUTO_PROXY_ENABLED -> {
                io("seProxyEnabled") {
                    if (persistentState.autoProxyEnabled) {
                        adapter()?.registerSeProxyIfNeeded()
                    } else {
                        adapter()?.unregisterSeProxyIfNeeded()
                    }
                }
            }
            PersistentState.CUSTOM_LAN_MODE_IPS_CHANGED -> {
                if (persistentState.customModeOrIpChanged) {
                    io("customIpsChanged") {
                        val reason = "customIpsChanged: ${System.currentTimeMillis()}"
                        requestRestart(reason)
                    }
                } else {
                    // no-op
                }
            }

            PersistentState.FIREWALL_BUBBLE -> {
                if (isAtleastQ()) {
                    handleBubbleIfNeeded()
                }
            }
        }
    }

    private suspend fun setExperimentalSettings(experimental: Boolean) {
        Logger.i(LOG_TAG_VPN, "set experimental settings: $experimental")
    }

    private suspend fun undelegatedDomains() {
        Logger.i(LOG_TAG_VPN, "use system dns for undelegated domains: ${persistentState.useSystemDnsForUndelegatedDomains}")
        adapter()?.undelegatedDomains(persistentState.useSystemDnsForUndelegatedDomains)
    }

    private suspend fun setDialStrategy() {
        Logger.d(
            LOG_TAG_VPN,
            "set dial strategy: ${persistentState.dialStrategy}, retry: ${persistentState.retryStrategy}, tcpKeepAlive: ${persistentState.tcpKeepAlive}, timeout: ${persistentState.dialTimeoutSec}"
        )
        adapter()?.setDialStrategy()
        adapter()?.setAutoMode()
    }

    private suspend fun setAutoDialsParallel() {
        Logger.d(LOG_TAG_VPN, "set auto dials parallel: ${persistentState.autoDialsParallel}")
        adapter()?.setAutoDialsParallel()
    }

    private suspend fun setTransparency() {
        Logger.d(LOG_TAG_VPN, "set endpoint independence: ${persistentState.endpointIndependence}")
        adapter()?.setTransparency(persistentState.endpointIndependence)
    }

    private fun disableAllowBypassIfNeeded() {
        if (appConfig.isProxyEnabled() && persistentState.allowBypass) {
            Logger.i(LOG_TAG_VPN, "disabling allowBypass, as proxy is set.")
            // inform user about the change in allow bypass setting by showing a toast
            scope.launch(Dispatchers.Main) {
                val message =
                    context.getString(
                        R.string.toast_allow_bypass_disabled,
                        context.getString(R.string.settings_allow_bypass_heading)
                    )
                showToastUiCentered(context, message, Toast.LENGTH_LONG)
            }
            persistentState.allowBypass = false
        }
    }

    private suspend fun setRDNS() {
        logd("set brave dns mode, local/remote")
        adapter()?.setRDNS()
    }

    private fun handleIPProtoChanges() {
        Logger.i(LOG_TAG_VPN, "handle ip proto changes")
        if (InternetProtocol.isAuto(persistentState.internetProtocolType)) {
            // initiates connectivity checks if Auto mode and calls onNetworkConnected
            // or onNetworkDisconnected. onNetworkConnected may call restartVpn
            notifyConnectionMonitor()
        }
        val reason = "ipProto: ${persistentState.internetProtocolType}"
        requestRestart(reason)
    }

    private suspend fun handleProxyChange() {
        val tunProxyMode = appConfig.getTunProxyMode()
        val proxy = AppConfig.ProxyProvider.getProxyProvider(appConfig.getProxyProvider())
        Logger.i(LOG_TAG_VPN, "handle proxy change, proxy: $proxy, mode: $tunProxyMode")
        when (proxy) {
            AppConfig.ProxyProvider.NONE -> {
                // no-op
            }

            AppConfig.ProxyProvider.TCP -> {
            }

            AppConfig.ProxyProvider.WIREGUARD -> {
                // no need to set proxy for wireguard, as WireguardManager handles it
            }

            AppConfig.ProxyProvider.ORBOT -> {
                // update orbot config, its treated as SOCKS5 or HTTP proxy internally
                // orbot proxy requires app to be excluded from vpn, so restart vpn
                val reason = "orbotProxy: ${appConfig.isOrbotProxyEnabled()}"
                requestRestart(reason)
                adapter()?.setCustomProxy(tunProxyMode)
            }

            AppConfig.ProxyProvider.CUSTOM -> {
                // custom either means socks5 or http proxy
                // socks5 proxy requires app to be excluded from vpn, so restart vpn
                val isSocks5 = tunProxyMode == AppConfig.TunProxyMode.SOCKS5
                val reason = if (isSocks5) {
                    "customProxy: ${appConfig.getSocks5ProxyDetails()}"
                } else {
                    "customProxy: ${appConfig.getHttpProxyDetails()}"
                }
                requestRestart(reason)
                adapter()?.setCustomProxy(tunProxyMode)
            }
        }
    }

    private fun spawnLocalBlocklistStampUpdate() {
        if (isPlayStoreFlavour()) return

        io("dnsStampUpdate") { adapter()?.setRDNSStamp() }
    }

    private suspend fun updateDnsAlg() {
        adapter()?.setDnsAlg()
    }

    private suspend fun setPcapMode() {
        val pcapPath = appConfig.getPcapFilePath()
        Logger.i(LOG_TAG_VPN, "pcap mode enabled, path: $pcapPath")
        adapter()?.setPcapMode(pcapPath)
    }

    private fun setTunMode() {
        val opts = newTunnelOptions()
        Logger.i(
            LOG_TAG_VPN,
            "set tun mode with dns: ${opts.tunDnsMode}, firewall: ${opts.tunFirewallMode}, proxy: ${opts.tunProxyMode}, pt: ${opts.ptMode}"
        )
        adapter()?.setTunMode(opts)
    }

    private fun io(s: String, f: suspend () -> Unit) =
        scope.launch(CoroutineName(s) + Dispatchers.IO) { f() }
}
