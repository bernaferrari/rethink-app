/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.compose.theme.RethinkColorPreset
import com.bernaferrari.bravedns.util.Themes

/** Android preference and resource adapter for the commonMain appearance picker. */
enum class AppearanceMode {
    AUTO,
    LIGHT,
    DARK;

    fun toThemePreference(): Int = when (this) {
        AUTO -> Themes.SYSTEM_DEFAULT.id
        LIGHT -> Themes.LIGHT_PLUS.id
        DARK -> Themes.DARK_PLUS.id
    }

    companion object {
        fun fromThemePreference(preference: Int): AppearanceMode = when (preference) {
            Themes.SYSTEM_DEFAULT.id -> AUTO
            Themes.LIGHT.id, Themes.LIGHT_PLUS.id -> LIGHT
            else -> DARK
        }
    }
}

@Composable
fun AppearanceSettingsCard(
    themePreference: Int,
    colorPresetId: Int,
    onAppearanceModeSelected: (AppearanceMode) -> Unit,
    onColorPresetSelected: (RethinkColorPreset) -> Unit,
    // Accent, like every other section header on the Settings hub.
    sectionHeaderColor: Color = MaterialTheme.colorScheme.primary,
    showSectionHeader: Boolean = true,
) {
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val dynamicColor = if (dynamicSupported) {
        if (isSystemInDarkTheme()) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val labels = mapOf(
        RethinkColorPreset.DYNAMIC to stringResource(R.string.settings_theme_color_dynamic),
        RethinkColorPreset.CORAL to stringResource(R.string.settings_theme_color_coral),
        RethinkColorPreset.ROSE to stringResource(R.string.settings_theme_color_rose),
        RethinkColorPreset.ORANGE to stringResource(R.string.settings_theme_color_orange),
        RethinkColorPreset.AMBER to stringResource(R.string.settings_theme_color_amber),
        RethinkColorPreset.GREEN to stringResource(R.string.settings_theme_color_green),
        RethinkColorPreset.TEAL to stringResource(R.string.settings_theme_color_teal),
        RethinkColorPreset.CYAN to stringResource(R.string.settings_theme_color_cyan),
        RethinkColorPreset.BLUE to stringResource(R.string.settings_theme_color_blue),
        RethinkColorPreset.INDIGO to stringResource(R.string.settings_theme_color_indigo),
        RethinkColorPreset.PURPLE to stringResource(R.string.settings_theme_color_purple),
    )
    val selectable = listOf(
        RethinkColorPreset.DYNAMIC,
        RethinkColorPreset.CORAL,
        RethinkColorPreset.ROSE,
        RethinkColorPreset.ORANGE,
        RethinkColorPreset.AMBER,
        RethinkColorPreset.GREEN,
        RethinkColorPreset.TEAL,
        RethinkColorPreset.CYAN,
        RethinkColorPreset.BLUE,
        RethinkColorPreset.INDIGO,
        RethinkColorPreset.PURPLE,
    )
    RethinkAppearanceSettingsCard(
        selectedMode = AppearanceMode.fromThemePreference(themePreference).toShared(),
        selectedPresetId = RethinkColorPreset.fromId(colorPresetId).let {
            if (it == RethinkColorPreset.AUTO) RethinkColorPreset.CORAL.id else it.id
        },
        presets = selectable.map { preset ->
            RethinkAppearancePreset(
                id = preset.id,
                label = labels.getValue(preset),
                color = preset.seedColor,
                isDynamic = preset == RethinkColorPreset.DYNAMIC,
            )
        },
        strings = RethinkAppearanceStrings(
            heading = stringResource(R.string.settings_theme_heading),
            system = stringResource(R.string.settings_theme_dialog_themes_1),
            light = stringResource(R.string.settings_theme_dialog_themes_2),
            dark = stringResource(R.string.settings_theme_dialog_themes_3),
        ),
        dynamicColor = dynamicColor,
        dynamicSupported = dynamicSupported,
        onModeSelected = { onAppearanceModeSelected(it.toAndroid()) },
        onPresetSelected = { selected ->
            onColorPresetSelected(RethinkColorPreset.fromId(selected.id))
        },
        sectionHeaderColor = sectionHeaderColor,
        showSectionHeader = showSectionHeader,
    )
}

private fun AppearanceMode.toShared() = when (this) {
    AppearanceMode.AUTO -> RethinkAppearanceMode.System
    AppearanceMode.LIGHT -> RethinkAppearanceMode.Light
    AppearanceMode.DARK -> RethinkAppearanceMode.Dark
}

private fun RethinkAppearanceMode.toAndroid() = when (this) {
    RethinkAppearanceMode.System -> AppearanceMode.AUTO
    RethinkAppearanceMode.Light -> AppearanceMode.LIGHT
    RethinkAppearanceMode.Dark -> AppearanceMode.DARK
}

/**
 * Settings › App › Appearance: theme mode and color on their own screen (the Settings hub is
 * navigation only). Writes the same preferences the hub's inline card did, then tells the host.
 */
@Composable
fun AppearanceSettingsScreen(
    persistentState: PersistentState,
    eventLogger: EventLogger,
    onThemeModeChanged: ((Int) -> Unit)? = null,
    onThemeColorChanged: ((Int) -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
) {
    var theme by remember { mutableStateOf(persistentState.theme) }
    var preset by remember { mutableStateOf(persistentState.themeColorPreset) }
    RethinkTopBarLazyColumnScreen(
        title = stringResource(R.string.settings_theme_heading),
        onBackClick = onBackClick,
    ) {
        item {
            AppearanceSettingsCard(
                themePreference = theme,
                colorPresetId = preset,
                onAppearanceModeSelected = { mode ->
                    val themeId = mode.toThemePreference()
                    theme = themeId
                    persistentState.theme = themeId
                    onThemeModeChanged?.invoke(themeId)
                    eventLogger.log(EventType.UI_SETTING_CHANGED, Severity.LOW, "Appearance", EventSource.UI, true, "Theme set to ${mode.name.lowercase()}")
                },
                onColorPresetSelected = { selected ->
                    preset = selected.id
                    persistentState.themeColorPreset = selected.id
                    onThemeColorChanged?.invoke(selected.id)
                    eventLogger.log(EventType.UI_SETTING_CHANGED, Severity.LOW, "Appearance color", EventSource.UI, true, "Color preset set to ${selected.name.lowercase()}")
                },
                showSectionHeader = false,
            )
        }
    }
}
