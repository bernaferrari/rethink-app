/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.dns

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.components.RethinkSharedIconContainer
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFilterChip
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormSection
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLazyColumnScreenScaffold
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor

data class RethinkBlockFreeDnsItem(val key: String, val name: String, val url: String, val type: String)
data class RethinkBlockFreeDnsFilter(val id: String?, val label: String)
data class RethinkBlockFreeDnsStrings(
    val title: String,
    val heading: String,
    val description: String,
    val selectedDescription: String,
)

/** Common trusted/block-free DNS endpoint picker. */
@Composable
fun RethinkBlockFreeDnsScreen(
    items: List<RethinkBlockFreeDnsItem>,
    filters: List<RethinkBlockFreeDnsFilter>,
    activeFilterId: String?,
    selectedKey: String,
    strings: RethinkBlockFreeDnsStrings,
    onFilterSelected: (String?) -> Unit,
    onItemSelected: (RethinkBlockFreeDnsItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RethinkLazyColumnScreenScaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { RethinkTopBar(strings.title, onBackClick = onBackClick) },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            bottom = SharedDimensions.spacing3xl,
        ),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
            item {
                RethinkFormSection(strings.heading, strings.description) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                        filters.forEach { filter ->
                            RethinkFilterChip(filter.label, selected = activeFilterId == filter.id, onClick = { onFilterSelected(filter.id) })
                        }
                    }
                }
            }
            if (items.isEmpty()) {
                item { RethinkEmptyState(title = strings.heading, message = strings.description) }
            } else {
                itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                    val selected = selectedKey == item.key
                    RethinkListItem(
                        headline = item.name,
                        supporting = item.url,
                        position = cardPositionFor(index, items.lastIndex),
                        highlighted = selected,
                        onClick = { onItemSelected(item) },
                        leadingContent = {
                            RethinkSharedIconContainer(MaterialTheme.colorScheme.primary) {
                                Icon(MaterialSymbols.Filled.Public, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        trailing = if (selected) ({ Icon(MaterialSymbols.Filled.Check, strings.selectedDescription) }) else null,
                    )
                }
            }
    }
}
