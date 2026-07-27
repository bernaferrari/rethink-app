@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.bernaferrari.bravedns.ui.compose.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The browser renderer uses a dedicated WebGL2 layer behind Compose. Keeping the shader outside
 * Skia avoids CPU rasterization and lets the field cover the complete viewport, including the
 * adaptive navigation chrome.
 */
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
    DisposableEffect(Unit) {
        mountProtectionFlow()
        onDispose { unmountProtectionFlow() }
    }
    SideEffect {
        updateProtectionFlow(
            phase = phase,
            active = activeProgress.coerceIn(0f, 1f),
            recovering = recoveringProgress.coerceIn(0f, 1f),
            accentRed = accent.red,
            accentGreen = accent.green,
            accentBlue = accent.blue,
            secondaryRed = secondary.red,
            secondaryGreen = secondary.green,
            secondaryBlue = secondary.blue,
            fieldRed = field.red,
            fieldGreen = field.green,
            fieldBlue = field.blue,
            surfaceRed = surface.red,
            surfaceGreen = surface.green,
            surfaceBlue = surface.blue,
            opacity = opacity.coerceIn(0f, 1f),
        )
    }
    Box(modifier)
}

@JsFun("() => globalThis.rethinkProtectionFlow.mount()")
private external fun mountProtectionFlow()

@JsFun("() => globalThis.rethinkProtectionFlow.unmount()")
private external fun unmountProtectionFlow()

@JsFun(
    """(
        phase,
        active,
        recovering,
        accentRed,
        accentGreen,
        accentBlue,
        secondaryRed,
        secondaryGreen,
        secondaryBlue,
        fieldRed,
        fieldGreen,
        fieldBlue,
        surfaceRed,
        surfaceGreen,
        surfaceBlue,
        opacity
    ) => globalThis.rethinkProtectionFlow.update(
        phase,
        active,
        recovering,
        accentRed,
        accentGreen,
        accentBlue,
        secondaryRed,
        secondaryGreen,
        secondaryBlue,
        fieldRed,
        fieldGreen,
        fieldBlue,
        surfaceRed,
        surfaceGreen,
        surfaceBlue,
        opacity
    )""",
)
private external fun updateProtectionFlow(
    phase: Float,
    active: Float,
    recovering: Float,
    accentRed: Float,
    accentGreen: Float,
    accentBlue: Float,
    secondaryRed: Float,
    secondaryGreen: Float,
    secondaryBlue: Float,
    fieldRed: Float,
    fieldGreen: Float,
    fieldBlue: Float,
    surfaceRed: Float,
    surfaceGreen: Float,
    surfaceBlue: Float,
    opacity: Float,
)
