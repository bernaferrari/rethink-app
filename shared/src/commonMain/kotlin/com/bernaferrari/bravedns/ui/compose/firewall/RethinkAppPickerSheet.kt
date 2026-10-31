/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSheetHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/** One choice in [RethinkAppPickerSheet]: an app uid (shared by every package under it). */
@Immutable
data class RethinkPickableApp(
    val uid: Int,
    val label: String,
    /** Other names under the same uid, or the package; shown as the supporting line. */
    val detail: String? = null,
    /** Host key for artwork (the package name on Android). */
    val iconKey: String = "",
)

data class RethinkAppPickerStrings(
    /** "Choose app". */
    val title: String,
    val searchPlaceholder: String,
    val clearSearch: String,
    val loading: String,
    val noResults: String,
)

/**
 * Pick one app for a rule's scope. [apps] null means still loading (no "no apps" flash); search
 * matches the label and the detail line. Picking closes the sheet, then reports the app.
 */
@Composable
fun RethinkAppPickerSheet(
    apps: List<RethinkPickableApp>?,
    strings: RethinkAppPickerStrings,
    onPick: (RethinkPickableApp) -> Unit,
    onDismiss: () -> Unit,
    selectedUid: Int? = null,
    appIcon: (@Composable (RethinkPickableApp) -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val needle = query.trim()
        apps?.filter { needle.isEmpty() || it.label.contains(needle, true) || it.detail?.contains(needle, true) == true }
    }
    RethinkModalBottomSheet(
        onDismissRequest = onDismiss,
        expandOnShow = true,
        contentPadding = PaddingValues(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingSm),
        verticalSpacing = SharedDimensions.spacingGridTile,
        includeBottomSpacer = false,
    ) { dismiss ->
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = SharedDimensions.sheetContentMaxHeight),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            RethinkSheetHeader(strings.title)
            RethinkSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = strings.searchPlaceholder,
                clearQueryContentDescription = strings.clearSearch,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                filtered == null -> RethinkLoadingState(strings.loading, contained = true)
                filtered.isEmpty() -> RethinkEmptyState(icon = MaterialSymbols.Filled.Search, title = strings.noResults, contained = true)
                else -> LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    itemsIndexed(filtered, key = { _, app -> app.uid }) { index, app ->
                        RethinkListItem(
                            headline = app.label,
                            supporting = app.detail,
                            position = cardPositionFor(index, filtered.lastIndex),
                            highlighted = app.uid == selectedUid,
                            leadingIcon = if (appIcon == null) MaterialSymbols.Filled.Android else null,
                            leadingContent = appIcon?.let { icon ->
                                { Box(Modifier.size(SharedDimensions.iconContainerMd), contentAlignment = Alignment.Center) { icon(app) } }
                            },
                            onClick = { dismiss(); onPick(app) },
                        )
                    }
                }
            }
        }
    }
}
