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
package com.bernaferrari.bravedns.net.go

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService.NOTIFICATION_SERVICE
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.BraveVPNService.Companion.NW_ENGINE_NOTIFICATION_ID
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.HomeScreenActivity
import com.bernaferrari.bravedns.util.UIUtils.getAccentColor
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.Utilities.isAtleastO

/** Existing DNS-failure notification (channel [CHANNEL_ID]). */
internal object DnsFailureNotifications {
    const val CHANNEL_ID = "DNS_failure_channel"

    fun show(
        context: Context,
        persistentState: PersistentState,
        runOnUi: (suspend () -> Unit) -> Unit,
        dnsType: String,
        message: String
    ) {
        runOnUi {
            val notificationManager =
                context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            if (isAtleastO()) {
                val channelName = context.getString(R.string.status_dns_error)
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID, channelName, importance)
                notificationManager.createNotificationChannel(channel)
            }

            val pendingIntent =
                Utilities.getActivityPendingIntent(
                    context,
                    Intent(context, HomeScreenActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    mutable = false
                )
            val dnsErrorTxt = context.getString(R.string.status_dns_error)
            // capitalize first 3 letter of the error message (dns error => DNS Error)
            val formattedErrTxt =
                if (dnsErrorTxt.length >= 5) {
                    dnsErrorTxt.replaceRange(0, 5, dnsErrorTxt.substring(0, 5).uppercase())
                } else {
                    dnsErrorTxt
                }
            val title = context.getString(R.string.two_argument_colon, formattedErrTxt, dnsType)
            val builder =
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_icon)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
            builder.color = ContextCompat.getColor(context, getAccentColor(persistentState.theme))
            notificationManager.notify(NW_ENGINE_NOTIFICATION_ID, builder.build())
        }
    }
}
