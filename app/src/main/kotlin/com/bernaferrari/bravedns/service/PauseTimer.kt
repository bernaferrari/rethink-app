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
package com.bernaferrari.bravedns.service

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

object PauseTimer {
    val DEFAULT_PAUSE_TIME_MS = TimeUnit.MINUTES.toMillis(15)
    val PAUSE_VPN_EXTRA_MILLIS = TimeUnit.MINUTES.toMillis(1)

    private val countdown = PauseCountdown(
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        SystemClock::elapsedRealtime,
    ) {
        if (VpnController.isAppPaused()) VpnController.resumeApp()
    }

    fun start(durationMs: Long) { countdown.start(durationMs) }
    fun stop() { countdown.stop() }
    fun addDuration(duration: Long) { countdown.adjust(duration) }
    fun subtractDuration(duration: Long) { countdown.adjust(-duration) }
    fun pauseCountDownFlow(): StateFlow<Long> = countdown.state
}
