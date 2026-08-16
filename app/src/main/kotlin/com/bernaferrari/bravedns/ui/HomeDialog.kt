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

import androidx.compose.runtime.saveable.Saver
import com.bernaferrari.bravedns.service.AppUpdater

/**
 * Activity-hosted dialogs. Payloads are primitive/enum so [Saver] can survive process
 * recreation and configuration changes.
 *
 * Rotation strategy:
 * - Restore: persist the incoming content URI as a string (intent-delivered; not a picker).
 * - Download / AlwaysOnStop / Stats / NewFeatures: persist enum + strings.
 * - AlwaysOnDisable / PrivateDns / NoLog / AccessibilityCrash: marker objects.
 * - FirstTimeVpn: marker only. The prepare [android.content.Intent] is not saveable;
 *   confirm re-calls [android.net.VpnService.prepare].
 */
internal sealed interface HomeDialog {
    data object Hidden : HomeDialog
    data class Restore(val uriString: String) : HomeDialog
    data class Download(
        val source: AppUpdater.InstallSource,
        val title: String,
        val message: String
    ) : HomeDialog

    data class AlwaysOnStop(val message: String) : HomeDialog
    data object AlwaysOnDisable : HomeDialog
    data object PrivateDns : HomeDialog
    data object FirstTimeVpn : HomeDialog
    data class Stats(val displayText: String, val dump: String) : HomeDialog
    data object NoLog : HomeDialog
    data object AccessibilityCrash : HomeDialog
    data class NewFeatures(val title: String) : HomeDialog

    companion object {
        private const val KIND_NONE = "none"
        private const val KIND_RESTORE = "restore"
        private const val KIND_DOWNLOAD = "download"
        private const val KIND_ALWAYS_ON_STOP = "always_on_stop"
        private const val KIND_ALWAYS_ON_DISABLE = "always_on_disable"
        private const val KIND_PRIVATE_DNS = "private_dns"
        private const val KIND_FIRST_TIME_VPN = "first_time_vpn"
        private const val KIND_STATS = "stats"
        private const val KIND_NO_LOG = "no_log"
        private const val KIND_ACCESSIBILITY = "accessibility"
        private const val KIND_NEW_FEATURES = "new_features"

        val Saver: Saver<HomeDialog, ArrayList<String>> = Saver(
            save = { dialog ->
                when (dialog) {
                    Hidden -> arrayListOf(KIND_NONE)
                    is Restore -> arrayListOf(KIND_RESTORE, dialog.uriString)
                    is Download -> arrayListOf(
                        KIND_DOWNLOAD,
                        dialog.source.name,
                        dialog.title,
                        dialog.message
                    )
                    is AlwaysOnStop -> arrayListOf(KIND_ALWAYS_ON_STOP, dialog.message)
                    AlwaysOnDisable -> arrayListOf(KIND_ALWAYS_ON_DISABLE)
                    PrivateDns -> arrayListOf(KIND_PRIVATE_DNS)
                    FirstTimeVpn -> arrayListOf(KIND_FIRST_TIME_VPN)
                    is Stats -> arrayListOf(KIND_STATS, dialog.displayText, dialog.dump)
                    NoLog -> arrayListOf(KIND_NO_LOG)
                    AccessibilityCrash -> arrayListOf(KIND_ACCESSIBILITY)
                    is NewFeatures -> arrayListOf(KIND_NEW_FEATURES, dialog.title)
                }
            },
            restore = { saved ->
                when (saved.firstOrNull()) {
                    KIND_RESTORE -> Restore(saved[1])
                    KIND_DOWNLOAD -> Download(
                        source = AppUpdater.InstallSource.entries.firstOrNull { it.name == saved[1] }
                            ?: AppUpdater.InstallSource.OTHER,
                        title = saved[2],
                        message = saved[3]
                    )
                    KIND_ALWAYS_ON_STOP -> AlwaysOnStop(saved[1])
                    KIND_ALWAYS_ON_DISABLE -> AlwaysOnDisable
                    KIND_PRIVATE_DNS -> PrivateDns
                    KIND_FIRST_TIME_VPN -> FirstTimeVpn
                    KIND_STATS -> Stats(saved[1], saved[2])
                    KIND_NO_LOG -> NoLog
                    KIND_ACCESSIBILITY -> AccessibilityCrash
                    KIND_NEW_FEATURES -> NewFeatures(saved[1])
                    else -> Hidden
                }
            }
        )
    }
}
