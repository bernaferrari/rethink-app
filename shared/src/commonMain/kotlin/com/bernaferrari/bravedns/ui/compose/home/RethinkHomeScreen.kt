@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Home answers three questions at a glance, in this order of weight:
 *  1. Am I protected? One coloured hero: a shape-morphing mark, the state in emphasized display
 *     type, the DNS in use, and the one primary action (Start, or a quieter Stop).
 *  2. What is it doing? The last 24 hours as allowed vs blocked, with a split bar, the lookups
 *     the DNS stopped, and the apps that were stopped most (each one opens its rules).
 *  3. Where do I go next? Apps, Logs and DNS as one connected group of large targets.
 * Phones stack these; wide windows put the hero in one pane and the activity in the other.
 */
package com.bernaferrari.bravedns.ui.compose.home

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import com.bernaferrari.bravedns.ui.compose.common.RethinkAdaptiveWidth
import com.bernaferrari.bravedns.ui.compose.common.WindowWidthClass
import com.bernaferrari.bravedns.ui.compose.common.isExpanded
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillColors
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillSize
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/** An app the firewall stopped, with how many of its connections were blocked. */
data class RethinkHomeBlockedApp(
    val id: String,
    val label: String,
    val count: Long,
    val icon: (@Composable () -> Unit)? = null,
)

data class RethinkHomeUiState(
    val isVpnActive: Boolean = false,
    val networkLogsCount: Long = 0,
    val dnsLogsCount: Long = 0,
    val protectionStatus: String = "",
    val isProtectionFailing: Boolean = false,
    /** Connections in the last 24 hours, by verdict. */
    val allowedToday: Long = 0,
    val blockedToday: Long = 0,
    /** DNS lookups the blocklists stopped in the last 24 hours. */
    val dnsBlockedToday: Long = 0,
    val topBlocked: List<RethinkHomeBlockedApp> = emptyList(),
    /** The resolver in use, shown in the hero while protected. */
    val dnsName: String = "",
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
    val last24Hours: String = "Last 24 hours",
    val allowed: String = "Allowed",
    val blocked: String = "Blocked",
    val stoppedAtDns: String = "lookups stopped at the DNS",
    val stoppedMost: String = "Stopped most",
    val nothingStopped: String = "Nothing has been blocked in the last 24 hours.",
    val seeStatistics: String = "Statistics",
    val apps: String = "Apps",
    val appsCaption: String = "Wi-Fi and data, app by app",
    val logs: String = "Logs",
    val logsCaption: String = "Every connection",
    val dns: String = "DNS",
    val dnsCaption: String = "Resolver and blocklists",
    val blockedCount: (Long) -> String = { "$it blocked" },
)

private enum class ProtectionVisualState { Protected, Recovering, Off }

private const val MARK_TURN_MILLIS = 24_000
private val MarkSizeCompact = 104.dp
private val MarkSizeExpanded = 232.dp
private val HeroCorner = 40.dp
private val SplitBarHeight = 14.dp
private val QuickTileHeight = 112.dp

@Composable
fun RethinkHomeScreen(
    uiState: RethinkHomeUiState,
    strings: RethinkHomeStrings,
    onStartStopClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Kept for hosts that draw their own background; Home itself is opaque now. */
    @Suppress("UNUSED_PARAMETER") showAtmosphere: Boolean = true,
    /** Opens the full breakdown (Statistics); null hides the link. */
    onActivityClick: (() -> Unit)? = null,
    onAppsClick: (() -> Unit)? = null,
    onLogsClick: (() -> Unit)? = null,
    onDnsClick: (() -> Unit)? = null,
    onBlockedAppClick: ((String) -> Unit)? = null,
) {
    val visualState = uiState.visualState()
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) { insets ->
        RethinkAdaptiveWidth(Modifier.fillMaxSize().padding(insets)) { widthClass ->
            val hero: @Composable (Modifier) -> Unit = { heroModifier ->
                ProtectionHero(uiState, strings, visualState, widthClass, onStartStopClick, heroModifier)
            }
            val activity: @Composable ColumnScope.() -> Unit = {
                TodaySection(uiState, strings, onActivityClick)
                StoppedMostSection(uiState.topBlocked, strings, onBlockedAppClick)
                QuickActions(strings, onAppsClick, onLogsClick, onDnsClick)
            }
            if (widthClass.isExpanded) {
                Row(
                    Modifier.fillMaxSize().padding(SharedDimensions.spacingXl),
                    horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXl),
                ) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        HomeHeader(strings, visualState)
                        Spacer(Modifier.height(SharedDimensions.spacingLg))
                        hero(Modifier.weight(1f).fillMaxWidth())
                    }
                    Column(
                        Modifier.weight(1.05f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXl),
                        content = activity,
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingLg),
                        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXl),
                    ) {
                        Column {
                            HomeHeader(strings, visualState)
                            Spacer(Modifier.height(SharedDimensions.spacingLg))
                            hero(Modifier.fillMaxWidth())
                        }
                        activity()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(strings: RethinkHomeStrings, state: ProtectionVisualState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            strings.productName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        RethinkPill(
            text = when (state) {
                ProtectionVisualState.Protected -> strings.live
                ProtectionVisualState.Recovering -> strings.reconnecting
                ProtectionVisualState.Off -> strings.offline
            },
            colors = RethinkPillColors(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface),
            size = RethinkPillSize.Large,
            dotColor = when (state) {
                ProtectionVisualState.Protected -> RethinkStatusColors.allowed
                ProtectionVisualState.Recovering -> MaterialTheme.colorScheme.error
                ProtectionVisualState.Off -> MaterialTheme.colorScheme.outline
            },
        )
    }
}

private data class HeroColors(val container: Color, val content: Color, val mark: Color, val onMark: Color, val halo: Color)

@Composable
private fun heroColors(state: ProtectionVisualState): HeroColors {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalRethinkMotion.current
    val target = when (state) {
        ProtectionVisualState.Protected -> HeroColors(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary, scheme.onPrimary, scheme.onPrimaryContainer.copy(alpha = 0.12f))
        ProtectionVisualState.Recovering -> HeroColors(scheme.errorContainer, scheme.onErrorContainer, scheme.error, scheme.onError, scheme.onErrorContainer.copy(alpha = 0.12f))
        ProtectionVisualState.Off -> HeroColors(scheme.surfaceContainerHigh, scheme.onSurface, scheme.surfaceContainerHighest, scheme.onSurfaceVariant, scheme.outlineVariant.copy(alpha = 0.5f))
    }
    val spec: AnimationSpec<Color> = if (motion.reducedMotion) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()
    val container by animateColorAsState(target.container, spec, label = "heroContainer")
    val content by animateColorAsState(target.content, spec, label = "heroContent")
    val mark by animateColorAsState(target.mark, spec, label = "heroMark")
    val onMark by animateColorAsState(target.onMark, spec, label = "heroOnMark")
    val halo by animateColorAsState(target.halo, spec, label = "heroHalo")
    return HeroColors(container, content, mark, onMark, halo)
}

/** The protection state as one coloured hero: mark, state, resolver, action. */
@Composable
private fun ProtectionHero(
    uiState: RethinkHomeUiState,
    strings: RethinkHomeStrings,
    state: ProtectionVisualState,
    widthClass: WindowWidthClass,
    onStartStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = heroColors(state)
    val expanded = widthClass.isExpanded
    val status = uiState.protectionStatus.ifBlank { if (uiState.isVpnActive) strings.protected else strings.notActive }
    val subtitle = when (state) {
        ProtectionVisualState.Protected -> strings.protectedSubtitle
        ProtectionVisualState.Recovering -> strings.failingSubtitle
        ProtectionVisualState.Off -> strings.inactiveSubtitle
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(HeroCorner), color = colors.container, contentColor = colors.content) {
        Column(
            Modifier.padding(if (expanded) SharedDimensions.spacing2xl else SharedDimensions.spacingXl),
        ) {
            val dnsPill: @Composable () -> Unit = {
                if (state != ProtectionVisualState.Off && uiState.dnsName.isNotBlank()) {
                    RethinkPill(
                        text = uiState.dnsName,
                        colors = RethinkPillColors(colors.content.copy(alpha = 0.1f), colors.content),
                        size = RethinkPillSize.Large,
                        leadingIcon = MaterialSymbols.Filled.Dns,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                }
            }
            if (expanded) {
                // Wide: the mark is the centre of the pane, the words and the action below it.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { dnsPill() }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ProtectionMark(state, colors, MarkSizeExpanded)
                }
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    ProtectionMark(state, colors, MarkSizeCompact)
                    Spacer(Modifier.weight(1f))
                    dnsPill()
                }
                Spacer(Modifier.height(SharedDimensions.spacingXl))
            }
            Text(
                text = status,
                style = if (expanded) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
                lineHeight = if (expanded) 60.sp else 50.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(SharedDimensions.spacingSm))
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = colors.content.copy(alpha = 0.82f))
            Spacer(Modifier.height(SharedDimensions.spacingXl))
            HeroAction(
                isActive = uiState.isVpnActive,
                label = if (uiState.isVpnActive) strings.stop else strings.start,
                stateDescription = status,
                heroColor = colors.container,
                contentColor = colors.content,
                onClick = onStartStopClick,
            )
        }
    }
}

/**
 * The hero mark: a Material shape that morphs from a circle (off) into a 12-sided cookie
 * (protected) on a spatial spring, over a soft-burst halo. While protected the two turn slowly in
 * opposite directions; with reduced motion they rest at angles where their lobes interleave.
 */
@Composable
private fun ProtectionMark(state: ProtectionVisualState, colors: HeroColors, size: Dp) {
    val motion = LocalRethinkMotion.current
    val active = state != ProtectionVisualState.Off
    val morphProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (motion.reducedMotion) snap() else MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "homeMarkMorph",
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided) }
    val haloShape = MaterialShapes.SoftBurst.toShape()
    val phase = rememberTurn(active && !motion.reducedMotion)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    alpha = morphProgress
                    rotationZ = HALO_REST_ANGLE - phase.value * 180f
                }
                .background(colors.halo, haloShape),
        )
        Box(
            Modifier
                .size(size * 0.78f)
                .graphicsLayer { rotationZ = phase.value * 360f }
                .background(colors.mark, MorphShape(morph, morphProgress)),
        )
        Icon(
            imageVector = if (state == ProtectionVisualState.Recovering) MaterialSymbols.Filled.WarningAmber else MaterialSymbols.Filled.Shield,
            contentDescription = null,
            tint = colors.onMark,
            modifier = Modifier.size(size * 0.34f),
        )
    }
}

private const val HALO_REST_ANGLE = 15f

/** A slow 0..1 turn read only in layer scopes, so Home never recomposes per frame. */
@Composable
private fun rememberTurn(running: Boolean): State<Float> {
    if (!running) return remember { StillTurn }
    return rememberInfiniteTransition(label = "homeMarkTurn").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(MARK_TURN_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "homeMarkPhase",
    )
}

private object StillTurn : State<Float> {
    override val value: Float = 0f
}

/**
 * Start is the call to action: a large filled button. Stop is deliberately quieter: a light
 * button on the coloured hero, so protection is not one careless tap away from off.
 */
@Composable
private fun HeroAction(isActive: Boolean, label: String, stateDescription: String, heroColor: Color, contentColor: Color, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(height),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .semantics { this.stateDescription = stateDescription },
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        colors = ButtonDefaults.buttonColors(
            // A near-white tint of the hero itself, in light and dark alike.
            containerColor = if (isActive) lerp(heroColor, Color.White, 0.82f) else scheme.primary,
            contentColor = if (isActive) contentColor else scheme.onPrimary,
        ),
    ) {
        Icon(
            if (isActive) MaterialSymbols.Filled.Stop else MaterialSymbols.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
        )
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        Text(label, style = ButtonDefaults.textStyleFor(height))
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = SharedDimensions.touchTargetMin), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action)
                Icon(MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(SharedDimensions.iconSizeSm))
            }
        }
    }
}

/** The last 24 hours: allowed vs blocked as numbers and as one split bar. */
@Composable
private fun TodaySection(uiState: RethinkHomeUiState, strings: RethinkHomeStrings, onActivityClick: (() -> Unit)?) {
    Column {
        SectionHeader(strings.last24Hours, strings.seeStatistics, onActivityClick)
        Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadius2xl), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(SharedDimensions.spacingXl), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg)) {
                Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg)) {
                    TodayMetric(formatGrouped(uiState.allowedToday), strings.allowed, RethinkStatusColors.allowed, MaterialSymbols.Filled.CheckCircle, Modifier.weight(1f))
                    TodayMetric(formatGrouped(uiState.blockedToday), strings.blocked, RethinkStatusColors.blocked, MaterialSymbols.Filled.Block, Modifier.weight(1f))
                }
                SplitBar(uiState.allowedToday, uiState.blockedToday)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    Icon(MaterialSymbols.Filled.Dns, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SharedDimensions.iconSizeSm))
                    Text(
                        "${formatGrouped(uiState.dnsBlockedToday)} ${strings.stoppedAtDns}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayMetric(value: String, label: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(SharedDimensions.iconSizeXs))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            value,
            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
    }
}

/** Allowed (green) and blocked (error) as one rounded bar with a small gap between the parts. */
@Composable
private fun SplitBar(allowed: Long, blocked: Long) {
    val total = (allowed + blocked).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(SplitBarHeight), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (allowed + blocked == 0L) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            return@Row
        }
        if (allowed > 0) Box(Modifier.weight(allowed.toFloat() / total).fillMaxHeight().clip(CircleShape).background(RethinkStatusColors.allowed))
        if (blocked > 0) Box(Modifier.weight(blocked.toFloat() / total).fillMaxHeight().clip(CircleShape).background(RethinkStatusColors.blocked))
    }
}

/** The apps the firewall stopped most, each a row that opens its rules. */
@Composable
private fun StoppedMostSection(apps: List<RethinkHomeBlockedApp>, strings: RethinkHomeStrings, onClick: ((String) -> Unit)?) {
    Column {
        SectionHeader(strings.stoppedMost)
        if (apps.isEmpty()) {
            Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadius2xl), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(
                    strings.nothingStopped,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(SharedDimensions.spacingXl),
                )
            }
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
            apps.forEachIndexed { index, app ->
                StoppedAppRow(app, strings, groupedShape(index, apps.size), onClick)
            }
        }
    }
}

@Composable
private fun StoppedAppRow(app: RethinkHomeBlockedApp, strings: RethinkHomeStrings, shape: Shape, onClick: ((String) -> Unit)?) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingLg, vertical = SharedDimensions.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            Box(Modifier.size(SharedDimensions.iconContainerMd).clip(RoundedCornerShape(SharedDimensions.cornerRadiusMd)), contentAlignment = Alignment.Center) {
                app.icon?.invoke() ?: Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
            }
            Text(app.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            RethinkPill(
                text = strings.blockedCount(app.count),
                colors = RethinkPillDefaults.blocked(),
                size = RethinkPillSize.Medium,
                leadingIcon = MaterialSymbols.Filled.Block,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    val color = MaterialTheme.colorScheme.surfaceContainerLow
    if (onClick != null) {
        Surface(onClick = { onClick(app.id) }, shape = shape, color = color, content = content)
    } else {
        Surface(shape = shape, color = color, content = content)
    }
}

/** Apps, Logs and DNS as one connected group: big targets, outer corners round, inner ones tight. */
@Composable
private fun QuickActions(strings: RethinkHomeStrings, onApps: (() -> Unit)?, onLogs: (() -> Unit)?, onDns: (() -> Unit)?) {
    val actions = listOfNotNull(
        onApps?.let { QuickAction(strings.apps, strings.appsCaption, MaterialSymbols.Filled.Apps, MaterialShapes.Cookie9Sided, it) },
        onLogs?.let { QuickAction(strings.logs, strings.logsCaption, MaterialSymbols.AutoMirrored.Filled.Subject, MaterialShapes.Pill, it) },
        onDns?.let { QuickAction(strings.dns, strings.dnsCaption, MaterialSymbols.Filled.Dns, MaterialShapes.Clover4Leaf, it) },
    )
    if (actions.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
        actions.forEachIndexed { index, action ->
            QuickTile(action, connectedShape(index, actions.size), Modifier.weight(1f).fillMaxHeight())
        }
    }
}

private class QuickAction(
    val label: String,
    val caption: String,
    val icon: ImageVector,
    val shape: androidx.graphics.shapes.RoundedPolygon,
    val onClick: () -> Unit,
)

@Composable
private fun QuickTile(action: QuickAction, shape: Shape, modifier: Modifier = Modifier) {
    Surface(
        onClick = action.onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = QuickTileHeight),
    ) {
        Column(Modifier.padding(SharedDimensions.spacingLg), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
            Box(
                Modifier.size(SharedDimensions.iconContainerLg).background(MaterialTheme.colorScheme.secondaryContainer, action.shape.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(action.icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(SharedDimensions.iconSizeMd))
            }
            Text(action.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(action.caption, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val OuterCorner = SharedDimensions.cornerRadius2xl
private val InnerCorner = SharedDimensions.cornerRadiusSm

private fun groupedShape(index: Int, count: Int) = RoundedCornerShape(
    topStart = if (index == 0) OuterCorner else InnerCorner,
    topEnd = if (index == 0) OuterCorner else InnerCorner,
    bottomStart = if (index == count - 1) OuterCorner else InnerCorner,
    bottomEnd = if (index == count - 1) OuterCorner else InnerCorner,
)

private fun connectedShape(index: Int, count: Int) = RoundedCornerShape(
    topStart = if (index == 0) OuterCorner else InnerCorner,
    bottomStart = if (index == 0) OuterCorner else InnerCorner,
    topEnd = if (index == count - 1) OuterCorner else InnerCorner,
    bottomEnd = if (index == count - 1) OuterCorner else InnerCorner,
)

private fun RethinkHomeUiState.visualState(): ProtectionVisualState = when {
    isVpnActive && isProtectionFailing -> ProtectionVisualState.Recovering
    isVpnActive -> ProtectionVisualState.Protected
    else -> ProtectionVisualState.Off
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

/** 1234567 → "1,234,567"; counts read faster grouped. */
internal fun formatGrouped(raw: Long): String {
    val digits = raw.coerceAtLeast(0).toString()
    return digits.reversed().chunked(3).joinToString(",").reversed()
}

/**
 * The web demo's ambient field behind Home: the protection flow, tinted by the same state.
 * Android Home is opaque and does not use it.
 */
@Composable
internal fun RethinkHomeAtmosphere(
    uiState: RethinkHomeUiState,
    modifier: Modifier = Modifier,
) {
    val state = uiState.visualState()
    val motion = LocalRethinkMotion.current
    val scheme = MaterialTheme.colorScheme
    val spec = if (motion.reducedMotion) snap<Float>() else MaterialTheme.motionScheme.defaultEffectsSpec()
    val active by animateFloatAsState(if (uiState.isVpnActive) 1f else 0f, spec, label = "atmosphereActive")
    val recovering by animateFloatAsState(if (state == ProtectionVisualState.Recovering) 1f else 0f, spec, label = "atmosphereRecovering")
    val phase = rememberTurn(!motion.reducedMotion)
    Box(modifier) {
        ProtectionFlowBackground(
            phase = { phase.value },
            activeProgress = active,
            recoveringProgress = recovering,
            accent = if (state == ProtectionVisualState.Recovering) scheme.error else scheme.primary,
            secondary = scheme.tertiary,
            field = scheme.primaryContainer,
            surface = scheme.surface,
            opacity = 0.56f,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
