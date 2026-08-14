/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.configure

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Configure is the only surface that uses a destination rainbow, and only on the icon well.
 * The card itself stays on the same quiet container as the rest of Settings.
 */
object ConfigureAccents {
    val apps = Color(0xFF74C5FF)
    val dns = Color(0xFFC5ACFF)
    val firewall = Color(0xFFFF907F)
    val proxy = Color(0xFF46EBC8)
    val network = Color(0xFFA3BCFF)
    val settings = Color(0xFFFFD878)
    val logs = Color(0xFF7EED92)
    val antiCensorship = Color(0xFFFFA7E0)
    val advanced = Color(0xFFFFE182)
    val plus = Color(0xFF9B8CFF)
}

data class ConfigureTileColors(
    val fill: Color,
    val well: Color,
    val icon: Color,
    val title: Color,
    val edge: Color,
)

@Composable
fun configureTileColors(accent: Color): ConfigureTileColors {
    val scheme = MaterialTheme.colorScheme
    val hue = if (accent == Color.Unspecified) scheme.primary else accent
    val well = if (scheme.surface.luminance() < 0.5f) lerp(hue, Color.Black, 0.18f) else lerp(hue, Color.White, 0.04f)
    val onWell = if (well.luminance() < 0.5f) Color(0xFFF6F3EE) else Color(0xFF1C1B18)
    return ConfigureTileColors(
        fill = scheme.surfaceContainerLow,
        well = well,
        icon = onWell,
        title = scheme.onSurface,
        edge = Color.Transparent,
    )
}
