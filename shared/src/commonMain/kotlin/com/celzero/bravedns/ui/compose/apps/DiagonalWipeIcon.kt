package com.celzero.bravedns.ui.compose.apps

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Immutable
object DiagonalWipeIconDefaults {
    const val EnableDurationMillis = 530
    const val DisableDurationMillis = 610
    val EnableEasing: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val DisableEasing: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    const val SeamOverlapPx = 0.8f
}

/** Shared two-layer diagonal icon transition used by app and firewall rows. */
@Composable
fun DiagonalWipeIcon(
    blocked: Boolean,
    allowedIcon: ImageVector,
    blockedIcon: ImageVector,
    allowedTint: Color,
    blockedTint: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enableDurationMillis: Int = DiagonalWipeIconDefaults.EnableDurationMillis,
    disableDurationMillis: Int = DiagonalWipeIconDefaults.DisableDurationMillis,
    enableEasing: Easing = DiagonalWipeIconDefaults.EnableEasing,
    disableEasing: Easing = DiagonalWipeIconDefaults.DisableEasing,
    seamOverlapPx: Float = DiagonalWipeIconDefaults.SeamOverlapPx,
) {
    val transition = updateTransition(targetState = blocked, label = "diagonalWipeIcon")
    val allowedPainter = rememberVectorPainter(allowedIcon)
    val blockedPainter = rememberVectorPainter(blockedIcon)
    val reveal by transition.animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) tween(enableDurationMillis, easing = enableEasing)
            else tween(disableDurationMillis, easing = disableEasing)
        },
        label = "diagonalWipeReveal",
    ) { if (it) 1f else 0f }
    val progress = reveal.coerceIn(0f, 1f)

    Box(
        modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        when {
            progress <= .001f -> Icon(allowedIcon, contentDescription, Modifier.fillMaxSize(), allowedTint)
            progress >= .999f -> Icon(blockedIcon, contentDescription, Modifier.fillMaxSize(), blockedTint)
            else -> Canvas(Modifier.fillMaxSize()) {
                val diagonalProgress = ((progress * (size.width + size.height) + seamOverlapPx) / (size.width + size.height)).coerceIn(0f, 1f)
                val path = diagonalRevealPath(size.width, size.height, diagonalProgress)
                clipPath(path, ClipOp.Difference) { with(allowedPainter) { draw(size, colorFilter = ColorFilter.tint(allowedTint)) } }
                clipPath(path, ClipOp.Intersect) { with(blockedPainter) { draw(size, colorFilter = ColorFilter.tint(blockedTint)) } }
            }
        }
    }
}

private fun diagonalRevealPath(width: Float, height: Float, progress: Float): Path = Path().apply {
    if (progress <= 0f) return@apply
    if (progress >= 1f) { moveTo(0f, 0f); lineTo(width, 0f); lineTo(width, height); lineTo(0f, height); close(); return@apply }
    val diagonal = (width + height) * progress
    moveTo(0f, 0f); lineTo(diagonal.coerceAtMost(width), 0f)
    if (diagonal > width) lineTo(width, (diagonal - width).coerceAtMost(height))
    if (diagonal > height) lineTo((diagonal - height).coerceAtMost(width), height)
    lineTo(0f, diagonal.coerceAtMost(height)); close()
}
