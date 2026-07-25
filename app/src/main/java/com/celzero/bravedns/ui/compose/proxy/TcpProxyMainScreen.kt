/* Copyright 2026 RethinkDNS and its authors */

package com.celzero.bravedns.ui.compose.proxy

import Logger
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.TcpProxyHelper
import com.celzero.bravedns.ui.dialog.WgIncludeAppsDialog
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.ProxyAppsMappingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "TcpProxyMainScreen"

/** Android service validation and app-picker bridge for common TCP proxy settings. */
@Composable
fun TcpProxyMainScreen(
    appConfig: AppConfig,
    mappingViewModel: ProxyAppsMappingViewModel,
    onBackClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tcpEnabled by remember { mutableStateOf(false) }
    var tcpStatus by remember { mutableStateOf("") }
    var tcpDescription by remember { mutableStateOf("") }
    var tcpError by remember { mutableStateOf<String?>(null) }
    var udpRelayEnabled by remember { mutableStateOf(false) }
    var warpEnabled by remember { mutableStateOf(false) }
    var showIncludeAppsDialog by remember { mutableStateOf(false) }
    val appCount by mappingViewModel.getAppCountById(ProxyManager.ID_TCP_BASE).collectAsState(initial = 0)
    val defaultDescription = stringResource(R.string.settings_https_desc)
    LaunchedEffect(Unit) { tcpDescription = defaultDescription }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { TcpProxyHelper.getActiveTcpProxy() }.let { proxy ->
            if (proxy == null || !proxy.isActive) {
                tcpStatus = "Not active"; tcpEnabled = false; tcpError = "Something went wrong"
            } else {
                Logger.i(TAG, "$TAG proxy: ${proxy.name}, ${proxy.url}")
                tcpStatus = "Active"; tcpEnabled = true; tcpError = null
            }
        }
    }
    fun updateTcpProxy(enabled: Boolean) {
        tcpEnabled = enabled
        scope.launch(Dispatchers.IO) {
            val hasApps = ProxyManager.isAnyAppSelected(ProxyManager.ID_TCP_BASE)
            withContext(Dispatchers.Main) {
                when {
                    enabled && warpEnabled -> {
                        tcpEnabled = false
                        Utilities.showToastUiCentered(context, context.getString(R.string.tcp_proxy_warp_active_error), Toast.LENGTH_SHORT)
                    }
                    enabled && !hasApps -> {
                        tcpEnabled = false
                        Utilities.showToastUiCentered(context, context.getString(R.string.tcp_proxy_no_apps_error), Toast.LENGTH_SHORT)
                    }
                    !enabled -> {
                        scope.launch(Dispatchers.IO) { TcpProxyHelper.disable() }
                        tcpDescription = defaultDescription
                    }
                    appConfig.getBraveMode().isDnsMode() -> tcpEnabled = false
                    !appConfig.canEnableTcpProxy() -> {
                        tcpEnabled = false
                        val provider = appConfig.getProxyProvider().lowercase().replaceFirstChar(Char::titlecase)
                        Utilities.showToastUiCentered(context, String.format(context.getString(R.string.settings_https_disabled_error), provider), Toast.LENGTH_SHORT)
                    }
                    else -> scope.launch(Dispatchers.IO) { TcpProxyHelper.enable() }
                }
            }
        }
    }
    if (showIncludeAppsDialog) {
        WgIncludeAppsDialog(
            viewModel = mappingViewModel,
            proxyId = ProxyManager.ID_TCP_BASE,
            proxyName = ProxyManager.TCP_PROXY_NAME,
            onDismiss = { showIncludeAppsDialog = false },
        )
    }
    RethinkTcpProxyScreen(
        tcpProxyEnabled = tcpEnabled,
        tcpProxyDescription = tcpStatus.ifEmpty { tcpDescription },
        tcpError = tcpError,
        udpRelayEnabled = udpRelayEnabled,
        warpEnabled = warpEnabled,
        appCount = appCount,
        strings = RethinkTcpProxyStrings(
            title = stringResource(R.string.settings_https_heading), active = stringResource(R.string.lbl_active), inactive = stringResource(R.string.lbl_inactive),
            rethinkProxyTitle = stringResource(R.string.tcp_proxy_rethink_proxy_title), rethinkProxyDescription = stringResource(R.string.settings_https_desc),
            udpRelayTitle = stringResource(R.string.tcp_proxy_enable_udp_relay), udpRelayDescription = stringResource(R.string.adv_set_experimental_desc),
            appsTitle = stringResource(R.string.lbl_apps), appsDescription = { count -> stringResource(R.string.add_remove_apps, count.toString()) },
            warpTitle = stringResource(R.string.tcp_proxy_cloudflare_warp_title), warpDescription = stringResource(R.string.tcp_proxy_cloudflare_warp_desc),
        ),
        onTcpProxyEnabledChange = ::updateTcpProxy,
        onUdpRelayEnabledChange = { udpRelayEnabled = it },
        onWarpEnabledChange = { warpEnabled = it },
        onAppsClick = { showIncludeAppsDialog = true },
        onBackClick = onBackClick,
    )
}
