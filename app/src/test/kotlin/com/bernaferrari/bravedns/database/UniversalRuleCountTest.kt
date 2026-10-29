package com.bernaferrari.bravedns.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class UniversalRuleCountTest {
    @Test
    fun countersAggregateOnlyPersistedUniversalRuleIds() {
        SQLiteDatabase.create(null).use { database ->
            database.execSQL("CREATE TABLE ConnectionTracker (blockedByRule TEXT NOT NULL)")
            listOf("Rule #3", "Rule #3", "Rule #4", "Http block", "Universal Lockdown", "Rule #1", "Rule #30")
                .forEach { rule -> database.execSQL("INSERT INTO ConnectionTracker VALUES (?)", arrayOf(rule)) }
            val counts = database.rawQuery(UNIVERSAL_RULE_COUNTS_QUERY, null).use { rows ->
                buildMap {
                    while (rows.moveToNext()) {
                        put(rows.getString(rows.getColumnIndexOrThrow("blockedByRule")), rows.getInt(rows.getColumnIndexOrThrow("count")))
                    }
                }
            }
            assertEquals(mapOf("Rule #3" to 2, "Rule #4" to 1, "Http block" to 1, "Universal Lockdown" to 1), counts)
        }
    }
}
