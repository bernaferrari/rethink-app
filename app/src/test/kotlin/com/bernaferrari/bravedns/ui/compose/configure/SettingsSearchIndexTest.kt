package com.bernaferrari.bravedns.ui.compose.configure

import com.bernaferrari.bravedns.ui.compose.navigation.RethinkRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * App-side behavior of [SettingsSearchDestination]; the search filter/highlight logic
 * itself is tested in :shared commonTest (RethinkConfigureSearchTest).
 */
class SettingsSearchIndexTest {

    @Test
    fun destinations_openTheirScreen_withTheFocusKey() {
        assertEquals(RethinkRoute.FirewallSettings("Rule #3"), SettingsSearchDestination.Firewall("Rule #3").route())
        assertEquals(RethinkRoute.LoggingSettings("general_logs"), SettingsSearchDestination.Logging("general_logs").route())
        assertEquals(RethinkRoute.MiscSettings("general_autostart"), SettingsSearchDestination.General("general_autostart").route())
        assertEquals(RethinkRoute.DnsAdvanced("dns_advanced_default_dns"), SettingsSearchDestination.Dns("dns_advanced_default_dns").route())
        assertEquals(RethinkRoute.DnsDetail("dns_mode_rethink"), SettingsSearchDestination.Dns("dns_mode_rethink").route())
        assertEquals(RethinkRoute.AppearanceSettings, SettingsSearchDestination.Appearance.route())
        assertEquals(RethinkRoute.AppLockSettings, SettingsSearchDestination.AppLock.route())
        assertEquals(RethinkRoute.Diagnostics, SettingsSearchDestination.Diagnostics.route())
        assertEquals(RethinkRoute.AdvancedSettings, SettingsSearchDestination.Developer.route())
    }

    @Test
    fun inPlaceDestinations_haveNoRoute() {
        // Notifications opens Android's settings; Backup & restore is a sheet over the hub.
        assertNull(SettingsSearchDestination.Notifications.route())
        assertNull(SettingsSearchDestination.Backup.route())
    }
}
