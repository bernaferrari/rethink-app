/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.Query
import com.bernaferrari.bravedns.data.AppConnection
import kotlinx.coroutines.flow.Flow

@Dao
interface CountryStatsDao {

    @Query(
        """
            SELECT 0 AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(count) AS count,
              flag,
              0 AS blocked,
              '' as appOrDnsName,
              0 as downloadBytes,
              0 as uploadBytes,
              0 as totalBytes
            FROM
              (
                -- From DnsLogs
                SELECT COUNT(id) AS count,
                  flag
                FROM DnsLogs
                WHERE isBlocked = 0
                  AND status = 'COMPLETE'
                  AND queryStr != ''
                  AND time > :to
                GROUP BY flag

                UNION ALL

                -- From ConnectionTracker
                SELECT COUNT(id) AS count,
                  flag
                FROM ConnectionTracker
                WHERE isBlocked = 0
                  AND timeStamp > :to
                GROUP BY flag
              ) AS combined
            GROUP BY flag
            ORDER BY count DESC
            LIMIT 7
        """
    )
    fun getMostContactedCountries(to: Long): Flow<List<AppConnection>>

    @Query(
        """
            SELECT 0 AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(count) AS count,
              flag,
              0 AS blocked,
              '' as appOrDnsName,
              0 as downloadBytes,
              0 as uploadBytes,
              0 as totalBytes
            FROM
              (
                -- From DnsLogs
                SELECT COUNT(id) AS count,
                  flag
                FROM DnsLogs
                WHERE isBlocked = 0
                  AND status = 'COMPLETE'
                  AND queryStr != ''
                  AND time > :to
                GROUP BY flag

                UNION ALL

                -- From ConnectionTracker
                SELECT COUNT(id) AS count,
                  flag
                FROM ConnectionTracker
                WHERE isBlocked = 0
                  AND timeStamp > :to
                GROUP BY flag
              ) AS combined
            GROUP BY flag
            ORDER BY count DESC
        """
    )
    fun getAllContactedCountries(to: Long): PagingSource<Int, AppConnection>

    @Query(
        """
            SELECT uid AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(count) AS count,
              flag AS flag,
              0 AS blocked,
              appOrDnsName AS appOrDnsName,
              Sum(uploadbytes) AS uploadBytes,
              Sum(downloadbytes) AS downloadBytes,
              Sum(uploadBytes + downloadBytes) AS totalBytes
            FROM
              (
                -- From ConnectionTracker
                SELECT uid,
                  appName AS appOrDnsName,
                  COUNT(id) AS count,
                  flag as flag,
                  SUM(uploadBytes) AS uploadBytes,
                  SUM(downloadBytes) AS downloadBytes
                FROM ConnectionTracker
                WHERE timeStamp > :to
                  AND flag = :flag
                  AND isBlocked = 0
                GROUP BY uid

                UNION ALL

                -- From DnsLogs
                SELECT uid,
                  appName AS appOrDnsName,
                  COUNT(id) AS count,
                  flag as flag,
                  0 AS uploadBytes,
                  0 AS downloadBytes
                FROM DnsLogs
                WHERE time > :to
                  AND flag = :flag
                  AND isBlocked = 0
                  AND status = 'COMPLETE'
                  AND queryStr != ''
                GROUP BY uid
              ) AS combined
            GROUP BY uid
            ORDER BY count DESC
        """
    )
    fun getFlagDetails(flag: String, to: Long): PagingSource<Int, AppConnection>

    @Query(
        """
            SELECT uid AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(count) AS count,
              flag AS flag,
              0 AS blocked,
              appOrDnsName AS appOrDnsName,
              Sum(uploadbytes) AS uploadBytes,
              Sum(downloadbytes) AS downloadBytes,
              Sum(uploadBytes + downloadBytes) AS totalBytes
            FROM
              (
                -- From ConnectionTracker
                SELECT uid,
                  appName AS appOrDnsName,
                  COUNT(id) AS count,
                  flag as flag,
                  SUM(uploadBytes) AS uploadBytes,
                  SUM(downloadBytes) AS downloadBytes
                FROM ConnectionTracker
                WHERE timeStamp > :to
                  AND flag = :flag
                  AND isBlocked = 0
                GROUP BY uid

                UNION ALL

                -- From DnsLogs
                SELECT uid,
                  appName AS appOrDnsName,
                  COUNT(id) AS count,
                  flag as flag,
                  0 AS uploadBytes,
                  0 AS downloadBytes
                FROM DnsLogs
                WHERE time > :to
                  AND flag = :flag
                  AND isBlocked = 0
                  AND status = 'COMPLETE'
                  AND queryStr != ''
                GROUP BY uid
              ) AS combined
            GROUP BY uid
            ORDER BY count DESC
            LIMIT :limit
        """
    )
    suspend fun getFlagDetailsLimited(flag: String, to: Long, limit: Int): List<AppConnection>
}
