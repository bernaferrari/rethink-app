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
interface AsnStatsDao {

    @Query(
        """
        SELECT -1 as uid,
            '' as ipAddress,
            0 as port,
            COUNT(*) as count,
            0 as blocked,
            ipInfo.countryCode AS flag,
            ipInfo.asName AS appOrDnsName,
            Sum(conn.downloadBytes) as downloadBytes,
            Sum(conn.uploadBytes) as uploadBytes,
            Sum(downloadBytes + uploadBytes) as totalBytes
        FROM ConnectionTracker AS conn
        INNER JOIN IpInfo AS ipInfo ON conn.ipAddress = ipInfo.ip
        WHERE conn.isBlocked = 0
            AND ipInfo.asName != ''
            AND conn.timeStamp > :to
        GROUP BY ipInfo.asName
        ORDER BY count DESC
        LIMIT 7
        """
    )
    fun getMostConnectedASN(to: Long): Flow<List<AppConnection>>

    @Query(
        """
        SELECT -1 as uid,
            '' as ipAddress,
            0 as port,
            COUNT(*) as count,
            0 as blocked,
            ipInfo.countryCode AS flag,
            ipInfo.asName AS appOrDnsName,
            Sum(conn.downloadBytes) as downloadBytes,
            Sum(conn.uploadBytes) as uploadBytes,
            Sum(downloadBytes + uploadBytes) as totalBytes
        FROM ConnectionTracker AS conn
        INNER JOIN IpInfo AS ipInfo ON conn.ipAddress = ipInfo.ip
        WHERE conn.isBlocked = 0
            AND ipInfo.asName != ''
            AND conn.timeStamp > :to
        GROUP BY ipInfo.asName
        ORDER BY count DESC
        """
    )
    fun getAllConnectedASN(to: Long): PagingSource<Int, AppConnection>

    @Query(
        """
        SELECT conn.uid as uid,
            ipInfo.asName as ipAddress,
            0 as port,
            COUNT(*) as count,
            0 as blocked,
            ipInfo.countryCode AS flag,
            conn.appName AS appOrDnsName,
            Sum(conn.downloadBytes) as downloadBytes,
            Sum(conn.uploadBytes) as uploadBytes,
            Sum(downloadBytes + uploadBytes) as totalBytes
        FROM ConnectionTracker AS conn
        INNER JOIN IpInfo AS ipInfo ON conn.ipAddress = ipInfo.ip
        WHERE conn.isBlocked = 0
            AND ipInfo.asName != ''
            AND conn.timeStamp > :to
            AND ipInfo.asName = :asn
        GROUP BY conn.uid
        ORDER BY count DESC
        """
    )
    fun getAsnDetails(asn: String, to: Long): PagingSource<Int, AppConnection>

    @Query(
        """
        SELECT -1 as uid,
            '' as ipAddress,
            0 as port,
            COUNT(*) as count,
            1 as blocked,
            ipInfo.countryCode AS flag,
            ipInfo.asName AS appOrDnsName,
            0 as downloadBytes,
            0 as uploadBytes,
            0 as totalBytes
        FROM ConnectionTracker AS conn
        INNER JOIN IpInfo AS ipInfo ON conn.ipAddress = ipInfo.ip
        WHERE conn.isBlocked = 1
            AND ipInfo.asName != ''
            AND conn.timeStamp > :to
        GROUP BY ipInfo.asName
        ORDER BY count DESC
        LIMIT 7
        """
    )
    fun getMostBlockedASN(to: Long): Flow<List<AppConnection>>

    @Query(
        """
        SELECT -1 as uid,
            '' as ipAddress,
            0 as port,
            COUNT(*) as count,
            1 as blocked,
            ipInfo.countryCode AS flag,
            ipInfo.asName AS appOrDnsName,
            0 as downloadBytes,
            0 as uploadBytes,
            0 as totalBytes
        FROM ConnectionTracker AS conn
        INNER JOIN IpInfo AS ipInfo ON conn.ipAddress = ipInfo.ip
        WHERE conn.isBlocked = 1
            AND ipInfo.asName != ''
            AND conn.timeStamp > :to
        GROUP BY ipInfo.asName
        ORDER BY count DESC
        """
    )
    fun getAllBlockedASN(to: Long): PagingSource<Int, AppConnection>

    @Query(
        """
        SELECT conn.uid as uid,
            ipInfo.asName as ipAddress,
            0 as port,
            COUNT(*) as count,
            1 as blocked,
            ipInfo.countryCode AS flag,
            conn.appName AS appOrDnsName,
            0 as downloadBytes,
            0 as uploadBytes,
            0 as totalBytes
        FROM ConnectionTracker AS conn
        INNER JOIN IpInfo AS ipInfo ON conn.ipAddress = ipInfo.ip
        WHERE conn.isBlocked = 1
            AND ipInfo.asName != ''
            AND conn.timeStamp > :to
            AND ipInfo.asName = :asn
        GROUP BY conn.uid
        ORDER BY count DESC
        """
    )
    fun getAsnBlockedDetails(asn: String, to: Long): PagingSource<Int, AppConnection>

    @Query(
        """
            SELECT
                conn.uid AS uid,
                GROUP_CONCAT(DISTINCT conn.ipAddress) AS ipAddress,
                0 AS port,
                COUNT(*) AS count,
                0 AS blocked,
                ipInfo.countryCode AS flag,
                ipInfo.asName AS appOrDnsName,
                0 AS downloadBytes,
                0 AS uploadBytes,
                0 AS totalBytes
            FROM
                ConnectionTracker AS conn
            INNER JOIN
                IpInfo AS ipInfo
            ON
                conn.ipAddress = ipInfo.ip
            WHERE
                ipInfo.asName != ''
                AND conn.timeStamp > :to
                AND conn.uid = :uid
            GROUP BY
                ipInfo.asName
            ORDER BY
                count DESC
            LIMIT 3
        """
    )
    fun getAsnLogsLimited(uid: Int, to: Long): Flow<List<AppConnection>>

    @Query(
        """
            SELECT
                conn.uid AS uid,
                GROUP_CONCAT(DISTINCT conn.ipAddress) AS ipAddress,
                0 AS port,
                COUNT(*) AS count,
                0 AS blocked,
                ipInfo.countryCode AS flag,
                ipInfo.asName AS appOrDnsName,
                0 AS downloadBytes,
                0 AS uploadBytes,
                0 AS totalBytes
            FROM
                ConnectionTracker AS conn
            INNER JOIN
                IpInfo AS ipInfo
            ON
                conn.ipAddress = ipInfo.ip
            WHERE
                ipInfo.asName != ''
                AND conn.timeStamp > :to
                AND conn.uid = :uid
                AND ipInfo.asName LIKE :input
            GROUP BY
                ipInfo.asName
            ORDER BY
                count DESC
           """
    )
    fun getAllAsnLogs(uid: Int, to: Long, input: String): PagingSource<Int, AppConnection>
}
