/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.ui.compose.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celzero.bravedns.R
import com.celzero.bravedns.service.BraveVPNService
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.PauseTimer.PAUSE_VPN_EXTRA_MILLIS
import com.celzero.bravedns.service.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Android state and VPN-action adapter for [RethinkPauseScreen]. */
@Composable
fun PauseScreen(onFinish: () -> Unit) {
    val scope = rememberCoroutineScope()
    val connectionState by VpnController.connectionStatus.collectAsStateWithLifecycle()
    val pauseMillis by (VpnController.pauseCountDownFlow()
        ?: kotlinx.coroutines.flow.MutableStateFlow(0L)).collectAsStateWithLifecycle()
    val appList by FirewallManager.appListFlow().collectAsStateWithLifecycle()

    val timerText = remember(pauseMillis) {
        val seconds = (TimeUnit.MILLISECONDS.toSeconds(pauseMillis) % 60).toString().padStart(2, '0')
        val minutes = (TimeUnit.MILLISECONDS.toMinutes(pauseMillis) % 60).toString().padStart(2, '0')
        val hours = TimeUnit.MILLISECONDS.toHours(pauseMillis).toString().padStart(2, '0')
        "$hours:$minutes:$seconds"
    }
    val blockedAppCount = remember(appList) {
        appList.count { it.connectionStatus != FirewallManager.ConnectionStatus.ALLOW.id }.toString()
    }
    var autoAdjustment by remember { mutableStateOf<RethinkPauseAdjustment?>(null) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        if (!VpnController.isAppPaused()) onFinish()
    }
    LaunchedEffect(connectionState) {
        if (connectionState != BraveVPNService.State.PAUSED) onFinish()
    }

    fun beginAutoAdjustment(adjustment: RethinkPauseAdjustment) {
        autoAdjustment = adjustment
        if (longPressJob?.isActive == true) return
        longPressJob = scope.launch(Dispatchers.Main) {
            while (autoAdjustment != null) {
                when (autoAdjustment) {
                    RethinkPauseAdjustment.Increase -> VpnController.increasePauseDuration(PAUSE_VPN_EXTRA_MILLIS)
                    RethinkPauseAdjustment.Decrease -> VpnController.decreasePauseDuration(PAUSE_VPN_EXTRA_MILLIS)
                    null -> Unit
                }
                delay(200)
            }
        }
    }

    fun resume() {
        VpnController.resumeApp()
        onFinish()
    }

    RethinkPauseScreen(
        state = RethinkPauseState(
            timerText = timerText,
            timerDescription = stringResource(R.string.pause_desc, blockedAppCount),
        ),
        strings = RethinkPauseStrings(
            title = stringResource(R.string.pause_text),
            pauseLabel = stringResource(R.string.pause_text),
            resume = stringResource(R.string.notif_dialog_pause_dialog_positive),
        ),
        onDecrease = { VpnController.decreasePauseDuration(PAUSE_VPN_EXTRA_MILLIS) },
        onIncrease = { VpnController.increasePauseDuration(PAUSE_VPN_EXTRA_MILLIS) },
        onResume = ::resume,
        onAutoAdjustmentStart = ::beginAutoAdjustment,
        onAutoAdjustmentStop = { autoAdjustment = null },
    )
}
