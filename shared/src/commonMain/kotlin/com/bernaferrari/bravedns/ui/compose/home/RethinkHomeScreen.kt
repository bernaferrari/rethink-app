@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * The home screen is a control surface, not a settings summary. It gives protection one
 * unmistakable hero: a shape-morphing protection mark (a quiet circle when off, a turning cookie
 * with soft pulse rings when protected), a headline that says the state in words, one primary
 * action, and the traffic this device has actually handled. Configuration belongs elsewhere.
 */
package com.bernaferrari.bravedns.ui.compose.home

import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillColors
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import com.bernaferrari.bravedns.ui.compose.common.RethinkAdaptiveWidth
import com.bernaferrari.bravedns.ui.compose.common.isExpanded
import com.bernaferrari.bravedns.ui.compose.common.rethinkContentWidth
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import kotlin.math.roundToInt

data class RethinkHomeUiState(
    val isVpnActive: Boolean = false,
    val networkLogsCount: Long = 0,
    val dnsLogsCount: Long = 0,
    val protectionStatus: String = "",
    val isProtectionFailing: Boolean = false,
)

data class RethinkHomeStrings(
    val productName: String,
    val protected: String,
    val notActive: String,
    val start: String,
    val stop: String,
    val protectedSubtitle: String,
    val inactiveSubtitle: String,
    val failingSubtitle: String,
    val connections: String,
    val dnsQueries: String,
    val networkProtection: String,
    val live: String,
    val offline: String,
    val reconnecting: String,
    val activityTitle: String,
    val activitySubtitle: String,
)

private enum class ProtectionVisualState { Protected, Recovering, Off }

private const val BEACON_TURN_MILLIS = 22_000

/** Pulse rings emitted per clock turn (one every two seconds). */
private const val PULSES_PER_TURN = 11
private const val PULSE_RINGS = 3

/** The morphing shape's share of the mark; the remainder is the pulse field. */
private const val MARK_SHAPE_RATIO = 0.62f
private const val MARK_ICON_RATIO = 0.22f
private val MarkSizeCompact = 248.dp
private val MarkSizeExpanded = 300.dp
private val ActionMinWidth = 208.dp
private val ActivityDividerHeight = 40.dp

@Composable
fun RethinkHomeScreen(
    uiState: RethinkHomeUiState,
    strings: RethinkHomeStrings,
    onStartStopClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAtmosphere: Boolean = true,
    /** Opens the full breakdown (Statistics); null keeps the activity card informational. */
    onActivityClick: (() -> Unit)? = null,
) {
    val visualState = uiState.visualState()
    val status = uiState.protectionStatus.ifBlank {
        if (uiState.isVpnActive) strings.protected else strings.notActive
    }
    val subtitle = when (visualState) {
        ProtectionVisualState.Protected -> strings.protectedSubtitle
        ProtectionVisualState.Recovering -> strings.failingSubtitle
        ProtectionVisualState.Off -> strings.inactiveSubtitle
    }
    val activeProgress by animateStateProgress(uiState.isVpnActive, "homeBeaconState")
    val recoveringProgress by animateStateProgress(
        visualState == ProtectionVisualState.Recovering,
        "homeRecoveringProgress",
    )
    // The shape itself rides a spatial spring (slight overshoot) so the circle → cookie change
    // feels physical; colors and the atmosphere keep the calmer effects spec.
    val motion = LocalRethinkMotion.current
    val morphProgress by animateFloatAsState(
        targetValue = if (uiState.isVpnActive) 1f else 0f,
        animationSpec = if (motion.reducedMotion) snap() else MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "homeMarkMorph",
    )
    val phase = rememberBeaconPhase()
    val palette = rememberBeaconPalette(visualState)

    Scaffold(
        modifier = modifier,
        containerColor = if (showAtmosphere) MaterialTheme.colorScheme.surface else Color.Transparent,
    ) { insets ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            if (showAtmosphere) {
                ProtectionFlowBackground(
                    phase = phase,
                    activeProgress = activeProgress,
                    recoveringProgress = recoveringProgress,
                    accent = palette.accent,
                    secondary = palette.secondary,
                    field = palette.field,
                    surface = MaterialTheme.colorScheme.surface,
                    opacity = 0.56f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            RethinkAdaptiveWidth(Modifier.fillMaxSize()) { widthClass ->
                val expanded = widthClass.isExpanded
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = SharedDimensions.screenPaddingHorizontal),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HomeMasthead(
                        name = strings.productName,
                        networkProtection = strings.networkProtection,
                        stateLabel = when (visualState) {
                            ProtectionVisualState.Protected -> strings.live
                            ProtectionVisualState.Recovering -> strings.reconnecting
                            ProtectionVisualState.Off -> strings.offline
                        },
                        stateColor = when (visualState) {
                            ProtectionVisualState.Off -> MaterialTheme.colorScheme.outline
                            else -> palette.accent
                        },
                        modifier = Modifier
                            .rethinkContentWidth(widthClass)
                            .padding(top = SharedDimensions.spacingLg),
                    )
                    Spacer(Modifier.weight(1f))
                    ProtectionMark(
                        visualState = visualState,
                        activeProgress = activeProgress,
                        morphProgress = morphProgress,
                        phase = phase,
                        palette = palette,
                        size = if (expanded) MarkSizeExpanded else MarkSizeCompact,
                    )
                    Spacer(Modifier.height(SharedDimensions.spacingLg))
                    HomeStatus(
                        status = status,
                        subtitle = subtitle,
                        isRecovering = visualState == ProtectionVisualState.Recovering,
                        expanded = expanded,
                        modifier = Modifier.rethinkContentWidth(widthClass),
                    )
                    Spacer(Modifier.height(SharedDimensions.spacingXl))
                    BeaconAction(
                        isActive = uiState.isVpnActive,
                        label = if (uiState.isVpnActive) strings.stop else strings.start,
                        stateDescription = status,
                        onClick = onStartStopClick,
                    )
                    Spacer(Modifier.weight(1f))
                    HomeActivityCard(
                        title = strings.activityTitle,
                        caption = strings.activitySubtitle,
                        connectionsLabel = strings.connections,
                        dnsQueriesLabel = strings.dnsQueries,
                        connections = uiState.networkLogsCount,
                        dnsQueries = uiState.dnsLogsCount,
                        onClick = onActivityClick,
                        modifier = Modifier
                            .rethinkContentWidth(widthClass)
                            .padding(bottom = SharedDimensions.spacingLg),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeMasthead(
    name: String,
    networkProtection: String,
    stateLabel: String,
    stateColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = networkProtection,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Live/Off as a tonal status pill, so the state reads as a control-surface readout.
        RethinkPill(
            text = stateLabel,
            colors = RethinkPillColors(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface),
            size = RethinkPillSize.Large,
            dotColor = stateColor,
        )
    }
}

private data class BeaconPalette(
    val accent: Color,
    val secondary: Color,
    val field: Color,
    val icon: Color,
    val disc: Color,
)

@Composable
private fun rememberBeaconPalette(state: ProtectionVisualState): BeaconPalette {
    val motion = LocalRethinkMotion.current
    val scheme = MaterialTheme.colorScheme
    // Protected follows RethinkStatusColors ("allowed is always primary"); tertiary only
    // supplies contrast in the atmosphere.
    val accentTarget = when (state) {
        ProtectionVisualState.Protected -> scheme.primary
        ProtectionVisualState.Recovering -> scheme.error
        ProtectionVisualState.Off -> scheme.outline
    }
    val fieldTarget = when (state) {
        ProtectionVisualState.Protected -> scheme.primaryContainer
        ProtectionVisualState.Recovering -> scheme.errorContainer
        ProtectionVisualState.Off -> scheme.surfaceContainerLow
    }
    val secondaryTarget = when (state) {
        ProtectionVisualState.Protected -> scheme.tertiary
        ProtectionVisualState.Recovering -> scheme.tertiary
        ProtectionVisualState.Off -> scheme.outlineVariant
    }
    // Off is a quiet tonal circle, not a filled outline-colored blob.
    val discTarget = when (state) {
        ProtectionVisualState.Off -> scheme.surfaceContainerHighest
        else -> accentTarget
    }
    val iconTarget = when (state) {
        ProtectionVisualState.Protected -> scheme.onPrimary
        ProtectionVisualState.Recovering -> scheme.onError
        ProtectionVisualState.Off -> scheme.onSurfaceVariant
    }
    val spec: AnimationSpec<Color> =
        if (motion.reducedMotion) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()
    val accent by animateColorAsState(accentTarget, spec, label = "beaconAccent")
    val secondary by animateColorAsState(secondaryTarget, spec, label = "beaconSecondary")
    val field by animateColorAsState(fieldTarget, spec, label = "beaconField")
    val icon by animateColorAsState(iconTarget, spec, label = "beaconIcon")
    val disc by animateColorAsState(discTarget, spec, label = "beaconDisc")
    return BeaconPalette(accent, secondary, field, icon, disc)
}

@Composable
internal fun RethinkHomeAtmosphere(
    uiState: RethinkHomeUiState,
    modifier: Modifier = Modifier,
) {
    val visualState = uiState.visualState()
    val activeProgress by animateStateProgress(uiState.isVpnActive, "homeAtmosphereState")
    val recoveringProgress by animateStateProgress(
        visualState == ProtectionVisualState.Recovering,
        "homeAtmosphereRecovering",
    )
    val phase = rememberBeaconPhase()
    val palette = rememberBeaconPalette(visualState)

    Box(modifier = modifier) {
        ProtectionFlowBackground(
            phase = phase,
            activeProgress = activeProgress,
            recoveringProgress = recoveringProgress,
            accent = palette.accent,
            secondary = palette.secondary,
            field = palette.field,
            surface = MaterialTheme.colorScheme.surface,
            opacity = 0.56f,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun RethinkHomeUiState.visualState(): ProtectionVisualState = when {
    isVpnActive && isProtectionFailing -> ProtectionVisualState.Recovering
    isVpnActive -> ProtectionVisualState.Protected
    else -> ProtectionVisualState.Off
}

@Composable
private fun animateStateProgress(target: Boolean, label: String): State<Float> {
    val motion = LocalRethinkMotion.current
    return animateFloatAsState(
        targetValue = if (target) 1f else 0f,
        animationSpec = if (motion.reducedMotion) snap() else MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = label,
    )
}

/**
 * The ambient 0..1 clock behind the mark and the protection field. It is handed out as a
 * provider and only read in draw/layer scopes, so the screen does not recompose on every frame.
 */
@Composable
private fun rememberBeaconPhase(): () -> Float {
    if (LocalRethinkMotion.current.reducedMotion) return StaticPhase
    val phase = rememberInfiniteTransition(label = "homeBeaconClock").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(BEACON_TURN_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "homeBeaconPhase",
    )
    return remember(phase) { { phase.value } }
}

private val StaticPhase: () -> Float = { 0f }

/**
 * The hero: a Material shape that morphs from a circle (off) into a 12-sided cookie
 * (protected) and turns slowly, with pulse rings radiating while protected. Rings and rotation
 * read the clock in draw/layer scopes only; with reduced motion they hold still.
 */
@Composable
private fun ProtectionMark(
    visualState: ProtectionVisualState,
    activeProgress: Float,
    morphProgress: Float,
    phase: () -> Float,
    palette: BeaconPalette,
    size: Dp,
) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided) }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(Modifier.matchParentSize()) {
            val outer = this.size.minDimension / 2f
            val inner = outer * MARK_SHAPE_RATIO
            // A hairline boundary is always there, so "off" still has a defined object.
            drawCircle(
                color = palette.accent.copy(alpha = 0.16f),
                radius = outer - 1.dp.toPx(),
                style = Stroke(width = 1.dp.toPx()),
            )
            if (activeProgress > 0f) {
                val turn = phase() * PULSES_PER_TURN
                repeat(PULSE_RINGS) { ring ->
                    val progress = (turn + ring / PULSE_RINGS.toFloat()) % 1f
                    drawCircle(
                        color = palette.accent.copy(alpha = (1f - progress) * 0.32f * activeProgress),
                        radius = inner + (outer - inner) * progress,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
        }
        Box(
            Modifier
                .size(size * MARK_SHAPE_RATIO)
                .graphicsLayer { rotationZ = phase() * 360f * activeProgress }
                .background(palette.disc, MorphShape(morph, morphProgress)),
        )
        Icon(
            imageVector = if (visualState == ProtectionVisualState.Recovering) {
                MaterialSymbols.Filled.WarningAmber
            } else {
                MaterialSymbols.Filled.Shield
            },
            contentDescription = null,
            tint = palette.icon,
            modifier = Modifier.size(size * MARK_ICON_RATIO),
        )
    }
}

@Composable
private fun HomeStatus(
    status: String,
    subtitle: String,
    isRecovering: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        Text(
            text = status,
            style = if (expanded) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5f).sp,
            textAlign = TextAlign.Center,
            color = if (isRecovering) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** A [Morph] between two normalized Material shapes, scaled to the layout size. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cubics = morph.asCubics(progress)
        cubics.firstOrNull()?.let { path.moveTo(it.anchor0X * size.width, it.anchor0Y * size.height) }
        cubics.forEach { cubic ->
            path.cubicTo(
                cubic.control0X * size.width, cubic.control0Y * size.height,
                cubic.control1X * size.width, cubic.control1Y * size.height,
                cubic.anchor1X * size.width, cubic.anchor1Y * size.height,
            )
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * The one hero action on Home. A plain [Button] (not a ToggleButton): its label already flips
 * between Start and Stop, so toggle semantics would announce "Stop, on". The protection state is
 * exposed as the state description instead, and press feedback comes from the expressive
 * shape morph. Active swaps to a quieter tonal button with square-ish corners.
 */
@Composable
private fun BeaconAction(
    isActive: Boolean,
    label: String,
    stateDescription: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        shapes = if (isActive) {
            ButtonDefaults.shapes(ButtonDefaults.squareShape, ButtonDefaults.mediumPressedShape)
        } else {
            ButtonDefaults.shapesFor(height)
        },
        modifier = Modifier
            .heightIn(min = height)
            .widthIn(min = ActionMinWidth)
            .semantics { this.stateDescription = stateDescription },
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        colors = ButtonDefaults.buttonColors(
            // Starting is the call to action; once protected, stopping is secondary and tonal.
            containerColor = if (isActive) scheme.surfaceContainerHighest else scheme.primary,
            contentColor = if (isActive) scheme.onSurface else scheme.onPrimary,
        ),
    ) {
        Icon(
            imageVector = if (isActive) MaterialSymbols.Filled.Stop else MaterialSymbols.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
        )
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        Text(text = label, style = ButtonDefaults.textStyleFor(height))
    }
}

/** Traffic handled since logs were last cleared, as hero numbers in a tonal card. */
@Composable
private fun HomeActivityCard(
    title: String,
    caption: String,
    connectionsLabel: String,
    dnsQueriesLabel: String,
    connections: Long,
    dnsQueries: Long,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SharedDimensions.groupCornerRadius)
    val color = MaterialTheme.colorScheme.surfaceContainerLow
    val content: @Composable () -> Unit = { HomeActivityContent(title, caption, connectionsLabel, dnsQueriesLabel, connections, dnsQueries, onClick != null) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, content = content)
    } else {
        Surface(modifier = modifier, shape = shape, color = color, content = content)
    }
}

@Composable
private fun HomeActivityContent(
    title: String,
    caption: String,
    connectionsLabel: String,
    dnsQueriesLabel: String,
    connections: Long,
    dnsQueries: Long,
    navigable: Boolean,
) {
    Column(
        modifier = Modifier.padding(SharedDimensions.cardPadding),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (navigable) {
                Icon(
                    MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SharedDimensions.iconSizeSm),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActivityMetric(
                value = formatCompactCount(connections),
                label = connectionsLabel,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                modifier = Modifier
                    .height(ActivityDividerHeight)
                    .padding(horizontal = SharedDimensions.spacingLg),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            ActivityMetric(
                value = formatCompactCount(dnsQueries),
                label = dnsQueriesLabel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActivityMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun formatCompactCount(rawValue: Long): String {
    val value = rawValue.coerceAtLeast(0)
    return when {
        value < 1_000 -> value.toString()
        value < 1_000_000 -> compactUnit(value, 1_000, "K")
        else -> compactUnit(value, 1_000_000, "M")
    }
}

private fun compactUnit(value: Long, unit: Long, suffix: String): String {
    val tenths = ((value.toDouble() / unit.toDouble()) * 10.0).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}$suffix" else "${tenths / 10}.${tenths % 10}$suffix"
}
