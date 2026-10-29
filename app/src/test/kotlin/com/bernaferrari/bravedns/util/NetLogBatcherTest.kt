package com.bernaferrari.bravedns.util

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetLogBatcherTest {
    @Before fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After fun teardown() = unmockkAll()

    @Test fun `close writes partial insert and summary buffers in order`() = runTest {
        val events = mutableListOf<String>()
        val batcher = NetLogBatcher<Int, Int>(
            "test", UnconfinedTestDispatcher(testScheduler), 40,
            { events += "insert:$it" }, { events += "update:$it" },
        )
        batcher.begin(backgroundScope)
        batcher.add(1)
        batcher.update(2)
        batcher.close()
        batcher.close()
        assertEquals(listOf("insert:[1]", "update:[2]"), events)
    }

    @Test fun `close waits for an in flight batch before writing its tail`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<Int>()
        val batcher = NetLogBatcher<Int, Nothing>(
            "test", UnconfinedTestDispatcher(testScheduler), 2,
            { batch ->
                if (batch == listOf(1, 2)) {
                    entered.complete(Unit)
                    release.await()
                }
                events += batch
            },
        )
        batcher.begin(backgroundScope)
        batcher.add(1)
        batcher.add(2)
        entered.await()
        batcher.add(3)
        val closing = launch { batcher.close() }
        release.complete(Unit)
        closing.join()
        assertEquals(listOf(1, 2, 3), events)
    }
}
