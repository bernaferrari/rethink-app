/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

private val MIGRATION_13_14 =
    object : Migration(13, 14) {
        override suspend fun migrate(connection: SQLiteConnection) {
            // v14 adds stats-aggregation indices: timeStamp, (uid, timeStamp), IpInfo.asName.
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ConnectionTracker_timeStamp` ON `ConnectionTracker` (`timeStamp`)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ConnectionTracker_uid_timeStamp` ON `ConnectionTracker` (`uid`, `timeStamp`)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_IpInfo_asName` ON `IpInfo` (`asName`)"
            )
        }
    }

internal fun logDatabaseMigrations(): Array<Migration> = arrayOf(MIGRATION_13_14)
