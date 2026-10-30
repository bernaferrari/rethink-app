/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/** One-line list-row height (Material 56dp). */
private val KeyValueRowMinHeight = 56.dp

/** [Inline]: label leading, value trailing (end-aligned). [Stacked]: label above value. */
enum class RethinkKeyValueLayout { Inline, Stacked }

/**
 * Read-only label/value presentation for detail views: a quiet label and an emphasized value.
 * Never interactive, so it never reads as a tappable row. The component adds no outer padding;
 * the host owns vertical rhythm.
 *
 * Inline rows align label and value on their first baseline; the value takes the remaining width
 * and wraps (up to [valueMaxLines]) rather than pushing into the label.
 */
@Composable
fun RethinkKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    layout: RethinkKeyValueLayout = RethinkKeyValueLayout.Inline,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    /** Identifiers (keys, IPs, hosts): monospace keeps look-alike characters distinguishable. */
    monospace: Boolean = false,
    /** Values the user copies into other clients. */
    selectable: Boolean = false,
    labelStyle: TextStyle = if (layout == RethinkKeyValueLayout.Stacked) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
    valueStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    valueFontWeight: FontWeight? = if (layout == RethinkKeyValueLayout.Inline) FontWeight.Medium else null,
    valueMaxLines: Int = Int.MAX_VALUE,
) {
    val valueText: @Composable (Modifier) -> Unit = { outer ->
        val text: @Composable (Modifier) -> Unit = { textModifier ->
            Text(
                value,
                modifier = textModifier,
                style = valueStyle,
                fontWeight = valueFontWeight,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                color = valueColor,
                textAlign = if (layout == RethinkKeyValueLayout.Inline) TextAlign.End else null,
                maxLines = valueMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selectable) SelectionContainer(outer) { text(if (layout == RethinkKeyValueLayout.Inline) Modifier.fillMaxWidth() else Modifier) } else text(outer)
    }
    when (layout) {
        RethinkKeyValueLayout.Stacked -> Column(modifier, verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
            Text(label, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            valueText(Modifier)
        }
        RethinkKeyValueLayout.Inline -> Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            Text(label, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alignByBaseline())
            valueText(Modifier.weight(1f).alignByBaseline())
        }
    }
}

/**
 * [RethinkKeyValueRow] as one tile of an expressive grouped list (same shape, gap and container
 * as [RethinkListItem]) for read-only facts such as account status or renewal date.
 */
@Composable
fun RethinkKeyValueListItem(
    label: String,
    value: String,
    position: CardPosition,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    monospace: Boolean = false,
    selectable: Boolean = false,
) {
    Surface(
        shape = rethinkGroupedListShape(position),
        color = LocalRethinkListContainerColor.current ?: MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.rethinkGroupedRow(position),
    ) {
        Row(
            Modifier
                .heightIn(min = KeyValueRowMinHeight)
                .padding(horizontal = SharedDimensions.cardPadding, vertical = SharedDimensions.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RethinkKeyValueRow(
                label = label,
                value = value,
                valueColor = valueColor,
                monospace = monospace,
                selectable = selectable,
                valueStyle = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
