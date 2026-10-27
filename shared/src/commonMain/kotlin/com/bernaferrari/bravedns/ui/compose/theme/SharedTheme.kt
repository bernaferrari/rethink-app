@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Web-safe counterpart of the Android Rethink design system. Keep these values in lockstep
 * with app/ui/compose/theme so the same shared screen composes with the same visual language.
 */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

/** The brand coral. Every default surface of the app is generated from it. */
val RethinkBrandSeed = Color(0xFFFF5D73)
private val RethinkDefaultSeed = RethinkBrandSeed

/** Seed of the semantic "allowed" role: a clear green that never reads as the coral brand or as error red. */
private val RethinkAllowedSeed = Color(0xFF1E9E5A)

/**
 * The app's color scheme for a seed. Brand presets use the Fidelity style, which keeps the seed
 * itself as the primary container (coral stays coral instead of the muted maroon TonalSpot makes
 * of it); the wallpaper-derived dynamic scheme keeps TonalSpot. Dark schemes deepen the base
 * surface so tonal cards stand clearly off the background.
 */
fun rethinkColorScheme(seedColor: Color, isDark: Boolean, fromWallpaper: Boolean = false): ColorScheme {
    val scheme = dynamicColorScheme(
        seedColor = seedColor,
        isDark = isDark,
        style = if (fromWallpaper) PaletteStyle.TonalSpot else PaletteStyle.Fidelity,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
    return if (isDark) scheme.withDeepDarkSurfaces() else scheme
}

private const val DARK_SURFACE_DEEPEN = 0.42f
private const val DARK_CONTAINER_LIFT = 0.18f

private fun ColorScheme.withDeepDarkSurfaces(): ColorScheme {
    val deep = { c: Color -> lerp(c, Color.Black, DARK_SURFACE_DEEPEN) }
    val lift = { c: Color -> lerp(c, surfaceBright, DARK_CONTAINER_LIFT) }
    return copy(
        background = deep(background),
        surface = deep(surface),
        surfaceDim = deep(surfaceDim),
        surfaceContainerLowest = deep(surfaceContainerLowest),
        surfaceContainerLow = lift(surfaceContainerLow),
        surfaceContainer = lift(surfaceContainer),
        surfaceContainerHigh = lift(surfaceContainerHigh),
        surfaceContainerHighest = lift(surfaceContainerHighest),
    )
}

/** Semantic roles the Material scheme has no slot for. */
@Immutable
data class RethinkSemanticColors(
    val allowed: Color,
    val allowedContainer: Color,
    val onAllowedContainer: Color,
)

fun rethinkSemanticColors(isDark: Boolean): RethinkSemanticColors {
    val green = dynamicColorScheme(
        seedColor = RethinkAllowedSeed,
        isDark = isDark,
        style = PaletteStyle.TonalSpot,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
    val vivid = dynamicColorScheme(
        seedColor = RethinkAllowedSeed,
        isDark = isDark,
        style = PaletteStyle.Fidelity,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
    return RethinkSemanticColors(
        allowed = vivid.primary,
        allowedContainer = green.primaryContainer,
        onAllowedContainer = green.onPrimaryContainer,
    )
}

private val LightSemanticColors = rethinkSemanticColors(isDark = false)
private val DarkSemanticColors = rethinkSemanticColors(isDark = true)

fun rethinkSemanticColorsFor(isDark: Boolean) = if (isDark) DarkSemanticColors else LightSemanticColors

val LocalRethinkSemanticColors = staticCompositionLocalOf { LightSemanticColors }

/** Shorthand for the semantic roles of the current theme. */
val MaterialTheme.semanticColors: RethinkSemanticColors
    @Composable @ReadOnlyComposable get() = LocalRethinkSemanticColors.current

private val RethinkSharedShapes = Shapes(
    extraSmall = RoundedCornerShape(SharedDimensions.cornerRadiusXs),
    small = RoundedCornerShape(SharedDimensions.cornerRadiusSm),
    medium = RoundedCornerShape(SharedDimensions.cornerRadiusLg),
    large = RoundedCornerShape(SharedDimensions.cornerRadiusXl),
    extraLarge = RoundedCornerShape(SharedDimensions.cornerRadius2xl),
)

val RethinkTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

@Composable
fun RethinkSharedTheme(
    darkTheme: Boolean,
    seedColor: Color = RethinkDefaultSeed,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalRethinkMotion provides RethinkMotion(reducedMotion),
        LocalRethinkSemanticColors provides rethinkSemanticColorsFor(darkTheme),
    ) {
        MaterialExpressiveTheme(
            colorScheme = rethinkColorScheme(seedColor, darkTheme),
            typography = RethinkTypography,
            shapes = RethinkSharedShapes,
            content = content,
        )
    }
}
