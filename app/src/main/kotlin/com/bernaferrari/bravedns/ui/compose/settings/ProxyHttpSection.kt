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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.util.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ProxyHttpDialogHost(
    state: HttpDialogState,
    onStateChange: (HttpDialogState?) -> Unit,
    appConfig: AppConfig,
    persistentState: PersistentState,
    httpProxyHostEmptyError: String,
    httpProxyToastSuccess: String,
    onLogEvent: (String) -> Unit,
    onReloadUi: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    HttpDialog(
        state = state,
        onStateChange = { onStateChange(it) },
        onCancel = { onStateChange(null) },
        onConfirm = {
            scope.launch {
                if (state.host.isBlank()) {
                    onStateChange(
                        state.copy(
                            error = httpProxyHostEmptyError
                        )
                    )
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    var endpoint = appConfig.getHttpProxyDetails()
                    if (endpoint == null) {
                        appConfig.addProxy(
                            AppConfig.ProxyType.HTTP,
                            AppConfig.ProxyProvider.CUSTOM
                        )
                        endpoint = appConfig.getHttpProxyDetails()
                    }

                    endpoint?.let {
                        it.proxyIP = state.host.trim()
                        it.proxyPort = 0
                        it.userName = null
                        it.password = null
                        it.proxyAppName = state.selectedAppPackage
                        appConfig.updateCustomHttpProxy(it)
                    }

                    persistentState.excludeAppsInProxy = !state.includeProxyApps
                }

                Utilities.showToastUiCentered(
                    context,
                    httpProxyToastSuccess,
                    Toast.LENGTH_SHORT
                )
                onLogEvent(
                    "Custom HTTP updated: ${state.host}, app=${state.selectedAppLabel()}"
                )
                onStateChange(null)
                onReloadUi()
            }
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
    RethinkProxyConfigurationDialog(
        kind = RethinkProxyConfigurationKind.Http,
        state = state.toRethinkProxyConfiguration(),
        strings = proxyConfigurationStrings(
            title = stringResource(R.string.http_proxy_dialog_heading),
            description = stringResource(R.string.http_proxy_dialog_desc),
        ),
        onStateChange = { next -> onStateChange(next.toHttpState(state)) },
        onCancel = onCancel,
        onConfirm = onConfirm,
        onLockdownInfo = { UIUtils.openVpnProfile(context) },
        appIcon = { option, size -> ProxyDialogAppIcon(state.appOptions.firstOrNull { it.packageName == option.id }, size) },
    )
}

private fun HttpDialogState.toRethinkProxyConfiguration() = RethinkProxyConfigurationState(
    host = host,
    selectedAppId = selectedAppPackage,
    apps = appOptions.map { RethinkProxyAppOption(it.packageName, it.label) },
    includeProxyApps = includeProxyApps,
    lockdown = lockdown,
    error = error,
)

private fun RethinkProxyConfigurationState.toHttpState(previous: HttpDialogState) = previous.copy(
    host = host,
    selectedAppPackage = selectedAppId,
    includeProxyApps = includeProxyApps,
    error = error,
)

internal fun HttpDialogState.selectedAppLabel(): String {
    return selectedProxyAppLabel(selectedAppPackage, appOptions)
}

internal suspend fun buildHttpDialogState(
    context: android.content.Context,
    appConfig: AppConfig,
    persistentState: PersistentState,
    defaultApp: String
): HttpDialogState {
    val endpoint = runCatching { appConfig.getHttpProxyDetails() }.getOrNull()
    val appOptions = buildProxyDialogAppOptions(context, defaultApp)

    val selectedPackage = endpoint?.proxyAppName.orEmpty()
    val normalizedSelectedPackage =
        if (appOptions.any { it.packageName == selectedPackage }) {
            selectedPackage
        } else {
            ""
        }

    return HttpDialogState(
        host = endpoint?.proxyIP ?: "http://127.0.0.1:8118",
        selectedAppPackage = normalizedSelectedPackage,
        appOptions = appOptions,
        includeProxyApps = !persistentState.excludeAppsInProxy,
        lockdown = VpnController.isVpnLockdown()
    )
}
