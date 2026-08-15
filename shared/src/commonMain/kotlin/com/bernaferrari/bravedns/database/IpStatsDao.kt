/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.Query
import com.bernaferrari.bravedns.data.AppConnection

@Dao
interface IpStatsDao {

    @Query(
        """
            SELECT uid AS uid,
              ipAddress AS ipAddress,
              0 AS port,
              COUNT(id) AS count,
              flag AS flag,
              isBlocked AS blocked,
              appName AS appOrDnsName,
              SUM(uploadBytes) AS uploadBytes,
              SUM(downloadBytes) AS downloadBytes,
              SUM(downloadBytes + uploadBytes) AS totalBytes
            FROM ConnectionTracker
            WHERE timeStamp > :to
              AND ipAddress = :ip
              AND isBlocked = :isBlocked
            GROUP BY uid
            ORDER BY count DESC
        """
    )
    fun getIpDetails(ip: String, to: Long, isBlocked: Boolean): PagingSource<Int, AppConnection>
}
