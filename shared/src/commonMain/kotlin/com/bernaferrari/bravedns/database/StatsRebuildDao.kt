package com.bernaferrari.bravedns.database

import androidx.room3.Query
import androidx.room3.Transaction

/** Rebuild only on explicit deletion; ordinary inserts remain O(batch). */
// Room requires a query method per SQL statement; transaction wrappers preserve atomic deletion.
@Suppress("TooManyFunctions")
interface StatsRebuildDao {
    @Query("DELETE FROM daily_app_stats") suspend fun clearAppStats()
    @Query("DELETE FROM daily_country_stats") suspend fun clearCountryStats()
    @Query("DELETE FROM daily_domain_stats") suspend fun clearDomainStats()
    @Query("DELETE FROM daily_ip_stats") suspend fun clearIpStats()
    @Query("DELETE FROM daily_asn_stats") suspend fun clearAsnStats()

    @Query("""
        INSERT INTO daily_app_stats
        (date,uid,appName,connectionCount,blockedCount,downloadBytes,uploadBytes,dnsQueryCount,blockedDnsCount)
        SELECT date,uid,MAX(appName),SUM(connections),SUM(blocked),SUM(rx),SUM(tx),SUM(dns),SUM(blockedDns)
        FROM (
          SELECT timeStamp/86400000 AS date,uid,appName,
            CASE WHEN isBlocked=0 AND COALESCE(dnsQuery,'')!='' THEN 1 ELSE 0 END AS connections,
            CASE WHEN isBlocked=1 THEN 1 ELSE 0 END AS blocked,
            CASE WHEN isBlocked=0 THEN downloadBytes ELSE 0 END AS rx,
            CASE WHEN isBlocked=0 THEN uploadBytes ELSE 0 END AS tx,0 AS dns,0 AS blockedDns
          FROM ConnectionTracker
          UNION ALL
          SELECT time/86400000,uid,appName,0,0,0,0,
            CASE WHEN isBlocked=0 AND status='COMPLETE' AND queryStr!='' THEN 1 ELSE 0 END,
            CASE WHEN isBlocked=1 THEN 1 ELSE 0 END FROM DNSLogs
        ) GROUP BY date,uid
    """) suspend fun rebuildAppStats()

    @Query("""
        INSERT INTO daily_country_stats (date,flag,connectionCount,dnsQueryCount)
        SELECT date,flag,SUM(connections),SUM(dns) FROM (
          SELECT timeStamp/86400000 AS date,flag,1 AS connections,0 AS dns
          FROM ConnectionTracker WHERE isBlocked=0
          UNION ALL
          SELECT time/86400000,flag,0,1 FROM DNSLogs
          WHERE isBlocked=0 AND status='COMPLETE' AND queryStr!=''
        ) GROUP BY date,flag
    """) suspend fun rebuildCountryStats()

    @Query("""
        INSERT INTO daily_domain_stats (date,domain,flag,contactedCount,blockedCount)
        SELECT date,domain,MAX(flag),SUM(contacted),SUM(blocked) FROM (
          SELECT timeStamp/86400000 AS date,RTRIM(dnsQuery,'.') AS domain,flag,
            CASE WHEN isBlocked=0 THEN 1 ELSE 0 END AS contacted,
            CASE WHEN isBlocked=1 THEN 1 ELSE 0 END AS blocked
          FROM ConnectionTracker WHERE COALESCE(dnsQuery,'')!=''
            AND (isBlocked=0 OR blockedByRule LIKE 'Rule #2G%')
          UNION ALL
          SELECT time/86400000,RTRIM(queryStr,'.'),flag,
            CASE WHEN isBlocked=0 THEN 1 ELSE 0 END,
            CASE WHEN isBlocked=1 THEN 1 ELSE 0 END FROM DNSLogs
          WHERE queryStr!='' AND (isBlocked=1 OR status='COMPLETE')
        ) WHERE domain!='' GROUP BY date,domain
    """) suspend fun rebuildDomainStats()

    @Query("""
        INSERT INTO daily_ip_stats (date,ipAddress,flag,contactedCount,blockedCount,downloadBytes,uploadBytes)
        SELECT timeStamp/86400000,ipAddress,MAX(flag),
          SUM(CASE WHEN isBlocked=0 THEN 1 ELSE 0 END),
          SUM(CASE WHEN isBlocked=1 THEN 1 ELSE 0 END),
          SUM(CASE WHEN isBlocked=0 THEN downloadBytes ELSE 0 END),
          SUM(CASE WHEN isBlocked=0 THEN uploadBytes ELSE 0 END)
        FROM ConnectionTracker WHERE ipAddress!='' GROUP BY timeStamp/86400000,ipAddress
    """) suspend fun rebuildIpStats()

    @Query("""
        INSERT INTO daily_asn_stats (date,asName,countryCode,connectionCount,blockedCount,downloadBytes,uploadBytes)
        SELECT c.timeStamp/86400000,i.asName,MAX(i.countryCode),
          SUM(CASE WHEN c.isBlocked=0 THEN 1 ELSE 0 END),
          SUM(CASE WHEN c.isBlocked=1 THEN 1 ELSE 0 END),
          SUM(CASE WHEN c.isBlocked=0 THEN c.downloadBytes ELSE 0 END),
          SUM(CASE WHEN c.isBlocked=0 THEN c.uploadBytes ELSE 0 END)
        FROM ConnectionTracker c JOIN IpInfo i ON c.ipAddress=i.ip WHERE i.asName!=''
        GROUP BY c.timeStamp/86400000,i.asName
    """) suspend fun rebuildAsnStats()

    @Transaction
    suspend fun rebuildStats() {
        clearAppStats()
        clearCountryStats()
        clearDomainStats()
        clearIpStats()
        clearAsnStats()
        rebuildAppStats()
        rebuildCountryStats()
        rebuildDomainStats()
        rebuildIpStats()
        rebuildAsnStats()
    }
}
