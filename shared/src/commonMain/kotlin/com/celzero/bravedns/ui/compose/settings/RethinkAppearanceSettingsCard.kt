/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.celzero.bravedns.ui.compose.settings

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

enum class RethinkAppearanceMode { System, Light, Dark }

data class RethinkAppearancePreset(
    val id: Int,
    val label: String,
    val color: Color?,
    val isDynamic: Boolean = false,
)

data class RethinkAppearanceStrings(
    val heading: String,
    val system: String,
    val light: String,
    val dark: String,
)

/**
 * Common theme picker. It intentionally always retains the responsive selection animation; hosts
 * are responsible only for applying the new persisted preference and, on Android, querying the
 * platform dynamic swatch.
 */
@Composable
fun RethinkAppearanceSettingsCard(
    selectedMode: RethinkAppearanceMode,
    selectedPresetId: Int,
    presets: List<RethinkAppearancePreset>,
    strings: RethinkAppearanceStrings,
    dynamicColor: Color,
    dynamicSupported: Boolean,
    onModeSelected: (RethinkAppearanceMode) -> Unit,
    onPresetSelected: (RethinkAppearancePreset) -> Unit,
    modifier: Modifier = Modifier,
    sectionHeaderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showSectionHeader: Boolean = true,
) {
    var appearanceMode by remember(selectedMode) { mutableStateOf(selectedMode) }
    var colorPresetId by remember(selectedPresetId) { mutableStateOf(selectedPresetId) }
    // Dynamic colors are supplied by Android. On targets without that platform capability, do not
    // render a disabled, non-functional option or an availability disclaimer: the picker should
    // contain only choices that can actually be applied.
    val visiblePresets = remember(presets, dynamicSupported) {
        presets.filterNot { it.isDynamic && !dynamicSupported }
    }
    val displayedPresetId =
        colorPresetId.takeIf { selectedId -> visiblePresets.any { it.id == selectedId } }
            ?: visiblePresets.firstOrNull()?.id
    val modes = listOf(
        RethinkAppearanceMode.System to strings.system,
        RethinkAppearanceMode.Light to strings.light,
        RethinkAppearanceMode.Dark to strings.dark,
    )

    Column(modifier = modifier) {
        if (showSectionHeader) {
            Text(
                text = strings.heading,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = sectionHeaderColor,
                modifier = Modifier.padding(top = SharedDimensions.spacingMd, bottom = SharedDimensions.spacingSm),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            modes.forEachIndexed { index, (mode, label) ->
                val selected = mode == appearanceMode
                ToggleButton(
                    checked = selected,
                    onCheckedChange = { checked ->
                        if (checked && mode != appearanceMode) {
                            appearanceMode = mode
                            onModeSelected(mode)
                        }
                    },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.semantics { role = Role.RadioButton },
                ) {
                    Icon(
                        imageVector = if (selected) MaterialSymbols.Filled.Check else mode.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(start = SharedDimensions.spacingSm, end = SharedDimensions.spacingSm, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            visiblePresets.forEach { preset ->
                RethinkThemeColorSwatch(
                    preset = preset,
                    selected = preset.id == displayedPresetId,
                    enabled = true,
                    dynamicColor = dynamicColor,
                    onClick = {
                        if (preset.id != colorPresetId) {
                            colorPresetId = preset.id
                            onPresetSelected(preset)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RethinkThemeColorSwatch(
    preset: RethinkAppearancePreset,
    selected: Boolean,
    enabled: Boolean,
    dynamicColor: Color,
    onClick: () -> Unit,
) {
    val baseColor = if (preset.isDynamic) dynamicColor else preset.color ?: dynamicColor
    val displayColor = if (enabled) baseColor else baseColor.copy(alpha = 0.42f)
    val interactionSource = remember { MutableInteractionSource() }
    val cornerFraction by animateFloatAsState(
        targetValue = if (selected) 0.5f else 0.26f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 520f),
        label = "appearance_swatch_corner_${preset.id}",
    )
    val orbScale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 0.86f,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 600f),
        label = "appearance_swatch_scale_${preset.id}",
    )
    val orbRotation by animateFloatAsState(
        targetValue = if (selected) 8f else 0f,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = 420f),
        label = "appearance_swatch_rotation_${preset.id}",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "appearance_swatch_glow_${preset.id}",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "appearance_swatch_icon_${preset.id}",
    )
    val orbShape = RoundedCornerShape(percent = (cornerFraction * 100).toInt())

    Box(
        modifier = Modifier
            .size(50.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.52f }
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = preset.label
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(56.dp).graphicsLayer { alpha = glowAlpha }.drawBehind {
                drawCircle(color = displayColor.copy(alpha = 0.44f), radius = size.minDimension * 0.5f)
            },
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer { scaleX = orbScale; scaleY = orbScale; rotationZ = orbRotation }
                .clip(orbShape)
                .indication(interactionSource, ripple(bounded = true, radius = 18.dp, color = Color.White.copy(alpha = 0.32f)))
                .background(displayColor),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                        start = Offset.Zero,
                        end = Offset(60f, 60f),
                    ),
                ),
            )
            when {
                selected -> Icon(MaterialSymbols.Filled.Check, null, Modifier.size(18.dp).graphicsLayer { alpha = iconAlpha; rotationZ = -orbRotation }, Color.White)
                preset.isDynamic -> Icon(MaterialSymbols.Filled.Palette, null, Modifier.size(16.dp), Color.White.copy(alpha = 0.88f))
            }
        }
    }
}

private fun RethinkAppearanceMode.icon() = when (this) {
    RethinkAppearanceMode.System -> MaterialSymbols.Filled.BrightnessAuto
    RethinkAppearanceMode.Light -> MaterialSymbols.Filled.LightMode
    RethinkAppearanceMode.Dark -> MaterialSymbols.Filled.DarkMode
}
