/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.dns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

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
    val rowShape = RoundedCornerShape(SharedDimensions.cornerRadiusXl)
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        if (showGroupHeader) RethinkBlocklistGroupHeader(pack.group)
        Surface(
            onClick = { onToggle(!pack.selected) },
            modifier = Modifier.fillMaxWidth().clip(rowShape),
            shape = rowShape,
            color = if (pack.selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                Modifier.padding(
                    horizontal = SharedDimensions.cardPadding,
                    vertical = SharedDimensions.spacingSmMd,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(pack.name.replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(strings.blocklistCount(pack.blocklistCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(SharedDimensions.spacingSm))
                Checkbox(pack.selected, onCheckedChange = onToggle)
            }
        }
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
    val (chipText, chipBackground) = tag.level.chipColors()
    val rowShape = RoundedCornerShape(SharedDimensions.cornerRadiusXl)
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        if (showGroupHeader) RethinkBlocklistGroupHeader(tag.group)
        Surface(
            onClick = { onToggle(!tag.selected) },
            modifier = Modifier.fillMaxWidth().clip(rowShape),
            shape = rowShape,
            color = if (tag.selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                Modifier.padding(
                    horizontal = SharedDimensions.cardPadding,
                    vertical = SharedDimensions.spacingSmMd,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tag.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            subgroup,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = SharedDimensions.spacingXs),
                        )
                        AssistChip(
                            onClick = { tag.url?.let(onOpenUrl) },
                            enabled = !tag.url.isNullOrBlank(),
                            label = { Text(strings.entries(tag.entries)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = chipBackground, labelColor = chipText),
                        )
                    }
                }
                Spacer(Modifier.width(SharedDimensions.spacingSm))
                Checkbox(tag.selected, onCheckedChange = onToggle)
            }
        }
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
        Text(group.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        if (group.description.isNotBlank()) Text(group.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Int?.chipColors(): Pair<Color, Color> = when (this) {
    0 -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
    1 -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
    2 -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.onSurface to MaterialTheme.colorScheme.surface
}
