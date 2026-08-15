/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.Dao
import androidx.room3.Query
import com.bernaferrari.bravedns.data.AppConnection
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStatsDao {

    @Query(
        """
        INSERT INTO daily_app_stats (
            date, uid, appName, connectionCount, blockedCount,
            downloadBytes, uploadBytes, dnsQueryCount, blockedDnsCount
        ) VALUES (
            :date, :uid, :appName, :connectionCount, :blockedCount,
            :downloadBytes, :uploadBytes, :dnsQueryCount, :blockedDnsCount
        )
        ON CONFLICT(date, uid) DO UPDATE SET
            appName = CASE WHEN excluded.appName != '' THEN excluded.appName ELSE appName END,
            connectionCount = connectionCount + excluded.connectionCount,
            blockedCount = blockedCount + excluded.blockedCount,
            downloadBytes = downloadBytes + excluded.downloadBytes,
            uploadBytes = uploadBytes + excluded.uploadBytes,
            dnsQueryCount = dnsQueryCount + excluded.dnsQueryCount,
            blockedDnsCount = blockedDnsCount + excluded.blockedDnsCount
        """
    )
    suspend fun upsertApp(
        date: Long,
        uid: Int,
        appName: String,
        connectionCount: Int,
        blockedCount: Int,
        downloadBytes: Long,
        uploadBytes: Long,
        dnsQueryCount: Int,
        blockedDnsCount: Int,
    )

    @Query(
        """
        INSERT INTO daily_country_stats (date, flag, connectionCount, dnsQueryCount)
        VALUES (:date, :flag, :connectionCount, :dnsQueryCount)
        ON CONFLICT(date, flag) DO UPDATE SET
            connectionCount = connectionCount + excluded.connectionCount,
            dnsQueryCount = dnsQueryCount + excluded.dnsQueryCount
        """
    )
    suspend fun upsertCountry(date: Long, flag: String, connectionCount: Int, dnsQueryCount: Int)

    @Query(
        """
        INSERT INTO daily_asn_stats (
            date, asName, countryCode, connectionCount, blockedCount, downloadBytes, uploadBytes
        ) VALUES (
            :date, :asName, :countryCode, :connectionCount, :blockedCount, :downloadBytes, :uploadBytes
        )
        ON CONFLICT(date, asName) DO UPDATE SET
            countryCode = CASE WHEN excluded.countryCode != '' THEN excluded.countryCode ELSE countryCode END,
            connectionCount = connectionCount + excluded.connectionCount,
            blockedCount = blockedCount + excluded.blockedCount,
            downloadBytes = downloadBytes + excluded.downloadBytes,
            uploadBytes = uploadBytes + excluded.uploadBytes
        """
    )
    suspend fun upsertAsn(
        date: Long,
        asName: String,
        countryCode: String,
        connectionCount: Int,
        blockedCount: Int,
        downloadBytes: Long,
        uploadBytes: Long,
    )

    @Query(
        """
        INSERT INTO daily_domain_stats (date, domain, flag, contactedCount, blockedCount)
        VALUES (:date, :domain, :flag, :contactedCount, :blockedCount)
        ON CONFLICT(date, domain) DO UPDATE SET
            flag = CASE WHEN excluded.flag != '' THEN excluded.flag ELSE flag END,
            contactedCount = contactedCount + excluded.contactedCount,
            blockedCount = blockedCount + excluded.blockedCount
        """
    )
    suspend fun upsertDomain(
        date: Long,
        domain: String,
        flag: String,
        contactedCount: Int,
        blockedCount: Int,
    )

    @Query(
        """
        INSERT INTO daily_ip_stats (
            date, ipAddress, flag, contactedCount, blockedCount, downloadBytes, uploadBytes
        ) VALUES (
            :date, :ipAddress, :flag, :contactedCount, :blockedCount, :downloadBytes, :uploadBytes
        )
        ON CONFLICT(date, ipAddress) DO UPDATE SET
            flag = CASE WHEN excluded.flag != '' THEN excluded.flag ELSE flag END,
            contactedCount = contactedCount + excluded.contactedCount,
            blockedCount = blockedCount + excluded.blockedCount,
            downloadBytes = downloadBytes + excluded.downloadBytes,
            uploadBytes = uploadBytes + excluded.uploadBytes
        """
    )
    suspend fun upsertIp(
        date: Long,
        ipAddress: String,
        flag: String,
        contactedCount: Int,
        blockedCount: Int,
        downloadBytes: Long,
        uploadBytes: Long,
    )

    @Query("DELETE FROM daily_app_stats WHERE date < :fromDay")
    suspend fun purgeAppsBefore(fromDay: Long)

    @Query("DELETE FROM daily_country_stats WHERE date < :fromDay")
    suspend fun purgeCountriesBefore(fromDay: Long)

    @Query("DELETE FROM daily_asn_stats WHERE date < :fromDay")
    suspend fun purgeAsnsBefore(fromDay: Long)

    @Query("DELETE FROM daily_domain_stats WHERE date < :fromDay")
    suspend fun purgeDomainsBefore(fromDay: Long)

    @Query("DELETE FROM daily_ip_stats WHERE date < :fromDay")
    suspend fun purgeIpsBefore(fromDay: Long)

    @Query(
        """
            SELECT 0 AS uid,
              ipAddress AS ipAddress,
              0 AS port,
              SUM(contactedCount) AS count,
              MAX(flag) AS flag,
              0 AS blocked,
              '' AS appOrDnsName,
              SUM(downloadBytes) AS downloadBytes,
              SUM(uploadBytes) AS uploadBytes,
              SUM(downloadBytes + uploadBytes) AS totalBytes
            FROM daily_ip_stats
            WHERE date >= (:to / 86400000)
            GROUP BY ipAddress
            HAVING SUM(contactedCount) > 0
            ORDER BY count DESC
            LIMIT 7
        """
    )
    fun getMostContactedIpsDaily(to: Long): Flow<List<AppConnection>>

    @Query(
        """
            SELECT 0 AS uid,
              ipAddress AS ipAddress,
              0 AS port,
              SUM(blockedCount) AS count,
              MAX(flag) AS flag,
              1 AS blocked,
              '' AS appOrDnsName,
              0 AS downloadBytes,
              0 AS uploadBytes,
              0 AS totalBytes
            FROM daily_ip_stats
            WHERE date >= (:to / 86400000)
            GROUP BY ipAddress
            HAVING SUM(blockedCount) > 0
            ORDER BY count DESC
            LIMIT 7
        """
    )
    fun getMostBlockedIpsDaily(to: Long): Flow<List<AppConnection>>

    // Daily materialized reads. [to] is the same window-start millis the live queries take;
    // rows with date >= epoch-day(to) are included. Swap these in from SummaryStatisticsViewModel.

    @Query(
        """
            SELECT uid AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(connectionCount + dnsQueryCount) AS count,
              '' AS flag,
              0 AS blocked,
              MAX(appName) AS appOrDnsName,
              SUM(downloadBytes) AS downloadBytes,
              SUM(uploadBytes) AS uploadBytes,
              SUM(downloadBytes + uploadBytes) AS totalBytes
            FROM daily_app_stats
            WHERE date >= (:to / 86400000)
            GROUP BY uid
            ORDER BY totalBytes DESC
            LIMIT 7
        """
    )
    fun getMostAllowedAppsDaily(to: Long): Flow<List<AppConnection>>

    @Query(
        """
            SELECT uid AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(blockedCount + blockedDnsCount) AS count,
              '' AS flag,
              1 AS blocked,
              MAX(appName) AS appOrDnsName,
              0 AS downloadBytes,
              0 AS uploadBytes,
              0 AS totalBytes
            FROM daily_app_stats
            WHERE date >= (:to / 86400000)
            GROUP BY uid
            HAVING SUM(blockedCount + blockedDnsCount) > 0
            ORDER BY count DESC
            LIMIT 7
        """
    )
    fun getMostBlockedAppsDaily(to: Long): Flow<List<AppConnection>>

    @Query(
        """
            SELECT -1 AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(connectionCount) AS count,
              0 AS blocked,
              MAX(countryCode) AS flag,
              asName AS appOrDnsName,
              SUM(downloadBytes) AS downloadBytes,
              SUM(uploadBytes) AS uploadBytes,
              SUM(downloadBytes + uploadBytes) AS totalBytes
            FROM daily_asn_stats
            WHERE date >= (:to / 86400000)
            GROUP BY asName
            HAVING SUM(connectionCount) > 0
            ORDER BY count DESC
            LIMIT 7
        """
    )
    fun getMostConnectedASNDaily(to: Long): Flow<List<AppConnection>>

    @Query(
        """
            SELECT -1 AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(blockedCount) AS count,
              1 AS blocked,
              MAX(countryCode) AS flag,
              asName AS appOrDnsName,
              0 AS downloadBytes,
              0 AS uploadBytes,
              0 AS totalBytes
            FROM daily_asn_stats
            WHERE date >= (:to / 86400000)
            GROUP BY asName
            HAVING SUM(blockedCount) > 0
            ORDER BY count DESC
            LIMIT 7
        """
    )
    fun getMostBlockedASNDaily(to: Long): Flow<List<AppConnection>>

    @Query(
        """
            SELECT 0 AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(blockedCount) AS count,
              MAX(flag) AS flag,
              1 AS blocked,
              domain AS appOrDnsName,
              0 AS downloadBytes,
              0 AS uploadBytes,
              0 AS totalBytes
            FROM daily_domain_stats
            WHERE date >= (:to / 86400000)
            GROUP BY domain
            HAVING SUM(blockedCount) > 0
            ORDER BY count DESC
            LIMIT 7
        """
    )
    fun getMostBlockedDomainsDaily(to: Long): Flow<List<AppConnection>>

    @Query(
        """
            SELECT 0 AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(contactedCount) AS count,
              MAX(flag) AS flag,
              0 AS blocked,
              domain AS appOrDnsName,
              0 AS downloadBytes,
              0 AS uploadBytes,
              0 AS totalBytes
            FROM daily_domain_stats
            WHERE date >= (:to / 86400000)
            GROUP BY domain
            HAVING SUM(contactedCount) > 0
            ORDER BY count DESC
            LIMIT 7
        """
    )
    fun getMostContactedDomainsDaily(to: Long): Flow<List<AppConnection>>

    @Query(
        """
            SELECT 0 AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(connectionCount + dnsQueryCount) AS count,
              flag,
              0 AS blocked,
              '' AS appOrDnsName,
              0 AS downloadBytes,
              0 AS uploadBytes,
              0 AS totalBytes
            FROM daily_country_stats
            WHERE date >= (:to / 86400000)
            GROUP BY flag
            ORDER BY count DESC
            LIMIT 7
        """
    )
    fun getMostContactedCountriesDaily(to: Long): Flow<List<AppConnection>>
}
