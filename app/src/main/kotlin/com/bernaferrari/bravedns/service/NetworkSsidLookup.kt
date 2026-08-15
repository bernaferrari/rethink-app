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
import Logger.LOG_TAG_VPN
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.NOTIF_ID_SSID_LOCATION_PERMISSION
import com.bernaferrari.bravedns.service.FirewallManager.NOTIF_CHANNEL_ID_FIREWALL_ALERTS
import com.bernaferrari.bravedns.service.WireguardManager.NOTIF_CHANNEL_ID_WIREGUARD_ALERTS
import com.bernaferrari.bravedns.ui.HomeScreenActivity
import com.bernaferrari.bravedns.util.Constants.Companion.NOTIF_WG_PERMISSION_NAME
import com.bernaferrari.bravedns.util.Constants.Companion.NOTIF_WG_PERMISSION_VALUE
import com.bernaferrari.bravedns.util.SsidPermissionManager
import com.bernaferrari.bravedns.util.UIUtils.getAccentColor
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.Utilities.isAtleastO
import com.bernaferrari.bravedns.util.Utilities.isAtleastS

/**
 * Wi-Fi SSID lookup and the location-permission notification used by [ConnectionMonitor].
 */
class NetworkSsidLookup(
    private val context: Context,
    private val persistentState: PersistentState,
    private val wifiManager: () -> WifiManager,
) {

    companion object {
        private const val UNKNOWN_SSID = "<unknown ssid>"
    }

    /**
     * Fetches the SSID for the given network if it's a WiFi network.
     *
     * @param network The network to get the SSID for
     * @return The SSID of the network if it's a WiFi network and SSID is available,
     *         null otherwise or if the network is not WiFi
     */
    fun getNetworkSSID(network: Network?, cap: NetworkCapabilities?): String? {
        if (network == null) {
            Logger.d(LOG_TAG_CONNECTION, "getNetworkSSID: network is null")
            return null
        }

        // Check if this is a WiFi network
        if (cap?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            Logger.v(
                LOG_TAG_CONNECTION,
                "getNetworkSSID: network ${network.networkHandle} is not a WiFi network"
            )
            return null
        }

        try {
            if (isAtleastS()) {
                // Check if transportInfo is actually WifiInfo before casting
                val transportInfo = cap.transportInfo
                val isTransportInfoWifi = transportInfo is WifiInfo
                var ssid: String? = null
                if (isTransportInfoWifi) {
                    ssid = transportInfo.ssid
                    extractCleanSsid(ssid)?.let { clean ->
                        Logger.i(
                            LOG_TAG_CONNECTION,
                            "getNetworkSSID(S): $clean (${network.networkHandle})"
                        )
                        return clean
                    }
                }
                Logger.v(
                    LOG_TAG_CONNECTION,
                    "isWifiInfo? $isTransportInfoWifi, (${transportInfo?.javaClass?.simpleName}), ssid: $ssid , falling back to WifiManager"
                )
            }

            @Suppress("DEPRECATION")
            val wifiInfo: WifiInfo? = wifiManager().connectionInfo
            if (wifiInfo == null) {
                Logger.v(LOG_TAG_CONNECTION, "getNetworkSSID: WifiInfo is null")
                return null
            }

            extractCleanSsid(wifiInfo.ssid)?.let { clean ->
                Logger.i(
                    LOG_TAG_CONNECTION,
                    "getNetworkSSID(WM): $clean (${network.networkHandle})"
                )
                return clean
            }

            Logger.w(
                LOG_TAG_CONNECTION,
                "getNetworkSSID: SSID is null or unknown for network ${network.networkHandle}"
            )
            showNotificationIfNeeded()
            return null

        } catch (e: SecurityException) {
            Logger.w(LOG_TAG_CONNECTION, "getNetworkSSID: SecurityException accessing WiFi info", e)
            return null
        } catch (e: IllegalStateException) {
            Logger.w(
                LOG_TAG_CONNECTION,
                "getNetworkSSID: Exception getting SSID for network ${network.networkHandle}",
                e
            )
            return null
        }
    }

    private fun extractCleanSsid(ssid: String?): String? {
        if (ssid.isNullOrEmpty() || ssid == UNKNOWN_SSID) return null
        return ssid.removeSurrounding("\"")
    }

    private fun showNotificationIfNeeded() {
        val wgs = WireguardManager.getActiveSsidEnabledConfigs()
        if (wgs.isEmpty()) return

        val hasPermission = SsidPermissionManager.hasRequiredPermissions(context)
        val locationEnabled = SsidPermissionManager.isLocationEnabled(context)
        if (hasPermission && locationEnabled) return

        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // check if notification is already active to prevent duplicates
        val activeNotifications = notificationManager.activeNotifications
        val isNotificationAlreadyActive = activeNotifications.any { notification ->
            notification.id == NOTIF_ID_SSID_LOCATION_PERMISSION
        }
        if (isNotificationAlreadyActive) {
            Logger.i(LOG_TAG_VPN, "ssid wgs: notification already active, skipping")
            return
        }

        Logger.w(LOG_TAG_VPN, "ssid wgs: missing permissions, show notification")
        val intent = Intent(context, HomeScreenActivity::class.java)
        intent.putExtra(
            NOTIF_WG_PERMISSION_NAME,
            NOTIF_WG_PERMISSION_VALUE
        )
        val pendingIntent =
            Utilities.getActivityPendingIntent(
                context,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                mutable = false
            )

        var builder: NotificationCompat.Builder
        if (isAtleastO()) {
            val name: CharSequence = context.getString(R.string.notif_channel_firewall_alerts)
            val description = context.getString(R.string.notif_channel_desc_firewall_alerts)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIF_CHANNEL_ID_WIREGUARD_ALERTS, name, importance)
            channel.description = description
            notificationManager.createNotificationChannel(channel)
            builder = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID_WIREGUARD_ALERTS)
        } else {
            builder = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID_WIREGUARD_ALERTS)
        }

        val contentTitle: String = context.getString(R.string.lbl_action_required)
        val contentText: String =
            context.getString(R.string.location_enable_explanation, context.getString(R.string.lbl_ssids))

        builder
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(contentTitle)
            .setContentIntent(pendingIntent)
            .setContentText(contentText)

        builder.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
        builder.color = ContextCompat.getColor(context, getAccentColor(persistentState.theme))

        // Secret notifications are not shown on the lock screen.  No need for this app to show
        // there.
        // Only available in API >= 21
        builder = builder.setVisibility(NotificationCompat.VISIBILITY_SECRET)

        // Cancel the notification after clicking.
        builder.setAutoCancel(true)

        notificationManager.notify(
            NOTIF_CHANNEL_ID_FIREWALL_ALERTS,
            NOTIF_ID_SSID_LOCATION_PERMISSION,
            builder.build()
        )
    }
}
