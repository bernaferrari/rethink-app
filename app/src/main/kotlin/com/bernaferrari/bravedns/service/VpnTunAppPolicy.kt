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
import android.content.pm.PackageManager
import android.net.Network
import android.net.VpnService
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.util.OrbotHelper
import com.bernaferrari.bravedns.util.Utilities.isPlayStoreFlavour

/**
 * Allowed/disallowed apps and bypass policy for the VPN builder.
 */
class VpnTunAppPolicy(
    private val service: VpnService,
    private val persistentState: PersistentState,
    private val appConfig: AppConfig,
) {
    suspend fun newBuilder(
        underlays: Array<Network>?,
        vpnLockdown: Boolean,
        isPaused: Boolean,
        excludedApps: Set<String>,
    ): VpnService.Builder {
        var builder = service.Builder()
        if (!vpnLockdown && !isPlayStoreFlavour() && canAllowBypass()) {
            Logger.i(LOG_TAG_VPN, "allow apps to bypass vpn on-demand")
            builder = builder.allowBypass()
            // TODO: should allowFamily be set?
            // family must be either AF_INET (for IPv4) or AF_INET6 (for IPv6)
        }
        builder.setUnderlyingNetworks(underlays)
        Logger.d(LOG_TAG_VPN, "builder: set underlying networks: ${underlays?.joinToString()}")

        // now that we set metered based on user preference, earlier it was always set to false
        // as cloud backups were failing thinking that the VPN connection is metered
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            builder.setMetered(persistentState.setVpnBuilderToMetered)
            Logger.d(LOG_TAG_VPN, "builder: set metered: ${persistentState.setVpnBuilderToMetered}")
        }

        // route rethink traffic in rethink based on the user selection
        if (!persistentState.routeRethinkInRethink) {
            Logger.i(LOG_TAG_VPN, "builder: exclude rethink app from builder")
            addDisallowedApplication(builder, service.packageName)
        } else {
            Logger.i(LOG_TAG_VPN, "builder: route rethink traffic in rethink")
            // no-op
        }

        if (isPaused) { // exclude all non-firewalled apps and be done
            if (vpnLockdown) {
                Logger.i(LOG_TAG_VPN, "paused but vpn is lockdown; cannot exclude apps")
                return builder
            }
            val nonFirewalledApps = FirewallManager.getNonFirewalledAppsPackageNames()
            val packages = nonFirewalledApps.map { it.packageName }
            Logger.i(LOG_TAG_VPN, "paused, exclude non-firewalled apps, size: ${packages.count()}")
            addDisallowedApplications(builder, packages)
            return builder
        }

        // re-hydrate exclude-apps incase it has changed in the interim
        if (appConfig.determineFirewallMode().isFirewallSinkMode()) {
            addAllowedApplication(builder, excludedApps)
        } else {
            // ignore excluded-apps settings when vpn is lockdown because
            // those apps would lose all internet connectivity, otherwise
            if (!vpnLockdown) {
                Logger.i(LOG_TAG_VPN, "builder, vpn is not lockdown, exclude-apps $excludedApps")
                addDisallowedApplications(builder, excludedApps)
            } else {
                Logger.w(LOG_TAG_VPN, "builder, vpn is lockdown, ignoring exclude-apps list")
            }
        }

        if (appConfig.isCustomSocks5Enabled()) {
            // For Socks5 if there is a app selected, add that app in excluded list
            val socks5ProxyEndpoint = appConfig.getConnectedSocks5Proxy()
            val appName =
                socks5ProxyEndpoint?.proxyAppName
                    ?: service.getString(R.string.settings_app_list_default_app)
            if (!vpnLockdown && isExcludeProxyApp(appName)) {
                Logger.i(LOG_TAG_VPN, "exclude app for socks5, pkg: $appName")
                addDisallowedApplication(builder, appName)
            } else {
                Logger.i(LOG_TAG_VPN, "socks5(exclude): app not set or exclude not possible")
            }
        }

        if (!vpnLockdown && appConfig.isOrbotProxyEnabled() && isExcludeProxyApp(service.getString(R.string.orbot))) {
            Logger.i(LOG_TAG_VPN, "exclude orbot app")
            addDisallowedApplication(builder, OrbotHelper.ORBOT_PACKAGE_NAME)
        }

        if (appConfig.isCustomHttpProxyEnabled()) {
            // For HTTP proxy if there is a app selected, add that app in excluded list
            val httpProxyEndpoint = appConfig.getConnectedHttpProxy()
            val appName =
                httpProxyEndpoint?.proxyAppName ?: service.getString(R.string.settings_app_list_default_app)
            if (!vpnLockdown && isExcludeProxyApp(appName)) {
                Logger.i(LOG_TAG_VPN, "exclude app for http proxy, pkg: $appName")
                addDisallowedApplication(builder, appName)
            } else {
                Logger.i(LOG_TAG_VPN, "http proxy(exclude): app not set or exclude not possible")
            }
        }

        if (appConfig.isDnsProxyActive()) {
            // For DNS proxy mode, if any app is set then exclude the application from the list
            val dnsProxyEndpoint = appConfig.getSelectedDnsProxyDetails()
            val appName =
                dnsProxyEndpoint?.proxyAppName ?: service.getString(R.string.settings_app_list_default_app)
            if (!vpnLockdown && isExcludeProxyApp(appName)) {
                Logger.i(LOG_TAG_VPN, "exclude app for dns proxy, pkg: $appName")
                addDisallowedApplication(builder, appName)
            } else {
                Logger.i(LOG_TAG_VPN, "dns proxy(exclude): app not set or exclude not possible")
            }
        }

        return builder
    }

    private fun canAllowBypass(): Boolean {
        return persistentState.allowBypass &&
                !appConfig.isProxyEnabled()
    }

    private fun isExcludeProxyApp(appName: String?): Boolean {
        // user settings to exclude apps in proxy mode
        if (!persistentState.excludeAppsInProxy) {
            Logger.i(LOG_TAG_VPN, "exclude apps in proxy is disabled")
            return false
        }

        return appName?.equals(service.getString(R.string.settings_app_list_default_app)) == false
    }

    private fun addDisallowedApplication(builder: VpnService.Builder, pkg: String) {
        try {
            Logger.d(LOG_TAG_VPN, "builder: exclude app: $pkg")
            builder.addDisallowedApplication(pkg)
        } catch (e: PackageManager.NameNotFoundException) {
            Logger.w(LOG_TAG_VPN, "builder: skip adding disallowed app ($pkg)", e)
        }
    }

    private fun addDisallowedApplications(builder: VpnService.Builder, packages: Collection<String>) {
        packages.forEach { addDisallowedApplication(builder, it) }
    }

    private fun addAllowedApplication(builder: VpnService.Builder, packages: Set<String>) {
        packages.forEach {
            try {
                builder.addAllowedApplication(it)
            } catch (e: PackageManager.NameNotFoundException) {
                Logger.w(LOG_TAG_VPN, "skip adding allowed app ($it)", e)
            }
        }
    }
}
