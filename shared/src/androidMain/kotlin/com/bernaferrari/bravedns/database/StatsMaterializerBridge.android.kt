/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private object StatsMaterializerBridge : KoinComponent {
    @kotlin.concurrent.Volatile
    private var instance: StatsMaterializer? = null

    fun impl(): StatsMaterializer {
        instance?.let { return it }
        val db: LogDatabase = get()
        return StatsMaterializer(db.dailyStatsDAO(), db.ipInfoDao()).also { instance = it }
    }
}

suspend fun materializeStatsInserted(batch: List<*>) = StatsMaterializerBridge.impl().onInserted(batch)

suspend fun materializeStatsUpdated(batch: List<*>) = StatsMaterializerBridge.impl().onUpdated(batch)

suspend fun materializeStatsPurgeBefore(cutoffMillis: Long) = StatsMaterializerBridge.impl().purgeBefore(cutoffMillis)
