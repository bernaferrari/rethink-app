/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.ui.compose.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 window-width buckets derived from the nearest bounded [BoxWithConstraints]
 * width. Compact and Medium keep the phone layout; Expanded is the tablet / wide wasm pane.
 *
 * Thresholds match the M3 Compact / Medium / Expanded width classes (600 / 840 dp).
 * Unbounded or zero-width probes (CMP-8543 on wasm) fall back to [WindowWidthClass.Compact].
 */
enum class WindowWidthClass { Compact, Medium, Expanded }

val WindowWidthClass.isExpanded: Boolean
    get() = this == WindowWidthClass.Expanded

object RethinkWindowBreakpoints {
    val CompactMax = 600.dp
    val MediumMax = 840.dp

    /** Same cap as [com.bernaferrari.bravedns.ui.compose.navigation.RethinkCenteredScreen]. */
    val ContentMax = 720.dp
}

fun windowWidthClass(maxWidth: Dp): WindowWidthClass {
    val width = maxWidth.value
    if (!width.isFinite() || width <= 0f) return WindowWidthClass.Compact
    return when {
        maxWidth < RethinkWindowBreakpoints.CompactMax -> WindowWidthClass.Compact
        maxWidth < RethinkWindowBreakpoints.MediumMax -> WindowWidthClass.Medium
        else -> WindowWidthClass.Expanded
    }
}

@Composable
fun RethinkAdaptiveWidth(
    modifier: Modifier = Modifier,
    content: @Composable (widthClass: WindowWidthClass) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val resolvedWidth = if (constraints.hasBoundedWidth && constraints.maxWidth > 0) {
            maxWidth
        } else {
            0.dp
        }
        content(windowWidthClass(resolvedWidth))
    }
}

fun Modifier.rethinkContentWidth(widthClass: WindowWidthClass): Modifier =
    if (widthClass.isExpanded) {
        fillMaxWidth().widthIn(max = RethinkWindowBreakpoints.ContentMax)
    } else {
        fillMaxWidth()
    }
