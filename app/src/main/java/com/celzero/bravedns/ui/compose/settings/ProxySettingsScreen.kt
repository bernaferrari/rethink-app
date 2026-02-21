/*
 * Copyright 2024 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.asFlow
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.ProxyEndpoint
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.net.doh.Transaction
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.ui.dialog.WgIncludeAppsDialog
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.ui.compose.theme.Dimensions
import androidx.compose.ui.res.painterResource
import com.celzero.bravedns.util.OrbotHelper
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.ProxyAppsMappingViewModel
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.RouterStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val REFRESH_TIMEOUT_MS = 4000L

private data class ProxyScreenState(
    val canEnableProxy: Boolean,
    val socks5Enabled: Boolean,
    val httpEnabled: Boolean,
    val orbotEnabled: Boolean,
    val wireguardDescription: String,
    val socks5Description: String,
    val httpDescription: String,
    val orbotDescription: String
)

private data class Socks5DialogState(
    val host: String,
    val port: String,
    val username: String,
    val password: String,
    val selectedApp: String,
    val appNames: List<String>,
    val udpBlocked: Boolean,
    val includeProxyApps: Boolean,
    val lockdown: Boolean,
    val error: String? = null
)

private data class HttpDialogState(
    val host: String,
    val selectedApp: String,
    val appNames: List<String>,
    val includeProxyApps: Boolean,
    val lockdown: Boolean,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(
    appConfig: AppConfig,
    persistentState: PersistentState,
    eventLogger: EventLogger,
    mappingViewModel: ProxyAppsMappingViewModel? = null,
    onWireguardClick: (() -> Unit)? = null,
    onNavigateToDns: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val providerName =
        persistentState.proxyProvider.lowercase().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    val lockDownProxyDesc = stringResource(R.string.settings_lock_down_proxy_desc)
    val refreshToast = stringResource(R.string.dc_refresh_toast)
    val socks5VpnDisabledError = stringResource(R.string.settings_socks5_vpn_disabled_error)
    val socks5DisabledError = stringResource(R.string.settings_socks5_disabled_error, providerName)
    val httpDisabledError = stringResource(R.string.settings_https_disabled_error, providerName)
    val orbotDisabledError = stringResource(R.string.settings_orbot_disabled_error)
    val orbotInstallError = stringResource(R.string.orbot_install_activity_error)
    val orbotWebsiteLink = stringResource(R.string.orbot_website_link)
    val orbotNoAppToast = stringResource(R.string.orbot_no_app_toast)
    val httpProxyHostEmptyError = stringResource(R.string.settings_http_proxy_error_text3)
    val httpProxyInvalidPortError = stringResource(R.string.settings_http_proxy_error_text2)
    val httpProxyRangePortError = stringResource(R.string.settings_http_proxy_error_text1)
    val httpProxyToastSuccess = stringResource(R.string.settings_http_proxy_toast_success)
    val defaultAppLabel = stringResource(R.string.settings_app_list_default_app)
    val orbotStopTitle = stringResource(R.string.orbot_stop_dialog_title)
    val orbotStopMessage = stringResource(R.string.orbot_stop_dialog_message)
    val orbotStopDnsMessage = stringResource(R.string.orbot_stop_dialog_dns_message)
    val orbotStopMessageCombo =
        stringResource(
            R.string.orbot_stop_dialog_message_combo,
            orbotStopMessage,
            orbotStopDnsMessage
        )
    val defaultWireguardDescription = stringResource(R.string.wireguard_description)
    val defaultSocks5Description = stringResource(R.string.settings_socks_forwarding_default_desc)
    val defaultHttpDescription = stringResource(R.string.settings_https_desc)
    val defaultOrbotDescription = stringResource(R.string.orbot_bs_status_4)

    val orbotHelper = remember(context, persistentState, appConfig) {
        OrbotHelper(context, persistentState, appConfig)
    }

    var refreshTick by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    var canEnableProxy by remember { mutableStateOf(appConfig.canEnableProxy()) }
    var socks5Enabled by remember { mutableStateOf(appConfig.isCustomSocks5Enabled()) }
    var httpEnabled by remember { mutableStateOf(appConfig.isCustomHttpProxyEnabled()) }
    var orbotEnabled by remember { mutableStateOf(appConfig.isOrbotProxyEnabled()) }
    val orbotConnecting =
        remember { persistentState.orbotConnectionStatus.asFlow() }.collectAsState(initial = false).value

    var wireguardDescription by remember(defaultWireguardDescription) { mutableStateOf(defaultWireguardDescription) }
    var socks5Description by remember(defaultSocks5Description) { mutableStateOf(defaultSocks5Description) }
    var httpDescription by remember(defaultHttpDescription) { mutableStateOf(defaultHttpDescription) }
    var orbotDescription by remember(defaultOrbotDescription) { mutableStateOf(defaultOrbotDescription) }

    var showOrbotInstallDialog by remember { mutableStateOf(false) }
    var showOrbotModeDialog by remember { mutableStateOf(false) }
    var showOrbotStopDialog by remember { mutableStateOf(false) }
    var showOrbotInfoDialog by remember { mutableStateOf(false) }
    var orbotStopHasDnsHint by remember { mutableStateOf(false) }
    var selectedOrbotMode by remember { mutableStateOf(AppConfig.ProxyType.SOCKS5.name) }

    var socks5DialogState by remember { mutableStateOf<Socks5DialogState?>(null) }
    var httpDialogState by remember { mutableStateOf<HttpDialogState?>(null) }
    var showOrbotAppsDialog by remember { mutableStateOf(false) }
    val orbotAppCount =
        if (mappingViewModel != null) {
            mappingViewModel.getAppCountById(ProxyManager.ID_ORBOT_BASE)
                .asFlow()
                .collectAsState(initial = 0)
                .value
        } else {
            0
        }

    fun logEvent(details: String) {
        eventLogger.log(
            type = EventType.UI_SETTING_CHANGED,
            severity = Severity.LOW,
            message = "Proxy settings",
            source = EventSource.UI,
            userAction = true,
            details = details
        )
    }

    fun reloadUi() {
        refreshTick++
    }

    fun showProxyDisabledToast() {
        Utilities.showToastUiCentered(
            context,
            lockDownProxyDesc,
            Toast.LENGTH_SHORT
        )
    }

    fun refreshWireguard() {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            withContext(Dispatchers.IO) {
                VpnController.refreshOrPauseOrResumeOrReAddProxies()
            }
            delay(REFRESH_TIMEOUT_MS)
            Utilities.showToastUiCentered(
                context,
                refreshToast,
                Toast.LENGTH_SHORT
            )
            isRefreshing = false
            reloadUi()
        }
    }

    fun openSocksDialog() {
        scope.launch {
            socks5DialogState =
                withContext(Dispatchers.IO) {
                    buildSocks5DialogState(context, appConfig, persistentState)
                }
        }
    }

    fun openHttpDialog() {
        scope.launch {
            httpDialogState =
                withContext(Dispatchers.IO) {
                    buildHttpDialogState(context, appConfig, persistentState)
                }
        }
    }

    fun enableOrbotFlow() {
        scope.launch {
            if (!canEnableProxy) {
                showProxyDisabledToast()
                reloadUi()
                return@launch
            }

            val isInstalled = withContext(Dispatchers.IO) { FirewallManager.isOrbotInstalled() }
            if (!isInstalled) {
                showOrbotInstallDialog = true
                return@launch
            }

            if (!appConfig.canEnableOrbotProxy()) {
                val msg =
                    if (providerName.equals(AppConfig.ProxyProvider.CUSTOM.name, ignoreCase = true)) {
                        orbotDisabledError
                    } else {
                        socks5DisabledError
                    }
                Utilities.showToastUiCentered(context, msg, Toast.LENGTH_SHORT)
                reloadUi()
                return@launch
            }

            if (!VpnController.hasTunnel()) {
                Utilities.showToastUiCentered(
                    context,
                    socks5VpnDisabledError,
                    Toast.LENGTH_SHORT
                )
                reloadUi()
                return@launch
            }

            selectedOrbotMode =
                if (orbotEnabled) appConfig.getProxyType() else AppConfig.ProxyType.SOCKS5.name
            showOrbotModeDialog = true
        }
    }

    fun stopOrbotForwarding(showDialog: Boolean) {
        scope.launch {
            val hasDnsHint =
                withContext(Dispatchers.IO) {
                    val isOrbotDns = appConfig.isOrbotDns()
                    appConfig.removeAllProxies()
                    orbotHelper.stopOrbot(isInteractive = true)
                    isOrbotDns
                }
            if (showDialog) {
                orbotStopHasDnsHint = hasDnsHint
                showOrbotStopDialog = true
            }
            logEvent("Orbot proxy disabled")
            reloadUi()
        }
    }

    LaunchedEffect(refreshTick) {
        val state =
            withContext(Dispatchers.IO) {
                buildProxyScreenState(context, appConfig)
            }

        canEnableProxy = state.canEnableProxy
        socks5Enabled = state.socks5Enabled
        httpEnabled = state.httpEnabled
        orbotEnabled = state.orbotEnabled
        wireguardDescription = state.wireguardDescription
        socks5Description = state.socks5Description
        httpDescription = state.httpDescription
        orbotDescription = state.orbotDescription
    }

    Scaffold(
        topBar = {
            RethinkTopBar(
                title = stringResource(R.string.settings_proxy_header),
                onBackClick = onBackClick,
                actions = {
                    if (canEnableProxy) {
                        IconButton(
                            onClick = { refreshWireguard() },
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(8.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.dc_refresh_toast)
                                )
                            }
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding =
                PaddingValues(
                    start = Dimensions.screenPaddingHorizontal,
                    end = Dimensions.screenPaddingHorizontal,
                    bottom = Dimensions.spacing3xl
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            item {
                ProxyOverviewCard(
                    canEnableProxy = canEnableProxy,
                    socks5Enabled = socks5Enabled,
                    httpEnabled = httpEnabled,
                    orbotEnabled = orbotEnabled
                )
            }

            if (!canEnableProxy) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = stringResource(R.string.settings_lock_down_proxy_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Network Proxy (WireGuard)
            if (onWireguardClick != null) {
                item {
                    SectionHeader(title = stringResource(R.string.setup_wireguard))
                    RethinkListGroup {
                        RethinkListItem(
                            headline = stringResource(R.string.setup_wireguard),
                            supporting = wireguardDescription,
                            leadingIconPainter = painterResource(id = R.drawable.ic_wireguard_icon),
                            onClick = {
                                if (!canEnableProxy) {
                                    showProxyDisabledToast()
                                } else {
                                    onWireguardClick()
                                }
                            }
                        )
                    }
                }
            }

            // SOCKS5
            item {
                SectionHeader(title = stringResource(R.string.settings_socks5_heading))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(R.string.settings_socks5_heading),
                        supporting = socks5Description,
                        leadingIconPainter = painterResource(id = R.drawable.ic_socks5),
                        onClick = {
                            if (canEnableProxy && socks5Enabled) {
                                openSocksDialog()
                            } else if (!socks5Enabled) {
                                if (!canEnableProxy) {
                                    showProxyDisabledToast()
                                    reloadUi()
                                } else if (appConfig.getBraveMode().isDnsMode()) {
                                    Utilities.showToastUiCentered(context, socks5VpnDisabledError, Toast.LENGTH_SHORT)
                                    reloadUi()
                                } else if (!appConfig.canEnableSocks5Proxy()) {
                                    Utilities.showToastUiCentered(context, socks5DisabledError, Toast.LENGTH_SHORT)
                                    reloadUi()
                                } else {
                                    openSocksDialog()
                                }
                            }
                        },
                        trailing = {
                            Switch(
                                checked = socks5Enabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        if (!canEnableProxy) {
                                            showProxyDisabledToast()
                                            reloadUi()
                                        } else if (appConfig.getBraveMode().isDnsMode()) {
                                            Utilities.showToastUiCentered(context, socks5VpnDisabledError, Toast.LENGTH_SHORT)
                                            reloadUi()
                                        } else if (!appConfig.canEnableSocks5Proxy()) {
                                            Utilities.showToastUiCentered(context, socks5DisabledError, Toast.LENGTH_SHORT)
                                            reloadUi()
                                        } else {
                                            openSocksDialog()
                                        }
                                    } else {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                appConfig.removeProxy(AppConfig.ProxyType.SOCKS5, AppConfig.ProxyProvider.CUSTOM)
                                            }
                                            logEvent("Custom SOCKS5 disabled")
                                            reloadUi()
                                        }
                                    }
                                },
                                enabled = canEnableProxy
                            )
                        }
                    )
                }
            }

            // HTTP
            item {
                SectionHeader(title = stringResource(R.string.settings_https_heading))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(R.string.settings_https_heading),
                        supporting = httpDescription,
                        leadingIconPainter = painterResource(id = R.drawable.ic_http),
                        onClick = {
                            if (canEnableProxy && httpEnabled) {
                                openHttpDialog()
                            } else if (!httpEnabled) {
                                if (!canEnableProxy) {
                                    showProxyDisabledToast()
                                    reloadUi()
                                } else if (appConfig.getBraveMode().isDnsMode()) {
                                    Utilities.showToastUiCentered(context, socks5VpnDisabledError, Toast.LENGTH_SHORT)
                                    reloadUi()
                                } else if (!appConfig.canEnableHttpProxy()) {
                                    Utilities.showToastUiCentered(context, httpDisabledError, Toast.LENGTH_SHORT)
                                    reloadUi()
                                } else {
                                    openHttpDialog()
                                }
                            }
                        },
                        trailing = {
                            Switch(
                                checked = httpEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        if (!canEnableProxy) {
                                            showProxyDisabledToast()
                                            reloadUi()
                                        } else if (appConfig.getBraveMode().isDnsMode()) {
                                            Utilities.showToastUiCentered(context, socks5VpnDisabledError, Toast.LENGTH_SHORT)
                                            reloadUi()
                                        } else if (!appConfig.canEnableHttpProxy()) {
                                            Utilities.showToastUiCentered(context, httpDisabledError, Toast.LENGTH_SHORT)
                                            reloadUi()
                                        } else {
                                            openHttpDialog()
                                        }
                                    } else {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                appConfig.removeProxy(AppConfig.ProxyType.HTTP, AppConfig.ProxyProvider.CUSTOM)
                                            }
                                            logEvent("Custom HTTP disabled")
                                            reloadUi()
                                        }
                                    }
                                },
                                enabled = canEnableProxy
                            )
                        }
                    )
                }
            }

            // Orbot
            item {
                SectionHeader(title = stringResource(R.string.orbot))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(R.string.orbot),
                        supporting = if (orbotConnecting) stringResource(R.string.orbot_bs_status_trying_connect) else orbotDescription,
                        leadingIconPainter = painterResource(id = R.drawable.ic_orbot),
                        onClick = {
                            if (canEnableProxy && !orbotConnecting) {
                                if (orbotEnabled) enableOrbotFlow() else enableOrbotFlow()
                            }
                        },
                        trailing = {
                            Switch(
                                checked = orbotEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        enableOrbotFlow()
                                    } else {
                                        stopOrbotForwarding(showDialog = true)
                                    }
                                },
                                enabled = canEnableProxy && !orbotConnecting
                            )
                        }
                    )

                    if (canEnableProxy && !orbotConnecting) {
                        if (mappingViewModel != null) {
                            RethinkListItem(
                                headline = stringResource(R.string.add_remove_apps, orbotAppCount.toString()),
                                leadingIconPainter = painterResource(id = R.drawable.ic_app_info_accent),
                                onClick = { showOrbotAppsDialog = true }
                            )
                        }
                        RethinkListItem(
                            headline = stringResource(R.string.settings_orbot_notification_action),
                            leadingIconPainter = painterResource(id = R.drawable.ic_right_arrow_small),
                            onClick = { orbotHelper.openOrbotApp() }
                        )
                        RethinkListItem(
                            headline = stringResource(R.string.lbl_info),
                            leadingIconPainter = painterResource(id = R.drawable.ic_info),
                            onClick = { showOrbotInfoDialog = true },
                            showDivider = false
                        )
                    }
                }
            }
        }
    }

    if (showOrbotAppsDialog && mappingViewModel != null) {
        WgIncludeAppsDialog(
            viewModel = mappingViewModel,
            proxyId = ProxyManager.ID_ORBOT_BASE,
            proxyName = ProxyManager.ORBOT_PROXY_NAME,
            onDismiss = { showOrbotAppsDialog = false }
        )
    }

    if (showOrbotInfoDialog) {
        AlertDialog(
            onDismissRequest = { showOrbotInfoDialog = false },
            title = { Text(text = stringResource(R.string.orbot_title)) },
            text = { Text(text = stringResource(R.string.orbot_explanation)) },
            confirmButton = {
                TextButton(onClick = { showOrbotInfoDialog = false }) {
                    Text(text = stringResource(R.string.lbl_dismiss))
                }
            }
        )
    }

    if (showOrbotInstallDialog) {
        AlertDialog(
            onDismissRequest = { showOrbotInstallDialog = false },
            title = { Text(text = stringResource(R.string.orbot_install_dialog_title)) },
            text = { Text(text = stringResource(R.string.orbot_install_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOrbotInstallDialog = false
                        val installIntent = orbotHelper.getIntentForDownload()
                        if (installIntent == null) {
                            Utilities.showToastUiCentered(
                                context,
                                orbotInstallError,
                                Toast.LENGTH_SHORT
                            )
                            return@TextButton
                        }

                        try {
                            context.startActivity(installIntent)
                        } catch (_: ActivityNotFoundException) {
                            Utilities.showToastUiCentered(
                                context,
                                orbotInstallError,
                                Toast.LENGTH_SHORT
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.orbot_install_dialog_positive))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showOrbotInstallDialog = false
                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        orbotWebsiteLink.toUri()
                                    )
                                )
                            } catch (_: ActivityNotFoundException) {
                                Utilities.showToastUiCentered(
                                    context,
                                    orbotInstallError,
                                    Toast.LENGTH_SHORT
                                )
                            }
                        }
                    ) {
                        Text(text = stringResource(R.string.orbot_install_dialog_neutral))
                    }
                    TextButton(onClick = { showOrbotInstallDialog = false }) {
                        Text(text = stringResource(R.string.lbl_dismiss))
                    }
                }
            }
        )
    }

    if (showOrbotModeDialog) {
        OrbotModeDialog(
            supportsHttp = Utilities.isAtleastQ(),
            selectedMode = selectedOrbotMode,
            onSelectedMode = { selectedOrbotMode = it },
            onDismiss = { showOrbotModeDialog = false },
            onConfirm = {
                scope.launch {
                    showOrbotModeDialog = false

                    if (selectedOrbotMode == AppConfig.ProxyType.NONE.name) {
                        stopOrbotForwarding(showDialog = true)
                        return@launch
                    }

                    if (!ProxyManager.isAnyAppSelected(ProxyManager.ID_ORBOT_BASE)) {
                        Utilities.showToastUiCentered(
                            context,
                            orbotNoAppToast,
                            Toast.LENGTH_SHORT
                        )
                        reloadUi()
                        return@launch
                    }

                    if (!VpnController.hasTunnel()) {
                        Utilities.showToastUiCentered(
                            context,
                            socks5VpnDisabledError,
                            Toast.LENGTH_SHORT
                        )
                        reloadUi()
                        return@launch
                    }

                    withContext(Dispatchers.IO) {
                        persistentState.orbotConnectionStatus.postValue(true)
                        orbotHelper.startOrbot(selectedOrbotMode)
                    }
                    logEvent("Orbot mode updated: $selectedOrbotMode")
                    reloadUi()
                }
            }
        )
    }

    if (showOrbotStopDialog) {
        AlertDialog(
            onDismissRequest = { showOrbotStopDialog = false },
            title = { Text(text = orbotStopTitle) },
            text = {
                Text(
                    text = if (orbotStopHasDnsHint) orbotStopMessageCombo else orbotStopMessage
                )
            },
            confirmButton = {
                TextButton(onClick = { showOrbotStopDialog = false }) {
                    Text(text = stringResource(R.string.lbl_dismiss))
                }
            },
            dismissButton = {
                Row {
                    if (orbotStopHasDnsHint && onNavigateToDns != null) {
                        TextButton(
                            onClick = {
                                showOrbotStopDialog = false
                                onNavigateToDns()
                            }
                        ) {
                            Text(text = stringResource(R.string.orbot_stop_dialog_neutral))
                        }
                    }
                    TextButton(
                        onClick = {
                            showOrbotStopDialog = false
                            orbotHelper.openOrbotApp()
                        }
                    ) {
                        Text(text = stringResource(R.string.orbot_stop_dialog_negative))
                    }
                }
            }
        )
    }

    socks5DialogState?.let { state ->
        Socks5Dialog(
            state = state,
            onStateChange = { socks5DialogState = it },
            onCancel = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        appConfig.removeProxy(
                            AppConfig.ProxyType.SOCKS5,
                            AppConfig.ProxyProvider.CUSTOM
                        )
                    }
                    socks5DialogState = null
                    reloadUi()
                }
            },
            onConfirm = {
                val current = socks5DialogState ?: return@Socks5Dialog
                scope.launch {
                    val validationError =
                        validateSocks5Input(
                            current.host,
                            current.port,
                            httpProxyHostEmptyError,
                            httpProxyInvalidPortError,
                            httpProxyRangePortError
                        )
                    if (validationError != null) {
                        socks5DialogState = current.copy(error = validationError)
                        return@launch
                    }

                    withContext(Dispatchers.IO) {
                        val appPackage =
                            if (current.selectedApp == defaultAppLabel) {
                                ""
                            } else {
                                FirewallManager.getPackageNameByAppName(current.selectedApp) ?: ""
                            }

                        var endpoint = appConfig.getSocks5ProxyDetails()
                        if (endpoint == null) {
                            appConfig.addProxy(
                                AppConfig.ProxyType.SOCKS5,
                                AppConfig.ProxyProvider.CUSTOM
                            )
                            endpoint = appConfig.getSocks5ProxyDetails()
                        }

                        endpoint?.let {
                            it.proxyIP = current.host.trim()
                            it.proxyPort = current.port.toInt()
                            it.userName = current.username.ifBlank { null }
                            it.password = current.password.ifBlank { null }
                            it.proxyAppName = appPackage
                            it.isUDP = current.udpBlocked
                            appConfig.updateCustomSocks5Proxy(it)
                        }

                        persistentState.setUdpBlocked(current.udpBlocked)
                        persistentState.excludeAppsInProxy = !current.includeProxyApps
                    }

                    logEvent(
                        "Custom SOCKS5 updated: ${current.host}:${current.port}, app=${current.selectedApp}, udp=${current.udpBlocked}"
                    )
                    socks5DialogState = null
                    reloadUi()
                }
            }
        )
    }

    httpDialogState?.let { state ->
        HttpDialog(
            state = state,
            onStateChange = { httpDialogState = it },
            onCancel = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        appConfig.removeProxy(
                            AppConfig.ProxyType.HTTP,
                            AppConfig.ProxyProvider.CUSTOM
                        )
                    }
                    httpDialogState = null
                    reloadUi()
                }
            },
            onConfirm = {
                val current = httpDialogState ?: return@HttpDialog
                scope.launch {
                    if (current.host.isBlank()) {
                        httpDialogState =
                            current.copy(
                                error = httpProxyHostEmptyError
                            )
                        return@launch
                    }

                    withContext(Dispatchers.IO) {
                        val appPackage =
                            if (current.selectedApp == defaultAppLabel) {
                                ""
                            } else {
                                FirewallManager.getPackageNameByAppName(current.selectedApp) ?: ""
                            }

                        var endpoint = appConfig.getHttpProxyDetails()
                        if (endpoint == null) {
                            appConfig.addProxy(
                                AppConfig.ProxyType.HTTP,
                                AppConfig.ProxyProvider.CUSTOM
                            )
                            endpoint = appConfig.getHttpProxyDetails()
                        }

                        endpoint?.let {
                            it.proxyIP = current.host.trim()
                            it.proxyPort = 0
                            it.userName = null
                            it.password = null
                            it.proxyAppName = appPackage
                            appConfig.updateCustomHttpProxy(it)
                        }

                        persistentState.excludeAppsInProxy = !current.includeProxyApps
                    }

                    Utilities.showToastUiCentered(
                        context,
                        httpProxyToastSuccess,
                        Toast.LENGTH_SHORT
                    )
                    logEvent("Custom HTTP updated: ${current.host}, app=${current.selectedApp}")
                    httpDialogState = null
                    reloadUi()
                }
            }
        )
    }
}

@Composable
private fun ProxyOverviewCard(
    canEnableProxy: Boolean,
    socks5Enabled: Boolean,
    httpEnabled: Boolean,
    orbotEnabled: Boolean
) {
    val activeCount = listOf(socks5Enabled, httpEnabled, orbotEnabled).count { it }

    Surface(
        shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
            )
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.spacingXl),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
        ) {
            Text(
                text = stringResource(R.string.settings_proxy_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.settings_exclude_proxy_apps_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Active: $activeCount / 3",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (!canEnableProxy) {
                Text(
                    text = stringResource(R.string.settings_lock_down_proxy_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun WireguardEntryCard(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.75f else 0.45f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun ProxyCard(
    title: String,
    description: String,
    enabled: Boolean,
    cardEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onConfigureClick: (() -> Unit)?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = enabled,
                    enabled = cardEnabled,
                    onCheckedChange = onEnabledChange
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (cardEnabled) 0.75f else 0.45f)
            )

            if (onConfigureClick != null && cardEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onConfigureClick) {
                    Text(text = stringResource(R.string.lbl_configure))
                }
            }
        }
    }
}

@Composable
private fun OrbotModeDialog(
    supportsHttp: Boolean,
    selectedMode: String,
    onSelectedMode: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val options =
        buildList {
            add(AppConfig.ProxyType.SOCKS5.name to R.string.orbot_socks5)
            if (supportsHttp) {
                add(AppConfig.ProxyType.HTTP.name to R.string.orbot_http)
                add(AppConfig.ProxyType.HTTP_SOCKS5.name to R.string.orbot_both)
            }
            add(AppConfig.ProxyType.NONE.name to R.string.orbot_none)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.orbot_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (value, labelRes) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelectedMode(value) }
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == value,
                            onClick = { onSelectedMode(value) }
                        )
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringResource(R.string.lbl_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.lbl_cancel)) }
        }
    )
}

@Composable
private fun Socks5Dialog(
    state: Socks5DialogState,
    onStateChange: (Socks5DialogState) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    var showApps by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = stringResource(R.string.settings_dns_proxy_dialog_header)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.host,
                    onValueChange = { onStateChange(state.copy(host = it, error = null)) },
                    label = { Text(text = stringResource(R.string.proxy_host_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.port,
                    onValueChange = { onStateChange(state.copy(port = it, error = null)) },
                    label = { Text(text = stringResource(R.string.proxy_port_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.username,
                    onValueChange = { onStateChange(state.copy(username = it, error = null)) },
                    label = { Text(text = stringResource(R.string.proxy_username_optional_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { onStateChange(state.copy(password = it, error = null)) },
                    label = { Text(text = stringResource(R.string.proxy_password_optional_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.settings_dns_proxy_dialog_app_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = { showApps = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = state.selectedApp)
                }
                DropdownMenu(
                    expanded = showApps,
                    onDismissRequest = { showApps = false }
                ) {
                    state.appNames.forEach { appName ->
                        DropdownMenuItem(
                            text = { Text(text = appName) },
                            onClick = {
                                showApps = false
                                onStateChange(state.copy(selectedApp = appName, error = null))
                            }
                        )
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onStateChange(
                                    state.copy(udpBlocked = !state.udpBlocked, error = null)
                                )
                            },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = state.udpBlocked,
                        onCheckedChange = {
                            onStateChange(state.copy(udpBlocked = it, error = null))
                        }
                    )
                    Text(
                        text = stringResource(R.string.settings_udp_block),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.lockdown) {
                                onStateChange(
                                    state.copy(
                                        includeProxyApps = !state.includeProxyApps,
                                        error = null
                                    )
                                )
                            },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = state.includeProxyApps,
                        enabled = !state.lockdown,
                        onCheckedChange = {
                            onStateChange(state.copy(includeProxyApps = it, error = null))
                        }
                    )
                    Text(
                        text = stringResource(R.string.settings_exclude_proxy_apps_heading),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                if (state.lockdown) {
                    Text(
                        text = stringResource(R.string.settings_lock_down_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { UIUtils.openVpnProfile(context) }
                    )
                }

                if (!state.error.isNullOrBlank()) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringResource(R.string.lbl_save)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(text = stringResource(R.string.lbl_cancel)) }
        }
    )
}

@Composable
private fun HttpDialog(
    state: HttpDialogState,
    onStateChange: (HttpDialogState) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    var showApps by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = stringResource(R.string.http_proxy_dialog_heading)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.http_proxy_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = state.host,
                    onValueChange = { onStateChange(state.copy(host = it, error = null)) },
                    label = { Text(text = stringResource(R.string.proxy_host_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.settings_dns_proxy_dialog_app_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showApps = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = state.selectedApp)
                }
                DropdownMenu(
                    expanded = showApps,
                    onDismissRequest = { showApps = false }
                ) {
                    state.appNames.forEach { appName ->
                        DropdownMenuItem(
                            text = { Text(text = appName) },
                            onClick = {
                                showApps = false
                                onStateChange(state.copy(selectedApp = appName, error = null))
                            }
                        )
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.lockdown) {
                                onStateChange(
                                    state.copy(
                                        includeProxyApps = !state.includeProxyApps,
                                        error = null
                                    )
                                )
                            },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = state.includeProxyApps,
                        enabled = !state.lockdown,
                        onCheckedChange = {
                            onStateChange(state.copy(includeProxyApps = it, error = null))
                        }
                    )
                    Text(
                        text = stringResource(R.string.settings_exclude_proxy_apps_heading),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                if (state.lockdown) {
                    Text(
                        text = stringResource(R.string.settings_lock_down_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { UIUtils.openVpnProfile(context) }
                    )
                }

                if (!state.error.isNullOrBlank()) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringResource(R.string.lbl_save)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(text = stringResource(R.string.lbl_cancel)) }
        }
    )
}

private suspend fun buildProxyScreenState(
    context: android.content.Context,
    appConfig: AppConfig
): ProxyScreenState {
    val canEnableProxy = appConfig.canEnableProxy()
    val socks5Enabled = appConfig.isCustomSocks5Enabled()
    val httpEnabled = appConfig.isCustomHttpProxyEnabled()
    val orbotEnabled = appConfig.isOrbotProxyEnabled()

    val socks5Description =
        if (!socks5Enabled) {
            context.resources.getString(R.string.settings_socks_forwarding_default_desc)
        } else {
            formatSocks5Description(context, runCatching { appConfig.getSocks5ProxyDetails() }.getOrNull())
        }

    val httpDescription =
        if (!httpEnabled) {
            context.resources.getString(R.string.settings_https_desc)
        } else {
            val endpoint = runCatching { appConfig.getHttpProxyDetails() }.getOrNull()
            val host = endpoint?.proxyIP ?: ""
            if (host.isBlank()) {
                context.resources.getString(R.string.settings_https_desc)
            } else {
                context.resources.getString(R.string.settings_http_proxy_desc, host)
            }
        }

    val orbotDescription = formatOrbotDescription(context, appConfig)
    val wireguardDescription = formatWireguardDescription(context)

    return ProxyScreenState(
        canEnableProxy = canEnableProxy,
        socks5Enabled = socks5Enabled,
        httpEnabled = httpEnabled,
        orbotEnabled = orbotEnabled,
        wireguardDescription = wireguardDescription,
        socks5Description = socks5Description,
        httpDescription = httpDescription,
        orbotDescription = orbotDescription
    )
}

private suspend fun formatSocks5Description(
    context: android.content.Context,
    endpoint: ProxyEndpoint?
): String {
    if (endpoint?.proxyIP.isNullOrBlank()) {
        return context.resources.getString(R.string.settings_socks_forwarding_default_desc)
    }

    val ip = endpoint?.proxyIP ?: return context.resources.getString(R.string.settings_socks_forwarding_default_desc)
    val port = (endpoint?.proxyPort ?: 0).toString()
    val packageName = endpoint.proxyAppName
    if (packageName.isNullOrBlank()) {
        return context.resources.getString(R.string.settings_socks_forwarding_desc_no_app, ip, port)
    }

    val appName = FirewallManager.getAppInfoByPackage(packageName)?.appName
    return if (appName.isNullOrBlank()) {
        context.resources.getString(R.string.settings_socks_forwarding_desc_no_app, ip, port)
    } else {
        context.resources.getString(R.string.settings_socks_forwarding_desc, ip, port, appName)
    }
}

private suspend fun formatOrbotDescription(
    context: android.content.Context,
    appConfig: AppConfig
): String {
    val isInstalled = FirewallManager.isOrbotInstalled()
    if (!isInstalled) {
        return context.resources.getString(R.string.settings_orbot_install_desc)
    }

    if (!appConfig.isOrbotProxyEnabled()) {
        return context.resources.getString(R.string.orbot_bs_status_4)
    }

    val isOrbotDns = appConfig.isOrbotDns()
    return when (appConfig.getProxyType()) {
        AppConfig.ProxyType.HTTP.name -> {
            context.resources.getString(R.string.orbot_bs_status_2)
        }
        AppConfig.ProxyType.SOCKS5.name -> {
            if (isOrbotDns) {
                context.resources.getString(
                    R.string.orbot_bs_status_1,
                    context.resources.getString(R.string.orbot_status_arg_3)
                )
            } else {
                context.resources.getString(
                    R.string.orbot_bs_status_1,
                    context.resources.getString(R.string.orbot_status_arg_2)
                )
            }
        }
        AppConfig.ProxyType.HTTP_SOCKS5.name -> {
            if (isOrbotDns) {
                context.resources.getString(
                    R.string.orbot_bs_status_3,
                    context.resources.getString(R.string.orbot_status_arg_3)
                )
            } else {
                context.resources.getString(
                    R.string.orbot_bs_status_3,
                    context.resources.getString(R.string.orbot_status_arg_2)
                )
            }
        }
        else -> context.resources.getString(R.string.orbot_bs_status_4)
    }
}

private suspend fun formatWireguardDescription(
    context: android.content.Context
): String {
    val activeWgs = WireguardManager.getActiveConfigs()
    if (activeWgs.isEmpty()) {
        return context.resources.getString(R.string.wireguard_description)
    }

    val details = StringBuilder()
    activeWgs.forEach {
        val id = ProxyManager.ID_WG_BASE + it.getId()
        val statusPair = VpnController.getProxyStatusById(id)
        val stats = VpnController.getProxyStats(id)
        val dnsStatusId = VpnController.getDnsStatus(id)

        val statusText =
            if (statusPair.first == Backend.TPU) {
                context.resources.getString(UIUtils.getProxyStatusStringRes(UIUtils.ProxyStatus.TPU.id))
                    .replaceFirstChar(Char::titlecase)
            } else if (isDnsError(dnsStatusId)) {
                context.resources.getString(R.string.status_failing).replaceFirstChar(Char::titlecase)
            } else {
                getProxyStatusText(context, statusPair, stats)
            }

        details.append(
            context.resources.getString(
                R.string.ci_ip_label,
                it.getName(),
                statusText.padStart(1, ' ')
            )
        )
        details.append('\n')
    }

    return details.toString().trimEnd()
}

private fun isDnsError(statusId: Long?): Boolean {
    if (statusId == null) return true

    val s = Transaction.Status.fromId(statusId)
    return s == Transaction.Status.BAD_QUERY ||
        s == Transaction.Status.BAD_RESPONSE ||
        s == Transaction.Status.NO_RESPONSE ||
        s == Transaction.Status.SEND_FAIL ||
        s == Transaction.Status.CLIENT_ERROR ||
        s == Transaction.Status.INTERNAL_ERROR ||
        s == Transaction.Status.TRANSPORT_ERROR
}

private fun getProxyStatusText(
    context: android.content.Context,
    statusPair: Pair<Long?, String>,
    stats: RouterStats?
): String {
    val status = UIUtils.ProxyStatus.entries.find { it.id == statusPair.first }
    if (status == null) {
        val txt =
            if (!statusPair.second.isNullOrBlank()) {
                context.resources.getString(R.string.status_waiting) + " (${statusPair.second})"
            } else {
                context.resources.getString(R.string.status_waiting)
            }
        return txt.replaceFirstChar(Char::titlecase)
    }

    val now = System.currentTimeMillis()
    val lastOk = stats?.lastOK ?: 0L
    val since = stats?.since ?: 0L
    if (now - since > WireguardManager.WG_UPTIME_THRESHOLD && lastOk == 0L) {
        return context.resources.getString(R.string.status_failing).replaceFirstChar(Char::titlecase)
    }

    val baseText =
        context.resources.getString(UIUtils.getProxyStatusStringRes(status.id))
            .replaceFirstChar(Char::titlecase)
    val handshakeTime =
        if (stats != null && stats.lastOK > 0L) {
            DateUtils.getRelativeTimeSpanString(
                    stats.lastOK,
                    now,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
                .toString()
        } else {
            null
        }

    return if (stats?.lastOK != 0L && handshakeTime != null) {
        context.resources.getString(R.string.about_version_install_source, baseText, handshakeTime)
    } else {
        baseText
    }
}

private suspend fun buildSocks5DialogState(
    context: android.content.Context,
    appConfig: AppConfig,
    persistentState: PersistentState
): Socks5DialogState {
    val defaultApp = context.resources.getString(R.string.settings_app_list_default_app)
    val endpoint = runCatching { appConfig.getSocks5ProxyDetails() }.getOrNull()
    val selectedApp =
        if (endpoint?.proxyAppName.isNullOrBlank()) {
            defaultApp
        } else {
            FirewallManager.getAppInfoByPackage(endpoint?.proxyAppName)?.appName ?: defaultApp
        }

    val appNames =
        buildList {
            add(defaultApp)
            addAll(FirewallManager.getAllAppNamesSortedByVpnPermission(context))
        }

    return Socks5DialogState(
        host = endpoint?.proxyIP ?: com.celzero.bravedns.util.Constants.SOCKS_DEFAULT_IP,
        port =
            if ((endpoint?.proxyPort ?: 0) > 0) {
                endpoint?.proxyPort?.toString() ?: com.celzero.bravedns.util.Constants.SOCKS_DEFAULT_PORT.toString()
            } else {
                com.celzero.bravedns.util.Constants.SOCKS_DEFAULT_PORT.toString()
            },
        username = endpoint?.userName.orEmpty(),
        password = endpoint?.password.orEmpty(),
        selectedApp = selectedApp,
        appNames = appNames,
        udpBlocked = persistentState.getUdpBlocked(),
        includeProxyApps = !persistentState.excludeAppsInProxy,
        lockdown = VpnController.isVpnLockdown()
    )
}

private suspend fun buildHttpDialogState(
    context: android.content.Context,
    appConfig: AppConfig,
    persistentState: PersistentState
): HttpDialogState {
    val defaultApp = context.resources.getString(R.string.settings_app_list_default_app)
    val endpoint = runCatching { appConfig.getHttpProxyDetails() }.getOrNull()
    val selectedApp =
        if (endpoint?.proxyAppName.isNullOrBlank()) {
            defaultApp
        } else {
            FirewallManager.getAppInfoByPackage(endpoint?.proxyAppName)?.appName ?: defaultApp
        }
    val appNames =
        buildList {
            add(defaultApp)
            addAll(FirewallManager.getAllAppNamesSortedByVpnPermission(context))
        }

    return HttpDialogState(
        host = endpoint?.proxyIP ?: "http://127.0.0.1:8118",
        selectedApp = selectedApp,
        appNames = appNames,
        includeProxyApps = !persistentState.excludeAppsInProxy,
        lockdown = VpnController.isVpnLockdown()
    )
}

private fun validateSocks5Input(
    host: String,
    portText: String,
    emptyHostError: String,
    invalidPortError: String,
    portRangeError: String
): String? {
    if (host.isBlank()) {
        return emptyHostError
    }

    val port =
        try {
            portText.toInt()
        } catch (_: NumberFormatException) {
            return invalidPortError
        }

    val valid =
        if (Utilities.isLanIpv4(host)) {
            Utilities.isValidLocalPort(port)
        } else {
            Utilities.isValidPort(port)
        }
    if (!valid) {
        return portRangeError
    }

    return null
}
