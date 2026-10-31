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
 * The pause controls Home and the notification's Pause actions use: `VpnController.pauseApp()`,
 * plus the timer adjustments the old Pause screen made ([PauseTimer]'s add, subtract and stop).
 */
internal interface HomePauseControls {
    fun hasTunnel(): Boolean
    fun isPaused(): Boolean
    /** Time left on the pause's countdown; 0 while paused means it lasts until resumed. */
    fun remainingMs(): Long
    /** Pauses with the default 15-minute timer. */
    fun pause()
    fun extend(durationMs: Long)
    fun shorten(durationMs: Long)
    /** Stops the countdown so the pause lasts until [resume]. */
    fun clearTimer()
    /** Restarts the countdown at [durationMs]; expiry resumes, as for any pause. */
    fun startTimer(durationMs: Long)
    fun resume()
}

internal object VpnControllerPauseControls : HomePauseControls {
    override fun hasTunnel() = VpnController.hasTunnel()
    override fun isPaused() = VpnController.isAppPaused()
    override fun remainingMs() = PauseTimer.pauseCountDownFlow().value
    override fun pause() = VpnController.pauseApp()
    override fun extend(durationMs: Long) = VpnController.increasePauseDuration(durationMs)
    override fun shorten(durationMs: Long) = VpnController.decreasePauseDuration(durationMs)
    override fun clearTimer() = PauseTimer.stop()
    override fun startTimer(durationMs: Long) = PauseTimer.start(durationMs)
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

/** What the paused hero can change: move a timed pause by 15 minutes, or switch timed ⇄ until resumed. */
enum class HomePauseAdjustment { RemoveFifteenMinutes, AddFifteenMinutes, UntilResumed, FifteenMinutes, OneHour }

/**
 * Changes the pause in progress. Only the timer moves: the tunnel stays paused (blocked apps stay
 * blocked) until the countdown ends or the user resumes. A timed pause moves by 15 minutes (never
 * shortened to nothing, which would resume behind the user's back) or drops its timer; a pause
 * until resumed gets a fresh timer. Returns false when there was nothing to change.
 */
internal fun adjustPause(adjustment: HomePauseAdjustment, controls: HomePauseControls): Boolean {
    if (!controls.isPaused()) return false
    val timed = controls.remainingMs() > 0
    when (adjustment) {
        HomePauseAdjustment.RemoveFifteenMinutes -> {
            if (!canShortenPause(controls.remainingMs())) return false
            controls.shorten(FIFTEEN_MINUTES_MS)
        }
        HomePauseAdjustment.AddFifteenMinutes -> {
            if (!timed) return false
            controls.extend(FIFTEEN_MINUTES_MS)
        }
        HomePauseAdjustment.UntilResumed -> {
            if (!timed) return false
            controls.clearTimer()
        }
        HomePauseAdjustment.FifteenMinutes -> controls.startTimer(FIFTEEN_MINUTES_MS)
        HomePauseAdjustment.OneHour -> controls.startTimer(ONE_HOUR_MS)
    }
    return true
}

/** "−15 min" only while more than 15 minutes are left, so it can't end the pause outright. */
internal fun canShortenPause(remainingMs: Long) = remainingMs > FIFTEEN_MINUTES_MS

private val FIFTEEN_MINUTES_MS = TimeUnit.MINUTES.toMillis(15)
private val ONE_HOUR_MS = TimeUnit.HOURS.toMillis(1)
