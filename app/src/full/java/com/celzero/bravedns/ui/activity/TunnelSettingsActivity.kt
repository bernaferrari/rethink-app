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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.dialog.NetworkReachabilityDialog
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.InternetProtocol
import com.celzero.bravedns.util.NewSettingsManager
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

class TunnelSettingsActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val appConfig by inject<AppConfig>()
    private val eventLogger by inject<EventLogger>()

    private var isLockdown by mutableStateOf(false)

    companion object {
        private const val SECONDS_PER_MINUTE = 60
        private const val SECONDS_PER_HOUR = 3600

        private const val POLICY_AUTO = 0
        private const val POLICY_SENSITIVE = 1
        private const val POLICY_RELAXED = 2
        private const val POLICY_FIXED = 3

        private const val IP_DIALOG_POS_IPV4 = 0
        private const val IP_DIALOG_POS_IPV6 = 1
        private const val IP_DIALOG_POS_ALWAYS_V46 = 2
        private const val IP_DIALOG_POS_V46 = 3

        private const val ALPHA_ENABLED = 1f
        private const val ALPHA_DISABLED = 0.5f
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

        isLockdown = VpnController.isVpnLockdown()

        setContent {
            RethinkTheme {
                TunnelSettingsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isLockdown = VpnController.isVpnLockdown()
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TunnelSettingsScreen() {
        var allowBypass by remember { mutableStateOf(persistentState.allowBypass) }
        var allowBypassLoading by remember { mutableStateOf(false) }
        var useMultipleNetworks by remember { mutableStateOf(persistentState.useMultipleNetworks) }
        var routeLan by remember { mutableStateOf(persistentState.privateIps) }
        var excludeApps by remember { mutableStateOf(!persistentState.excludeAppsInProxy) }
        var stallNoNetwork by remember { mutableStateOf(persistentState.stallOnNoNetwork) }
        var protocolTranslation by remember { mutableStateOf(persistentState.protocolTranslationType) }
        var meteredOnlyMobile by remember { mutableStateOf(persistentState.treatOnlyMobileNetworkAsMetered) }
        var listenPortFixed by remember { mutableStateOf(!persistentState.randomizeListenPort) }
        var wgLockdown by remember { mutableStateOf(persistentState.wgGlobalLockdown) }
        var endpointIndependence by remember { mutableStateOf(persistentState.endpointIndependence) }
        var allowIncoming by remember { mutableStateOf(persistentState.nwEngExperimentalFeatures) }
        var tcpKeepAlive by remember { mutableStateOf(persistentState.tcpKeepAlive) }
        var useMaxMtu by remember { mutableStateOf(persistentState.useMaxMtu) }
        var tunnelMetered by remember { mutableStateOf(persistentState.setVpnBuilderToMetered) }
        var dialTimeoutMin by remember { mutableStateOf(persistentState.dialTimeoutSec / SECONDS_PER_MINUTE) }
        var internetProtocol by remember { mutableStateOf(persistentState.internetProtocolType) }
        var vpnPolicy by remember { mutableStateOf(persistentState.vpnBuilderPolicy) }
        var connectivityChecks by remember { mutableStateOf(persistentState.connectivityChecks) }

        val canModify = !isLockdown
        val showPtrans = internetProtocol == InternetProtocol.IPv6.id
        val showConnectivityChecks = internetProtocol == InternetProtocol.IPv46.id
        val showPingIps = showConnectivityChecks && connectivityChecks

        val ipDesc =
            when (internetProtocol) {
                InternetProtocol.IPv4.id -> getString(R.string.settings_ip_text_ipv4)
                InternetProtocol.IPv6.id -> getString(R.string.settings_ip_text_ipv6)
                InternetProtocol.IPv46.id -> getString(R.string.settings_ip_text_ipv46)
                InternetProtocol.ALWAYSv46.id ->
                    getString(R.string.settings_ip_text_ipv4) + " & " + getString(R.string.settings_ip_text_ipv6)
                else -> getString(R.string.settings_ip_text_ipv4)
            }

        val vpnPolicyDesc =
            when (vpnPolicy) {
                POLICY_AUTO -> getString(R.string.settings_ip_text_ipv46)
                POLICY_SENSITIVE -> getString(R.string.vpn_policy_sensitive)
                POLICY_RELAXED -> getString(R.string.vpn_policy_relaxed)
                POLICY_FIXED -> getString(R.string.vpn_policy_fixed)
                else -> getString(R.string.settings_ip_text_ipv46)
            }

        val dialTimeoutDesc = formatTimeShort(dialTimeoutMin * SECONDS_PER_MINUTE)

        Scaffold(
            topBar = {
                TopAppBar(title = { Text(text = getString(R.string.lbl_network)) })
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLockdown) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { UIUtils.openVpnProfile(this@TunnelSettingsActivity) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NetworkCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = getString(R.string.settings_lock_down_mode_desc))
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingToggleRow(
                            icon = Icons.Filled.Settings,
                            title = getString(R.string.settings_allow_bypass_heading),
                            description = getString(R.string.settings_allow_bypass_desc),
                            checked = allowBypass,
                            enabled = canModify && !Utilities.isPlayStoreFlavour(),
                            alpha = if (canModify) ALPHA_ENABLED else ALPHA_DISABLED,
                            onCheckedChange = { checked ->
                                if (Utilities.isPlayStoreFlavour()) return@SettingToggleRow
                                allowBypass = checked
                                persistentState.allowBypass = checked
                                allowBypassLoading = true
                                Utilities.delay(TimeUnit.SECONDS.toMillis(1L), lifecycleScope) {
                                    allowBypassLoading = false
                                }
                                logEvent("allow bypass", "Allow bypass VPN: $checked")
                            }
                        )
                        if (allowBypassLoading) {
                            Text(text = getString(R.string.lbl_loading), style = MaterialTheme.typography.bodySmall)
                        }

                        SettingToggleRow(
                            icon = Icons.Filled.Tune,
                            title = getString(R.string.fail_open_network_title),
                            description = getString(R.string.fail_open_network_desc),
                            checked = stallNoNetwork,
                            enabled = true,
                            onCheckedChange = {
                                stallNoNetwork = it
                                persistentState.stallOnNoNetwork = it
                                logEvent("stall on no network", "Stall on no network: $it")
                            }
                        )

                        SettingToggleRow(
                            icon = Icons.Filled.Tune,
                            title = getString(R.string.settings_allow_lan_heading),
                            description = getString(R.string.settings_allow_lan_desc),
                            checked = routeLan,
                            enabled = canModify,
                            alpha = if (canModify) ALPHA_ENABLED else ALPHA_DISABLED,
                            onCheckedChange = { checked ->
                                routeLan = checked
                                persistentState.privateIps = checked
                                if (checked) persistentState.enableStabilityDependentSettings(this@TunnelSettingsActivity)
                                logEvent("route lan traffic", "Route LAN traffic: $checked")
                            }
                        )

                        SettingToggleRow(
                            icon = Icons.Filled.Tune,
                            title = getString(R.string.settings_network_all_networks),
                            description = getString(R.string.settings_network_all_networks_desc),
                            checked = useMultipleNetworks,
                            enabled = canModify,
                            alpha = if (canModify) ALPHA_ENABLED else ALPHA_DISABLED,
                            onCheckedChange = { checked ->
                                useMultipleNetworks = checked
                                persistentState.useMultipleNetworks = checked
                                if (checked) persistentState.enableStabilityDependentSettings(this@TunnelSettingsActivity)
                                if (!checked && persistentState.routeRethinkInRethink) {
                                    persistentState.routeRethinkInRethink = false
                                }
                                logEvent("use all networks", "Use all networks for VPN: $checked")
                            }
                        )

                        SettingToggleRow(
                            icon = Icons.Filled.Tune,
                            title = getString(R.string.settings_exclude_apps_in_proxy),
                            description = getString(R.string.settings_exclude_apps_in_proxy_desc),
                            checked = excludeApps,
                            enabled = canModify,
                            alpha = if (canModify) ALPHA_ENABLED else ALPHA_DISABLED,
                            onCheckedChange = { checked ->
                                excludeApps = checked
                                persistentState.excludeAppsInProxy = !checked
                                logEvent("exclude apps in proxy", "Exclude apps in proxy: ${!checked}")
                            }
                        )

                        SettingToggleRow(
                            icon = Icons.Filled.Tune,
                            title = getString(R.string.settings_protocol_translation),
                            description = getString(R.string.settings_protocol_translation_desc),
                            checked = protocolTranslation,
                            enabled = showPtrans,
                            alpha = if (showPtrans) ALPHA_ENABLED else ALPHA_DISABLED,
                            onCheckedChange = { checked ->
                                if (appConfig.getBraveMode().isDnsActive()) {
                                    protocolTranslation = checked
                                    persistentState.protocolTranslationType = checked
                                } else {
                                    protocolTranslation = false
                                    showToastUiCentered(
                                        this@TunnelSettingsActivity,
                                        getString(R.string.settings_protocol_translation_dns_inactive),
                                        Toast.LENGTH_SHORT
                                    )
                                }
                                logEvent("protocol translation", "Protocol translation set to: $checked")
                            }
                        )

                        SettingActionRow(
                            icon = Icons.Filled.Settings,
                            title = getString(R.string.settings_default_dns_heading),
                            description = getString(R.string.settings_default_dns_desc),
                            onClick = { showDefaultDnsDialog() }
                        )

                        SettingActionRow(
                            icon = Icons.Filled.Settings,
                            title = getString(R.string.vpn_policy_title),
                            description = vpnPolicyDesc,
                            onClick = { showTunNetworkPolicyDialog { policy ->
                                vpnPolicy = policy
                                if (policy == POLICY_FIXED) {
                                    persistentState.useMaxMtu = true
                                    useMaxMtu = true
                                    persistentState.internetProtocolType = InternetProtocol.ALWAYSv46.id
                                    internetProtocol = InternetProtocol.ALWAYSv46.id
                                }
                            } }
                        )

                        SettingActionRow(
                            icon = Icons.Filled.Settings,
                            title = getString(R.string.settings_ip_dialog_title),
                            description = getString(R.string.settings_selected_ip_desc, ipDesc),
                            enabled = vpnPolicy != POLICY_FIXED,
                            onClick = { showIpDialog { protocol ->
                                internetProtocol = protocol
                            } }
                        )

                        SettingActionRow(
                            icon = Icons.Filled.Settings,
                            title = getString(R.string.settings_connectivity_checks),
                            description = getString(R.string.settings_connectivity_checks_desc),
                            enabled = showConnectivityChecks,
                            onClick = {
                                showConnectivityChecksOptionsDialog { enabled, auto ->
                                    connectivityChecks = enabled
                                    persistentState.performAutoNetworkConnectivityChecks = auto
                                    persistentState.connectivityChecks = enabled
                                }
                            }
                        )

                        if (showPingIps) {
                            Button(onClick = { showNwReachabilityCheckDialog() }) {
                                Text(text = getString(R.string.settings_ping_ips))
                            }
                        }

                        SettingToggleRow(
                            icon = Icons.Filled.Tune,
                            title = getString(R.string.settings_treat_mobile_metered),
                            description = getString(R.string.settings_treat_mobile_metered_desc),
                            checked = meteredOnlyMobile,
                            enabled = true,
                            onCheckedChange = { checked ->
                                meteredOnlyMobile = checked
                                persistentState.treatOnlyMobileNetworkAsMetered = checked
                                logEvent("treat mobile network as metered", "Treat only mobile network as metered: $checked")
                            }
                        )

                        SettingToggleRow(
                            icon = Icons.Filled.Tune,
                            title = getString(R.string.settings_wg_listen_port),
                            description = getString(R.string.settings_wg_listen_port_desc),
                            checked = listenPortFixed,
                            enabled = true,
                            onCheckedChange = { checked ->
                                listenPortFixed = checked
                                persistentState.randomizeListenPort = !checked
                                logEvent("wireguard listen port", "WireGuard listen port randomize: ${!checked}")
                            }
                        )

                        SettingToggleRow(
                            icon = Icons.Filled.Tune,
                            title = getString(R.string.settings_wg_lockdown),
                            description = getString(R.string.settings_wg_lockdown_desc),
                            checked = wgLockdown,
                            enabled = true,
                            onCheckedChange = { checked ->
                                wgLockdown = checked
                                persistentState.wgGlobalLockdown = checked
                                NewSettingsManager.markSettingSeen(NewSettingsManager.WG_GLOBAL_LOCKDOWN_MODE_SETTING)
                                logEvent("wg global lockdown", "WireGuard global lockdown mode set to: $checked")
                            }
                        )

                        SettingToggleRow(
                            icon = Icons.Filled.Tune,
                            title = getString(R.string.settings_endpoint_independence),
                            description = getString(R.string.settings_endpoint_independence_desc),
                            checked = endpointIndependence,
                            enabled = true,
                            onCheckedChange = { checked ->
                                endpointIndependence = checked
                                persistentState.endpointIndependence = checked
                                if (!checked) {
                                    allowIncoming = false
                                    persistentState.nwEngExperimentalFeatures = false
                                } else {
                                    allowIncoming = persistentState.nwEngExperimentalFeatures
                                }
                                logEvent("endpoint independence", "Endpoint independence (EIM/EIF) set to: $checked")
                            }
                        )

                        if (endpointIndependence) {
                            SettingToggleRow(
                                icon = Icons.Filled.Tune,
                                title = getString(R.string.settings_allow_incoming_wg_packets),
                                description = getString(R.string.settings_allow_incoming_wg_packets_desc),
                                checked = allowIncoming,
                                enabled = true,
                                onCheckedChange = { checked ->
                                    allowIncoming = checked
                                    persistentState.nwEngExperimentalFeatures = checked
                                    logEvent("wg allow incoming packets", "WireGuard allow incoming packets set to: $checked")
                                }
                            )
                        }

                        SettingToggleRow(
                            icon = Icons.Filled.Tune,
                            title = getString(R.string.settings_tcp_keep_alive),
                            description = getString(R.string.settings_tcp_keep_alive_desc),
                            checked = tcpKeepAlive,
                            enabled = true,
                            onCheckedChange = { checked ->
                                tcpKeepAlive = checked
                                persistentState.tcpKeepAlive = checked
                                logEvent("tcp keep alive", "TCP keep alive set to: $checked")
                            }
                        )

                        SettingToggleRow(
                            icon = Icons.Filled.Tune,
                            title = getString(R.string.settings_jumbo_packets),
                            description = getString(R.string.settings_jumbo_packets_desc),
                            checked = useMaxMtu,
                            enabled = vpnPolicy != POLICY_FIXED && !persistentState.routeRethinkInRethink,
                            alpha = if (vpnPolicy != POLICY_FIXED && !persistentState.routeRethinkInRethink) ALPHA_ENABLED else ALPHA_DISABLED,
                            onCheckedChange = { checked ->
                                useMaxMtu = checked
                                persistentState.useMaxMtu = checked
                                logEvent("use jumbo packets", "Use jumbo packets set to: $checked")
                            }
                        )

                        if (isAtleastQ()) {
                            SettingToggleRow(
                                icon = Icons.Filled.Tune,
                                title = getString(R.string.settings_vpn_builder_metered),
                                description = getString(R.string.settings_vpn_builder_metered_desc),
                                checked = tunnelMetered,
                                enabled = true,
                                onCheckedChange = { checked ->
                                    tunnelMetered = checked
                                    persistentState.setVpnBuilderToMetered = checked
                                    logEvent("set vpn metered", "Set VPN builder to metered: $checked")
                                }
                            )
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(text = getString(R.string.settings_dial_timeout), style = MaterialTheme.typography.titleSmall)
                            Text(text = dialTimeoutDesc, style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = dialTimeoutMin.toFloat(),
                                onValueChange = { value ->
                                    dialTimeoutMin = value.toInt()
                                    persistentState.dialTimeoutSec = dialTimeoutMin * SECONDS_PER_MINUTE
                                },
                                valueRange = 0f..60f
                            )
                        }

                        SettingActionRow(
                            icon = Icons.Filled.Settings,
                            title = getString(R.string.custom_lan_ip_title),
                            description = getString(R.string.custom_lan_ip_desc),
                            onClick = { openCustomLanIpDialog() }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingToggleRow(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        description: String,
        checked: Boolean,
        enabled: Boolean,
        alpha: Float = 1f,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth().clickable(enabled = enabled) {
                    onCheckedChange(!checked)
                }.alpha(alpha),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                    Text(text = description, style = MaterialTheme.typography.bodySmall)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }

    @Composable
    private fun SettingActionRow(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        description: String,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onClick() }.alpha(if (enabled) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(text = description, style = MaterialTheme.typography.bodySmall)
            }
            Icon(imageVector = Icons.Filled.ArrowForward, contentDescription = null)
        }
    }

    private fun openCustomLanIpDialog() {
        try {
            var themeId = Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme)
            if (Themes.isFrostTheme(themeId)) {
                themeId = R.style.App_Dialog_NoDim
            }
            val dialog = com.celzero.bravedns.ui.dialog.CustomLanIpDialog(
                this,
                persistentState,
                themeId
            )
            dialog.setCanceledOnTouchOutside(true)
            dialog.show()
        } catch (e: Exception) {
            Napier.e("err opening CustomLanIpDialog: ${e.message}", e)
            showToastUiCentered(
                this,
                getString(R.string.custom_lan_ip_open_error),
                Toast.LENGTH_LONG
            )
        }
    }

    private fun showDefaultDnsDialog() {
        val alertBuilder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        alertBuilder.setTitle(getString(R.string.settings_default_dns_heading))
        val items = Constants.DEFAULT_DNS_LIST.map { it.name }.toTypedArray()
        val checkedItem =
            Constants.DEFAULT_DNS_LIST.firstOrNull { it.url == persistentState.defaultDnsUrl }
                ?.let { Constants.DEFAULT_DNS_LIST.indexOf(it) } ?: 0
        alertBuilder.setSingleChoiceItems(items, checkedItem) { dialog, pos ->
            dialog.dismiss()
            persistentState.defaultDnsUrl = Constants.DEFAULT_DNS_LIST[pos].url
            logEvent(
                "default dns changed",
                "Default DNS changed to: ${Constants.DEFAULT_DNS_LIST[pos].name}"
            )
        }
        alertBuilder.create().show()
    }

    data class NetworkPolicyOption(val title: String, val description: String)

    private fun showTunNetworkPolicyDialog(onSelected: (Int) -> Unit) {
        val conservativeTxt = getString(
            R.string.two_argument_space,
            getString(R.string.vpn_policy_fixed),
            getString(R.string.lbl_experimental)
        )
        val options = listOf(
            NetworkPolicyOption(getString(R.string.settings_ip_text_ipv46), getString(R.string.vpn_policy_auto_desc)),
            NetworkPolicyOption(getString(R.string.vpn_policy_sensitive), getString(R.string.vpn_policy_sensitive_desc)),
            NetworkPolicyOption(getString(R.string.vpn_policy_relaxed), getString(R.string.vpn_policy_relaxed_desc)),
            NetworkPolicyOption(conservativeTxt, getString(R.string.vpn_policy_fixed_desc))
        )
        var currentSelection = persistentState.vpnBuilderPolicy

        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
            .setTitle(getString(R.string.vpn_policy_title))
            .setSingleChoiceItems(
                options.map { it.title }.toTypedArray(),
                currentSelection
            ) { dialog, which ->
                dialog.dismiss()
                currentSelection = which
                if (currentSelection == POLICY_FIXED) {
                    persistentState.enableStabilityDependentSettings(this)
                }
                persistentState.vpnBuilderPolicy = which
                onSelected(which)
                logEvent("vpn builder network policy changed", "VPN builder network policy changed to index: $which")
            }

        builder.create().show()
    }

    private fun showIpDialog(onSelected: (Int) -> Unit) {
        val alertBuilder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        alertBuilder.setTitle(getString(R.string.settings_ip_dialog_title))
        val alwaysv46Txt =
            getString(R.string.settings_ip_text_ipv4) + " & " +
                getString(R.string.settings_ip_text_ipv6) + " " +
                getString(R.string.lbl_experimental)
        val items =
            arrayOf(
                getString(R.string.settings_ip_dialog_ipv4),
                getString(R.string.settings_ip_dialog_ipv6),
                alwaysv46Txt,
                getString(R.string.settings_ip_dialog_ipv46)
            )
        val chosenProtocol = persistentState.internetProtocolType
        val checkedItem =
            when (chosenProtocol) {
                InternetProtocol.ALWAYSv46.id -> IP_DIALOG_POS_ALWAYS_V46
                InternetProtocol.IPv46.id -> IP_DIALOG_POS_V46
                InternetProtocol.IPv4.id -> IP_DIALOG_POS_IPV4
                InternetProtocol.IPv6.id -> IP_DIALOG_POS_IPV6
                else -> IP_DIALOG_POS_IPV4
            }
        alertBuilder.setSingleChoiceItems(items, checkedItem) { dialog, which ->
            dialog.dismiss()
            val selectedItem =
                when (which) {
                    IP_DIALOG_POS_V46 -> InternetProtocol.IPv46.id
                    IP_DIALOG_POS_ALWAYS_V46 -> InternetProtocol.ALWAYSv46.id
                    else -> which
                }
            if (persistentState.internetProtocolType == selectedItem) return@setSingleChoiceItems

            val protocolType = InternetProtocol.getInternetProtocol(selectedItem)
            persistentState.internetProtocolType = protocolType.id
            if (protocolType.id == InternetProtocol.IPv6.id ||
                protocolType.id == InternetProtocol.IPv46.id ||
                protocolType.id == InternetProtocol.ALWAYSv46.id
            ) {
                persistentState.enableStabilityDependentSettings(this)
            }
            onSelected(protocolType.id)
            logEvent("internet protocol changed", "Internet protocol changed to: ${protocolType.name}")
        }
        alertBuilder.create().show()
    }

    private fun showConnectivityChecksOptionsDialog(onSelected: (Boolean, Boolean) -> Unit) {
        val alertBuilder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        alertBuilder.setTitle(getString(R.string.settings_connectivity_checks))
        val items = arrayOf(
            getString(R.string.settings_app_list_default_app),
            getString(R.string.settings_ip_text_ipv46),
            getString(R.string.lbl_manual)
        )
        val type = persistentState.performAutoNetworkConnectivityChecks
        val enabled = persistentState.connectivityChecks
        val checkedItem =
            if (!enabled) {
                0
            } else {
                when (type) {
                    true -> 1
                    false -> 2
                }
            }

        alertBuilder.setSingleChoiceItems(items, checkedItem) { dialog, which ->
            dialog.dismiss()
            when (which) {
                0 -> {
                    persistentState.performAutoNetworkConnectivityChecks = true
                    persistentState.connectivityChecks = false
                    onSelected(false, true)
                }
                1 -> {
                    persistentState.performAutoNetworkConnectivityChecks = true
                    persistentState.connectivityChecks = true
                    onSelected(true, true)
                }
                2 -> {
                    persistentState.performAutoNetworkConnectivityChecks = false
                    persistentState.connectivityChecks = true
                    onSelected(true, false)
                }
            }
            logEvent("connectivity checks changed", "Connectivity checks changed to option index: $which")
        }
        alertBuilder.create().show()
    }

    private fun showNwReachabilityCheckDialog() {
        if (!VpnController.hasTunnel()) {
            showToastUiCentered(
                this,
                getString(R.string.settings_socks5_vpn_disabled_error),
                Toast.LENGTH_SHORT
            )
            return
        }
        var themeId = Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme)
        if (Themes.isFrostTheme(themeId)) {
            themeId = R.style.App_Dialog_NoDim
        }
        val nwReachabilityDialog = NetworkReachabilityDialog(this, persistentState, themeId)
        nwReachabilityDialog.setCanceledOnTouchOutside(true)
        nwReachabilityDialog.show()
    }

    private fun logEvent(msg: String, details: String) {
        eventLogger.log(EventType.TUN_ESTABLISHED, Severity.LOW, msg, EventSource.UI, false, details)
    }

    private fun formatTimeShort(totalSeconds: Int): String {
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE

        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (seconds > 0) parts.add("${seconds}s")

        return if (parts.isEmpty()) getString(R.string.lbl_disabled) else parts.joinToString(" ")
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}
