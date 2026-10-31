/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFilterChip
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSheetHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/** Which entries the Activity log lists: both as one timeline (the default), or one kind. */
enum class RethinkLogType { All, Connections, Lookups }

@Immutable
data class RethinkLogFiltersStrings(
    val title: String,
    val type: String,
    val all: String,
    val connections: String,
    val lookups: String,
    val app: String,
    val reason: String,
)

/** A non-default filter, shown as a removable chip under the log's control row. */
@Immutable
data class RethinkActiveLogFilter(val key: String, val label: String, val onRemove: () -> Unit)

/**
 * The log's Filters sheet: Type, then App and Reason, each a row that opens its own picker.
 * [appSummary] null hides App (a log opened for one app); [reasonSummary] null hides Reason
 * (lookups have no firewall reason).
 */
@Composable
fun RethinkLogFiltersSheet(
    type: RethinkLogType,
    onType: (RethinkLogType) -> Unit,
    appSummary: String?,
    onApp: () -> Unit,
    reasonSummary: String?,
    onReason: () -> Unit,
    strings: RethinkLogFiltersStrings,
    onDismiss: () -> Unit,
) {
    RethinkModalBottomSheet(onDismissRequest = onDismiss) { dismiss ->
        RethinkSheetHeader(title = strings.title)
        Text(strings.type, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RethinkConnectedChoiceGroup(
            options = listOf(
                RethinkChoiceOption(RethinkLogType.All, strings.all),
                RethinkChoiceOption(RethinkLogType.Connections, strings.connections),
                RethinkChoiceOption(RethinkLogType.Lookups, strings.lookups),
            ),
            selected = type,
            onSelect = onType,
        )
        val rows = listOfNotNull(
            appSummary?.let { Triple(strings.app, it, onApp) },
            reasonSummary?.let { Triple(strings.reason, it, onReason) },
        )
        rows.forEachIndexed { index, (headline, summary, open) ->
            RethinkListItem(
                headline = headline,
                supporting = summary,
                position = cardPositionFor(index, rows.lastIndex),
                showTrailingChevron = true,
                onClick = {
                    dismiss()
                    open()
                },
            )
        }
    }
}

/** Active filters as chips whose tap removes them; nothing at all when none is active. */
@Composable
fun RethinkActiveLogFilters(
    filters: List<RethinkActiveLogFilter>,
    removeDescription: (label: String) -> String,
    modifier: Modifier = Modifier,
) {
    if (filters.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = SharedDimensions.screenPaddingHorizontal)
            .padding(bottom = SharedDimensions.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        filters.forEach { filter ->
            val description = removeDescription(filter.label)
            RethinkFilterChip(
                label = filter.label,
                selected = true,
                selectedCheck = false,
                onClick = filter.onRemove,
                modifier = Modifier.semantics { contentDescription = description },
                trailingIcon = {
                    Icon(MaterialSymbols.Filled.Close, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                },
            )
        }
    }
}
