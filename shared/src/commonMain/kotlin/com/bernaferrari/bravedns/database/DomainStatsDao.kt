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
interface DomainStatsDao {

    @Query(
        """
          SELECT 0 AS uid,
            '' AS ipAddress,
            0 AS port,
            SUM(count) AS count,
            flag,
            1 AS blocked,
            appOrDnsName,
            0 as downloadBytes,
            0 as uploadBytes,
            0 as totalBytes
          FROM
            (
              -- From DnsLogs
              SELECT RTRIM(queryStr, '.') AS appOrDnsName,
                COUNT(id) AS count,
                flag
              FROM DnsLogs
              WHERE isBlocked = 1
                AND time > :to
                AND queryStr != ''
              GROUP BY RTRIM(queryStr, '.')

              UNION ALL

                -- From ConnectionTracker
              SELECT dnsQuery AS appOrDnsName,
                COUNT(id) AS count,
                flag
              FROM ConnectionTracker
              WHERE isBlocked = 1
                AND timeStamp > :to
                AND blockedByRule LIKE 'Rule #2G%'
              GROUP BY dnsQuery
            ) AS combined
          GROUP BY appOrDnsName
          ORDER BY count DESC
          LIMIT 7
        """
    )
    fun getMostBlockedDomains(to: Long): Flow<List<AppConnection>>

    @Query(
        """
            SELECT 0 AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(count) AS count,
              flag,
              1 AS blocked,
              appOrDnsName,
              0 as downloadBytes,
              0 as uploadBytes,
              0 as totalBytes
            FROM
              (
                -- From DnsLogs
                SELECT RTRIM(queryStr, '.') AS appOrDnsName,
                  COUNT(id) AS count,
                  flag
                FROM DnsLogs
                WHERE isBlocked = 1
                  AND time > :to
                  AND queryStr != ''
                GROUP BY RTRIM(queryStr, '.')

                UNION ALL

                -- From ConnectionTracker
                SELECT dnsQuery AS appOrDnsName,
                  COUNT(id) AS count,
                  flag
                FROM ConnectionTracker
                WHERE isBlocked = 1
                  AND timeStamp > :to
                  AND blockedByRule LIKE 'Rule #2G%'
                GROUP BY
                  dnsQuery
              ) AS combined
            GROUP BY appOrDnsName
            ORDER BY count DESC
        """
    )
    fun getAllBlockedDomains(to: Long): PagingSource<Int, AppConnection>

    @Query(
        """
            SELECT 0 AS uid,
                '' AS ipAddress,
                0 AS port,
                SUM(count) AS count,
                flag,
                0 AS blocked,
                appOrDnsName,
                0 as downloadBytes,
                0 as uploadBytes,
                0 as totalBytes
            FROM (
                -- From DnsLogs
                SELECT RTRIM(queryStr, '.') AS appOrDnsName,
                    COUNT(id) AS count,
                    flag
                FROM DnsLogs
                WHERE isBlocked = 0
                      AND status = 'COMPLETE'
                      AND queryStr != ''
                      AND time > :to
                GROUP BY RTRIM(queryStr, '.')

                UNION ALL

                -- From ConnectionTracker
                SELECT dnsQuery AS appOrDnsName,
                    COUNT(id) AS count,
                    flag
                FROM ConnectionTracker
                WHERE isBlocked = 0
                      AND timeStamp > :to
                      AND dnsQuery != ''
                GROUP BY dnsQuery
            ) AS combined
            GROUP BY appOrDnsName
            ORDER BY count DESC
            LIMIT 7
            """
    )
    fun getMostContactedDomains(to: Long): Flow<List<AppConnection>>

    @Query(
        """
            SELECT 0 AS uid,
                '' AS ipAddress,
                0 AS port,
                SUM(count) AS count,
                flag,
                0 AS blocked,
                appOrDnsName,
                0 as downloadBytes,
                0 as uploadBytes,
                0 as totalBytes
            FROM (
                -- From DnsLogs
                SELECT RTRIM(queryStr, '.') AS appOrDnsName,
                    COUNT(id) AS count,
                    flag
                FROM DnsLogs
                WHERE isBlocked = 0
                      AND status = 'COMPLETE'
                      AND queryStr != ''
                      AND time > :to
                GROUP BY RTRIM(queryStr, '.')

                UNION ALL

                -- From ConnectionTracker
                SELECT dnsQuery AS appOrDnsName,
                    COUNT(id) AS count,
                    flag
                FROM ConnectionTracker
                WHERE isBlocked = 0
                    AND timeStamp > :to
                    AND dnsQuery != ''
                GROUP BY dnsQuery
            ) AS combined
            GROUP BY appOrDnsName
            ORDER BY count DESC
            """
    )
    fun getAllContactedDomains(to: Long): PagingSource<Int, AppConnection>

    @Query(
        """
            SELECT uid AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(count) AS count,
              '' AS flag,
              0 AS blocked,
              appOrDnsName,
              SUM(uploadBytes) AS uploadBytes,
              SUM(downloadBytes) AS downloadBytes,
              0 AS totalBytes
            FROM
              (
                -- From ConnectionTracker
                SELECT uid,
                  appName AS appOrDnsName,
                  COUNT(id) AS count,
                  SUM(uploadBytes) AS uploadBytes,
                  SUM(downloadBytes) AS downloadBytes
                FROM ConnectionTracker
                WHERE timeStamp > :to
                  AND dnsQuery = :query
                  AND isBlocked = :isBlocked
                GROUP BY uid

                UNION ALL

                -- From DnsLogs
                SELECT uid,
                  appName AS appOrDnsName,
                  COUNT(id) AS count,
                  0 AS uploadBytes,
                  0 AS downloadBytes
                FROM DnsLogs
                WHERE time > :to
                  AND queryStr like :query
                  AND isBlocked = :isBlocked
                  AND status = 'COMPLETE'
                  AND queryStr != ''
                GROUP BY uid
              ) AS combined
            GROUP BY uid
            ORDER BY count DESC
        """
    )
    fun getDomainDetails(query: String, to: Long, isBlocked: Boolean): PagingSource<Int, AppConnection>

    @Query(
        """
        SELECT :uid AS uid,
            '' AS ipAddress,
            0 AS port,
            SUM(count) AS count,
            flag AS flag,
            0 AS blocked,
            appOrDnsName,
            0 AS uploadBytes,
            0 AS downloadBytes,
            0 AS totalBytes
        FROM
            (
                -- From ConnectionTracker
                SELECT dnsQuery AS appOrDnsName,
                    COUNT(dnsQuery) AS count,
                    flag as flag
                FROM ConnectionTracker
                WHERE dnsQuery != ''
                    AND timeStamp > :to
                    AND uid = :uid
                GROUP BY dnsQuery

                UNION ALL

                -- From DnsLogs
                SELECT queryStr AS appOrDnsName,
                    COUNT(queryStr) AS count,
                    flag as flag
                FROM DnsLogs
                WHERE uid = :uid
                    AND time > :to
                    AND status = 'COMPLETE'
                    AND queryStr != ''
                GROUP BY queryStr
            ) AS combined
        GROUP BY appOrDnsName
        ORDER BY count DESC
        LIMIT 3
        """
    )
    fun getMostDomainsByUid(uid: Int, to: Long): Flow<List<AppConnection>>

    @Query(
        """
        SELECT :uid AS uid,
            '' AS ipAddress,
            0 AS port,
            SUM(count) AS count,
            flag AS flag,
            0 AS blocked,
            appOrDnsName,
            0 AS uploadBytes,
            0 AS downloadBytes,
            0 AS totalBytes
        FROM
            (
                -- From ConnectionTracker
                SELECT dnsQuery AS appOrDnsName,
                    COUNT(dnsQuery) AS count,
                    flag as flag
                FROM ConnectionTracker
                WHERE dnsQuery != ''
                    AND timeStamp > :to
                    AND uid = :uid
                    AND dnsQuery LIKE :input
                GROUP BY dnsQuery

                UNION ALL

                -- From DnsLogs
                SELECT queryStr AS appOrDnsName,
                    COUNT(queryStr) AS count,
                    flag as flag
                FROM DnsLogs
                WHERE uid = :uid
                    AND time > :to
                    AND status = 'COMPLETE'
                    AND queryStr != ''
                    AND queryStr LIKE :input
                GROUP BY queryStr
            ) AS combined
        GROUP BY appOrDnsName
        ORDER BY count DESC
        """
    )
    fun getAllDomainsByUid(uid: Int, to: Long, input: String): PagingSource<Int, AppConnection>

    @Query(
        """
        SELECT :uid AS uid,
            '' AS ipAddress,
            0 AS port,
            SUM(count) AS count,
            flag AS flag,
            0 AS blocked,
            appOrDnsName,
            0 AS uploadBytes,
            0 AS downloadBytes,
            0 AS totalBytes
        FROM
            (
                -- From ConnectionTracker
                SELECT dnsQuery AS appOrDnsName,
                    COUNT(dnsQuery) AS count,
                    flag as flag
                FROM ConnectionTracker
                WHERE dnsQuery != ''
                    AND timeStamp > :to
                    AND uid = :uid
                GROUP BY dnsQuery

                UNION ALL

                -- From DnsLogs
                SELECT queryStr AS appOrDnsName,
                    COUNT(queryStr) AS count,
                    flag as flag
                FROM DnsLogs
                WHERE uid = :uid
                    AND time > :to
                    AND status = 'COMPLETE'
                    AND queryStr != ''
                GROUP BY queryStr
            ) AS combined
        GROUP BY appOrDnsName
        ORDER BY count DESC
        """
    )
    fun getAllDomainsByUid(uid: Int, to: Long): PagingSource<Int, AppConnection>
}
