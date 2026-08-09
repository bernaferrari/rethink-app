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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
)

private enum class ProtectionVisualState { Protected, Recovering, Off }

private const val STATE_CHANGE_MILLIS = 280
private const val BEACON_TURN_MILLIS = 22_000

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = SharedDimensions.spacingXl),
            ) {
                HomeMasthead(
                    name = strings.productName,
                    isProtected = visualState == ProtectionVisualState.Protected,
                    accent = palette.accent,
                    modifier = Modifier.padding(top = SharedDimensions.spacingLg),
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
                    )
                }
                HomeActivityFooter(
                    connectionsLabel = strings.connections,
                    dnsQueriesLabel = strings.dnsQueries,
                    connections = uiState.networkLogsCount,
                    dnsQueries = uiState.dnsLogsCount,
                    modifier = Modifier.padding(bottom = SharedDimensions.spacingLg),
                )
            }
        }
    }
}

@Composable
private fun HomeMasthead(
    name: String,
    isProtected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "NETWORK PROTECTION",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = RoundedCornerShape(SharedDimensions.cornerRadiusPill),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
            border = BorderStroke(
                SharedDimensions.dividerThickness,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = SharedDimensions.spacingMd,
                    vertical = SharedDimensions.spacingSm,
                ),
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(
                            if (isProtected) accent else MaterialTheme.colorScheme.outline,
                            androidx.compose.foundation.shape.CircleShape,
                        ),
                )
                Text(
                    text = if (isProtected) "LIVE" else "OFFLINE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    val dark = scheme.surface.luminance() < 0.5f
    val protectedAccent = if (dark) Color(0xFF80E4C6) else Color(0xFF147D67)
    val protectedSecondary = if (dark) Color(0xFF72C9F3) else Color(0xFF2D75A4)
    val protectedField = if (dark) Color(0xFF143E34) else Color(0xFFE0F4ED)
    val accentTarget = when (state) {
        ProtectionVisualState.Protected -> protectedAccent
        ProtectionVisualState.Recovering -> scheme.error
        ProtectionVisualState.Off -> scheme.outline
    }
    val fieldTarget = when (state) {
        ProtectionVisualState.Protected -> protectedField
        ProtectionVisualState.Recovering -> scheme.errorContainer
        ProtectionVisualState.Off -> scheme.surfaceContainerLow
    }
    val secondaryTarget = when (state) {
        ProtectionVisualState.Protected -> protectedSecondary
        ProtectionVisualState.Recovering -> scheme.tertiary
        ProtectionVisualState.Off -> scheme.outlineVariant
    }
    val iconTarget = when (state) {
        ProtectionVisualState.Protected -> if (dark) Color(0xFF063C30) else Color.White
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
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
    ) {
        ProtectionTunnel(
            visualState = visualState,
            activeProgress = activeProgress,
            phase = phase,
            palette = palette,
        )
        Spacer(Modifier.height(SharedDimensions.spacingXs))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = status,
                style = MaterialTheme.typography.headlineLarge,
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = SharedDimensions.spacingSm),
            )
        }
        Spacer(Modifier.height(SharedDimensions.spacingXs))
        BeaconAction(
            isActive = visualState != ProtectionVisualState.Off,
            label = action,
            accent = palette.accent,
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
        modifier = Modifier
            .size(width = 184.dp, height = 220.dp),
        shape = RoundedCornerShape(72.dp),
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
                        ProtectionVisualState.Off -> {
                            Icon(
                                imageVector = MaterialSymbols.Filled.Shield,
                                contentDescription = null,
                                tint = palette.icon,
                                modifier = Modifier.size(30.dp),
                            )
                            Icon(
                                imageVector = MaterialSymbols.Filled.Shield,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
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
    accent: Color,
    onClick: () -> Unit,
) {
    val motion = LocalRethinkMotion.current
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val container by animateColorAsState(
        targetValue = if (isActive) scheme.onSurface else accent,
        animationSpec = tween(if (motion.reducedMotion) 0 else STATE_CHANGE_MILLIS, easing = FastOutSlowInEasing),
        label = "beaconActionContainer",
    )
    val content by animateColorAsState(
        targetValue = if (isActive) {
            scheme.surface
        } else if (dark) {
            Color(0xFF063C30)
        } else {
            Color.White
        },
        animationSpec = tween(if (motion.reducedMotion) 0 else STATE_CHANGE_MILLIS, easing = FastOutSlowInEasing),
        label = "beaconActionContent",
    )
    Button(
        onClick = onClick,
        modifier = Modifier.height(SharedDimensions.buttonHeight),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Icon(
            imageVector = if (isActive) MaterialSymbols.Filled.Stop else MaterialSymbols.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(SharedDimensions.iconSizeSm),
        )
        Spacer(Modifier.width(SharedDimensions.spacingSm))
        Text(text = label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadiusXl),
        border = BorderStroke(
            SharedDimensions.dividerThickness,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SharedDimensions.spacingLg, vertical = SharedDimensions.spacingMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActivityMetric(value = formatCompactCount(connections), label = connectionsLabel, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            ActivityMetric(value = formatCompactCount(dnsQueries), label = dnsQueriesLabel, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActivityMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4f).sp,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
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
