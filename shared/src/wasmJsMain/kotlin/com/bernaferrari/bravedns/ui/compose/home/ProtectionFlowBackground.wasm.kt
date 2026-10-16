@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.bernaferrari.bravedns.ui.compose.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The browser renderer uses a dedicated WebGL2 layer behind Compose. Keeping the shader outside
 * Skia avoids CPU rasterization and lets the field cover the complete viewport, including the
 * adaptive navigation chrome.
 */
@Composable
internal actual fun ProtectionFlowBackground(
    phase: () -> Float,
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
    val active by rememberUpdatedState(activeProgress.coerceIn(0f, 1f))
    val recovering by rememberUpdatedState(recoveringProgress.coerceIn(0f, 1f))
    val colors by rememberUpdatedState(listOf(accent, secondary, field, surface))
    val strength by rememberUpdatedState(opacity.coerceIn(0f, 1f))
    // Every input is read inside snapshotFlow, so the WebGL layer updates per animation frame
    // without recomposing the screen that hosts it.
    LaunchedEffect(Unit) {
        snapshotFlow { ProtectionFlowFrame(phase(), active, recovering, colors, strength) }
            .collect { frame ->
                val (a, s, f, bg) = frame.colors
                updateProtectionFlow(
                    phase = frame.phase,
                    active = frame.active,
                    recovering = frame.recovering,
                    accentRed = a.red,
                    accentGreen = a.green,
                    accentBlue = a.blue,
                    secondaryRed = s.red,
                    secondaryGreen = s.green,
                    secondaryBlue = s.blue,
                    fieldRed = f.red,
                    fieldGreen = f.green,
                    fieldBlue = f.blue,
                    surfaceRed = bg.red,
                    surfaceGreen = bg.green,
                    surfaceBlue = bg.blue,
                    opacity = frame.opacity,
                )
            }
    }
    Box(modifier)
}

private data class ProtectionFlowFrame(
    val phase: Float,
    val active: Float,
    val recovering: Float,
    val colors: List<Color>,
    val opacity: Float,
)

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
