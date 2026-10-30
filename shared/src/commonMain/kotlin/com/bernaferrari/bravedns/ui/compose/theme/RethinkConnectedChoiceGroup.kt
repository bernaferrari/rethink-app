/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints

/**
 * One option of a [RethinkConnectedChoiceGroup]. [selectedIcon] lets an option swap its glyph
 * (e.g. to a check mark) while selected; it defaults to [icon].
 */
@Immutable
data class RethinkChoiceOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val selectedIcon: ImageVector? = icon,
)

/**
 * How a [RethinkConnectedChoiceGroup] shows its options.
 * - [Always]: every option shows icon + label; when a full-width row is too narrow for the
 *   labels, they drop and the icons stay (labels become content descriptions).
 * - [SelectedLabel]: the selected option shows icon + label, the others only their icon. Only for
 *   a two-way switch whose icons are self-evident (Network / DNS), where it reads cleaner.
 */
enum class RethinkChoiceLabels {
    Always,
    SelectedLabel,
}

/**
 * The app's single-select connected button group (M3 Expressive connected ToggleButtons).
 *
 * Exposed to accessibility as a radio group: the row is a `selectableGroup` and each item is a
 * [Role.RadioButton]. Selected items use secondaryContainer, per M3 connected button groups.
 *
 * @param selected the selected value, or null when nothing is selected.
 * @param fillWidth true splits the row width evenly across options; false wraps each option.
 * @param iconOnly renders only the icon (label becomes its content description) for narrow widths.
 * @param onDeselect when non-null, tapping the selected option clears the selection.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> RethinkConnectedChoiceGroup(
    options: List<RethinkChoiceOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    iconOnly: Boolean = false,
    onDeselect: (() -> Unit)? = null,
    labels: RethinkChoiceLabels = RethinkChoiceLabels.Always,
    /**
     * Unselected segments sit a tone above whatever they're on (screen, cards, sheets, and the
     * lifted rows inside sheets), so every option reads as a button, not a label.
     */
    unselectedContainerColor: Color = if (LocalRethinkListContainerColor.current != null) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    },
    /** Shorter segments with smaller type, for a group that sits inside a top bar. */
    compact: Boolean = false,
) {
    if (!fillWidth || iconOnly || labels != RethinkChoiceLabels.Always || options.any { it.icon == null }) {
        ChoiceRow(options, selected, onSelect, modifier, fillWidth, iconOnly, onDeselect, labels, unselectedContainerColor, compact)
        return
    }
    // Icons carry the meaning, so when the labels don't fit their segments the text goes and the icons stay.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val style = (if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge).copy(fontWeight = FontWeight.SemiBold)
        val padding = if (compact) CompactContentPadding else FillWidthContentPadding
        val chrome = padding.calculateLeftPadding(LayoutDirection.Ltr) + padding.calculateRightPadding(LayoutDirection.Ltr) +
            ToggleButtonDefaults.IconSize + ToggleButtonDefaults.IconSpacing
        val segment = (maxWidth - ButtonGroupDefaults.ConnectedSpaceBetween * (options.size - 1)) / options.size
        val labelsFit = options.all { option ->
            with(density) { measurer.measure(option.label, style, maxLines = 1).size.width.toDp() } + chrome <= segment
        }
        ChoiceRow(options, selected, onSelect, Modifier, fillWidth = true, iconOnly = !labelsFit, onDeselect, labels, unselectedContainerColor, compact)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ChoiceRow(
    options: List<RethinkChoiceOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier,
    fillWidth: Boolean,
    iconOnly: Boolean,
    onDeselect: (() -> Unit)?,
    labels: RethinkChoiceLabels,
    unselectedContainerColor: Color,
    compact: Boolean,
) {
    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colors = ToggleButtonDefaults.colors(
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            containerColor = unselectedContainerColor,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        options.forEachIndexed { index, option ->
            val isSelected = option.value == selected
            val collapsedToIcon = labels == RethinkChoiceLabels.SelectedLabel && option.icon != null && !isSelected
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { checked ->
                    when {
                        checked -> onSelect(option.value)
                        isSelected -> onDeselect?.invoke()
                    }
                },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = colors,
                border = null,
                // Equal-width segments are already centered by weight; the default side padding
                // only steals room from the label ("Allow…", "Block…") on phones.
                contentPadding = when {
                    compact -> CompactContentPadding
                    fillWidth -> FillWidthContentPadding
                    else -> ButtonDefaults.ContentPadding
                },
                modifier = Modifier
                    .then(if (fillWidth) Modifier.weight(1f) else Modifier.animateContentSize())
                    .heightIn(min = if (compact) SharedDimensions.buttonHeightSm else SharedDimensions.touchTargetMin)
                    // A segment narrower than the touch target gets invisible side padding from
                    // Material, which shows as wide gaps; at the target width the 2dp gap holds.
                    .then(if (compact) Modifier.widthIn(min = SharedDimensions.touchTargetMin) else Modifier)
                    .semantics { role = Role.RadioButton },
            ) {
                val icon = when {
                    isSelected -> option.selectedIcon
                    else -> option.icon
                }
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = if (iconOnly || collapsedToIcon) option.label else null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                    )
                }
                if ((!iconOnly && !collapsedToIcon) || icon == null) {
                    if (icon != null) Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    Text(
                        text = option.label,
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val CompactContentPadding = PaddingValues(horizontal = SharedDimensions.spacingMd)
private val FillWidthContentPadding = PaddingValues(
    horizontal = SharedDimensions.spacingSm,
    vertical = SharedDimensions.spacingSm,
)

