package com.bernaferrari.bravedns.diagnostics

import android.content.Context
import androidx.work.*
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.util.PcapMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.TimeUnit

/** Owns capture files; active captures must be closed by native code before files are exposed. */
class PcapCaptureManager(
    private val context: Context,
    private val appConfig: AppConfig,
    private val persistentState: PersistentState,
) {
    private val mutex = Mutex()
    private val directory get() = File(context.filesDir, "pcap").apply { mkdirs() }
    val active: Boolean get() = persistentState.pcapMode != PcapMode.NONE.id

    fun files(): List<File> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "pcap" && it.canonicalFile.parentFile == directory.canonicalFile }
        .sortedByDescending { it.lastModified() }

    suspend fun start(mode: PcapMode): Boolean = mutex.withLock {
        if (active || !VpnController.hasTunnel() || mode == PcapMode.NONE) return@withLock false
        val path = if (mode == PcapMode.EXTERNAL_FILE) {
            File.createTempFile("capture-", ".pcap", directory).absolutePath
        } else PcapMode.ENABLE_PCAP_LOGCAT
        val captureId = java.util.UUID.randomUUID().toString()
        persistentState.pcapCaptureId = captureId
        persistentState.pcapStartedAt = System.currentTimeMillis()
        appConfig.setPcap(mode.id, path)
        val request = OneTimeWorkRequestBuilder<PcapStopWorker>()
            .setInputData(workDataOf(CAPTURE_ID to captureId))
            .setInitialDelay(MAX_CAPTURE_HOURS, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(STOP_WORK, ExistingWorkPolicy.REPLACE, request)
        true
    }

    suspend fun stop(expectedCaptureId: String? = null): Boolean = mutex.withLock {
        if (expectedCaptureId != null && expectedCaptureId != persistentState.pcapCaptureId) return@withLock true
        if (active && !VpnController.stopPcapCapture()) return@withLock false
        appConfig.setPcap(PcapMode.NONE.id)
        persistentState.pcapStartedAt = 0L
        persistentState.pcapCaptureId = ""
        true
    }

    fun canExpose(file: File): Boolean = !active &&
        file.canonicalFile.parentFile == directory.canonicalFile && file.isFile && file.extension == "pcap"

    fun delete(file: File): Boolean = canExpose(file) && file.delete()

    companion object {
        const val MAX_CAPTURE_HOURS = 3L
        const val CAPTURE_ID = "capture_id"
        const val STOP_WORK = "StopPacketCapture"
    }
}
