package com.bernaferrari.bravedns.diagnostics

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.service.PcapSessionController
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.util.PcapMode
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
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
    private lateinit var scheduler: WorkManager
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
        scheduler = mockk<WorkManager>(relaxed = true)
        val operation = mockk<Operation>()
        val future = mockk<ListenableFuture<Operation.State.SUCCESS>>()
        every { future.isDone } returns true
        every { future.get() } returns Operation.SUCCESS
        every { operation.result } returns future
        every { scheduler.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) } returns operation
        manager = PcapCaptureManager(context, config, preferences) { scheduler }
    }
    private fun connectSession(native: suspend (String) -> Boolean, identity: () -> Any?) {
        var path = ""
        val session = PcapSessionController(native, { newMode, newPath ->
            mode = newMode
            path = newPath
            if (newMode == PcapMode.NONE.id) captureId = ""
        }, { path }, identity, { File(it).length() == 0L })
        coEvery { VpnController.startPcapCapture(any(), any()) } coAnswers {
            session.start(firstArg(), secondArg())
        }
        coEvery { VpnController.stopPcapCapture() } coAnswers { session.stop() }
    }

    @After fun cleanup() { unmockkAll() }

    @Test fun nativeStartFailureDoesNotPublishActiveStateOrKeepAnEmptyFile() = runTest {
        coEvery { VpnController.startPcapCapture(any(), any()) } returns false
        assertFalse(manager.start(PcapMode.EXTERNAL_FILE))
        assertFalse(manager.active)
        assertEquals("", captureId)
        assertTrue(manager.files().isEmpty())
    }
    @Test fun aFileBeingStartedCannotBeExposedBeforeNativeAcknowledgement() = runTest {
        val acknowledge = CompletableDeferred<Boolean>()
        coEvery { VpnController.startPcapCapture(any(), any()) } coAnswers {
            val result = acknowledge.await()
            if (result) mode = firstArg()
            result
        }
        val start = async { manager.start(PcapMode.EXTERNAL_FILE) }
        runCurrent()
        val file = manager.files().single()
        assertFalse(manager.active)
        assertFalse(manager.canExpose(file))
        acknowledge.complete(true)
        assertTrue(start.await())
        assertTrue(manager.active)
        assertFalse(manager.canExpose(file))
    }
    @Test fun schedulerFailureCleansTheCreatedFileAndGeneration() = runTest {
        val broken = PcapCaptureManager(context, mockk(relaxed = true), preferences) { error("scheduler unavailable") }
        try { broken.start(PcapMode.EXTERNAL_FILE); fail("Expected scheduler failure") }
        catch (_: IllegalStateException) { }
        assertEquals("", captureId)
        assertTrue(broken.files().isEmpty())
        assertFalse(broken.active)
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
    @Test fun staleBackendSuccessKeepsFileAndDeadlineUntilDisableAcknowledges() = runTest {
        var backend = Any()
        var allowStop = false
        connectSession({ target ->
            if (target.isEmpty()) allowStop else {
                File(target).writeText("native capture header")
                backend = Any()
                true
            }
        }, { backend })

        assertFalse(manager.start(PcapMode.EXTERNAL_FILE))
        val file = manager.files().single()
        assertTrue(file.exists())
        assertTrue(manager.active)
        assertTrue(captureId.isNotEmpty())
        assertFalse(manager.canExpose(file))
        assertFalse(manager.delete(file))
        verify(exactly = 1) { scheduler.enqueueUniqueWork(PcapCaptureManager.STOP_WORK, any(), any<OneTimeWorkRequest>()) }
        verify(exactly = 0) { scheduler.cancelUniqueWork(any()) }

        allowStop = true
        assertTrue(manager.stop())
        assertFalse(manager.active)
        assertTrue(file.exists())
        assertTrue(manager.canExpose(file))
    }

    @Test fun cancellationAfterNativeSideEffectKeepsOwnershipWhenDisableFails() = runTest {
        var allowStop = false
        val entered = CompletableDeferred<Unit>()
        connectSession({ target ->
            if (target.isEmpty()) allowStop else {
                File(target).writeText("native capture header")
                entered.complete(Unit)
                awaitCancellation()
            }
        }, { Unit })
        val starting = async { manager.start(PcapMode.EXTERNAL_FILE) }
        entered.await()
        starting.cancel()
        starting.join()

        val file = manager.files().single()
        assertTrue(starting.isCancelled)
        assertTrue(manager.active)
        assertTrue(captureId.isNotEmpty())
        assertTrue(file.exists())
        assertFalse(manager.canExpose(file))
        verify(exactly = 0) { scheduler.cancelUniqueWork(any()) }
        allowStop = true
        assertTrue(manager.stop())
        assertTrue(file.exists())
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
