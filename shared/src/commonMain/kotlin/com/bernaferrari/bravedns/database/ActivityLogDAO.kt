/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Query
import com.bernaferrari.bravedns.util.Constants.Companion.MAX_LOGS

/**
 * One row of the Activity log: a network connection or a DNS lookup, whichever [kind] says.
 * Exactly one of [connection] and [lookup] is set; [ts] and [rowId] are the timeline's sort key.
 */
data class ActivityLogEntry(
    val kind: Int,
    val ts: Long,
    val rowId: Long,
    @Embedded(prefix = "c_") val connection: ConnectionTracker?,
    @Embedded(prefix = "d_") val lookup: DnsLog?,
) {
    /** Stable across both tables (their ids overlap): the list key and the wide pane's selection. */
    val key: String get() = (if (kind == KIND_CONNECTION) "c" else "d") + rowId

    companion object {
        const val KIND_CONNECTION = 0
        const val KIND_LOOKUP = 1
    }
}

/** An app in the log's app filter: one uid (names collide, uids don't), with its entry count. */
data class ActivityAppCount(
    val uid: Int,
    val appName: String,
    val packageName: String,
    val count: Int,
)

/** Which entries the Activity log lists. [rules] are firewall reasons, so they only match connections. */
data class ActivityLogQuery(
    val kinds: ActivityLogKinds = ActivityLogKinds.All,
    val verdict: ActivityVerdict = ActivityVerdict.All,
    val rules: Set<String> = emptySet(),
    val search: String = "",
    /** One app's entries only. */
    val uid: Int? = null,
)

enum class ActivityLogKinds(val connections: Boolean, val lookups: Boolean) {
    All(true, true),
    Connections(true, false),
    Lookups(false, true),
}

/**
 * Allowed and Blocked mean what each per-type log means by them: a DNS lookup counts as allowed only
 * when no blocklist matched it either (the DNS log keeps "maybe blocked" lookups out of Allowed).
 */
enum class ActivityVerdict(internal val sql: Int) { All(-1), Allowed(0), Blocked(1) }

/*
 * The timeline is one compound SELECT: connections UNION ALL lookups, each arm padded with NULLs
 * for the other table's columns, ordered by time. Each arm's own index on its time column (or on
 * (uid, time) for one app) already yields rows in (ts DESC, rowId DESC) order, so SQLite merges
 * the two arms instead of sorting, and stops at the page Room asks for (ActivityTimelineQueryPlanTest).
 *
 * Filters are bound, not spliced: a disabled arm and a reason filter on lookups are constant
 * terms SQLite evaluates once before scanning, so a one-kind log never walks the other table.
 */

private const val CONNECTION_COLUMNS =
    "id AS c_id, appName AS c_appName, uid AS c_uid, packageName AS c_packageName, usrId AS c_usrId, " +
        "ipAddress AS c_ipAddress, port AS c_port, protocol AS c_protocol, isBlocked AS c_isBlocked, " +
        "blockedByRule AS c_blockedByRule, blocklists AS c_blocklists, proxyDetails AS c_proxyDetails, " +
        "flag AS c_flag, dnsQuery AS c_dnsQuery, timeStamp AS c_timeStamp, connId AS c_connId, " +
        "downloadBytes AS c_downloadBytes, uploadBytes AS c_uploadBytes, duration AS c_duration, " +
        "synack AS c_synack, rpid AS c_rpid, message AS c_message, connType AS c_connType"

private const val NO_CONNECTION_COLUMNS =
    "NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, " +
        "NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL"

private const val LOOKUP_COLUMNS =
    "id, uid, appName, packageName, queryStr, time, flag, resolver, latency, ttl, typeName, " +
        "isBlocked, blockLists, serverIP, proxyId, relayIP, responseTime, response, status, dnsType, " +
        "responseIps, resolverId, msg, upstreamBlock, region, isCached, dnssecOk, dnssecValid, " +
        "blockedTarget, isEch"

private const val NO_LOOKUP_COLUMNS =
    "NULL AS d_id, NULL AS d_uid, NULL AS d_appName, NULL AS d_packageName, NULL AS d_queryStr, " +
        "NULL AS d_time, NULL AS d_flag, NULL AS d_resolver, NULL AS d_latency, NULL AS d_ttl, " +
        "NULL AS d_typeName, NULL AS d_isBlocked, NULL AS d_blockLists, NULL AS d_serverIP, " +
        "NULL AS d_proxyId, NULL AS d_relayIP, NULL AS d_responseTime, NULL AS d_response, " +
        "NULL AS d_status, NULL AS d_dnsType, NULL AS d_responseIps, NULL AS d_resolverId, " +
        "NULL AS d_msg, NULL AS d_upstreamBlock, NULL AS d_region, NULL AS d_isCached, " +
        "NULL AS d_dnssecOk, NULL AS d_dnssecValid, NULL AS d_blockedTarget, NULL AS d_isEch"

private const val CONNECTION_FILTERS =
    " AND (:verdict < 0 OR isBlocked = :verdict)" +
        " AND (:anyRule = 0 OR blockedByRule IN (:rules))" +
        " AND (:search = '' OR appName LIKE :search OR ipAddress LIKE :search OR dnsQuery LIKE :search" +
        " OR flag LIKE :search OR proxyDetails LIKE :search OR connId LIKE :search)"

private const val LOOKUP_FILTERS =
    " AND (:verdict < 0 OR (:verdict = 1 AND isBlocked = 1)" +
        " OR (:verdict = 0 AND isBlocked = 0 AND blockLists = ''))" +
        " AND (:search = '' OR queryStr LIKE :search OR responseIps LIKE :search OR appName LIKE :search)"

// Within one millisecond the newer row id goes first; SQLite's merge breaks a full tie towards the
// connection arm. No term may be a constant (such as kind): SQLite would sort for it.
private const val TIMELINE_ORDER = " ORDER BY ts DESC, rowId DESC LIMIT $MAX_LOGS"

/** Every app's connections and lookups, newest first. Kept public for the query-plan test. */
const val ACTIVITY_TIMELINE_QUERY =
    "SELECT 0 AS kind, timeStamp AS ts, id AS rowId, " + CONNECTION_COLUMNS + ", " + NO_LOOKUP_COLUMNS +
        " FROM ConnectionTracker WHERE :connections = 1" + CONNECTION_FILTERS +
        " UNION ALL " +
        "SELECT 1, time, id, " + NO_CONNECTION_COLUMNS + ", " + LOOKUP_COLUMNS +
        " FROM DNSLogs WHERE :lookups = 1 AND :anyRule = 0" + LOOKUP_FILTERS +
        TIMELINE_ORDER

/** One app's timeline: `uid = :uid` lets both arms walk their (uid, time) index. */
const val ACTIVITY_TIMELINE_FOR_UID_QUERY =
    "SELECT 0 AS kind, timeStamp AS ts, id AS rowId, " + CONNECTION_COLUMNS + ", " + NO_LOOKUP_COLUMNS +
        " FROM ConnectionTracker WHERE :connections = 1 AND uid = :uid" + CONNECTION_FILTERS +
        " UNION ALL " +
        "SELECT 1, time, id, " + NO_CONNECTION_COLUMNS + ", " + LOOKUP_COLUMNS +
        " FROM DNSLogs WHERE :lookups = 1 AND :anyRule = 0 AND uid = :uid" + LOOKUP_FILTERS +
        TIMELINE_ORDER

private const val ACTIVITY_APPS_QUERY =
    "SELECT uid AS uid, appName AS appName, packageName AS packageName, COUNT(*) AS count FROM (" +
        "SELECT uid, appName, packageName FROM ConnectionTracker WHERE :connections = 1" +
        " AND (:verdict < 0 OR isBlocked = :verdict) AND (:anyRule = 0 OR blockedByRule IN (:rules))" +
        " UNION ALL " +
        "SELECT uid, appName, packageName FROM DNSLogs WHERE :lookups = 1 AND :anyRule = 0" +
        " AND (:verdict < 0 OR (:verdict = 1 AND isBlocked = 1)" +
        " OR (:verdict = 0 AND isBlocked = 0 AND blockLists = ''))" +
        ") WHERE TRIM(appName) != '' GROUP BY uid ORDER BY count DESC"

/** The Activity log: connections and DNS lookups as one timeline, or either alone. */
@Dao
interface ActivityLogDAO {
    @Query(ACTIVITY_TIMELINE_QUERY)
    fun timeline(
        connections: Boolean,
        lookups: Boolean,
        verdict: Int,
        anyRule: Boolean,
        rules: Set<String>,
        search: String,
    ): PagingSource<Int, ActivityLogEntry>

    @Query(ACTIVITY_TIMELINE_FOR_UID_QUERY)
    fun timelineForUid(
        uid: Int,
        connections: Boolean,
        lookups: Boolean,
        verdict: Int,
        anyRule: Boolean,
        rules: Set<String>,
        search: String,
    ): PagingSource<Int, ActivityLogEntry>

    @Query(ACTIVITY_APPS_QUERY)
    suspend fun loggedApps(
        connections: Boolean,
        lookups: Boolean,
        verdict: Int,
        anyRule: Boolean,
        rules: Set<String>,
    ): List<ActivityAppCount>
}

/** The rows [query] asks for, newest first. */
fun ActivityLogDAO.timeline(query: ActivityLogQuery): PagingSource<Int, ActivityLogEntry> {
    val rules = query.effectiveRules()
    val search = query.search.trim().let { if (it.isEmpty()) "" else "%$it%" }
    val uid = query.uid
    return if (uid == null) {
        timeline(query.kinds.connections, query.kinds.lookups, query.verdict.sql, rules.isNotEmpty(), rules, search)
    } else {
        timelineForUid(uid, query.kinds.connections, query.kinds.lookups, query.verdict.sql, rules.isNotEmpty(), rules, search)
    }
}

/** The apps [query]'s kinds, verdict and reasons leave in the log, busiest first (search and app aside). */
suspend fun ActivityLogDAO.loggedApps(query: ActivityLogQuery): List<ActivityAppCount> {
    val rules = query.effectiveRules()
    return loggedApps(query.kinds.connections, query.kinds.lookups, query.verdict.sql, rules.isNotEmpty(), rules)
}

/** Reasons only exist on connections: a lookups-only log ignores them rather than listing nothing. */
private fun ActivityLogQuery.effectiveRules(): Set<String> =
    if (kinds == ActivityLogKinds.Lookups) emptySet() else rules
