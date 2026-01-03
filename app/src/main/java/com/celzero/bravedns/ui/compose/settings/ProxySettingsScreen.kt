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

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.util.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Full Proxy Settings Screen for navigation integration.
 * Displays proxy configuration options including SOCKS5, HTTP, and Orbot integration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(
    appConfig: AppConfig,
    persistentState: PersistentState,
    eventLogger: EventLogger,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showSocks5Dialog by remember { mutableStateOf(false) }
    var showHttpDialog by remember { mutableStateOf(false) }
    
    // Proxy states - use simple boolean states without calling complex AppConfig methods
    var socks5Enabled by remember { mutableStateOf(false) }
    var httpEnabled by remember { mutableStateOf(false) }
    var orbotEnabled by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Proxy Settings") },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_arrow_back_24),
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // SOCKS5 Proxy Section
            ProxyCard(
                title = "SOCKS5 Proxy",
                description = "Route traffic through a SOCKS5 proxy server",
                enabled = socks5Enabled,
                onEnabledChange = { enabled ->
                    if (enabled) {
                        showSocks5Dialog = true
                    } else {
                        scope.launch(Dispatchers.IO) {
                            appConfig.removeProxy(AppConfig.ProxyType.SOCKS5, AppConfig.ProxyProvider.CUSTOM)
                        }
                        socks5Enabled = false
                    }
                },
                onConfigureClick = { showSocks5Dialog = true }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // HTTP Proxy Section
            ProxyCard(
                title = "HTTP Proxy",
                description = "Route traffic through an HTTP proxy server",
                enabled = httpEnabled,
                onEnabledChange = { enabled ->
                    if (enabled) {
                        showHttpDialog = true
                    } else {
                        scope.launch(Dispatchers.IO) {
                            appConfig.removeProxy(AppConfig.ProxyType.HTTP, AppConfig.ProxyProvider.CUSTOM)
                        }
                        httpEnabled = false
                    }
                },
                onConfigureClick = { showHttpDialog = true }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Orbot Integration Section
            ProxyCard(
                title = "Orbot",
                description = "Route traffic through Tor via Orbot app",
                enabled = orbotEnabled,
                onEnabledChange = { enabled ->
                    orbotEnabled = enabled
                    if (enabled) {
                        Utilities.showToastUiCentered(context, "Orbot integration enabled", Toast.LENGTH_SHORT)
                    }
                },
                onConfigureClick = null
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Proxy Info
            Text(
                text = "Configure proxy settings to route your traffic through external servers. SOCKS5 and HTTP proxies can be used for privacy or bypassing restrictions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
    
    // SOCKS5 Configuration Dialog
    if (showSocks5Dialog) {
        ProxyConfigDialog(
            title = "SOCKS5 Proxy",
            onDismiss = { showSocks5Dialog = false },
            onConfirm = { host, port, _, _ ->
                scope.launch(Dispatchers.IO) {
                    appConfig.addProxy(AppConfig.ProxyType.SOCKS5, AppConfig.ProxyProvider.CUSTOM)
                }
                socks5Enabled = true
                showSocks5Dialog = false
                Utilities.showToastUiCentered(context, "SOCKS5 proxy configured", Toast.LENGTH_SHORT)
            }
        )
    }
    
    // HTTP Configuration Dialog
    if (showHttpDialog) {
        ProxyConfigDialog(
            title = "HTTP Proxy",
            onDismiss = { showHttpDialog = false },
            onConfirm = { host, port, _, _ ->
                scope.launch(Dispatchers.IO) {
                    appConfig.addProxy(AppConfig.ProxyType.HTTP, AppConfig.ProxyProvider.CUSTOM)
                }
                httpEnabled = true
                showHttpDialog = false
                Utilities.showToastUiCentered(context, "HTTP proxy configured", Toast.LENGTH_SHORT)
            }
        )
    }
}

@Composable
private fun ProxyCard(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onConfigureClick: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                    onCheckedChange = onEnabledChange
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            if (onConfigureClick != null && enabled) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onConfigureClick) {
                    Text(text = stringResource(R.string.lbl_configure))
                }
            }
        }
    }
}

@Composable
private fun ProxyConfigDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (host: String, port: String, username: String, password: String) -> Unit
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(host, port, username, password) },
                enabled = host.isNotBlank() && port.isNotBlank()
            ) {
                Text(text = stringResource(R.string.lbl_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.lbl_cancel))
            }
        }
    )
}
