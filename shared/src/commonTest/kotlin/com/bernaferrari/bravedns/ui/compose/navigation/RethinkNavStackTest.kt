package com.bernaferrari.bravedns.ui.compose.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RethinkNavStackTest {

    @Test
    fun open_sameRoot_pushesWithoutClearing() {
        val current = listOf(RethinkRoute.Configure, RethinkRoute.AppList)
        assertEquals(
            listOf(RethinkRoute.Configure, RethinkRoute.AppList, RethinkRoute.AppInfo(5)),
            stackAfterOpen(current, RethinkRoute.AppInfo(5)),
        )
    }

    @Test
    fun open_sameRoot_popsBackToExistingInsteadOfDuplicating() {
        val current = listOf(
            RethinkRoute.Configure,
            RethinkRoute.AppList,
            RethinkRoute.AppInfo(5),
        )
        assertEquals(
            listOf(RethinkRoute.Configure, RethinkRoute.AppList),
            stackAfterOpen(current, RethinkRoute.AppList),
        )
    }

    @Test
    fun open_differentRoot_usesCanonicalStack() {
        assertEquals(
            listOf(RethinkRoute.Configure, RethinkRoute.AppList),
            stackAfterOpen(listOf(RethinkRoute.Home), RethinkRoute.AppList),
        )
        assertEquals(
            listOf(RethinkRoute.Configure, RethinkRoute.AppList),
            stackAfterOpen(listOf(RethinkRoute.Statistics), RethinkRoute.AppList),
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
    fun pop_configureOrStatisticsRoot_goesHome() {
        assertEquals(listOf(RethinkRoute.Home), stackAfterPop(listOf(RethinkRoute.Configure)))
        assertEquals(listOf(RethinkRoute.Home), stackAfterPop(listOf(RethinkRoute.Statistics)))
    }

    @Test
    fun pop_homeRoot_leavesStackForSystem() {
        assertNull(stackAfterPop(listOf(RethinkRoute.Home)))
    }

    @Test
    fun shouldPushExternal_sameNestedRootOnly() {
        assertTrue(shouldPushExternal(RethinkRoute.Configure, RethinkRoute.AppList))
        assertTrue(
            shouldPushExternal(
                RethinkRoute.AppList,
                RethinkRoute.AppInfo(2),
            ),
        )
        assertFalse(shouldPushExternal(RethinkRoute.Home, RethinkRoute.AppList))
        assertFalse(shouldPushExternal(null, RethinkRoute.AppList))
        assertFalse(shouldPushExternal(RethinkRoute.Welcome, RethinkRoute.Home))
    }

    @Test
    fun saveForRoot_onlyWhenDepthGreaterThanOne() {
        assertNull(stackToSaveForRoot(listOf(RethinkRoute.Configure)))
        assertEquals(
            RethinkRootDestination.Configure to listOf(RethinkRoute.Configure, RethinkRoute.AppList),
            stackToSaveForRoot(listOf(RethinkRoute.Configure, RethinkRoute.AppList)),
        )
    }

    @Test
    fun openFromApp_sameRoot_pushes() {
        assertEquals(
            listOf(RethinkRoute.Configure, RethinkRoute.AppList, RethinkRoute.AppInfo(5)),
            stackAfterOpenFromApp(
                listOf(RethinkRoute.Configure, RethinkRoute.AppList),
                RethinkRoute.AppInfo(5),
            ),
        )
    }

    @Test
    fun openFromApp_differentRoot_usesCanonicalStack() {
        assertEquals(
            listOf(RethinkRoute.Configure, RethinkRoute.AppList),
            stackAfterOpenFromApp(listOf(RethinkRoute.Home), RethinkRoute.AppList),
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
