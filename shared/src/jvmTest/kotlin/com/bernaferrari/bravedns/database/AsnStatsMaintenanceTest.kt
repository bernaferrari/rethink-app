package com.bernaferrari.bravedns.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.Test
import kotlin.test.assertEquals

class AsnStatsMaintenanceTest {
    @Test
    fun lateMetadataBackfillsCountsAndSummaryReplacesBytes() = withDatabase { db ->
        db.execute("INSERT INTO daily_ip_stats VALUES (10, '192.0.2.1', 1, 0, 0, 0)")
        // The connection was materialized before its ASN metadata arrived.
        db.execute("INSERT INTO IpInfo VALUES ('192.0.2.1', 'Example', 'US')")
        db.rebuild(10, "Example")
        assertEquals(listOf(1L, 0L, 0L, 0L), db.totals(10, "Example"))
        db.execute("UPDATE daily_ip_stats SET downloadBytes = 150, uploadBytes = 30")
        db.rebuild(10, "Example")
        assertEquals(listOf(1L, 0L, 150L, 30L), db.totals(10, "Example"))
        db.rebuild(10, "Example")
        assertEquals(listOf(1L, 0L, 150L, 30L), db.totals(10, "Example"))
    }

    @Test
    fun rebuildingOneAsnIncludesOtherIpsButLeavesOtherDaysUntouched() = withDatabase { db ->
        db.execute("INSERT INTO daily_ip_stats VALUES (10, '192.0.2.1', 1, 0, 100, 20)")
        db.execute("INSERT INTO daily_ip_stats VALUES (10, '192.0.2.2', 2, 1, 200, 40)")
        db.execute("INSERT INTO IpInfo VALUES ('192.0.2.1', 'Example', 'US')")
        db.execute("INSERT INTO IpInfo VALUES ('192.0.2.2', 'Example', 'US')")
        db.execute("INSERT INTO daily_asn_stats VALUES (9, 'Example', 'US', 7, 0, 700, 70)")
        db.rebuild(10, "Example")
        assertEquals(listOf(3L, 1L, 300L, 60L), db.totals(10, "Example"))
        assertEquals(listOf(7L, 0L, 700L, 70L), db.totals(9, "Example"))
    }

    @Test
    fun changedDestinationClearsOldAsnAndPopulatesNewAsn() = withDatabase { db ->
        db.execute("INSERT INTO daily_ip_stats VALUES (10, '192.0.2.1', 0, 0, 0, 0)")
        db.execute("INSERT INTO daily_ip_stats VALUES (10, '192.0.2.2', 1, 0, 200, 40)")
        db.execute("INSERT INTO IpInfo VALUES ('192.0.2.1', 'Old', 'US')")
        db.execute("INSERT INTO IpInfo VALUES ('192.0.2.2', 'New', 'DE')")
        db.execute("INSERT INTO daily_asn_stats VALUES (10, 'Old', 'US', 1, 0, 0, 0)")
        db.rebuild(10, "Old")
        db.rebuild(10, "New")
        assertEquals(listOf(0L, 0L, 0L, 0L), db.totals(10, "Old"))
        assertEquals(listOf(1L, 0L, 200L, 40L), db.totals(10, "New"))
    }

    private fun withDatabase(test: (SQLiteConnection) -> Unit) {
        BundledSQLiteDriver().open(":memory:").use { db ->
            db.execute("CREATE TABLE IpInfo (ip TEXT PRIMARY KEY, asName TEXT NOT NULL, countryCode TEXT NOT NULL)")
            db.execute("""CREATE TABLE daily_ip_stats (date INTEGER, ipAddress TEXT,
                contactedCount INTEGER, blockedCount INTEGER, downloadBytes INTEGER, uploadBytes INTEGER,
                PRIMARY KEY(date, ipAddress))""")
            db.execute("""CREATE TABLE daily_asn_stats (date INTEGER, asName TEXT, countryCode TEXT,
                connectionCount INTEGER, blockedCount INTEGER, downloadBytes INTEGER, uploadBytes INTEGER,
                PRIMARY KEY(date, asName))""")
            test(db)
        }
    }

    private fun SQLiteConnection.execute(sql: String) { prepare(sql).use { it.step() } }

    private fun SQLiteConnection.rebuild(day: Long, name: String) {
        prepare(REPLACE_ASN_DAY_SQL).use {
            it.bindLong(1, day)
            it.bindText(2, name)
            it.step()
        }
    }

    private fun SQLiteConnection.totals(day: Long, name: String): List<Long> =
        prepare("""SELECT connectionCount, blockedCount, downloadBytes, uploadBytes
            FROM daily_asn_stats WHERE date=? AND asName=?""").use {
            it.bindLong(1, day)
            it.bindText(2, name)
            check(it.step())
            (0..3).map(it::getLong)
        }
}
