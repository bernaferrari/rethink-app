/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Shared Material motion tokens used by Android and the browser demo. */
@Immutable
data class RethinkMotion(
    val reducedMotion: Boolean = false,
    val durationFast: Int = if (reducedMotion) 0 else 140,
    val durationMedium: Int = if (reducedMotion) 0 else 220,
    val durationExit: Int = if (reducedMotion) 0 else 180,
    val durationScreen: Int = if (reducedMotion) 0 else 320,
    val easingStandard: CubicBezierEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val easingDecelerate: CubicBezierEasing = CubicBezierEasing(0f, 0f, 0f, 1f),
    val easingAccelerate: CubicBezierEasing = CubicBezierEasing(0.3f, 0f, 1f, 1f),
)

val LocalRethinkMotion = staticCompositionLocalOf { RethinkMotion() }
