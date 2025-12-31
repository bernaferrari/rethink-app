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
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.viewinterop.AndroidView
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.activity.ProxySettingsActivity
import com.celzero.bravedns.ui.activity.WgMainActivity
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants.Companion.INIT_TIME_MS
import com.celzero.bravedns.util.SsidPermissionManager
import com.celzero.bravedns.util.Themes.Companion.getBottomsheetCurrentTheme
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.openVpnProfile
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HomeScreenSettingDialog(private val activity: FragmentActivity) : KoinComponent {
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val appConfig by inject<AppConfig>()
    private val persistentState by inject<PersistentState>()

    init {
        val composeView = ComposeView(activity)
        composeView.setContent {
            RethinkTheme {
                HomeScreenSettingContent()
            }
        }
        dialog.setContentView(composeView)
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
    }

    fun show() {
        dialog.show()
    }

    private fun getThemeId(): Int {
        val isDark =
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return getBottomsheetCurrentTheme(isDark, persistentState.theme)
    }

    @Composable
    private fun HomeScreenSettingContent() {
        val isLockdown = remember { VpnController.isVpnLockdown() }
        val isProxyEnabled = remember { appConfig.isProxyEnabled() }
        val isDisabled = isLockdown || isProxyEnabled
        var selectedMode by remember { mutableStateOf(appConfig.getBraveMode().mode) }
        val uptimeInfo = remember { buildUptimeText() }
        val borderColor = Color(UIUtils.fetchColor(activity, R.attr.border))

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(60.dp)
                        .height(3.dp)
                        .background(borderColor, RoundedCornerShape(2.dp))
            )

            Text(
                text = activity.getString(R.string.app_mode_choose),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            if (isLockdown || isProxyEnabled) {
                LockdownMessage(isLockdown)
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeRow(
                    iconRes = R.drawable.dns_home_screen,
                    title = activity.getString(R.string.app_mode_dns_low_battery),
                    subtitle = activity.getString(R.string.dns_mode_info_explanation_new),
                    selected = selectedMode == AppConfig.BraveMode.DNS.mode,
                    enabled = !isDisabled
                ) {
                    selectedMode = AppConfig.BraveMode.DNS.mode
                    modifyBraveMode(AppConfig.BraveMode.DNS.mode)
                }

                ModeRow(
                    iconRes = R.drawable.firewall_home_screen,
                    title = activity.getString(R.string.app_mode_firewall),
                    subtitle = activity.getString(R.string.firewall_mode_info_explanation_new),
                    selected = selectedMode == AppConfig.BraveMode.FIREWALL.mode,
                    enabled = !isDisabled
                ) {
                    selectedMode = AppConfig.BraveMode.FIREWALL.mode
                    modifyBraveMode(AppConfig.BraveMode.FIREWALL.mode)
                }

                ModeRow(
                    iconRes = R.drawable.ic_dns_firewall,
                    title = activity.getString(R.string.app_mode_dns_firewall),
                    subtitle = activity.getString(R.string.dns_firewall_mode_info_explanation_new),
                    selected = selectedMode == AppConfig.BraveMode.DNS_FIREWALL.mode,
                    enabled = !isDisabled
                ) {
                    selectedMode = AppConfig.BraveMode.DNS_FIREWALL.mode
                    modifyBraveMode(AppConfig.BraveMode.DNS_FIREWALL.mode)
                }
            }

            if (uptimeInfo.show) {
                Text(
                    text = uptimeInfo.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().alpha(0.75f).padding(horizontal = 12.dp),
                )
            }
        }
    }

    @Composable
    private fun LockdownMessage(isLockdown: Boolean) {
        val textColor = UIUtils.fetchColor(activity, R.attr.primaryTextColor)
        val warning =
            if (isLockdown) {
                activity.getString(R.string.hs_btm_sheet_lock_down)
            } else {
                activity.getString(R.string.mode_change_error_proxy_enabled)
            }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.dis_allowed),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        setTextColor(textColor)
                    }
                },
                update = { tv ->
                    tv.text = HtmlCompat.fromHtml(warning, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    tv.setOnClickListener {
                        if (isLockdown) {
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
            )
        }
    }

    @Composable
    private fun ModeRow(
        iconRes: Int,
        title: String,
        subtitle: String,
        selected: Boolean,
        enabled: Boolean,
        onSelect: () -> Unit
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .alpha(if (enabled) 1f else 0.5f)
                    .clickable(enabled = enabled) { onSelect() }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = activity.getString(R.string.apps_icon_content_desc),
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = selected, onClick = null, enabled = enabled)
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

    private fun buildUptimeText(): UptimeInfo {
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
        if (uptimeMs < INIT_TIME_MS) {
            return UptimeInfo(activity.getString(R.string.hsf_downtime, t), false)
        }

        val text =
            if (isSsidPermissionGranted && !ssid.isNullOrEmpty()) {
                activity.getString(R.string.hsf_uptime, t, protocols, netType, mtu, ssid)
            } else {
                activity.getString(R.string.hsf_uptime, t, protocols, netType, mtu, "")
                    .dropLast(9) + ")"
            }
        return UptimeInfo(text, true)
    }

    private fun modifyBraveMode(braveMode: Int) {
        Logger.d(LOG_TAG_VPN, "Home screen bottom sheet selectedIndex: $braveMode")
        io { appConfig.changeBraveMode(braveMode) }
    }

    private fun io(f: suspend () -> Unit) {
        activity.lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private data class UptimeInfo(val text: String, val show: Boolean)

    companion object {
        const val SCREEN_WG = "screen_wireguard"
        const val SCREEN_PROXY = "screen_proxy"
    }
}
