/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.settings

import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape

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

/** Product palette used by Settings. Android maps these ids onto [RethinkColorPreset]. */
val rethinkAppearancePresets = listOf(
    RethinkAppearancePreset(1, "Dynamic", null, isDynamic = true),
    RethinkAppearancePreset(2, "Coral", Color(0xFFFF5D73)),
    RethinkAppearancePreset(11, "Rose", Color(0xFFEC4899)),
    RethinkAppearancePreset(6, "Orange", Color(0xFFF97316)),
    RethinkAppearancePreset(8, "Amber", Color(0xFFF2B705)),
    RethinkAppearancePreset(7, "Green", Color(0xFF22C55E)),
    RethinkAppearancePreset(3, "Teal", Color(0xFF14B8A6)),
    RethinkAppearancePreset(9, "Cyan", Color(0xFF06B6D4)),
    RethinkAppearancePreset(4, "Blue", Color(0xFF3B82F6)),
    RethinkAppearancePreset(10, "Indigo", Color(0xFF6366F1)),
    RethinkAppearancePreset(5, "Purple", Color(0xFFA855F7)),
)

val rethinkAppearanceStrings = RethinkAppearanceStrings(
    heading = "Appearance",
    system = "System",
    light = "Light",
    dark = "Dark",
)

/** Common theme picker. Hosts persist the preference and, on Android, supply the dynamic swatch. */
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
    sectionHeaderColor: Color = MaterialTheme.colorScheme.primary,
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        if (showSectionHeader) {
            SectionHeader(strings.heading, color = sectionHeaderColor)
        }
        Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = rethinkGroupedListShape(CardPosition.First),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                RethinkConnectedChoiceGroup(
                    options = modes.map { (mode, label) ->
                        RethinkChoiceOption(mode, label, icon = mode.icon(), selectedIcon = MaterialSymbols.Filled.Check)
                    },
                    selected = appearanceMode,
                    onSelect = { mode ->
                        appearanceMode = mode
                        onModeSelected(mode)
                    },
                    modifier = Modifier.padding(SharedDimensions.cardPadding),
                    unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = rethinkGroupedListShape(CardPosition.Last),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                // Fixed column grid: every cell has the same width on every row, so swatches line up
                // vertically and a shorter last row sits centered (11 → 6 + 5, 10 → 5 + 5).
                val columns = ((visiblePresets.size + 1) / 2).coerceAtLeast(1)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingSm),
                ) {
                    visiblePresets.chunked(columns).forEach { rowPresets ->
                        val missing = columns - rowPresets.size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (missing > 0) Spacer(Modifier.weight(missing / 2f))
                            rowPresets.forEach { preset ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
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
                            if (missing > 0) Spacer(Modifier.weight(missing / 2f))
                        }
                    }
                }
            }
        }
    }
}

private val ColorTileHitSize = SharedDimensions.touchTargetMin
private val ColorTileSize = SharedDimensions.iconContainerMd
private val ColorTileRingWidth = 2.dp
private val SwatchInk = Color(0xFF1C1B18)

@Composable
private fun RethinkThemeColorSwatch(
    preset: RethinkAppearancePreset,
    selected: Boolean,
    enabled: Boolean,
    dynamicColor: Color,
    onClick: () -> Unit,
) {
    val motion = LocalRethinkMotion.current
    val baseColor = if (preset.isDynamic) dynamicColor else preset.color ?: dynamicColor
    val displayColor = if (enabled) baseColor else baseColor.copy(alpha = 0.42f)
    // Pick whichever ink has the higher WCAG contrast on this swatch, so the check stays legible on
    // light seeds (Amber, Green, Cyan) where a plain luminance < 0.5 cut-off picks white.
    val onSwatch = contrastingInk(displayColor)
    val ringColor = MaterialTheme.colorScheme.primary
    val springOrSnap: AnimationSpec<Float> =
        if (motion.reducedMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 520f)
    val cornerFraction by animateFloatAsState(
        targetValue = if (selected) 0.5f else 0.26f,
        animationSpec = springOrSnap,
        label = "swatchCorner_${preset.id}",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.92f else 0.86f,
        animationSpec = if (motion.reducedMotion) snap() else spring(dampingRatio = 0.56f, stiffness = 600f),
        label = "swatchScale_${preset.id}",
    )
    val rotation by animateFloatAsState(
        targetValue = if (selected) 8f else 0f,
        animationSpec = if (motion.reducedMotion) snap() else spring(dampingRatio = 0.66f, stiffness = 420f),
        label = "swatchRotation_${preset.id}",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = motion.durationMedium, easing = FastOutSlowInEasing),
        label = "swatchGlow_${preset.id}",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected || preset.isDynamic) 1f else 0f,
        animationSpec = tween(durationMillis = motion.durationFast, easing = FastOutSlowInEasing),
        label = "swatchIcon_${preset.id}",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(percent = (cornerFraction * 100).toInt().coerceIn(1, 50))

    Box(
        modifier = Modifier
            .size(ColorTileHitSize)
            .graphicsLayer { alpha = if (enabled) 1f else 0.52f }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = preset.label
            },
        contentAlignment = Alignment.Center,
    ) {
        // Selection ring in primary, separated from the swatch by a small gap.
        Box(
            modifier = Modifier
                .size(ColorTileHitSize)
                .graphicsLayer { alpha = glowAlpha }
                .drawBehind {
                    val stroke = ColorTileRingWidth.toPx()
                    drawCircle(
                        color = ringColor,
                        radius = (size.minDimension - stroke) / 2f,
                        style = Stroke(width = stroke),
                    )
                },
        )
        Box(
            modifier = Modifier
                .size(ColorTileSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rotation
                }
                .clip(shape)
                .indication(
                    interactionSource = interactionSource,
                    indication = ripple(
                        bounded = true,
                        radius = 18.dp,
                        color = Color.White.copy(alpha = 0.32f),
                    ),
                )
                .background(displayColor),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                            start = Offset.Zero,
                            end = Offset(60f, 60f),
                        ),
                    ),
            )
            if (selected || preset.isDynamic) {
                Icon(
                    imageVector = if (selected) MaterialSymbols.Filled.Check else MaterialSymbols.Filled.Palette,
                    contentDescription = null,
                    modifier = Modifier
                        .size(if (selected) 18.dp else 16.dp)
                        .graphicsLayer {
                            alpha = iconAlpha
                            rotationZ = -rotation
                        },
                    tint = onSwatch.copy(alpha = if (selected) 1f else 0.9f),
                )
            }
        }
    }
}

private fun contrastingInk(background: Color): Color {
    val luminance = background.luminance()
    val whiteContrast = 1.05f / (luminance + 0.05f)
    val inkContrast = (luminance + 0.05f) / (SwatchInk.luminance() + 0.05f)
    return if (whiteContrast >= inkContrast) Color.White else SwatchInk
}

private fun RethinkAppearanceMode.icon() = when (this) {
    RethinkAppearanceMode.System -> MaterialSymbols.Filled.BrightnessAuto
    RethinkAppearanceMode.Light -> MaterialSymbols.Filled.LightMode
    RethinkAppearanceMode.Dark -> MaterialSymbols.Filled.DarkMode
}
