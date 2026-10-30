/*
 * Copyright 2021 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package com.bernaferrari.bravedns.net.go

import Logger
import Logger.LOG_TAG_VPN
import android.content.Context
import android.widget.Toast
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.DnsCryptRelayEndpoint
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.util.Constants.Companion.RETHINKDNS_DOMAIN
import com.bernaferrari.bravedns.util.Utilities.showToastUiCentered
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.DNSResolver
import com.celzero.firestack.intra.Intra
import com.celzero.firestack.intra.Tunnel
import kotlinx.coroutines.CancellationException

/**
 * Adapter-side DNS transport construction: builds protocol URLs / stamps and
 * calls Intra. Public [GoVpnAdapter] methods delegate here unchanged.
 */
internal class GoDnsTransports(
    private val tunnel: () -> Tunnel,
    private val context: Context,
    private val appConfig: AppConfig,
    private val callbacks: Callbacks,
    private val installDoh: (Tunnel, String, String, String) -> Unit = { tun, id, url, ips ->
        Intra.addDoHTransport(tun, id, url, ips)
    },
) {
    internal interface Callbacks {
        fun runOnUi(block: suspend () -> Unit)
        suspend fun resolver(): DNSResolver?
        suspend fun convertRdnsStamp(stamp: String): String
        fun logEvent(severity: Severity, msg: String, details: String)
        fun ipString(url: String): String
        suspend fun addMultipleDnsAsPlus()
        fun onDnsFailure(dnsType: String, message: String)
    }

    suspend fun addTransport() {
        Logger.v(LOG_TAG_VPN, "$TAG addTransport")
        if (!tunnel().isConnected) {
            Logger.e(LOG_TAG_VPN, "$TAG no tunnel, skip add transport")
            return
        }

        // TODO: #321 As of now there is no block free transport for dns types other than Rethink.
        // introduce block free transport for other dns types when there is a UI to configure the
        // block free transport
        // always set the block free transport before setting the transport to the resolver
        // because of the way the alg is implemented in the go code.

        // now it will fallback to rethink default if the transport is not available in kt to add
        when (appConfig.getDnsType()) {
            AppConfig.DnsType.DOH -> {
                addDohTransport(Backend.Preferred)
            }

            AppConfig.DnsType.DOT -> {
                addDotTransport(Backend.Preferred)
            }

            AppConfig.DnsType.ODOH -> {
                addOdohTransport(Backend.Preferred)
            }

            AppConfig.DnsType.DNSCRYPT -> {
                addDnscryptTransport(Backend.Preferred)
            }

            AppConfig.DnsType.DNS_PROXY -> {
                addDnsProxyTransport(Backend.Preferred)
            }

            AppConfig.DnsType.SYSTEM_DNS -> {
                // no-op; system dns propagated by ConnectionMonitor
            }

            AppConfig.DnsType.SMART_DNS -> {
                callbacks.addMultipleDnsAsPlus()
            }

            AppConfig.DnsType.RETHINK_REMOTE -> {
                // only rethink has different stamp for block free transport
                // create new transport for block free
                val rdnsRemoteUrl = appConfig.getRemoteRethinkEndpoint()?.url
                val blockfreeUrl = appConfig.getBlockFreeRethinkEndpoint()

                if (blockfreeUrl.isNotEmpty()) {
                    Logger.i(LOG_TAG_VPN, "$TAG adding blockfree url: $blockfreeUrl")
                    addRdnsTransport(Backend.BlockFree, blockfreeUrl)
                } else {
                    Logger.i(LOG_TAG_VPN, "$TAG no blockfree url found")
                    // if blockfree url is not available, do not proceed further
                    return
                }
                if (!rdnsRemoteUrl.isNullOrEmpty()) {
                    Logger.i(LOG_TAG_VPN, "$TAG adding rdns remote url: $rdnsRemoteUrl")
                    addRdnsTransport(Backend.Preferred, rdnsRemoteUrl)
                } else {
                    Logger.e(LOG_TAG_VPN, "$TAG no rdns remote url found")
                    fallbackToRethinkDefault()
                }
            }
        }

        // only in rethink's remote dns, we have block free transport, so need to remove the
        // block free transport if the selected dns type is not rethink remote, to avoid having
        // stale block free transport when user switches from rethink remote to other dns type.
        // for other dns types, the block free transport is not added at all, so no need to remove.
        removeBlockFreeTransportIfNeeded()
        Logger.v(LOG_TAG_VPN, "$TAG addTransport done")
    }

    private suspend fun fallbackToRethinkDefault() {
        // this should never happen, but as a safeguard, fall back to rethink default
        // to prevent silent failures. case: on a OnePlus device where the database contained
        // values, but none of the transport types were selected, causing vpnAdapter to skip
        // setting any preferred transport.
        Logger.w(LOG_TAG_VPN, "$TAG fallback to rethink default")
        callbacks.onDnsFailure(
            preferredDnsTypeLabel(),
            context.getString(R.string.system_dns_connection_failure)
        )
        val rethinkDefault = appConfig.getRethinkDefaultEndpoint()
        check(rethinkDefault != null && rethinkDefault.url.isNotBlank()) {
            "Cannot initialize DNS: the default Rethink endpoint catalog is missing"
        }
        // Initial setup precedes preference observation: install now, then publish the selection.
        // Waiting for an observer can leave Preferred absent for the entire VPN session.
        installRdnsTransport(Backend.Preferred, rethinkDefault.url)
        appConfig.handleRethinkChanges(rethinkDefault)
    }

    private suspend fun removeBlockFreeTransportIfNeeded() {
        if (appConfig.getDnsType() != AppConfig.DnsType.RETHINK_REMOTE) {
            Logger.i(LOG_TAG_VPN, "$TAG removing block free transport as dns type is not rethink remote")
            removeResolver(Backend.BlockFree)
        }
    }

    suspend fun removeResolver(id: String): Boolean {
        if (!tunnel().isConnected) {
            Logger.e(LOG_TAG_VPN, "$TAG no tunnel, skip remove resolver $id")
            return false
        }
        return try {
            val res = callbacks.resolver()?.remove(id) ?: false
            callbacks.logEvent(
                Severity.LOW,
                "resolver removed? $res",
                "removed $id from tun? $res"
            )
            res
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Logger.e(LOG_TAG_VPN, "$TAG err remove resolver $id: ${e.message}", e)
            callbacks.logEvent(
                Severity.HIGH,
                "error removing resolver",
                "Error removing resolver $id: ${e.message}"
            )
            false
        }
    }

    private suspend fun addDohTransport(id: String) {
        Logger.v(LOG_TAG_VPN, "$TAG addDohTransport")
        var url: String? = null
        try {
            val doh = appConfig.getDOHDetails()
            if (doh == null || doh.dohURL.isEmpty()) {
                Logger.e(LOG_TAG_VPN, "$TAG connect-tunnel: doh url is empty, cannot add transport")
                fallbackToRethinkDefault()
                return
            }
            url = doh.dohURL
            val ips: String = callbacks.ipString(url)
            // change the url from https to http if the isSecure is false
            if (!doh.isSecure) {
                Logger.d(LOG_TAG_VPN, "$TAG changing url from https to http for $url")
            }
            url = dohTransportUrl(url, doh.isSecure)
            // add replaces the existing transport with the same id if successful
            // so no need to remove the transport before adding
            installDoh(tunnel(), id, url, ips)
            Logger.i(LOG_TAG_VPN, "$TAG new doh: $id (${doh.dohName}), url: $url, ips: $ips")
            callbacks.logEvent(
                Severity.LOW,
                "doh transport added",
                "id: $id, url: $url, ips: $ips"
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Logger.e(LOG_TAG_VPN, "$TAG connect-tunnel: doh failure, url: $url", e)
            removeResolver(id)
            callbacks.onDnsFailure(context.getString(R.string.other_dns_list_tab1), e.message ?: context.getString(R.string.system_dns_connection_failure))
            showDnsFailureToast(url.orEmpty())
            callbacks.logEvent(
                Severity.HIGH,
                "doh transport error",
                "error adding doh transport for url: $url, reason: ${e.message}"
            )
        }
        Logger.v(LOG_TAG_VPN, "$TAG addDohTransport done")
    }

    private suspend fun addDotTransport(id: String) {
        Logger.v(LOG_TAG_VPN, "$TAG addDotTransport, id: $id")
        var url: String? = null
        try {
            val dot = appConfig.getDOTDetails()
            if (dot == null || dot.url.isEmpty()) {
                Logger.e(LOG_TAG_VPN, "$TAG connect-tunnel: dot url is empty, cannot add transport")
                fallbackToRethinkDefault()
                return
            }
            url = dot.url
            // if tls is present, remove it and pass it to getIpString
            val ips: String = callbacks.ipString(dotLookupHost(url))
            if (dot.isSecure && !url.startsWith("tls")) {
                Logger.d(LOG_TAG_VPN, "$TAG adding tls to url for $url")
            }
            url = dotTransportUrl(url, dot.isSecure)
            // add replaces the existing transport with the same id if successful
            // so no need to remove the transport before adding
            Intra.addDoTTransport(tunnel(), id, url, ips)
            Logger.i(LOG_TAG_VPN, "$TAG new dot: $id (${dot.name}), url: $url, ips: $ips")
            callbacks.logEvent(
                Severity.LOW,
                "dot transport added",
                "id: $id, url: $url, ips: $ips"
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Logger.e(LOG_TAG_VPN, "$TAG connect-tunnel: dot failure, url: $url", e)
            removeResolver(id)
            callbacks.onDnsFailure(context.getString(R.string.lbl_dot), e.message ?: context.getString(R.string.system_dns_connection_failure))
            showDnsFailureToast(url.orEmpty())
            callbacks.logEvent(
                Severity.HIGH,
                "dot transport error",
                "error adding dot transport for url: $url, reason: ${e.message}"
            )
        }
        Logger.v(LOG_TAG_VPN, "$TAG addDotTransport done")
    }

    private suspend fun addOdohTransport(id: String) {
        Logger.v(LOG_TAG_VPN, "$TAG addOdohTransport, id: $id")
        var resolver: String? = null
        try {
            val odoh = appConfig.getODoHDetails()
            if (odoh == null || odoh.resolver.isEmpty()) {
                Logger.e(LOG_TAG_VPN, "$TAG connect-tunnel: odoh resolver is empty, cannot add transport")
                fallbackToRethinkDefault()
                return
            }
            val proxy = odoh.proxy
            resolver = odoh.resolver
            val proxyIps = ""
            // add replaces the existing transport with the same id if successful
            // so no need to remove the transport before adding
            Intra.addODoHTransport(tunnel(), id, proxy, resolver, proxyIps)
            Logger.i(LOG_TAG_VPN, "$TAG new odoh: $id (${odoh.name}), p: $proxy, r: $resolver")
            callbacks.logEvent(
                Severity.LOW,
                "odoh transport added",
                "id: $id, proxy: $proxy, resolver: $resolver"
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Logger.e(LOG_TAG_VPN, "$TAG connect-tunnel: odoh failure, res: $resolver", e)
            removeResolver(id)
            callbacks.onDnsFailure(context.getString(R.string.lbl_odoh), e.message ?: context.getString(R.string.system_dns_connection_failure))
            showDnsFailureToast(resolver.orEmpty())
            callbacks.logEvent(
                Severity.HIGH,
                "odoh transport error",
                "error adding odoh transport for resolver: $resolver, reason: ${e.message}"
            )
        }
        Logger.v(LOG_TAG_VPN, "$TAG addOdohTransport done")
    }

    private suspend fun addDnscryptTransport(id: String) {
        Logger.v(LOG_TAG_VPN, "$TAG addDnscryptTransport, id: $id")
        try {
            val dc = appConfig.getConnectedDnscryptServer()
            if (dc == null || dc.dnsCryptURL.isEmpty()) {
                Logger.e(LOG_TAG_VPN, "$TAG connect-tunnel: dns-crypt is empty, cannot add transport")
                fallbackToRethinkDefault()
                return
            }
            val url = dc.dnsCryptURL
            // add replaces the existing transport with the same id if successful
            // so no need to remove the transport before adding
            Intra.addDNSCryptTransport(tunnel(), id, url)
            Logger.i(LOG_TAG_VPN, "$TAG new dnscrypt: $id (${dc.dnsCryptName}), url: $url")
            setDnscryptRelaysIfAny() // is expected to catch exceptions
            callbacks.logEvent(
                Severity.LOW,
                "dnscrypt transport added",
                "id: $id, url: $url"
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Logger.e(LOG_TAG_VPN, "$TAG connect-tunnel: dns crypt failure for $id", e)
            removeResolver(id)
            removeDnscryptRelaysIfAny()
            callbacks.onDnsFailure(context.getString(R.string.dc_dns_crypt), e.message ?: context.getString(R.string.dns_crypt_connection_failure))
            showDnscryptConnectionFailureToast()
            callbacks.logEvent(
                Severity.HIGH,
                "dns crypt transport error",
                "error adding dns crypt transport, reason: ${e.message}"
            )
        }
        Logger.v(LOG_TAG_VPN, "$TAG addDnscryptTransport done")
    }

    private suspend fun addDnsProxyTransport(id: String) {
        Logger.v(LOG_TAG_VPN, "$TAG addDnsProxyTransport, id: $id")
        try {
            val dnsProxy = appConfig.getSelectedDnsProxyDetails()
            if (dnsProxy == null || dnsProxy.proxyIP.isNullOrEmpty()) {
                Logger.e(LOG_TAG_VPN, "$TAG connect-tunnel: dns-proxy is empty, cannot add transport")
                fallbackToRethinkDefault()
                return
            }
            // append port number to each ip if multiple ips are present else add the single ip:port
            val ipPortCsv = dnsProxyIpPortCsv(dnsProxy.proxyIP.orEmpty(), dnsProxy.proxyPort)
            // add replaces the existing transport with the same id if successful
            // so no need to remove the transport before adding
            Intra.addDNSProxy(tunnel(), id, ipPortCsv)
            Logger.i(LOG_TAG_VPN, "$TAG new dns proxy: $id(${dnsProxy.proxyName}), ip: $ipPortCsv")
            callbacks.logEvent(
                Severity.LOW,
                "dns proxy transport added",
                "id: $id, ip: $ipPortCsv"
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Logger.e(LOG_TAG_VPN, "$TAG connect-tunnel: dns proxy failure", e)
            removeResolver(id)
            callbacks.onDnsFailure(context.getString(R.string.dc_dns_proxy), e.message ?: context.getString(R.string.dns_proxy_connection_failure))
            showDnsProxyConnectionFailureToast()
            callbacks.logEvent(
                Severity.HIGH,
                "dns proxy transport error",
                "error adding dns proxy transport, reason: ${e.message}"
            )
        }
        Logger.v(LOG_TAG_VPN, "$TAG addDnsProxyTransport done")
    }

    private fun preferredDnsTypeLabel(): String {
        return when (appConfig.getDnsType()) {
            AppConfig.DnsType.DOH -> context.getString(R.string.other_dns_list_tab1)
            AppConfig.DnsType.DOT -> context.getString(R.string.lbl_dot)
            AppConfig.DnsType.ODOH -> context.getString(R.string.lbl_odoh)
            AppConfig.DnsType.DNSCRYPT -> context.getString(R.string.dc_dns_crypt)
            AppConfig.DnsType.DNS_PROXY -> context.getString(R.string.dc_dns_proxy)
            AppConfig.DnsType.RETHINK_REMOTE -> context.getString(R.string.dc_rethink_dns_radio)
            AppConfig.DnsType.SYSTEM_DNS -> context.getString(R.string.network_dns)
            AppConfig.DnsType.SMART_DNS -> context.getString(R.string.smart_dns)
        }
    }

    private suspend fun addRdnsTransport(id: String, url: String) {
        Logger.v(LOG_TAG_VPN, "$TAG addRdnsTransport, id: $id, url: $url")
        try {
            installRdnsTransport(id, url)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Logger.e(LOG_TAG_VPN, "$TAG connect-tunnel: rdns failure, url: $url ($id)", e)
            removeResolver(id)
            callbacks.onDnsFailure(context.getString(R.string.dc_rethink_dns_radio), e.message ?: context.getString(R.string.system_dns_connection_failure))
            showDnsFailureToast(url)
            callbacks.logEvent(
                Severity.HIGH,
                "rdns transport error ($id)",
                "error adding rdns transport for url: $url ($id), reason: ${e.message}"
            )
        }
        Logger.v(LOG_TAG_VPN, "$TAG addRdnsTransport done")
    }

    private suspend fun installRdnsTransport(id: String, url: String) {
        val ips = callbacks.ipString(url)
        val convertedUrl = checkNotNull(getRdnsUrl(url)) { "Invalid Rethink DNS endpoint: $url" }
        installDoh(tunnel(), id, convertedUrl, ips)
        Logger.i(LOG_TAG_VPN, "$TAG new doh (rdns): $id, url: $convertedUrl, ips: $ips")
        callbacks.logEvent(Severity.LOW, "rdns transport added", "id: $id, url: $convertedUrl, ips: $ips")
    }

    private suspend fun getRdnsUrl(url: String): String? {
        if (!url.contains(RETHINKDNS_DOMAIN)) {
            return null
        }
        if (isBlockFreeRdnsUrl(url)) return url
        val stamp = extractRdnsPathStamp(url) ?: return null
        return assembleRdnsUrl(url, callbacks.convertRdnsStamp(stamp))
    }

    private suspend fun setDnscryptRelaysIfAny() {
        dnscryptRelayStamps(appConfig.getDnscryptRelayServers()).forEach {
            Logger.i(LOG_TAG_VPN, "$TAG new dnscrypt relay: $it")
            try {
                Intra.addDNSCryptRelay(tunnel(), it) // entire url is the id
                callbacks.logEvent(
                    Severity.LOW,
                    "add dnscrypt relay",
                    "added dnscrypt relay $it"
                )
            } catch (ex: Exception) {
                Logger.e(LOG_TAG_VPN, "$TAG connect-tunnel: dnscrypt failure", ex)
                appConfig.removeDnscryptRelay(it)
                removeResolver(it)
                callbacks.logEvent(
                    Severity.HIGH,
                    "add dnscrypt relay error",
                    "error adding dnscrypt relay $it, reason: ${ex.message}"
                )
            }
        }
    }

    private suspend fun removeDnscryptRelaysIfAny() {
        dnscryptRelayStamps(appConfig.getDnscryptRelayServers()).forEach {
            Logger.i(LOG_TAG_VPN, "$TAG remove dnscrypt relay: $it")
            // remove from appConfig, as this is not from ui, but from the tunnel start up
            appConfig.removeDnscryptRelay(it)
            removeResolver(it)
        }
    }

    suspend fun addDnscryptRelay(relay: DnsCryptRelayEndpoint) {
        if (!tunnel().isConnected) {
            Logger.e(LOG_TAG_VPN, "$TAG no tunnel, skip add dnscrypt relay")
            return
        }
        try {
            Intra.addDNSCryptRelay(tunnel(), relay.dnsCryptRelayURL)
            Logger.i(LOG_TAG_VPN, "$TAG new dnscrypt relay: ${relay.dnsCryptRelayURL}")
            callbacks.logEvent(
                Severity.LOW,
                "add dnscrypt relay",
                "added dnscrypt relay ${relay.dnsCryptRelayURL}"
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Logger.e(
                LOG_TAG_VPN,
                "$TAG connect-tunnel: dnscrypt relay failure: ${relay.dnsCryptRelayURL}",
                e
            )
            appConfig.removeDnscryptRelay(relay.dnsCryptRelayURL)
            removeResolver(relay.dnsCryptRelayURL)
            callbacks.logEvent(
                Severity.HIGH,
                "add dnscrypt relay error",
                "error adding dnscrypt relay ${relay.dnsCryptRelayURL}, reason: ${e.message}"
            )
        }
    }

    suspend fun removeDnscryptRelay(relay: DnsCryptRelayEndpoint) {
        if (!tunnel().isConnected) {
            Logger.e(LOG_TAG_VPN, "$TAG no tunnel, skip remove dnscrypt relay")
            return
        }
        try {
            // no need to remove from appConfig, as it is already removed
            val rmv = callbacks.resolver()?.remove(relay.dnsCryptRelayURL)
            Logger.i(
                LOG_TAG_VPN,
                "$TAG rmv dnscrypt relay: ${relay.dnsCryptRelayURL}, success? $rmv"
            )
            callbacks.logEvent(
                Severity.LOW,
                "remove dns crypt relay",
                "remove dns crypt relay for id: ${relay.id}, url: ${relay.dnsCryptRelayURL}"
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Logger.w(LOG_TAG_VPN, "$TAG connect-tunnel: dnscrypt rmv failure")
            callbacks.logEvent(
                Severity.HIGH,
                "error; dnscrypt relay removal",
                "Error on dnscrypt relay removal for ${relay.dnsCryptRelayURL}, ${e.message}"
            )
        }
    }

    private fun showDnscryptConnectionFailureToast() {
        callbacks.runOnUi {
            showToastUiCentered(
                context.applicationContext,
                context.getString(R.string.dns_crypt_connection_failure),
                Toast.LENGTH_LONG
            )
        }
    }

    private fun showDnsFailureToast(url: String) {
        callbacks.runOnUi {
            val msg =
                context.getString(
                    R.string.two_argument_colon,
                    url,
                    context.getString(R.string.dns_proxy_connection_failure)
                )
            showToastUiCentered(context.applicationContext, msg, Toast.LENGTH_LONG)
        }
    }

    private fun showDnsProxyConnectionFailureToast() {
        callbacks.runOnUi {
            showToastUiCentered(
                context.applicationContext,
                context.getString(R.string.dns_proxy_connection_failure),
                Toast.LENGTH_SHORT
            )
        }
    }

    companion object {
        private const val TAG = "TunAdapter;"
    }
}
