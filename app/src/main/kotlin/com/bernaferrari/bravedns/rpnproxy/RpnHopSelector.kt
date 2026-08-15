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
package com.bernaferrari.bravedns.rpnproxy

import Logger
import Logger.LOG_TAG_PROXY
import com.bernaferrari.bravedns.database.CountryConfig

/**
 * Pure hop-picking rules for RPN WIN servers.
 *
 * Mirrors the historical [RpnProxyManager] lookup quirks:
 * - SSID / lockdown lookup matches `id` or `key` as given (no prefix strip).
 * - [canUseConfig] lookup also matches the prefix-stripped id/key.
 * - Metered-only (`mobileOnly`) passes only when the network is mobile and SSID is empty.
 * - SSID-based passes only on unmetered networks.
 * - Lockdown short-circuits further catch-all collection (one-WG interplay).
 */
internal class RpnHopSelector(
    private val rpnWinPrefix: String,
    private val autoServerId: String,
    private val matchesSsid: (ssidList: String, ssid: String) -> Boolean,
    private val tag: String = "RpnMgr",
) {

    fun findConfigForSsid(id: String, servers: List<CountryConfig>): CountryConfig? {
        return if (id == rpnWinPrefix || id.equals(autoServerId, true)) {
            servers.find { it.id.equals(autoServerId, true) }
        } else {
            servers.find { it.id == id || it.key == id }
        }
    }

    fun findConfigForUse(id: String, servers: List<CountryConfig>): CountryConfig? {
        val actualId = id.substringAfter(rpnWinPrefix)
        return if (actualId == rpnWinPrefix || actualId.equals(autoServerId, true)) {
            servers.find { it.id.equals(autoServerId, true) }
        } else {
            servers.find { it.id == id || it.id == actualId || it.key == id || it.key == actualId }
        }
    }

    fun findConfigForLockdown(pid: String, servers: List<CountryConfig>): CountryConfig? {
        return if (pid == rpnWinPrefix || pid.equals(autoServerId, true)) {
            servers.find { it.id.equals(autoServerId, true) }
        } else {
            servers.find { it.id == pid || it.key == pid }
        }
    }

    fun isEligibleForNetwork(
        id: String,
        usesMobileNw: Boolean,
        ssid: String,
        mobileOnlySetting: Boolean,
        ssidEnabled: Boolean,
        servers: List<CountryConfig>,
    ): Boolean {
        if (!mobileOnlySetting && !ssidEnabled) return true

        val passMobileOnly = mobileOnlySetting && (usesMobileNw && ssid.isEmpty())
        val passSsid = ssidEnabled && !usesMobileNw && matchesSsidListForConfig(id, ssid, servers)
        return passMobileOnly || passSsid
    }

    fun matchesSsidListForConfig(id: String, ssid: String, servers: List<CountryConfig>): Boolean {
        val config = findConfigForSsid(id, servers)
        if (config == null) {
            Logger.w(LOG_TAG_PROXY, "matchesSsidListForConfig: config is null for id: $id")
            return false
        }
        return matchesSsid(config.ssids, ssid)
    }

    fun canUseConfig(
        id: String,
        type: String,
        usesMobileNw: Boolean,
        ssid: String,
        servers: List<CountryConfig>,
    ): Boolean {
        if (id.isEmpty()) {
            return false
        }
        val actualId = id.substringAfter(rpnWinPrefix)

        val config = findConfigForUse(id, servers)

        if (config == null) {
            Logger.w(LOG_TAG_PROXY, "$tag; config null($actualId) no need to proceed, return empty")
            return false
        }

        Logger.vv(LOG_TAG_PROXY, "$tag; config-details: $config")

        val lockdown = config.lockdown

        if (lockdown && isEligibleForNetwork(id, usesMobileNw, ssid, config.mobileOnly, config.ssidBased, servers)) {
            Logger.d(LOG_TAG_PROXY, "$tag; lockdown wg for $type => return $id")
            return true // no need to proceed further for lockdown
        }

        // check if the config is active and if it can be used on this network
        if (config.isEnabled && isEligibleForNetwork(id, usesMobileNw, ssid, config.mobileOnly, config.ssidBased, servers)) {
            Logger.d(LOG_TAG_PROXY, "$tag; active wg for $type => add $id")
            return true
        }

        Logger.v(
            LOG_TAG_PROXY,
            "$tag wg for $type not active or not eligible nw, return empty, for id: $id, usesMobileNw: $usesMobileNw, ssid: $ssid"
        )
        return false
    }

    fun isAnyProxyLockdown(proxies: List<String>, servers: List<CountryConfig>): Boolean {
        var lockdown = false
        proxies.forEach { pid ->
            val config = findConfigForLockdown(pid, servers)
            lockdown = config?.lockdown ?: false
            if (lockdown) return true
        }
        return lockdown
    }

    suspend fun collectProxyIds(
        uid: Int,
        ip: String,
        port: Int,
        domain: String,
        usesMobileNw: Boolean,
        ssid: String,
        allProxyIdsForApp: List<String>,
        rpnProxyIdsForApp: List<String>,
        servers: List<CountryConfig>,
        resolveAutoId: suspend () -> String,
    ): List<String> {
        val proxyIds: MutableList<String> = mutableListOf()

        Logger.vv(LOG_TAG_PROXY, "$tag; getAllPossibleConfigIdsForApp: init $uid, $ip, $port, $domain, $usesMobileNw, $ssid")
        Logger.v(LOG_TAG_PROXY, "$tag; allProxyIdsForApp: $allProxyIdsForApp, rpnProxyIdsForApp: $rpnProxyIdsForApp")

        if (rpnProxyIdsForApp.isNotEmpty()) {
            for (pid in rpnProxyIdsForApp) {
                val canUse = canUseConfig(pid, "app($uid)", usesMobileNw, ssid, servers)
                if (canUse) {
                    if (pid.contains(autoServerId, true)) {
                        val id = resolveAutoId()
                        proxyIds.add(id)
                    } else {
                        proxyIds.add(pid)
                    }
                }
            }
        }

        if (isAnyProxyLockdown(proxyIds, servers)) {
            Logger.i(LOG_TAG_PROXY, "$tag lockdown rpn for app($uid) => return $proxyIds")
            return proxyIds
        }

        val cac = try {
            servers.filter { it.isEnabled && it.catchAll }
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "$tag; error filtering catch-all configs: ${e.message}", e)
            emptyList()
        }

        Logger.v(LOG_TAG_PROXY, "$tag; cac: $cac")
        cac.forEach {
            try {
                val configId = rpnWinPrefix + it.key
                if (isEligibleForNetwork(it.id, usesMobileNw, ssid, it.mobileOnly, it.ssidBased, servers) &&
                    !proxyIds.contains(configId)
                ) {
                    val id = if (configId.contains(autoServerId, true)) {
                        resolveAutoId()
                    } else {
                        configId
                    }
                    proxyIds.add(id)
                    Logger.i(
                        LOG_TAG_PROXY,
                        "$tag catch-all config is active: ${it.id}, ${it.name} => add $id"
                    )
                }
            } catch (e: Exception) {
                Logger.e(LOG_TAG_PROXY, "$tag; error processing catch-all config ${it.id}: ${e.message}", e)
            }
        }

        Logger.i(LOG_TAG_PROXY, "$tag returning proxy ids for $uid, $ip, $port, $domain: $proxyIds")
        return proxyIds
    }
}
