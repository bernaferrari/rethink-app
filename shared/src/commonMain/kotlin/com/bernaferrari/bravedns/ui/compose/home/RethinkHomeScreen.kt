@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * The home screen is intentionally a control surface, not a settings summary. It gives
 * protection a single unmistakable visual home: a live beacon that quietly maps the state of
 * the device. Configuration belongs elsewhere; the only supporting information here is the
 * traffic this device has actually handled.
 */
package com.bernaferrari.bravedns.ui.compose.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bernaferrari.bravedns.ui.compose.common.RethinkAdaptiveWidth
import com.bernaferrari.bravedns.ui.compose.common.WindowWidthClass
import com.bernaferrari.bravedns.ui.compose.common.isExpanded
import com.bernaferrari.bravedns.ui.compose.common.rethinkContentWidth
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

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
)

private enum class ProtectionVisualState { Protected, Recovering, Off }

private const val STATE_CHANGE_MILLIS = 280
private const val BEACON_TURN_MILLIS = 22_000
private val BeaconCompactWidth = 184.dp
private val BeaconCompactHeight = 220.dp
private val BeaconAspect = BeaconCompactWidth / BeaconCompactHeight

@Composable
fun RethinkHomeScreen(
    uiState: RethinkHomeUiState,
    strings: RethinkHomeStrings,
    onStartStopClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAtmosphere: Boolean = true,
) {
    val motion = LocalRethinkMotion.current
    val visualState = when {
        uiState.isVpnActive && uiState.isProtectionFailing -> ProtectionVisualState.Recovering
        uiState.isVpnActive -> ProtectionVisualState.Protected
        else -> ProtectionVisualState.Off
    }
    val status = uiState.protectionStatus.ifBlank {
        if (uiState.isVpnActive) strings.protected else strings.notActive
    }
    val subtitle = when (visualState) {
        ProtectionVisualState.Protected -> strings.protectedSubtitle
        ProtectionVisualState.Recovering -> strings.failingSubtitle
        ProtectionVisualState.Off -> strings.inactiveSubtitle
    }
    val activeProgress by animateFloatAsState(
        targetValue = if (uiState.isVpnActive) 1f else 0f,
        animationSpec = tween(if (motion.reducedMotion) 0 else STATE_CHANGE_MILLIS, easing = FastOutSlowInEasing),
        label = "homeBeaconState",
    )
    val recoveringProgress by animateFloatAsState(
        targetValue = if (visualState == ProtectionVisualState.Recovering) 1f else 0f,
        animationSpec = tween(if (motion.reducedMotion) 0 else STATE_CHANGE_MILLIS, easing = FastOutSlowInEasing),
        label = "homeRecoveringProgress",
    )
    val phase = if (motion.reducedMotion) {
        0f
    } else {
        rememberInfiniteTransition(label = "homeAtmosphereMotion").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(BEACON_TURN_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "homeAtmospherePhase",
        ).value
    }
    val palette = rememberBeaconPalette(visualState)

    Scaffold(
        modifier = modifier,
        containerColor = if (showAtmosphere) MaterialTheme.colorScheme.surface else Color.Transparent,
    ) { insets ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .background(
                    if (showAtmosphere) MaterialTheme.colorScheme.surface else Color.Transparent,
                ),
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = SharedDimensions.spacingXl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HomeMasthead(
                        name = strings.productName,
                        networkProtection = strings.networkProtection,
                        live = strings.live,
                        offline = strings.offline,
                        isProtected = visualState == ProtectionVisualState.Protected,
                        accent = palette.accent,
                        modifier = Modifier
                            .rethinkContentWidth(widthClass)
                            .padding(top = SharedDimensions.spacingLg),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        ProtectionBeacon(
                            visualState = visualState,
                            activeProgress = activeProgress,
                            phase = phase,
                            palette = palette,
                            status = status,
                            subtitle = subtitle,
                            action = if (uiState.isVpnActive) strings.stop else strings.start,
                            onToggle = onStartStopClick,
                            widthClass = widthClass,
                        )
                    }
                    HomeActivityFooter(
                        connectionsLabel = strings.connections,
                        dnsQueriesLabel = strings.dnsQueries,
                        connections = uiState.networkLogsCount,
                        dnsQueries = uiState.dnsLogsCount,
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
    live: String,
    offline: String,
    isProtected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = networkProtection,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(
                        if (isProtected) accent else MaterialTheme.colorScheme.outline,
                        androidx.compose.foundation.shape.CircleShape,
                    ),
            )
            Text(
                text = if (isProtected) live else offline,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class BeaconPalette(
    val accent: Color,
    val secondary: Color,
    val field: Color,
    val icon: Color,
)

@Composable
private fun rememberBeaconPalette(state: ProtectionVisualState): BeaconPalette {
    val motion = LocalRethinkMotion.current
    val scheme = MaterialTheme.colorScheme
    val accentTarget = when (state) {
        ProtectionVisualState.Protected -> scheme.tertiary
        ProtectionVisualState.Recovering -> scheme.error
        ProtectionVisualState.Off -> scheme.outline
    }
    val fieldTarget = when (state) {
        ProtectionVisualState.Protected -> scheme.tertiaryContainer
        ProtectionVisualState.Recovering -> scheme.errorContainer
        ProtectionVisualState.Off -> scheme.surfaceContainerLow
    }
    val secondaryTarget = when (state) {
        ProtectionVisualState.Protected -> scheme.primary
        ProtectionVisualState.Recovering -> scheme.tertiary
        ProtectionVisualState.Off -> scheme.outlineVariant
    }
    val iconTarget = when (state) {
        ProtectionVisualState.Protected -> scheme.onTertiary
        ProtectionVisualState.Recovering -> scheme.onError
        ProtectionVisualState.Off -> scheme.onSurfaceVariant
    }
    val spec = tween<Color>(if (motion.reducedMotion) 0 else STATE_CHANGE_MILLIS, easing = FastOutSlowInEasing)
    val accent by animateColorAsState(accentTarget, spec, label = "beaconAccent")
    val secondary by animateColorAsState(secondaryTarget, spec, label = "beaconSecondary")
    val field by animateColorAsState(fieldTarget, spec, label = "beaconField")
    val icon by animateColorAsState(iconTarget, spec, label = "beaconIcon")
    return BeaconPalette(accent, secondary, field, icon)
}

@Composable
internal fun RethinkHomeAtmosphere(
    uiState: RethinkHomeUiState,
    modifier: Modifier = Modifier,
) {
    val motion = LocalRethinkMotion.current
    val visualState = when {
        uiState.isVpnActive && uiState.isProtectionFailing -> ProtectionVisualState.Recovering
        uiState.isVpnActive -> ProtectionVisualState.Protected
        else -> ProtectionVisualState.Off
    }
    val activeProgress by animateFloatAsState(
        targetValue = if (uiState.isVpnActive) 1f else 0f,
        animationSpec = tween(if (motion.reducedMotion) 0 else STATE_CHANGE_MILLIS, easing = FastOutSlowInEasing),
        label = "homeAtmosphereState",
    )
    val recoveringProgress by animateFloatAsState(
        targetValue = if (visualState == ProtectionVisualState.Recovering) 1f else 0f,
        animationSpec = tween(if (motion.reducedMotion) 0 else STATE_CHANGE_MILLIS, easing = FastOutSlowInEasing),
        label = "homeAtmosphereRecovering",
    )
    val phase = if (motion.reducedMotion) {
        0f
    } else {
        rememberInfiniteTransition(label = "homeFullBleedAtmosphere").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(BEACON_TURN_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "homeFullBleedAtmospherePhase",
        ).value
    }
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

@Composable
private fun ProtectionBeacon(
    visualState: ProtectionVisualState,
    activeProgress: Float,
    phase: Float,
    palette: BeaconPalette,
    status: String,
    subtitle: String,
    action: String,
    onToggle: () -> Unit,
    widthClass: WindowWidthClass,
) {
    val expanded = widthClass.isExpanded
    Column(
        modifier = if (expanded) Modifier.rethinkContentWidth(widthClass) else Modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            if (expanded) SharedDimensions.spacingLg else SharedDimensions.spacingMd,
        ),
    ) {
        ProtectionTunnel(
            visualState = visualState,
            activeProgress = activeProgress,
            phase = phase,
            palette = palette,
            expanded = expanded,
        )
        Spacer(Modifier.height(SharedDimensions.spacingXs))
        Column(
            modifier = if (expanded) Modifier.fillMaxWidth() else Modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = status,
                style = if (expanded) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.headlineLarge
                },
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6f).sp,
                color = if (visualState == ProtectionVisualState.Recovering) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(SharedDimensions.spacingXs))
            Text(
                text = subtitle,
                style = if (expanded) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = if (expanded) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.padding(horizontal = SharedDimensions.spacingSm)
                },
            )
        }
        Spacer(Modifier.height(SharedDimensions.spacingXs))
        BeaconAction(
            isActive = visualState != ProtectionVisualState.Off,
            label = action,
            onClick = onToggle,
        )
    }
}

@Composable
private fun ProtectionTunnel(
    visualState: ProtectionVisualState,
    phase: Float,
    activeProgress: Float,
    palette: BeaconPalette,
    expanded: Boolean,
) {
    val initialRoutes = remember { randomTunnelRoutes() }
    var incomingLanes by remember { mutableStateOf(initialRoutes.first) }
    var outgoingLanes by remember { mutableStateOf(initialRoutes.second) }
    val routingMoment = when {
        phase in 0.48f..0.52f -> 1
        phase >= 0.98f || phase <= 0.02f -> 2
        else -> 0
    }
    LaunchedEffect(routingMoment) {
        if (routingMoment != 0) {
            val nextIncoming = randomIncomingLanes()
            val outgoingCount = Random.nextInt(
                from = 2,
                until = minOf(incomingLanes.size, nextIncoming.size) + 1,
            )
            outgoingLanes = incomingLanes.shuffled(Random).take(outgoingCount)
            incomingLanes = nextIncoming
        }
    }

    Surface(
        modifier = if (expanded) {
            Modifier
                .fillMaxWidth(1f / 3f)
                .aspectRatio(BeaconAspect)
        } else {
            Modifier.size(width = BeaconCompactWidth, height = BeaconCompactHeight)
        },
        shape = RoundedCornerShape(SharedDimensions.beaconCornerRadius),
        color = palette.field.copy(alpha = 0.96f - activeProgress * 0.06f),
        border = BorderStroke(
            1.dp,
            palette.accent.copy(alpha = 0.20f + 0.18f * activeProgress),
        ),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val laneCount = 7
                repeat(laneCount) { index ->
                    val spread = (index - (laneCount - 1) / 2f) * size.width * 0.105f
                    val path = Path().apply {
                        moveTo(centerX + spread, 0f)
                        cubicTo(
                            centerX + spread,
                            centerY * 0.40f,
                            centerX + spread * 0.24f,
                            centerY * 0.70f,
                            centerX + spread * 0.12f,
                            centerY,
                        )
                        cubicTo(
                            centerX + spread * 0.24f,
                            centerY * 1.30f,
                            centerX + spread,
                            centerY * 1.60f,
                            centerX + spread,
                            size.height,
                        )
                    }
                    drawPath(
                        path = path,
                        color = palette.accent.copy(alpha = 0.08f + activeProgress * 0.10f),
                        style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                val packetWave = (phase * 2f) % 1f
                incomingLanes.forEachIndexed { index, laneIndex ->
                    val delay = index * 0.045f
                    val localProgress =
                        ((packetWave - delay) / (1f - delay)).coerceIn(0f, 1f)
                    val progress = 0.03f + packetEase(localProgress) * 0.46f
                    val spread =
                        (laneIndex - (laneCount - 1) / 2f) * size.width * 0.105f
                    val point = tunnelLanePoint(
                        progress = progress,
                        centerX = centerX,
                        centerY = centerY,
                        height = size.height,
                        spread = spread,
                    )
                    val breath =
                        0.94f +
                            0.06f * sin((phase * 6.0 * PI + index * 1.7).toFloat())
                    val visibility =
                        packetVisibility(localProgress) *
                            (0.18f + activeProgress * 0.82f)
                    drawCircle(
                        color = palette.accent.copy(
                            alpha = (visibility * breath).coerceIn(0f, 1f),
                        ),
                        radius = ((2.2f + activeProgress * 1.2f) * breath).dp.toPx(),
                        center = point,
                    )
                }
                outgoingLanes.forEachIndexed { index, laneIndex ->
                    val delay = 0.03f + index * 0.055f
                    val localProgress =
                        ((packetWave - delay) / (1f - delay)).coerceIn(0f, 1f)
                    val progress = 0.51f + packetEase(localProgress) * 0.46f
                    val spread =
                        (laneIndex - (laneCount - 1) / 2f) * size.width * 0.105f
                    val point = tunnelLanePoint(
                        progress = progress,
                        centerX = centerX,
                        centerY = centerY,
                        height = size.height,
                        spread = spread,
                    )
                    val breath =
                        0.94f +
                            0.06f *
                            sin((phase * 6.0 * PI + index * 1.9 + 0.8).toFloat())
                    val visibility =
                        packetVisibility(localProgress) *
                            (0.18f + activeProgress * 0.82f)
                    drawCircle(
                        color = palette.secondary.copy(
                            alpha = (visibility * breath).coerceIn(0f, 1f),
                        ),
                        radius = ((2.2f + activeProgress * 1.2f) * breath).dp.toPx(),
                        center = point,
                    )
                }
                drawCircle(
                    color = palette.accent.copy(alpha = 0.12f),
                    radius = 45.dp.toPx(),
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = palette.accent,
                modifier = Modifier.size(68.dp),
                shadowElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (visualState) {
                        ProtectionVisualState.Off -> Icon(
                            imageVector = MaterialSymbols.Filled.Shield,
                            contentDescription = null,
                            tint = palette.icon,
                            modifier = Modifier.size(30.dp),
                        )
                        ProtectionVisualState.Recovering -> Icon(
                            imageVector = MaterialSymbols.Filled.WarningAmber,
                            contentDescription = null,
                            tint = palette.icon,
                            modifier = Modifier.size(30.dp),
                        )
                        ProtectionVisualState.Protected -> Icon(
                            imageVector = MaterialSymbols.Filled.Shield,
                            contentDescription = null,
                            tint = palette.icon,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun randomIncomingLanes(): List<Int> =
    (0 until 7)
        .shuffled(Random)
        .take(Random.nextInt(from = 3, until = 6))

private fun randomTunnelRoutes(): Pair<List<Int>, List<Int>> {
    val incoming = randomIncomingLanes()
    val outgoingCount = Random.nextInt(from = 2, until = incoming.size + 1)
    val outgoing = incoming.shuffled(Random).take(outgoingCount)
    return incoming to outgoing
}

private fun packetEase(progress: Float): Float =
    ((1f - cos((PI * progress).toFloat())) * 0.5f)

private fun packetVisibility(progress: Float): Float =
    sqrt(sin((PI * progress).toFloat()).coerceAtLeast(0f))

private fun tunnelLanePoint(
    progress: Float,
    centerX: Float,
    centerY: Float,
    height: Float,
    spread: Float,
): Offset {
    val firstHalf = progress < 0.5f
    val t = if (firstHalf) progress * 2f else (progress - 0.5f) * 2f
    return if (firstHalf) {
        Offset(
            x = cubicBezier(
                start = centerX + spread,
                control1 = centerX + spread,
                control2 = centerX + spread * 0.24f,
                end = centerX + spread * 0.12f,
                t = t,
            ),
            y = cubicBezier(
                start = 0f,
                control1 = centerY * 0.40f,
                control2 = centerY * 0.70f,
                end = centerY,
                t = t,
            ),
        )
    } else {
        Offset(
            x = cubicBezier(
                start = centerX + spread * 0.12f,
                control1 = centerX + spread * 0.24f,
                control2 = centerX + spread,
                end = centerX + spread,
                t = t,
            ),
            y = cubicBezier(
                start = centerY,
                control1 = centerY * 1.30f,
                control2 = centerY * 1.60f,
                end = height,
                t = t,
            ),
        )
    }
}

private fun cubicBezier(
    start: Float,
    control1: Float,
    control2: Float,
    end: Float,
    t: Float,
): Float {
    val inverse = 1f - t
    return inverse * inverse * inverse * start +
        3f * inverse * inverse * t * control1 +
        3f * inverse * t * t * control2 +
        t * t * t * end
}

@Composable
private fun BeaconAction(
    isActive: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ToggleButton(
        checked = isActive,
        onCheckedChange = { onClick() },
        modifier = Modifier.height(56.dp),
        colors = ToggleButtonDefaults.toggleButtonColors(
            checkedContainerColor = scheme.onSurface,
            checkedContentColor = scheme.surface,
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
        ),
    ) {
        Icon(
            imageVector = if (isActive) MaterialSymbols.Filled.Stop else MaterialSymbols.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(SharedDimensions.iconSizeMd),
        )
        Spacer(Modifier.width(SharedDimensions.spacingSm))
        Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HomeActivityFooter(
    connectionsLabel: String,
    dnsQueriesLabel: String,
    connections: Long,
    dnsQueries: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActivityMetric(
            value = formatCompactCount(connections),
            label = connectionsLabel,
            modifier = Modifier.weight(1f),
        )
        ActivityMetric(
            value = formatCompactCount(dnsQueries),
            label = dnsQueriesLabel,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActivityMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2f).sp,
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
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
