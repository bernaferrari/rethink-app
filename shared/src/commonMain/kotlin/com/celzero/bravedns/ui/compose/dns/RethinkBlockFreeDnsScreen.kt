/* Copyright 2026 RethinkDNS and its authors */

package com.celzero.bravedns.ui.compose.dns

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.celzero.bravedns.ui.compose.components.RethinkSharedIconContainer
import com.celzero.bravedns.ui.compose.theme.RethinkFilterChip
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkLazyColumnScreenScaffold
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.ui.compose.theme.cardPositionFor
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

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
        topBar = { RethinkTopBar(strings.title, onBackClick = onBackClick) },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            bottom = SharedDimensions.spacing3xl,
        ),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
            item {
                Text(strings.heading.uppercase(), style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = SharedDimensions.spacingLg, top = SharedDimensions.spacingMd))
                Text(strings.description, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = SharedDimensions.spacingLg))
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    filters.forEach { filter ->
                        RethinkFilterChip(filter.label, selected = activeFilterId == filter.id, onClick = { onFilterSelected(filter.id) })
                    }
                }
            }
            itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                val selected = selectedKey == item.key
                RethinkListItem(
                    headline = item.name,
                    supporting = item.url,
                    position = cardPositionFor(index, items.lastIndex),
                    highlighted = selected,
                    onClick = { onItemSelected(item) },
                    leadingContent = {
                        RethinkSharedIconContainer(androidx.compose.material3.MaterialTheme.colorScheme.primary) {
                            Icon(MaterialSymbols.Filled.Public, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                        }
                    },
                    trailing = if (selected) ({ Icon(MaterialSymbols.Filled.Check, strings.selectedDescription) }) else null,
                )
            }
    }
}
