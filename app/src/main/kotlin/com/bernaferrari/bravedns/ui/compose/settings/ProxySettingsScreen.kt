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
package com.bernaferrari.bravedns.ui.compose.settings

import android.widget.Toast
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.ProxyManager
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.util.OrbotHelper
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.viewmodel.ProxyAppsMappingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private const val REFRESH_TIMEOUT_MS = 4000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(
    appConfig: AppConfig,
    persistentState: PersistentState,
    eventLogger: EventLogger,
    mappingViewModel: ProxyAppsMappingViewModel? = null,
    initialFocusKey: String? = null,
    onWireguardClick: (() -> Unit)? = null,
    onOpenOrbotApps: (() -> Unit)? = null,
    onNavigateToDns: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val providerLocale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.US
    val providerName =
        persistentState.proxyProvider.lowercase().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(providerLocale) else it.toString()
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
    val defaultSocks5NoAppTemplate = stringResource(R.string.settings_socks_forwarding_desc_no_app)
    val defaultSocks5WithAppTemplate = stringResource(R.string.settings_socks_forwarding_desc)
    val httpProxyDescriptionTemplate = stringResource(R.string.settings_http_proxy_desc)
    val orbotStatus2Description = stringResource(R.string.orbot_bs_status_2)
    val orbotStatus1Template = stringResource(R.string.orbot_bs_status_1)
    val orbotStatus3Template = stringResource(R.string.orbot_bs_status_3)
    val orbotStatusArgDns = stringResource(R.string.orbot_status_arg_3)
    val orbotStatusArgProxy = stringResource(R.string.orbot_status_arg_2)
    val wireguardStatusFailingText = stringResource(R.string.status_failing).replaceFirstChar(Char::titlecase)
    val wireguardStatusWaitingText = stringResource(R.string.status_waiting)
    val wireguardVersionTemplate = stringResource(R.string.about_version_install_source)
    val wireguardIpLabelTemplate = stringResource(R.string.ci_ip_label)
    val offHostTemplate = stringResource(R.string.proxy_state_off_host)
    val offNotSetUp = stringResource(R.string.proxy_state_off_not_set_up)
    val tunnelsNoneOnTemplate = stringResource(R.string.proxy_wireguard_tunnels_none_on)
    val noTunnels = stringResource(R.string.proxy_wireguard_empty)
    val resources = context.resources
    val offCopy = ProxyOffCopy(
        offWithHost = { host -> offHostTemplate.format(host) },
        offNotSetUp = offNotSetUp,
        tunnelsNoneOn = { count ->
            tunnelsNoneOnTemplate.format(resources.getQuantityString(R.plurals.proxy_wireguard_tunnels, count, count))
        },
        noTunnels = noTunnels,
    )
    val proxyStatusLabelById = mutableMapOf<Int, String>().apply {
        for (status in UIUtils.ProxyStatus.entries) {
            put(
                status.id,
                stringResource(UIUtils.getProxyStatusStringRes(status.id)).replaceFirstChar(Char::titlecase)
            )
        }
    }

    val orbotHelper = remember(context, persistentState, appConfig) {
        OrbotHelper(context, persistentState, appConfig)
    }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val initialFocus = initialFocusKey?.trim().orEmpty()
    var pendingFocusKey by rememberSaveable(initialFocus) { mutableStateOf(initialFocus) }
    var activeFocusKey by rememberSaveable(initialFocus) {
        mutableStateOf(initialFocus.ifBlank { null })
    }

    var refreshTick by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    var canEnableProxy by remember { mutableStateOf(appConfig.canEnableProxy()) }
    var socks5Enabled by remember { mutableStateOf(appConfig.isCustomSocks5Enabled()) }
    var httpEnabled by remember { mutableStateOf(appConfig.isCustomHttpProxyEnabled()) }
    // Assume saved until the first state load says otherwise, so a switch never pops in late.
    var socks5Configured by remember { mutableStateOf(true) }
    var httpConfigured by remember { mutableStateOf(true) }
    var orbotEnabled by remember { mutableStateOf(appConfig.isOrbotProxyEnabled()) }
    val orbotConnecting =
        remember { persistentState.orbotConnectionStatus }.collectAsState(initial = false).value

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
    val orbotAppCount =
        if (mappingViewModel != null) {
            mappingViewModel.getAppCountById(ProxyManager.ID_ORBOT_BASE)

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
                    buildSocks5DialogState(
                        context = context,
                        appConfig = appConfig,
                        persistentState = persistentState,
                        defaultApp = defaultAppLabel
                    )
                }
        }
    }

    fun openHttpDialog() {
        scope.launch {
            httpDialogState =
                withContext(Dispatchers.IO) {
                    buildHttpDialogState(
                        context = context,
                        appConfig = appConfig,
                        persistentState = persistentState,
                        defaultApp = defaultAppLabel
                    )
                }
        }
    }

    fun tryOpenCustomProxyDialog(
        canEnableSpecificProxy: () -> Boolean,
        disabledError: String,
        openDialog: () -> Unit
    ) {
        if (!canEnableProxy) {
            showProxyDisabledToast()
            reloadUi()
            return
        }

        if (appConfig.getBraveMode().isDnsMode()) {
            Utilities.showToastUiCentered(context, socks5VpnDisabledError, Toast.LENGTH_SHORT)
            reloadUi()
            return
        }

        if (!canEnableSpecificProxy()) {
            Utilities.showToastUiCentered(context, disabledError, Toast.LENGTH_SHORT)
            reloadUi()
            return
        }

        openDialog()
    }

    fun disableCustomProxy(type: AppConfig.ProxyType, logMessage: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                appConfig.removeProxy(type, AppConfig.ProxyProvider.CUSTOM)
            }
            logEvent(logMessage)
            reloadUi()
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
                buildProxyScreenState(
                    appConfig = appConfig,
                    defaultSocks5Description = defaultSocks5Description,
                    defaultHttpDescription = defaultHttpDescription,
                    httpProxyDescriptionTemplate = httpProxyDescriptionTemplate,
                    defaultSocks5DescriptionNoApp = defaultSocks5NoAppTemplate,
                    defaultSocks5DescriptionWithApp = defaultSocks5WithAppTemplate,
                    orbotDisabledDescription = defaultOrbotDescription,
                    orbotStatus2Description = orbotStatus2Description,
                    orbotStatus1Template = orbotStatus1Template,
                    orbotStatus3Template = orbotStatus3Template,
                    orbotStatusArgDns = orbotStatusArgDns,
                    orbotStatusArgProxy = orbotStatusArgProxy,
                    defaultWireguardDescription = defaultWireguardDescription,
                    statusTextById = proxyStatusLabelById,
                    statusFailingText = wireguardStatusFailingText,
                    statusWaitingText = wireguardStatusWaitingText,
                    wireguardVersionTemplate = wireguardVersionTemplate,
                    ciIpLabelTemplate = wireguardIpLabelTemplate,
                    offCopy = offCopy,
                )
            }

        canEnableProxy = state.canEnableProxy
        socks5Enabled = state.socks5Enabled
        httpEnabled = state.httpEnabled
        socks5Configured = state.socks5Configured
        httpConfigured = state.httpConfigured
        orbotEnabled = state.orbotEnabled
        wireguardDescription = state.wireguardDescription
        socks5Description = state.socks5Description
        httpDescription = state.httpDescription
        orbotDescription = state.orbotDescription
    }

    LaunchedEffect(pendingFocusKey, canEnableProxy, onWireguardClick != null) {
        val key = pendingFocusKey.trim()
        if (key.isBlank()) return@LaunchedEffect
        activeFocusKey = key

        val showWarning = !canEnableProxy
        var index = 0
        if (showWarning && key == "proxy_warning") {
            listState.animateScrollToItem(0)
            delay(900)
            if (activeFocusKey == key) {
                activeFocusKey = null
            }
            pendingFocusKey = ""
            return@LaunchedEffect
        }
        if (showWarning) index++

        // WireGuard, SOCKS5 and HTTP share one "servers" section item; Orbot is the next item.
        if (key == "proxy_wireguard" || key == "proxy_socks" || key == "proxy_http") {
            listState.animateScrollToItem(index)
            delay(900)
            if (activeFocusKey == key) {
                activeFocusKey = null
            }
            pendingFocusKey = ""
            return@LaunchedEffect
        }
        index++

        val orbotOffsetDp =
            when (key) {
                "proxy_orbot" -> 0
                "proxy_orbot_apps" -> 70
                "proxy_orbot_open_app",
                "proxy_orbot_notification" -> 70
                "proxy_orbot_info" -> if (mappingViewModel != null) 132 else 70
                else -> null
            }

        if (orbotOffsetDp != null) {
            val offsetPx = with(density) { orbotOffsetDp.dp.toPx().roundToInt() }
            listState.animateScrollToItem(index, offsetPx)
            delay(900)
            if (activeFocusKey == key) {
                activeFocusKey = null
            }
        }
        pendingFocusKey = ""
    }

    RethinkProxySettingsScreen(
        listState = listState,
        state = RethinkProxySettingsState(
            canEnableProxy = canEnableProxy,
            isRefreshing = isRefreshing,
            wireguardAvailable = onWireguardClick != null,
            wireguardDescription = wireguardDescription,
            socks5Enabled = socks5Enabled,
            socks5Description = socks5Description,
            httpEnabled = httpEnabled,
            httpDescription = httpDescription,
            orbotEnabled = orbotEnabled,
            orbotConnecting = orbotConnecting,
            orbotDescription = if (orbotConnecting) stringResource(R.string.orbot_bs_status_trying_connect) else orbotDescription,
            orbotAppCount = if (mappingViewModel != null) orbotAppCount else null,
            socks5Configured = socks5Configured,
            httpConfigured = httpConfigured,
        ),
        strings = RethinkProxySettingsStrings(
            title = stringResource(R.string.settings_proxy_vpn),
            refresh = stringResource(R.string.dc_refresh_toast),
            warning = stringResource(R.string.settings_lock_down_proxy_desc),
            wireguard = stringResource(R.string.proxy_row_wireguard),
            socks5 = stringResource(R.string.proxy_row_socks5),
            http = stringResource(R.string.proxy_row_http),
            orbot = stringResource(R.string.proxy_row_orbot),
            active = stringResource(R.string.lbl_active),
            inactive = stringResource(R.string.lbl_inactive),
            waiting = stringResource(R.string.status_waiting),
            apps = stringResource(R.string.lbl_apps),
            appsCount = { count -> pluralStringResource(R.plurals.orbot_apps_count, count, count) },
            openApp = stringResource(R.string.settings_orbot_notification_action),
            info = stringResource(R.string.lbl_info),
            serversSection = stringResource(R.string.proxy_servers_section),
            torSection = stringResource(R.string.proxy_tor_section),
            torExplainer = stringResource(R.string.proxy_orbot_explainer),
        ),
        activeFocusKey = activeFocusKey,
        onBackClick = onBackClick,
        onRefresh = ::refreshWireguard,
        onWireguardClick = { if (!canEnableProxy) showProxyDisabledToast() else onWireguardClick?.invoke() },
        onSocksRowClick = {
            if (canEnableProxy && socks5Enabled) openSocksDialog()
            else if (!socks5Enabled) tryOpenCustomProxyDialog(
                canEnableSpecificProxy = { appConfig.canEnableSocks5Proxy() },
                disabledError = socks5DisabledError,
                openDialog = ::openSocksDialog,
            )
        },
        onSocksChange = { enabled ->
            if (enabled) tryOpenCustomProxyDialog(
                canEnableSpecificProxy = { appConfig.canEnableSocks5Proxy() },
                disabledError = socks5DisabledError,
                openDialog = ::openSocksDialog,
            ) else disableCustomProxy(AppConfig.ProxyType.SOCKS5, "Custom SOCKS5 disabled")
        },
        onHttpRowClick = {
            if (canEnableProxy && httpEnabled) openHttpDialog()
            else if (!httpEnabled) tryOpenCustomProxyDialog(
                canEnableSpecificProxy = { appConfig.canEnableHttpProxy() },
                disabledError = httpDisabledError,
                openDialog = ::openHttpDialog,
            )
        },
        onHttpChange = { enabled ->
            if (enabled) tryOpenCustomProxyDialog(
                canEnableSpecificProxy = { appConfig.canEnableHttpProxy() },
                disabledError = httpDisabledError,
                openDialog = ::openHttpDialog,
            ) else disableCustomProxy(AppConfig.ProxyType.HTTP, "Custom HTTP disabled")
        },
        onOrbotClick = ::enableOrbotFlow,
        onOrbotChange = { checked -> if (checked) enableOrbotFlow() else stopOrbotForwarding(showDialog = true) },
        onOrbotAppsClick = if (mappingViewModel != null && onOpenOrbotApps != null) ({ onOpenOrbotApps() }) else null,
        onOpenOrbotApp = { orbotHelper.openOrbotApp() },
        // The explainer is a line under the Tor header now, not a dialog behind an Info row.
        onOrbotInfo = null,
    )
    ProxyOrbotDialogs(
        ui = ProxyOrbotDialogUi(
            showInfo = showOrbotInfoDialog,
            showInstall = showOrbotInstallDialog,
            showMode = showOrbotModeDialog,
            showStop = showOrbotStopDialog,
            selectedMode = selectedOrbotMode,
            stopHasDnsHint = orbotStopHasDnsHint,
        ),
        orbotHelper = orbotHelper,
        persistentState = persistentState,
        strings = ProxyOrbotDialogStrings(
            installError = orbotInstallError,
            websiteLink = orbotWebsiteLink,
            noAppToast = orbotNoAppToast,
            vpnDisabledError = socks5VpnDisabledError,
            stopTitle = orbotStopTitle,
            stopMessage = orbotStopMessage,
            stopMessageCombo = orbotStopMessageCombo,
        ),
        onUiChange = { next ->
            showOrbotInfoDialog = next.showInfo
            showOrbotInstallDialog = next.showInstall
            showOrbotModeDialog = next.showMode
            showOrbotStopDialog = next.showStop
            selectedOrbotMode = next.selectedMode
            orbotStopHasDnsHint = next.stopHasDnsHint
        },
        onStopOrbot = { stopOrbotForwarding(showDialog = true) },
        onLogEvent = ::logEvent,
        onReloadUi = ::reloadUi,
        onNavigateToDns = onNavigateToDns,
    )

    socks5DialogState?.let { state ->
        ProxySocks5DialogHost(
            state = state,
            onStateChange = { socks5DialogState = it },
            appConfig = appConfig,
            persistentState = persistentState,
            httpProxyHostEmptyError = httpProxyHostEmptyError,
            httpProxyInvalidPortError = httpProxyInvalidPortError,
            httpProxyRangePortError = httpProxyRangePortError,
            onLogEvent = ::logEvent,
            onReloadUi = ::reloadUi,
        )
    }

    httpDialogState?.let { state ->
        ProxyHttpDialogHost(
            state = state,
            onStateChange = { httpDialogState = it },
            appConfig = appConfig,
            persistentState = persistentState,
            httpProxyHostEmptyError = httpProxyHostEmptyError,
            httpProxyToastSuccess = httpProxyToastSuccess,
            onLogEvent = ::logEvent,
            onReloadUi = ::reloadUi,
        )
    }
}
