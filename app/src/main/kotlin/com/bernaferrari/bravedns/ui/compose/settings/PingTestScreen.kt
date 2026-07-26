/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.settings

import Logger
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.VpnController
import com.celzero.firestack.backend.Backend
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

/** Android reachability bridge for the target-neutral connectivity-check renderer. */
@Composable
fun PingTestScreen(onBackClick: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    var ip1 by remember { mutableStateOf(PING_IP1) }
    var ip2 by remember { mutableStateOf(PING_IP2) }
    var ip3 by remember { mutableStateOf(PING_IP3) }
    var host1 by remember { mutableStateOf(PING_HOST1) }
    var host2 by remember { mutableStateOf(PING_HOST2) }
    var host3 by remember { mutableStateOf(PING_HOST3) }
    var ip1Status by remember { mutableStateOf<RethinkPingStatus>(RethinkPingStatus.Idle) }
    var ip2Status by remember { mutableStateOf<RethinkPingStatus>(RethinkPingStatus.Idle) }
    var ip3Status by remember { mutableStateOf<RethinkPingStatus>(RethinkPingStatus.Idle) }
    var host1Status by remember { mutableStateOf<RethinkPingStatus>(RethinkPingStatus.Idle) }
    var host2Status by remember { mutableStateOf<RethinkPingStatus>(RethinkPingStatus.Idle) }
    var host3Status by remember { mutableStateOf<RethinkPingStatus>(RethinkPingStatus.Idle) }
    var strength by remember { mutableStateOf<Int?>(null) }
    val proxiesStatus = remember { mutableListOf<Boolean>() }

    suspend fun getProxiesStatus(csv: String): List<Boolean> {
        if (proxiesStatus.isNotEmpty()) return proxiesStatus
        return withContext(Dispatchers.IO) {
            listOf(
                VpnController.isProxyReachable(Backend.RpnWin, csv),
                VpnController.isProxyReachable(Backend.RpnWin, csv),
                VpnController.isProxyReachable(Backend.RpnWin, csv),
                VpnController.isRpnReachable(csv),
                VpnController.isProxyReachable(Backend.Rpn64, csv),
            ).also { probes -> proxiesStatus.addAll(probes) }
        }
    }

    suspend fun isReachable(csv: String): Boolean = getProxiesStatus(csv).any { it }

    suspend fun calculateStrength(csv: String): Int = getProxiesStatus(csv).count { it }

    fun performPing() = scope.launch {
        try {
            proxiesStatus.clear()
            ip1Status = RethinkPingStatus.Loading; ip2Status = RethinkPingStatus.Loading; ip3Status = RethinkPingStatus.Loading
            host1Status = RethinkPingStatus.Loading; host2Status = RethinkPingStatus.Loading; host3Status = RethinkPingStatus.Loading
            strength = null
            val ip1Reachable = isReachable(ip1)
            val ip2Reachable = isReachable(ip2)
            val ip3Reachable = isReachable(ip3)
            val host1Reachable = isReachable(host1)
            val host2Reachable = isReachable(host2)
            val host3Reachable = isReachable(host3)
            ip1Status = RethinkPingStatus.Result(ip1Reachable); ip2Status = RethinkPingStatus.Result(ip2Reachable); ip3Status = RethinkPingStatus.Result(ip3Reachable)
            host1Status = RethinkPingStatus.Result(host1Reachable); host2Status = RethinkPingStatus.Result(host2Reachable); host3Status = RethinkPingStatus.Result(host3Reachable)
            strength = calculateStrength(ip3).coerceIn(1, STRENGTH_MAX)
        } catch (error: Exception) {
            Logger.e(TAG, "reachability test failed: ${error.message}", error)
            ip1Status = RethinkPingStatus.Result(false); ip2Status = RethinkPingStatus.Result(false); ip3Status = RethinkPingStatus.Result(false)
            host1Status = RethinkPingStatus.Result(false); host2Status = RethinkPingStatus.Result(false); host3Status = RethinkPingStatus.Result(false)
        }
    }
    RethinkPingTestScreen(
        ipChecks = listOf(RethinkPingCheck("ip1", ip1, false, ip1Status), RethinkPingCheck("ip2", ip2, false, ip2Status), RethinkPingCheck("ip3", ip3, true, ip3Status)),
        hostChecks = listOf(RethinkPingCheck("host1", host1, false, host1Status), RethinkPingCheck("host2", host2, false, host2Status), RethinkPingCheck("host3", host3, true, host3Status)),
        strength = strength,
        maxStrength = STRENGTH_MAX,
        vpnActive = VpnController.hasTunnel(),
        strings = RethinkPingTestStrings(
            title = stringResource(R.string.settings_connectivity_checks), subtitle = stringResource(R.string.settings_connectivity_checks_desc),
            noVpnTitle = stringResource(R.string.vpn_not_active_dialog_title), noVpnDescription = stringResource(R.string.vpn_not_active_dialog_desc), dismiss = stringResource(R.string.lbl_dismiss),
            ipSection = stringResource(R.string.ping_ip_port_title), hostSection = stringResource(R.string.ping_host_port_title),
            test = stringResource(R.string.lbl_test), strength = stringResource(R.string.ping_strength_title),
            strengthValue = { value, max -> stringResource(R.string.two_argument, value.toString(), max.toString()) },
        ),
        onValueChange = { id, value -> if (id == "ip3") ip3 = value else if (id == "host3") host3 = value },
        onTest = ::performPing,
        onBackClick = onBackClick,
    )
}
