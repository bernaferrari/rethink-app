/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.logs

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import com.celzero.bravedns.ui.compose.theme.cardPositionFor
import com.celzero.bravedns.ui.compose.theme.rethinkGroupedListShape

data class RethinkLogDetail(
    val label: String,
    val value: String,
    val monospace: Boolean = false,
    val isError: Boolean = false,
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
    var expanded by remember(model.id) { mutableStateOf(false) }
    var keepDetails by remember(model.id) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val allowedColor = Color(0xFF2FB36B)
    val statusColor = if (model.isBlocked) scheme.error else allowedColor
    val statusContainer = if (model.isBlocked) scheme.errorContainer.copy(alpha = 0.55f) else allowedColor.copy(alpha = 0.2f)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.988f else 1f,
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f),
        label = "log_row_scale_${model.id}",
    )
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "log_row_chevron_${model.id}",
    )
    val detailsProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(230, easing = FastOutSlowInEasing),
        label = "log_row_details_${model.id}",
        finishedListener = { if (it == 0f) keepDetails = false },
    )
    val baseColor = if (expanded) scheme.surfaceContainer else scheme.surfaceContainerLow
    val cardColor by animateColorAsState(
        targetValue = if (pressed) androidx.compose.ui.graphics.lerp(baseColor, scheme.primaryContainer, 0.16f) else baseColor,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "log_row_color_${model.id}",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 1.dp else 0.dp,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "log_row_elevation_${model.id}",
    )
    val stripeAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.9f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
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
        tonalElevation = if (expanded) 1.dp else 0.dp,
        shadowElevation = elevation,
        border = if (expanded) BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)) else null,
        modifier = modifier.fillMaxWidth().scale(scale).clip(shape),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 26.dp, end = 12.dp, top = 12.dp, bottom = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LogIconSlot(statusContainer, model.icon)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(model.destination, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(model.appLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            LogTypeTag(model.typeLabel)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        LogStatusLabel(if (model.isBlocked) model.blockedLabel else model.allowedLabel, statusColor)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(model.timeLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant.copy(alpha = 0.92f))
                            Icon(MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp).rotate(chevron))
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
                modifier = Modifier.align(Alignment.TopStart).fillMaxHeight().padding(start = 10.dp, top = 10.dp, bottom = 10.dp).width(5.dp).clip(RoundedCornerShape(999.dp)).background(
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
            Surface(shape = RoundedCornerShape(10.dp), color = statusContainer, modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(MaterialSymbols.Filled.Language, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp)) }
            }
        }
    }
}

@Composable
private fun LogTypeTag(label: String) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.58f)).padding(horizontal = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LogStatusLabel(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun LogDetailsPanel(model: RethinkLogRowModel, statusColor: Color) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.42f), thickness = 0.5.dp)
        Column(Modifier.padding(start = 26.dp, end = 14.dp, top = 10.dp, bottom = 14.dp)) {
            model.latencyMs?.let { LogLatency(it) }
            if (model.latencyMs != null && model.details.isNotEmpty()) Spacer(Modifier.height(8.dp))
            model.details.forEach { detail -> LogDetailRow(detail) }
            model.blocklistsLabel?.takeIf { it.isNotBlank() }?.let { label ->
                Spacer(Modifier.height(SharedLogDimensions.detailGap))
                val blocklistShape = RoundedCornerShape(SharedDimensions.cornerRadiusSmMd)
                Surface(onClick = model.onBlocklistsClick ?: {}, enabled = model.onBlocklistsClick != null, shape = blocklistShape, color = scheme.errorContainer.copy(alpha = 0.38f), modifier = Modifier.fillMaxWidth().clip(blocklistShape)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = statusColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                }
            }
        }
    }
}

@Composable
private fun LogLatency(latency: Long) {
    val scheme = MaterialTheme.colorScheme
    val (color, label) = when {
        latency in 1..10 -> Color(0xFF2FB36B) to "${latency}ms · fast"
        latency in 11..50 -> scheme.tertiary to "${latency}ms · ok"
        latency > 50 -> scheme.error to "${latency}ms · slow"
        else -> scheme.onSurfaceVariant to "${latency}ms"
    }
    val fraction = (latency.toFloat() / 100f).coerceIn(0.04f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Latency", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = color)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(scheme.outlineVariant.copy(alpha = 0.35f))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))))
        }
    }
}

@Composable
private fun LogDetailRow(detail: RethinkLogDetail) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(detail.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 72.dp))
        Text(detail.value, style = MaterialTheme.typography.labelSmall, color = if (detail.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, fontFamily = if (detail.monospace) FontFamily.Monospace else FontFamily.Default, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

private object SharedLogDimensions { val detailGap = 4.dp }

private fun Modifier.logAccordionReveal(progress: Float): Modifier {
    val normalized = progress.coerceIn(0f, 1f)
    return graphicsLayer { alpha = normalized }.clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = (placeable.height * normalized).toInt()
        layout(placeable.width, height) { if (height > 0) placeable.place(0, 0) }
    }
}
