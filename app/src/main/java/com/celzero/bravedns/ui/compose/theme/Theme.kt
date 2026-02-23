/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.celzero.bravedns.ui.compose.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.celzero.bravedns.util.Themes
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicMaterialThemeState

private val RethinkCoralSeed = Color(0xffFF6B4A)
private val RethinkTealSeed = Color(0xff009688)
private val RethinkBlueSeed = Color(0xff2962FF)
private val RethinkPurpleSeed = Color(0xff7E57C2)
private val RethinkOrangeSeed = Color(0xffF57C00)
private val RethinkGreenSeed = Color(0xff2E7D32)

// M3 Expressive shape scale — generous corner radii throughout
val RethinkShapes = Shapes(
    extraSmall = RoundedCornerShape(Dimensions.cornerRadiusSm),
    small = RoundedCornerShape(Dimensions.cornerRadiusSmMd),
    medium = RoundedCornerShape(Dimensions.cornerRadiusLg),
    large = RoundedCornerShape(Dimensions.cornerRadius2xl),
    extraLarge = RoundedCornerShape(Dimensions.heroCornerRadius)
)

enum class RethinkThemeMode {
    LIGHT, DARK, TRUE_BLACK, LIGHT_PLUS, TRUE_BLACK_PLUS
}

enum class RethinkColorPreset(val id: Int, val seedColor: Color?) {
    AUTO(0, null),
    DYNAMIC(1, null),
    CORAL(2, RethinkCoralSeed),
    TEAL(3, RethinkTealSeed),
    BLUE(4, RethinkBlueSeed),
    PURPLE(5, RethinkPurpleSeed),
    ORANGE(6, RethinkOrangeSeed),
    GREEN(7, RethinkGreenSeed);

    companion object {
        fun fromId(id: Int): RethinkColorPreset {
            return entries.firstOrNull { it.id == id } ?: AUTO
        }
    }
}

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
    themePreference: Int = Themes.SYSTEM_DEFAULT.id,
    colorPreset: RethinkColorPreset = RethinkColorPreset.AUTO,
    content: @Composable () -> Unit
) {
    val darkTheme = themeMode == RethinkThemeMode.DARK ||
            themeMode == RethinkThemeMode.TRUE_BLACK ||
            themeMode == RethinkThemeMode.TRUE_BLACK_PLUS

    val useDynamicColor = when (colorPreset) {
        RethinkColorPreset.AUTO -> Themes.useDynamicColor(themePreference)
        RethinkColorPreset.DYNAMIC -> true
        else -> false
    } && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val seedColor = colorPreset.seedColor ?: RethinkCoralSeed

    val colorScheme = when {
        useDynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        }

        else ->
            rememberDynamicMaterialThemeState(
                seedColor = seedColor,
                style = PaletteStyle.TonalSpot,
                isDark = darkTheme,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
            ).colorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = !darkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = RethinkShapes,
        content = content
    )
}
