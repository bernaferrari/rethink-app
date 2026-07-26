/*
 * Copyright 2025 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.ui.compose.wireguard

import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.components.OneWgConfigRow
import com.bernaferrari.bravedns.ui.components.WgConfigRow
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.WgConfigFiles
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.WireguardManager
import com.bernaferrari.bravedns.ui.compose.dns.RethinkEndpointFeed
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardScreen
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardStrings
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWgTab
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.viewmodel.WgConfigViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WgMainScreen(
    wgConfigViewModel: WgConfigViewModel,
    persistentState: PersistentState,
    appConfig: AppConfig,
    eventLogger: EventLogger,
    onBackClick: () -> Unit,
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit,
    onQrScanClick: () -> Unit,
    onConfigDetailClick: (Int, WgType) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val navBarBottomInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val scope = rememberCoroutineScope()
    val wireguardDisclaimerText = stringResource(R.string.wireguard_disclaimer)
    val fallbackDnsLabel = stringResource(R.string.lbl_fallback)
    val wireguardDisableFailure = stringResource(R.string.wireguard_disable_failure)
    val wireguardDisableFailureRelay = stringResource(R.string.wireguard_disable_failure_relay)

    var selectedTab by remember {
        mutableStateOf(
            if (WireguardManager.isAnyWgActive() && !WireguardManager.oneWireGuardEnabled()) {
                RethinkWgTab.General
            } else {
                RethinkWgTab.One
            }
        )
    }
    var showDisableDialog by remember { mutableStateOf(false) }
    var disableDialogIsOneWgToggle by remember { mutableStateOf(false) }
    var disclaimerText by remember { mutableStateOf("") }

    val configCount by wgConfigViewModel.configCount()
        .collectAsStateWithLifecycle(initialValue = 0)
    val showEmpty = configCount == 0

    // Observe connected DNS for non-OneWG mode
    val connectedDns by appConfig.connectedDnsFlow()
        .collectAsStateWithLifecycle(initialValue = "")

    // DNS status listener callback - updates disclaimer text
    fun updateDisclaimerText() {
        val activeConfigs = WireguardManager.getActiveConfigs()
        disclaimerText = if (WireguardManager.oneWireGuardEnabled()) {
            val dnsName = activeConfigs.firstOrNull()?.getName() ?: ""
            String.format(wireguardDisclaimerText, dnsName)
        } else {
            var dnsNames = connectedDns
            if (persistentState.splitDns && activeConfigs.isNotEmpty()) {
                if (dnsNames.isNotEmpty()) {
                    dnsNames += ", "
                }
                dnsNames += activeConfigs.joinToString(",") { it.getName() }
            }
            if (persistentState.useFallbackDnsToBypass) {
                dnsNames += ", $fallbackDnsLabel"
            }
            String.format(wireguardDisclaimerText, dnsNames)
        }
    }

    // A counter to trigger disclaimer text refresh
    var dnsRefreshTrigger by remember { mutableStateOf(0) }

    // Initialize and update disclaimer text when tab, DNS, or refresh trigger changes
    LaunchedEffect(selectedTab, connectedDns, dnsRefreshTrigger) {
        updateDisclaimerText()
    }



    val configs = wgConfigViewModel.interfaces.collectAsLazyPagingItems()

    RethinkWireguardScreen(
        selectedTab = selectedTab,
        overview = disclaimerText,
        isEmpty = showEmpty,
        configs = AndroidWgConfigFeed(configs),
        strings = RethinkWireguardStrings(
            title = stringResource(R.string.lbl_wireguard),
            oneTab = stringResource(R.string.rt_list_simple_btn_txt),
            generalTab = stringResource(R.string.lbl_advanced),
            create = stringResource(R.string.lbl_create),
            import = stringResource(R.string.lbl_import),
            qrCode = stringResource(R.string.lbl_qr_code),
            noConfigurations = stringResource(R.string.wireguard_no_config_msg),
            disableTitle = stringResource(R.string.wireguard_disable_title),
            disableMessage = stringResource(R.string.wireguard_disable_message),
            disableConfirm = stringResource(R.string.always_on_dialog_positive),
            cancel = stringResource(R.string.lbl_cancel),
        ),
        bottomInset = navBarBottomInset,
        confirmDisable = showDisableDialog,
        onBackClick = onBackClick,
        onTabClick = { tab ->
            if (tab == RethinkWgTab.One) {
                val activeConfigs = WireguardManager.getActiveConfigs()
                if (activeConfigs.isNotEmpty() && !WireguardManager.oneWireGuardEnabled()) {
                    disableDialogIsOneWgToggle = true
                    showDisableDialog = true
                } else {
                    selectedTab = RethinkWgTab.One
                }
            } else if (WireguardManager.oneWireGuardEnabled()) {
                disableDialogIsOneWgToggle = false
                showDisableDialog = true
            } else {
                selectedTab = RethinkWgTab.General
            }
        },
        onDismissDisable = { showDisableDialog = false },
        onConfirmDisable = {
            showDisableDialog = false
            val switchToOne = disableDialogIsOneWgToggle
            scope.launch(Dispatchers.IO) {
                if (WireguardManager.canDisableAllActiveConfigs()) {
                    WireguardManager.disableAllActiveConfigs()
                    logEvent(eventLogger, "Wireguard disable", "all configs from toggle switch; isOneWgToggle: $switchToOne")
                    withContext(Dispatchers.Main) {
                        dnsRefreshTrigger++
                        selectedTab = if (switchToOne) RethinkWgTab.One else RethinkWgTab.General
                    }
                } else {
                    val activeConfigs = WireguardManager.getActiveCatchAllConfig()
                    withContext(Dispatchers.Main) {
                        Utilities.showToastUiCentered(context, if (activeConfigs.isNotEmpty()) wireguardDisableFailure else wireguardDisableFailureRelay, Toast.LENGTH_LONG)
                    }
                }
            }
        },
        onCreate = onCreateClick,
        onImport = onImportClick,
        onQrCode = onQrScanClick,
        configRow = { config, tab ->
            if (tab == RethinkWgTab.One) {
                OneWgConfigRow(config, eventLogger, onDnsStatusChanged = { dnsRefreshTrigger++ }, onConfigDetailClick = onConfigDetailClick)
            } else {
                WgConfigRow(config, eventLogger, onDnsStatusChanged = { dnsRefreshTrigger++ }, onConfigDetailClick = onConfigDetailClick)
            }
        },
    )
}

private class AndroidWgConfigFeed(
    private val items: androidx.paging.compose.LazyPagingItems<WgConfigFiles>,
) : RethinkEndpointFeed<WgConfigFiles> {
    override val itemCount: Int get() = items.itemCount
    override fun get(index: Int): WgConfigFiles? = items[index]
}

private fun logEvent(eventLogger: EventLogger, msg: String, details: String) {
    eventLogger.log(EventType.PROXY_SWITCH, Severity.LOW, msg, EventSource.UI, false, details)
}
