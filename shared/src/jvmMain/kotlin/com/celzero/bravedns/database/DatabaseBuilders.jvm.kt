/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.celzero.bravedns.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

fun buildAppDatabaseJvm(dbFile: File? = null): AppDatabase {
    val builder =
        if (dbFile != null) {
            Room.databaseBuilder(name = dbFile.absolutePath, factory = AppDatabaseConstructor::initialize)
        } else {
            Room.inMemoryDatabaseBuilder(factory = AppDatabaseConstructor::initialize)
        }
    return builder
        .setDriver(BundledSQLiteDriver())
        .addCallback(AppDatabase.roomCallback)
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
}
