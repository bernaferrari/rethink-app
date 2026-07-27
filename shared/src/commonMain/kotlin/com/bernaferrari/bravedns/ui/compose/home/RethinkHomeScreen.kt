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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
        animationSpec = tween(STATE_CHANGE_MILLIS, easing = FastOutSlowInEasing),
        label = "homeBeaconState",
    )
    val recoveringProgress by animateFloatAsState(
        targetValue = if (visualState == ProtectionVisualState.Recovering) 1f else 0f,
        animationSpec = tween(STATE_CHANGE_MILLIS, easing = FastOutSlowInEasing),
        label = "homeRecoveringProgress",
    )
    val phase by rememberInfiniteTransition(label = "homeAtmosphereMotion").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(BEACON_TURN_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "homeAtmospherePhase",
    )
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
                    opacity = 0.90f,
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
private fun HomeMasthead(name: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(SharedDimensions.spacingSm))
        Text(
            text = "NETWORK PROTECTION",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    val spec = tween<Color>(STATE_CHANGE_MILLIS, easing = FastOutSlowInEasing)
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
    val visualState = when {
        uiState.isVpnActive && uiState.isProtectionFailing -> ProtectionVisualState.Recovering
        uiState.isVpnActive -> ProtectionVisualState.Protected
        else -> ProtectionVisualState.Off
    }
    val activeProgress by animateFloatAsState(
        targetValue = if (uiState.isVpnActive) 1f else 0f,
        animationSpec = tween(STATE_CHANGE_MILLIS, easing = FastOutSlowInEasing),
        label = "homeAtmosphereState",
    )
    val recoveringProgress by animateFloatAsState(
        targetValue = if (visualState == ProtectionVisualState.Recovering) 1f else 0f,
        animationSpec = tween(STATE_CHANGE_MILLIS, easing = FastOutSlowInEasing),
        label = "homeAtmosphereRecovering",
    )
    val phase by rememberInfiniteTransition(label = "homeFullBleedAtmosphere").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(BEACON_TURN_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "homeFullBleedAtmospherePhase",
    )
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
            opacity = 0.90f,
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
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
    ) {
        Box(
            modifier = Modifier
                .size(252.dp)
                .semantics {
                    contentDescription = action
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            BeaconField(
                phase = phase,
                activeProgress = activeProgress,
                accent = palette.accent,
                field = palette.field,
                modifier = Modifier.fillMaxSize(),
            )
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = palette.accent,
                modifier = Modifier.size(76.dp),
                shadowElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (visualState) {
                            ProtectionVisualState.Recovering -> MaterialSymbols.Filled.WarningAmber
                            else -> MaterialSymbols.Filled.Shield
                        },
                        contentDescription = null,
                        tint = palette.icon,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }
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
            Spacer(Modifier.height(SharedDimensions.spacingSm))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = SharedDimensions.spacingSm),
            )
        }
        BeaconAction(
            isActive = visualState != ProtectionVisualState.Off,
            label = action,
            accent = palette.accent,
            onClick = onToggle,
        )
    }
}

@Composable
private fun BeaconField(
    phase: Float,
    activeProgress: Float,
    accent: Color,
    field: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension
        drawCircle(
            color = field,
            radius = base * 0.48f,
            center = center,
        )
        val ringAlphas = listOf(0.12f, 0.18f, 0.26f)
        ringAlphas.forEachIndexed { index, alpha ->
            val pulse = (sin((phase * 2.0 * PI + index * 1.8).toFloat()) + 1f) / 2f
            val radius = base * (0.23f + index * 0.105f + pulse * 0.010f * activeProgress)
            drawCircle(
                color = accent.copy(alpha = alpha * (0.32f + 0.68f * activeProgress)),
                radius = radius,
                center = center,
                style = Stroke(width = 1.25.dp.toPx()),
            )
        }
        if (activeProgress > 0.01f) {
            repeat(2) { index ->
                val angle = (phase * 2.0 * PI + index * PI).toFloat()
                val orbit = base * (0.37f + index * 0.025f)
                drawCircle(
                    color = accent.copy(alpha = 0.88f * activeProgress),
                    radius = 3.5.dp.toPx(),
                    center = Offset(center.x + orbit * cos(angle), center.y + orbit * sin(angle)),
                )
            }
        }
    }
}

@Composable
private fun BeaconAction(
    isActive: Boolean,
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val container by animateColorAsState(
        targetValue = if (isActive) scheme.surfaceContainerHigh else accent,
        animationSpec = tween(STATE_CHANGE_MILLIS, easing = FastOutSlowInEasing),
        label = "beaconActionContainer",
    )
    val content by animateColorAsState(
        targetValue = if (isActive) scheme.onSurface else if (dark) Color(0xFF063C30) else Color.White,
        animationSpec = tween(STATE_CHANGE_MILLIS, easing = FastOutSlowInEasing),
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadiusXl),
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
