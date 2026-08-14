/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

data class RethinkBlocklistGroup(
    val id: String,
    val title: String,
    val description: String = "",
)

data class RethinkBlocklistPack(
    val id: String,
    val group: RethinkBlocklistGroup,
    val name: String,
    val blocklistCount: Int,
    val selected: Boolean,
    /** Database identifiers are opaque to common UI but let a host persist a pack toggle. */
    val tagIds: Set<Int> = emptySet(),
)

data class RethinkBlocklistFileTag(
    val id: String,
    val group: RethinkBlocklistGroup,
    val subgroup: String,
    val name: String,
    val entries: Int,
    val level: Int? = null,
    val url: String? = null,
    val selected: Boolean,
    /** Database identifiers are opaque to common UI but let a host persist a row toggle. */
    val tagIds: Set<Int> = emptySet(),
)

data class RethinkBlocklistRowStrings(
    val blocklistCount: (Int) -> String,
    val entries: (Int) -> String,
)

/** Target-neutral simple pack row, including group chrome and selection behavior. */
@Composable
fun RethinkBlocklistPackRow(
    pack: RethinkBlocklistPack,
    showGroupHeader: Boolean,
    strings: RethinkBlocklistRowStrings,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        if (showGroupHeader) RethinkBlocklistGroupHeader(pack.group)
        RethinkListItem(
            headline = pack.name.replaceFirstChar(Char::titlecase),
            supporting = strings.blocklistCount(pack.blocklistCount),
            position = CardPosition.Single,
            highlighted = pack.selected,
            onClick = { onToggle(!pack.selected) },
            trailing = { Checkbox(pack.selected, onCheckedChange = onToggle) },
        )
    }
}

/** Target-neutral granular blocklist row. Hosts retain only URL opening and persistence. */
@Composable
fun RethinkBlocklistFileTagRow(
    tag: RethinkBlocklistFileTag,
    showGroupHeader: Boolean,
    strings: RethinkBlocklistRowStrings,
    onToggle: (Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subgroup = tag.subgroup.ifBlank { tag.group.title }
    val entries = strings.entries(tag.entries)
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        if (showGroupHeader) RethinkBlocklistGroupHeader(tag.group)
        RethinkListItem(
            headline = tag.name,
            supporting = "$subgroup · $entries",
            position = CardPosition.Single,
            highlighted = tag.selected,
            onClick = { onToggle(!tag.selected) },
            trailing = { Checkbox(tag.selected, onCheckedChange = onToggle) },
        )
    }
}

@Composable
private fun RethinkBlocklistGroupHeader(group: RethinkBlocklistGroup) {
    Column(
        Modifier.fillMaxWidth().padding(
            start = SharedDimensions.spacingGridTile,
            bottom = SharedDimensions.spacingGridTile,
        ),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(group.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        if (group.description.isNotBlank()) Text(group.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
