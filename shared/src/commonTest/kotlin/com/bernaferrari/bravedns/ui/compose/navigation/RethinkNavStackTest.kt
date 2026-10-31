package com.bernaferrari.bravedns.ui.compose.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RethinkNavStackTest {

    @Test
    fun open_sameRoot_pushesWithoutClearing() {
        val current = listOf(RethinkRoute.Configure, RethinkRoute.FirewallSettings())
        assertEquals(
            listOf(RethinkRoute.Configure, RethinkRoute.FirewallSettings(), RethinkRoute.UniversalFirewall),
            stackAfterOpen(current, RethinkRoute.UniversalFirewall),
        )
    }

    @Test
    fun open_sameRoot_popsBackToExistingInsteadOfDuplicating() {
        val current = listOf(
            RethinkRoute.Configure,
            RethinkRoute.FirewallSettings(),
            RethinkRoute.UniversalFirewall,
        )
        assertEquals(
            listOf(RethinkRoute.Configure, RethinkRoute.FirewallSettings()),
            stackAfterOpen(current, RethinkRoute.FirewallSettings()),
        )
    }

    @Test
    fun open_differentRoot_usesCanonicalStack() {
        assertEquals(
            listOf(RethinkRoute.AppList, RethinkRoute.AppInfo(5)),
            stackAfterOpen(listOf(RethinkRoute.Home), RethinkRoute.AppInfo(5)),
        )
        assertEquals(
            listOf(RethinkRoute.Activity, RethinkRoute.NetworkLogs("example.com")),
            stackAfterOpen(listOf(RethinkRoute.Configure), RethinkRoute.NetworkLogs("example.com")),
        )
    }

    @Test
    fun tabs_areHomeAppsActivitySettings() {
        assertEquals(
            listOf(RethinkRoute.Home, RethinkRoute.AppList, RethinkRoute.Activity, RethinkRoute.Configure),
            RethinkRootDestination.entries.map { it.route },
        )
    }

    @Test
    fun statsAndUnfilteredLogs_openTheActivityTab() {
        assertEquals(listOf(RethinkRoute.Activity), canonicalStackFor(RethinkRoute.Statistics))
        assertEquals(listOf(RethinkRoute.Activity), canonicalStackFor(RethinkRoute.NetworkLogs()))
        assertEquals(RethinkActivityView.Insights, RethinkRoute.Statistics.activityView())
        assertEquals(RethinkActivityView.Log, RethinkRoute.NetworkLogs().activityView())
        assertNull(RethinkRoute.NetworkLogs("clock").activityView())
        // One app's log is a screen pushed on the Activity tab, not the tab's own log.
        assertNull(RethinkRoute.NetworkLogs(uid = 10_123, appName = "Clock").activityView())
        assertEquals(RethinkRootDestination.Activity, RethinkRoute.Statistics.tabRoot())
        assertEquals(RethinkRootDestination.Apps, RethinkRoute.AppList.tabRoot())
        assertNull(RethinkRoute.AppInfo(1).tabRoot())
        assertNull(RethinkRoute.Welcome.tabRoot())
    }

    @Test
    fun openFromApp_tabRoot_resetsThatTab() {
        assertEquals(
            listOf(RethinkRoute.Activity),
            stackAfterOpenFromApp(listOf(RethinkRoute.Activity, RethinkRoute.NetworkLogs("x")), RethinkRoute.Statistics),
        )
    }

    @Test
    fun open_tabOrFullscreenRoot_replacesWithCanonical() {
        assertEquals(
            listOf(RethinkRoute.Home),
            stackAfterOpen(listOf(RethinkRoute.Welcome), RethinkRoute.Home),
        )
        assertEquals(
            listOf(RethinkRoute.Configure),
            stackAfterOpen(listOf(RethinkRoute.Configure, RethinkRoute.AppList), RethinkRoute.Configure),
        )
    }

    @Test
    fun pop_removesLastWhenStackHasMoreThanOne() {
        assertEquals(
            listOf(RethinkRoute.Configure, RethinkRoute.AppList),
            stackAfterPop(
                listOf(RethinkRoute.Configure, RethinkRoute.AppList, RethinkRoute.AppInfo(1)),
            ),
        )
    }

    @Test
    fun pop_otherTabRoots_goHome() {
        assertEquals(listOf(RethinkRoute.Home), stackAfterPop(listOf(RethinkRoute.Configure)))
        assertEquals(listOf(RethinkRoute.Home), stackAfterPop(listOf(RethinkRoute.Statistics)))
        assertEquals(listOf(RethinkRoute.Home), stackAfterPop(listOf(RethinkRoute.AppList)))
        assertEquals(listOf(RethinkRoute.Home), stackAfterPop(listOf(RethinkRoute.Activity)))
    }

    @Test
    fun pop_homeRoot_leavesStackForSystem() {
        assertNull(stackAfterPop(listOf(RethinkRoute.Home)))
    }

    @Test
    fun shouldPushExternal_sameNestedRootOnly() {
        assertTrue(shouldPushExternal(RethinkRoute.Configure, RethinkRoute.FirewallSettings()))
        assertTrue(
            shouldPushExternal(
                RethinkRoute.AppList,
                RethinkRoute.AppInfo(2),
            ),
        )
        assertFalse(shouldPushExternal(RethinkRoute.Configure, RethinkRoute.AppList))
        assertFalse(shouldPushExternal(RethinkRoute.Home, RethinkRoute.AppList))
        assertFalse(shouldPushExternal(null, RethinkRoute.AppList))
        assertFalse(shouldPushExternal(RethinkRoute.Welcome, RethinkRoute.Home))
    }

    @Test
    fun openFromApp_sameRoot_pushes() {
        assertEquals(
            listOf(RethinkRoute.AppList, RethinkRoute.AppInfo(5)),
            stackAfterOpenFromApp(
                listOf(RethinkRoute.AppList),
                RethinkRoute.AppInfo(5),
            ),
        )
    }

    @Test
    fun openFromApp_differentRoot_usesCanonicalStack() {
        assertEquals(
            listOf(RethinkRoute.AppList, RethinkRoute.AppInfo(5)),
            stackAfterOpenFromApp(listOf(RethinkRoute.Home), RethinkRoute.AppInfo(5)),
        )
    }

    @Test
    fun openFromApp_sameRootFullscreen_pushesInsteadOfReplacing() {
        assertEquals(
            listOf(RethinkRoute.Home, RethinkRoute.AppLock),
            stackAfterOpenFromApp(listOf(RethinkRoute.Home), RethinkRoute.AppLock),
        )
    }
}
