/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.withWriteTransaction
import com.bernaferrari.bravedns.data.ConnectionSummary
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private object StatsMaterializerBridge : KoinComponent {
    @kotlin.concurrent.Volatile
    private var instance: StatsMaterializer? = null

    fun database(): LogDatabase = get()

    fun impl(): StatsMaterializer {
        instance?.let { return it }
        val db: LogDatabase = get()
        return StatsMaterializer(db.dailyStatsDAO(), db.ipInfoDao()).also { instance = it }
    }
}

suspend fun materializeStatsInserted(batch: List<*>) = StatsMaterializerBridge.impl().onInserted(batch)

suspend fun persistLogBatch(batch: List<*>, persist: suspend () -> Unit) {
    if (batch.firstOrNull() !is ConnectionTracker && batch.firstOrNull() !is DnsLog) {
        persist()
        return
    }
    StatsMaterializerBridge.database().withWriteTransaction {
        persist()
        StatsMaterializerBridge.impl().onInserted(batch)
    }
}

suspend fun persistConnectionSummaries(batch: List<*>, persist: suspend () -> Unit) {
    val summaries = batch.filterIsInstance<ConnectionSummary>()
    if (summaries.isEmpty()) {
        persist()
        return
    }
    val database = StatsMaterializerBridge.database()
    database.withWriteTransaction {
        val ids = summaries.map { it.connId }.distinct()
        val before = database.connectionTrackerDAO().connectionsByIds(ids)
        persist()
        val after = database.connectionTrackerDAO().connectionsByIds(ids)
        StatsMaterializerBridge.impl().onConnectionsChanged(before, after)
    }
}

suspend fun materializeStatsPurgeBefore(cutoffMillis: Long) = StatsMaterializerBridge.impl().purgeBefore(cutoffMillis)
