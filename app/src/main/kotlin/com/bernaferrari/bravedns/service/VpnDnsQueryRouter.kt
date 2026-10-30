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
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.service.ProxyManager.ID_WG_BASE
import com.bernaferrari.bravedns.util.AndroidUidConfig
import com.bernaferrari.bravedns.util.Constants.Companion.INVALID_UID
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.bernaferrari.bravedns.util.ResourceRecordTypes
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.DNSOpts

/**
 * onQuery transport-id / proxy-id decision tree for [BraveVPNService].
 */
class VpnDnsQueryRouter(
    private val persistentState: PersistentState,
    private val appConfig: AppConfig,
    private val rethinkUid: () -> Int,
    private val isAppPaused: () -> Boolean,
    private val isLockdown: () -> Boolean,
    private val getDomainRule: (String, Int) -> Pair<DomainRulesManager.Status, String?>,
    private val getUnderlyingSsid: () -> String?,
    private val isUidPresentInAnyDnsRequest: () -> Boolean,
    private val markUidPresentInDnsRequest: () -> Unit,
    private val logd: (String) -> Unit,
    private val normalizeUid: (Int) -> Int = { it },
    private val isCaptivePortal: () -> Boolean = { false },
    private val isActiveCellular: () -> Boolean = { false },
) {
    companion object {
        private const val TAG = "VpnService;"
    }

    suspend fun resolve(uidGostr: String?, fqdn: String, qtype: Long): DNSOpts {
        val uidStr = uidGostr.orEmpty()
        var result: DNSOpts?
        var uid: Int = INVALID_UID
        val rinr = persistentState.routeRethinkInRethink
        try {
            uid = when (uidStr) {
                rethinkUid().toString() -> {
                    rethinkUid()
                }
                else -> {
                    uidStr.toInt()
                }
            }
        } catch (_: NumberFormatException) {
            Logger.w(LOG_TAG_VPN, "onQuery: invalid uid: $uidStr, using default $uid")
        }

        uid = normalizeUid(uid)
        logd("onQuery: rcvd uid: $uid query: $fqdn, qtype: $qtype")
        if (isCaptivePortal()) {
            val captive = makeNsOpts(uid, Pair(Backend.System, ""), fqdn, true, rinr, forceBase = true)
            return checkUserAllowedDnsQtypes(captive, uid, fqdn, qtype, rinr)
        }
        val appMode = appConfig.getBraveMode()
        if (appMode.isDnsMode()) {
            result = getTransportIdForDnsMode(uid, fqdn, rinr)
            logd("onQuery (Dns):$fqdn, dnsx: $result")
            return checkUserAllowedDnsQtypes(result, uid, fqdn, qtype, rinr = rinr)
        }

        if (appMode.isDnsFirewallMode()) {
            result = getTransportIdForDnsFirewallMode(uid, fqdn, rinr = rinr)
            logd("onQuery (Dns+Firewall):$fqdn, dnsx: $result")
            return checkUserAllowedDnsQtypes(result, uid, fqdn, qtype, rinr = rinr)
        }

        val tid = if (appConfig.isSmartDnsEnabled()) {
            Backend.Plus
        } else {
            Backend.Preferred
        }
        result = makeNsOpts(uid, Pair(tid, ""), fqdn, rinr = rinr)
        Logger.e(LOG_TAG_VPN, "onQuery: unknown mode ${appMode}, $fqdn, returning $result")
        return checkUserAllowedDnsQtypes(result, uid, fqdn, qtype, rinr = rinr)
    }

    suspend fun bypassAnswer(uid: Int, domain: String, previous: DNSOpts): DNSOpts {
        val old = Pair(previous.tidcsv, previous.tidseccsv)
        val clean = Pair(DnsTransportRouting.transportIds(old.first), DnsTransportRouting.transportIds(old.second))
        val target = getTransportIdToBypass(clean)
        if (DnsTransportRouting.sameTransports(previous.tidcsv, target.first)) {
            return if (previous.noblock) DNSOpts() else previous.copy().apply { noblock = true }
        }
        return makeNsOpts(uid, target, domain, true, persistentState.routeRethinkInRethink)
    }

    private suspend fun checkUserAllowedDnsQtypes(result: DNSOpts, uid: Int, fqdn: String, qtype: Long, rinr: Boolean): DNSOpts {
        if (result.tidcsv.contains(Backend.BlockAll)) {
            return result
        }
        val allowedTypes = persistentState.getAllowedDnsRecordTypesAsEnum()
        if (!ResourceRecordTypes.isQtypeAllowed(qtype.toInt(), allowedTypes)) {
            val r = makeNsOpts(uid, Pair(Backend.BlockAll, ""), fqdn, false, rinr = rinr)
            logd("onQuery: blocking dns responses based on qtype for $fqdn, qtype: $qtype, returning $r")
            return r
        } else {
            return result
        }
    }

    private fun getTransportIdToBypass(id: Pair<String, String>): Pair<String, String> {
        val secondary = listOf(id.first, id.second).flatMap { it.split(',') }
            .filter(String::isNotBlank).distinct().joinToString(",")
        return when (BlockFreeDnsMode.fromMode(persistentState.blockFreeDnsMode)) {
            BlockFreeDnsMode.AUTO -> {
                if (persistentState.splitDns || persistentState.preventDnsLeaks) {
                    id
                } else {
                    Pair(Backend.Default, secondary)
                }
            }
            BlockFreeDnsMode.GLOBAL -> {
                val primary = when {
                    appConfig.isSystemDns() || (isAppPaused() && isLockdown()) -> Backend.System
                    appConfig.isSmartDnsEnabled() -> Backend.Plus
                    else -> Backend.Preferred
                }
                Pair(primary, secondary)
            }
            BlockFreeDnsMode.FALLBACK -> Pair(Backend.Default, secondary)
        }
    }

    private suspend fun getTransportIdForDnsMode(uid: Int, fqdn: String, rinr: Boolean): DNSOpts {
        val tid = determineDnsTransportIdForDnsMode()

        if (uid == rethinkUid() && !rinr) {
            return makeNsOpts(uid, tid, fqdn, rinr = false)
        }

        if (uid != INVALID_UID) {
            when (DomainRulesManager.getDomainRule(fqdn, uid)) {
                DomainRulesManager.Status.TRUST -> return makeNsOpts(uid, getTransportIdToBypass(tid), fqdn, true, rinr = rinr)
                DomainRulesManager.Status.BLOCK -> return makeNsOpts(uid, Pair(Backend.BlockAll, ""), fqdn, false, rinr = rinr)
                else -> {}
            }
        }

        when (DomainRulesManager.getDomainRule(fqdn, UID_EVERYBODY)) {
            DomainRulesManager.Status.TRUST -> return makeNsOpts(uid, getTransportIdToBypass(tid), fqdn, true, rinr = rinr)
            DomainRulesManager.Status.BLOCK -> return makeNsOpts(uid, Pair(Backend.BlockAll, ""), fqdn, false, rinr = rinr)
            else -> {}
        }

        return makeNsOpts(uid, tid, fqdn, rinr = rinr)
    }

    private suspend fun getTransportIdForDnsFirewallMode(uid: Int, fqdn: String, rinr: Boolean): DNSOpts {
        val splitDns = persistentState.splitDns && WireguardManager.isAdvancedWgActive()
        val tid = determineDnsTransportIdForDFMode(uid, fqdn, splitDns, rinr)
        val forceBypassLocalBlocklists = isAppPaused() && isLockdown()

        if (uid == rethinkUid() && !rinr) {
            Logger.vv(LOG_TAG_VPN, "$TAG; onQuery: rethink's uid use $tid for $fqdn")
            return makeNsOpts(uid, tid, fqdn, true, rinr = rinr)
        }
        if (forceBypassLocalBlocklists) {
            Logger.vv(LOG_TAG_VPN, "$TAG; onQuery: bypassing local blocklists for $fqdn, $uid")
            return makeNsOpts(uid, tid, fqdn, true, rinr = rinr)
        }
        if (persistentState.bypassBlockInDns && persistentState.enableDnsAlg) {
            Logger.vv(LOG_TAG_VPN, "$TAG; onQuery: bypass block in onquery $fqdn, $uid")
            return makeNsOpts(uid, transportIdsAlg(tid, splitDns), fqdn, true, rinr = rinr)
        }

        if (uid == INVALID_UID) {
            return resolveUnknownUidDns(uid, fqdn, tid, splitDns, rinr)
        }
        return resolveKnownUidDns(uid, fqdn, tid, rinr)
    }

    private suspend fun resolveUnknownUidDns(
        uid: Int,
        fqdn: String,
        tid: Pair<String, String>,
        splitDns: Boolean,
        rinr: Boolean,
    ): DNSOpts {
        return if (FirewallManager.isAnyAppBypassesDns()) {
            Logger.vv(LOG_TAG_VPN, "$TAG; onQuery: no uid, some app is bypassed $fqdn")
            makeNsOpts(uid, transportIdsAlg(tid, splitDns), fqdn, true, rinr = rinr)
        } else if (DomainRulesManager.isDomainTrusted(fqdn)) {
            Logger.vv(LOG_TAG_VPN, "$TAG; onQuery: no uid, univ domain trusted $fqdn")
            makeNsOpts(uid, transportIdsAlg(tid, splitDns), fqdn, true, rinr = rinr)
        } else if (getDomainRule(fqdn, UID_EVERYBODY).first == DomainRulesManager.Status.BLOCK) {
            Logger.vv(LOG_TAG_VPN, "$TAG; onQuery: no uid, univ domain blocked $fqdn")
            makeNsOpts(uid, Pair(Backend.BlockAll, ""), fqdn, rinr = rinr)
        } else if (isUidPresentInAnyDnsRequest() && persistentState.getBlockUnknownConnections()) {
            Logger.vv(LOG_TAG_VPN, "$TAG; onQuery: no uid, block unknown connections $fqdn")
            makeNsOpts(uid, Pair(Backend.BlockAll, ""), fqdn, rinr = rinr)
        } else {
            Logger.vv(LOG_TAG_VPN, "$TAG; onQuery: no uid, no global rule, use $tid for $fqdn")
            makeNsOpts(uid, tid, fqdn, rinr = rinr)
        }
    }

    private suspend fun resolveKnownUidDns(
        uid: Int,
        fqdn: String,
        tid: Pair<String, String>,
        rinr: Boolean,
    ): DNSOpts {
        if (!isUidPresentInAnyDnsRequest() && uid != AndroidUidConfig.DNS.uid) {
            markUidPresentInDnsRequest()
            Logger.v(LOG_TAG_VPN, "$TAG; onQuery: uid present in dns request")
        }
        val isTempAllowed = FirewallManager.isTempAllowed(uid)
        if (isTempAllowed) {
            Logger.vv(LOG_TAG_VPN, "$TAG onQuery, $uid is temp allowed, $fqdn")
            return makeNsOpts(uid, getTransportIdToBypass(tid), fqdn, true, rinr = rinr)
        }
        val connectionStatus = FirewallManager.connectionStatus(uid)
        if (connectionStatus.blocked()) {
            Logger.vv(LOG_TAG_VPN, "$TAG onQuery, $uid is blocked, $fqdn")
            return makeNsOpts(uid, Pair(Backend.BlockAll, ""), fqdn, rinr = rinr)
        }

        val appStatus = FirewallManager.appStatus(uid)
        if (appStatus.bypassDnsFirewall()) {
            Logger.vv(LOG_TAG_VPN, "$TAG onQuery, $uid bypasses dns+firewall, $fqdn")
            return makeNsOpts(uid, getTransportIdToBypass(tid), fqdn, true, rinr = rinr)
        }

        val appDomainRule = getDomainRule(fqdn, uid).first
        when (appDomainRule) {
            DomainRulesManager.Status.TRUST -> {
                Logger.vv(LOG_TAG_VPN, "$TAG onQuery, $uid, domain trusted: $fqdn")
                return makeNsOpts(uid, getTransportIdToBypass(tid), fqdn, true, rinr = rinr)
            }
            DomainRulesManager.Status.BLOCK -> {
                Logger.vv(LOG_TAG_VPN, "$TAG onQuery, $uid, domain blocked: $fqdn")
                return makeNsOpts(uid, Pair(Backend.BlockAll, ""), fqdn, rinr = rinr)
            }
            DomainRulesManager.Status.NONE -> {
            }
        }

        val globalDomainRule = getDomainRule(fqdn, UID_EVERYBODY).first
        when (globalDomainRule) {
            DomainRulesManager.Status.TRUST -> {
                Logger.vv(LOG_TAG_VPN, "$TAG onQuery, $uid, univ domain trusted: $fqdn")
                return makeNsOpts(uid, getTransportIdToBypass(tid), fqdn, true, rinr = rinr)
            }
            DomainRulesManager.Status.BLOCK -> {
                Logger.vv(LOG_TAG_VPN, "$TAG onQuery, $uid, univ domain blocked: $fqdn")
                return makeNsOpts(uid, Pair(Backend.BlockAll, ""), fqdn, rinr = rinr)
            }
            DomainRulesManager.Status.NONE -> {
            }
        }

        Logger.vv(LOG_TAG_VPN, "$TAG onQuery, $uid, no rules, use $tid for $fqdn")
        return makeNsOpts(uid, tid, fqdn, rinr = rinr)
    }

    private fun determineDnsTransportIdForDnsMode(): Pair<String, String> {
        val tid = if (appConfig.isSystemDns() || (isAppPaused() && isLockdown())) {
            Backend.System
        } else if (appConfig.isSmartDnsEnabled()) {
            Backend.Plus
        } else {
            Backend.Preferred
        }
        return Pair(tid, "")
    }

    private suspend fun determineDnsTransportIdForDFMode(uid: Int, domain: String, splitDns: Boolean, rinr: Boolean): Pair<String, String> {
        val defaultTid =
            if (appConfig.isSystemDns() || (isAppPaused() && isLockdown())) {
                Backend.System
            } else if (appConfig.isSmartDnsEnabled()) {
                Backend.Plus
            } else {
                Backend.Preferred
            }

        if (uid == rethinkUid() && !rinr) {
            val id = Backend.Default
            Logger.d(LOG_TAG_VPN, "(onQuery)rethink's uid using $id")
            return Pair(defaultTid, "")
        }

        if (uid == INVALID_UID) {
            val oneWgId = WireguardManager.getOneWireGuardProxyId()
            val tid = if (oneWgId != null) {
                ID_WG_BASE + oneWgId
            } else {
                defaultTid
            }
            return if (splitDns) {
                Pair(tid, Backend.Fixed)
            } else {
                Pair(tid, "")
            }
        } else {
            if (!splitDns && !WireguardManager.oneWireGuardEnabled()) {
                Logger.d(LOG_TAG_VPN, "(onQuery)no split dns, using $defaultTid")
                return Pair(defaultTid, "")
            }
            if (FirewallManager.isAppExcludedFromProxy(uid)) {
                return Pair(defaultTid, "")
            }
            val ssid = getUnderlyingSsid() ?: ""
            val ids = WireguardManager.getAllPossibleConfigIdsForApp(uid, ip = "", port = 0, domain, isActiveCellular(), ssid, defaultTid)
            val wgOrDefaultTid = if (ids.isEmpty()) {
                Logger.d(LOG_TAG_VPN, "(onQuery-pid)no wg found, return $defaultTid")
                defaultTid
            } else {
                Logger.d(LOG_TAG_VPN, "(onQuery-pid)wg ids($ids) found for uid: $uid")
                ids.joinToString(",")
            }
            Logger.d(LOG_TAG_VPN, "(onQuery)wg ids($ids) found for uid: $uid")
            return Pair(wgOrDefaultTid, "")
        }
    }

    private suspend fun makeNsOpts(
        uid: Int,
        tid: Pair<String, String>,
        domain: String,
        bypassLocalBlocklists: Boolean = false,
        rinr: Boolean,
        forceBase: Boolean = false,
    ): DNSOpts {
        val opts = DNSOpts()
        opts.ipcsv = ""
        val tidCsv = tid.first.split(",").joinToString(",") { appendDnsCacheIfNeeded(it) }
        opts.tidcsv = tidCsv
        val secCsv = if (tid.second.isNotEmpty()) tid.second.split(",").joinToString(",") { appendDnsCacheIfNeeded(it) } else ""
        opts.tidseccsv = secCsv

        val proxyIds = if (forceBase || (uid == rethinkUid() && !rinr)) {
            Backend.Base
        } else if (tid.first.contains(ID_WG_BASE) || tid.second.contains(ID_WG_BASE)) {
            Backend.Base
        } else {
            proxyIdForOnQuery(uid, domain)
        }
        opts.uid = uid.toString()
        opts.tidcsv = DnsTransportRouting.encode(tidCsv, proxyIds)
        opts.tidseccsv = DnsTransportRouting.encode(secCsv, proxyIds)
        opts.noblock = bypassLocalBlocklists
        Logger.vv(LOG_TAG_VPN, "onQuery: uid: $uid, domain: $domain, tid: ${opts.tidcsv}, sec: ${opts.tidseccsv}, noblock: ${opts.noblock}")
        return opts
    }

    private fun transportIdsAlg(preferredId: Pair<String, String>, splitDns: Boolean): Pair<String, String> {
        if (splitDns) {
            return preferredId
        }
        return if (isRethinkDnsEnabled()) {
            val tr1 = Backend.BlockFree
            val tr2 = preferredId.first
            val p = Pair(tr1, tr2)
            p
        } else {
            preferredId
        }
    }

    private fun isRethinkDnsEnabled(): Boolean {
        return appConfig.isRethinkDnsConnected() && !WireguardManager.oneWireGuardEnabled()
    }

    private fun appendDnsCacheIfNeeded(id: String): String {
        return if (canUseDnsCacheOnTransportId(id) && !id.startsWith(Backend.CT)) {
            Backend.CT + id
        } else {
            id
        }
    }

    private fun canUseDnsCacheOnTransportId(userPreferredId: String): Boolean {
        return persistentState.enableDnsCache && userPreferredId != Backend.BlockAll
    }

    private suspend fun proxyIdForOnQuery(uid: Int, domain: String): String {
        val defaultProxy = when {
            persistentState.routeRethinkInRethink -> Backend.Base
            else -> Backend.Base
        }

        if (!appConfig.getBraveMode().isDnsFirewallMode()) {
            Logger.d(LOG_TAG_VPN, "(onQuery-pid)not in dns-firewall mode")
            return defaultProxy
        }

        if (!persistentState.proxyDns) {
            Logger.d(LOG_TAG_VPN, "(onQuery-pid)proxyDns is disabled, return $defaultProxy")
            return defaultProxy
        }

        if (FirewallManager.isAppExcludedFromProxy(uid)) {
            logd("(onQuery-pid) app excluded from proxy, return $defaultProxy")
            return defaultProxy
        }

        if (appConfig.isDnsProxyActive()) {
            val endpoint = appConfig.getSelectedDnsProxyDetails()
            val app = endpoint?.proxyAppName
            if (!app.isNullOrEmpty()) {
                Logger.d(LOG_TAG_VPN, "(onQuery-pid)proxy app: $app, return $defaultProxy")
                return defaultProxy
            }
        }

        return if (appConfig.isCustomSocks5Enabled()) {
            Logger.d(
                LOG_TAG_VPN,
                "(onQuery-pid)customSocks5 enabled, return ${ProxyManager.ID_S5_BASE},${defaultProxy}"
            )
            "${ProxyManager.ID_S5_BASE},${defaultProxy}"
        } else if (appConfig.isCustomHttpProxyEnabled()) {
            Logger.d(
                LOG_TAG_VPN,
                "(onQuery-pid)customHttp enabled, return ${ProxyManager.ID_HTTP_BASE},${defaultProxy}"
            )
            "${ProxyManager.ID_HTTP_BASE},${defaultProxy}"
        } else {
            val ssid = getUnderlyingSsid().orEmpty()
            val ids = WireguardManager.getAllPossibleConfigIdsForApp(
                uid,
                ip = "",
                port = 0,
                domain,
                isActiveCellular(),
                ssid,
                defaultProxy
            )
            if (ids.isEmpty()) {
                Logger.d(LOG_TAG_VPN, "(onQuery-pid)no wg found, return $defaultProxy")
                defaultProxy
            } else {
                Logger.d(LOG_TAG_VPN, "(onQuery-pid)wg ids($ids) found for uid: $uid")
                ids.joinToString(",")
            }
        }
    }
}
