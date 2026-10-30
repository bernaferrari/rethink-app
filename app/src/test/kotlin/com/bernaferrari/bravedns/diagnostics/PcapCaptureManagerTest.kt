package com.bernaferrari.bravedns.diagnostics

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.util.PcapMode
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PcapCaptureManagerTest {
    private lateinit var manager: PcapCaptureManager
    private lateinit var context: Application
    private lateinit var preferences: PersistentState
    private var mode = 0
    private var captureId = ""

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "pcap").deleteRecursively()
        preferences = mockk(relaxed = true)
        every { preferences.pcapMode } answers { mode }
        every { preferences.pcapCaptureId } answers { captureId }
        every { preferences.pcapCaptureId = any() } answers { captureId = firstArg() }
        every { preferences.preferenceKeyChanges() } returns MutableSharedFlow()
        val config = mockk<AppConfig>(relaxed = true)
        every { config.setPcap(any(), any()) } answers { mode = firstArg() }
        mockkObject(VpnController)
        every { VpnController.hasTunnel() } returns true
        coEvery { VpnController.startPcapCapture(any(), any()) } answers { mode = firstArg(); true }
        coEvery { VpnController.stopPcapCapture() } returns true
        val scheduler = mockk<WorkManager>(relaxed = true)
        val operation = mockk<Operation>()
        val future = mockk<ListenableFuture<Operation.State.SUCCESS>>()
        every { future.isDone } returns true
        every { future.get() } returns Operation.SUCCESS
        every { operation.result } returns future
        every { scheduler.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) } returns operation
        manager = PcapCaptureManager(context, config, preferences) { scheduler }
    }
    @After fun cleanup() { unmockkAll() }

    @Test fun nativeStartFailureDoesNotPublishActiveStateOrKeepAnEmptyFile() = runTest {
        coEvery { VpnController.startPcapCapture(any(), any()) } returns false
        assertFalse(manager.start(PcapMode.EXTERNAL_FILE))
        assertFalse(manager.active)
        assertEquals("", captureId)
        assertTrue(manager.files().isEmpty())
    }
    @Test fun staleWorkerCannotStopANewerCapture() = runTest {
        assertTrue(manager.start(PcapMode.EXTERNAL_FILE))
        assertTrue(manager.stop("older-capture"))
        assertTrue(manager.active)
        coVerify(exactly = 0) { VpnController.stopPcapCapture() }
    }
    @Test fun captureIsNotExposedUntilNativeStopAcknowledgesDrain() = runTest {
        assertTrue(manager.start(PcapMode.EXTERNAL_FILE))
        val file = manager.files().single()
        val drain = CompletableDeferred<Boolean>()
        coEvery { VpnController.stopPcapCapture() } coAnswers { drain.await() }
        val stop = async { manager.stop() }
        runCurrent()
        assertFalse(manager.canExpose(file))
        assertFalse(manager.delete(file))
        drain.complete(true)
        assertTrue(stop.await())
        assertTrue(manager.canExpose(file))
    }
    @Test fun foreignFilesAndSymlinkAliasesCannotBeSharedOrDeleted() {
        val directory = File(context.filesDir, "pcap").apply { mkdirs() }
        val closed = File(directory, "closed.pcap").apply { writeText("capture") }
        val foreign = File(context.cacheDir, "foreign.pcap").apply { writeText("private") }
        val alias = File(directory, "alias.pcap")
        Files.createSymbolicLink(alias.toPath(), closed.toPath())
        assertTrue(manager.canExpose(closed))
        assertFalse(manager.canExpose(foreign))
        assertFalse(manager.canExpose(alias))
        assertFalse(manager.delete(alias))
        assertTrue(closed.exists())
    }
}
