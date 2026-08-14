/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

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
        .fallbackToDestructiveMigration()
        .build()
}
