package com.bernaferrari.bravedns.database

import androidx.room3.Query

/** Rebuilds one affected ASN/day from IP totals, including metadata learned after connection start. */
interface AsnStatsMaintenanceDao {
    @Query("SELECT DISTINCT date FROM daily_ip_stats WHERE ipAddress = :ip")
    suspend fun daysForIp(ip: String): List<Long>

    @Query(REPLACE_ASN_DAY_SQL)
    suspend fun replaceAsnDay(date: Long, asName: String)
}

internal const val REPLACE_ASN_DAY_SQL = """
    INSERT INTO daily_asn_stats
        (date, asName, countryCode, connectionCount, blockedCount, downloadBytes, uploadBytes)
    SELECT :date, :asName, COALESCE(MAX(i.countryCode), ''),
        COALESCE(SUM(p.contactedCount), 0), COALESCE(SUM(p.blockedCount), 0),
        COALESCE(SUM(p.downloadBytes), 0), COALESCE(SUM(p.uploadBytes), 0)
    FROM daily_ip_stats p JOIN IpInfo i ON p.ipAddress = i.ip
    WHERE p.date = :date AND i.asName = :asName
    ON CONFLICT(date, asName) DO UPDATE SET
        countryCode = excluded.countryCode,
        connectionCount = excluded.connectionCount,
        blockedCount = excluded.blockedCount,
        downloadBytes = excluded.downloadBytes,
        uploadBytes = excluded.uploadBytes
"""
