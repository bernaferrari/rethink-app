/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
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
 * How a [RethinkConnectedChoiceGroup] shows labels.
 * - [Always]: every option shows icon + label.
 * - [SelectedOnly]: unselected options collapse to their icon and the selected one expands to
 *   icon + label. For crowded rows (log filters, toolbar switches) where full labels would
 *   truncate; every option must have an icon, and the label stays as its accessibility name.
 */
enum class RethinkChoiceLabels { Always, SelectedOnly }

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
    /** Lift this one tone when the group sits on a surfaceContainerLow card. */
    unselectedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    /** Shorter segments with smaller type, for a group that sits inside a top bar. */
    compact: Boolean = false,
) {
    val motion = LocalRethinkMotion.current
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
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
            val collapsible = labels == RethinkChoiceLabels.SelectedOnly && option.icon != null
            val showLabel = !iconOnly && (!collapsible || isSelected)
            // Selected segment grows to hold its label; the others shrink to an icon. The weights
            // ride the spatial spring so the row reflows like one expressive control.
            val weight by animateFloatAsState(
                targetValue = if (collapsible && !isSelected) COLLAPSED_WEIGHT else 1f,
                animationSpec = if (motion.reducedMotion) snap() else spatial,
                label = "choiceWeight",
            )
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
                    .then(if (fillWidth) Modifier.weight(weight) else Modifier.animateContentSize())
                    .heightIn(min = if (compact) SharedDimensions.buttonHeightSm else SharedDimensions.touchTargetMin)
                    // A segment narrower than the touch target gets invisible side padding from
                    // Material, which shows as wide gaps; at the target width the 2dp gap holds.
                    .then(if (compact) Modifier.widthIn(min = SharedDimensions.touchTargetMin) else Modifier)
                    .semantics { role = Role.RadioButton },
            ) {
                val icon = if (isSelected) option.selectedIcon else option.icon
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = if (showLabel) null else option.label,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                    )
                }
                if (showLabel || icon == null) {
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

/** Width share of a collapsed (icon-only) segment relative to the expanded one. */
private const val COLLAPSED_WEIGHT = 0.42f
