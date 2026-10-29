package com.bernaferrari.bravedns.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PauseCountdownTest {
    @Test fun `replacing a pause leaves one countdown and one expiry`() = runTest {
        var expirations = 0
        val timer = PauseCountdown(backgroundScope, { testScheduler.currentTime }, { expirations++ })
        timer.start(4_000)
        runCurrent()
        advanceTimeBy(500)
        timer.stop()
        timer.start(4_000)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1_000L, timer.state.value)
        assertEquals(0, expirations)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0L, timer.state.value)
        assertEquals(1, expirations)
    }

    @Test fun `duration changes publish immediately and stopped timer cannot expire`() = runTest {
        var expirations = 0
        val timer = PauseCountdown(backgroundScope, { testScheduler.currentTime }, { expirations++ })
        timer.start(60_000)
        timer.adjust(60_000)
        assertEquals(120_000L, timer.state.value)
        timer.adjust(-60_000)
        assertEquals(60_000L, timer.state.value)
        timer.stop()
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(0, expirations)
        assertEquals(0L, timer.state.value)
    }
}
