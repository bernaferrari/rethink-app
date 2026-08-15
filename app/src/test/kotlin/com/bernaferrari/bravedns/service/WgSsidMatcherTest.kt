/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.data.SsidItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WgSsidMatcherTest {

    private fun storage(vararg items: Pair<String, SsidItem.SsidType>): String {
        return SsidItem.toStorageList(items.map { SsidItem(it.first, it.second) })
    }

    @Test
    fun `empty list matches all`() {
        assertTrue(WgSsidMatcher.matchesSsidList("", "AnySSID"))
        assertTrue(WgSsidMatcher.matchesSsidList("   ", "AnySSID"))
    }

    @Test
    fun `exact match is case insensitive`() {
        val list = storage("HomeNet" to SsidItem.SsidType.EQUAL_EXACT)
        assertTrue(WgSsidMatcher.matchesSsidList(list, "HomeNet"))
        assertTrue(WgSsidMatcher.matchesSsidList(list, "homenet"))
        assertFalse(WgSsidMatcher.matchesSsidList(list, "Office"))
    }

    @Test
    fun `wildcard prefix and question mark`() {
        assertTrue(
            WgSsidMatcher.matchesSsidList(
                storage("WiFi*" to SsidItem.SsidType.EQUAL_WILDCARD),
                "WiFi123"
            )
        )
        assertTrue(WgSsidMatcher.matchesWildcard("Test?", "Test1"))
        assertFalse(WgSsidMatcher.matchesWildcard("Test?", "TestAB"))
    }

    @Test
    fun `not-equal exact excludes matching ssid`() {
        val list = storage("Guest" to SsidItem.SsidType.NOTEQUAL_EXACT)
        assertFalse(WgSsidMatcher.matchesSsidList(list, "Guest"))
        assertTrue(WgSsidMatcher.matchesSsidList(list, "Home"))
    }

    @Test
    fun `not-equal wildcard excludes matching ssid`() {
        val list = storage("Corp*" to SsidItem.SsidType.NOTEQUAL_WILDCARD)
        assertFalse(WgSsidMatcher.matchesSsidList(list, "CorpGuest"))
        assertTrue(WgSsidMatcher.matchesSsidList(list, "Home"))
    }

    @Test
    fun `not-equal plus equal requires equal after exclusion`() {
        val list = storage(
            "Guest" to SsidItem.SsidType.NOTEQUAL_EXACT,
            "Home*" to SsidItem.SsidType.EQUAL_WILDCARD,
        )
        assertFalse(WgSsidMatcher.matchesSsidList(list, "Guest"))
        assertTrue(WgSsidMatcher.matchesSsidList(list, "Home123"))
        assertFalse(WgSsidMatcher.matchesSsidList(list, "Office"))
    }

    @Test
    fun `malformed storage fails open`() {
        assertTrue(WgSsidMatcher.matchesSsidList("WiFi1##", "WiFi1"))
        assertTrue(WgSsidMatcher.matchesSsidList("not-json", "Home"))
    }

    @Test
    fun `wildcard also matches via contains fallback`() {
        assertTrue(WgSsidMatcher.matchesWildcard("Net", "MyNetwork"))
    }
}
