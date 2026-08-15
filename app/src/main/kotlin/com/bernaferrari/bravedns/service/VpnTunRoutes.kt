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
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.IPUtil
import com.bernaferrari.bravedns.util.InternetProtocol
import com.bernaferrari.bravedns.util.KnownPorts
import com.bernaferrari.bravedns.util.Utilities.isNetworkSame
import inet.ipaddr.HostName
import inet.ipaddr.IPAddressString
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException
import java.util.Locale
import kotlin.math.min

data class TunEstablishResult(
    val fd: ParcelFileDescriptor?,
    val routes: Pair<Boolean, Boolean>,
    val stats: String,
)

/**
 * VPN interface addresses, DNS servers, and IPv4/IPv6 routes for [BraveVPNService].
 */
class VpnTunRoutes(
    private val persistentState: PersistentState,
    private val appConfig: AppConfig,
    private val connectivityManager: ConnectivityManager,
) {
    companion object {
        private const val TAG = "VpnService;"
        // changing the below ip should require a changes in ConnectionTracer, RethinkLogAdapter
        private const val IPV4_TEMPLATE: String = "10.111.222.%d"
        private const val IPV4_PREFIX_LENGTH: Int = 24
        private const val IPV6_TEMPLATE: String = "fd66:f83a:c650::%d"
        private const val IPV6_PREFIX_LENGTH: Int = 120
    }

    // The VPN service and tun2socks must agree on the layout of the network.  By convention, we
    // assign the following values to the final byte of an address within a subnet.
    // Value of the final byte, to be substituted into the template.
    private enum class LanIp(private val value: Int) {
        GATEWAY(1),
        ROUTER(2),
        DNS(3);

        fun make(template: String): String {
            val format = String.format(Locale.ROOT, template, value)
            return HostName(format).toString()
        }

        // accepts ip template and port number, converts into address or host with port
        // introduced IPAddressString, as IPv6 is not well-formed after appending port number
        // with the formatted(String.format) ip
        fun make(template: String, port: Int): String {
            val format = String.format(Locale.ROOT, template, value)
            // Hostname() accepts IPAddress, port(Int) as parameters
            return try {
                HostName(IPAddressString(format).address, port).toString()
            } catch (e: Exception) {
                Logger.e(LOG_TAG_VPN, "err make lan ip: ${e.message}", e)
                ""
            }
        }
    }

    fun determineRoutes(n: BraveVPNService.Networks): Pair<Boolean, Boolean> {
        var has6 = route6(n)
        var has4 = route4(n)

        val isAuto = InternetProtocol.isAuto(persistentState.internetProtocolType)
        // in auto mode, assume v4 route is available if only v6 route is available, which is true
        // for scenarios like 464Xlat and other 4to6 translation mechanisms
        if (BraveVPNService.ROUTE4IN6 && isAuto && (has6 && !has4)) {
            Logger.w(LOG_TAG_VPN, "Adding v4 route in v6-only network")
            has4 = true
        }

        if (!has4 && !has6 && !n.overlayNws.failOpen) {
            // When overlay networks has v6 routes but active network has v4 routes
            // both has4 and has6 will be false and fail-open may open up BOTH routes
            // What's desirable is for the active network route to take precedence, that is,
            // to only add v4 route in case of a mismatch. Failing open will falsely make
            // apps think the underlying active network is dual-stack when it is not causing
            // all sorts of delays (due to happy eyeballs).
            // fixme: this code doesn't seem to be doing anything, because route4 and route6
            // will always be false
            val n2 = BraveVPNService.Networks(n.underlyingNws, /*fail-open overlay*/ BraveVPNService.OverlayNetworks())
            has4 = route4(n2)
            has6 = route6(n2)
        }
        if (!has4 && !has6) {
            val failOpen = !persistentState.stallOnNoNetwork
            // no route available for both v4 and v6, add all routes
            // connectivity manager is expected to retry when no route is available
            // see ConnectionMonitor#repopulateTrackedNetworks
            Logger.i(LOG_TAG_VPN, "No routes, fail-open? $failOpen")
            has4 = failOpen
            has6 = failOpen
        } else {
            Logger.i(LOG_TAG_VPN, "Building vpn for v4? $has4, v6? $has6")
        }

        return Pair(has4, has6)
    }

    fun establish(builder: VpnService.Builder, networks: BraveVPNService.Networks, mtu: Int): TunEstablishResult {
        var routes = Pair(false, false)
        var stats = ""
        try {
            val s = StringBuilder()

            val (has4, has6) = determineRoutes(networks)

            // TODO: do we need to still exclude the routes in case of noRoutes?
            val noRoutes = !has4 && !has6

            routes = Pair(has4, has6)
            val dnsMode = appConfig.getBraveMode().isDnsActive()
            val firewallMode = appConfig.getBraveMode().isFirewallActive()

            var b = builder
            // setup the gateway addr
            if (has4 || noRoutes) {
                b = addIfAddress4(b)
            }
            if (has6 || noRoutes) {
                b = addIfAddress6(b)
            }

            if (dnsMode) {
                // setup dns addrs and dns routes
                if (has4) {
                    b = addDnsRoute4(b)
                    b = addDnsServer4(b)
                }
                if (has6) {
                    b = addDnsRoute6(b)
                    b = addDnsServer6(b)
                }
            }
            if (firewallMode) {
                // setup catch-all / default routes
                if (has4) {
                    b = addRoute4(b)
                }
                if (has6) {
                    b = addRoute6(b)
                }
            } else {
                // when not routing all traffic (firewall inactive) allow v4/v6 families
                // to be routed based on the underlying network (bypassing the tunnel)
                Logger.i(LOG_TAG_VPN, "dns-only mode, allowFamily: v4: $has4, v6: $has6")
                if (has4 && !noRoutes) {
                    b.allowFamily(android.system.OsConstants.AF_INET)
                }
                if (has6 && !noRoutes) {
                    b.allowFamily(android.system.OsConstants.AF_INET6)
                }
            }

            // nw engine expects the fd to be non-blocking
            // builder.setBlocking(false)

            Logger.i(
                LOG_TAG_VPN,
                "$TAG; establish vpn, mtu: $mtu, has4: $has4, has6: $has6, noRoutes: $noRoutes, dnsMode? $dnsMode, firewallMode? $firewallMode"
            )

            s.append("mtu: $mtu\n   has4: $has4\n   has6: $has6\n   noRoutes: $noRoutes\n   dnsMode? $dnsMode\n   firewallMode? $firewallMode")
            stats = s.toString()
            return TunEstablishResult(b.establish(), routes, stats)
        } catch (e: Exception) {
            Logger.crash(LOG_TAG_VPN, e.message ?: "err establishVpn", e)
            return TunEstablishResult(null, routes, stats)
        }
    }

    fun dnsLanIps(): Pair<String, String> {
        return Pair(LanIp.DNS.make(IPV4_TEMPLATE), LanIp.DNS.make(IPV6_TEMPLATE))
    }

    fun fakeDns(): String {
        val isCustomLanDns = persistentState.customLanIpMode
        if (isCustomLanDns) {
            val customDnsV4 = persistentState.customLanDnsIpv4
            val customDnsV6 = persistentState.customLanDnsIpv6
            Logger.i(
                LOG_TAG_VPN,
                "getFakeDns: using custom dns ips: v4: $customDnsV4, v6: $customDnsV6"
            )
            val ipv4 = customDnsV4.split(",") .joinToString(",") {
                val ipParts = it.split("/")
                HostName(IPAddressString(ipParts[0]).address, KnownPorts.DNS_PORT).toString()
            }
            val ipv6 = customDnsV6.split(",") .joinToString(",") {
                val ipParts = it.split("/")
                HostName(IPAddressString(ipParts[0]).address, KnownPorts.DNS_PORT).toString()
            }
            return "$ipv4,$ipv6"
        } else {
            val ipv4 = LanIp.DNS.make(IPV4_TEMPLATE, KnownPorts.DNS_PORT)
            val ipv6 = LanIp.DNS.make(IPV6_TEMPLATE, KnownPorts.DNS_PORT)
            // now fakedns will be only set during first time vpn is started, so set both ipv4 and ipv6
            // addresses, so that if the network changes doesn't affect
            return "$ipv4,$ipv6"
        }
    }

    fun addresses(): String {
        val isCustomLanIp = persistentState.customLanIpMode
        if (isCustomLanIp) {
            val customGatewayV4 = persistentState.customLanGatewayIpv4
            val customGatewayV6 = persistentState.customLanGatewayIpv6
            Logger.i(
                LOG_TAG_VPN,
                "getAddresses: using custom gateway ips: v4: $customGatewayV4, v6: $customGatewayV6"
            )
            val ip4Parts = customGatewayV4.split("/")
            val prefix4 = ip4Parts[1].toIntOrNull() ?: IPV4_PREFIX_LENGTH
            val ipv4 = IPAddressString("${ip4Parts[0]}/$prefix4").address.toNormalizedString()
            val ip6Parts = customGatewayV6.split("/")
            val prefix6 = ip6Parts[1].toIntOrNull() ?: IPV6_PREFIX_LENGTH
            val ipv6 = IPAddressString("${ip6Parts[0]}/$prefix6").address.toNormalizedString()
            return "$ipv4,$ipv6"
        } else {
            val ipv4 = IPAddressString("${LanIp.GATEWAY.make(IPV4_TEMPLATE)}/$IPV4_PREFIX_LENGTH")
            val ipv6 = IPAddressString("${LanIp.GATEWAY.make(IPV6_TEMPLATE)}/$IPV6_PREFIX_LENGTH")
            return "${ipv4.address.toNormalizedString()},${ipv6.address.toNormalizedString()}"
        }
    }

    private fun route6(nws: BraveVPNService.Networks): Boolean {
        return when (appConfig.getInternetProtocol()) {
            InternetProtocol.IPv4 -> {
                false
            }

            InternetProtocol.IPv6 -> {
                true
            }

            InternetProtocol.ALWAYSv46 -> {
                true
            }

            InternetProtocol.IPv46 -> {
                // null overlayNetwork means no active wireguard network, default to true so
                // that the route is added based on the underlying network
                val overlayIpv6 = nws.overlayNws.has6 || nws.overlayNws.failOpen

                val underlay = nws.underlyingNws
                // when no underlying-networks are unknown, or if use-multiple-networks is enabled,
                // simply check whether there are ANY v6 networks available; otherwise, if the vpn
                // must only use the active-network (always the first network in allNet), then check
                // if active-network has v6 connectivity (that is, it must be present in ipv6Net).
                // check if isReachable is true, if not, don't need to add route for v6 (return
                // false)
                Logger.d(LOG_TAG_VPN, "$TAG r6: underlay: ${underlay?.useActive}, ${underlay?.ipv6Net?.size}")
                if (underlay?.useActive != true) {
                    val underlayIpv6 = (underlay?.ipv6Net?.size ?: 0) > 0
                    return if (!underlayIpv6) {
                        Logger.i(LOG_TAG_VPN, "r6: No IPv6 networks available")
                        false
                    } else {
                        Logger.i(LOG_TAG_VPN, "r6: IPv6 available, overlay: $overlayIpv6")
                        // underlay network is available, check if overlay network is available
                        overlayIpv6
                    }
                } else {
                    val activeNetwork = connectivityManager.activeNetwork

                    if (activeNetwork == null) {
                        Logger.i(LOG_TAG_VPN, "r6: missing active network, use the first network")
                        return underlay.ipv6Net.isNotEmpty() && overlayIpv6
                    }
                    underlay.ipv6Net.forEach {
                        val underlayIpv6 = isNetworkSame(it.network, activeNetwork)
                        if (underlayIpv6) {
                            Logger.i(LOG_TAG_VPN, "r6: Active network ok: ov: $overlayIpv6")
                            // underlay network is available, check if overlay network is available
                            return overlayIpv6
                        }
                    }
                    Logger.i(LOG_TAG_VPN, "r6: active network not available")
                    return false
                }
            }
        }
    }

    private fun route4(nws: BraveVPNService.Networks): Boolean {
        return when (appConfig.getInternetProtocol()) {
            InternetProtocol.IPv4 -> {
                true
            }

            InternetProtocol.IPv6 -> {
                false
            }

            InternetProtocol.ALWAYSv46 -> {
                true
            }

            InternetProtocol.IPv46 -> {
                // null overlayNetwork means no active wireguard network, default to true so
                // that the route is added based on the underlying network
                val overlayIpv4 = nws.overlayNws.has4 || nws.overlayNws.failOpen

                val underlay = nws.underlyingNws
                // when no underlying-networks are unknown, or if use-multiple-networks is enabled,
                // simply check whether there are ANY v4 networks available; otherwise, if the vpn
                // must only use the active-network (always the first network in allNet), then check
                // if active-network has v4 connectivity (that is, it must be present in ipv4Net).
                // check if isReachable is true, if not, don't need to add route for v4 (return
                // false)
                Logger.d(LOG_TAG_VPN, "$TAG r4: useActive? ${underlay?.useActive}, sz: ${underlay?.ipv4Net?.size}")
                if (underlay?.useActive != true) {
                    val underlayIpv4 = (underlay?.ipv4Net?.size ?: 0) > 0
                    if (!underlayIpv4) {
                        Logger.i(LOG_TAG_VPN, "r4: No IPv4 networks available")
                        return false
                    } else {
                        Logger.i(LOG_TAG_VPN, "r4: IPv4 networks available")
                        // underlay network is available, check if overlay network is available
                        return overlayIpv4
                    }
                } else {
                    val activeNetwork = connectivityManager.activeNetwork
                    if (activeNetwork == null) {
                        Logger.i(LOG_TAG_VPN, "r4: missing active network, use the first network")
                        return underlay.ipv4Net.isNotEmpty() && overlayIpv4
                    }

                    underlay.ipv4Net.forEach {
                        val underlayIpv4 = isNetworkSame(it.network, activeNetwork)
                        if (underlayIpv4) {
                            Logger.i(LOG_TAG_VPN, "r4: reachable, ov: $overlayIpv4")
                            // underlay network is available, check if overlay network is available
                            return overlayIpv4
                        }
                    }
                    return false
                }
            }
        }
    }

    private fun addRoute6(b: VpnService.Builder): VpnService.Builder {
        // TODO: as of now, vpn lockdown mode is not handled, check if this is required
        if (persistentState.privateIps) {
            Logger.i(LOG_TAG_VPN, "addRoute6: privateIps is true, adding routes")
            // exclude LAN traffic, add only unicast routes
            // add only unicast routes
            // range 0000:0000:0000:0000:0000:0000:0000:0000-
            // 0000:0000:0000:0000:ffff:ffff:ffff:ffff
            // fixme: see if the ranges overlap with the default route
            b.addRoute("0000::", 64)
            b.addRoute("2000::", 3) // 2000:: - 3fff::
            b.addRoute("4000::", 3) // 4000:: - 5fff::
            b.addRoute("6000::", 3) // 6000:: - 7fff::
            b.addRoute("8000::", 3) // 8000:: - 9fff::
            b.addRoute("a000::", 3) // a000:: - bfff::
            b.addRoute("c000::", 3) // c000:: - dfff::
            b.addRoute("e000::", 4) // e000:: - efff::
            b.addRoute("f000::", 5) // f000:: - f7ff::
            b.addRoute("64:ff9b:1::", 48) // RFC8215/alg
            b.addRoute("64:ff9b::", 96) // RFC6052/dns64

            // b.addRoute("f800::", 6) // unicast routes
            // b.addRoute("fe00::", 9) // unicast routes
            // b.addRoute("ff00::", 8) // multicast routes
            // not considering 100::/64 and other reserved ranges
        } else {
            // no need to exclude LAN traffic, add default route which is ::/0
            Logger.i(LOG_TAG_VPN, "addRoute6: privateIps is false, adding default route")
            b.addRoute(Constants.UNSPECIFIED_IP_IPV6, Constants.UNSPECIFIED_PORT)
        }

        return b
    }

    private fun addRoute4(b: VpnService.Builder): VpnService.Builder {
        // TODO: as of now, vpn lockdown mode is not handled, check if this is required
        if (persistentState.privateIps) {
            Logger.i(LOG_TAG_VPN, "addRoute4: privateIps is true, adding routes")
            // https://developer.android.com/reference/android/net/VpnService.Builder.html#addRoute(java.lang.String,%20int)
            // Adds a route to the VPN's routing table. The VPN will forward all traffic to the
            // destination through the VPN interface. The destination is specified by address and
            // prefixLength.
            // ref: github.com/celzero/rethink-app/issues/26
            // github.com/M66B/NetGuard/blob/master/app/src/main/kotlin/eu/faircode/netguard/ServiceSinkhole.java#L1276-L1353
            val ipsToExclude: MutableList<IPUtil.CIDR> = ArrayList()

            // loopback
            ipsToExclude.add(IPUtil.CIDR("127.0.0.0", 8))
            // lan: tools.ietf.org/html/rfc1918
            ipsToExclude.add(IPUtil.CIDR("10.0.0.0", 8))
            ipsToExclude.add(IPUtil.CIDR("172.16.0.0", 12))
            ipsToExclude.add(IPUtil.CIDR("192.168.0.0", 16))
            // link local
            ipsToExclude.add(IPUtil.CIDR("169.254.0.0", 16))
            // Broadcast
            ipsToExclude.add(IPUtil.CIDR("224.0.0.0", 3))

            ipsToExclude.sort()

            try {
                var start: InetAddress? = InetAddress.getByName(Constants.UNSPECIFIED_IP_IPV4)
                ipsToExclude.forEach { exclude ->
                    val include = IPUtil.toCIDR(start, IPUtil.minus1(exclude.start)!!)
                    include?.forEach {
                        try {
                            it.address?.let { it1 -> b.addRoute(it1, it.prefix) }
                        } catch (ex: Exception) {
                            Logger.e(LOG_TAG_VPN, "exception while adding route: ${ex.message}", ex)
                        }
                    }
                    start = IPUtil.plus1(exclude.end)
                }
            } catch (ex: SocketException) {
                Logger.e(LOG_TAG_VPN, "addRoute4: ${ex.message}", ex)
            } catch (ex: UnknownHostException) {
                Logger.e(LOG_TAG_VPN, "addRoute4: ${ex.message}", ex)
            }
            if (persistentState.customLanIpMode) {
                // gateway
                val gatewayIp4 = persistentState.customLanGatewayIpv4
                val ipParts = gatewayIp4.split("/")
                val gwIp4 = ipParts[0]
                val prefixGwIp4 = ipParts[1].toIntOrNull() ?: 32
                b.addRoute(HostName(gwIp4).toString(), prefixGwIp4)
                // dns
                val customDns4 = persistentState.customLanDnsIpv4
                val dnsIpParts = customDns4.split("/")
                val dnsIp4 = dnsIpParts[0]
                val prefixDnsIp4 = dnsIpParts[1].toIntOrNull() ?: 32
                b.addRoute(HostName(dnsIp4).toString(), prefixDnsIp4)
                // router
                val router4 = persistentState.customLanRouterIpv4
                val routerIpParts = router4.split("/")
                val routerIp4 = routerIpParts[0]
                val prefixRouterIp4 = routerIpParts[1].toIntOrNull() ?: 32
                b.addRoute(HostName(routerIp4).toString(), prefixRouterIp4)
            } else {
                b.addRoute(LanIp.GATEWAY.make(IPV4_TEMPLATE), 32)
                b.addRoute(LanIp.DNS.make(IPV4_TEMPLATE), 32)
                b.addRoute(LanIp.ROUTER.make(IPV4_TEMPLATE), 32)
            }
        } else {
            Logger.i(LOG_TAG_VPN, "addRoute4: privateIps is false, adding default route")
            // no need to exclude LAN traffic, add default route which is 0.0.0.0/0
            b.addRoute(Constants.UNSPECIFIED_IP_IPV4, Constants.UNSPECIFIED_PORT)
        }

        return b
    }

    private fun addIfAddress4(b: VpnService.Builder): VpnService.Builder {
        val isCustomGateway = persistentState.customLanIpMode
        if (isCustomGateway) {
            val customIp = persistentState.customLanGatewayIpv4
            Logger.i(LOG_TAG_VPN, "addIfAddress4: using custom gateway ip: $customIp")
            // split the address and prefix length "$ip/$prefix"
            val ip4Parts = customIp.split("/")
            val ip4 = HostName(ip4Parts[0]).toString()
            b.addAddress(ip4, ip4Parts[1].toIntOrNull() ?: IPV4_PREFIX_LENGTH)
        } else {
            b.addAddress(LanIp.GATEWAY.make(IPV4_TEMPLATE), IPV4_PREFIX_LENGTH)
        }
        return b
    }

    private fun addIfAddress6(b: VpnService.Builder): VpnService.Builder {
        val isCustomGateway = persistentState.customLanIpMode
        if (isCustomGateway) {
            val customIp = persistentState.customLanGatewayIpv6
            Logger.i(LOG_TAG_VPN, "addIfAddress6: using custom gateway ip: $customIp")
            // split the address and prefix length "$ip/$prefix"
            val ipParts = customIp.split("/")
            val ip6 = HostName(ipParts[0]).toString()
            b.addAddress(ip6, ipParts[1].toIntOrNull() ?: IPV6_PREFIX_LENGTH)
        } else {
            b.addAddress(LanIp.GATEWAY.make(IPV6_TEMPLATE), IPV6_PREFIX_LENGTH)
        }
        return b
    }

    private fun addDnsServer4(b: VpnService.Builder): VpnService.Builder {
        val isCustomLanDns = persistentState.customLanIpMode
        if (isCustomLanDns) {
            val customDns = persistentState.customLanDnsIpv4
            Logger.i(LOG_TAG_VPN, "addDnsServer4: using custom dns ip: $customDns")
            val dnsIp = customDns.split("/")
            val dns = HostName(dnsIp[0]).toString()
            b.addDnsServer(dns)
        } else {
            b.addDnsServer(LanIp.DNS.make(IPV4_TEMPLATE))
        }
        return b
    }

    private fun addDnsServer6(b: VpnService.Builder): VpnService.Builder {
        val isCustomLanDns = persistentState.customLanIpMode
        if (isCustomLanDns) {
            val customDns = persistentState.customLanDnsIpv6
            Logger.i(LOG_TAG_VPN, "addDnsServer6: using custom dns ip: $customDns")
            val dnsIp = customDns.split("/")
            val dns = HostName(dnsIp[0]).toString()
            b.addDnsServer(dns)
        } else {
            b.addDnsServer(LanIp.DNS.make(IPV6_TEMPLATE))
        }
        return b
    }

    private fun addDnsRoute4(b: VpnService.Builder): VpnService.Builder {
        val isCustomLanDns = persistentState.customLanIpMode
        if (isCustomLanDns) {
            val customDns = persistentState.customLanDnsIpv4
            Logger.i(LOG_TAG_VPN, "addDnsRoute4: using custom dns ip: $customDns")
            val ipParts = customDns.split("/")
            val ip = HostName(ipParts[0]).toString()
            b.addRoute(ip, ipParts[1].toIntOrNull() ?: 32)
        } else {
            b.addRoute(LanIp.DNS.make(IPV4_TEMPLATE), 32)
        }
        return b
    }

    // builder.addRoute() when the app is in DNS only mode
    private fun addDnsRoute6(b: VpnService.Builder): VpnService.Builder {
        val isCustomLanDns = persistentState.customLanIpMode
        if (isCustomLanDns) {
            val customDns = persistentState.customLanDnsIpv6
            Logger.i(LOG_TAG_VPN, "addDnsRoute6: using custom dns ip: $customDns")
            val ipParts = customDns.split("/")
            val ip = HostName(ipParts[0]).toString()
            b.addRoute(ip, ipParts[1].toIntOrNull() ?: 128)
        } else {
            b.addRoute(LanIp.DNS.make(IPV6_TEMPLATE), 128)
        }
        return b
    }

    fun getUnderlays(
        networks: ConnectionMonitor.UnderlyingNetworks?,
        isLockdown: Boolean,
        netid: (Long) -> Long,
    ): Array<Network>? {
        val failOpen = !persistentState.stallOnNoNetwork
        val setNullOnVpnLockdown = false
        // always check lockdown from isLockdown() as it is the original source of truth
        val currentlyLockdown = isLockdown
        val mustSetNullOnVpnLockdown = currentlyLockdown && setNullOnVpnLockdown
        if (networks == null) {
            Logger.w(LOG_TAG_VPN, "getUnderlays: null nws; fail-open? $failOpen, must-set-null? $mustSetNullOnVpnLockdown, lockdown? $currentlyLockdown")
            return if (failOpen || mustSetNullOnVpnLockdown) { // failing open on no nw / lockdown
                null // use whichever network is active, whenever it becomes active
            } else {
                emptyArray() // deny all traffic; fail closed
            }
        }

        // underlying networks is set to null, which prompts Android to set it to whatever is
        // the current active network. Later, ConnectionMonitor#onVpnStarted, depending on user
        // chosen preferences, sets appropriate underlying network/s.

        // add ipv4/ipv6 networks to the tunnel
        val allNetworks = networks.ipv4Net.map { it.network } + networks.ipv6Net.map { it.network }
        // remove duplicates, as the same network can be both ipv4 and ipv6
        val distinctNetworks = allNetworks.distinctBy { it.networkHandle }
        val hasUnderlyingNetwork = distinctNetworks.isNotEmpty()
        val underlays = if (hasUnderlyingNetwork) {
            if (networks.useActive) {
                null // null denotes active network
            } else {
                distinctNetworks.toTypedArray() // use all networks
            }
        } else {
            // failing open on no nw / lockdown
            if (failOpen || mustSetNullOnVpnLockdown) {
                null // use whichever network is active, whenever it becomes active
            } else {
                emptyArray() // deny all traffic; fail closed
            }
        }

        Logger.i(
            LOG_TAG_VPN,
            "getUnderlays: use active? ${networks.useActive}; fail-open? $failOpen; lockdown? $mustSetNullOnVpnLockdown; networks: ${underlays?.size}; null-underlay? ${underlays == null}"
        )
        if (!hasUnderlyingNetwork) {
            Logger.w(LOG_TAG_VPN, "getUnderlays: no underlying networks found")
        } else {
            underlays?.forEach {
                Logger.i(
                    LOG_TAG_VPN,
                    "getUnderlays: network: ${it.networkHandle}, netId: ${netid(it.networkHandle)}"
                )
            }
        }
        return underlays
    }

    fun interestingNetworkChanges(
        old: ConnectionMonitor.UnderlyingNetworks?,
        incoming: ConnectionMonitor.UnderlyingNetworks?,
        aux: BraveVPNService.OverlayNetworks,
        overlayMtu: Int,
        tunMtu: Int,
        builderRoutes: Pair<Boolean, Boolean>,
        reconcileWithVpnRoutes: Boolean,
        logd: (String) -> Unit,
    ): BraveVPNService.NetworkChanges {
        var new = incoming
        // when old and new are null, no changes
        if (old == null && new == null) {
            logd("tun: old and new nws are null")
            return BraveVPNService.NetworkChanges(routesChanged = false, netChanged = false, mtuChanged = false)
        }
        // no old routes to compare with, return true
        if (old == null) {
            logd("tun: old nw is null, new nw: $new")
            return BraveVPNService.NetworkChanges()
        }
        if (new == null) {
            // new is null, but old is not, then check for changes in aux networks
            logd("tun: new nw is null, using old nw: $old")
            new = old
        }

        val useMaxMtu = persistentState.useMaxMtu && !persistentState.routeRethinkInRethink
        logd(
            "tun: useMaxMtu? $useMaxMtu tunMtu:$tunMtu; old: ${old.minMtu}, new: ${new.minMtu}; oldaux: $overlayMtu, newaux: ${aux.mtu}"
        )

        // mark mtu changed if any tunMtu differs from min mtu of new underlying & overlay network
        val mtuChanged = !useMaxMtu && tunMtu != min(new.minMtu, aux.mtu)

        val (builderHas4, builderHas6) = builderRoutes // current tunnel routes v4/v6?

        // when the nws are null from the connection monitor, then consider the builder routes
        // as the new routes

        var vpnHas4 = builderHas4
        var vpnHas6 = builderHas6
        if (reconcileWithVpnRoutes) {
            vpnHas4 = new.vpnRoutes?.first ?: builderHas4
            vpnHas6 = new.vpnRoutes?.second ?: builderHas6
        }

        val n = BraveVPNService.Networks(new, aux)
        val (tunWants4, tunWants6) = determineRoutes(n)

        // old & new agree on activ capable of routing ipv4 or not
        val ok4 = builderHas4 == tunWants4 && builderHas4 == vpnHas4
        // old & new agree on activ capable of routing ipv6 or not
        val ok6 = builderHas6 == tunWants6 && builderHas6 == vpnHas6

        val routesChanged = !ok4 || !ok6

        logd("tun: has4: $builderHas4, wants4: $tunWants4, vpnHas4: $vpnHas4, has6: $builderHas6, wants6: $tunWants6, vpnHas6: $vpnHas6, routesChanged? $routesChanged, ")

        if (new.useActive) {
            connectivityManager.activeNetwork?.let { activ ->
                val activHas4 = isNetworkSame(new.ipv4Net.firstOrNull()?.network, activ)
                val activHas6 = isNetworkSame(new.ipv6Net.firstOrNull()?.network, activ)
                val oldActivHas4 = isNetworkSame(old.ipv4Net.firstOrNull()?.network, activ)
                val oldActivHas6 = isNetworkSame(old.ipv6Net.firstOrNull()?.network, activ)
                val okActiv4 = oldActivHas4 == activHas4 // routing for ipv4 is same in old and new FIRST network
                val okActiv6 = oldActivHas6 == activHas6 // routing for ipv6 is same in old and new FIRST network
                val netChanged = !okActiv4 || !okActiv6

                val ssidChanged = old.activeSsid != new.activeSsid
                logd("tun: oldActiv4: $oldActivHas4, newActiv4: $activHas4, oldActiv6: $oldActivHas6, newActiv6: $activHas6, netChanged? $netChanged")
                logd("tun: oldActiveSsid: ${old.activeSsid}, newActiveSsid: ${new.activeSsid}, ssidChanged? $ssidChanged")
                // for active networks, changes in routes includes all possible network changes;
                return BraveVPNService.NetworkChanges(routesChanged, netChanged, mtuChanged, ssidChanged)
            } // active network null, fallthrough to check for netChanged
        }
        // check if the first networks are different to urge rebinds where necessary (ex: WireGuard)
        val oldFirst6 = old.ipv6Net.firstOrNull()?.network
        val newFirst6 = new.ipv6Net.firstOrNull()?.network
        val oldFirst4 = old.ipv4Net.firstOrNull()?.network
        val newFirst4 = new.ipv4Net.firstOrNull()?.network
        val netChanged = !isNetworkSame(oldFirst6, newFirst6) || !isNetworkSame(oldFirst4, newFirst4)

        val oldSsidFirst4 = old.ipv4Net.firstOrNull()?.ssid
        val newSsidFirst4 = new.ipv4Net.firstOrNull()?.ssid
        val oldSsidFirst6 = old.ipv6Net.firstOrNull()?.ssid
        val newSsidFirst6 = new.ipv6Net.firstOrNull()?.ssid
        val ssidChanged = oldSsidFirst4 != newSsidFirst4 || oldSsidFirst6 != newSsidFirst6

        logd("tun: oldFirst4: $oldFirst4, newFirst4: $newFirst4, oldFirst6: $oldFirst6, newFirst6: $newFirst6, netChanged? $netChanged")
        logd("tun: oldSsidFirst4: $oldSsidFirst4, newSsidFirst4: $newSsidFirst4, oldSsidFirst6: $oldSsidFirst6, newSsidFirst6: $newSsidFirst6, ssidChanged? $ssidChanged")
        return BraveVPNService.NetworkChanges(routesChanged, netChanged, mtuChanged, ssidChanged)
    }
}
