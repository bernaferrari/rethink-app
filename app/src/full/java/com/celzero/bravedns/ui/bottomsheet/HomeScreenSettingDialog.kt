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
package com.celzero.bravedns.ui.bottomsheet

import Logger
import Logger.LOG_TAG_UI
import Logger.LOG_TAG_VPN
import android.content.Intent
import android.content.res.Configuration
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.databinding.BottomSheetHomeScreenBinding
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.activity.ProxySettingsActivity
import com.celzero.bravedns.ui.activity.WgMainActivity
import com.celzero.bravedns.util.Constants.Companion.INIT_TIME_MS
import com.celzero.bravedns.util.SsidPermissionManager
import com.celzero.bravedns.util.Themes.Companion.getBottomsheetCurrentTheme
import com.celzero.bravedns.util.UIUtils.htmlToSpannedText
import com.celzero.bravedns.util.UIUtils.openVpnProfile
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HomeScreenSettingDialog(private val activity: FragmentActivity) : KoinComponent {
    private val binding = BottomSheetHomeScreenBinding.inflate(LayoutInflater.from(activity))
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val appConfig by inject<AppConfig>()
    private val persistentState by inject<PersistentState>()

    init {
        dialog.setContentView(binding.root)
        dialog.setOnShowListener {
            dialog.useTransparentNoDimBackground()
            dialog.window?.let { window ->
                if (isAtleastQ()) {
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    controller.isAppearanceLightNavigationBars = false
                    window.isNavigationBarContrastEnforced = false
                }
            }
        }
        initView()
        updateUptime()
        initializeClickListeners()
    }

    fun show() {
        dialog.show()
        handleLockdownModeIfNeeded()
    }

    private fun getThemeId(): Int {
        val isDark =
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return getBottomsheetCurrentTheme(isDark, persistentState.theme)
    }

    private fun initView() {
        binding.bsHomeScreenConnectedStatus.text = getConnectionStatus()
        val selectedIndex = appConfig.getBraveMode().mode
        Logger.d(LOG_TAG_VPN, "Home screen bottom sheet selectedIndex: $selectedIndex")
        updateStatus(selectedIndex)
    }

    private fun updateStatus(selectedState: Int) {
        when (selectedState) {
            AppConfig.BraveMode.DNS.mode -> {
                binding.bsHomeScreenRadioDns.isChecked = true
            }
            AppConfig.BraveMode.FIREWALL.mode -> {
                binding.bsHomeScreenRadioFirewall.isChecked = true
            }
            AppConfig.BraveMode.DNS_FIREWALL.mode -> {
                binding.bsHomeScreenRadioDnsFirewall.isChecked = true
            }
            else -> {
                binding.bsHomeScreenRadioDnsFirewall.isChecked = true
            }
        }
    }

    private fun initializeClickListeners() {
        binding.bsHomeScreenRadioDns.setOnCheckedChangeListener { _: CompoundButton, isSelected: Boolean ->
            handleDnsMode(isSelected)
        }

        binding.bsHomeScreenRadioFirewall.setOnCheckedChangeListener {
            _: CompoundButton,
            isSelected: Boolean ->
            handleFirewallMode(isSelected)
        }

        binding.bsHomeScreenRadioDnsFirewall.setOnCheckedChangeListener {
            _: CompoundButton,
            isSelected: Boolean ->
            handleDnsFirewallMode(isSelected)
        }

        binding.bsHsDnsRl.setOnClickListener {
            val checked = binding.bsHomeScreenRadioDns.isChecked
            if (!checked) {
                binding.bsHomeScreenRadioDns.isChecked = true
            }
            handleDnsMode(checked)
        }

        binding.bsHsFirewallRl.setOnClickListener {
            val checked = binding.bsHomeScreenRadioFirewall.isChecked
            if (!checked) {
                binding.bsHomeScreenRadioFirewall.isChecked = true
            }
            handleFirewallMode(checked)
        }

        binding.bsHsDnsFirewallRl.setOnClickListener {
            val checked = binding.bsHomeScreenRadioDnsFirewall.isChecked
            if (!checked) {
                binding.bsHomeScreenRadioDnsFirewall.isChecked = true
            }
            handleDnsFirewallMode(checked)
        }

        binding.bsHomeScreenVpnLockdownDesc.setOnClickListener {
            if (VpnController.isVpnLockdown()) {
                openVpnProfile(activity)
            } else if (appConfig.isProxyEnabled()) {
                if (appConfig.isWireGuardEnabled()) {
                    openProxySettings(SCREEN_WG)
                } else {
                    openProxySettings(SCREEN_PROXY)
                }
            }
        }
    }

    private fun openProxySettings(screen: String) {
        val intent = if (screen == SCREEN_WG) {
            Logger.d(LOG_TAG_UI, "hmbs; invoke wireguard settings screen")
            Intent(activity, WgMainActivity::class.java)
        } else {
            Logger.d(LOG_TAG_UI, "hmbs; invoke proxy settings screen")
            Intent(activity, ProxySettingsActivity::class.java)
        }
        activity.startActivity(intent)
        dialog.dismiss()
    }

    private fun handleLockdownModeIfNeeded() {
        val isLockdown = VpnController.isVpnLockdown()
        val isProxyEnabled = appConfig.isProxyEnabled()
        if (isLockdown) {
            binding.bsHomeScreenVpnLockdownDesc.text =
                htmlToSpannedText(activity.getString(R.string.hs_btm_sheet_lock_down))
            binding.bsHomeScreenVpnLockdownDesc.visibility = View.VISIBLE
            binding.bsHsDnsRl.alpha = 0.5f
            binding.bsHsFirewallRl.alpha = 0.5f
            setRadioButtonsEnabled(false)
        } else if (isProxyEnabled) {
            binding.bsHomeScreenVpnLockdownDesc.text =
                htmlToSpannedText(activity.getString(R.string.mode_change_error_proxy_enabled))
            binding.bsHomeScreenVpnLockdownDesc.visibility = View.VISIBLE
            binding.bsHsDnsRl.alpha = 0.5f
            binding.bsHsFirewallRl.alpha = 0.5f
            setRadioButtonsEnabled(false)
        } else {
            binding.bsHomeScreenVpnLockdownDesc.visibility = View.GONE
            binding.bsHsDnsRl.alpha = 1f
            binding.bsHsFirewallRl.alpha = 1f
            setRadioButtonsEnabled(true)
        }
    }

    private fun setRadioButtonsEnabled(isEnabled: Boolean) {
        binding.bsHsDnsRl.isEnabled = isEnabled
        binding.bsHsFirewallRl.isEnabled = isEnabled
        binding.bsHsDnsFirewallRl.isEnabled = isEnabled
        binding.bsHomeScreenRadioDns.isEnabled = isEnabled
        binding.bsHomeScreenRadioFirewall.isEnabled = isEnabled
        binding.bsHomeScreenRadioDnsFirewall.isEnabled = isEnabled
    }

    private fun handleDnsMode(isChecked: Boolean) {
        if (!isChecked) return

        binding.bsHomeScreenRadioFirewall.isChecked = false
        binding.bsHomeScreenRadioDnsFirewall.isChecked = false
        modifyBraveMode(AppConfig.BraveMode.DNS.mode)
    }

    private fun handleFirewallMode(isChecked: Boolean) {
        if (!isChecked) return

        binding.bsHomeScreenRadioDns.isChecked = false
        binding.bsHomeScreenRadioDnsFirewall.isChecked = false
        modifyBraveMode(AppConfig.BraveMode.FIREWALL.mode)
    }

    private fun handleDnsFirewallMode(isChecked: Boolean) {
        if (!isChecked) return

        binding.bsHomeScreenRadioDns.isChecked = false
        binding.bsHomeScreenRadioFirewall.isChecked = false
        modifyBraveMode(AppConfig.BraveMode.DNS_FIREWALL.mode)
    }

    private fun updateUptime() {
        val uptimeMs = VpnController.uptimeMs()
        val protocols = VpnController.protocols()
        val ssid = VpnController.underlyingSsid()
        val netType = VpnController.netType()
        val now = System.currentTimeMillis()
        val mtu = VpnController.mtu().toString()

        val isSsidPermissionGranted =
            SsidPermissionManager.hasRequiredPermissions(activity) &&
                SsidPermissionManager.isLocationEnabled(activity)
        val t =
            DateUtils.getRelativeTimeSpanString(
                now - uptimeMs,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )

        binding.bsHomeScreenAppUptime.text =
            if (uptimeMs < INIT_TIME_MS) {
                binding.bsHomeScreenAppUptime.visibility = View.GONE
                activity.getString(R.string.hsf_downtime, t)
            } else {
                binding.bsHomeScreenAppUptime.visibility = View.VISIBLE
                if (isSsidPermissionGranted && !ssid.isNullOrEmpty()) {
                    activity.getString(R.string.hsf_uptime, t, protocols, netType, mtu, ssid)
                } else {
                    activity.getString(R.string.hsf_uptime, t, protocols, netType, mtu, "")
                        .dropLast(9) + ")"
                }
            }
    }

    private fun modifyBraveMode(braveMode: Int) {
        io { appConfig.changeBraveMode(braveMode) }
    }

    private fun getConnectionStatus(): String {
        return when (appConfig.getBraveMode()) {
            AppConfig.BraveMode.DNS -> {
                activity.getString(R.string.dns_explanation_dns_connected)
            }
            AppConfig.BraveMode.FIREWALL -> {
                activity.getString(R.string.dns_explanation_firewall_connected)
            }
            else -> {
                activity.getString(R.string.dns_explanation_connected)
            }
        }
    }

    private fun io(f: suspend () -> Unit) {
        activity.lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    companion object {
        const val SCREEN_WG = "screen_wireguard"
        const val SCREEN_PROXY = "screen_proxy"
    }
}
