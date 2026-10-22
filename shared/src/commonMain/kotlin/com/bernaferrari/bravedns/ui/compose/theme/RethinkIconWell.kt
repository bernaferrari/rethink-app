/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/** Default tonal wash behind a leading icon (list rows, sheet headers). */
val RethinkIconWellContainerColor: Color
    @Composable get() = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)

object RethinkIconWellDefaults {
    /** Alpha of an accent-tinted wash ([tinted]). */
    const val TintedContainerAlpha = 0.14f

    /** Container for a well tinted by a data/status accent instead of the primary wash. */
    fun tinted(accent: Color): Color = accent.copy(alpha = TintedContainerAlpha)
}

/**
 * The one leading-icon container: a tinted rounded well that centers its content. [contentColor]
 * is provided as [LocalContentColor] so plain `Icon(...)` content picks it up.
 */
@Composable
fun RethinkIconWell(
    modifier: Modifier = Modifier,
    containerColor: Color = RethinkIconWellContainerColor,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    size: Dp = SharedDimensions.iconContainerMd,
    shape: Shape = RoundedCornerShape(SharedDimensions.iconContainerRadius),
    content: @Composable () -> Unit,
) {
    Surface(shape = shape, color = containerColor, contentColor = contentColor, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
        }
    }
}

/** [RethinkIconWell] holding a single decorative (or described) vector icon. */
@Composable
fun RethinkIconWell(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = RethinkIconWellContainerColor,
    size: Dp = SharedDimensions.iconContainerMd,
    iconSize: Dp = SharedDimensions.iconSizeSm,
    shape: Shape = RoundedCornerShape(SharedDimensions.iconContainerRadius),
    contentDescription: String? = null,
) = RethinkIconWell(modifier, containerColor, tint, size, shape) {
    Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
}
