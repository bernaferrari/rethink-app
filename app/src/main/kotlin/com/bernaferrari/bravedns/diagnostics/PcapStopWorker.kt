package com.bernaferrari.bravedns.diagnostics

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PcapStopWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val manager by inject<PcapCaptureManager>()
    override suspend fun doWork(): Result = if (manager.stop(inputData.getString(PcapCaptureManager.CAPTURE_ID).orEmpty())) Result.success() else Result.retry()
}
