/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    CardPosition.Single -> RoundedCornerShape(SharedDimensions.cornerRadius4xl)
    CardPosition.First -> RoundedCornerShape(
        topStart = SharedDimensions.cornerRadius4xl,
        topEnd = SharedDimensions.cornerRadius4xl,
        bottomStart = SharedDimensions.cornerRadiusSm,
        bottomEnd = SharedDimensions.cornerRadiusSm,
    )
    CardPosition.Last -> RoundedCornerShape(
        topStart = SharedDimensions.cornerRadiusSm,
        topEnd = SharedDimensions.cornerRadiusSm,
        bottomStart = SharedDimensions.cornerRadius4xl,
        bottomEnd = SharedDimensions.cornerRadius4xl,
    )
    CardPosition.Middle -> RoundedCornerShape(SharedDimensions.cornerRadiusSm)
}

/** The matching shape for one tile in a two-column expressive group. */
fun rethinkGroupedListPairShape(
    isLeadingTile: Boolean,
    position: CardPosition,
): RoundedCornerShape {
    val hasTopOuterCorner = position == CardPosition.First || position == CardPosition.Single
    val hasBottomOuterCorner = position == CardPosition.Last || position == CardPosition.Single
    return RoundedCornerShape(
        topStart = if (isLeadingTile && hasTopOuterCorner) SharedDimensions.cornerRadius4xl else SharedDimensions.cornerRadiusSm,
        topEnd = if (!isLeadingTile && hasTopOuterCorner) SharedDimensions.cornerRadius4xl else SharedDimensions.cornerRadiusSm,
        bottomEnd = if (!isLeadingTile && hasBottomOuterCorner) SharedDimensions.cornerRadius4xl else SharedDimensions.cornerRadiusSm,
        bottomStart = if (isLeadingTile && hasBottomOuterCorner) SharedDimensions.cornerRadius4xl else SharedDimensions.cornerRadiusSm,
    )
}

@Composable
fun RethinkListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp), content = content)
}

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
    leadingIconPainter: Painter? = null,
    leadingIconTint: Color = MaterialTheme.colorScheme.primary,
    leadingIconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
    leadingIconContainerShape: Shape = RoundedCornerShape(SharedDimensions.iconContainerRadius),
    leadingContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    position: CardPosition = CardPosition.Middle,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    defaultContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    highlightContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
    showTrailingChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && onClick != null) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "rethink_list_item_scale",
    )
    val highlightAlpha by animateFloatAsState(
        targetValue = if (highlighted) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "rethink_list_item_highlight",
    )
    val shape = rethinkGroupedListShape(position)
    val alpha = if (enabled) 1f else 0.5f
    val containerColor = lerp(defaultContainerColor, highlightContainerColor, highlightAlpha)

    Column(modifier = modifier.fillMaxWidth().scale(scale)) {
        Surface(
            onClick = onClick ?: {},
            enabled = enabled && onClick != null,
            interactionSource = interactionSource,
            shape = shape,
            color = containerColor,
            border = if (highlighted) BorderStroke(SharedDimensions.dividerThicknessBold, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (position == CardPosition.First || position == CardPosition.Single) 0.dp else 2.dp)
                .clip(shape),
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        text = headlineAnnotated ?: AnnotatedString(headline),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        modifier = contentOffset,
                    )
                },
                supportingContent = if (supporting != null || supportingAnnotated != null) {
                    {
                        Text(
                            text = supportingAnnotated ?: AnnotatedString(supporting.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f * alpha),
                            modifier = contentOffset.then(Modifier.padding(top = SharedDimensions.spacingXs)),
                        )
                    }
                } else null,
                leadingContent = {
                    when {
                        leadingContent != null -> leadingContent()
                        leadingIcon != null || leadingIconPainter != null -> {
                            Surface(shape = leadingIconContainerShape, color = leadingIconContainerColor, modifier = Modifier.size(SharedDimensions.iconContainerSm)) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(SharedDimensions.iconContainerSm)) {
                                    when {
                                        leadingIcon != null -> androidx.compose.material3.Icon(leadingIcon, null, tint = leadingIconTint.copy(alpha = alpha), modifier = Modifier.size(SharedDimensions.iconSizeSm))
                                        leadingIconPainter != null && leadingIconTint == Color.Unspecified -> Image(leadingIconPainter, null, modifier = Modifier.size(18.dp))
                                        leadingIconPainter != null -> androidx.compose.material3.Icon(leadingIconPainter, null, tint = leadingIconTint.copy(alpha = alpha), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                trailingContent = when {
                    trailing != null -> trailing
                    showTrailingChevron && onClick != null && enabled -> {
                        { androidx.compose.material3.Icon(MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }
                    }
                    else -> null
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.clip(shape).padding(vertical = 1.dp),
            )
        }
    }
}
