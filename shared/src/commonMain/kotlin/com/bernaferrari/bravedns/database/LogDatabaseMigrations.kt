/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

private val MIGRATION_13_14 =
    object : Migration(13, 14) {
        override suspend fun migrate(connection: SQLiteConnection) {
            // v14 adds stats-aggregation indices: timeStamp, (uid, timeStamp), IpInfo.asName.
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ConnectionTracker_timeStamp` ON `ConnectionTracker` (`timeStamp`)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ConnectionTracker_uid_timeStamp` ON `ConnectionTracker` (`uid`, `timeStamp`)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_IpInfo_asName` ON `IpInfo` (`asName`)"
            )
        }
    }

private val MIGRATION_14_15 =
    object : Migration(14, 15) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_app_stats` (`date` INTEGER NOT NULL, `uid` INTEGER NOT NULL, `appName` TEXT NOT NULL, `connectionCount` INTEGER NOT NULL, `blockedCount` INTEGER NOT NULL, `downloadBytes` INTEGER NOT NULL, `uploadBytes` INTEGER NOT NULL, `dnsQueryCount` INTEGER NOT NULL, `blockedDnsCount` INTEGER NOT NULL, PRIMARY KEY(`date`, `uid`))"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_daily_app_stats_date` ON `daily_app_stats` (`date`)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_daily_app_stats_date_uid` ON `daily_app_stats` (`date`, `uid`)"
            )

            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_country_stats` (`date` INTEGER NOT NULL, `flag` TEXT NOT NULL, `connectionCount` INTEGER NOT NULL, `dnsQueryCount` INTEGER NOT NULL, PRIMARY KEY(`date`, `flag`))"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_daily_country_stats_date` ON `daily_country_stats` (`date`)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_daily_country_stats_date_flag` ON `daily_country_stats` (`date`, `flag`)"
            )

            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_asn_stats` (`date` INTEGER NOT NULL, `asName` TEXT NOT NULL, `countryCode` TEXT NOT NULL, `connectionCount` INTEGER NOT NULL, `blockedCount` INTEGER NOT NULL, `downloadBytes` INTEGER NOT NULL, `uploadBytes` INTEGER NOT NULL, PRIMARY KEY(`date`, `asName`))"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_daily_asn_stats_date` ON `daily_asn_stats` (`date`)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_daily_asn_stats_date_asName` ON `daily_asn_stats` (`date`, `asName`)"
            )

            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_domain_stats` (`date` INTEGER NOT NULL, `domain` TEXT NOT NULL, `flag` TEXT NOT NULL, `contactedCount` INTEGER NOT NULL, `blockedCount` INTEGER NOT NULL, PRIMARY KEY(`date`, `domain`))"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_daily_domain_stats_date` ON `daily_domain_stats` (`date`)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_daily_domain_stats_date_domain` ON `daily_domain_stats` (`date`, `domain`)"
            )

            backfillDailyAppStats(connection)
            backfillDailyCountryStats(connection)
            backfillDailyAsnStats(connection)
            backfillDailyDomainStats(connection)
        }
    }

private val MIGRATION_15_16 =
    object : Migration(15, 16) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_ip_stats` (`date` INTEGER NOT NULL, `ipAddress` TEXT NOT NULL, `flag` TEXT NOT NULL, `contactedCount` INTEGER NOT NULL, `blockedCount` INTEGER NOT NULL, `downloadBytes` INTEGER NOT NULL, `uploadBytes` INTEGER NOT NULL, PRIMARY KEY(`date`, `ipAddress`))"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_daily_ip_stats_date` ON `daily_ip_stats` (`date`)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_daily_ip_stats_date_ipAddress` ON `daily_ip_stats` (`date`, `ipAddress`)"
            )
            backfillDailyIpStats(connection)
        }
    }

private suspend fun backfillDailyAppStats(connection: SQLiteConnection) {
    connection.execSQL(
        """
        INSERT INTO daily_app_stats (
            date, uid, appName, connectionCount, blockedCount,
            downloadBytes, uploadBytes, dnsQueryCount, blockedDnsCount
        )
        SELECT
            CAST(timeStamp / 86400000 AS INTEGER),
            uid,
            appName,
            SUM(CASE WHEN isBlocked = 0 AND dnsQuery IS NOT NULL AND dnsQuery != '' THEN 1 ELSE 0 END),
            SUM(CASE WHEN isBlocked = 1 THEN 1 ELSE 0 END),
            SUM(CASE WHEN isBlocked = 0 AND dnsQuery IS NOT NULL AND dnsQuery != '' THEN downloadBytes ELSE 0 END),
            SUM(CASE WHEN isBlocked = 0 AND dnsQuery IS NOT NULL AND dnsQuery != '' THEN uploadBytes ELSE 0 END),
            0,
            0
        FROM ConnectionTracker
        GROUP BY CAST(timeStamp / 86400000 AS INTEGER), uid
        """.trimIndent()
    )
    connection.execSQL(
        """
        INSERT INTO daily_app_stats (
            date, uid, appName, connectionCount, blockedCount,
            downloadBytes, uploadBytes, dnsQueryCount, blockedDnsCount
        )
        SELECT
            CAST(time / 86400000 AS INTEGER),
            uid,
            appName,
            0,
            0,
            0,
            0,
            SUM(CASE WHEN isBlocked = 0 AND status = 'COMPLETE' AND queryStr != '' THEN 1 ELSE 0 END),
            SUM(CASE WHEN isBlocked = 1 THEN 1 ELSE 0 END)
        FROM DNSLogs
        GROUP BY CAST(time / 86400000 AS INTEGER), uid
        ON CONFLICT(date, uid) DO UPDATE SET
            appName = CASE WHEN excluded.appName != '' THEN excluded.appName ELSE daily_app_stats.appName END,
            dnsQueryCount = daily_app_stats.dnsQueryCount + excluded.dnsQueryCount,
            blockedDnsCount = daily_app_stats.blockedDnsCount + excluded.blockedDnsCount
        """.trimIndent()
    )
}

private suspend fun backfillDailyCountryStats(connection: SQLiteConnection) {
    connection.execSQL(
        """
        INSERT INTO daily_country_stats (date, flag, connectionCount, dnsQueryCount)
        SELECT
            CAST(timeStamp / 86400000 AS INTEGER),
            flag,
            SUM(CASE WHEN isBlocked = 0 THEN 1 ELSE 0 END),
            0
        FROM ConnectionTracker
        WHERE isBlocked = 0
        GROUP BY CAST(timeStamp / 86400000 AS INTEGER), flag
        """.trimIndent()
    )
    connection.execSQL(
        """
        INSERT INTO daily_country_stats (date, flag, connectionCount, dnsQueryCount)
        SELECT
            CAST(time / 86400000 AS INTEGER),
            flag,
            0,
            COUNT(*)
        FROM DNSLogs
        WHERE isBlocked = 0 AND status = 'COMPLETE' AND queryStr != ''
        GROUP BY CAST(time / 86400000 AS INTEGER), flag
        ON CONFLICT(date, flag) DO UPDATE SET
            dnsQueryCount = daily_country_stats.dnsQueryCount + excluded.dnsQueryCount
        """.trimIndent()
    )
}

private suspend fun backfillDailyAsnStats(connection: SQLiteConnection) {
    connection.execSQL(
        """
        INSERT INTO daily_asn_stats (
            date, asName, countryCode, connectionCount, blockedCount, downloadBytes, uploadBytes
        )
        SELECT
            CAST(conn.timeStamp / 86400000 AS INTEGER),
            ipInfo.asName,
            ipInfo.countryCode,
            SUM(CASE WHEN conn.isBlocked = 0 THEN 1 ELSE 0 END),
            SUM(CASE WHEN conn.isBlocked = 1 THEN 1 ELSE 0 END),
            SUM(CASE WHEN conn.isBlocked = 0 THEN conn.downloadBytes ELSE 0 END),
            SUM(CASE WHEN conn.isBlocked = 0 THEN conn.uploadBytes ELSE 0 END)
        FROM ConnectionTracker AS conn
        INNER JOIN IpInfo AS ipInfo ON conn.ipAddress = ipInfo.ip
        WHERE ipInfo.asName != ''
        GROUP BY CAST(conn.timeStamp / 86400000 AS INTEGER), ipInfo.asName
        """.trimIndent()
    )
}

private suspend fun backfillDailyDomainStats(connection: SQLiteConnection) {
    connection.execSQL(
        """
        INSERT INTO daily_domain_stats (date, domain, flag, contactedCount, blockedCount)
        SELECT
            CAST(timeStamp / 86400000 AS INTEGER),
            dnsQuery,
            flag,
            SUM(CASE WHEN isBlocked = 0 THEN 1 ELSE 0 END),
            SUM(CASE WHEN isBlocked = 1 AND blockedByRule LIKE 'Rule #2G%' THEN 1 ELSE 0 END)
        FROM ConnectionTracker
        WHERE dnsQuery IS NOT NULL AND dnsQuery != ''
        GROUP BY CAST(timeStamp / 86400000 AS INTEGER), dnsQuery
        """.trimIndent()
    )
    connection.execSQL(
        """
        INSERT INTO daily_domain_stats (date, domain, flag, contactedCount, blockedCount)
        SELECT
            CAST(time / 86400000 AS INTEGER),
            RTRIM(queryStr, '.'),
            flag,
            SUM(CASE WHEN isBlocked = 0 AND status = 'COMPLETE' THEN 1 ELSE 0 END),
            SUM(CASE WHEN isBlocked = 1 THEN 1 ELSE 0 END)
        FROM DNSLogs
        WHERE queryStr != ''
        GROUP BY CAST(time / 86400000 AS INTEGER), RTRIM(queryStr, '.')
        ON CONFLICT(date, domain) DO UPDATE SET
            flag = CASE WHEN excluded.flag != '' THEN excluded.flag ELSE daily_domain_stats.flag END,
            contactedCount = daily_domain_stats.contactedCount + excluded.contactedCount,
            blockedCount = daily_domain_stats.blockedCount + excluded.blockedCount
        """.trimIndent()
    )
}

private suspend fun backfillDailyIpStats(connection: SQLiteConnection) {
    connection.execSQL(
        """
        INSERT INTO daily_ip_stats (
            date, ipAddress, flag, contactedCount, blockedCount, downloadBytes, uploadBytes
        )
        SELECT
            CAST(timeStamp / 86400000 AS INTEGER),
            ipAddress,
            flag,
            SUM(CASE WHEN isBlocked = 0 THEN 1 ELSE 0 END),
            SUM(CASE WHEN isBlocked = 1 THEN 1 ELSE 0 END),
            SUM(CASE WHEN isBlocked = 0 THEN downloadBytes ELSE 0 END),
            SUM(CASE WHEN isBlocked = 0 THEN uploadBytes ELSE 0 END)
        FROM ConnectionTracker
        WHERE ipAddress != ''
        GROUP BY CAST(timeStamp / 86400000 AS INTEGER), ipAddress
        """.trimIndent()
    )
}

internal fun logDatabaseMigrations(): Array<Migration> = arrayOf(MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
