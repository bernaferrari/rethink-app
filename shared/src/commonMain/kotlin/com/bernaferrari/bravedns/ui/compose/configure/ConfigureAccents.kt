/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.configure

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
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
    val appearance = Color(0xFFFF9EC4)
    val appLock = Color(0xFF8FD0FF)
    val notifications = Color(0xFFFFB86B)
    val backup = Color(0xFF7FE0D0)
    val diagnostics = Color(0xFFB7C7FF)
    val about = Color(0xFFC9C3B8)
}

data class ConfigureTileColors(
    val fill: Color,
    val well: Color,
    val icon: Color,
    val title: Color,
    val edge: Color,
)

/**
 * One rule for every well, whatever its hue: the hue's container tone with its on-container icon
 * (a pale well and a dark icon in light mode, a deep well and a pale icon in dark mode), so no
 * tile carries a light icon beside a dark one.
 */
@Composable
fun configureTileColors(accent: Color): ConfigureTileColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val hue = if (accent == Color.Unspecified) scheme.primary else harmonize(accent, scheme.primary)
    val tones = remember(hue, dark) {
        dynamicColorScheme(seedColor = hue, isDark = dark, style = PaletteStyle.Vibrant, specVersion = ColorSpec.SpecVersion.SPEC_2025)
    }
    return ConfigureTileColors(
        fill = scheme.surfaceContainerLow,
        well = tones.primaryContainer,
        icon = tones.onPrimaryContainer,
        title = scheme.onSurface,
        edge = Color.Transparent,
    )
}

private const val MAX_HARMONIZE_DEGREES = 15f

/**
 * Material color harmonization: rotate [design]'s hue toward [source]'s by half the distance,
 * capped at 15°, keeping saturation and value. Category accents stay distinguishable while
 * sitting inside the user's (possibly dynamic) palette instead of a fixed rainbow.
 */
internal fun harmonize(design: Color, source: Color): Color {
    val (designHue, saturation, value) = design.toHsv()
    val sourceHue = source.toHsv().first
    val delta = ((sourceHue - designHue + 540f) % 360f) - 180f
    val rotation = minOf(kotlin.math.abs(delta) * 0.5f, MAX_HARMONIZE_DEGREES) * kotlin.math.sign(delta)
    val hue = (designHue + rotation + 360f) % 360f
    return Color.hsv(hue, saturation, value, design.alpha)
}

private fun Color.toHsv(): Triple<Float, Float, Float> {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val chroma = max - min
    val hue = when {
        chroma == 0f -> 0f
        max == red -> 60f * (((green - blue) / chroma) % 6f)
        max == green -> 60f * (((blue - red) / chroma) + 2f)
        else -> 60f * (((red - green) / chroma) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (max == 0f) 0f else chroma / max
    return Triple(hue, saturation, max)
}
