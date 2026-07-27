package com.bernaferrari.bravedns.ui.compose.home

import android.os.Build
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ProtectionFlowShader(
            phase = phase,
            activeProgress = activeProgress,
            recoveringProgress = recoveringProgress,
            accent = accent,
            secondary = secondary,
            field = field,
            surface = surface,
            opacity = opacity,
            modifier = modifier,
        )
    } else {
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
}
