/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun buildAppDatabase(context: Context): AppDatabase =
    Room.databaseBuilder(
        context.applicationContext,
        AppDatabase.DATABASE_NAME
    ) { AppDatabaseConstructor.initialize() }
        .setDriver(BundledSQLiteDriver())
        .setJournalMode(RoomDatabase.JournalMode.AUTOMATIC)
        .build()

fun buildLogDatabase(context: Context): LogDatabase =
    Room.databaseBuilder(
        context.applicationContext,
        LogDatabase.LOGS_DATABASE_NAME
    ) { LogDatabaseConstructor.initialize() }
        .setDriver(BundledSQLiteDriver())
        .setJournalMode(RoomDatabase.JournalMode.AUTOMATIC)
        .build()

fun buildConsoleLogDatabase(context: Context): ConsoleLogDatabase =
    Room.inMemoryDatabaseBuilder(context.applicationContext) {
        ConsoleLogDatabaseConstructor.initialize()
    }
        .setDriver(BundledSQLiteDriver())
        // In-memory only; schema is not persisted across process death.
        .fallbackToDestructiveMigration()
        .build()
