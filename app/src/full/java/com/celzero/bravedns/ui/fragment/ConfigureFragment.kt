/*
 * Copyright 2023 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.celzero.bravedns.R
import com.celzero.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.celzero.bravedns.ui.activity.AdvancedSettingActivity
import com.celzero.bravedns.ui.activity.AntiCensorshipActivity
import com.celzero.bravedns.ui.activity.AppListActivity
import com.celzero.bravedns.ui.activity.DnsDetailActivity
import com.celzero.bravedns.ui.activity.FirewallActivity
import com.celzero.bravedns.ui.activity.MiscSettingsActivity
import com.celzero.bravedns.ui.activity.NetworkLogsActivity
import com.celzero.bravedns.ui.activity.ProxySettingsActivity
import com.celzero.bravedns.ui.activity.TunnelSettingsActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.celzero.bravedns.ui.compose.configure.ConfigureScreen
import com.celzero.bravedns.ui.compose.theme.RethinkTheme

class ConfigureFragment : Fragment() {

    private val miscSettingsResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == MiscSettingsActivity.THEME_CHANGED_RESULT) {
                requireActivity().recreate()
            }
        }

    enum class ScreenType {
        APPS,
        DNS,
        FIREWALL,
        PROXY,
        VPN,
        OTHERS,
        LOGS,
        ANTI_CENSORSHIP,
        ADVANCED
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                RethinkTheme {
                    ConfigureScreen(
                        isDebug = DEBUG,
                        onAppsClick = { startActivity(ScreenType.APPS) },
                        onDnsClick = { startActivity(ScreenType.DNS) },
                        onFirewallClick = { startActivity(ScreenType.FIREWALL) },
                        onProxyClick = { startActivity(ScreenType.PROXY) },
                        onNetworkClick = { startActivity(ScreenType.VPN) },
                        onOthersClick = { startActivity(ScreenType.OTHERS) },
                        onLogsClick = { startActivity(ScreenType.LOGS) },
                        onAntiCensorshipClick = { startActivity(ScreenType.ANTI_CENSORSHIP) },
                        onAdvancedClick = { startActivity(ScreenType.ADVANCED) }
                    )
                }
            }
        }
    }

    private fun startActivity(type: ScreenType) {
        val intent =
            when (type) {
                ScreenType.APPS -> Intent(requireContext(), AppListActivity::class.java)
                ScreenType.DNS -> Intent(requireContext(), DnsDetailActivity::class.java)
                ScreenType.FIREWALL -> Intent(requireContext(), FirewallActivity::class.java)
                ScreenType.PROXY -> Intent(requireContext(), ProxySettingsActivity::class.java)
                ScreenType.VPN -> Intent(requireContext(), TunnelSettingsActivity::class.java)
                ScreenType.OTHERS -> Intent(requireContext(), MiscSettingsActivity::class.java)
                ScreenType.LOGS -> Intent(requireContext(), NetworkLogsActivity::class.java)
                ScreenType.ANTI_CENSORSHIP -> Intent(requireContext(), AntiCensorshipActivity::class.java)
                ScreenType.ADVANCED -> Intent(requireContext(), AdvancedSettingActivity::class.java)
            }

        if (type == ScreenType.OTHERS) {
            miscSettingsResultLauncher.launch(intent)
        } else {
            startActivity(intent)
        }
    }
}
