/*
 * Copyright 2024 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.ui.compose.settings

import android.text.format.DateUtils
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.net.doh.Transaction
import com.bernaferrari.bravedns.service.ProxyManager
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.service.WireguardManager
import com.bernaferrari.bravedns.util.UIUtils
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.RouterStats

internal data class ProxyScreenState(
    val canEnableProxy: Boolean,
    val socks5Enabled: Boolean,
    val httpEnabled: Boolean,
    val orbotEnabled: Boolean,
    val wireguardDescription: String,
    val socks5Description: String,
    val httpDescription: String,
    val orbotDescription: String
)

internal suspend fun buildProxyScreenState(
    appConfig: AppConfig,
    defaultSocks5Description: String,
    defaultHttpDescription: String,
    httpProxyDescriptionTemplate: String,
    defaultSocks5DescriptionNoApp: String,
    defaultSocks5DescriptionWithApp: String,
    orbotDisabledDescription: String,
    orbotStatus2Description: String,
    orbotStatus1Template: String,
    orbotStatus3Template: String,
    orbotStatusArgDns: String,
    orbotStatusArgProxy: String,
    defaultWireguardDescription: String,
    statusTextById: Map<Int, String>,
    statusFailingText: String,
    statusWaitingText: String,
    wireguardVersionTemplate: String,
    ciIpLabelTemplate: String
): ProxyScreenState {
    val canEnableProxy = appConfig.canEnableProxy()
    val socks5Enabled = appConfig.isCustomSocks5Enabled()
    val httpEnabled = appConfig.isCustomHttpProxyEnabled()
    val orbotEnabled = appConfig.isOrbotProxyEnabled()

    val socks5Description =
        if (!socks5Enabled) {
            defaultSocks5Description
        } else {
            formatSocks5Description(
                runCatching { appConfig.getSocks5ProxyDetails() }.getOrNull(),
                defaultSocks5Description,
                defaultSocks5DescriptionNoApp,
                defaultSocks5DescriptionWithApp
            )
        }

    val httpDescription =
        if (!httpEnabled) {
            defaultHttpDescription
        } else {
            val endpoint = runCatching { appConfig.getHttpProxyDetails() }.getOrNull()
            val host = endpoint?.proxyIP ?: ""
            if (host.isBlank()) {
                defaultHttpDescription
            } else {
                String.format(httpProxyDescriptionTemplate, host)
            }
        }

    val orbotDescription =
        formatOrbotDescription(
            appConfig,
            orbotDisabledDescription,
            orbotStatus2Description,
            orbotStatus1Template,
            orbotStatus3Template,
            orbotStatusArgDns,
            orbotStatusArgProxy
        )
    val wireguardDescription =
        formatWireguardDescription(
            defaultWireguardDescription,
            statusTextById,
            statusWaitingText,
            statusFailingText,
            wireguardVersionTemplate,
            ciIpLabelTemplate
        )

    return ProxyScreenState(
        canEnableProxy = canEnableProxy,
        socks5Enabled = socks5Enabled,
        httpEnabled = httpEnabled,
        orbotEnabled = orbotEnabled,
        wireguardDescription = wireguardDescription,
        socks5Description = socks5Description,
        httpDescription = httpDescription,
        orbotDescription = orbotDescription
    )
}

internal suspend fun formatWireguardDescription(
    defaultDescription: String,
    statusTextById: Map<Int, String>,
    statusWaitingText: String,
    statusFailingText: String,
    wireguardVersionTemplate: String,
    ciIpLabelTemplate: String
): String {
    val activeWgs = WireguardManager.getActiveConfigs()
    if (activeWgs.isEmpty()) {
        return defaultDescription
    }

    val details = StringBuilder()
    activeWgs.forEach {
        val id = ProxyManager.ID_WG_BASE + it.getId()
        val statusPair = VpnController.getProxyStatusById(id)
        val stats = VpnController.getProxyStats(id)
        val dnsStatusId = VpnController.getDnsStatus(id)

        val statusText =
            if (statusPair.first == Backend.TPU) {
                statusTextById[UIUtils.ProxyStatus.TPU.id] ?: statusFailingText
            } else if (isDnsError(dnsStatusId)) {
                statusFailingText
            } else {
                getProxyStatusText(
                    statusPair = statusPair,
                    stats = stats,
                    statusTextById = statusTextById,
                    statusWaitingText = statusWaitingText,
                    statusFailingText = statusFailingText,
                    wireguardVersionTemplate = wireguardVersionTemplate
                )
            }

        details.append(
            String.format(
                ciIpLabelTemplate,
                it.getName(),
                statusText.padStart(1, ' ')
            )
        )
        details.append('\n')
    }

    return details.toString().trimEnd()
}

internal fun isDnsError(statusId: Int?): Boolean {
    if (statusId == null) return true

    val s = Transaction.Status.fromId(statusId)
    return s == Transaction.Status.BAD_QUERY ||
            s == Transaction.Status.BAD_RESPONSE ||
            s == Transaction.Status.NO_RESPONSE ||
            s == Transaction.Status.SEND_FAIL ||
            s == Transaction.Status.CLIENT_ERROR ||
            s == Transaction.Status.INTERNAL_ERROR ||
            s == Transaction.Status.TRANSPORT_ERROR
}

internal fun getProxyStatusText(
    statusPair: Pair<Int?, String>,
    stats: RouterStats?,
    statusTextById: Map<Int, String>,
    statusWaitingText: String,
    statusFailingText: String,
    wireguardVersionTemplate: String
): String {
    val status = UIUtils.ProxyStatus.entries.find { it.id == statusPair.first }
    if (status == null) {
        val txt =
            if (!statusPair.second.isNullOrBlank()) {
                "$statusWaitingText (${statusPair.second})"
            } else {
                statusWaitingText
            }
        return txt.replaceFirstChar(Char::titlecase)
    }

    val now = System.currentTimeMillis()
    val lastOk = stats?.lastOK ?: 0L
    val since = stats?.since ?: 0L
    if (now - since > WireguardManager.WG_UPTIME_THRESHOLD && lastOk == 0L) {
        return statusFailingText
    }

    val baseText =
        statusTextById.getValue(status.id)
    val handshakeTime =
        if (stats != null && stats.lastOK > 0L) {
            DateUtils.getRelativeTimeSpanString(
                stats.lastOK,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
                .toString()
        } else {
            null
        }

    return if (stats?.lastOK != 0L && handshakeTime != null) {
        String.format(wireguardVersionTemplate, baseText, handshakeTime)
    } else {
        baseText
    }
}
