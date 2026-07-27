package com.bernaferrari.bravedns.ui.compose.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Platform-accelerated protection field where available. [phase] is normalized to 0..1 and all
 * motion is periodic, so the last frame joins the first without a discontinuity.
 */
@Composable
internal expect fun ProtectionFlowBackground(
    phase: Float,
    activeProgress: Float,
    recoveringProgress: Float,
    accent: Color,
    secondary: Color,
    field: Color,
    surface: Color,
    opacity: Float,
    modifier: Modifier = Modifier,
)
