/*
 * Copyright 2026 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.settings

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.RethinkColorPreset
import com.celzero.bravedns.ui.compose.theme.RethinkSegmentedChoiceRow
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.ui.compose.theme.rememberReducedMotion
import com.celzero.bravedns.util.Themes

enum class AppearanceMode {
    AUTO,
    LIGHT,
    DARK;

    fun toThemePreference(): Int {
        return when (this) {
            AUTO -> Themes.SYSTEM_DEFAULT.id
            LIGHT -> Themes.LIGHT_PLUS.id
            DARK -> Themes.DARK_PLUS.id
        }
    }

    fun icon(): ImageVector {
        return when (this) {
            AUTO -> Icons.Rounded.BrightnessAuto
            LIGHT -> Icons.Rounded.LightMode
            DARK -> Icons.Rounded.DarkMode
        }
    }

    companion object {
        fun fromThemePreference(preference: Int): AppearanceMode {
            return when (preference) {
                Themes.SYSTEM_DEFAULT.id -> AUTO
                Themes.LIGHT.id, Themes.LIGHT_PLUS.id -> LIGHT
                else -> DARK
            }
        }
    }
}

@Composable
fun AppearanceSettingsCard(
    themePreference: Int,
    colorPresetId: Int,
    onAppearanceModeSelected: (AppearanceMode) -> Unit,
    onColorPresetSelected: (RethinkColorPreset) -> Unit,
    modeHighlightAlpha: Float = 0f,
    colorHighlightAlpha: Float = 0f,
    showSectionHeader: Boolean = true
) {
    var appearanceMode by remember(themePreference) {
        mutableStateOf(AppearanceMode.fromThemePreference(themePreference))
    }
    var colorPreset by remember(colorPresetId) {
        mutableStateOf(RethinkColorPreset.fromId(colorPresetId))
    }

    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val selectableColorPresets = remember {
        RethinkColorPreset.entries.filterNot { it == RethinkColorPreset.AUTO }
    }

    Column {
        if (showSectionHeader) {
            SectionHeader(
                title = stringResource(R.string.settings_theme_heading)
            )
        }

        Surface(
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
            color = lerp(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
                modeHighlightAlpha
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
                RethinkSegmentedChoiceRow(
                    options = AppearanceMode.entries,
                    selectedOption = appearanceMode,
                    onOptionSelected = { option ->
                        if (appearanceMode != option) {
                            appearanceMode = option
                            onAppearanceModeSelected(option)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    fillEqually = true,
                    minHeight = 38.dp,
                    icon = { option, isSelected ->
                        SegmentedButtonDefaults.Icon(active = isSelected) {
                            Icon(
                                imageVector = if (isSelected) Icons.Filled.Check else option.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                            )
                        }
                    },
                    label = { option, _ ->
                        Text(
                            text = option.toDisplayName(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
            color = lerp(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
                colorHighlightAlpha
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(
                        items = selectableColorPresets,
                        key = { it.id }
                    ) { preset ->
                        ThemeColorSwatch(
                            preset = preset,
                            isSelected = preset == colorPreset,
                            isEnabled = preset != RethinkColorPreset.DYNAMIC || dynamicSupported,
                            onClick = {
                                if (preset == colorPreset) return@ThemeColorSwatch
                                colorPreset = preset
                                onColorPresetSelected(preset)
                            }
                        )
                    }
                }

                if (!dynamicSupported) {
                    Text(
                        text = stringResource(id = R.string.settings_theme_color_dynamic_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeColorSwatch(
    preset: RethinkColorPreset,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val reducedMotion = rememberReducedMotion()
    val outlineColor = MaterialTheme.colorScheme.outline
    val displayColor = when (preset) {
        RethinkColorPreset.AUTO -> MaterialTheme.colorScheme.secondary
        RethinkColorPreset.DYNAMIC -> MaterialTheme.colorScheme.primary
        else -> preset.seedColor ?: MaterialTheme.colorScheme.primary
    }.let { color ->
        if (isEnabled) color else color.copy(alpha = 0.35f)
    }

    val cornerFraction by animateFloatAsState(
        targetValue = if (isSelected) 0.5f else 0.26f,
        animationSpec = tween(durationMillis = 170),
        label = "swatch_corner_${preset.id}"
    )
    val fillScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.85f,
        animationSpec = tween(durationMillis = 170),
        label = "swatch_scale_${preset.id}"
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 170),
        label = "swatch_ring_${preset.id}"
    )
    val wobble = remember(preset.id) { Animatable(0f) }

    LaunchedEffect(isSelected, reducedMotion) {
        if (!isSelected || reducedMotion) {
            wobble.snapTo(0f)
            return@LaunchedEffect
        }

        wobble.snapTo(0f)
        wobble.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 110))
        wobble.animateTo(targetValue = -0.45f, animationSpec = tween(durationMillis = 100))
        wobble.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 90))
    }

    Surface(
        onClick = onClick,
        enabled = isEnabled,
        color = Color.Transparent,
        shape = CircleShape,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (ringAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer { alpha = ringAlpha }
                        .drawBehind {
                            drawCircle(
                                color = outlineColor,
                                radius = size.minDimension / 2f,
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        scaleX = fillScale
                        scaleY = fillScale
                        rotationZ = wobble.value * 8f
                    }
                    .clip(RoundedCornerShape(percent = (cornerFraction * 100).toInt()))
                    .background(displayColor),
                contentAlignment = Alignment.Center
            ) {
                val iconLabel = when (preset) {
                    RethinkColorPreset.AUTO -> "A"
                    RethinkColorPreset.DYNAMIC -> "D"
                    else -> null
                }
                when {
                    isSelected -> {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = preset.toDisplayName(),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    iconLabel != null -> {
                        Text(
                            text = iconLabel,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceMode.toDisplayName(): String {
    return when (this) {
        AppearanceMode.AUTO -> stringResource(id = R.string.settings_theme_dialog_themes_1)
        AppearanceMode.LIGHT -> stringResource(id = R.string.settings_theme_dialog_themes_2)
        AppearanceMode.DARK -> stringResource(id = R.string.settings_theme_dialog_themes_3)
    }
}

@Composable
private fun RethinkColorPreset.toDisplayName(): String {
    return when (this) {
        RethinkColorPreset.AUTO -> stringResource(id = R.string.settings_theme_color_auto)
        RethinkColorPreset.DYNAMIC -> stringResource(id = R.string.settings_theme_color_dynamic)
        RethinkColorPreset.CORAL -> stringResource(id = R.string.settings_theme_color_coral)
        RethinkColorPreset.TEAL -> stringResource(id = R.string.settings_theme_color_teal)
        RethinkColorPreset.BLUE -> stringResource(id = R.string.settings_theme_color_blue)
        RethinkColorPreset.PURPLE -> stringResource(id = R.string.settings_theme_color_purple)
        RethinkColorPreset.ORANGE -> stringResource(id = R.string.settings_theme_color_orange)
        RethinkColorPreset.GREEN -> stringResource(id = R.string.settings_theme_color_green)
    }
}
