/*
 * Copyright 2024 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import androidx.sqlite.SQLiteConnection

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ConsoleLogDatabaseConstructor : RoomDatabaseConstructor<ConsoleLogDatabase> {
    override fun initialize(): ConsoleLogDatabase
}

@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
@ConstructedBy(ConsoleLogDatabaseConstructor::class)
@Database(entities = [ConsoleLog::class], version = 2, exportSchema = false)
abstract class ConsoleLogDatabase : RoomDatabase() {
    companion object {
        internal val MIGRATION_1_2 = object : androidx.room3.migration.Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE ConsoleLog ADD COLUMN level INTEGER DEFAULT 3")
            }
        }
    }

    abstract fun consoleLogDAO(): ConsoleLogDAO

    fun consoleLogRepository() = ConsoleLogRepository(consoleLogDAO())
}
