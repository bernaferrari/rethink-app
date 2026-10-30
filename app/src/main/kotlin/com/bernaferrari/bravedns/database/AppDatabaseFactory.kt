package com.bernaferrari.bravedns.database

import android.content.Context
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/** Production factory; driver injection lets Android database contracts run under Robolectric. */
internal fun buildConfiguredAppDatabase(
    context: Context,
    driver: SQLiteDriver = BundledSQLiteDriver(),
): AppDatabase = buildAppDatabase(context, driver, DefaultCatalogInitializer(context))
