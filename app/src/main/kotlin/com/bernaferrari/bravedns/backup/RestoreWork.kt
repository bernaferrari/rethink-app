package com.bernaferrari.bravedns.backup

import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.TimeUnit

/** Queue and observe the accepted restore, including when KEEP ignores a duplicate request. */
internal object RestoreWork {
    const val NAME = RestoreAgent.TAG

    fun enqueue(manager: WorkManager, uri: Uri) {
        val request = OneTimeWorkRequestBuilder<RestoreAgent>()
            .setInputData(Data.Builder().putString(BackupHelper.DATA_BUILDER_RESTORE_URI, uri.toString()).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(RestoreAgent.TAG)
            .build()
        manager.enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(manager: WorkManager): Flow<WorkInfo?> = manager.getWorkInfosForUniqueWorkFlow(NAME)
        .mapLatest { work ->
            val accepted = work.firstOrNull { !it.state.isFinished } ?: work.firstOrNull()
            // Cancellation is published before non-cancellable installation/rollback finishes.
            if (accepted?.state?.isFinished == true) BackupRestoreFileGate.awaitIdle()
            accepted
        }
        .distinctUntilChanged()
}
