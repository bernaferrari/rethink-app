/*
 * Copyright 2026 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.ui.compose.home

import com.bernaferrari.bravedns.service.PauseTimer
import com.bernaferrari.bravedns.service.VpnController
import java.util.concurrent.TimeUnit

/**
 * The pause controls Home uses: the same calls as the notification's Pause action
 * (`NotificationActionReceiver.pauseApp`), plus the timer adjustments the old Pause screen made.
 */
internal interface HomePauseControls {
    fun hasTunnel(): Boolean
    /** Pauses with the default 15-minute timer. */
    fun pause()
    fun extend(durationMs: Long)
    /** Stops the countdown so the pause lasts until [resume]. */
    fun clearTimer()
    fun resume()
}

internal object VpnControllerPauseControls : HomePauseControls {
    override fun hasTunnel() = VpnController.hasTunnel()
    override fun pause() = VpnController.pauseApp()
    override fun extend(durationMs: Long) = VpnController.increasePauseDuration(durationMs)
    override fun clearTimer() = PauseTimer.stop()
    override fun resume() = VpnController.resumeApp()
}

/**
 * Pauses for [duration]. Every choice starts the existing 15-minute pause; an hour extends that
 * timer and "until I resume" stops it, so expiry and resume behave exactly as they always have.
 * Returns false (and does nothing) without a running tunnel, as the notification action does.
 */
internal fun pauseProtection(duration: HomePauseDuration, controls: HomePauseControls): Boolean {
    if (!controls.hasTunnel()) return false
    controls.pause()
    when (duration) {
        HomePauseDuration.FifteenMinutes -> Unit
        HomePauseDuration.OneHour -> controls.extend(ONE_HOUR_MS - PauseTimer.DEFAULT_PAUSE_TIME_MS)
        HomePauseDuration.UntilResumed -> controls.clearTimer()
    }
    return true
}

enum class HomePauseDuration { FifteenMinutes, OneHour, UntilResumed }

private val ONE_HOUR_MS = TimeUnit.HOURS.toMillis(1)

/** "14:32" under an hour, "1:02:05" above; what the paused hero shows beside "left". */
internal fun formatPauseRemaining(millis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0))
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$minutes:$ss"
}
