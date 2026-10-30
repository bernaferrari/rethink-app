/*
 * Copyright 2022 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bernaferrari.bravedns.scheduler

import Logger
import Logger.LOG_TAG_SCHEDULER
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bernaferrari.bravedns.database.RefreshDatabase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.util.LogRetention

class PurgeConnectionLogs(val context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters), KoinComponent {

    private val refreshDatabase by inject<RefreshDatabase>()
    private val eventDao by inject<com.bernaferrari.bravedns.database.EventDao>()
    private val persistentState by inject<PersistentState>()

    override suspend fun doWork(): Result {
        val retention = LogRetention.fromHours(persistentState.logRetentionHours)
        val date = System.currentTimeMillis() - retention.durationMillis
        Logger.i(LOG_TAG_SCHEDULER, "Purging logs older than ${retention.hours} hours")
        refreshDatabase.purgeConnectionLogs(date)
        eventDao.deleteOlderThan(date)

        return Result.success()
    }
}
