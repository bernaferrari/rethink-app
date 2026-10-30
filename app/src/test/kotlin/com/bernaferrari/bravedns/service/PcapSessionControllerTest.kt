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
        val controller = PcapSessionController({ false }, { _, _ -> error("must not persist failed start") }, { "" }, { Unit }, { true })
        assertFalse(controller.start(PcapMode.EXTERNAL_FILE.id, "/capture.pcap"))
    }
    @Test fun `stop awaits native drain before persisting disabled`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val drained = CompletableDeferred<Unit>()
        var path = "/capture.pcap"
        val controller = PcapSessionController({ entered.complete(Unit); drained.await(); true }, { _, value -> path = value }, { path }, { Unit }, { true })
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
        }, { _, value -> path = value }, { path }, { Unit }, { true })
        val stop = async { controller.stop() }
        entered.await()
        val delayed = async { controller.refresh() }
        runCurrent()
        drained.complete(Unit)
        assertTrue(stop.await())
        delayed.await()
        assertEquals(listOf(""), applied)
    }

    @Test fun `start and delayed preference refresh install capture once`() = runTest {
        val applied = mutableListOf<String>()
        var path = ""
        val controller = PcapSessionController({ applied.add(it); true }, { _, value -> path = value }, { path }, { Unit }, { true })
        assertTrue(controller.start(PcapMode.EXTERNAL_FILE.id, "/capture.pcap"))
        controller.refresh()
        controller.refresh()
        assertEquals(listOf("/capture.pcap"), applied)
    }

    @Test fun `fresh backend initializes an empty persisted file once`() = runTest {
        val applied = mutableListOf<String>()
        val controller = PcapSessionController({ applied.add(it); true }, { _, _ -> error("selection is unchanged") }, { "/capture.pcap" }, { Unit }, { true })
        controller.refresh()
        controller.refresh()
        assertEquals(listOf("/capture.pcap"), applied)
    }

    @Test fun `failed native transition invalidates cache and preference refresh retries`() = runTest {
        val applied = mutableListOf<String>()
        var path = ""
        var succeeds = true
        val controller = PcapSessionController({ applied.add(it); succeeds }, { _, value -> path = value }, { path }, { Unit }, { true })
        assertTrue(controller.start(PcapMode.EXTERNAL_FILE.id, "/capture.pcap"))
        succeeds = false
        assertFalse(controller.stop())
        assertEquals("/capture.pcap", path)
        succeeds = true
        controller.refresh()
        controller.refresh()
        assertEquals(listOf("/capture.pcap", "", "/capture.pcap"), applied)
    }

    @Test fun `backend replacement during native apply cannot persist stale success`() = runTest {
        var backend = Any()
        var path = ""
        val controller = PcapSessionController({ backend = Any(); true }, { _, value -> path = value }, { path }, { backend }, { true })
        assertFalse(controller.start(PcapMode.EXTERNAL_FILE.id, "/capture.pcap"))
        assertEquals("", path)
    }

    @Test fun `replacement backend stops nonempty file without appending another header`() = runTest {
        var backend = Any()
        var empty = true
        var path = ""
        val applied = mutableListOf<String>()
        val controller = PcapSessionController({ applied.add(it); empty = false; true }, { _, value -> path = value }, { path }, { backend }, { empty })
        assertTrue(controller.start(PcapMode.EXTERNAL_FILE.id, "/capture.pcap"))
        controller.refresh()
        backend = Any()
        controller.refresh()
        controller.refresh()
        assertEquals(listOf("/capture.pcap", ""), applied)
        assertEquals("", path)
    }

    @Test fun `failed disable after replacement retries before publishing stopped state`() = runTest {
        var backend = Any()
        var empty = true
        var succeeds = true
        var path = ""
        val applied = mutableListOf<String>()
        val controller = PcapSessionController({ applied.add(it); empty = false; succeeds }, { _, value -> path = value }, { path }, { backend }, { empty })
        assertTrue(controller.start(PcapMode.EXTERNAL_FILE.id, "/capture.pcap"))
        backend = Any()
        succeeds = false
        controller.refresh()
        assertEquals("/capture.pcap", path)
        succeeds = true
        controller.refresh()
        assertEquals("", path)
        assertEquals(listOf("/capture.pcap", "", ""), applied)
    }

    @Test fun `replacement backend can resume logcat without file headers`() = runTest {
        var backend = Any()
        var path = ""
        val applied = mutableListOf<String>()
        val controller = PcapSessionController({ applied.add(it); true }, { _, value -> path = value }, { path }, { backend }, { error("logcat is not a file") })
        assertTrue(controller.start(PcapMode.LOGCAT.id, ""))
        backend = Any()
        controller.refresh()
        controller.refresh()
        assertEquals(listOf("0", "0"), applied)
    }
}
