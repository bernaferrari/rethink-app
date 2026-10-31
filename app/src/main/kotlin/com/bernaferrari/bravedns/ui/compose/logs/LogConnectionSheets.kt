/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.bernaferrari.bravedns.database.ConnectionTracker
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.database.DnsLog
import com.bernaferrari.bravedns.database.DnsLogRepository
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.ui.compose.dns.dnsLogModel

/**
 * What a log row, a pane or an Insights row opens the Connection sheet for. Rows hand over the
 * entry they show; Insights hands over the newest entry behind an aggregate ([findLogSheetTarget]).
 */
@Immutable
sealed interface LogSheetTarget {
    data class Connection(val ct: ConnectionTracker) : LogSheetTarget
    data class Lookup(val log: DnsLog) : LogSheetTarget
    /** Nothing logged is left for an aggregate row (purged): the target alone, for every app. */
    data class Aggregate(val request: ConnectionSheetRequest) : LogSheetTarget
}

/** The Connection sheet for [target], built from the same row model the log draws. */
@Composable
fun LogConnectionSheet(
    target: LogSheetTarget,
    eventLogger: EventLogger,
    navigation: ConnectionSheetNavigation,
    onDismiss: () -> Unit,
    loadFavIcon: Boolean = false,
) {
    val request = when (target) {
        is LogSheetTarget.Connection -> {
            val model = connectionLogModel(target.ct)
            target.ct.toConnectionSheetRequest(model.timeLabel, model.typeLabel, model.details)
        }
        is LogSheetTarget.Lookup -> {
            val model = dnsLogModel(target.log, loadFavIcon, isRethinkDns = false, onShowBlocklist = {})
            target.log.toConnectionSheetRequest(model.timeLabel, model.typeLabel, model.details)
        }
        is LogSheetTarget.Aggregate -> target.request
    }
    ConnectionSheetHost(
        request = request,
        eventLogger = eventLogger,
        onDismiss = onDismiss,
        navigation = navigation,
    )
}

/**
 * The newest connection to [domain] or [ip] (else the newest lookup of [domain]) with this
 * verdict since [since], so a ranked Insights row opens a real verdict and its real fix. Falls back
 * to an all-apps [LogSheetTarget.Aggregate] when the log no longer holds one.
 */
suspend fun findLogSheetTarget(
    connections: ConnectionTrackerRepository,
    lookups: DnsLogRepository,
    domain: String?,
    ip: String?,
    blocked: Boolean,
    since: Long,
    /** "All apps": the aggregate's subtitle, since no one app stands for it. */
    allAppsLabel: String,
): LogSheetTarget {
    connections.latestConnectionTo(domain.orEmpty(), ip.orEmpty(), blocked, since)
        ?.let { return LogSheetTarget.Connection(it) }
    if (!domain.isNullOrBlank()) {
        lookups.latestLookupOf(domain, blocked, since)?.let { return LogSheetTarget.Lookup(it) }
    }
    return LogSheetTarget.Aggregate(
        ConnectionSheetRequest(
            uid = -1,
            appName = allAppsLabel,
            domain = domain?.takeIf { it.isNotBlank() },
            ipAddress = ip?.takeIf { it.isNotBlank() },
            timeLabel = "",
            isBlocked = blocked,
            isDnsLookup = !domain.isNullOrBlank(),
        ),
    )
}
