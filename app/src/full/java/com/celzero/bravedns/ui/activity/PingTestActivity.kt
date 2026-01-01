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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.firestack.backend.Backend
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class PingTestActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()

    private var ip1 by mutableStateOf(PING_IP1)
    private var ip2 by mutableStateOf(PING_IP2)
    private var ip3 by mutableStateOf(PING_IP3)
    private var host1 by mutableStateOf(PING_HOST1)
    private var host2 by mutableStateOf(PING_HOST2)
    private var host3 by mutableStateOf(PING_HOST3)

    private var ip1Status by mutableStateOf<PingStatus>(PingStatus.Idle)
    private var ip2Status by mutableStateOf<PingStatus>(PingStatus.Idle)
    private var ip3Status by mutableStateOf<PingStatus>(PingStatus.Idle)
    private var host1Status by mutableStateOf<PingStatus>(PingStatus.Idle)
    private var host2Status by mutableStateOf<PingStatus>(PingStatus.Idle)
    private var host3Status by mutableStateOf<PingStatus>(PingStatus.Idle)

    private var strength by mutableStateOf<Int?>(null)
    private var showStartVpnDialog by mutableStateOf(false)

    private val proxiesStatus = mutableListOf<Boolean>()

    companion object {
        private const val TAG = "PingUi"
        private const val PING_IP1 = "1.1.1.1:53"
        private const val PING_IP2 = "8.8.8.8:53"
        private const val PING_IP3 = "216.239.32.27:443"
        private const val PING_HOST1 = "cloudflare.com:443"
        private const val PING_HOST2 = "google.com:443"
        private const val PING_HOST3 = "brave.com:443"
        private const val STRENGTH_MAX = 5
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

        if (!VpnController.hasTunnel()) {
            showStartVpnDialog = true
        }

        setContent {
            RethinkTheme {
                PingTestScreen()
            }
        }
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @Composable
    private fun PingTestScreen() {
        if (showStartVpnDialog) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(text = getString(R.string.vpn_not_active_dialog_title)) },
                text = { Text(text = getString(R.string.vpn_not_active_dialog_desc)) },
                confirmButton = {
                    TextButton(onClick = { finish() }) {
                        Text(text = getString(R.string.dns_info_positive))
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = stringResourceCompat(R.string.ping_ip_port_title),
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
                text = stringResourceCompat(R.string.ping_host_port_title),
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
                    onClick = { finish() }
                ) {
                    Text(text = stringResourceCompat(R.string.lbl_cancel))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { performPing() }
                ) {
                    Text(text = stringResourceCompat(R.string.lbl_test))
                }
            }

            strength?.let { value ->
                val progress = value.toFloat() / STRENGTH_MAX.toFloat()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResourceCompat(R.string.ping_strength_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResourceCompat(
                            R.string.two_argument,
                            value.toString(),
                            STRENGTH_MAX.toString()
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
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

    private fun performPing() {
        try {
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

            io {
                val validI1 = isReachable(ip1Local)
                val validI2 = isReachable(ip2Local)
                val validI3 = isReachable(ip3Local)

                val validH1 = isReachable(host1Local)
                val validH2 = isReachable(host2Local)
                val validH3 = isReachable(host3Local)

                Napier.d("$TAG ip1 reachable: $validI1, ip2 reachable: $validI2, ip3 reachable: $validI3")
                Napier.d("$TAG host1 reachable: $validH1, host2 reachable: $validH2, host3 reachable: $validH3")

                uiCtx {
                    ip1Status = PingStatus.Result(validI1)
                    ip2Status = PingStatus.Result(validI2)
                    ip3Status = PingStatus.Result(validI3)
                    host1Status = PingStatus.Result(validH1)
                    host2Status = PingStatus.Result(validH2)
                    host3Status = PingStatus.Result(validH3)
                }

                val strengthValue = calculateStrength(ip3Local)
                Napier.d("$TAG strength: $strengthValue for $ip3Local")
                uiCtx {
                    strength = strengthValue.coerceIn(1, STRENGTH_MAX)
                }
            }
        } catch (e: Exception) {
            Napier.e("$TAG err isReachable: ${e.message}", e)
        }
    }

    private suspend fun isReachable(csv: String): Boolean {
        val (warp, pr, se, w64, exit) =
            if (proxiesStatus.isEmpty()) {
                getProxiesStatus(csv)
            } else {
                proxiesStatus
            }
        Napier.d("$TAG ip $csv reachable: $warp, $pr, $se, $w64, $exit")
        Napier.i("$TAG ip $csv reachable: ${warp || pr || se || w64 || exit}")
        return warp || se || w64 || exit
    }

    private suspend fun calculateStrength(csv: String): Int {
        val (wg, amz, win, se, w64) =
            if (proxiesStatus.isEmpty()) {
                getProxiesStatus(csv)
            } else {
                proxiesStatus
            }

        var strength = 0
        if (wg) strength++
        if (amz) strength++
        if (win) strength++
        if (se) strength++
        if (w64) strength++

        Napier.i("$TAG strength: $strength ($wg, $amz, $se, $w64 )")
        return strength
    }

    private suspend fun getProxiesStatus(csv: String): List<Boolean> {
        if (proxiesStatus.isNotEmpty()) return proxiesStatus

        val warp = VpnController.isProxyReachable(Backend.RpnWin, csv)
        val amz = VpnController.isProxyReachable(Backend.RpnWin, csv)
        val win = VpnController.isProxyReachable(Backend.RpnWin, csv)
        val se = VpnController.isProxyReachable(Backend.RpnSE, csv)
        val w64 = VpnController.isProxyReachable(Backend.Rpn64, csv)
        Napier.d("$TAG proxies reachable: $warp, $amz $win, $se, $w64")
        return proxiesStatus.apply {
            clear()
            add(warp)
            add(amz)
            add(win)
            add(se)
            add(w64)
        }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private sealed class PingStatus {
        data object Idle : PingStatus()
        data object Loading : PingStatus()
        data class Result(val success: Boolean) : PingStatus()
    }

    @Composable
    private fun stringResourceCompat(id: Int, vararg args: Any): String {
        val context = LocalContext.current
        return if (args.isNotEmpty()) {
            context.getString(id, *args)
        } else {
            context.getString(id)
        }
    }
}
