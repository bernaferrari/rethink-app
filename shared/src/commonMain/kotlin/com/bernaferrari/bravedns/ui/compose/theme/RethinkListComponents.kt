@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Switch
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ripple
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Immutable
import androidx.compose.ui.semantics.Role
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Default container for grouped rows. Surfaces that already sit on surfaceContainerLow (bottom
 * sheets) provide a lifted tone here so their rows don't vanish into the background.
 */
val LocalRethinkListContainerColor = compositionLocalOf<Color?> { null }

/** Material's disabled-content opacity (md.sys.state.disabled). */
private const val DISABLED_CONTENT_ALPHA = 0.38f

enum class CardPosition { First, Middle, Last, Single }

fun cardPositionFor(index: Int, lastIndex: Int): CardPosition = when {
    lastIndex <= 0 -> CardPosition.Single
    index == 0 -> CardPosition.First
    index == lastIndex -> CardPosition.Last
    else -> CardPosition.Middle
}

/**
 * Expressive grouped-list corners: generous rounding at the outside of the group and a small,
 * still-visible radius between adjacent rows. Keeping this in the component layer prevents every
 * screen from inventing a slightly different version of the same visual rule.
 */
fun rethinkGroupedListShape(position: CardPosition): RoundedCornerShape = when (position) {
    CardPosition.Single -> RoundedCornerShape(SharedDimensions.groupCornerRadius)
    CardPosition.First -> RoundedCornerShape(
        topStart = SharedDimensions.groupCornerRadius,
        topEnd = SharedDimensions.groupCornerRadius,
        bottomStart = SharedDimensions.cornerRadiusSm,
        bottomEnd = SharedDimensions.cornerRadiusSm,
    )
    CardPosition.Last -> RoundedCornerShape(
        topStart = SharedDimensions.cornerRadiusSm,
        topEnd = SharedDimensions.cornerRadiusSm,
        bottomStart = SharedDimensions.groupCornerRadius,
        bottomEnd = SharedDimensions.groupCornerRadius,
    )
    CardPosition.Middle -> RoundedCornerShape(SharedDimensions.cornerRadiusSm)
}

/**
 * One cell of an expressive grid group: the group radius only on corners that touch the group's
 * outside, the small inner radius everywhere else. [rowPosition] is the cell's row in the group.
 */
fun rethinkGroupedGridCellShape(
    column: Int,
    columns: Int,
    rowPosition: CardPosition,
): RoundedCornerShape {
    val isTopRow = rowPosition == CardPosition.First || rowPosition == CardPosition.Single
    val isBottomRow = rowPosition == CardPosition.Last || rowPosition == CardPosition.Single
    val isStart = column == 0
    val isEnd = column == columns - 1
    fun corner(outer: Boolean) = if (outer) SharedDimensions.groupCornerRadius else SharedDimensions.cornerRadiusSm
    return RoundedCornerShape(
        topStart = corner(isStart && isTopRow),
        topEnd = corner(isEnd && isTopRow),
        bottomEnd = corner(isEnd && isBottomRow),
        bottomStart = corner(isStart && isBottomRow),
    )
}

/** The matching shape for one tile in a two-column expressive group. */
fun rethinkGroupedListPairShape(
    isLeadingTile: Boolean,
    position: CardPosition,
): RoundedCornerShape = rethinkGroupedGridCellShape(if (isLeadingTile) 0 else 1, columns = 2, rowPosition = position)

/** One tile of a single horizontal connected row (quick actions, usage tiles). */
fun rethinkConnectedRowShape(index: Int, count: Int): RoundedCornerShape =
    rethinkGroupedGridCellShape(index, columns = count, rowPosition = CardPosition.Single)

/** Gap above a grouped row: none for the group's first row, [SharedDimensions.spacingGridTile] otherwise. */
fun rethinkGroupedRowTopGap(position: CardPosition): Dp =
    if (position == CardPosition.First || position == CardPosition.Single) SharedDimensions.spacingNone else SharedDimensions.spacingGridTile

/**
 * Full-width row of an expressive grouped list: the [rethinkGroupedRowTopGap] above it, then
 * clipped to [rethinkGroupedListShape] so ripples and highlights follow the row's corners. Custom
 * rows apply this before their Surface; [RethinkListItem] already does.
 */
fun Modifier.rethinkGroupedRow(position: CardPosition): Modifier =
    fillMaxWidth()
        .padding(top = rethinkGroupedRowTopGap(position))
        .clip(rethinkGroupedListShape(position))

@Composable
fun RethinkListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = SharedDimensions.spacingGridTile), content = content)
}

/**
 * Whole-row control semantics for [RethinkListItem]. With a trailing Switch/Checkbox/RadioButton
 * whose own callback is null, the row is the control: TalkBack announces "Switch, on" instead of
 * a plain button, and the entire row toggles.
 */
@Immutable
data class RethinkListSelection(
    val checked: Boolean,
    val role: Role = Role.Switch,
    val onCheckedChange: (Boolean) -> Unit,
)

/** Shared expressive grouped list row. Resources and side effects are injected by each host. */
@Composable
fun RethinkListItem(
    modifier: Modifier = Modifier,
    headline: String,
    headlineAnnotated: AnnotatedString? = null,
    supporting: String? = null,
    supportingAnnotated: AnnotatedString? = null,
    contentOffset: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    /**
     * Destructive action row (delete, reset, sign out): error-toned icon and headline.
     * Overrides [leadingIconTint].
     */
    destructive: Boolean = false,
    leadingIconTint: Color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    /**
     * Null draws the icon bare, which is what plain rows want: a filled well on every row reads
     * heavy. Pass a colour only when the well itself says something (active, selected, warning).
     */
    leadingIconContainerColor: Color? = null,
    leadingIconContainerShape: Shape = RoundedCornerShape(SharedDimensions.iconContainerRadius),
    leadingContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    position: CardPosition = CardPosition.Middle,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    defaultContainerColor: Color = LocalRethinkListContainerColor.current ?: MaterialTheme.colorScheme.surfaceContainerLow,
    highlightContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
    showTrailingChevron: Boolean = false,
    selection: RethinkListSelection? = null,
    /**
     * For identifiers (IPs, hosts, keys): keep the headline on one line and elide the middle, so
     * both the start and the meaningful tail (port, TLD) stay visible.
     */
    singleLineHeadline: Boolean = false,
    /** Secondary gesture (e.g. enter multi-select). Requires [onClick]. */
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val motion = LocalRethinkMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val interactive = enabled && (onClick != null || selection != null)
    val scale by animateFloatAsState(
        targetValue = if (!motion.reducedMotion && pressed && interactive) 0.985f else 1f,
        animationSpec = if (motion.reducedMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "rethink_list_item_scale",
    )
    val highlightAlpha by animateFloatAsState(
        targetValue = if (highlighted) 1f else 0f,
        animationSpec = if (motion.reducedMotion) snap() else MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "rethink_list_item_highlight",
    )
    val shape = rethinkGroupedListShape(position)
    val alpha = if (enabled) 1f else DISABLED_CONTENT_ALPHA
    val containerColor = lerp(defaultContainerColor, highlightContainerColor, highlightAlpha)
    val headlineColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    val surfaceModifier = Modifier.rethinkGroupedRow(position)
    // Highlight (deep-link focus, selection) is tonal only: grouped rows never draw borders.
    val border: BorderStroke? = null
    val content: @Composable () -> Unit = {
        // The expressive ListItem overload centers trailing controls (switches, radios) even on
        // three-line rows, where the legacy one top-aligns them.
        ListItem(
                verticalAlignment = Alignment.CenterVertically,
                content = {
                    Text(
                        text = headlineAnnotated ?: AnnotatedString(headline),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            color = headlineColor.copy(alpha = alpha),
                        ),
                        color = if (headlineAnnotated != null) Color.Unspecified else headlineColor.copy(alpha = alpha),
                        maxLines = if (singleLineHeadline) 1 else Int.MAX_VALUE,
                        overflow = if (singleLineHeadline) TextOverflow.MiddleEllipsis else TextOverflow.Clip,
                        modifier = contentOffset,
                    )
                },
                supportingContent = if (supporting != null || supportingAnnotated != null) {
                    {
                        Text(
                            text = supportingAnnotated ?: AnnotatedString(supporting.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                            ),
                            color = if (supportingAnnotated != null) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                            modifier = contentOffset.then(Modifier.padding(top = SharedDimensions.spacingXs)),
                        )
                    }
                } else null,
                // Material's ListItem reserves a leading column whenever it receives a
                // lambda, even when that lambda emits nothing. Keep it null for text-only
                // rows so their content aligns with the card inset instead of a phantom icon.
                leadingContent = when {
                    leadingContent != null -> leadingContent
                    leadingIcon != null && leadingIconContainerColor != null -> {
                        {
                            RethinkIconWell(containerColor = leadingIconContainerColor, contentColor = leadingIconTint, shape = leadingIconContainerShape) {
                                androidx.compose.material3.Icon(leadingIcon, null, tint = leadingIconTint.copy(alpha = alpha), modifier = Modifier.size(SharedDimensions.iconSizeSm))
                            }
                        }
                    }
                    // Bare icon in the same 40dp slot, so text lines up with rows that do have a well.
                    leadingIcon != null -> {
                        {
                            Box(Modifier.size(SharedDimensions.iconContainerMd), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(leadingIcon, null, tint = leadingIconTint.copy(alpha = alpha), modifier = Modifier.size(SharedDimensions.iconSizeMd))
                            }
                        }
                    }
                    else -> null
                },
                trailingContent = when {
                    trailing != null -> trailing
                    showTrailingChevron && onClick != null && enabled -> {
                        { androidx.compose.material3.Icon(MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    else -> null
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clip(shape).padding(vertical = 1.dp),
        )
    }

    Column(modifier = modifier.fillMaxWidth().scale(scale)) {
        if (selection != null) {
            val controlModifier = if (selection.role == Role.RadioButton) {
                Modifier.selectable(
                    selected = selection.checked,
                    interactionSource = interactionSource,
                    indication = ripple(),
                    enabled = enabled,
                    role = selection.role,
                    onClick = { selection.onCheckedChange(true) },
                )
            } else {
                Modifier.toggleable(
                    value = selection.checked,
                    interactionSource = interactionSource,
                    indication = ripple(),
                    enabled = enabled,
                    role = selection.role,
                    onValueChange = selection.onCheckedChange,
                )
            }
            Surface(
                shape = shape,
                color = containerColor,
                border = border,
                modifier = surfaceModifier.then(controlModifier),
                content = content,
            )
        } else if (onClick != null && onLongClick != null) {
            Surface(
                shape = shape,
                color = containerColor,
                border = border,
                modifier = surfaceModifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    enabled = enabled,
                    onLongClick = onLongClick,
                    onClick = onClick,
                ),
                content = content,
            )
        } else if (onClick != null) {
            Surface(
                onClick = onClick,
                enabled = enabled,
                interactionSource = interactionSource,
                shape = shape,
                color = containerColor,
                border = border,
                modifier = surfaceModifier,
                content = content,
            )
        } else {
            Surface(
                shape = shape,
                color = containerColor,
                border = border,
                modifier = surfaceModifier,
                content = content,
            )
        }
    }
}

/**
 * A row whose whole surface is one switch: tapping anywhere toggles, TalkBack announces
 * "Switch, on". The trailing [Switch] is display-only so it never becomes a second target.
 */
@Composable
fun RethinkSwitchListItem(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingAnnotated: AnnotatedString? = null,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = MaterialTheme.colorScheme.primary,
    leadingIconContainerColor: Color? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    position: CardPosition = CardPosition.Middle,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    highlightContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
    singleLineHeadline: Boolean = false,
) = RethinkListItem(
    modifier = modifier,
    headline = headline,
    supporting = supporting,
    supportingAnnotated = supportingAnnotated,
    leadingIcon = leadingIcon,
    leadingIconTint = leadingIconTint,
    leadingIconContainerColor = leadingIconContainerColor,
    leadingContent = leadingContent,
    position = position,
    enabled = enabled,
    highlighted = highlighted,
    highlightContainerColor = highlightContainerColor,
    singleLineHeadline = singleLineHeadline,
    selection = RethinkListSelection(checked = checked, role = Role.Switch, onCheckedChange = onCheckedChange),
    trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
)

/**
 * The app's one single-choice row: the whole row is the radio (TalkBack: "Radio button,
 * selected"), the selected row fills with secondaryContainer, the selection family shared with
 * chips and connected groups, and a display-only trailing [RadioButton] shows the state. Put
 * these in a `RethinkListGroup(Modifier.selectableGroup())`.
 */
@Composable
fun RethinkRadioListItem(
    headline: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    position: CardPosition = CardPosition.Middle,
    enabled: Boolean = true,
) = RethinkListItem(
    modifier = modifier,
    headline = headline,
    supporting = supporting,
    leadingIcon = leadingIcon,
    position = position,
    enabled = enabled,
    highlighted = selected,
    highlightContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selection = RethinkListSelection(checked = selected, role = Role.RadioButton) { onSelect() },
    trailing = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
)

/** [RethinkSwitchListItem] counterpart for multi-select lists: the whole row is one checkbox. */
@Composable
fun RethinkCheckboxListItem(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingAnnotated: AnnotatedString? = null,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = MaterialTheme.colorScheme.primary,
    leadingIconContainerColor: Color? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    position: CardPosition = CardPosition.Middle,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    highlightContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
    singleLineHeadline: Boolean = false,
) = RethinkListItem(
    modifier = modifier,
    headline = headline,
    supporting = supporting,
    supportingAnnotated = supportingAnnotated,
    leadingIcon = leadingIcon,
    leadingIconTint = leadingIconTint,
    leadingIconContainerColor = leadingIconContainerColor,
    leadingContent = leadingContent,
    position = position,
    enabled = enabled,
    highlighted = highlighted,
    highlightContainerColor = highlightContainerColor,
    singleLineHeadline = singleLineHeadline,
    selection = RethinkListSelection(checked = checked, role = Role.Checkbox, onCheckedChange = onCheckedChange),
    trailing = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
)

/**
 * Trailing control for a split row: the row itself navigates (opens details/logs) and this
 * switch is a separate target. No divider: it squeezed titles onto two lines, and the switch is
 * already its own target; it carries [contentDescription] so it is announced on its own.
 */
@Composable
fun RethinkSplitSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    )
}
