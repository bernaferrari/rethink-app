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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val MINUTE_MS = 60_000L
private const val FIFTEEN_MINUTES_MS = 15 * MINUTE_MS
private const val ONE_HOUR_MS = 60 * MINUTE_MS

/**
 * The web demo's pause, with the app's rules: every pause starts paused, a timed one counts down
 * and resumes at zero, −15 min never ends it, and Stop ends any pause.
 */
private class DemoPause {
    var paused by mutableStateOf(false)
        private set
    /** Time left; null while paused means until resumed. */
    var remainingMs by mutableStateOf<Long?>(null)
        private set

    fun pause(choice: RethinkPauseChoice) {
        paused = true
        remainingMs = when (choice) {
            RethinkPauseChoice.FifteenMinutes -> FIFTEEN_MINUTES_MS
            RethinkPauseChoice.OneHour -> ONE_HOUR_MS
            RethinkPauseChoice.UntilResumed -> null
        }
    }

    fun adjust(adjustment: RethinkPauseAdjustment) {
        if (!paused) return
        val left = remainingMs
        remainingMs = when (adjustment) {
            RethinkPauseAdjustment.RemoveFifteenMinutes ->
                if (left != null && left > FIFTEEN_MINUTES_MS) left - FIFTEEN_MINUTES_MS else left
            RethinkPauseAdjustment.AddFifteenMinutes -> left?.plus(FIFTEEN_MINUTES_MS)
            RethinkPauseAdjustment.UntilResumed -> null
            RethinkPauseAdjustment.FifteenMinutes -> FIFTEEN_MINUTES_MS
            RethinkPauseAdjustment.OneHour -> ONE_HOUR_MS
        }
    }

    fun tick(elapsedMs: Long) {
        val left = remainingMs ?: return
        if (left <= elapsedMs) resume() else remainingMs = left - elapsedMs
    }

    fun resume() {
        paused = false
        remainingMs = null
    }
}

/** Home for the web demo: fixture activity, a working Start · Stop and the app's Pause. */
@Composable
fun RethinkDemoHome(
    vpnOn: MutableState<Boolean>,
    strings: RethinkHomeStrings,
    onActivityClick: () -> Unit,
) {
    val active = vpnOn.value
    val pause = remember { DemoPause() }
    // Turning protection off anywhere (a Settings toggle, say) ends the pause too.
    LaunchedEffect(active) { if (!active) pause.resume() }
    val ticking = active && pause.paused && pause.remainingMs != null
    LaunchedEffect(ticking) {
        while (ticking) {
            delay(1_000)
            pause.tick(1_000)
        }
    }
    val paused = active && pause.paused
    val remaining = pause.remainingMs
    RethinkHomeScreen(
        uiState = RethinkHomeUiState(
            isVpnActive = active,
            networkLogsCount = 4_320,
            dnsLogsCount = 1_284,
            protectionStatus = when {
                paused -> strings.paused
                active -> strings.protected
                else -> strings.notActive
            },
            allowedToday = 3_412,
            blockedToday = 908,
            dnsBlockedToday = 512,
            topBlocked = listOf(
                RethinkHomeBlockedApp("chrome", "Chrome", 214),
                RethinkHomeBlockedApp("youtube", "YouTube", 133),
                RethinkHomeBlockedApp("clock", "Clock", 96),
            ),
            dnsName = "Rethink DNS",
            isPaused = paused,
            pausedDetail = if (remaining != null) {
                "${formatPauseRemaining(remaining)} left · Blocked apps stay blocked"
            } else {
                "Until you resume · Blocked apps stay blocked"
            },
            pauseTimed = remaining != null,
            pauseCanShorten = remaining != null && remaining > FIFTEEN_MINUTES_MS,
        ),
        strings = strings,
        onStartStopClick = {
            pause.resume()
            vpnOn.value = !vpnOn.value
        },
        showAtmosphere = false,
        onActivityClick = onActivityClick,
        onPause = pause::pause,
        onResume = pause::resume,
        onAdjustPause = pause::adjust,
    )
}
