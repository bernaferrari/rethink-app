package com.celzero.bravedns.ui.compose.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.util.Themes

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val TrueBlackColorScheme = darkColorScheme(
    primary = PrimaryBlack,
    onPrimary = OnPrimaryBlack,
    primaryContainer = PrimaryContainerBlack,
    onPrimaryContainer = OnPrimaryContainerBlack,
    secondary = SecondaryBlack,
    onSecondary = OnSecondaryBlack,
    secondaryContainer = SecondaryContainerBlack,
    onSecondaryContainer = OnSecondaryContainerBlack,
    tertiary = TertiaryBlack,
    onTertiary = OnTertiaryBlack,
    tertiaryContainer = TertiaryContainerBlack,
    onTertiaryContainer = OnTertiaryContainerBlack,
    background = BackgroundBlack,
    onBackground = OnBackgroundBlack,
    surface = SurfaceBlack,
    onSurface = OnSurfaceBlack,
    surfaceVariant = SurfaceVariantBlack,
    onSurfaceVariant = OnSurfaceVariantBlack,
    outline = OutlineBlack,
    error = ErrorBlack,
    onError = OnErrorBlack,
    errorContainer = ErrorContainerBlack,
    onErrorContainer = OnErrorContainerBlack
)

private val LightPlusColorScheme = lightColorScheme(
    primary = PrimaryLightPlus,
    onPrimary = OnPrimaryLightPlus,
    primaryContainer = PrimaryContainerLightPlus,
    onPrimaryContainer = OnPrimaryContainerLightPlus,
    secondary = SecondaryLightPlus,
    onSecondary = OnSecondaryLightPlus,
    secondaryContainer = SecondaryContainerLightPlus,
    onSecondaryContainer = OnSecondaryContainerLightPlus,
    background = BackgroundLightPlus,
    onBackground = OnBackgroundLightPlus,
    surface = SurfaceLightPlus,
    onSurface = OnSurfaceLightPlus,
    surfaceVariant = SurfaceVariantLightPlus,
    onSurfaceVariant = OnSurfaceVariantLightPlus,
    outline = OutlineLightPlus,
    error = AccentBadLightPlus,
    tertiary = AccentGoodLightPlus
)

private val TrueBlackPlusColorScheme = darkColorScheme(
    primary = PrimaryBlackPlus,
    onPrimary = OnPrimaryBlackPlus,
    primaryContainer = PrimaryContainerBlackPlus,
    onPrimaryContainer = OnPrimaryContainerBlackPlus,
    secondary = SecondaryBlackPlus,
    onSecondary = OnSecondaryBlackPlus,
    secondaryContainer = SecondaryContainerBlackPlus,
    onSecondaryContainer = OnSecondaryContainerBlackPlus,
    background = BackgroundBlackPlus,
    onBackground = OnBackgroundBlackPlus,
    surface = SurfaceBlackPlus,
    onSurface = OnSurfaceBlackPlus,
    surfaceVariant = SurfaceVariantBlackPlus,
    onSurfaceVariant = OnSurfaceVariantBlackPlus,
    outline = OutlineBlackPlus,
    error = AccentBadBlackPlus,
    tertiary = AccentGoodBlackPlus
)

enum class RethinkThemeMode {
    LIGHT, DARK, TRUE_BLACK, LIGHT_PLUS, TRUE_BLACK_PLUS
}

private val RethinkShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

fun mapThemePreferenceToComposeMode(preference: Int, isSystemDark: Boolean): RethinkThemeMode {
    return when (preference) {
        Themes.LIGHT.id -> RethinkThemeMode.LIGHT
        Themes.DARK.id -> RethinkThemeMode.DARK
        Themes.TRUE_BLACK.id -> RethinkThemeMode.TRUE_BLACK
        Themes.LIGHT_PLUS.id -> RethinkThemeMode.LIGHT_PLUS
        Themes.DARK_PLUS.id, Themes.DARK_FROST.id -> RethinkThemeMode.TRUE_BLACK_PLUS
        else -> if (isSystemDark) RethinkThemeMode.TRUE_BLACK_PLUS else RethinkThemeMode.LIGHT_PLUS
    }
}

@Composable
fun RethinkTheme(
    themeMode: RethinkThemeMode = if (isSystemInDarkTheme()) RethinkThemeMode.TRUE_BLACK_PLUS else RethinkThemeMode.LIGHT_PLUS,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (themeMode == RethinkThemeMode.DARK || themeMode == RethinkThemeMode.TRUE_BLACK || themeMode == RethinkThemeMode.TRUE_BLACK_PLUS) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        themeMode == RethinkThemeMode.LIGHT -> LightColorScheme
        themeMode == RethinkThemeMode.DARK -> DarkColorScheme
        themeMode == RethinkThemeMode.TRUE_BLACK -> TrueBlackColorScheme
        themeMode == RethinkThemeMode.LIGHT_PLUS -> LightPlusColorScheme
        themeMode == RethinkThemeMode.TRUE_BLACK_PLUS -> TrueBlackPlusColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = RethinkShapes,
        content = content
    )
}
