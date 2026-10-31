package com.bernaferrari.bravedns.ui.compose.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class HomePauseTest {
    private class Recorder(
        private val tunnel: Boolean = true,
        private val paused: Boolean = false,
        private val remaining: Long = 0,
    ) : HomePauseControls {
        val calls = mutableListOf<String>()
        override fun hasTunnel() = tunnel
        override fun isPaused() = paused
        override fun remainingMs() = remaining
        override fun pause() { calls += "pause" }
        override fun extend(durationMs: Long) { calls += "extend:${minutes(durationMs)}m" }
        override fun shorten(durationMs: Long) { calls += "shorten:${minutes(durationMs)}m" }
        override fun clearTimer() { calls += "clear" }
        override fun startTimer(durationMs: Long) { calls += "timer:${minutes(durationMs)}m" }
        override fun resume() { calls += "resume" }
    }

    private fun pausedFor(minutes: Long) = Recorder(paused = true, remaining = TimeUnit.MINUTES.toMillis(minutes))

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

    @Test fun aTimedPauseMovesByFifteenMinutes() {
        val controls = pausedFor(40)
        assertTrue(adjustPause(HomePauseAdjustment.AddFifteenMinutes, controls))
        assertTrue(adjustPause(HomePauseAdjustment.RemoveFifteenMinutes, controls))
        assertEquals(listOf("extend:15m", "shorten:15m"), controls.calls)
    }

    @Test fun shorteningNeverEndsThePauseBehindTheUsersBack() {
        val controls = pausedFor(15)
        assertFalse(adjustPause(HomePauseAdjustment.RemoveFifteenMinutes, controls))
        assertTrue(controls.calls.isEmpty())
        assertFalse(canShortenPause(TimeUnit.MINUTES.toMillis(15)))
        assertTrue(canShortenPause(TimeUnit.MINUTES.toMillis(15) + 1))
    }

    @Test fun aTimedPauseCanDropItsTimer() {
        val controls = pausedFor(10)
        assertTrue(adjustPause(HomePauseAdjustment.UntilResumed, controls))
        assertEquals(listOf("clear"), controls.calls)
    }

    @Test fun aPauseUntilResumedGetsAFreshTimer() {
        val untilResumed = Recorder(paused = true, remaining = 0)
        assertTrue(adjustPause(HomePauseAdjustment.FifteenMinutes, untilResumed))
        assertTrue(adjustPause(HomePauseAdjustment.OneHour, untilResumed))
        assertEquals(listOf("timer:15m", "timer:60m"), untilResumed.calls)
    }

    @Test fun aPauseUntilResumedHasNoTimerToMove() {
        val untilResumed = Recorder(paused = true, remaining = 0)
        HomePauseAdjustment.entries
            .filter { it != HomePauseAdjustment.FifteenMinutes && it != HomePauseAdjustment.OneHour }
            .forEach { assertFalse(it.name, adjustPause(it, untilResumed)) }
        assertTrue(untilResumed.calls.isEmpty())
    }

    @Test fun adjustingNeverPausesOrResumesTheTunnel() {
        val notPaused = Recorder(paused = false, remaining = TimeUnit.MINUTES.toMillis(30))
        HomePauseAdjustment.entries.forEach { assertFalse(it.name, adjustPause(it, notPaused)) }
        assertTrue(notPaused.calls.isEmpty())
        val paused = pausedFor(30)
        HomePauseAdjustment.entries.forEach { adjustPause(it, paused) }
        assertTrue(paused.calls.none { it == "pause" || it == "resume" })
    }
}

private fun minutes(ms: Long) = TimeUnit.MILLISECONDS.toMinutes(ms)
