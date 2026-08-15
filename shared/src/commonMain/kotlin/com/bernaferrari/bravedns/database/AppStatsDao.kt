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
interface AppStatsDao {

    @Query(
        """
               SELECT uid as uid,
                   '' as ipAddress,
                   0 as port,
                   COUNT(*) as count,
                   0 as blocked,
                   '' as flag,
                   appName as appOrDnsName,
                   0 as downloadBytes,
                   0 as uploadBytes,
                   0 as totalBytes
               FROM ConnectionTracker
               WHERE downloadBytes = 0
                   AND uploadBytes = 0
                   AND isBlocked = 0
                   AND duration = 0
                   AND synack = 0
                    AND message = ''
                    AND timeStamp > :to
                GROUP BY uid
                ORDER BY count DESC
                LIMIT 7
                """
    )
    fun getTopActiveConns(to: Long): Flow<List<AppConnection>>

    @Query(
        """
            SELECT uid as uid,
                '' as ipAddress,
                0 as port,
                COUNT(*) as count,
                0 as blocked,
                '' as flag,
                appName as appOrDnsName,
                0 as downloadBytes,
                0 as uploadBytes,
                0 as totalBytes
            FROM ConnectionTracker
            WHERE downloadBytes = 0
                AND uploadBytes = 0
                AND isBlocked = 0
                AND duration = 0
                AND synack = 0
                AND message = ''
                AND timeStamp >= :to
            GROUP BY uid
            ORDER BY count DESC
            """
    )
    fun getAllActiveConns(to: Long): PagingSource<Int, AppConnection>

    @Query(
        """
                   SELECT ct.uid as uid,
                               ct.ipAddress as ipAddress,
                               0 as port,
                               COUNT(*) as count,
                               0 as blocked,
                               ct.flag as flag,
                               asn.asName as appOrDnsName,
                               0 as downloadBytes,
                               0 as uploadBytes,
                               0 as totalBytes
                           FROM ConnectionTracker as ct
                           LEFT JOIN IpInfo as asn ON ct.ipAddress = asn.ip
                           WHERE downloadBytes = 0
                               AND uploadBytes = 0
                               AND isBlocked = 0
                               AND duration = 0
                               AND synack = 0
                               AND message = ''
                               AND uid = :uid
                               AND timeStamp >= :to
                           GROUP BY ipAddress
                           ORDER BY count DESC
                   LIMIT 3
               """
    )
    fun getTopActiveConns(uid: Int, to: Long): Flow<List<AppConnection>>

    @Query(
        """
        SELECT ct.uid as uid,
            ct.ipAddress as ipAddress,
            0 as port,
            COUNT(*) as count,
            0 as blocked,
            ct.flag as flag,
            asn.asName as appOrDnsName,
            0 as downloadBytes,
            0 as uploadBytes,
            0 as totalBytes
        FROM ConnectionTracker as ct
        LEFT JOIN IpInfo as asn ON ct.ipAddress = asn.ip
        WHERE downloadBytes = 0
            AND uploadBytes = 0
            AND isBlocked = 0
            AND duration = 0
            AND synack = 0
            AND message = ''
            AND uid = :uid
            AND timeStamp >= :to
            AND ct.ipAddress LIKE :query
        GROUP BY ipAddress
        ORDER BY count DESC
        """
    )
    fun getAllActiveConns(uid: Int, to: Long, query: String): PagingSource<Int, AppConnection>

    @Query(
        """
                       SELECT rl.uid as uid,
                                   rl.ipAddress as ipAddress,
                                   0 as port,
                                   COUNT(*) as count,
                                   0 as blocked,
                                   rl.flag as flag,
                                   asn.asName as appOrDnsName,
                                   0 as downloadBytes,
                                   0 as uploadBytes,
                                   0 as totalBytes
                               FROM RethinkLog as rl
                               LEFT JOIN IpInfo as asn ON rl.ipAddress = asn.ip
                               WHERE downloadBytes = 0
                                   AND uploadBytes = 0
                                   AND isBlocked = 0
                                   AND duration = 0
                                   AND synack = 0
                                   AND message = ''
                                   AND timeStamp >= :to
                               GROUP BY ipAddress
                               ORDER BY count DESC
                       LIMIT 3
                   """
    )
    fun getRethinkTopActiveConns(to: Long): Flow<List<AppConnection>>

    @Query (
        """
                       SELECT rl.uid as uid,
                                   rl.ipAddress as ipAddress,
                                   0 as port,
                                   COUNT(*) as count,
                                   0 as blocked,
                                   rl.flag as flag,
                                   asn.asName as appOrDnsName,
                                   0 as downloadBytes,
                                   0 as uploadBytes,
                                   0 as totalBytes
                               FROM RethinkLog as rl
                               LEFT JOIN IpInfo as asn ON rl.ipAddress = asn.ip
                               WHERE downloadBytes = 0
                                   AND uploadBytes = 0
                                   AND isBlocked = 0
                                   AND duration = 0
                                   AND synack = 0
                                   AND message = ''
                                   AND timeStamp >= :to
                               GROUP BY ipAddress
                               ORDER BY count DESC
                   """
    )
    fun getRethinkAllActiveConns(to: Long): PagingSource<Int, AppConnection>

    @Query(
        """
            SELECT uid AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(count) AS count,
              '' AS flag,
              0 AS blocked,
              appOrDnsName,
              Sum(downloadBytes) AS downloadBytes,
              Sum(uploadBytes) AS uploadBytes,
              Sum(uploadBytes + downloadBytes) AS totalBytes
            FROM
              (
                -- From DnsLogs
                SELECT uid AS uid,
                  appName AS appOrDnsName,
                  Count(uid) AS count,
                  0 AS downloadBytes,
                  0 AS uploadBytes
                FROM DnsLogs
                WHERE isBlocked = 0
                  AND status = 'COMPLETE'
                  AND queryStr != ''
                  AND time > :to
                GROUP BY uid

                UNION ALL

                -- From ConnectionTracker
                SELECT uid AS uid,
                  appName AS appOrDnsName,
                  COUNT(uid) AS count,
                  sum(downloadBytes) as downloadBytes,
                  sum(uploadBytes) as uploadBytes
                FROM ConnectionTracker
                WHERE isBlocked = 0
                  AND timeStamp > :to
                  AND dnsQuery != ''
                GROUP BY uid
              ) AS combined
            GROUP BY uid
            ORDER BY totalBytes DESC
            LIMIT 7
        """
    )
    fun getMostAllowedApps(to: Long): Flow<List<AppConnection>>

    @Query(
        """
            SELECT uid AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(count) AS count,
              '' as flag,
              0 AS blocked,
              appOrDnsName,
              sum(downloadBytes) as downloadBytes,
              sum(uploadBytes) as uploadBytes,
              sum(uploadBytes + downloadBytes) as totalBytes
            FROM
              (
                -- From DnsLogs
                SELECT uid as uid,
                  appName AS appOrDnsName,
                  COUNT(uid) AS count,
                  0 as downloadBytes,
                  0 as uploadBytes
                FROM DnsLogs
                WHERE isBlocked = 0
                  AND status = 'COMPLETE'
                  AND queryStr != ''
                  AND time > :to
                GROUP BY uid

                UNION ALL

                  -- From ConnectionTracker
                SELECT uid as uid,
                  appName AS appOrDnsName,
                  COUNT(uid) AS count,
                  sum(downloadBytes) as downloadBytes,
                  sum(uploadBytes) as uploadBytes
                FROM ConnectionTracker
                WHERE isBlocked = 0
                  AND timeStamp > :to
                  AND dnsQuery != ''
                GROUP BY uid
              ) AS combined
            GROUP BY uid
            ORDER BY totalBytes DESC
        """
    )
    fun getAllAllowedApps(to: Long): PagingSource<Int, AppConnection>

    @Query(
        """
            SELECT uid AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(count) AS count,
              '' as flag,
              1 AS blocked,
              appOrDnsName,
              0 as downloadBytes,
              0 as uploadBytes,
              0 as totalBytes
            FROM
              (
                -- From DnsLogs
                SELECT uid as uid,
                  appName AS appOrDnsName,
                  COUNT(id) AS count
                FROM DnsLogs
                WHERE isBlocked = 1
                  AND time > :to
                GROUP BY uid

                UNION ALL

                  -- From ConnectionTracker
                SELECT uid as uid,
                  appName AS appOrDnsName,
                  COUNT(id) AS count
                FROM ConnectionTracker
                WHERE isBlocked = 1
                  AND timeStamp > :to
                GROUP BY uid
              ) AS combined
            GROUP BY uid
            ORDER BY count DESC
            LIMIT 7
        """
    )
    fun getMostBlockedApps(to: Long): Flow<List<AppConnection>>

    @Query(
        """
            SELECT
              uid AS uid,
              '' AS ipAddress,
              0 AS port,
              SUM(count) AS count,
              '' as flag,
              1 AS blocked,
              appOrDnsName,
              0 as downloadBytes,
              0 as uploadBytes,
              0 as totalBytes
            FROM
              (
                -- From DnsLogs
                SELECT uid as uid,
                  appName AS appOrDnsName,
                  COUNT(id) AS count
                FROM DnsLogs
                WHERE isBlocked = 1
                  AND time > :to
                GROUP BY uid

                UNION ALL

                  -- From ConnectionTracker
                SELECT uid as uid,
                  appName AS appOrDnsName,
                  COUNT(id) AS count
                FROM ConnectionTracker
                WHERE isBlocked = 1
                  AND timeStamp > :to
                GROUP BY uid
              ) AS combined
            GROUP BY uid
            ORDER BY count DESC
        """
    )
    fun getAllBlockedApps(to: Long): PagingSource<Int, AppConnection>
}
