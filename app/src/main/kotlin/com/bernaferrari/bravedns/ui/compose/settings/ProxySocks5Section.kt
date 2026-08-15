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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.ProxyEndpoint
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.util.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ProxySocks5DialogHost(
    state: Socks5DialogState,
    onStateChange: (Socks5DialogState?) -> Unit,
    appConfig: AppConfig,
    persistentState: PersistentState,
    httpProxyHostEmptyError: String,
    httpProxyInvalidPortError: String,
    httpProxyRangePortError: String,
    onLogEvent: (String) -> Unit,
    onReloadUi: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Socks5Dialog(
        state = state,
        onStateChange = { onStateChange(it) },
        onCancel = {
            scope.launch {
                withContext(Dispatchers.IO) {
                    appConfig.removeProxy(
                        AppConfig.ProxyType.SOCKS5,
                        AppConfig.ProxyProvider.CUSTOM
                    )
                }
                onStateChange(null)
                onReloadUi()
            }
        },
        onConfirm = {
            scope.launch {
                val validationError =
                    validateSocks5Input(
                        state.host,
                        state.port,
                        httpProxyHostEmptyError,
                        httpProxyInvalidPortError,
                        httpProxyRangePortError
                    )
                if (validationError != null) {
                    onStateChange(state.copy(error = validationError))
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    var endpoint = appConfig.getSocks5ProxyDetails()
                    if (endpoint == null) {
                        appConfig.addProxy(
                            AppConfig.ProxyType.SOCKS5,
                            AppConfig.ProxyProvider.CUSTOM
                        )
                        endpoint = appConfig.getSocks5ProxyDetails()
                    }

                    endpoint?.let {
                        it.proxyIP = state.host.trim()
                        it.proxyPort = state.port.toInt()
                        it.userName = state.username.ifBlank { null }
                        it.password = state.password.ifBlank { null }
                        it.proxyAppName = state.selectedAppPackage
                        it.isUDP = state.udpBlocked
                        appConfig.updateCustomSocks5Proxy(it)
                    }

                    persistentState.setUdpBlocked(state.udpBlocked)
                    persistentState.excludeAppsInProxy = !state.includeProxyApps
                }

                onLogEvent(
                    "Custom SOCKS5 updated: ${state.host}:${state.port}, app=${state.selectedAppLabel()}, udp=${state.udpBlocked}"
                )
                onStateChange(null)
                onReloadUi()
            }
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
    RethinkProxyConfigurationDialog(
        kind = RethinkProxyConfigurationKind.Socks5,
        state = state.toRethinkProxyConfiguration(),
        strings = proxyConfigurationStrings(
            title = stringResource(R.string.settings_dns_proxy_dialog_header),
            description = null,
        ),
        onStateChange = { next -> onStateChange(next.toSocks5State(state)) },
        onCancel = onCancel,
        onConfirm = onConfirm,
        onLockdownInfo = { UIUtils.openVpnProfile(context) },
        appIcon = { option, size -> ProxyDialogAppIcon(state.appOptions.firstOrNull { it.packageName == option.id }, size) },
    )
}

private fun Socks5DialogState.toRethinkProxyConfiguration() = RethinkProxyConfigurationState(
    host = host,
    port = port,
    username = username,
    password = password,
    selectedAppId = selectedAppPackage,
    apps = appOptions.map { RethinkProxyAppOption(it.packageName, it.label) },
    udpBlocked = udpBlocked,
    includeProxyApps = includeProxyApps,
    lockdown = lockdown,
    error = error,
)

private fun RethinkProxyConfigurationState.toSocks5State(previous: Socks5DialogState) = previous.copy(
    host = host,
    port = port,
    username = username,
    password = password,
    selectedAppPackage = selectedAppId,
    udpBlocked = udpBlocked,
    includeProxyApps = includeProxyApps,
    error = error,
)

internal fun Socks5DialogState.selectedAppLabel(): String {
    return selectedProxyAppLabel(selectedAppPackage, appOptions)
}

internal suspend fun buildSocks5DialogState(
    context: android.content.Context,
    appConfig: AppConfig,
    persistentState: PersistentState,
    defaultApp: String
): Socks5DialogState {
    val endpoint = runCatching { appConfig.getSocks5ProxyDetails() }.getOrNull()
    val appOptions = buildProxyDialogAppOptions(context, defaultApp)
    val selectedPackage = endpoint?.proxyAppName.orEmpty()
    val normalizedSelectedPackage =
        if (appOptions.any { it.packageName == selectedPackage }) {
            selectedPackage
        } else {
            ""
        }

    return Socks5DialogState(
        host = endpoint?.proxyIP ?: com.bernaferrari.bravedns.util.Constants.SOCKS_DEFAULT_IP,
        port =
            if ((endpoint?.proxyPort ?: 0) > 0) {
                endpoint?.proxyPort?.toString() ?: com.bernaferrari.bravedns.util.Constants.SOCKS_DEFAULT_PORT.toString()
            } else {
                com.bernaferrari.bravedns.util.Constants.SOCKS_DEFAULT_PORT.toString()
            },
        username = endpoint?.userName.orEmpty(),
        password = endpoint?.password.orEmpty(),
        selectedAppPackage = normalizedSelectedPackage,
        appOptions = appOptions,
        udpBlocked = persistentState.getUdpBlocked(),
        includeProxyApps = !persistentState.excludeAppsInProxy,
        lockdown = VpnController.isVpnLockdown()
    )
}

internal fun validateSocks5Input(
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

internal suspend fun formatSocks5Description(
    endpoint: ProxyEndpoint?,
    defaultDescription: String,
    defaultDescriptionNoAppTemplate: String,
    defaultDescriptionWithAppTemplate: String
): String {
    val endpointValue = endpoint ?: return defaultDescription
    if (endpointValue.proxyIP.isNullOrBlank()) {
        return defaultDescription
    }

    val ip = endpointValue.proxyIP ?: return defaultDescription
    val port = endpointValue.proxyPort.toString()
    val packageName = endpointValue.proxyAppName
    if (packageName.isNullOrBlank()) {
        return String.format(defaultDescriptionNoAppTemplate, ip, port)
    }

    val appName = FirewallManager.getAppInfoByPackage(packageName)?.appName
    return if (appName.isNullOrBlank()) {
        String.format(defaultDescriptionNoAppTemplate, ip, port)
    } else {
        String.format(defaultDescriptionWithAppTemplate, ip, port, appName)
    }
}
