/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.configure

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/** Browse (appearance + sections) unless search is open with a non-blank query. */
fun rethinkConfigureShowsBrowse(searchOpen: Boolean, query: String): Boolean =
    !searchOpen || query.normalizeConfigureQuery().isBlank()

fun rethinkConfigureOnSearchOpenChange(
    open: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
) {
    if (!open) onQueryChange("")
    onSearchOpenChange(open)
}

fun rethinkConfigureFilterResults(
    searchEntries: List<RethinkConfigureSearchEntry>,
    query: String,
    limit: Int = 12,
): List<RethinkConfigureSearchEntry> {
    val normalized = query.normalizeConfigureQuery()
    if (normalized.isBlank()) return emptyList()
    return searchEntries
        .filter { it.matchesQuery(normalized) }
        .distinctBy(::rethinkConfigureSearchDedupKey)
        .sortedBy { it.entry.title }
        .take(limit)
}

/** Collapse host + deep-index duplicates that share a visible title and path. */
fun rethinkConfigureSearchDedupKey(entry: RethinkConfigureSearchEntry): String =
    "${entry.entry.title}\u0000${entry.path}".normalizeConfigureQuery()

fun String.normalizeConfigureQuery(): String = lowercase().trim().replace(Regex("\\s+"), " ")

fun RethinkConfigureSearchEntry.matchesQuery(query: String): Boolean {
    val needle = query.normalizeConfigureQuery()
    if (needle.isEmpty()) return false
    val e = entry
    return listOf(e.title, e.subtitle.orEmpty(), path, e.keywords.joinToString(" "))
        .any { it.normalizeConfigureQuery().contains(needle) }
}

/** Path, plus subtitle or a keyword when that is the only visible place the query matched. */
fun rethinkConfigureSearchSupporting(entry: RethinkConfigureSearchEntry, query: String): String {
    val needle = query.normalizeConfigureQuery()
    val path = entry.path
    if (needle.isEmpty()) return path
    val title = entry.entry.title
    val subtitle = entry.entry.subtitle.orEmpty()
    if (title.normalizeConfigureQuery().contains(needle) || path.normalizeConfigureQuery().contains(needle)) {
        return path
    }
    if (subtitle.isNotBlank() && subtitle.normalizeConfigureQuery().contains(needle)) {
        return "$path · $subtitle"
    }
    val keyword = entry.entry.keywords.firstOrNull { it.normalizeConfigureQuery().contains(needle) }
    return if (keyword != null) "$path · $keyword" else path
}

fun highlightConfigureQuery(text: String, query: String, matchStyle: SpanStyle): AnnotatedString {
    val ranges = configureQueryHighlightRanges(text, query)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        ranges.forEach { range ->
            addStyle(matchStyle, range.first, range.last + 1)
        }
    }
}

/** Same spans as [matchesQuery]: every case-insensitive substring occurrence of the query. */
fun configureQueryHighlightRanges(text: String, query: String): List<IntRange> {
    val needle = query.normalizeConfigureQuery()
    if (text.isEmpty() || needle.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var start = 0
    while (start <= text.length - needle.length) {
        val index = text.indexOf(needle, startIndex = start, ignoreCase = true)
        if (index < 0) break
        ranges += index until (index + needle.length)
        start = index + needle.length
    }
    return ranges
}
