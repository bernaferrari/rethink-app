/*
 * Copyright 2021 RethinkDNS and its authors
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
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.service.BraveVPNService
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.PauseTimer.PAUSE_VPN_EXTRA_MILLIS
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants.Companion.INIT_TIME_MS
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

class PauseActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    @Volatile var j: CompletableJob? = null

    enum class AutoOp {
        INCREASE,
        DECREASE,
        NONE
    }

    @Volatile var autoOp = AutoOp.NONE
    var lastStopActivityInvokeTime: Long = INIT_TIME_MS

    private var timerText by mutableStateOf("00:00:00")
    private var timerDesc by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        timerDesc = getString(R.string.pause_desc, "0")

        setContent {
            RethinkTheme {
                PauseContent()
            }
        }

        initView()
        observeAppState()
        observeTimer()
        openHomeScreenAndFinishIfNeeded()
    }

    private fun openHomeScreenAndFinishIfNeeded() {
        if (!VpnController.isAppPaused()) {
            openHomeScreenAndFinish()
        }
    }

    private fun initView() {
        FirewallManager.getApplistObserver().observe(this) {
            val blockedList =
                it.filter { a -> a.connectionStatus != FirewallManager.ConnectionStatus.ALLOW.id }
            timerDesc = getString(R.string.pause_desc, blockedList.count().toString())
        }
    }

    private fun observeAppState() {
        VpnController.connectionStatus.observe(this) {
            if (it != BraveVPNService.State.PAUSED) {
                openHomeScreenAndFinish()
            }
        }
    }

    private fun observeTimer() {
        VpnController.getPauseCountDownObserver()?.observe(this) {
            val ss = (TimeUnit.MILLISECONDS.toSeconds(it) % 60).toString().padStart(2, '0')
            val mm = (TimeUnit.MILLISECONDS.toMinutes(it) % 60).toString().padStart(2, '0')
            val hh = TimeUnit.MILLISECONDS.toHours(it).toString().padStart(2, '0')
            timerText = getString(R.string.three_argument_colon, hh, mm, ss)
        }
    }

    private fun decreaseTimer() {
        VpnController.decreasePauseDuration(PAUSE_VPN_EXTRA_MILLIS)
    }

    private fun increaseTimer() {
        VpnController.increasePauseDuration(PAUSE_VPN_EXTRA_MILLIS)
    }

    private fun openHomeScreenAndFinish() {
        if (
            SystemClock.elapsedRealtime() - lastStopActivityInvokeTime <
                TimeUnit.SECONDS.toMillis(1L)
        ) {
            return
        }

        lastStopActivityInvokeTime = SystemClock.elapsedRealtime()
        val intent = Intent(this, AppLockActivity::class.java)
        intent.setPackage(this.packageName)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun handleLongPress() {
        if (j?.isActive == true) {
            return
        }

        j = Job()
        lifecycleScope.launch(j!! + Dispatchers.Main) {
            while (autoOp != AutoOp.NONE) {
                when (autoOp) {
                    AutoOp.INCREASE -> {
                        delay(200)
                        increaseTimer()
                    }
                    AutoOp.DECREASE -> {
                        delay(200)
                        decreaseTimer()
                    }
                    else -> {
                        // no-op
                    }
                }
            }
            j?.cancel()
        }
    }

    @Composable
    private fun PauseContent() {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 15.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = getString(R.string.app_name_small_case),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.25.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = getString(R.string.pause_title_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(50.dp))

            Text(
                text = getString(R.string.pause_text),
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
                    onClick = { decreaseTimer() },
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
                        openHomeScreenAndFinish()
                    },
                    onLongClick = {},
                    onRelease = {}
                )
                Spacer(modifier = Modifier.width(20.dp))
                ControlIcon(
                    icon = R.drawable.ic_plus,
                    size = 48.dp,
                    onClick = { increaseTimer() },
                    onLongClick = {
                        autoOp = AutoOp.INCREASE
                        handleLongPress()
                    },
                    onRelease = { autoOp = AutoOp.NONE }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = timerDesc,
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
            modifier =
                Modifier.size(size)
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
}
