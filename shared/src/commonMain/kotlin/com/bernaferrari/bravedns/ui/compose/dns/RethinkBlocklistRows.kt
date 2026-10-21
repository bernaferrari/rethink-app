/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.ui.semantics.Role
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListSelection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeaderWithSubtitle

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
    position: CardPosition = CardPosition.Single,
) {
    Column(modifier.fillMaxWidth()) {
        if (showGroupHeader) RethinkBlocklistGroupHeader(pack.group)
        RethinkListItem(
            headline = pack.name.replaceFirstChar(Char::titlecase),
            supporting = strings.blocklistCount(pack.blocklistCount),
            position = position,
            defaultContainerColor = blocklistRowContainer(pack.selected),
            selection = RethinkListSelection(checked = pack.selected, role = Role.Checkbox, onCheckedChange = onToggle),
            trailing = { Checkbox(pack.selected, onCheckedChange = null) },
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
    position: CardPosition = CardPosition.Single,
) {
    val subgroup = tag.subgroup.ifBlank { tag.group.title }
    val entries = strings.entries(tag.entries)
    Column(modifier.fillMaxWidth()) {
        if (showGroupHeader) RethinkBlocklistGroupHeader(tag.group)
        RethinkListItem(
            headline = tag.name,
            supporting = "$subgroup · $entries",
            position = position,
            defaultContainerColor = blocklistRowContainer(tag.selected),
            selection = RethinkListSelection(checked = tag.selected, role = Role.Checkbox, onCheckedChange = onToggle),
            trailing = { Checkbox(tag.selected, onCheckedChange = null) },
        )
    }
}

@Composable
private fun RethinkBlocklistGroupHeader(group: RethinkBlocklistGroup) {
    SectionHeaderWithSubtitle(title = group.title, subtitle = group.description.takeIf { it.isNotBlank() })
}

/** Selection reads through container tone inside the grouped list, never through a border. */
@Composable
private fun blocklistRowContainer(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow

/** Grouped-list position of row [index] in a feed whose rows are grouped by [groupIdAt]. */
fun rethinkBlocklistPosition(index: Int, count: Int, groupIdAt: (Int) -> String?): CardPosition {
    val id = groupIdAt(index)
    val first = index == 0 || groupIdAt(index - 1) != id
    val last = index == count - 1 || groupIdAt(index + 1) != id
    return when {
        first && last -> CardPosition.Single
        first -> CardPosition.First
        last -> CardPosition.Last
        else -> CardPosition.Middle
    }
}
