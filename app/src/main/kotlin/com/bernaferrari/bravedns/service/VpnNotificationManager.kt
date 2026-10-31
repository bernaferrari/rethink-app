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
package com.bernaferrari.bravedns.service

import Logger
import Logger.LOG_GO_LOGGER
import Logger.LOG_TAG_VPN
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.receiver.NotificationActionReceiver
import com.bernaferrari.bravedns.service.FirewallManager.NOTIF_CHANNEL_ID_FIREWALL_ALERTS
import com.bernaferrari.bravedns.ui.HomeScreenActivity
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Constants.Companion.NOTIF_INTENT_EXTRA_ACCESSIBILITY_NAME
import com.bernaferrari.bravedns.util.Constants.Companion.NOTIF_INTENT_EXTRA_ACCESSIBILITY_VALUE
import com.bernaferrari.bravedns.util.NotificationActionType
import com.bernaferrari.bravedns.util.UIUtils.getAccentColor
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.Utilities.isAtleastO

/**
 * Foreground, warning, accessibility, and memory notifications for [BraveVPNService].
 * Channel IDs, notification IDs, strings, flags, and actions match the previous in-service impl.
 */
class VpnNotificationManager(
    private val context: Context,
    private val persistentState: PersistentState,
    private val appConfig: AppConfig,
    private val isAppPaused: () -> Boolean,
    private val isAppLockEnabled: () -> Boolean,
) {
    companion object {
        private const val MAIN_CHANNEL_ID = "vpn"
        internal const val WARNING_CHANNEL_ID = "warning"

        private const val NOTIF_ACTION_MODE_RESUME = 98
        private const val NOTIF_ACTION_MODE_PAUSE = 99
        private const val NOTIF_ACTION_MODE_STOP = 100
        private const val NOTIF_ACTION_MODE_DNS_ONLY = 101
        private const val NOTIF_ACTION_MODE_DNS_FIREWALL = 102

        private const val NOTIF_ID_ACCESSIBILITY_FAILURE = 104
        // A broadcast request code beside 98-102; RefreshDatabase's actions use 103-105.
        private const val NOTIF_ACTION_MODE_PAUSE_HOUR = 110
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun buildForegroundNotification(): Notification {
        val pendingIntent =
            Utilities.getActivityPendingIntent(
                context,
                Intent(context, HomeScreenActivity::class.java).setAction("${context.packageName}.notification.foreground"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                mutable = false
            )
        var builder: NotificationCompat.Builder
        if (isAtleastO()) {
            val name: CharSequence = context.resources.getString(R.string.notif_channel_vpn_notification)
            // LOW is the lowest importance that is allowed with startForeground in Android O
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(MAIN_CHANNEL_ID, name, importance)
            channel.description = context.resources.getString(R.string.notif_channel_desc_vpn_notification)
            notificationManager.createNotificationChannel(channel)
            builder = NotificationCompat.Builder(context, MAIN_CHANNEL_ID)
        } else {
            builder = NotificationCompat.Builder(context, MAIN_CHANNEL_ID)
        }
        val isProxyEnabled = appConfig.isProxyEnabled()

        var contentTitle: String =
            when (appConfig.getBraveMode()) {
                AppConfig.BraveMode.DNS -> context.resources.getString(R.string.dns_mode_notification_title)
                AppConfig.BraveMode.FIREWALL ->
                    context.resources.getString(R.string.firewall_mode_notification_title)

                AppConfig.BraveMode.DNS_FIREWALL ->
                    if (isProxyEnabled) {
                        context.resources.getString(R.string.hybrid_mode_with_proxy_notification_title)
                    } else {
                        context.resources.getString(R.string.hybrid_mode_notification_title)
                    }
            }

        if (isAppPaused()) {
            contentTitle = context.resources.getString(R.string.pause_mode_notification_title)
        }
        builder.setSmallIcon(R.drawable.ic_notification_icon).setContentIntent(pendingIntent)
        builder.color = ContextCompat.getColor(context, getAccentColor(persistentState.theme))

        // New action button options in the notification
        // 1. Pause / Resume, Stop action button.
        // 2. RethinkDNS modes (dns & dns+firewall mode)
        // 3. No action button.
        // do not show notification action when app lock is enabled
        val notifActionType =
            if (isAppLockEnabled()) {
                NotificationActionType.NONE
            } else {
                NotificationActionType.getNotificationActionType(
                    persistentState.notificationActionType
                )
            }
        Logger.d(
            LOG_TAG_VPN,
            "notification action type: ${persistentState.notificationActionType}, $notifActionType"
        )

        when (notifActionType) {
            NotificationActionType.PAUSE_STOP -> {
                // Home's choices: paused offers Resume; protected offers Pause for 15 minutes or
                // an hour (Android shows at most three actions). Stop comes last, as on Home.
                builder.setContentTitle(contentTitle)
                if (isAppPaused()) {
                    builder.addAction(
                        0,
                        context.resources.getString(R.string.notification_action_resume_vpn),
                        makeVpnIntent(NOTIF_ACTION_MODE_RESUME, Constants.NOTIF_ACTION_RESUME_VPN)
                    )
                } else {
                    builder.addAction(
                        0,
                        context.resources.getString(R.string.notification_action_pause_15_minutes),
                        makeVpnIntent(NOTIF_ACTION_MODE_PAUSE, Constants.NOTIF_ACTION_PAUSE_VPN)
                    )
                    builder.addAction(
                        0,
                        context.resources.getString(R.string.notification_action_pause_1_hour),
                        makeVpnIntent(NOTIF_ACTION_MODE_PAUSE_HOUR, Constants.NOTIF_ACTION_PAUSE_VPN_HOUR)
                    )
                }
                builder.addAction(
                    0,
                    context.resources.getString(R.string.notification_action_stop_vpn),
                    makeVpnIntent(NOTIF_ACTION_MODE_STOP, Constants.NOTIF_ACTION_STOP_VPN)
                )
            }

            NotificationActionType.DNS_FIREWALL -> {
                val openIntent1 =
                    makeVpnIntent(NOTIF_ACTION_MODE_DNS_ONLY, Constants.NOTIF_ACTION_DNS_VPN)
                val openIntent2 =
                    makeVpnIntent(
                        NOTIF_ACTION_MODE_DNS_FIREWALL,
                        Constants.NOTIF_ACTION_DNS_FIREWALL_VPN
                    )
                val notificationAction: NotificationCompat.Action =
                    NotificationCompat.Action(
                        0,
                        context.resources.getString(R.string.notification_action_dns_mode),
                        openIntent1
                    )
                val notificationAction2: NotificationCompat.Action =
                    NotificationCompat.Action(
                        0,
                        context.resources.getString(R.string.notification_action_dns_firewall_mode),
                        openIntent2
                    )
                builder.addAction(notificationAction)
                builder.addAction(notificationAction2)
                // set content title for notifications which has actions
                builder.setContentTitle(contentTitle)
            }

            NotificationActionType.NONE -> {
                Logger.i(LOG_TAG_VPN, "No notification action")
                builder.setContentTitle(contentTitle)
            }
        }

        // from docs, Starting in Android 13 (API level 33), users can dismiss the notification
        // associated with a foreground service by default. To do so, users perform a swipe gesture
        // on the notification. On previous versions of Android, the notification can't be dismissed
        // unless the foreground service is either stopped or removed from the foreground.
        // make it ongoing to prevent that. https://github.com/celzero/rethink-app/issues/1136
        if (persistentState.persistentNotification) {
            builder.setOngoing(true)
        } else {
            builder.setOngoing(false)
        }

        // Secret notifications are not shown on the lock screen.  No need for this app to show
        // there. Only available in API >= 21
        builder = builder.setVisibility(NotificationCompat.VISIBILITY_SECRET)
        val notification = builder.build()

        if (persistentState.persistentNotification) {
            notification.flags = Notification.FLAG_ONGOING_EVENT
        } else {
            notification.flags = Notification.FLAG_NO_CLEAR
        }
        return notification
    }

    fun notifyForeground() {
        notificationManager.notify(BraveVPNService.SERVICE_ID, buildForegroundNotification())
    }

    fun showAccessibilityStoppedNotification() {
        Logger.i(LOG_TAG_VPN, "app not in use failure, show notification")

        val intent = Intent(context, HomeScreenActivity::class.java).setAction("${context.packageName}.notification.accessibility")
        intent.putExtra(
            NOTIF_INTENT_EXTRA_ACCESSIBILITY_NAME,
            NOTIF_INTENT_EXTRA_ACCESSIBILITY_VALUE
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
            val channel = NotificationChannel(NOTIF_CHANNEL_ID_FIREWALL_ALERTS, name, importance)
            channel.description = description
            notificationManager.createNotificationChannel(channel)
            builder = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID_FIREWALL_ALERTS)
        } else {
            builder = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID_FIREWALL_ALERTS)
        }

        val contentTitle: String = context.getString(R.string.lbl_action_required)
        val contentText: String =
            context.getString(R.string.accessibility_notification_content)

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
            NOTIF_ID_ACCESSIBILITY_FAILURE,
            builder.build()
        )
    }

    fun showVpnFailureNotification() {
        val vibrationPattern = longArrayOf(1000) // Vibrate for one second.
        // Show revocation warning
        val builder: NotificationCompat.Builder
        if (isAtleastO()) {
            val name: CharSequence = context.getString(R.string.notif_channel_vpn_failure)
            val description = context.getString(R.string.notif_channel_desc_vpn_failure)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(WARNING_CHANNEL_ID, name, importance)
            channel.description = description
            channel.enableVibration(true)
            channel.vibrationPattern = vibrationPattern
            notificationManager.createNotificationChannel(channel)
            builder = NotificationCompat.Builder(context, WARNING_CHANNEL_ID)
        } else {
            builder = NotificationCompat.Builder(context, WARNING_CHANNEL_ID)
            builder.setVibrate(vibrationPattern)
        }

        val pendingIntent =
            Utilities.getActivityPendingIntent(
                context,
                Intent(context, HomeScreenActivity::class.java).setAction("${context.packageName}.notification.vpn_failure"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                mutable = false
            )
        builder
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(context.resources.getText(R.string.warning_title))
            // fixme: should the string need to be changed based on failure type?
            .setContentText(context.resources.getText(R.string.notification_content))
            .setContentIntent(pendingIntent)
            // Open the main UI if possible.
            .setAutoCancel(true)
        notificationManager.notify(0, builder.build())
    }

    fun showNwEngineNotification(msg: String) {
        if (msg.isEmpty()) {
            Logger.e(LOG_GO_LOGGER, "empty msg with log level set as user")
            return
        }

        val pendingIntent =
            Utilities.getActivityPendingIntent(
                context,
                Intent(context, HomeScreenActivity::class.java).setAction("${context.packageName}.notification.network_engine"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                mutable = false
            )
        val builder =
            NotificationCompat.Builder(context, WARNING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setContentTitle(msg)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
        builder.color = ContextCompat.getColor(context, getAccentColor(persistentState.theme))
        notificationManager.notify(BraveVPNService.NW_ENGINE_NOTIFICATION_ID, builder.build())
        Logger.w(LOG_TAG_VPN, "nw eng notification: $msg")
    }

    fun showMemoryNotification() {
        val pendingIntent =
            Utilities.getActivityPendingIntent(
                context,
                Intent(context, HomeScreenActivity::class.java).setAction("${context.packageName}.notification.memory"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                mutable = false
            )

        val builder =
            NotificationCompat.Builder(context, WARNING_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.memory_notification_text))
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
        builder.color = ContextCompat.getColor(context, getAccentColor(persistentState.theme))
        notificationManager.notify(BraveVPNService.MEMORY_NOTIFICATION_ID, builder.build())
    }

    // keep in sync with RefreshDatabase#makeVpnIntent
    private fun makeVpnIntent(notificationID: Int, intentExtra: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
        intent.putExtra(Constants.NOTIFICATION_ACTION, intentExtra)
        return Utilities.getBroadcastPendingIntent(
            context,
            notificationID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            mutable = false
        )
    }
}
