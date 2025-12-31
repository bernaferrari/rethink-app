/*
Copyright 2020 RethinkDNS and its authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.celzero.bravedns.R
import com.celzero.bravedns.service.BraveVPNService
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.firewall.FirewallSettingsScreen
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.INTENT_UID
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.celzero.bravedns.util.Themes.Companion.getCurrentTheme
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import org.koin.android.ext.android.inject

class FirewallActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            RethinkTheme {
                FirewallSettingsScreen(
                    onUniversalFirewallClick = { openUniversalFirewallScreen() },
                    onCustomIpDomainClick = { openCustomIpScreen() },
                    onAppWiseIpDomainClick = { openAppWiseIpScreen() }
                )
            }
        }

        observeAppState()
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun openCustomIpScreen() {
        val intent = Intent(this, CustomRulesActivity::class.java)
        intent.putExtra(
            Constants.VIEW_PAGER_SCREEN_TO_LOAD,
            CustomRulesActivity.Tabs.IP_RULES.screen
        )
        intent.putExtra(
            CustomRulesActivity.INTENT_RULES,
            CustomRulesActivity.RULES.APP_SPECIFIC_RULES.type
        )
        intent.putExtra(INTENT_UID, UID_EVERYBODY)
        startActivity(intent)
    }

    private fun openAppWiseIpScreen() {
        val intent = Intent(this, CustomRulesActivity::class.java)
        intent.putExtra(
            Constants.VIEW_PAGER_SCREEN_TO_LOAD,
            CustomRulesActivity.Tabs.IP_RULES.screen
        )
        intent.putExtra(CustomRulesActivity.INTENT_RULES, CustomRulesActivity.RULES.ALL_RULES.type)
        intent.putExtra(INTENT_UID, UID_EVERYBODY)
        startActivity(intent)
    }

    private fun openUniversalFirewallScreen() {
        val intent = Intent(this, UniversalFirewallSettingsActivity::class.java)
        startActivity(intent)
    }

    private fun observeAppState() {
        VpnController.connectionStatus.observe(this) {
            if (it == BraveVPNService.State.PAUSED) {
                startActivity(Intent().setClass(this, PauseActivity::class.java))
                finish()
            }
        }
    }
}
