/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.rpnproxy

import com.bernaferrari.bravedns.data.SsidItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RpnSsidControllerTest {

    private val matcher = RpnSsidController()

    private fun storage(vararg items: Pair<String, SsidItem.SsidType>): String {
        return SsidItem.toStorageList(items.map { SsidItem(it.first, it.second) })
    }

    @Test
    fun `empty policy matches any ssid`() {
        assertTrue(matcher.matchesSsidList("", "Home"))
        assertTrue(matcher.matchesSsidList("", ""))
    }

    @Test
    fun `empty current ssid never matches a non-empty policy`() {
        assertFalse(matcher.matchesSsidList("Home", ""))
        assertFalse(matcher.matchesSsidList(storage("Office" to SsidItem.SsidType.EQUAL_EXACT), ""))
    }

    @Test
    fun `unparseable csv policy fails open`() {
        assertTrue(matcher.matchesSsidList("Home,Office,Guest", "Office"))
        assertTrue(matcher.matchesSsidList(" Home WiFi , OFFICE ", "office"))
        assertTrue(matcher.matchesSsidList("Home,Office", "Off"))
    }

    @Test
    fun `exact equal matches ignoring case`() {
        val list = storage("Office" to SsidItem.SsidType.EQUAL_EXACT)
        assertTrue(matcher.matchesSsidList(list, "Office"))
        assertTrue(matcher.matchesSsidList(list, "office"))
        assertFalse(matcher.matchesSsidList(list, "Home"))
        assertFalse(matcher.matchesSsidList(list, "Off"))
    }

    @Test
    fun `wildcard equal matches prefix and contains`() {
        val list = storage("Off" to SsidItem.SsidType.EQUAL_WILDCARD)
        assertTrue(matcher.matchesSsidList(list, "Office"))
        assertTrue(matcher.matchesSsidList(list, "offsite"))
        assertFalse(matcher.matchesSsidList(list, "Home"))
    }

    @Test
    fun `star wildcard matches any sequence`() {
        val list = storage("Home*" to SsidItem.SsidType.EQUAL_WILDCARD)
        assertTrue(matcher.matchesSsidList(list, "Home"))
        assertTrue(matcher.matchesSsidList(list, "Home-5G"))
        assertFalse(matcher.matchesSsidList(list, "Office"))
    }

    @Test
    fun `question wildcard matches a single character`() {
        assertTrue(matcher.matchesWildcard("A?C", "ABC"))
        assertTrue(matcher.matchesWildcard("A?C", "aXc"))
        assertFalse(matcher.matchesWildcard("A?C", "AC"))
    }

    @Test
    fun `empty wildcard operands do not match`() {
        assertFalse(matcher.matchesWildcard("", "Home"))
        assertFalse(matcher.matchesWildcard("Home", ""))
    }

    @Test
    fun `not-equal exact excludes that network and allows others`() {
        val list = storage("Guest" to SsidItem.SsidType.NOTEQUAL_EXACT)
        assertFalse(matcher.matchesSsidList(list, "Guest"))
        assertFalse(matcher.matchesSsidList(list, "guest"))
        assertTrue(matcher.matchesSsidList(list, "Home"))
    }

    @Test
    fun `not-equal wildcard excludes matching names`() {
        val list = storage("Guest*" to SsidItem.SsidType.NOTEQUAL_WILDCARD)
        assertFalse(matcher.matchesSsidList(list, "Guest"))
        assertFalse(matcher.matchesSsidList(list, "Guest-5G"))
        assertTrue(matcher.matchesSsidList(list, "Home"))
    }

    @Test
    fun `not-equal wins over equal when both match`() {
        val list = storage(
            "Office" to SsidItem.SsidType.EQUAL_EXACT,
            "Office" to SsidItem.SsidType.NOTEQUAL_EXACT,
        )
        assertFalse(matcher.matchesSsidList(list, "Office"))
    }

    @Test
    fun `equal and not-equal can combine across different names`() {
        val list = storage(
            "Home" to SsidItem.SsidType.EQUAL_EXACT,
            "Guest" to SsidItem.SsidType.NOTEQUAL_EXACT,
        )
        assertTrue(matcher.matchesSsidList(list, "Home"))
        assertFalse(matcher.matchesSsidList(list, "Guest"))
        assertFalse(matcher.matchesSsidList(list, "Office"))
    }

    @Test
    fun `blank json array fails open`() {
        assertTrue(matcher.matchesSsidList("[]", "Home"))
    }
}
