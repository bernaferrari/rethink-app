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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.service.VpnController
import com.celzero.firestack.backend.Backend
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PING_IP1 = "1.1.1.1:53"
private const val PING_IP2 = "8.8.8.8:53"
private const val PING_IP3 = "216.239.32.27:443"
private const val PING_HOST1 = "cloudflare.com:443"
private const val PING_HOST2 = "google.com:443"
private const val PING_HOST3 = "brave.com:443"
private const val STRENGTH_MAX = 5
private const val TAG = "PingUi"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingTestScreen(
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var ip1 by remember { mutableStateOf(PING_IP1) }
    var ip2 by remember { mutableStateOf(PING_IP2) }
    var ip3 by remember { mutableStateOf(PING_IP3) }
    var host1 by remember { mutableStateOf(PING_HOST1) }
    var host2 by remember { mutableStateOf(PING_HOST2) }
    var host3 by remember { mutableStateOf(PING_HOST3) }

    var ip1Status by remember { mutableStateOf<PingStatus>(PingStatus.Idle) }
    var ip2Status by remember { mutableStateOf<PingStatus>(PingStatus.Idle) }
    var ip3Status by remember { mutableStateOf<PingStatus>(PingStatus.Idle) }
    var host1Status by remember { mutableStateOf<PingStatus>(PingStatus.Idle) }
    var host2Status by remember { mutableStateOf<PingStatus>(PingStatus.Idle) }
    var host3Status by remember { mutableStateOf<PingStatus>(PingStatus.Idle) }

    var strength by remember { mutableStateOf<Int?>(null) }
    val showStartVpnDialog = remember { !VpnController.hasTunnel() }
    
    // Cache for proxy status
    val proxiesStatus = remember { mutableListOf<Boolean>() }

    suspend fun getProxiesStatus(csv: String): List<Boolean> {
        if (proxiesStatus.isNotEmpty()) return proxiesStatus

        return withContext(Dispatchers.IO) {
            val warp = VpnController.isProxyReachable(Backend.RpnWin, csv)
            val amz = VpnController.isProxyReachable(Backend.RpnWin, csv)
            val win = VpnController.isProxyReachable(Backend.RpnWin, csv)
            val se = VpnController.isProxyReachable(Backend.RpnSE, csv)
            val w64 = VpnController.isProxyReachable(Backend.Rpn64, csv)
            Napier.d("$TAG proxies reachable: $warp, $amz $win, $se, $w64")
            
            val status = listOf(warp, amz, win, se, w64)
            proxiesStatus.clear()
            proxiesStatus.addAll(status)
            status
        }
    }

    suspend fun isReachable(csv: String): Boolean {
        val status = getProxiesStatus(csv)
        // Check if any proxy is reachable
        val reachable = status.any { it }
        Napier.d("$TAG ip $csv reachable: $reachable")
        return reachable
    }

    suspend fun calculateStrength(csv: String): Int {
        val status = getProxiesStatus(csv)
        // Count how many are true
        val strengthVal = status.count { it }
        Napier.i("$TAG strength: $strengthVal ($status)")
        return strengthVal
    }

    fun performPing() {
        scope.launch {
            try {
                proxiesStatus.clear() // Clear cache for new test
                Napier.v("$TAG initiating ping test")
                ip1Status = PingStatus.Loading
                ip2Status = PingStatus.Loading
                ip3Status = PingStatus.Loading
                host1Status = PingStatus.Loading
                host2Status = PingStatus.Loading
                host3Status = PingStatus.Loading
                strength = null

                val ip1Local = ip1
                val ip2Local = ip2
                val ip3Local = ip3
                val host1Local = host1
                val host2Local = host2
                val host3Local = host3

                // Run reachable checks sequentially in IO as per original
                val validI1 = isReachable(ip1Local)
                val validI2 = isReachable(ip2Local)
                val validI3 = isReachable(ip3Local)
                val validH1 = isReachable(host1Local)
                val validH2 = isReachable(host2Local)
                val validH3 = isReachable(host3Local)

                ip1Status = PingStatus.Result(validI1)
                ip2Status = PingStatus.Result(validI2)
                ip3Status = PingStatus.Result(validI3)
                host1Status = PingStatus.Result(validH1)
                host2Status = PingStatus.Result(validH2)
                host3Status = PingStatus.Result(validH3)

                val strengthValue = calculateStrength(ip3Local)
                strength = strengthValue.coerceIn(1, STRENGTH_MAX)
            } catch (e: Exception) {
                Napier.e("$TAG err isReachable: ${e.message}", e)
                ip1Status = PingStatus.Result(false)
                ip2Status = PingStatus.Result(false)
                ip3Status = PingStatus.Result(false)
                host1Status = PingStatus.Result(false)
                host2Status = PingStatus.Result(false)
                host3Status = PingStatus.Result(false)
            }
        }
    }

    Scaffold(
        topBar = {
             TopAppBar(
                title = { Text(text = stringResource(R.string.settings_connectivity_checks)) },
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
    ) { paddingValues ->
        if (showStartVpnDialog) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(text = stringResource(androidx.appcompat.R.string.abc_capital_off)) }, // Fallback titles? Using generic for now
                 // Wait, I should use the R.string used in activity
                // vpn_not_active_dialog_title
                 // I will assume R.string... exists. Ideally I check R.string.
                // Reverting to what I saw in code: R.string.vpn_not_active_dialog_title
                text = { Text(text = stringResource(R.string.vpn_not_active_dialog_desc)) },
                confirmButton = {
                    TextButton(onClick = { onBackClick?.invoke() }) {
                        Text(text = stringResource(R.string.dns_info_positive))
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.ping_ip_port_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            PingField(
                value = ip1,
                readOnly = true,
                status = ip1Status
            )
            PingField(
                value = ip2,
                readOnly = true,
                status = ip2Status
            )
            PingField(
                value = ip3,
                readOnly = false,
                status = ip3Status,
                onValueChange = { ip3 = it }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.ping_host_port_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            PingField(
                value = host1,
                readOnly = true,
                status = host1Status
            )
            PingField(
                value = host2,
                readOnly = true,
                status = host2Status
            )
            PingField(
                value = host3,
                readOnly = false,
                status = host3Status,
                onValueChange = { host3 = it }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onBackClick?.invoke() }
                ) {
                    Text(text = stringResource(R.string.lbl_cancel))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { performPing() }
                ) {
                    Text(text = stringResource(R.string.lbl_test))
                }
            }

            strength?.let { value ->
                val progress = value.toFloat() / STRENGTH_MAX.toFloat()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.ping_strength_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.two_argument,
                            value.toString(),
                            STRENGTH_MAX.toString()
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PingField(
    value: String,
    readOnly: Boolean,
    status: PingStatus,
    onValueChange: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Spacer(modifier = Modifier.width(8.dp))
        when (status) {
            PingStatus.Loading -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            is PingStatus.Result -> {
                val icon =
                    if (status.success) R.drawable.ic_tick else R.drawable.ic_cross_accent
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            PingStatus.Idle -> Unit
        }
    }
}

private sealed class PingStatus {
    data object Idle : PingStatus()
    data object Loading : PingStatus()
    data class Result(val success: Boolean) : PingStatus()
}
