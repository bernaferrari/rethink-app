/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Shared Material motion tokens used by Android and the browser demo. */
@Immutable
data class RethinkMotion(
    val reducedMotion: Boolean = false,
    val durationFast: Int = if (reducedMotion) 0 else 140,
    val durationMedium: Int = if (reducedMotion) 0 else 220,
)

val LocalRethinkMotion = staticCompositionLocalOf { RethinkMotion() }
