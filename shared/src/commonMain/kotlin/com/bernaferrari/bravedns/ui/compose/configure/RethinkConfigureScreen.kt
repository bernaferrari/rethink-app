/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.configure

import com.bernaferrari.bravedns.ui.compose.common.RethinkAdaptiveWidth
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.WindowWidthClass
import com.bernaferrari.bravedns.ui.compose.common.isExpanded
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListPairShape
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape

enum class RethinkConfigureLayout { GridFour, GridTriad, List }

data class RethinkConfigureEntry(
    val id: String,
    val title: String,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
    val subtitle: String? = null,
    val keywords: List<String> = emptyList(),
    val accent: Color = Color.Unspecified,
)

data class RethinkConfigureSection(
    val title: String,
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
    /** Optional appearance block shown after the sections (theme mode + color swatches). */
    appearanceContent: (@Composable () -> Unit)? = null,
) {
    val searchScroll = TopAppBarDefaults.pinnedScrollBehavior()
    val browseScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehavior = if (searchOpen) searchScroll else browseScroll
    val focusRequester = remember { FocusRequester() }
    val showBrowse = rethinkConfigureShowsBrowse(searchOpen, query)
    val results = rethinkConfigureFilterResults(searchEntries, query)
    fun setSearchOpen(open: Boolean) {
        rethinkConfigureOnSearchOpenChange(open, onSearchOpenChange, onQueryChange)
    }
    LaunchedEffect(searchOpen) {
        if (searchOpen) focusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (searchOpen) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { setSearchOpen(false) }) {
                            Icon(MaterialSymbols.AutoMirrored.Filled.ArrowBack, strings.closeSearch)
                        }
                    },
                    title = {
                        val searchShape = RoundedCornerShape(SharedDimensions.cornerRadiusLg)
                        TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .clip(searchShape),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            placeholder = {
                                Text(strings.searchHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            shape = searchShape,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        )
                    },
                    actions = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(MaterialSymbols.Filled.Close, strings.clearSearch)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            } else {
                RethinkLargeTopBar(
                    title = strings.title,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { setSearchOpen(true) }) {
                            Icon(MaterialSymbols.Filled.Search, strings.openSearch)
                        }
                    },
                )
            }
        },
    ) { padding ->
        RethinkAdaptiveWidth(Modifier.fillMaxSize().padding(padding)) { widthClass ->
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize(),
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
                if (showBrowse) {
                    // Protection controls lead; theming is a one-time preference and sits last.
                    sections.forEach { section -> item { ConfigureSection(section, widthClass) } }
                    appearanceContent?.let { content ->
                        item { content() }
                    }
                    item { Spacer(Modifier.height(SharedDimensions.spacingSm)) }
                } else {
                    item {
                        if (results.isEmpty()) NoResults(strings) else SearchResults(results, query)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigureSection(section: RethinkConfigureSection, widthClass: WindowWidthClass) {
    Column {
        SectionHeader(section.title)
        section.subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SharedDimensions.spacingLg, top = SharedDimensions.spacingGridTile),
            )
        }
        when (section.layout) {
            RethinkConfigureLayout.GridFour -> FourGrid(section.entries, widthClass)
            RethinkConfigureLayout.GridTriad -> TriadGrid(section.entries, widthClass)
            RethinkConfigureLayout.List -> EntryList(section.entries)
        }
    }
}

@Composable private fun FourGrid(entries: List<RethinkConfigureEntry>, widthClass: WindowWidthClass) {
    // A host that adds or hides an entry must not make the whole section vanish.
    if (entries.size != 4) {
        AdaptiveTileGrid(entries, columns = if (widthClass.isExpanded) 3 else 2)
        return
    }
    if (widthClass.isExpanded) {
        AdaptiveTileGrid(entries, columns = 3)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
        GridRow(entries[0], entries[1], rethinkGroupedListPairShape(true, CardPosition.First), rethinkGroupedListPairShape(false, CardPosition.First), 0)
        GridRow(entries[2], entries[3], rethinkGroupedListPairShape(true, CardPosition.Last), rethinkGroupedListPairShape(false, CardPosition.Last), 2)
    }
}
@Composable private fun TriadGrid(entries: List<RethinkConfigureEntry>, widthClass: WindowWidthClass) {
    if (entries.size < 2) return
    if (widthClass.isExpanded) {
        AdaptiveTileGrid(entries, columns = 3)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
        GridRow(entries[0], entries[1], rethinkGroupedListPairShape(true, CardPosition.First), rethinkGroupedListPairShape(false, CardPosition.First), 0)
        entries.drop(2).forEachIndexed { index, entry ->
            val isLast = index == entries.lastIndex - 2
            GridTile(
                entry,
                rethinkGroupedListShape(if (isLast) CardPosition.Last else CardPosition.Middle),
                Modifier.fillMaxWidth(),
                index + 2,
            )
        }
    }
}

@Composable
private fun AdaptiveTileGrid(entries: List<RethinkConfigureEntry>, columns: Int) {
    val rows = entries.chunked(columns)
    Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
        rows.forEachIndexed { rowIndex, row ->
            val isFirstRow = rowIndex == 0
            val isLastRow = rowIndex == rows.lastIndex
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
            ) {
                row.forEachIndexed { column, entry ->
                    GridTile(
                        entry,
                        configureGridCellShape(column, columns, isFirstRow, isLastRow),
                        Modifier.weight(1f),
                        rowIndex * columns + column,
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun configureGridCellShape(
    column: Int,
    columns: Int,
    isFirstRow: Boolean,
    isLastRow: Boolean,
): Shape {
    val outer = SharedDimensions.cornerRadius4xl
    val inner = SharedDimensions.cornerRadiusSm
    return RoundedCornerShape(
        topStart = if (column == 0 && isFirstRow) outer else inner,
        topEnd = if (column == columns - 1 && isFirstRow) outer else inner,
        bottomStart = if (column == 0 && isLastRow) outer else inner,
        bottomEnd = if (column == columns - 1 && isLastRow) outer else inner,
    )
}
@Composable private fun GridRow(
    first: RethinkConfigureEntry,
    second: RethinkConfigureEntry,
    firstShape: Shape,
    secondShape: Shape,
    startIndex: Int,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
        GridTile(first, firstShape, Modifier.weight(1f), startIndex)
        GridTile(second, secondShape, Modifier.weight(1f), startIndex + 1)
    }
}
@Composable private fun GridTile(entry: RethinkConfigureEntry, shape: Shape, modifier: Modifier, index: Int) {
    val colors = configureTileColors(entry.accent)
    Surface(
        onClick = entry.onClick,
        shape = shape,
        color = colors.fill,
        modifier = modifier.clip(shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SharedDimensions.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            ConfigureIconWell(colors) { entry.icon() }
            Column(modifier = Modifier.weight(1f)) {
                // Same type roles as RethinkListItem so grid tiles and list rows on this screen
                // read as one family; two lines survive large font scales.
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = colors.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.subtitle?.let { subtitle ->
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
@Composable private fun EntryList(entries: List<RethinkConfigureEntry>) = Column {
    entries.forEachIndexed { index, entry ->
        val colors = configureTileColors(entry.accent)
        RethinkListItem(
            headline = entry.title,
            supporting = entry.subtitle,
            position = cardPositionFor(index, entries.lastIndex),
            onClick = entry.onClick,
            defaultContainerColor = colors.fill,
            leadingContent = { ConfigureIconWell(colors) { entry.icon() } },
        )
    }
}

@Composable
private fun ConfigureIconWell(colors: ConfigureTileColors, icon: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg),
        color = colors.well,
        modifier = Modifier.size(SharedDimensions.iconContainerMd),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides colors.icon) { icon() }
        }
    }
}
@Composable private fun NoResults(strings: RethinkConfigureStrings) = Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadiusXl), color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(SharedDimensions.dividerThicknessBold, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))) {
    RethinkEmptyState(icon = MaterialSymbols.Filled.Search, title = strings.noResultsTitle, message = strings.noResultsSubtitle)
}
@Composable
private fun SearchResults(results: List<RethinkConfigureSearchEntry>, query: String) {
    val matchStyle = SpanStyle(color = MaterialTheme.colorScheme.primary)
    Column {
        results.forEachIndexed { index, result ->
            val colors = configureTileColors(result.entry.accent)
            val supporting = rethinkConfigureSearchSupporting(result, query)
            RethinkListItem(
                headline = result.entry.title,
                headlineAnnotated = highlightConfigureQuery(result.entry.title, query, matchStyle),
                supporting = supporting,
                supportingAnnotated = highlightConfigureQuery(supporting, query, matchStyle),
                position = cardPositionFor(index, results.lastIndex),
                onClick = result.entry.onClick,
                defaultContainerColor = colors.fill,
                showTrailingChevron = true,
                leadingContent = { ConfigureIconWell(colors) { result.entry.icon() } },
            )
        }
    }
}
