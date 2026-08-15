/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import com.bernaferrari.bravedns.data.ConnectionSummary
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Incremental daily-stats maintenance. Groups only the flushed log batch (O(batch)),
 * then UPSERTs into the daily_* tables. Bound lazily from [LogDatabase] via Koin.
 */
class StatsMaterializer(
    private val dailyStatsDao: DailyStatsDao,
    private val ipInfoDao: IpInfoDAO,
) {
    suspend fun onInserted(batch: List<*>) {
        if (batch.isEmpty()) return
        when (batch.first()) {
            is ConnectionTracker -> applyConnections(batch.filterIsInstance<ConnectionTracker>())
            is DnsLog -> applyDns(batch.filterIsInstance<DnsLog>())
        }
    }

    suspend fun onUpdated(batch: List<*>) {
        if (batch.isEmpty()) return
        if (batch.first() !is ConnectionSummary) return
        applySummaries(batch.filterIsInstance<ConnectionSummary>())
    }

    suspend fun purgeBefore(cutoffMillis: Long) {
        val fromDay = epochDay(cutoffMillis)
        dailyStatsDao.purgeAppsBefore(fromDay)
        dailyStatsDao.purgeCountriesBefore(fromDay)
        dailyStatsDao.purgeAsnsBefore(fromDay)
        dailyStatsDao.purgeDomainsBefore(fromDay)
    }

    private suspend fun applyConnections(conns: List<ConnectionTracker>) {
        val apps = aggregateAppsFromConnections(conns)
        val countries = aggregateCountriesFromConnections(conns)
        val domains = aggregateDomainsFromConnections(conns)
        val asns = aggregateAsnsFromConnections(conns, lookupAsns(conns))
        apps.forEach { dailyStatsDao.upsertApp(it.date, it.uid, it.appName, it.connectionCount, it.blockedCount, it.downloadBytes, it.uploadBytes, it.dnsQueryCount, it.blockedDnsCount) }
        countries.forEach { dailyStatsDao.upsertCountry(it.date, it.flag, it.connectionCount, it.dnsQueryCount) }
        domains.forEach { dailyStatsDao.upsertDomain(it.date, it.domain, it.flag, it.contactedCount, it.blockedCount) }
        asns.forEach { dailyStatsDao.upsertAsn(it.date, it.asName, it.countryCode, it.connectionCount, it.blockedCount, it.downloadBytes, it.uploadBytes) }
    }

    private suspend fun applyDns(logs: List<DnsLog>) {
        val apps = aggregateAppsFromDns(logs)
        val countries = aggregateCountriesFromDns(logs)
        val domains = aggregateDomainsFromDns(logs)
        apps.forEach { dailyStatsDao.upsertApp(it.date, it.uid, it.appName, it.connectionCount, it.blockedCount, it.downloadBytes, it.uploadBytes, it.dnsQueryCount, it.blockedDnsCount) }
        countries.forEach { dailyStatsDao.upsertCountry(it.date, it.flag, it.connectionCount, it.dnsQueryCount) }
        domains.forEach { dailyStatsDao.upsertDomain(it.date, it.domain, it.flag, it.contactedCount, it.blockedCount) }
    }

    private suspend fun applySummaries(summaries: List<ConnectionSummary>) {
        val today = epochDay(kotlin.time.Clock.System.now().toEpochMilliseconds())
        val byUid = LinkedHashMap<Int, LongArray>()
        for (s in summaries) {
            val uid = s.uid.toIntOrNull() ?: continue
            val acc = byUid.getOrPut(uid) { longArrayOf(0L, 0L) }
            acc[0] += s.downloadBytes
            acc[1] += s.uploadBytes
        }
        for ((uid, bytes) in byUid) {
            if (bytes[0] == 0L && bytes[1] == 0L) continue
            dailyStatsDao.upsertApp(today, uid, "", 0, 0, bytes[0], bytes[1], 0, 0)
        }
    }

    private suspend fun lookupAsns(conns: List<ConnectionTracker>): Map<String, IpInfo> {
        val ips = conns.map { it.ipAddress }.filter { it.isNotEmpty() }.distinct()
        if (ips.isEmpty()) return emptyMap()
        return ipInfoDao.getByIps(ips).associateBy { it.ip }
    }

    companion object : KoinComponent {
        @Volatile
        private var instance: StatsMaterializer? = null

        suspend fun onInserted(batch: List<*>) = impl().onInserted(batch)

        suspend fun onUpdated(batch: List<*>) = impl().onUpdated(batch)

        suspend fun purgeBefore(cutoffMillis: Long) = impl().purgeBefore(cutoffMillis)

        private fun impl(): StatsMaterializer {
            instance?.let { return it }
            val db: LogDatabase = get()
            return StatsMaterializer(db.dailyStatsDAO(), db.ipInfoDao()).also { instance = it }
        }
    }
}

internal const val MS_PER_DAY = 86_400_000L
internal const val BLOCKED_DOMAIN_RULE_PREFIX = "Rule #2G"
internal const val DNS_STATUS_COMPLETE = "COMPLETE"

internal fun epochDay(timeMillis: Long): Long = timeMillis / MS_PER_DAY

internal fun normalizeDomain(raw: String): String = raw.trimEnd('.')

internal data class AppAgg(
    val date: Long,
    val uid: Int,
    var appName: String,
    var connectionCount: Int = 0,
    var blockedCount: Int = 0,
    var downloadBytes: Long = 0,
    var uploadBytes: Long = 0,
    var dnsQueryCount: Int = 0,
    var blockedDnsCount: Int = 0,
)

internal data class CountryAgg(
    val date: Long,
    val flag: String,
    var connectionCount: Int = 0,
    var dnsQueryCount: Int = 0,
)

internal data class DomainAgg(
    val date: Long,
    val domain: String,
    var flag: String,
    var contactedCount: Int = 0,
    var blockedCount: Int = 0,
)

internal data class AsnAgg(
    val date: Long,
    val asName: String,
    var countryCode: String,
    var connectionCount: Int = 0,
    var blockedCount: Int = 0,
    var downloadBytes: Long = 0,
    var uploadBytes: Long = 0,
)

internal fun aggregateAppsFromConnections(conns: List<ConnectionTracker>): Collection<AppAgg> {
    val out = LinkedHashMap<Pair<Long, Int>, AppAgg>()
    for (c in conns) {
        val day = epochDay(c.timeStamp)
        val row = out.getOrPut(day to c.uid) { AppAgg(day, c.uid, c.appName) }
        if (c.appName.isNotEmpty()) row.appName = c.appName
        if (c.isBlocked) {
            row.blockedCount += 1
        } else if (!c.dnsQuery.isNullOrEmpty()) {
            row.connectionCount += 1
            row.downloadBytes += c.downloadBytes
            row.uploadBytes += c.uploadBytes
        }
    }
    return out.values
}

internal fun aggregateAppsFromDns(logs: List<DnsLog>): Collection<AppAgg> {
    val out = LinkedHashMap<Pair<Long, Int>, AppAgg>()
    for (d in logs) {
        val day = epochDay(d.time)
        val row = out.getOrPut(day to d.uid) { AppAgg(day, d.uid, d.appName) }
        if (d.appName.isNotEmpty()) row.appName = d.appName
        if (d.isBlocked) {
            row.blockedDnsCount += 1
        } else if (d.status == DNS_STATUS_COMPLETE && d.queryStr.isNotEmpty()) {
            row.dnsQueryCount += 1
        }
    }
    return out.values
}

internal fun aggregateCountriesFromConnections(conns: List<ConnectionTracker>): Collection<CountryAgg> {
    val out = LinkedHashMap<Pair<Long, String>, CountryAgg>()
    for (c in conns) {
        if (c.isBlocked) continue
        val day = epochDay(c.timeStamp)
        val row = out.getOrPut(day to c.flag) { CountryAgg(day, c.flag) }
        row.connectionCount += 1
    }
    return out.values
}

internal fun aggregateCountriesFromDns(logs: List<DnsLog>): Collection<CountryAgg> {
    val out = LinkedHashMap<Pair<Long, String>, CountryAgg>()
    for (d in logs) {
        if (d.isBlocked || d.status != DNS_STATUS_COMPLETE || d.queryStr.isEmpty()) continue
        val day = epochDay(d.time)
        val row = out.getOrPut(day to d.flag) { CountryAgg(day, d.flag) }
        row.dnsQueryCount += 1
    }
    return out.values
}

internal fun aggregateDomainsFromConnections(conns: List<ConnectionTracker>): Collection<DomainAgg> {
    val out = LinkedHashMap<Pair<Long, String>, DomainAgg>()
    for (c in conns) {
        val domain = c.dnsQuery?.let(::normalizeDomain).orEmpty()
        if (domain.isEmpty()) continue
        val contacted = !c.isBlocked
        val blocked = c.isBlocked && c.blockedByRule.startsWith(BLOCKED_DOMAIN_RULE_PREFIX)
        if (!contacted && !blocked) continue
        val day = epochDay(c.timeStamp)
        val row = out.getOrPut(day to domain) { DomainAgg(day, domain, c.flag) }
        if (c.flag.isNotEmpty()) row.flag = c.flag
        if (contacted) row.contactedCount += 1 else row.blockedCount += 1
    }
    return out.values
}

internal fun aggregateDomainsFromDns(logs: List<DnsLog>): Collection<DomainAgg> {
    val out = LinkedHashMap<Pair<Long, String>, DomainAgg>()
    for (d in logs) {
        val domain = normalizeDomain(d.queryStr)
        if (domain.isEmpty()) continue
        val contacted = !d.isBlocked && d.status == DNS_STATUS_COMPLETE
        val blocked = d.isBlocked
        if (!contacted && !blocked) continue
        val day = epochDay(d.time)
        val row = out.getOrPut(day to domain) { DomainAgg(day, domain, d.flag) }
        if (d.flag.isNotEmpty()) row.flag = d.flag
        if (contacted) row.contactedCount += 1 else row.blockedCount += 1
    }
    return out.values
}

internal fun aggregateAsnsFromConnections(
    conns: List<ConnectionTracker>,
    ipInfo: Map<String, IpInfo>,
): Collection<AsnAgg> {
    val out = LinkedHashMap<Pair<Long, String>, AsnAgg>()
    for (c in conns) {
        val info = ipInfo[c.ipAddress] ?: continue
        if (info.asName.isEmpty()) continue
        val day = epochDay(c.timeStamp)
        val row = out.getOrPut(day to info.asName) { AsnAgg(day, info.asName, info.countryCode) }
        if (info.countryCode.isNotEmpty()) row.countryCode = info.countryCode
        if (c.isBlocked) {
            row.blockedCount += 1
        } else {
            row.connectionCount += 1
            row.downloadBytes += c.downloadBytes
            row.uploadBytes += c.uploadBytes
        }
    }
    return out.values
}
