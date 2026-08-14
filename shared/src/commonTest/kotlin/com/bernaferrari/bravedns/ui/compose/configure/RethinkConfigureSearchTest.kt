package com.bernaferrari.bravedns.ui.compose.configure

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RethinkConfigureSearchTest {

    private val dns = entry("dns", "DNS", "resolver", listOf("dns", "blocklist"))
    private val apps = entry("apps", "Apps", "application list", listOf("apps"))
    private val catalog = listOf(
        RethinkConfigureSearchEntry(dns, "Protection > DNS"),
        RethinkConfigureSearchEntry(apps, "Protection > Apps"),
    )

    @Test
    fun browse_whenSearchClosed_evenIfQueryRemains() {
        assertTrue(rethinkConfigureShowsBrowse(searchOpen = false, query = ""))
        assertTrue(rethinkConfigureShowsBrowse(searchOpen = false, query = "dns"))
    }

    @Test
    fun browse_whenSearchOpenWithBlankQuery() {
        assertTrue(rethinkConfigureShowsBrowse(searchOpen = true, query = ""))
        assertTrue(rethinkConfigureShowsBrowse(searchOpen = true, query = "   "))
    }

    @Test
    fun results_whenSearchOpenWithQuery() {
        assertFalse(rethinkConfigureShowsBrowse(searchOpen = true, query = "dns"))
        val matches = rethinkConfigureFilterResults(catalog, "dns")
        assertEquals(listOf("dns"), matches.map { it.entry.id })
        assertTrue(matches.single().matchesQuery("dns"))
        assertFalse(catalog.first { it.entry.id == "apps" }.matchesQuery("dns"))
    }

    @Test
    fun highlight_marksEachOccurrence() {
        val ranges = configureQueryHighlightRanges("DNS over HTTPS", "dns")
        assertEquals(listOf(0..2), ranges)
        val multi = configureQueryHighlightRanges("Protection > DNS", "dns")
        assertEquals(listOf(13..15), multi)
    }

    @Test
    fun highlight_doesNotUseSubsequenceLetters() {
        val ranges = configureQueryHighlightRanges("Galaxy Store", "gs")
        assertEquals(emptyList(), ranges)
    }

    @Test
    fun highlight_marksEverySubstringOccurrence() {
        val ranges = configureQueryHighlightRanges("DNS and DNS", "dns")
        assertEquals(listOf(0..2, 8..10), ranges)
    }

    @Test
    fun results_deduplicateByTitleAndPath() {
        val duplicate = RethinkConfigureSearchEntry(
            entry("top.apps", "Apps", "application list", listOf("apps")),
            "Protection > Apps",
        )
        val matches = rethinkConfigureFilterResults(catalog + duplicate, "apps")
        assertEquals(listOf("apps"), matches.map { it.entry.id })
    }

    @Test
    fun supporting_appendsVisibleMatchWhenTitleAndPathDoNotMatch() {
        val entry = RethinkConfigureSearchEntry(
            entry("apps", "Apps", "application list", listOf("application", "app list")),
            "Protection > Apps",
        )
        assertEquals("Protection > Apps", rethinkConfigureSearchSupporting(entry, "apps"))
        assertEquals(
            "Protection > Apps · application list",
            rethinkConfigureSearchSupporting(entry, "application"),
        )
        assertEquals(
            "Protection > Apps · app list",
            rethinkConfigureSearchSupporting(entry, "app list"),
        )
    }

    @Test
    fun close_clearsQuery_andLeavesSearchClosed() {
        var open = true
        var query = "dns"
        rethinkConfigureOnSearchOpenChange(
            open = false,
            onSearchOpenChange = { open = it },
            onQueryChange = { query = it },
        )
        assertFalse(open)
        assertEquals("", query)
        assertTrue(rethinkConfigureShowsBrowse(searchOpen = open, query = query))
    }

    private fun entry(
        id: String,
        title: String,
        subtitle: String,
        keywords: List<String>,
    ) = RethinkConfigureEntry(
        id = id,
        title = title,
        icon = {},
        accent = Color.Unspecified,
        onClick = {},
        subtitle = subtitle,
        keywords = keywords,
    )
}
