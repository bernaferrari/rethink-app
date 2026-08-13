/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.configure

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListPairShape
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape

enum class RethinkConfigureLayout { GridFour, GridTriad, List }

data class RethinkConfigureEntry(
    val id: String,
    val title: String,
    val icon: @Composable () -> Unit,
    val accent: Color,
    val onClick: () -> Unit,
    val subtitle: String? = null,
    val keywords: List<String> = emptyList(),
)

data class RethinkConfigureSection(
    val title: String,
    val accent: Color,
    val entries: List<RethinkConfigureEntry>,
    val layout: RethinkConfigureLayout,
    val subtitle: String? = null,
)

data class RethinkConfigureSearchEntry(
    val entry: RethinkConfigureEntry,
    val path: String,
)

data class RethinkConfigureStrings(
    val title: String,
    val searchHint: String,
    val openSearch: String,
    val closeSearch: String,
    val clearSearch: String,
    val noResultsTitle: String,
    val noResultsSubtitle: String,
)

@Composable
fun RethinkConfigureScreen(
    sections: List<RethinkConfigureSection>,
    searchEntries: List<RethinkConfigureSearchEntry>,
    strings: RethinkConfigureStrings,
    searchOpen: Boolean,
    query: String,
    onSearchOpenChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Optional appearance block shown at the top of Settings (theme mode + color swatches). */
    appearanceContent: (@Composable () -> Unit)? = null,
) {
    val scrollBehavior = if (searchOpen) TopAppBarDefaults.pinnedScrollBehavior() else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val normalizedQuery = query.normalize()
    val results = if (normalizedQuery.isBlank()) emptyList() else searchEntries
        .filter { candidate -> candidate.matches(normalizedQuery) }
        .sortedBy { it.entry.title }
        .take(12)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (searchOpen) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { onSearchOpenChange(false) }) { Icon(MaterialSymbols.AutoMirrored.Filled.ArrowBack, strings.closeSearch) } },
                    title = {
                        TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth().padding(vertical = SharedDimensions.spacingSm),
                            singleLine = true,
                            placeholder = { Text(strings.searchHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            shape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    },
                    actions = { if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(MaterialSymbols.Filled.Close, strings.clearSearch) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                    scrollBehavior = scrollBehavior,
                )
            } else {
                LargeTopAppBar(
                    title = { Text(strings.title) },
                    actions = { IconButton(onClick = { onSearchOpenChange(true) }) { Icon(MaterialSymbols.Filled.Search, strings.openSearch) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize().padding(padding),
            // The root navigation owns the system/navigation-bar inset. Keep a deliberate tail here
            // as well: the final expressive tile is taller than a list row and needs room to scroll
            // fully above the persistent navigation bar.
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingMd,
                end = SharedDimensions.screenPaddingHorizontal,
                bottom = SharedDimensions.spacing3xl + SharedDimensions.spacingSm,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            if (normalizedQuery.isBlank()) {
                appearanceContent?.let { content ->
                    item { content() }
                }
                sections.forEach { section -> item { ConfigureSection(section) } }
                item { Spacer(Modifier.height(SharedDimensions.spacingSm)) }
            } else {
                item {
                    if (results.isEmpty()) NoResults(strings) else SearchResults(results)
                }
            }
        }
    }
}

@Composable
private fun ConfigureSection(section: RethinkConfigureSection) {
    Column {
        Text(section.title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = section.accent, modifier = Modifier.padding(start = SharedDimensions.spacingLg))
        section.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = SharedDimensions.spacingLg, top = 2.dp)) }
        Spacer(Modifier.height(SharedDimensions.spacingSm))
        when (section.layout) {
            RethinkConfigureLayout.GridFour -> FourGrid(section.entries)
            RethinkConfigureLayout.GridTriad -> TriadGrid(section.entries)
            RethinkConfigureLayout.List -> EntryList(section.entries)
        }
    }
}

@Composable private fun FourGrid(entries: List<RethinkConfigureEntry>) {
    if (entries.size != 4) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        GridRow(entries[0], entries[1], rethinkGroupedListPairShape(true, CardPosition.First), rethinkGroupedListPairShape(false, CardPosition.First))
        GridRow(entries[2], entries[3], rethinkGroupedListPairShape(true, CardPosition.Last), rethinkGroupedListPairShape(false, CardPosition.Last))
    }
}
@Composable private fun TriadGrid(entries: List<RethinkConfigureEntry>) {
    if (entries.size < 2) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        GridRow(entries[0], entries[1], rethinkGroupedListPairShape(true, CardPosition.First), rethinkGroupedListPairShape(false, CardPosition.First))
        entries.drop(2).forEachIndexed { index, entry ->
            val isLast = index == entries.lastIndex - 2
            GridTile(
                entry,
                rethinkGroupedListShape(if (isLast) CardPosition.Last else CardPosition.Middle),
                Modifier.fillMaxWidth(),
            )
        }
    }
}
@Composable private fun GridRow(first: RethinkConfigureEntry, second: RethinkConfigureEntry, firstShape: Shape, secondShape: Shape) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) { GridTile(first, firstShape, Modifier.weight(1f)); GridTile(second, secondShape, Modifier.weight(1f)) }
}
@Composable private fun GridTile(entry: RethinkConfigureEntry, shape: Shape, modifier: Modifier) {
    Surface(onClick = entry.onClick, shape = shape, color = entry.accent.copy(alpha = 0.22f), modifier = modifier.clip(shape)) {
        Column(Modifier.padding(SharedDimensions.cardPadding), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
            Surface(shape = RoundedCornerShape(SharedDimensions.iconContainerRadius), color = entry.accent, modifier = Modifier.size(SharedDimensions.iconContainerSm)) { androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { entry.icon() } }
            Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}
@Composable private fun EntryList(entries: List<RethinkConfigureEntry>) = Column {
    entries.forEachIndexed { index, entry ->
        RethinkListItem(headline = entry.title, supporting = entry.subtitle, position = cardPositionFor(index, entries.lastIndex), onClick = entry.onClick, leadingContent = {
            Surface(shape = RoundedCornerShape(SharedDimensions.iconContainerRadius), color = entry.accent, modifier = Modifier.size(SharedDimensions.iconContainerSm)) { androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { entry.icon() } }
        })
    }
}
@Composable private fun NoResults(strings: RethinkConfigureStrings) = Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadiusXl), color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(strings.noResultsTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text(strings.noResultsSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable private fun SearchResults(results: List<RethinkConfigureSearchEntry>) = EntryList(results.map { it.entry.copy(subtitle = it.path) })
private fun String.normalize() = lowercase().trim().replace(Regex("\\s+"), " ")
private fun RethinkConfigureSearchEntry.matches(query: String): Boolean {
    val e = entry
    return listOf(e.title, e.subtitle.orEmpty(), path, e.keywords.joinToString(" ")).any { it.normalize().contains(query) }
}
