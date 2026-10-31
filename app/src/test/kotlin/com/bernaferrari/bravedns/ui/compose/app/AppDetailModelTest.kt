package com.bernaferrari.bravedns.ui.compose.app

import com.bernaferrari.bravedns.database.ConnectionTracker
import com.bernaferrari.bravedns.service.FirewallManager.ConnectionStatus
import com.bernaferrari.bravedns.service.FirewallManager.FirewallStatus
import com.bernaferrari.bravedns.ui.compose.apps.RethinkAppMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppDetailModelTest {
    @Test
    fun statusFollowsTemporaryAccessThenModeThenNetworks() {
        assertEquals(AppStatusKind.TemporarilyAllowed, appStatusKind(FirewallStatus.NONE, ConnectionStatus.BOTH, temporarilyAllowed = true))
        assertEquals(AppStatusKind.NoInternet, appStatusKind(FirewallStatus.NONE, ConnectionStatus.BOTH, false))
        assertEquals(AppStatusKind.BlockedOnWifi, appStatusKind(FirewallStatus.NONE, ConnectionStatus.UNMETERED, false))
        assertEquals(AppStatusKind.BlockedOnMobileData, appStatusKind(FirewallStatus.NONE, ConnectionStatus.METERED, false))
        assertEquals(AppStatusKind.CanUseInternet, appStatusKind(FirewallStatus.NONE, ConnectionStatus.ALLOW, false))
        assertEquals(AppStatusKind.TrustedOnly, appStatusKind(FirewallStatus.ISOLATE, ConnectionStatus.ALLOW, false))
        assertEquals(AppStatusKind.Excluded, appStatusKind(FirewallStatus.EXCLUDE, ConnectionStatus.ALLOW, false))
        assertEquals(AppStatusKind.SkipsAllFiltering, appStatusKind(FirewallStatus.BYPASS_DNS_FIREWALL, ConnectionStatus.ALLOW, false))
        assertEquals(AppStatusKind.SkipsUniversalRules, appStatusKind(FirewallStatus.BYPASS_UNIVERSAL, ConnectionStatus.ALLOW, false))
        assertEquals(AppStatusKind.Unknown, appStatusKind(FirewallStatus.UNTRACKED, ConnectionStatus.ALLOW, false))
    }

    @Test
    fun networkTogglesFlipOnlyTheirOwnFlag() {
        assertEquals(ConnectionStatus.UNMETERED, ConnectionStatus.ALLOW.toggledWifi())
        assertEquals(ConnectionStatus.BOTH, ConnectionStatus.METERED.toggledWifi())
        assertEquals(ConnectionStatus.METERED, ConnectionStatus.BOTH.toggledWifi())
        assertEquals(ConnectionStatus.ALLOW, ConnectionStatus.UNMETERED.toggledWifi())
        assertEquals(ConnectionStatus.METERED, ConnectionStatus.ALLOW.toggledMobile())
        assertEquals(ConnectionStatus.BOTH, ConnectionStatus.UNMETERED.toggledMobile())
        assertEquals(ConnectionStatus.UNMETERED, ConnectionStatus.BOTH.toggledMobile())
        assertEquals(ConnectionStatus.ALLOW, ConnectionStatus.METERED.toggledMobile())
    }

    @Test
    fun modeChangeMatchesTheRadioGroupsWrites() {
        // Entering a mode clears the network flags.
        assertEquals(
            FirewallStatus.EXCLUDE to ConnectionStatus.ALLOW,
            modeChange(FirewallStatus.NONE, ConnectionStatus.METERED, ConnectionStatus.METERED, RethinkAppMode.Excluded),
        )
        // Normal restores the flags the app had before.
        assertEquals(
            FirewallStatus.NONE to ConnectionStatus.METERED,
            modeChange(FirewallStatus.ISOLATE, ConnectionStatus.ALLOW, ConnectionStatus.METERED, RethinkAppMode.Normal),
        )
        // Switching between modes.
        assertEquals(
            FirewallStatus.BYPASS_UNIVERSAL to ConnectionStatus.ALLOW,
            modeChange(FirewallStatus.ISOLATE, ConnectionStatus.ALLOW, ConnectionStatus.ALLOW, RethinkAppMode.SkipUniversalRules),
        )
        assertNull(modeChange(FirewallStatus.ISOLATE, ConnectionStatus.ALLOW, ConnectionStatus.ALLOW, RethinkAppMode.TrustedOnly))
        assertNull(modeChange(FirewallStatus.NONE, ConnectionStatus.BOTH, ConnectionStatus.BOTH, RethinkAppMode.Normal))
        assertNull(modeChange(FirewallStatus.UNTRACKED, ConnectionStatus.ALLOW, ConnectionStatus.ALLOW, RethinkAppMode.Normal))
    }

    @Test
    fun recentActivityFoldsPerDestinationBlockedFirst() {
        fun conn(domain: String?, ip: String, blocked: Boolean, ts: Long) = ConnectionTracker().apply {
            dnsQuery = domain
            ipAddress = ip
            isBlocked = blocked
            timeStamp = ts
        }
        val rows = foldRecentActivity(
            listOf(
                conn("www.google.com.", "1.2.3.4", false, 30),
                conn("www.google.com", "1.2.3.5", false, 40),
                conn("ads.example.com", "5.6.7.8", true, 10),
                conn(null, "9.9.9.9", false, 50),
                conn("www.google.com", "1.2.3.4", true, 5),
            ),
        )
        assertEquals(
            listOf("b:ads.example.com", "b:www.google.com", "a:9.9.9.9", "a:www.google.com"),
            rows.map { it.key },
        )
        assertEquals(2, rows.last().count)
        assertEquals(40L, rows.last().latest.timeStamp)
    }

    @Test
    fun minutesLeftRoundsUpAndEndsAtExpiry() {
        assertEquals(15, minutesLeft(expiry = 15 * 60_000L, now = 0L))
        assertEquals(1, minutesLeft(expiry = 61_000L, now = 59_000L))
        assertNull(minutesLeft(expiry = 1_000L, now = 1_000L))
        assertNull(minutesLeft(expiry = 0L, now = 5L))
    }
}
