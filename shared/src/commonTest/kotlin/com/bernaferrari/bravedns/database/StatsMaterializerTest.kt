package com.bernaferrari.bravedns.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsMaterializerTest {

    @Test
    fun connections_groupByDayAndUid() {
        val day = 20_000L
        val ts = day * MS_PER_DAY + 3_600_000L
        val batch = listOf(
            connection(uid = 10, appName = "Maps", timeStamp = ts, dnsQuery = "a.example", downloadBytes = 100, uploadBytes = 20),
            connection(uid = 10, appName = "Maps", timeStamp = ts + 1, dnsQuery = "b.example", downloadBytes = 50, uploadBytes = 5),
            connection(uid = 10, appName = "Maps", timeStamp = ts + 2, isBlocked = true),
            connection(uid = 11, appName = "Mail", timeStamp = ts, dnsQuery = "c.example", downloadBytes = 8, uploadBytes = 1),
        )

        val apps = aggregateAppsFromConnections(batch).associateBy { it.uid }
        assertEquals(2, apps[10]!!.connectionCount)
        assertEquals(1, apps[10]!!.blockedCount)
        assertEquals(150, apps[10]!!.downloadBytes)
        assertEquals(25, apps[10]!!.uploadBytes)
        assertEquals(1, apps[11]!!.connectionCount)
        assertEquals(0, apps[11]!!.blockedCount)
    }

    @Test
    fun dns_countsAllowedAndBlocked() {
        val day = 20_001L
        val ts = day * MS_PER_DAY
        val batch = listOf(
            dns(uid = 7, appName = "Browser", time = ts, queryStr = "ads.example.", isBlocked = true),
            dns(uid = 7, appName = "Browser", time = ts + 1, queryStr = "ok.example.", status = DNS_STATUS_COMPLETE),
            dns(uid = 7, appName = "Browser", time = ts + 2, queryStr = "pending.example.", status = "START"),
        )

        val apps = aggregateAppsFromDns(batch).single()
        assertEquals(7, apps.uid)
        assertEquals(1, apps.dnsQueryCount)
        assertEquals(1, apps.blockedDnsCount)
        assertEquals(0, apps.connectionCount)
    }

    @Test
    fun domains_trimDotAndRule2G() {
        val ts = 20_002L * MS_PER_DAY
        val conns = listOf(
            connection(uid = 1, timeStamp = ts, dnsQuery = "ok.example", flag = "US"),
            connection(uid = 1, timeStamp = ts, dnsQuery = "bad.example", isBlocked = true, blockedByRule = "Rule #2G-ads", flag = "US"),
            connection(uid = 1, timeStamp = ts, dnsQuery = "other.example", isBlocked = true, blockedByRule = "Rule #1", flag = "US"),
        )
        val dns = listOf(
            dns(uid = 1, time = ts, queryStr = "ok.example.", status = DNS_STATUS_COMPLETE, flag = "US"),
        )

        val fromConn = aggregateDomainsFromConnections(conns).associateBy { it.domain }
        assertEquals(1, fromConn["ok.example"]!!.contactedCount)
        assertEquals(1, fromConn["bad.example"]!!.blockedCount)
        assertTrue("other.example" !in fromConn)

        val fromDns = aggregateDomainsFromDns(dns).single()
        assertEquals("ok.example", fromDns.domain)
        assertEquals(1, fromDns.contactedCount)
    }

    @Test
    fun asn_skipsMissingIpInfo() {
        val ts = 20_003L * MS_PER_DAY
        val conns = listOf(
            connection(uid = 1, timeStamp = ts, ipAddress = "1.1.1.1", downloadBytes = 9, uploadBytes = 3),
            connection(uid = 1, timeStamp = ts, ipAddress = "9.9.9.9", isBlocked = true),
            connection(uid = 1, timeStamp = ts, ipAddress = "8.8.8.8"),
        )
        val info = mapOf(
            "1.1.1.1" to ipInfo("1.1.1.1", "Cloudflare", "US"),
            "9.9.9.9" to ipInfo("9.9.9.9", "Quad9", "CH"),
        )

        val asns = aggregateAsnsFromConnections(conns, info).associateBy { it.asName }
        assertEquals(1, asns["Cloudflare"]!!.connectionCount)
        assertEquals(9, asns["Cloudflare"]!!.downloadBytes)
        assertEquals(1, asns["Quad9"]!!.blockedCount)
        assertTrue("" !in asns)
        assertEquals(2, asns.size)
    }

    private fun connection(
        uid: Int,
        appName: String = "App",
        timeStamp: Long,
        dnsQuery: String? = null,
        isBlocked: Boolean = false,
        blockedByRule: String = "",
        downloadBytes: Long = 0,
        uploadBytes: Long = 0,
        ipAddress: String = "",
        flag: String = "",
    ): ConnectionTracker {
        val c = ConnectionTracker()
        c.uid = uid
        c.appName = appName
        c.timeStamp = timeStamp
        c.dnsQuery = dnsQuery
        c.isBlocked = isBlocked
        c.blockedByRule = blockedByRule
        c.downloadBytes = downloadBytes
        c.uploadBytes = uploadBytes
        c.ipAddress = ipAddress
        c.flag = flag
        return c
    }

    private fun dns(
        uid: Int,
        appName: String = "App",
        time: Long,
        queryStr: String,
        isBlocked: Boolean = false,
        status: String = DNS_STATUS_COMPLETE,
        flag: String = "",
    ): DnsLog {
        val d = DnsLog()
        d.uid = uid
        d.appName = appName
        d.time = time
        d.queryStr = queryStr
        d.isBlocked = isBlocked
        d.status = status
        d.flag = flag
        return d
    }

    private fun ipInfo(ip: String, asName: String, countryCode: String): IpInfo {
        val info = IpInfo()
        info.ip = ip
        info.asName = asName
        info.countryCode = countryCode
        return info
    }
}
