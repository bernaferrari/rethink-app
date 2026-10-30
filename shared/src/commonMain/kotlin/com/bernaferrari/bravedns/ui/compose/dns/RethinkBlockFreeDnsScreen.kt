/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.dns

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFilterChip
import com.bernaferrari.bravedns.ui.compose.theme.RethinkRadioListItem
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkSection
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen

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
    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        rethinkSection(strings.heading, strings.description) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                filters.forEach { filter ->
                    RethinkFilterChip(filter.label, selected = activeFilterId == filter.id, onClick = { onFilterSelected(filter.id) })
                }
            }
        }
        item {
            if (items.isEmpty()) {
                // The section above already explains the list; the empty state only marks it empty.
                RethinkEmptyState(title = strings.heading, icon = MaterialSymbols.Filled.Dns, contained = true)
            } else {
                RethinkListGroup {
                    items.forEachIndexed { index, item ->
                        // Single choice: the whole row is the radio, like every other one-of list.
                        RethinkRadioListItem(
                            headline = item.name,
                            supporting = item.url,
                            selected = selectedKey == item.key,
                            onSelect = { onItemSelected(item) },
                            leadingIcon = MaterialSymbols.Filled.Public,
                            position = cardPositionFor(index, items.lastIndex),
                        )
                    }
                }
            }
        }
    }
}
