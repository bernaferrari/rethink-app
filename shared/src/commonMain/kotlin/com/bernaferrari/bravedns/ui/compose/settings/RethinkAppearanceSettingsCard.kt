/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.settings

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

fun rethinkSeedColorForPresetId(id: Int): Color? =
    rethinkAppearancePresets.firstOrNull { it.id == id }?.color

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(SharedDimensions.cardPadding),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    verticalAlignment = Alignment.CenterVertically,
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
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = rethinkGroupedListShape(CardPosition.Last),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingXs),
                    horizontalArrangement = Arrangement.spacedBy(
                        SharedDimensions.spacingNone,
                        Alignment.CenterHorizontally,
                    ),
                    verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingNone),
                    itemVerticalAlignment = Alignment.CenterVertically,
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
    }
}

private val ColorTileHitSize = 50.dp
private val ColorTileGlowSize = 56.dp
private val ColorTileSize = 40.dp

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
    val onSwatch = if (displayColor.luminance() < 0.5f) Color.White else Color(0xFF1C1B18)
    val springOrSnap: AnimationSpec<Float> =
        if (motion.reducedMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 520f)
    val cornerFraction by animateFloatAsState(
        targetValue = if (selected) 0.5f else 0.26f,
        animationSpec = springOrSnap,
        label = "swatchCorner_${preset.id}",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 0.86f,
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
        animationSpec = tween(durationMillis = if (motion.reducedMotion) 0 else motion.durationMedium, easing = FastOutSlowInEasing),
        label = "swatchGlow_${preset.id}",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected || preset.isDynamic) 1f else 0f,
        animationSpec = tween(durationMillis = if (motion.reducedMotion) 0 else motion.durationFast, easing = FastOutSlowInEasing),
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
        Box(
            modifier = Modifier
                .size(ColorTileGlowSize)
                .graphicsLayer { alpha = glowAlpha }
                .drawBehind {
                    drawCircle(
                        color = displayColor.copy(alpha = 0.44f),
                        radius = size.minDimension * 0.5f,
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

private fun RethinkAppearanceMode.icon() = when (this) {
    RethinkAppearanceMode.System -> MaterialSymbols.Filled.BrightnessAuto
    RethinkAppearanceMode.Light -> MaterialSymbols.Filled.LightMode
    RethinkAppearanceMode.Dark -> MaterialSymbols.Filled.DarkMode
}
