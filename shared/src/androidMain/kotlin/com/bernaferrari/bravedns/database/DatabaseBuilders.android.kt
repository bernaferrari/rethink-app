/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.SQLiteDriver

fun buildAppDatabase(
    context: Context,
    driver: SQLiteDriver = BundledSQLiteDriver(),
    callback: RoomDatabase.Callback? = null,
): AppDatabase =
    Room.databaseBuilder(
        context.applicationContext,
        AppDatabase.DATABASE_NAME
    ) { AppDatabaseConstructor.initialize() }
        .setDriver(driver)
        .setJournalMode(RoomDatabase.JournalMode.AUTOMATIC)
        .apply { if (callback != null) addCallback(callback) }
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
