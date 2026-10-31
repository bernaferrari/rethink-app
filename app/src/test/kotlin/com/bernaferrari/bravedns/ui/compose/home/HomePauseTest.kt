package com.bernaferrari.bravedns.ui.compose.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class HomePauseTest {
    private class Recorder(private val tunnel: Boolean = true) : HomePauseControls {
        val calls = mutableListOf<String>()
        override fun hasTunnel() = tunnel
        override fun pause() { calls += "pause" }
        override fun extend(durationMs: Long) { calls += "extend:${TimeUnit.MILLISECONDS.toMinutes(durationMs)}m" }
        override fun clearTimer() { calls += "clear" }
        override fun resume() { calls += "resume" }
    }

    @Test fun fifteenMinutesIsTheNotificationsPauseUnchanged() {
        val controls = Recorder()
        assertTrue(pauseProtection(HomePauseDuration.FifteenMinutes, controls))
        assertEquals(listOf("pause"), controls.calls)
    }

    @Test fun oneHourExtendsTheDefaultTimerToSixtyMinutes() {
        val controls = Recorder()
        pauseProtection(HomePauseDuration.OneHour, controls)
        assertEquals(listOf("pause", "extend:45m"), controls.calls)
    }

    @Test fun untilResumedStopsTheTimerAfterPausing() {
        val controls = Recorder()
        pauseProtection(HomePauseDuration.UntilResumed, controls)
        assertEquals(listOf("pause", "clear"), controls.calls)
    }

    @Test fun withoutATunnelNothingPauses() {
        val controls = Recorder(tunnel = false)
        assertFalse(pauseProtection(HomePauseDuration.OneHour, controls))
        assertTrue(controls.calls.isEmpty())
    }

    @Test fun remainingTimeReadsAsMinutesOrHours() {
        assertEquals("14:32", formatPauseRemaining(TimeUnit.MINUTES.toMillis(14) + 32_000))
        assertEquals("0:05", formatPauseRemaining(5_000))
        assertEquals("1:02:05", formatPauseRemaining(TimeUnit.HOURS.toMillis(1) + 125_000))
        assertEquals("0:00", formatPauseRemaining(-1))
    }
}
