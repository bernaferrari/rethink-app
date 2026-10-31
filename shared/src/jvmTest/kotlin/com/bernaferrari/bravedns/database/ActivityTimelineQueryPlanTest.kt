package com.bernaferrari.bravedns.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The merged Activity timeline must stay cheap on a large log: SQLite merges the connection and
 * lookup arms along their time indices instead of sorting both tables for every page. Run against
 * the exported v1 schema, so a dropped or renamed index fails here rather than on a phone.
 */
class ActivityTimelineQueryPlanTest {
    @Test
    fun everyAppsTimelineMergesTheTwoTimeIndicesWithoutSorting() = withLogSchema { db ->
        val plan = db.plan(ACTIVITY_TIMELINE_QUERY)
        assertFalse(plan.any { "TEMP B-TREE" in it }, plan.joinToString("\n"))
        assertTrue(plan.any { "index_ConnectionTracker_timeStamp" in it }, plan.joinToString("\n"))
        assertTrue(plan.any { "index_DNSLogs_time" in it }, plan.joinToString("\n"))
    }

    @Test
    fun oneAppsTimelineWalksTheUidTimeIndices() = withLogSchema { db ->
        val plan = db.plan(ACTIVITY_TIMELINE_FOR_UID_QUERY)
        assertFalse(plan.any { "TEMP B-TREE" in it }, plan.joinToString("\n"))
        assertTrue(plan.any { "index_ConnectionTracker_uid_timeStamp" in it }, plan.joinToString("\n"))
        assertTrue(plan.any { "index_DNSLogs_uid_time" in it }, plan.joinToString("\n"))
    }

    private fun SQLiteConnection.plan(query: String): List<String> {
        // Room binds these; for the plan any value of the right type will do.
        val sql = query
            .replace("(:rules)", "('rule')")
            .replace(":connections", "1").replace(":lookups", "1").replace(":verdict", "-1")
            .replace(":anyRule", "0").replace(":search", "''").replace(":uid", "10001")
        return prepare("EXPLAIN QUERY PLAN $sql").use { statement ->
            buildList { while (statement.step()) add(statement.getText(3)) }
        }
    }

    private fun withLogSchema(test: (SQLiteConnection) -> Unit) {
        val schema = Json.parseToJsonElement(File(SCHEMA).readText()).jsonObject.getValue("database").jsonObject
        BundledSQLiteDriver().open(":memory:").use { db ->
            for (entity in schema.getValue("entities").jsonArray.map { it.jsonObject }) {
                val table = entity.getValue("tableName").jsonPrimitive.content
                db.execute(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                for (index in entity["indices"]?.jsonArray.orEmpty()) {
                    db.execute(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            test(db)
        }
    }

    private fun SQLiteConnection.execute(sql: String) { prepare(sql).use { it.step() } }

    private companion object {
        const val SCHEMA = "schemas/com.bernaferrari.bravedns.database.LogDatabase/1.json"
    }
}
