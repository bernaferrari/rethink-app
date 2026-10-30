/*
 * Copyright 2026 RethinkDNS and its authors
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
import Logger.LOG_TAG_PROXY
import com.bernaferrari.bravedns.database.WgConfigFilesImmutable
import com.bernaferrari.bravedns.service.ProxyManager.ID_WG_BASE

/**
 * Pure WireGuard proxy-id resolution: one-WG short-circuit, app-specific ids,
 * lockdown, catch-all append, then default.
 *
 * Metered-only (`useOnlyOnMetered`) follows the selected mobile underlay, even
 * when a Wi-Fi interface remains connected. SSID-based passes only on Wi-Fi. Missing SSID
 * mappings fail closed.
 */
internal class WgHopSelector(
    private val wgPrefix: String = ID_WG_BASE,
    private val invalidId: Int = WireguardManager.INVALID_CONF_ID,
    private val matchesSsid: (ssidList: String, ssid: String) -> Boolean = WgSsidMatcher::matchesSsidList,
) {

    fun convertStringIdToId(id: String): Int {
        return try {
            val configId = id.substring(wgPrefix.length)
            configId.toIntOrNull() ?: invalidId
        } catch (_: IndexOutOfBoundsException) {
            Logger.i(LOG_TAG_PROXY, "err converting string id to int: $id")
            invalidId
        }
    }

    fun isEligibleForNetwork(
        id: Int,
        usesMobileNw: Boolean,
        ssid: String,
        mobileOnlySetting: Boolean,
        ssidEnabled: Boolean,
        mappings: Collection<WgConfigFilesImmutable>,
    ): Boolean {
        if (!mobileOnlySetting && !ssidEnabled) return true

        val passMobileOnly = mobileOnlySetting && usesMobileNw
        val passSsid = ssidEnabled && !usesMobileNw && matchesSsidListForConfig(id, ssid, mappings)
        return passMobileOnly || passSsid
    }

    fun matchesSsidListForConfig(
        id: Int,
        ssid: String,
        mappings: Collection<WgConfigFilesImmutable>,
    ): Boolean {
        val config = mappings.find { it.id == id }
        if (config == null) {
            Logger.e(LOG_TAG_PROXY, "matchesSsidListForConfig: wg not found, id: $id, ${mappings.size}")
            return false
        }
        return matchesSsid(config.ssids, ssid)
    }

    fun canUseConfig(
        idStr: String,
        type: String,
        usesMobileNw: Boolean,
        ssid: String,
        mappings: Collection<WgConfigFilesImmutable>,
    ): Boolean {
        if (idStr.isEmpty()) {
            return false
        }
        val id = convertStringIdToId(idStr)
        val config = if (id == invalidId) null else mappings.find { it.id == id }

        if (config == null) {
            Logger.d(LOG_TAG_PROXY, "config null($idStr) no need to proceed, return empty")
            return false
        }

        val lockdown = config.isLockdown

        if (lockdown && isEligibleForNetwork(id, usesMobileNw, ssid, config.useOnlyOnMetered, config.ssidEnabled, mappings)) {
            Logger.d(LOG_TAG_PROXY, "lockdown wg for $type => return $idStr")
            return true
        }

        if (config.isActive && isEligibleForNetwork(id, usesMobileNw, ssid, config.useOnlyOnMetered, config.ssidEnabled, mappings)) {
            Logger.d(LOG_TAG_PROXY, "active wg for $type => add $idStr")
            return true
        }

        Logger.v(LOG_TAG_PROXY, "wg for $type not active or not eligible nw, return empty, for id: $idStr, usesMtrdNw: $usesMobileNw, ssid: $ssid")
        return false
    }

    fun isAnyProxyLockdown(
        proxies: List<String>,
        mappings: Collection<WgConfigFilesImmutable>,
    ): Boolean {
        var lockdown = false
        proxies.forEach { it ->
            val confId = convertStringIdToId(it)
            val conf = mappings.find { mapping -> mapping.id == confId }
            lockdown = conf?.isLockdown ?: false
            if (lockdown) return true
        }
        return lockdown
    }

    fun collectProxyIds(
        uid: Int,
        ip: String,
        port: Int,
        domain: String,
        usesMobileNw: Boolean,
        ssid: String,
        default: String,
        oneWireGuardId: Int?,
        wgProxyIdsForApp: List<String>,
        mappings: Collection<WgConfigFilesImmutable>,
    ): List<String> {
        val proxyIds: MutableList<String> = mutableListOf()

        if (oneWireGuardId != null) {
            if (oneWireGuardId == invalidId) {
                Logger.e(LOG_TAG_PROXY, "canAdd: one-wg not found, id: $oneWireGuardId, return empty")
                return emptyList()
            }

            proxyIds.add(wgPrefix + oneWireGuardId)
            if (mappings.find { it.id == oneWireGuardId }?.isSplitProxy == true && default.isNotEmpty()) {
                proxyIds.add(default)
            }
            Logger.i(LOG_TAG_PROXY, "one-wg enabled, return $proxyIds")
            return proxyIds
        }

        if (wgProxyIdsForApp.isNotEmpty()) {
            for (pid in wgProxyIdsForApp) {
                val canUse = canUseConfig(pid, "app($uid)", usesMobileNw, ssid, mappings)
                if (canUse) {
                    proxyIds.add(pid)
                }
            }
        }

        if (isAnyProxyLockdown(proxyIds, mappings)) {
            Logger.i(LOG_TAG_PROXY, "lockdown wg for app($uid) => return $proxyIds")
            return proxyIds
        }

        val cac = mappings.filter { it.isActive && it.isCatchAll }
        cac.forEach {
            if (isEligibleForNetwork(it.id, usesMobileNw, ssid, it.useOnlyOnMetered, it.ssidEnabled, mappings) &&
                !proxyIds.contains(wgPrefix + it.id)
            ) {
                proxyIds.add(wgPrefix + it.id)
                Logger.i(
                    LOG_TAG_PROXY,
                    "catch-all config is active: ${it.id}, ${it.name} => add ${wgPrefix + it.id}"
                )
            }
        }

        if (proxyIds.isEmpty()) {
            Logger.i(
                LOG_TAG_PROXY,
                "no proxy ids found for $uid, $ip, $port, $domain; returning $default"
            )
            return if (default.isEmpty()) emptyList() else listOf(default)
        }

        val isAnyIdLockdown = proxyIds.any { id ->
            val confId = convertStringIdToId(id)
            val conf = mappings.find { it.id == confId }
            conf?.isLockdown ?: false
        }

        if (default.isNotEmpty() && !isAnyIdLockdown) proxyIds.add(default)

        Logger.i(LOG_TAG_PROXY, "returning proxy ids for $uid, $ip, $port, $domain: $proxyIds")
        return proxyIds
    }
}
