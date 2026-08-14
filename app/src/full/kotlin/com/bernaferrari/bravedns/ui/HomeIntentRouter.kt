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
package com.bernaferrari.bravedns.ui

import android.content.Intent
import com.bernaferrari.bravedns.ui.compose.logs.DomainConnectionsInputType
import com.bernaferrari.bravedns.ui.compose.navigation.HomeNavRequest
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.viewmodel.DomainConnectionsViewModel

/**
 * Parses notification extras and [HomeScreenActivity.EXTRA_NAV_TARGET] into a typed
 * [HomeIntentResult]. Does not navigate; the Activity applies the result.
 *
 * Notification extras win over [HomeScreenActivity.EXTRA_NAV_TARGET] (same order as before).
 */
internal class HomeIntentRouter {

    fun parse(intent: Intent?): HomeIntentResult? {
        if (intent == null) return null
        parseNotification(intent)?.let { return it }
        return parseNavTarget(intent)
    }

    private fun parseNotification(intent: Intent): HomeIntentResult? {
        if (intent.extras == null) return null

        val accessibility = intent.getStringExtra(Constants.NOTIF_INTENT_EXTRA_ACCESSIBILITY_NAME)
        if (Constants.NOTIF_INTENT_EXTRA_ACCESSIBILITY_VALUE == accessibility) {
            return HomeIntentResult.AccessibilityCrash
        }

        val newApp = intent.getStringExtra(Constants.NOTIF_INTENT_EXTRA_NEW_APP_NAME)
        if (Constants.NOTIF_INTENT_EXTRA_NEW_APP_VALUE == newApp) {
            val uid =
                intent.getIntExtra(Constants.NOTIF_INTENT_EXTRA_APP_UID, Constants.INVALID_UID)
            return if (uid > 0) {
                HomeIntentResult.Navigate(HomeNavRequest.AppInfo(uid = uid))
            } else {
                HomeIntentResult.Ignored
            }
        }

        val wg = intent.getStringExtra(Constants.NOTIF_WG_PERMISSION_NAME)
        if (Constants.NOTIF_WG_PERMISSION_VALUE == wg) {
            return HomeIntentResult.Navigate(HomeNavRequest.WgMain)
        }

        val hasRpnAccountAlert =
            intent.getStringExtra(Constants.NOTIF_INTENT_EXTRA_IAB_CONFLICT_NAME) ==
                Constants.NOTIF_INTENT_EXTRA_IAB_CONFLICT_VALUE ||
                intent.getStringExtra(Constants.NOTIF_INTENT_EXTRA_IAB_DEVICE_NOT_REGISTERED_NAME) ==
                Constants.NOTIF_INTENT_EXTRA_IAB_DEVICE_NOT_REGISTERED_VALUE ||
                intent.getStringExtra(Constants.NOTIF_INTENT_EXTRA_IAB_DEVICE_AUTH_ERROR_NAME) ==
                Constants.NOTIF_INTENT_EXTRA_IAB_DEVICE_AUTH_ERROR_VALUE
        if (hasRpnAccountAlert) {
            return HomeIntentResult.RpnAccountAlert
        }

        return null
    }

    private fun parseNavTarget(intent: Intent): HomeIntentResult? {
        val target = intent.getStringExtra(HomeScreenActivity.EXTRA_NAV_TARGET) ?: return null
        return when (target) {
            HomeScreenActivity.NAV_TARGET_DOMAIN_CONNECTIONS -> {
                val typeValue = intent.getIntExtra(HomeScreenActivity.EXTRA_DC_TYPE, 0)
                val flag = intent.getStringExtra(HomeScreenActivity.EXTRA_DC_FLAG).orEmpty()
                val domain = intent.getStringExtra(HomeScreenActivity.EXTRA_DC_DOMAIN).orEmpty()
                val asn = intent.getStringExtra(HomeScreenActivity.EXTRA_DC_ASN).orEmpty()
                val ip = intent.getStringExtra(HomeScreenActivity.EXTRA_DC_IP).orEmpty()
                val isBlocked = intent.getBooleanExtra(HomeScreenActivity.EXTRA_DC_IS_BLOCKED, false)
                val timeCategoryValue =
                    intent.getIntExtra(HomeScreenActivity.EXTRA_DC_TIME_CATEGORY, 0)
                val type = DomainConnectionsInputType.fromValue(typeValue)
                val timeCategory =
                    DomainConnectionsViewModel.TimeCategory.fromValue(timeCategoryValue)
                        ?: DomainConnectionsViewModel.TimeCategory.ONE_HOUR
                HomeIntentResult.Navigate(
                    HomeNavRequest.DomainConnections(
                        type = type,
                        flag = flag,
                        domain = domain,
                        asn = asn,
                        ip = ip,
                        isBlocked = isBlocked,
                        timeCategory = timeCategory
                    )
                )
            }

            HomeScreenActivity.NAV_TARGET_APP_INFO -> {
                val uid = intent.getIntExtra(
                    HomeScreenActivity.EXTRA_APP_INFO_UID,
                    Constants.INVALID_UID
                )
                if (uid == Constants.INVALID_UID) {
                    HomeIntentResult.Ignored
                } else {
                    HomeIntentResult.Navigate(HomeNavRequest.AppInfo(uid = uid))
                }
            }

            HomeScreenActivity.NAV_TARGET_NETWORK_LOGS ->
                HomeIntentResult.Navigate(HomeNavRequest.NetworkLogs())

            HomeScreenActivity.NAV_TARGET_WG_MAIN ->
                HomeIntentResult.Navigate(HomeNavRequest.WgMain)

            else -> null
        }
    }
}

internal sealed interface HomeIntentResult {
    data class Navigate(val request: HomeNavRequest) : HomeIntentResult
    data object AccessibilityCrash : HomeIntentResult
    data object RpnAccountAlert : HomeIntentResult
    data object Ignored : HomeIntentResult
}
