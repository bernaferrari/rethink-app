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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.net.go.GoVpnAdapter
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Utilities.showToastUiCentered
import com.celzero.firestack.backend.Backend
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

/**
 * DNS-type dispatch and resolver/transport wiring for [BraveVPNService].
 */
class VpnDnsTransportController(
    private val context: Context,
    private val persistentState: PersistentState,
    private val appConfig: AppConfig,
    private val connectivityManager: ConnectivityManager,
    private val serializer: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val adapter: () -> GoVpnAdapter?,
    private val underlyingNetworks: () -> ConnectionMonitor.UnderlyingNetworks?,
    private val requestRestart: (String) -> Unit,
    private val getDnsStatus: suspend (String) -> Int?,
    private val logd: (String) -> Unit,
) {
    private var prevDns: MutableSet<InetAddress> = mutableSetOf()

    fun systemDnsSnapshot(): String = prevDns.joinToString()

    suspend fun addTransport() {
        // TODO: no need to call addTransport in case of relay changes, which we are doing now
        logd("handle transport change")
        adapter()?.addTransport()
    }

    suspend fun refreshResolvers() {
        Logger.i(LOG_TAG_VPN, "refresh resolvers")
        adapter()?.refreshResolvers()
    }

    suspend fun onDnsTypeChanged() {
        /*
         * Handles the DNS type changes.
         * DNS Proxy - Requires restart of the VPN.
         * DNSCrypt - Set the tunnel with DNSCrypt mode once the live servers size is not 0.
         * DOH - Overwrites the tunnel values with new values.
         */
        // FIXME: update just that dns proxy, not the entire tunnel
        when (appConfig.getDnsType()) {
            AppConfig.DnsType.DOH -> {
                addTransport()
            }

            AppConfig.DnsType.DNSCRYPT -> {
                addTransport()
            }

            AppConfig.DnsType.DNS_PROXY -> {
                val reason = "dnsProxy: ${appConfig.getSelectedDnsProxyDetails()?.id}"
                requestRestart(reason)
                addTransport()
            }

            AppConfig.DnsType.RETHINK_REMOTE -> {
                addTransport()
            }

            AppConfig.DnsType.SYSTEM_DNS -> {
                setNetworkAndDefaultDnsIfNeeded(forceUpdate = true)
            }

            AppConfig.DnsType.SMART_DNS -> {
                // no need to add multiple DoH as smart dns as it is expected to be
                // added by the vpn adapter while starting, but add it if it is missing
                if(getDnsStatus(Backend.Plus) == null) addTransport()
            }

            AppConfig.DnsType.DOT -> {
                addTransport()
            }

            AppConfig.DnsType.ODOH -> {
                addTransport()
            }
        }
    }

    fun isDefaultDnsNone(): Boolean {
        // if none is set then the url will either be empty or will not be one of the default dns
        return persistentState.defaultDnsUrl.isEmpty() ||
                !Constants.DEFAULT_DNS_LIST.any { it.url == persistentState.defaultDnsUrl }
    }

    suspend fun setNetworkAndDefaultDnsIfNeeded(forceUpdate: Boolean = false) {
        val ctx = context
        withContext(serializer) {
            val currNet = underlyingNetworks()
            // get dns servers from the first network or active network
            val active = connectivityManager.activeNetwork
            val dnsServers: MutableSet<InetAddress> =
            if (connectivityManager.getNetworkCapabilities(active)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                Logger.i(LOG_TAG_VPN, "active network is vpn, so no need get dns servers")
                mutableSetOf()
            } else {
                val lp = connectivityManager.getLinkProperties(active)
                // here dnsServers are validated with underlyingNetworks, so there may be a case
                // where v6 address is added when v6 network is not available
                // so, dnsServers will have both v4 and v6 addresses
                lp?.dnsServers?.toMutableSet() ?: mutableSetOf()
            }

            if (dnsServers.isEmpty()) {
                // first network is considered to be active network
                val ipv4 = currNet?.ipv4Net?.firstOrNull()
                val ipv6 = currNet?.ipv6Net?.firstOrNull()
                val dns4 = ipv4?.linkProperties?.dnsServers
                val dns6 = ipv6?.linkProperties?.dnsServers
                // if active network is not found in the list of networks, then use dns from
                // first network
                val dl = mutableSetOf<InetAddress>()
                // add all the dns servers from the first network, depending on the current
                // route, netstack will make use of the dns servers
                dns4?.let { dl.addAll(it) }
                dns6?.let { dl.addAll(it) }
                Logger.i(LOG_TAG_VPN, "dns servers for network: $dl")
                dnsServers.addAll(dl)
            } else {
                Logger.i(LOG_TAG_VPN, "dns servers for network: $dnsServers")
            }

            if (dnsServers.isEmpty()) {
                // TODO: send an alert/notification instead?
                Logger.w(LOG_TAG_VPN, "No system dns servers found")
                if (appConfig.isSystemDns()) {
                    // on null dns servers, show toast
                    scope.launch(Dispatchers.Main) {
                        showToastUiCentered(
                            ctx,
                            ctx.getString(R.string.system_dns_connection_failure),
                            Toast.LENGTH_LONG
                        )
                    }
                } else {
                    // no-op
                }
            }
            scope.launch(CoroutineName("setSystemAndDefaultDns") + Dispatchers.IO) {
                // ref: kotlinlang.org/docs/equality.html#structural-equality
                val same = dnsServers == prevDns

                Logger.i(
                    LOG_TAG_VPN,
                    "dns: $dnsServers, existing: $prevDns, force: $forceUpdate, same? $same"
                )
                if (same && !forceUpdate) {
                    return@launch
                }
                // set system dns whenever there is a change in network
                prevDns.clear()
                prevDns.addAll(dnsServers)
                val dns = dnsServers.map { it.hostAddress }
                adapter()?.setSystemDns(dns)
                // set default dns server for the tunnel if none is set
                if (isDefaultDnsNone()) {
                    val dnsCsv = dns.joinToString(",")
                    adapter()?.addDefaultTransport(dnsCsv)
                }

                val id = if (appConfig.isSmartDnsEnabled()) Backend.Plus else Backend.Preferred
                val mainDnsOK = adapter()?.getDnsStatus(id) != null
                Logger.i(LOG_TAG_VPN, "preferred/plus set? ${mainDnsOK}, if not set it again")

                if (!mainDnsOK) {
                    adapter()?.addTransport()
                }
            }
        }
    }
}
