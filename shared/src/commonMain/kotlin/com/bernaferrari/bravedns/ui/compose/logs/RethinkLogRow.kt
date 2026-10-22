/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusIndicator
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusLabel
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWell
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillSize
import com.bernaferrari.bravedns.ui.compose.theme.RethinkKeyValueRow
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

data class RethinkLogDetail(
    val label: String,
    val value: String,
    val monospace: Boolean = false,
    val isError: Boolean = false,
)

/** Localised copy for the latency meter shown in expanded DNS log rows. */
data class RethinkLogLatencyStrings(
    val title: String,
    val fast: String,
    val ok: String,
    val slow: String,
)

data class RethinkLogRowModel(
    val id: String,
    val destination: String,
    val appLabel: String,
    val typeLabel: String,
    val timeLabel: String,
    val isBlocked: Boolean,
    val allowedLabel: String,
    val blockedLabel: String,
    val details: List<RethinkLogDetail>,
    val icon: (@Composable (statusContainer: Color) -> Unit)? = null,
    val latencyMs: Long? = null,
    val latencyStrings: RethinkLogLatencyStrings? = null,
    val blocklistsLabel: String? = null,
    val onBlocklistsClick: (() -> Unit)? = null,
)

/**
 * Portable expandable network/DNS log card. Platform code supplies data, formatted copy, and an
 * optional real app/fav icon; all row geometry, interaction, and expansion animation stay shared.
 */
@Composable
fun RethinkLogRow(
    model: RethinkLogRowModel,
    index: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    val motion = LocalRethinkMotion.current
    var expanded by remember(model.id) { mutableStateOf(false) }
    var keepDetails by remember(model.id) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val allowedColor = RethinkStatusColors.allowed
    val statusColor = if (model.isBlocked) RethinkStatusColors.blocked else allowedColor
    val statusContainer = if (model.isBlocked) scheme.errorContainer.copy(alpha = 0.55f) else allowedColor.copy(alpha = 0.2f)
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reducedMotion) 0.988f else 1f,
        animationSpec = if (motion.reducedMotion) snap() else spring(stiffness = 420f, dampingRatio = 0.82f),
        label = "log_row_scale_${model.id}",
    )
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (motion.reducedMotion) snap() else motionScheme.fastSpatialSpec(),
        label = "log_row_chevron_${model.id}",
    )
    val detailsProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (motion.reducedMotion) snap() else motionScheme.defaultEffectsSpec(),
        label = "log_row_details_${model.id}",
        finishedListener = { if (it == 0f) keepDetails = false },
    )
    val baseColor = if (expanded) scheme.surfaceContainer else scheme.surfaceContainerLow
    val cardColor by animateColorAsState(
        targetValue = if (pressed) androidx.compose.ui.graphics.lerp(baseColor, scheme.primaryContainer, 0.16f) else baseColor,
        animationSpec = if (motion.reducedMotion) snap() else motionScheme.fastEffectsSpec(),
        label = "log_row_color_${model.id}",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 1.dp else 0.dp,
        animationSpec = if (motion.reducedMotion) snap() else motionScheme.fastEffectsSpec(),
        label = "log_row_elevation_${model.id}",
    )
    val stripeAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.9f,
        animationSpec = if (motion.reducedMotion) snap() else motionScheme.fastEffectsSpec(),
        label = "log_row_stripe_${model.id}",
    )
    val shape = rethinkGroupedListShape(cardPositionFor(index, itemCount - 1))

    Surface(
        onClick = {
            expanded = !expanded
            if (expanded) keepDetails = true
        },
        interactionSource = interactionSource,
        shape = shape,
        color = cardColor,
        shadowElevation = elevation,
        modifier = modifier.fillMaxWidth().scale(scale).clip(shape),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        start = SharedDimensions.spacingXl + SharedDimensions.spacingGridTile,
                        end = SharedDimensions.spacingMd,
                        top = SharedDimensions.spacingMd,
                        bottom = SharedDimensions.spacingMd,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSmMd),
                ) {
                    LogIconSlot(statusContainer, model.icon)
                    // Two lines, each a single baseline: destination + verdict, then app/type +
                    // time. Sharing rows (instead of two independent columns) keeps "• Allowed"
                    // on the headline's baseline at every font scale.
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                            Text(model.destination, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).alignByBaseline())
                            RethinkStatusLabel(
                                label = if (model.isBlocked) model.blockedLabel else model.allowedLabel,
                                color = statusColor,
                                modifier = Modifier.alignByBaseline(),
                                indicator = RethinkStatusIndicator.Dot(size = SharedDimensions.spacingXs),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
                                Text(model.appLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                RethinkPill(model.typeLabel, size = RethinkPillSize.Small, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                                Text(model.timeLabel, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = scheme.onSurfaceVariant, maxLines = 1)
                                // Inline accordion, not navigation: a down chevron that flips,
                                // rather than a forward arrow that implies a new screen.
                                Icon(MaterialSymbols.Filled.KeyboardArrowDown, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(SharedDimensions.iconSizeXs).rotate(chevron))
                            }
                        }
                    }
                }
                if (keepDetails) {
                    Box(Modifier.fillMaxWidth().logAccordionReveal(detailsProgress)) {
                        LogDetailsPanel(model, statusColor)
                    }
                }
            }
            Box(
                modifier = Modifier.align(Alignment.TopStart).fillMaxHeight().padding(start = SharedDimensions.spacingSmMd, top = SharedDimensions.spacingSmMd, bottom = SharedDimensions.spacingSmMd).width(SharedDimensions.spacingXs).clip(RoundedCornerShape(SharedDimensions.cornerRadiusFull)).background(
                    Brush.verticalGradient(listOf(statusColor.copy(alpha = stripeAlpha), statusColor.copy(alpha = 0.38f))),
                ),
            )
        }
    }
}

@Composable
private fun LogIconSlot(statusContainer: Color, icon: (@Composable (Color) -> Unit)?) {
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        if (icon != null) {
            icon(statusContainer)
        } else {
            RethinkIconWell(
                icon = MaterialSymbols.Filled.Language,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                containerColor = statusContainer,
                size = SharedDimensions.iconContainerSm,
                shape = RoundedCornerShape(SharedDimensions.cornerRadiusSmMd),
            )
        }
    }
}

@Composable
private fun LogDetailsPanel(model: RethinkLogRowModel, statusColor: Color) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.42f), thickness = SharedDimensions.dividerThickness)
        Column(Modifier.padding(start = SharedDimensions.spacingXl + SharedDimensions.spacingGridTile, end = SharedDimensions.spacingLg, top = SharedDimensions.spacingSmMd, bottom = SharedDimensions.spacingLg)) {
            model.latencyMs?.let { LogLatency(it, model.latencyStrings) }
            if (model.latencyMs != null && model.details.isNotEmpty()) Spacer(Modifier.height(SharedDimensions.spacingSm))
            model.details.forEach { detail ->
                RethinkKeyValueRow(
                    label = detail.label,
                    value = detail.value,
                    modifier = Modifier.padding(vertical = SharedDimensions.spacingXs),
                    valueColor = if (detail.isError) scheme.error else scheme.onSurface,
                    monospace = detail.monospace,
                    labelStyle = MaterialTheme.typography.labelSmall,
                    valueStyle = MaterialTheme.typography.labelSmall,
                    valueMaxLines = 2,
                )
            }
            model.blocklistsLabel?.takeIf { it.isNotBlank() }?.let { label ->
                Spacer(Modifier.height(SharedDimensions.spacingXs))
                val blocklistShape = RoundedCornerShape(SharedDimensions.cornerRadiusSmMd)
                Surface(onClick = model.onBlocklistsClick ?: {}, enabled = model.onBlocklistsClick != null, shape = blocklistShape, color = scheme.errorContainer.copy(alpha = 0.38f), modifier = Modifier.fillMaxWidth().clip(blocklistShape)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = statusColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = SharedDimensions.spacingSmMd, vertical = SharedDimensions.spacingSm))
                }
            }
        }
    }
}

@Composable
private fun LogLatency(latency: Long, strings: RethinkLogLatencyStrings?) {
    val scheme = MaterialTheme.colorScheme
    val (color, quality) = when {
        latency in 1..10 -> scheme.tertiary to strings?.fast
        latency in 11..50 -> scheme.tertiary to strings?.ok
        latency > 50 -> scheme.error to strings?.slow
        else -> scheme.onSurfaceVariant to null
    }
    val label = if (quality.isNullOrBlank()) "${latency}ms" else "${latency}ms · $quality"
    val fraction = (latency.toFloat() / 100f).coerceIn(0.04f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(strings?.title.orEmpty(), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = color)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = scheme.outlineVariant.copy(alpha = 0.35f),
        )
    }
}


private fun Modifier.logAccordionReveal(progress: Float): Modifier {
    val normalized = progress.coerceIn(0f, 1f)
    return graphicsLayer { alpha = normalized }.clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = (placeable.height * normalized).toInt()
        layout(placeable.width, height) { if (height > 0) placeable.place(0, 0) }
    }
}
