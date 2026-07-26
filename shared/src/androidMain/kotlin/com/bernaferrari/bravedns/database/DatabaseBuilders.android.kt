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
        .createFromAsset(AppDatabase.DATABASE_PATH)
        .addCallback(AppDatabase.roomCallback)
        .setDriver(BundledSQLiteDriver())
        .setJournalMode(RoomDatabase.JournalMode.AUTOMATIC)
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .addMigrations(AppDatabase.MIGRATION_2_3)
        .addMigrations(AppDatabase.MIGRATION_3_4)
        .addMigrations(AppDatabase.MIGRATION_4_5)
        .addMigrations(AppDatabase.MIGRATION_5_6)
        .addMigrations(AppDatabase.MIGRATION_6_7)
        .addMigrations(AppDatabase.MIGRATION_7_8)
        .addMigrations(AppDatabase.MIGRATION_8_9)
        .addMigrations(AppDatabase.MIGRATION_9_10)
        .addMigrations(AppDatabase.MIGRATION_10_11)
        .addMigrations(AppDatabase.MIGRATION_11_12)
        .addMigrations(AppDatabase.MIGRATION_12_13)
        .addMigrations(AppDatabase.MIGRATION_13_14)
        .addMigrations(AppDatabase.MIGRATION_14_15)
        .addMigrations(AppDatabase.MIGRATION_15_16)
        .addMigrations(AppDatabase.MIGRATION_16_17)
        .addMigrations(AppDatabase.MIGRATION_17_18)
        .addMigrations(AppDatabase.migration1819())
        .addMigrations(AppDatabase.MIGRATION_19_20)
        .addMigrations(AppDatabase.MIGRATION_20_21)
        .addMigrations(AppDatabase.MIGRATION_21_22)
        .addMigrations(AppDatabase.MIGRATION_22_23)
        .addMigrations(AppDatabase.MIGRATION_23_24)
        .addMigrations(AppDatabase.MIGRATION_24_25)
        .addMigrations(AppDatabase.MIGRATION_25_26)
        .addMigrations(AppDatabase.MIGRATION_26_27)
        .addMigrations(AppDatabase.MIGRATION_27_28)
        .addMigrations(AppDatabase.MIGRATION_28_29)
        .addMigrations(AppDatabase.MIGRATION_29_30)
        .build()

fun buildLogDatabase(
    context: Context,
    rethinkDnsDbPath: String,
    isFreshInstall: Boolean
): LogDatabase {
    LogDatabase.rethinkDnsDbPath = rethinkDnsDbPath
    LogDatabase.isFreshInstall = isFreshInstall
    return Room.databaseBuilder(
        context.applicationContext,
        LogDatabase.LOGS_DATABASE_NAME
    ) { LogDatabaseConstructor.initialize() }
        .setDriver(BundledSQLiteDriver())
        .setJournalMode(RoomDatabase.JournalMode.AUTOMATIC)
        .addCallback(LogDatabase.roomCallback)
        .addMigrations(LogDatabase.MIGRATION_2_3)
        .addMigrations(LogDatabase.MIGRATION_3_4)
        .addMigrations(LogDatabase.MIGRATION_4_5)
        .addMigrations(LogDatabase.MIGRATION_5_6)
        .addMigrations(LogDatabase.MIGRATION_6_7)
        .addMigrations(LogDatabase.MIGRATION_7_8)
        .addMigrations(LogDatabase.Migration_8_9)
        .addMigrations(LogDatabase.Migration_9_10)
        .addMigrations(LogDatabase.MIGRATION_10_11)
        .addMigrations(LogDatabase.MIGRATION_11_12)
        .addMigrations(LogDatabase.MIGRATION_12_13)
        .fallbackToDestructiveMigration()
        .build()
}

fun buildConsoleLogDatabase(context: Context): ConsoleLogDatabase =
    Room.inMemoryDatabaseBuilder(context.applicationContext) {
        ConsoleLogDatabaseConstructor.initialize()
    }
        .setDriver(BundledSQLiteDriver())
        .addMigrations(ConsoleLogDatabase.MIGRATION_1_2)
        .build()
