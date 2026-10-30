package com.bernaferrari.bravedns.diagnostics

import android.content.Context
import androidx.work.*
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.util.PcapMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.TimeUnit

/** Owns capture files; active captures must be closed by native code before files are exposed. */
class PcapCaptureManager(
    private val context: Context,
    private val appConfig: AppConfig,
    private val persistentState: PersistentState,
    private val getScheduler: () -> WorkManager = { WorkManager.getInstance(context) },
) {
    private val mutex = Mutex()
    @Volatile private var transitionInProgress = false
    private val directory get() = File(context.filesDir, "pcap").apply { mkdirs() }
    val active: Boolean get() = persistentState.pcapMode != PcapMode.NONE.id
    val activeFlow = persistentState.preferenceKeyChanges()
        .onStart { emit(PersistentState.PCAP_MODE) }
        .map { active }.distinctUntilChanged()

    fun files(): List<File> = directory.listFiles().orEmpty()
        .filter(::ownedRegularFile)
        .sortedByDescending { it.lastModified() }

    suspend fun start(mode: PcapMode): Boolean = mutex.withLock {
        if (active || !VpnController.hasTunnel() || mode == PcapMode.NONE) return@withLock false
        transitionInProgress = true
        var createdFile: File? = null
        var scheduler: WorkManager? = null
        try {
            val path = if (mode == PcapMode.EXTERNAL_FILE) {
                File.createTempFile("capture-", ".pcap", directory).also { createdFile = it }.absolutePath
            } else PcapMode.ENABLE_PCAP_LOGCAT
            val captureId = java.util.UUID.randomUUID().toString()
            persistentState.pcapCaptureId = captureId
            persistentState.pcapStartedAt = System.currentTimeMillis()
            val request = OneTimeWorkRequestBuilder<PcapStopWorker>()
                .setInputData(workDataOf(CAPTURE_ID to captureId))
                .setInitialDelay(MAX_CAPTURE_HOURS, TimeUnit.HOURS).build()
            scheduler = getScheduler()
            // Persist the deadline before native capture can start.
            scheduler.enqueueUniqueWork(STOP_WORK, ExistingWorkPolicy.REPLACE, request).await()
            VpnController.startPcapCapture(mode.id, path)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                if (VpnController.stopPcapCapture()) appConfig.setPcap(PcapMode.NONE.id)
            }
            throw cancelled
        } finally {
            try {
                if (!active) {
                    persistentState.pcapCaptureId = ""
                    persistentState.pcapStartedAt = 0L
                    createdFile?.delete()
                    scheduler?.cancelUniqueWork(STOP_WORK)
                }
            } finally { transitionInProgress = false }
        }
    }

    suspend fun stop(expectedCaptureId: String? = null): Boolean = mutex.withLock {
        if (expectedCaptureId != null && expectedCaptureId != persistentState.pcapCaptureId) return@withLock true
        transitionInProgress = true
        try {
            if (active && !VpnController.stopPcapCapture()) return@withLock false
            appConfig.setPcap(PcapMode.NONE.id)
            persistentState.pcapStartedAt = 0L
            persistentState.pcapCaptureId = ""
            true
        } finally { transitionInProgress = false }
    }

    private fun ownedRegularFile(file: File): Boolean = file.isFile && file.extension == "pcap" &&
        file.canonicalFile == File(directory.canonicalFile, file.name)

    fun canExpose(file: File): Boolean = !transitionInProgress && !active && ownedRegularFile(file)

    fun delete(file: File): Boolean = canExpose(file) && file.delete()

    companion object {
        const val MAX_CAPTURE_HOURS = 3L
        const val CAPTURE_ID = "capture_id"
        const val STOP_WORK = "StopPacketCapture"
    }
}
