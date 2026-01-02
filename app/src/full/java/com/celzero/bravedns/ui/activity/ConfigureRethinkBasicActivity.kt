/*
 * Copyright 2022 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.download.AppDownloadManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.dns.ConfigureRethinkBasicScreen
import com.celzero.bravedns.ui.compose.dns.ConfigureRethinkScreenType
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.LocalBlocklistPacksMapViewModel
import com.celzero.bravedns.viewmodel.RemoteBlocklistPacksMapViewModel
import com.celzero.bravedns.viewmodel.RethinkEndpointViewModel
import com.celzero.bravedns.viewmodel.RethinkLocalFileTagViewModel
import com.celzero.bravedns.viewmodel.RethinkRemoteFileTagViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

// TODO-refactor: Consider migrating to navigation component and passing parameters via nav args
class ConfigureRethinkBasicActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val appDownloadManager by inject<AppDownloadManager>()
    private val appConfig by inject<AppConfig>()

    private val rethinkEndpointViewModel: RethinkEndpointViewModel by viewModel()
    private val remoteFileTagViewModel: RethinkRemoteFileTagViewModel by viewModel()
    private val localFileTagViewModel: RethinkLocalFileTagViewModel by viewModel()
    private val remoteBlocklistPacksMapViewModel: RemoteBlocklistPacksMapViewModel by viewModel()
    private val localBlocklistPacksMapViewModel: LocalBlocklistPacksMapViewModel by viewModel()

    enum class FragmentLoader {
        REMOTE,
        LOCAL,
        DB_LIST
    }

    companion object {
        const val INTENT = "RethinkDns_Intent"
        const val RETHINK_BLOCKLIST_TYPE = "RethinkBlocklistType"
        const val RETHINK_BLOCKLIST_NAME = "RethinkBlocklistName"
        const val RETHINK_BLOCKLIST_URL = "RethinkBlocklistUrl"
        const val UID = "UID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        val type = intent.getIntExtra(INTENT, FragmentLoader.REMOTE.ordinal)
        val screen = FragmentLoader.entries[type]
        val uid = intent.getIntExtra(UID, Constants.MISSING_UID)
        val remoteName = intent.getStringExtra(RETHINK_BLOCKLIST_NAME) ?: ""
        val remoteUrl = intent.getStringExtra(RETHINK_BLOCKLIST_URL) ?: ""

        val screenType = when (screen) {
            FragmentLoader.REMOTE -> ConfigureRethinkScreenType.REMOTE
            FragmentLoader.LOCAL -> ConfigureRethinkScreenType.LOCAL
            FragmentLoader.DB_LIST -> ConfigureRethinkScreenType.DB_LIST
        }

        setContent {
            RethinkTheme {
                ConfigureRethinkBasicScreen(
                    screenType = screenType,
                    remoteName = remoteName,
                    remoteUrl = remoteUrl,
                    uid = uid,
                    persistentState = persistentState,
                    appConfig = appConfig,
                    appDownloadManager = appDownloadManager,
                    rethinkEndpointViewModel = rethinkEndpointViewModel,
                    remoteFileTagViewModel = remoteFileTagViewModel,
                    localFileTagViewModel = localFileTagViewModel,
                    remoteBlocklistPacksMapViewModel = remoteBlocklistPacksMapViewModel,
                    localBlocklistPacksMapViewModel = localBlocklistPacksMapViewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }
}
