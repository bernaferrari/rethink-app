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
package com.celzero.bravedns.ui.compose.proxy

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.TcpProxyHelper
import com.celzero.bravedns.ui.dialog.WgIncludeAppsDialog
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.ProxyAppsMappingViewModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "TcpProxyMainScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TcpProxyMainScreen(
    appConfig: AppConfig,
    mappingViewModel: ProxyAppsMappingViewModel,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tcpProxySwitchChecked by remember { mutableStateOf(false) }
    var tcpProxyStatus by remember { mutableStateOf("") }
    var tcpProxyDesc by remember { mutableStateOf("") }
    var tcpProxyAddAppsText by remember { mutableStateOf("") }
    var tcpErrorVisible by remember { mutableStateOf(false) }
    var tcpErrorText by remember { mutableStateOf("") }
    var enableUdpRelayChecked by remember { mutableStateOf(false) }
    var warpSwitchChecked by remember { mutableStateOf(false) }
    var showIncludeAppsDialog by remember { mutableStateOf(false) }
    var includeAppsProxyId by remember { mutableStateOf("") }
    var includeAppsProxyName by remember { mutableStateOf("") }

    // Observe app count for TCP proxy
    val appCount by mappingViewModel.getAppCountById(ProxyManager.ID_TCP_BASE)
        .asFlow()
        .collectAsState(initial = null)

    // Update add apps text when app count changes
    LaunchedEffect(appCount) {
        tcpProxyAddAppsText = if (appCount == null || appCount == 0) {
            context.resources.getString(R.string.add_remove_apps, "0")
        } else {
            context.resources.getString(R.string.add_remove_apps, appCount.toString())
        }
    }

    // Initialize description
    LaunchedEffect(Unit) {
        tcpProxyDesc = context.resources.getString(R.string.settings_https_desc)
        tcpProxyAddAppsText = context.resources.getString(R.string.add_remove_apps, "0")
    }

    // Display TCP proxy status on launch
    LaunchedEffect(Unit) {
        displayTcpProxyStatus(
            onStatusUpdate = { status, switchChecked, errorVisible, errorText ->
                tcpProxyStatus = status
                tcpProxySwitchChecked = switchChecked
                tcpErrorVisible = errorVisible
                tcpErrorText = errorText
            }
        )
    }

    fun showTcpErrorLayout() {
        tcpErrorVisible = true
        tcpErrorText = context.resources.getString(R.string.something_went_wrong)
    }

    fun onTcpProxySwitchChanged(checked: Boolean) {
        tcpProxySwitchChecked = checked
        scope.launch(Dispatchers.IO) {
            val isActive = true
            withContext(Dispatchers.Main) {
                if (checked && isActive) {
                    tcpProxySwitchChecked = false
                    Utilities.showToastUiCentered(
                        context,
                        context.resources.getString(R.string.tcp_proxy_warp_active_error),
                        Toast.LENGTH_SHORT
                    )
                    return@withContext
                }

                val apps = ProxyManager.isAnyAppSelected(ProxyManager.ID_TCP_BASE)

                if (!apps) {
                    Utilities.showToastUiCentered(
                        context,
                        context.resources.getString(R.string.tcp_proxy_no_apps_error),
                        Toast.LENGTH_SHORT
                    )
                    warpSwitchChecked = false
                    tcpProxySwitchChecked = false
                    return@withContext
                }

                if (!checked) {
                    scope.launch(Dispatchers.IO) { TcpProxyHelper.disable() }
                    tcpProxyDesc = context.resources.getString(R.string.settings_https_desc)
                    return@withContext
                }

                if (appConfig.getBraveMode().isDnsMode()) {
                    tcpProxySwitchChecked = false
                    return@withContext
                }

                if (!appConfig.canEnableTcpProxy()) {
                    val s = appConfig.getProxyProvider()
                        .lowercase()
                        .replaceFirstChar(Char::titlecase)
                    Utilities.showToastUiCentered(
                        context,
                        context.resources.getString(R.string.settings_https_disabled_error, s),
                        Toast.LENGTH_SHORT
                    )
                    tcpProxySwitchChecked = false
                    return@withContext
                }
                scope.launch(Dispatchers.IO) {
                    TcpProxyHelper.enable()
                }
            }
        }
    }

    fun openAppsDialog() {
        val proxyId = ProxyManager.ID_TCP_BASE
        val proxyName = ProxyManager.TCP_PROXY_NAME
        includeAppsProxyId = proxyId
        includeAppsProxyName = proxyName
        showIncludeAppsDialog = true
    }

    if (showIncludeAppsDialog) {
        WgIncludeAppsDialog(
            viewModel = mappingViewModel,
            proxyId = includeAppsProxyId,
            proxyName = includeAppsProxyName,
            onDismiss = { showIncludeAppsDialog = false }
        )
    }

    Scaffold(
        topBar = {
            RethinkTopBar(
                title = stringResource(R.string.settings_proxy_header),
                onBackClick = onBackClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Rethink Proxy Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_wireguard_icon),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.tcp_proxy_rethink_proxy_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Switch(
                            checked = tcpProxySwitchChecked,
                            onCheckedChange = { onTcpProxySwitchChanged(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = tcpProxyDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = tcpProxyStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.tcp_proxy_enable_udp_relay),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = enableUdpRelayChecked,
                            onCheckedChange = { enableUdpRelayChecked = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(onClick = { openAppsDialog() }) {
                        Text(text = tcpProxyAddAppsText)
                    }

                    if (tcpErrorVisible) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = tcpErrorText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Cloudflare WARP Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_wireguard_icon),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.tcp_proxy_cloudflare_warp_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Switch(
                            checked = warpSwitchChecked,
                            onCheckedChange = { warpSwitchChecked = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.tcp_proxy_cloudflare_warp_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private suspend fun displayTcpProxyStatus(
    onStatusUpdate: (status: String, switchChecked: Boolean, errorVisible: Boolean, errorText: String) -> Unit
) {
    withContext(Dispatchers.IO) {
        val tcpProxies = TcpProxyHelper.getActiveTcpProxy()
        withContext(Dispatchers.Main) {
            if (tcpProxies == null || !tcpProxies.isActive) {
                onStatusUpdate("Not active", false, true, "Something went wrong")
                return@withContext
            }

            Napier.i("$TAG displayTcpProxyUi: ${tcpProxies.name}, ${tcpProxies.url}")
            onStatusUpdate("Active", true, false, "")
        }
    }
}
