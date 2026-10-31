/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.app

import com.bernaferrari.bravedns.database.ConnectionTracker
import com.bernaferrari.bravedns.service.FirewallManager.ConnectionStatus
import com.bernaferrari.bravedns.service.FirewallManager.FirewallStatus
import com.bernaferrari.bravedns.ui.compose.apps.RethinkAppMode
import com.bernaferrari.bravedns.ui.compose.apps.RethinkAppStatusTone

/** What App detail's status line says, from the same flags the controls edit. */
internal enum class AppStatusKind(val tone: RethinkAppStatusTone) {
    TemporarilyAllowed(RethinkAppStatusTone.Temporary),
    NoInternet(RethinkAppStatusTone.Blocked),
    // A block reads as a block (error tone), even on one network: the tertiary "mixed" tone can
    // land near green on some seeds.
    BlockedOnWifi(RethinkAppStatusTone.Blocked),
    BlockedOnMobileData(RethinkAppStatusTone.Blocked),
    CanUseInternet(RethinkAppStatusTone.Allowed),
    TrustedOnly(RethinkAppStatusTone.Blocked),
    SkipsAllFiltering(RethinkAppStatusTone.Partial),
    SkipsUniversalRules(RethinkAppStatusTone.Partial),
    Excluded(RethinkAppStatusTone.Partial),
    Unknown(RethinkAppStatusTone.Partial),
}

/**
 * Temporary access wins (the firewall allows a temporarily allowed app before any app rule), then
 * the handling mode, then the Wi‑Fi (unmetered) / mobile data (metered) flags.
 */
internal fun appStatusKind(status: FirewallStatus, connection: ConnectionStatus, temporarilyAllowed: Boolean): AppStatusKind = when {
    temporarilyAllowed -> AppStatusKind.TemporarilyAllowed
    status == FirewallStatus.ISOLATE -> AppStatusKind.TrustedOnly
    status == FirewallStatus.BYPASS_DNS_FIREWALL -> AppStatusKind.SkipsAllFiltering
    status == FirewallStatus.BYPASS_UNIVERSAL -> AppStatusKind.SkipsUniversalRules
    status == FirewallStatus.EXCLUDE -> AppStatusKind.Excluded
    status == FirewallStatus.UNTRACKED -> AppStatusKind.Unknown
    else -> when (connection) {
        ConnectionStatus.BOTH -> AppStatusKind.NoInternet
        ConnectionStatus.UNMETERED -> AppStatusKind.BlockedOnWifi
        ConnectionStatus.METERED -> AppStatusKind.BlockedOnMobileData
        ConnectionStatus.ALLOW -> AppStatusKind.CanUseInternet
    }
}

internal fun FirewallStatus.toAppMode(): RethinkAppMode = when (this) {
    FirewallStatus.ISOLATE -> RethinkAppMode.TrustedOnly
    FirewallStatus.BYPASS_DNS_FIREWALL -> RethinkAppMode.SkipAllFiltering
    FirewallStatus.BYPASS_UNIVERSAL -> RethinkAppMode.SkipUniversalRules
    FirewallStatus.EXCLUDE -> RethinkAppMode.Excluded
    FirewallStatus.NONE, FirewallStatus.UNTRACKED -> RethinkAppMode.Normal
}

internal fun RethinkAppMode.toFirewallStatus(): FirewallStatus = when (this) {
    RethinkAppMode.Normal -> FirewallStatus.NONE
    RethinkAppMode.TrustedOnly -> FirewallStatus.ISOLATE
    RethinkAppMode.SkipAllFiltering -> FirewallStatus.BYPASS_DNS_FIREWALL
    RethinkAppMode.SkipUniversalRules -> FirewallStatus.BYPASS_UNIVERSAL
    RethinkAppMode.Excluded -> FirewallStatus.EXCLUDE
}

/**
 * The firewall write for choosing [target] in the Special handling sheet, the same as the radio
 * group always did: entering a mode clears the network flags (remembering them in [baseline]),
 * and Normal restores [baseline]. Null when nothing would change.
 */
internal fun modeChange(
    current: FirewallStatus,
    connection: ConnectionStatus,
    baseline: ConnectionStatus,
    target: RethinkAppMode,
): Pair<FirewallStatus, ConnectionStatus>? {
    val targetStatus = target.toFirewallStatus()
    if (current == targetStatus) return null
    return if (target == RethinkAppMode.Normal) {
        // Untracked apps have no mode to leave.
        if (current == FirewallStatus.UNTRACKED) null else FirewallStatus.NONE to baseline
    } else {
        targetStatus to ConnectionStatus.ALLOW
    }?.takeUnless { it.first == current && it.second == connection }
}

/** Flip the Wi‑Fi (unmetered) flag, keeping mobile data's. */
internal fun ConnectionStatus.toggledWifi(): ConnectionStatus = when (this) {
    ConnectionStatus.UNMETERED -> ConnectionStatus.ALLOW
    ConnectionStatus.BOTH -> ConnectionStatus.METERED
    ConnectionStatus.METERED -> ConnectionStatus.BOTH
    ConnectionStatus.ALLOW -> ConnectionStatus.UNMETERED
}

/** Flip the mobile data (metered) flag, keeping Wi‑Fi's. */
internal fun ConnectionStatus.toggledMobile(): ConnectionStatus = when (this) {
    ConnectionStatus.METERED -> ConnectionStatus.ALLOW
    ConnectionStatus.UNMETERED -> ConnectionStatus.BOTH
    ConnectionStatus.BOTH -> ConnectionStatus.UNMETERED
    ConnectionStatus.ALLOW -> ConnectionStatus.METERED
}

internal val ConnectionStatus.wifiBlocked get() = this == ConnectionStatus.UNMETERED || this == ConnectionStatus.BOTH
internal val ConnectionStatus.mobileBlocked get() = this == ConnectionStatus.METERED || this == ConnectionStatus.BOTH

/** One Recent activity row: the newest connection to a destination with one verdict. */
internal data class RecentActivityRow(
    val destination: String,
    val blocked: Boolean,
    val latest: ConnectionTracker,
    val count: Int,
) {
    val key: String get() = "${if (blocked) "b" else "a"}:$destination"
}

/**
 * Fold an app's connections (any order) into one row per destination and verdict: blocked rows
 * first, newest first within each, at most [limit] rows. The destination is the looked-up domain
 * (trailing dot dropped) when known, else the IP.
 */
internal fun foldRecentActivity(connections: List<ConnectionTracker>, limit: Int = RECENT_ACTIVITY_ROWS): List<RecentActivityRow> =
    connections
        .groupBy { (it.dnsQuery?.trimEnd('.')?.takeIf(String::isNotBlank) ?: it.ipAddress) to it.isBlocked }
        .filterKeys { (destination, _) -> destination.isNotBlank() }
        .map { (key, rows) ->
            RecentActivityRow(key.first, key.second, rows.maxBy { it.timeStamp }, rows.size)
        }
        .sortedWith(compareByDescending<RecentActivityRow> { it.blocked }.thenByDescending { it.latest.timeStamp })
        .take(limit)

internal const val RECENT_ACTIVITY_ROWS = 8

/** Whole minutes left (rounded up) until [expiry], or null once it has passed (or there is none). */
internal fun minutesLeft(expiry: Long, now: Long): Int? =
    if (expiry <= now) null else ((expiry - now + 59_999L) / 60_000L).toInt()
