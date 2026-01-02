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
package com.celzero.bravedns.ui.compose.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
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

@Composable
fun PauseScreen(
    onFinish: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var timerText by remember { mutableStateOf("00:00:00") }
    var timerDesc by remember { mutableStateOf("") }
    var autoOp by remember { mutableStateOf(AutoOp.NONE) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(lifecycleOwner) {
        val connectionObserver = androidx.lifecycle.Observer<BraveVPNService.State?> { state ->
            if (state != BraveVPNService.State.PAUSED) {
                onFinish()
            }
        }

        val timerObserver = androidx.lifecycle.Observer<Long> { millis ->
            val ss = (TimeUnit.MILLISECONDS.toSeconds(millis) % 60).toString().padStart(2, '0')
            val mm = (TimeUnit.MILLISECONDS.toMinutes(millis) % 60).toString().padStart(2, '0')
            val hh = TimeUnit.MILLISECONDS.toHours(millis).toString().padStart(2, '0')
            timerText = "$hh:$mm:$ss"
        }

        val appListObserver = androidx.lifecycle.Observer<Collection<com.celzero.bravedns.database.AppInfo>> { list ->
            val blockedList = list.filter { a -> a.connectionStatus != FirewallManager.ConnectionStatus.ALLOW.id }
            timerDesc = blockedList.count().toString()
        }

        VpnController.connectionStatus.observe(lifecycleOwner, connectionObserver)
        VpnController.getPauseCountDownObserver()?.observe(lifecycleOwner, timerObserver)
        FirewallManager.getApplistObserver().observe(lifecycleOwner, appListObserver)

        // Check if app is not paused
        if (!VpnController.isAppPaused()) {
            onFinish()
        }

        onDispose {
            VpnController.connectionStatus.removeObserver(connectionObserver)
            VpnController.getPauseCountDownObserver()?.removeObserver(timerObserver)
            FirewallManager.getApplistObserver().removeObserver(appListObserver)
            longPressJob?.cancel()
        }
    }

    fun handleLongPress() {
        if (longPressJob?.isActive == true) return

        longPressJob = scope.launch(Dispatchers.Main) {
            while (autoOp != AutoOp.NONE) {
                when (autoOp) {
                    AutoOp.INCREASE -> {
                        delay(200)
                        VpnController.increasePauseDuration(PAUSE_VPN_EXTRA_MILLIS)
                    }
                    AutoOp.DECREASE -> {
                        delay(200)
                        VpnController.decreasePauseDuration(PAUSE_VPN_EXTRA_MILLIS)
                    }
                    else -> {}
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 15.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.app_name_small_case),
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.25.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.pause_title_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(50.dp))

        Text(
            text = stringResource(R.string.pause_text),
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = timerText,
            fontSize = 75.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlIcon(
                icon = R.drawable.ic_minus,
                size = 48.dp,
                onClick = { VpnController.decreasePauseDuration(PAUSE_VPN_EXTRA_MILLIS) },
                onLongClick = {
                    autoOp = AutoOp.DECREASE
                    handleLongPress()
                },
                onRelease = { autoOp = AutoOp.NONE }
            )
            Spacer(modifier = Modifier.width(20.dp))
            ControlIcon(
                icon = R.drawable.ic_stop,
                size = 80.dp,
                onClick = {
                    VpnController.resumeApp()
                    onFinish()
                },
                onLongClick = {},
                onRelease = {}
            )
            Spacer(modifier = Modifier.width(20.dp))
            ControlIcon(
                icon = R.drawable.ic_plus,
                size = 48.dp,
                onClick = { VpnController.increasePauseDuration(PAUSE_VPN_EXTRA_MILLIS) },
                onLongClick = {
                    autoOp = AutoOp.INCREASE
                    handleLongPress()
                },
                onRelease = { autoOp = AutoOp.NONE }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(R.string.pause_desc, timerDesc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        )
    }
}

@Composable
private fun ControlIcon(
    icon: Int,
    size: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRelease: () -> Unit
) {
    Image(
        painter = painterResource(id = icon),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                    onPress = {
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            }
    )
}

private enum class AutoOp {
    INCREASE,
    DECREASE,
    NONE
}
