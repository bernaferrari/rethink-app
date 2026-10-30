package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.util.PcapMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PcapSessionControllerTest {
    @Test fun `failed native start never persists active capture`() = runTest {
        val controller = PcapSessionController({ false }, { _, _ -> error("must not persist failed start") }, { "" })
        assertFalse(controller.start(PcapMode.EXTERNAL_FILE.id, "/capture.pcap"))
    }
    @Test fun `stop awaits native drain before persisting disabled`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val drained = CompletableDeferred<Unit>()
        var path = "/capture.pcap"
        val controller = PcapSessionController({ entered.complete(Unit); drained.await(); true }, { _, value -> path = value }, { path })
        val stop = async { controller.stop() }
        entered.await()
        assertEquals("/capture.pcap", path)
        assertFalse(stop.isCompleted)
        drained.complete(Unit)
        assertTrue(stop.await())
        assertEquals("", path)
    }
    @Test fun `delayed observer reads stopped state inside owner lock`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val drained = CompletableDeferred<Unit>()
        val applied = mutableListOf<String>()
        var path = "/capture.pcap"
        val controller = PcapSessionController({ target ->
            applied.add(target)
            if (applied.size == 1) { entered.complete(Unit); drained.await() }
            true
        }, { _, value -> path = value }, { path })
        val stop = async { controller.stop() }
        entered.await()
        val delayed = async { controller.refresh() }
        runCurrent()
        drained.complete(Unit)
        assertTrue(stop.await())
        delayed.await()
        assertEquals(listOf("", ""), applied)
    }
}
