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
package com.celzero.bravedns.ui.compose.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.celzero.bravedns.util.Themes
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicMaterialThemeState

// Vibrant coral/orange seed — pops beautifully in Vibrant palette mode
private val RethinkSeedColor = Color(0xffFF6B4A)

// M3 Expressive shape scale — generous corner radii throughout
val RethinkShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

enum class RethinkThemeMode {
    LIGHT, DARK, TRUE_BLACK, LIGHT_PLUS, TRUE_BLACK_PLUS
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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = themeMode == RethinkThemeMode.DARK ||
            themeMode == RethinkThemeMode.TRUE_BLACK ||
            themeMode == RethinkThemeMode.TRUE_BLACK_PLUS

    // MaterialKolor generates a full expressive color scheme from a vibrant seed
    val colorScheme = rememberDynamicMaterialThemeState(
        seedColor = RethinkSeedColor,
        style = PaletteStyle.Vibrant,
        isDark = darkTheme,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    ).colorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = !darkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = RethinkShapes,
        content = content
    )
}
