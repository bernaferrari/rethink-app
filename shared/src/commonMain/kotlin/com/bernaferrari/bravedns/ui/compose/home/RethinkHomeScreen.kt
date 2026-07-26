/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Common render layer for the real Home screen. Platform code owns only live state, Android
 * resources, and side effects; the layout and every visual decision live here.
 */
package com.bernaferrari.bravedns.ui.compose.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
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
    val protection: String,
    val protected: String,
    val notActive: String,
    val start: String,
    val stop: String,
    val protectedSubtitle: String,
    val inactiveSubtitle: String,
    val failingSubtitle: String,
    val activity: String,
    val activitySubtitle: String,
    val connections: String,
    val dnsQueries: String,
)

private enum class ProtectionVisualState {
    Protected,
    Recovering,
    Off,
}

private data class ProtectionMotion(
    val rotation: Float = 0f,
    val pulse: Float = 0f,
)

@Composable
fun RethinkHomeScreen(
    uiState: RethinkHomeUiState,
    strings: RethinkHomeStrings,
    onStartStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visualState = when {
        uiState.isVpnActive && !uiState.isProtectionFailing -> ProtectionVisualState.Protected
        uiState.isProtectionFailing && uiState.isVpnActive -> ProtectionVisualState.Recovering
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

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingXl,
                bottom = SharedDimensions.spacing3xl,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            item {
                Text(
                    text = strings.productName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp,
                    modifier = Modifier.padding(horizontal = SharedDimensions.spacingSm),
                )
            }
            item {
                ProtectionHero(
                    visualState = visualState,
                    status = status,
                    subtitle = subtitle,
                    protectionLabel = strings.protection,
                    actionLabel = if (uiState.isVpnActive) strings.stop else strings.start,
                    isVpnActive = uiState.isVpnActive,
                    onStartStopClick = onStartStopClick,
                )
            }
            item {
                ActivitySection(
                    title = strings.activity,
                    subtitle = strings.activitySubtitle,
                    connectionsLabel = strings.connections,
                    dnsQueriesLabel = strings.dnsQueries,
                    connections = uiState.networkLogsCount,
                    dnsQueries = uiState.dnsLogsCount,
                    isActive = uiState.isVpnActive,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProtectionHero(
    visualState: ProtectionVisualState,
    status: String,
    subtitle: String,
    protectionLabel: String,
    actionLabel: String,
    isVpnActive: Boolean,
    onStartStopClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val protectedAccent = if (isDark) Color(0xFF73E2C5) else Color(0xFF167D69)
    val protectedContainer = if (isDark) Color(0xFF113B32) else Color(0xFFDDF6EE)
    val targetAccent = when (visualState) {
        ProtectionVisualState.Protected -> protectedAccent
        ProtectionVisualState.Recovering -> colors.error
        ProtectionVisualState.Off -> colors.onSurfaceVariant
    }
    val targetContainer = when (visualState) {
        ProtectionVisualState.Protected -> protectedContainer
        ProtectionVisualState.Recovering -> colors.errorContainer
        ProtectionVisualState.Off -> colors.surfaceContainerHigh
    }
    val accent by animateColorAsState(targetAccent, tween(220), label = "homeAccent")
    val container by animateColorAsState(targetContainer, tween(220), label = "homeContainer")
    val fieldProgress by animateFloatAsState(
        targetValue = if (isVpnActive) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "homeField",
    )
    val motion = rememberProtectionMotion(
        isMoving = visualState == ProtectionVisualState.Protected,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.heroCornerRadius),
        color = container,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SharedDimensions.spacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            ) {
                Surface(
                    modifier = Modifier.size(9.dp),
                    shape = CircleShape,
                    color = accent,
                ) {}
                Text(
                    text = protectionLabel.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = colors.onSurfaceVariant,
                )
            }

            ProtectionField(
                accent = accent,
                progress = fieldProgress,
                visualState = visualState,
                motion = motion,
                modifier = Modifier.fillMaxWidth().height(190.dp),
            )

            Text(
                text = status,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(SharedDimensions.spacingXs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SharedDimensions.spacingXl))
            StartStopButton(
                isPlaying = isVpnActive,
                label = actionLabel,
                onClick = onStartStopClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun rememberProtectionMotion(isMoving: Boolean): ProtectionMotion {
    if (!isMoving) return ProtectionMotion()

    val transition = rememberInfiniteTransition(label = "protectionMotion")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "protectionRotation",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "protectionPulse",
    )
    return ProtectionMotion(rotation = rotation, pulse = pulse)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProtectionField(
    accent: Color,
    progress: Float,
    visualState: ProtectionVisualState,
    motion: ProtectionMotion,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val orbitRadii = listOf(58.dp.toPx(), 78.dp.toPx(), 96.dp.toPx())

            if (visualState == ProtectionVisualState.Protected) {
                drawCircle(
                    color = accent.copy(alpha = (1f - motion.pulse) * 0.13f),
                    radius = 50.dp.toPx() + (motion.pulse * 38.dp.toPx()),
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            orbitRadii.forEachIndexed { index, radius ->
                val direction = if (index % 2 == 0) 1f else -1f
                val start = (motion.rotation * direction * (0.58f + index * 0.16f)) + index * 74f
                drawArc(
                    color = accent.copy(alpha = 0.11f + (progress * 0.25f)),
                    startAngle = start,
                    sweepAngle = 96f + index * 22f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                    style = Stroke(width = if (index == 2) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent.copy(alpha = 0.07f + (progress * 0.12f)),
                    startAngle = start + 178f,
                    sweepAngle = 54f + index * 12f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            repeat(7) { index ->
                val angleDegrees =
                    (motion.rotation * (if (index % 2 == 0) 1.0 else -0.72)) +
                        index * (360.0 / 7.0)
                val angle = angleDegrees * PI / 180.0
                val radius = orbitRadii[index % orbitRadii.size]
                val point = Offset(
                    x = center.x + cos(angle).toFloat() * radius,
                    y = center.y + sin(angle).toFloat() * radius,
                )
                drawCircle(
                    color = accent.copy(alpha = 0.20f + (progress * 0.58f)),
                    radius = if (index % 3 == 0) 4.dp.toPx() else 2.5.dp.toPx(),
                    center = point,
                )
            }
        }

        Surface(
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    rotationZ = motion.rotation * 0.32f
                    val pulseScale = 1f + (motion.pulse * 0.025f)
                    scaleX = pulseScale
                    scaleY = pulseScale
                },
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = accent,
            shadowElevation = if (visualState == ProtectionVisualState.Protected) 4.dp else 1.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (visualState) {
                        ProtectionVisualState.Protected -> MaterialSymbols.Filled.Shield
                        ProtectionVisualState.Recovering -> MaterialSymbols.Filled.WarningAmber
                        ProtectionVisualState.Off -> MaterialSymbols.Filled.ShieldMoon
                    },
                    contentDescription = null,
                    tint = when (visualState) {
                        ProtectionVisualState.Protected -> if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                            Color(0xFF063C30)
                        } else {
                            Color.White
                        }
                        ProtectionVisualState.Recovering -> MaterialTheme.colorScheme.onError
                        ProtectionVisualState.Off -> MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .graphicsLayer { rotationZ = -(motion.rotation * 0.32f) },
                )
            }
        }
    }
}

@Composable
private fun StartStopButton(
    isPlaying: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "homeActionScale",
    )

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.height(SharedDimensions.buttonHeight).graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPlaying) colors.onSurface else colors.primary,
            contentColor = if (isPlaying) colors.surface else colors.onPrimary,
        ),
    ) {
        Icon(
            imageVector = if (isPlaying) MaterialSymbols.Filled.Stop else MaterialSymbols.Filled.PlayArrow,
            contentDescription = label,
            modifier = Modifier.size(SharedDimensions.iconSizeSm),
        )
        Spacer(Modifier.width(SharedDimensions.spacingSm))
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActivitySection(
    title: String,
    subtitle: String,
    connectionsLabel: String,
    dnsQueriesLabel: String,
    connections: Long,
    dnsQueries: Long,
    isActive: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
    ) {
        Column(Modifier.padding(horizontal = SharedDimensions.spacingSm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            ) {
                if (isActive) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = if (colors.surface.luminance() < 0.5f) Color(0xFF73E2C5) else Color(0xFF167D69),
                    ) {}
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            ActivityMetricCard(
                value = formatCompactCount(connections),
                label = connectionsLabel,
                icon = MaterialSymbols.Filled.Subject,
                modifier = Modifier.weight(1f),
            )
            ActivityMetricCard(
                value = formatCompactCount(dnsQueries),
                label = dnsQueriesLabel,
                icon = MaterialSymbols.Filled.Dns,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActivityMetricCard(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(126.dp),
        shape = RoundedCornerShape(SharedDimensions.cardCornerRadiusLarge),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(SharedDimensions.cardPadding),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(SharedDimensions.iconContainerRadius),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(SharedDimensions.iconSizeXs),
                    )
                }
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
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
    return if (tenths % 10 == 0) {
        "${tenths / 10}$suffix"
    } else {
        "${tenths / 10}.${tenths % 10}$suffix"
    }
}
