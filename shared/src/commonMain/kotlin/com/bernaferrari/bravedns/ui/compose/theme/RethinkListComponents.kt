@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp

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
     * Defaults to the error tone in [colors].
     */
    destructive: Boolean = false,
    colors: RethinkListItemColors = rethinkListItemColors(destructive = destructive),
    leadingContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    position: CardPosition = CardPosition.Middle,
    enabled: Boolean = true,
    highlighted: Boolean = false,
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
    val defaultContainerColor = colors.defaultContainerColor
    val highlightContainerColor = colors.highlightContainerColor
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
        RethinkListItemContent(
            headline, headlineAnnotated, supporting, supportingAnnotated, contentOffset,
            leadingIcon, leadingContent, trailing, colors, headlineColor, shape, alpha,
            singleLineHeadline, showTrailingChevron && onClick != null && enabled,
        )
    }

    Column(modifier = modifier.fillMaxWidth().scale(scale)) {
        RethinkListItemSurface(
            selection, enabled, onClick, onLongClick, interactionSource,
            shape, containerColor, border, surfaceModifier, content,
        )
    }
}
