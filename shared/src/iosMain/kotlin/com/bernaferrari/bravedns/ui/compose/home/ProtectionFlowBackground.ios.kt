package com.bernaferrari.bravedns.ui.compose.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal actual fun ProtectionFlowBackground(
    phase: Float,
    activeProgress: Float,
    recoveringProgress: Float,
    accent: Color,
    secondary: Color,
    field: Color,
    surface: Color,
    opacity: Float,
    modifier: Modifier,
) {
    ProtectionFlowCanvas(
        phase,
        activeProgress,
        recoveringProgress,
        accent,
        secondary,
        field,
        surface,
        opacity,
        modifier,
    )
}
