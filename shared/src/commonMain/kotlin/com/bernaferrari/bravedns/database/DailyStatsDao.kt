/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.Dao
import androidx.room3.Query

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

    @Query("DELETE FROM daily_app_stats WHERE date < :fromDay")
    suspend fun purgeAppsBefore(fromDay: Long)

    @Query("DELETE FROM daily_country_stats WHERE date < :fromDay")
    suspend fun purgeCountriesBefore(fromDay: Long)

    @Query("DELETE FROM daily_asn_stats WHERE date < :fromDay")
    suspend fun purgeAsnsBefore(fromDay: Long)

    @Query("DELETE FROM daily_domain_stats WHERE date < :fromDay")
    suspend fun purgeDomainsBefore(fromDay: Long)
}
