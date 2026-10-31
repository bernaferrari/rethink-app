@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Home answers two questions at a glance, in this order of weight:
 *  1. Am I protected? One coloured hero: a shape-morphing mark, the state in emphasized display
 *     type, the DNS in use as a tappable line, and the controls (Start; or Pause ▾ · Stop; or
 *     Resume · Stop while paused, with the pause's time adjustable in place). A health banner above it says when protection isn't doing
 *     what the hero claims (another always-on VPN, Private DNS, a resolver that isn't answering),
 *     each with its fix; one card below it marks the first start or asks for notifications.
 *  2. What is it doing? The last 24 hours as allowed vs blocked, with a split bar, the lookups
 *     the DNS stopped, and the apps that were stopped most (each one opens its detail).
 * Apps and Activity are tabs, so Home no longer repeats them as tiles.
 * Phones stack these; wide windows put the hero in one pane and the activity in the other.
 */
package com.bernaferrari.bravedns.ui.compose.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkContainedCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
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
import com.bernaferrari.bravedns.ui.compose.common.RethinkWindowBreakpoints
import com.bernaferrari.bravedns.ui.compose.common.WindowWidthClass
import com.bernaferrari.bravedns.ui.compose.common.isExpanded
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
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
    /** The hero's DNS line ("DNS: Keepout · Default"); falls back to [dnsName] when blank. */
    val dnsLine: String = "",
    val isPaused: Boolean = false,
    /** The paused hero's subtitle: time left (or "until you resume") and what still applies. */
    val pausedDetail: String = "",
    /** The pause has a countdown; false while paused means it lasts until resumed. */
    val pauseTimed: Boolean = false,
    /** More than 15 minutes are left, so "−15 min" can't end the pause outright. */
    val pauseCanShorten: Boolean = false,
)

/** How long the hero's Pause menu pauses protection. */
enum class RethinkPauseChoice { FifteenMinutes, OneHour, UntilResumed }

/**
 * Changes to a pause in progress, from the paused hero: a timed pause moves by 15 minutes or
 * drops its timer; one that lasts until resumed gets a timer (15 minutes or an hour from now).
 */
enum class RethinkPauseAdjustment { RemoveFifteenMinutes, AddFifteenMinutes, UntilResumed, FifteenMinutes, OneHour }

/**
 * A problem that makes the hero's state untrue or unreachable (a conflicting always-on VPN,
 * Private DNS overriding Keepout, a resolver that isn't answering), with the one action that fixes it.
 */
data class RethinkHomeBanner(
    val title: String,
    val body: String,
    val actionLabel: String,
    val onAction: () -> Unit,
)

/**
 * A one-off card under the hero (the first start's "You're protected", the deferred notification
 * ask): a primary action, an optional quiet one, and an optional dismiss.
 */
data class RethinkHomeCard(
    val title: String,
    val body: String,
    val icon: ImageVector,
    val primaryLabel: String,
    val onPrimary: () -> Unit,
    val secondaryLabel: String? = null,
    val onSecondary: (() -> Unit)? = null,
    val dismissLabel: String? = null,
    val onDismiss: (() -> Unit)? = null,
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
    val stoppedAtDns: (Long) -> String = { if (it == 1L) "$it lookup stopped at the DNS" else "${formatGrouped(it)} lookups stopped at the DNS" },
    val stoppedMost: String = "Stopped most",
    val nothingStopped: String = "Nothing has been blocked in the last 24 hours.",
    val seeStatistics: String = "Statistics",
    val blockedCount: (Long) -> String = { "$it blocked" },
    val paused: String = "Paused",
    val pause: String = "Pause",
    val pauseFifteenMinutes: String = "15 minutes",
    val pauseOneHour: String = "1 hour",
    val pauseUntilResumed: String = "Until I resume",
    val pauseRemoveFifteenMinutes: String = "\u221215 min",
    val pauseRemoveFifteenMinutesDescription: String = "15 minutes less",
    val pauseAddFifteenMinutes: String = "+15 min",
    val pauseAddFifteenMinutesDescription: String = "15 minutes more",
    val pauseNoTimer: String = "No timer",
    val pauseNoTimerDescription: String = "Pause until I resume",
    val pauseResumeInFifteenMinutesDescription: String = "Resume in 15 minutes",
    val pauseResumeInOneHourDescription: String = "Resume in 1 hour",
    val resume: String = "Resume",
    val openDns: String = "Open DNS settings",
)

private enum class ProtectionVisualState { Protected, Recovering, Paused, Off }

private const val MARK_TURN_MILLIS = 24_000
private val MarkSizeCompact = 104.dp
private val MarkSizeExpanded = 232.dp
private val HeroCorner = 40.dp
private val SplitBarHeight = 14.dp
/** Inner corners of the hero's connected button pair (M3 Expressive connected group). */
private val ConnectedInnerCorner = 8.dp

@Composable
fun RethinkHomeScreen(
    uiState: RethinkHomeUiState,
    strings: RethinkHomeStrings,
    onStartStopClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Kept for hosts that draw their own background; Home itself is opaque now. */
    @Suppress("UNUSED_PARAMETER") showAtmosphere: Boolean = true,
    /** Opens the full breakdown (Activity › Insights); null hides the link. */
    onActivityClick: (() -> Unit)? = null,
    /** Opens DNS from the hero's DNS line; null leaves the line as plain text. */
    onDnsClick: (() -> Unit)? = null,
    onBlockedAppClick: ((String) -> Unit)? = null,
    /** Pauses protection; null leaves Stop alone in the hero. */
    onPause: ((RethinkPauseChoice) -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    /** Changes the time left on a pause; null hides the paused hero's adjustments. */
    onAdjustPause: ((RethinkPauseAdjustment) -> Unit)? = null,
    banner: RethinkHomeBanner? = null,
    card: RethinkHomeCard? = null,
) {
    val visualState = uiState.visualState()
    // The same large title as Stats and Settings, collapsing as the page scrolls. The hero says the
    // state in display type, so the status pill only joins the bar once it has collapsed, beside the
    // small title, instead of floating alone above the large one.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val barCollapsed by remember { derivedStateOf { scrollBehavior.state.collapsedFraction > 0.9f } }
    val reducedMotion = LocalRethinkMotion.current.reducedMotion
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkLargeTopBar(
                title = strings.productName,
                scrollBehavior = scrollBehavior,
                actions = {
                    AnimatedVisibility(
                        visible = barCollapsed,
                        enter = if (reducedMotion) EnterTransition.None else fadeIn() + scaleIn(initialScale = 0.9f),
                        exit = if (reducedMotion) ExitTransition.None else fadeOut() + scaleOut(targetScale = 0.9f),
                    ) {
                        HomeStatus(strings, visualState, Modifier.padding(end = SharedDimensions.spacingMd))
                    }
                },
            )
        },
    ) { insets ->
        RethinkAdaptiveWidth(Modifier.fillMaxSize().padding(insets)) { widthClass ->
            val hero: @Composable (Modifier) -> Unit = { heroModifier ->
                ProtectionHero(uiState, strings, visualState, widthClass, onStartStopClick, onPause, onResume, onAdjustPause, onDnsClick, heroModifier)
            }
            val activity: @Composable ColumnScope.() -> Unit = {
                TodaySection(uiState, strings, onActivityClick)
                StoppedMostSection(uiState.topBlocked, strings, onBlockedAppClick)
            }
            if (widthClass.isExpanded) {
                Row(
                    Modifier.fillMaxSize().padding(SharedDimensions.spacingXl),
                    horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXl),
                ) {
                    hero(Modifier.weight(1f).fillMaxHeight())
                    Column(
                        Modifier.weight(1.05f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXl),
                    ) {
                        banner?.let { HealthBanner(it) }
                        card?.let { HomeCard(it) }
                        activity()
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = RethinkWindowBreakpoints.ContentMax)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = SharedDimensions.screenPaddingHorizontal,
                                end = SharedDimensions.screenPaddingHorizontal,
                                top = SharedDimensions.screenContentTop,
                                bottom = SharedDimensions.spacingLg,
                            ),
                        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXl),
                    ) {
                        // The banner sits above the hero, so "Protected" never reads without it.
                        banner?.let { HealthBanner(it) }
                        hero(Modifier.fillMaxWidth())
                        card?.let { HomeCard(it) }
                        activity()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStatus(strings: RethinkHomeStrings, state: ProtectionVisualState, modifier: Modifier = Modifier) {
    RethinkPill(
        text = when (state) {
            ProtectionVisualState.Protected -> strings.live
            ProtectionVisualState.Recovering -> strings.reconnecting
            ProtectionVisualState.Paused -> strings.paused
            ProtectionVisualState.Off -> strings.offline
        },
        colors = RethinkPillColors(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface),
        size = RethinkPillSize.Large,
        dotColor = when (state) {
            ProtectionVisualState.Protected -> RethinkStatusColors.allowed
            ProtectionVisualState.Recovering -> MaterialTheme.colorScheme.error
            ProtectionVisualState.Paused -> MaterialTheme.colorScheme.secondary
            ProtectionVisualState.Off -> MaterialTheme.colorScheme.outline
        },
        modifier = modifier,
    )
}

private data class HeroColors(val container: Color, val content: Color, val mark: Color, val onMark: Color, val halo: Color)

@Composable
private fun heroColors(state: ProtectionVisualState): HeroColors {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalRethinkMotion.current
    // The brand palette keeps primaryContainer at the seed's own bright tone in dark theme too; the
    // secondary container is the same hue at a dark tone, so the hero dims with the rest of the UI.
    val dark = scheme.surface.luminance() < 0.5f
    val heroContainer = if (dark) scheme.secondaryContainer else scheme.primaryContainer
    val onHeroContainer = if (dark) scheme.onSecondaryContainer else scheme.onPrimaryContainer
    val target = when (state) {
        ProtectionVisualState.Protected -> HeroColors(heroContainer, onHeroContainer, scheme.primary, scheme.onPrimary, onHeroContainer.copy(alpha = 0.12f))
        ProtectionVisualState.Recovering -> HeroColors(scheme.errorContainer, scheme.onErrorContainer, scheme.error, scheme.onError, scheme.onErrorContainer.copy(alpha = 0.12f))
        // Paused is neutral, like off, so Resume (the call to action) carries the colour.
        ProtectionVisualState.Paused -> HeroColors(scheme.surfaceContainerHigh, scheme.onSurface, scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.outlineVariant.copy(alpha = 0.5f))
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

/** The protection state as one coloured hero: mark, state, DNS line, controls. */
@Composable
private fun ProtectionHero(
    uiState: RethinkHomeUiState,
    strings: RethinkHomeStrings,
    state: ProtectionVisualState,
    widthClass: WindowWidthClass,
    onStartStopClick: () -> Unit,
    onPause: ((RethinkPauseChoice) -> Unit)?,
    onResume: (() -> Unit)?,
    onAdjustPause: ((RethinkPauseAdjustment) -> Unit)?,
    onDnsClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = heroColors(state)
    val expanded = widthClass.isExpanded
    val status = uiState.protectionStatus.ifBlank {
        when {
            uiState.isVpnActive && uiState.isPaused -> strings.paused
            uiState.isVpnActive -> strings.protected
            else -> strings.notActive
        }
    }
    val subtitle = when (state) {
        ProtectionVisualState.Protected -> strings.protectedSubtitle
        ProtectionVisualState.Recovering -> strings.failingSubtitle
        ProtectionVisualState.Paused -> uiState.pausedDetail
        ProtectionVisualState.Off -> strings.inactiveSubtitle
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(HeroCorner), color = colors.container, contentColor = colors.content) {
        Column(
            Modifier.padding(if (expanded) SharedDimensions.spacing2xl else SharedDimensions.spacingXl),
        ) {
            if (expanded) {
                // Wide: the mark is the centre of the pane, the words and the controls below it.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ProtectionMark(state, colors, MarkSizeExpanded)
                }
            } else {
                ProtectionMark(state, colors, MarkSizeCompact)
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
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = colors.content.copy(alpha = 0.82f))
            }
            if (state == ProtectionVisualState.Paused && onAdjustPause != null) {
                Spacer(Modifier.height(SharedDimensions.spacingSm))
                PauseAdjustments(uiState, strings, colors, onAdjustPause)
            }
            val dnsLine = uiState.dnsLine.ifBlank { uiState.dnsName }
            if (state != ProtectionVisualState.Off && dnsLine.isNotBlank()) {
                HeroDnsLine(dnsLine, colors.content, strings.openDns, onDnsClick)
            } else {
                Spacer(Modifier.height(SharedDimensions.spacingSm))
            }
            Spacer(Modifier.height(SharedDimensions.spacingMd))
            HeroControls(uiState, strings, status, colors, onStartStopClick, onPause, onResume)
        }
    }
}

/**
 * The resolver in use as one tappable line ("DNS: Keepout · Default ›"): it informs first, and
 * opens DNS for whoever wants to change it.
 */
@Composable
private fun HeroDnsLine(text: String, contentColor: Color, openLabel: String, onClick: (() -> Unit)?) {
    val tint = contentColor.copy(alpha = 0.82f)
    Row(
        Modifier
            .heightIn(min = SharedDimensions.touchTargetMin)
            .clip(CircleShape)
            .then(
                if (onClick != null) Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick) else Modifier,
            )
            .semantics(mergeDescendants = true) {}
            .padding(end = SharedDimensions.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
    ) {
        Icon(MaterialSymbols.Filled.Dns, null, tint = tint, modifier = Modifier.size(SharedDimensions.iconSizeXs))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onClick != null) {
            Icon(MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight, null, tint = tint, modifier = Modifier.size(SharedDimensions.iconSizeSm))
        }
    }
}

/**
 * Off: one large Start. On: a connected pair, Pause ▾ (15 minutes, 1 hour, until I resume) and
 * Stop. Paused: Resume and Stop. The pair uses a quieter tone of the hero itself, so protection
 * is never one careless tap from off; Start and Resume are the calls to action.
 */
@Composable
private fun HeroControls(
    uiState: RethinkHomeUiState,
    strings: RethinkHomeStrings,
    status: String,
    colors: HeroColors,
    onStartStopClick: () -> Unit,
    onPause: ((RethinkPauseChoice) -> Unit)?,
    onResume: (() -> Unit)?,
) {
    if (!uiState.isVpnActive) {
        HeroButton(
            label = strings.start,
            icon = MaterialSymbols.Filled.PlayArrow,
            emphasized = true,
            heroColor = colors.container,
            contentColor = colors.content,
            shape = null,
            onClick = onStartStopClick,
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = status },
        )
        return
    }
    val leading: (@Composable (Modifier) -> Unit)? = when {
        uiState.isPaused && onResume != null -> { m ->
            HeroButton(strings.resume, MaterialSymbols.Filled.PlayArrow, emphasized = true, colors.container, colors.content, LeadingShape, onResume, m)
        }
        !uiState.isPaused && onPause != null -> { m -> PauseMenuButton(strings, colors, onPause, m) }
        else -> null
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        leading?.invoke(Modifier.weight(1f))
        HeroButton(
            label = strings.stop,
            icon = MaterialSymbols.Filled.Stop,
            emphasized = false,
            heroColor = colors.container,
            contentColor = colors.content,
            shape = if (leading != null) TrailingShape else null,
            onClick = onStartStopClick,
            modifier = Modifier.weight(1f).semantics { stateDescription = status },
        )
    }
}

private val LeadingShape = RoundedCornerShape(
    topStart = CornerSize(50),
    topEnd = CornerSize(ConnectedInnerCorner),
    bottomEnd = CornerSize(ConnectedInnerCorner),
    bottomStart = CornerSize(50),
)

private val TrailingShape = RoundedCornerShape(
    topStart = CornerSize(ConnectedInnerCorner),
    topEnd = CornerSize(50),
    bottomEnd = CornerSize(50),
    bottomStart = CornerSize(ConnectedInnerCorner),
)

@Composable
private fun PauseMenuButton(
    strings: RethinkHomeStrings,
    colors: HeroColors,
    onPause: (RethinkPauseChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    // Trigger and menu share one Box so the menu anchors to the button on every target.
    Box(modifier) {
        HeroButton(
            label = strings.pause,
            icon = MaterialSymbols.Filled.Pause,
            emphasized = false,
            heroColor = colors.container,
            contentColor = colors.content,
            shape = LeadingShape,
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = MaterialSymbols.Filled.KeyboardArrowDown,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                RethinkPauseChoice.FifteenMinutes to strings.pauseFifteenMinutes,
                RethinkPauseChoice.OneHour to strings.pauseOneHour,
                RethinkPauseChoice.UntilResumed to strings.pauseUntilResumed,
            ).forEach { (choice, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        Icon(if (choice == RethinkPauseChoice.UntilResumed) MaterialSymbols.Filled.Pause else MaterialSymbols.Filled.Timer, null)
                    },
                    onClick = {
                        open = false
                        onPause(choice)
                    },
                )
            }
        }
    }
}

@Composable
private fun HeroButton(
    label: String,
    icon: ImageVector,
    emphasized: Boolean,
    heroColor: Color,
    contentColor: Color,
    shape: Shape?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val height = ButtonDefaults.MediumContainerHeight
    val colors = ButtonDefaults.buttonColors(
        containerColor = if (emphasized) scheme.primary else quietTone(heroColor),
        contentColor = if (emphasized) scheme.onPrimary else contentColor,
    )
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        Text(label, style = ButtonDefaults.textStyleFor(height), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailingIcon != null) {
            Spacer(Modifier.width(SharedDimensions.spacingXs))
            Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(SharedDimensions.iconSizeSm))
        }
    }
    val sized = modifier.heightIn(min = height)
    // Narrower sides than the medium default, so "Resume" fits half the hero on a 360dp phone.
    val padding = PaddingValues(horizontal = SharedDimensions.spacingMd, vertical = ButtonDefaults.contentPaddingFor(height).calculateTopPadding())
    if (shape == null) {
        Button(onClick = onClick, shapes = ButtonDefaults.shapesFor(height), modifier = sized, contentPadding = padding, colors = colors, content = content)
    } else {
        Button(onClick = onClick, shape = shape, modifier = sized, contentPadding = padding, colors = colors, content = content)
    }
}

/**
 * A quieter tone of the hero itself: lighter on a light hero, deeper on a dark one, so the hero's
 * own content colour always reads on it.
 */
@Composable
private fun quietTone(heroColor: Color): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) lerp(heroColor, Color.Black, 0.35f)
    else lerp(heroColor, Color.White, 0.82f)

/**
 * The paused hero's time controls, right under the time they change. Timed: −15 min (only while
 * more than 15 minutes are left, so it never ends the pause by surprise), +15 min, and No timer
 * (until I resume). Until resumed: a timer of 15 minutes or an hour from now. Small buttons in the
 * hero's quiet tone, so Resume stays the call to action; short labels keep them on one line on a
 * 360dp phone, and each says in full what it does to a screen reader.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PauseAdjustments(
    uiState: RethinkHomeUiState,
    strings: RethinkHomeStrings,
    colors: HeroColors,
    onAdjust: (RethinkPauseAdjustment) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        @Composable
        fun chip(adjustment: RethinkPauseAdjustment, label: String, description: String, icon: ImageVector? = null, enabled: Boolean = true) =
            PauseAdjustButton(label, description, icon, enabled, colors) { onAdjust(adjustment) }
        if (uiState.pauseTimed) {
            chip(
                RethinkPauseAdjustment.RemoveFifteenMinutes, strings.pauseRemoveFifteenMinutes,
                strings.pauseRemoveFifteenMinutesDescription, enabled = uiState.pauseCanShorten,
            )
            chip(RethinkPauseAdjustment.AddFifteenMinutes, strings.pauseAddFifteenMinutes, strings.pauseAddFifteenMinutesDescription)
            chip(RethinkPauseAdjustment.UntilResumed, strings.pauseNoTimer, strings.pauseNoTimerDescription)
        } else {
            chip(
                RethinkPauseAdjustment.FifteenMinutes, strings.pauseFifteenMinutes,
                strings.pauseResumeInFifteenMinutesDescription, MaterialSymbols.Filled.Timer,
            )
            chip(RethinkPauseAdjustment.OneHour, strings.pauseOneHour, strings.pauseResumeInOneHourDescription, MaterialSymbols.Filled.Timer)
        }
    }
}

@Composable
private fun PauseAdjustButton(
    label: String,
    description: String,
    icon: ImageVector?,
    enabled: Boolean,
    colors: HeroColors,
    onClick: () -> Unit,
) {
    val height = ButtonDefaults.ExtraSmallContainerHeight
    val tone = quietTone(colors.container)
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapesFor(height),
        modifier = Modifier.heightIn(min = height).semantics { contentDescription = description },
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        colors = ButtonDefaults.buttonColors(
            containerColor = tone,
            contentColor = colors.content,
            disabledContainerColor = tone.copy(alpha = 0.5f),
            disabledContentColor = colors.content.copy(alpha = 0.38f),
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        }
        Text(label, style = ButtonDefaults.textStyleFor(height), maxLines = 1)
    }
}

/** A problem with what the hero says, and its one fix, in the error container. */
@Composable
private fun HealthBanner(banner: RethinkHomeBanner) {
    val scheme = MaterialTheme.colorScheme
    RethinkContainedCard(
        color = scheme.errorContainer,
        contentColor = scheme.onErrorContainer,
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
            Icon(MaterialSymbols.Filled.Warning, null, tint = scheme.error, modifier = Modifier.size(SharedDimensions.iconSizeMd))
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                Text(banner.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text(banner.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = banner.onAction,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
            ) { Text(banner.actionLabel) }
        }
    }
}

/** The first start's success, or the deferred notification ask: one card, one or two actions. */
@Composable
private fun HomeCard(card: RethinkHomeCard) {
    val scheme = MaterialTheme.colorScheme
    RethinkContainedCard(
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
            Box(
                Modifier.size(SharedDimensions.iconContainerMd).background(scheme.secondary, RoundedCornerShape(SharedDimensions.iconContainerRadius)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(card.icon, null, tint = scheme.onSecondary, modifier = Modifier.size(SharedDimensions.iconSizeSm))
            }
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text(card.body, style = MaterialTheme.typography.bodyMedium)
            }
            if (card.onDismiss != null) {
                IconButton(onClick = card.onDismiss, modifier = Modifier.size(SharedDimensions.touchTargetMin).offset(x = SharedDimensions.spacingSm, y = -SharedDimensions.spacingSm)) {
                    Icon(MaterialSymbols.Filled.Close, card.dismissLabel)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (card.secondaryLabel != null && card.onSecondary != null) {
                TextButton(onClick = card.onSecondary, shapes = ButtonDefaults.shapes()) { Text(card.secondaryLabel) }
            }
            Button(onClick = card.onPrimary, shapes = ButtonDefaults.shapes()) { Text(card.primaryLabel) }
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
    // Paused rests as a still circle, like off, but in the paused hero's colour.
    val active = state == ProtectionVisualState.Protected || state == ProtectionVisualState.Recovering
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
            imageVector = when (state) {
                ProtectionVisualState.Protected -> MaterialSymbols.Filled.GppGood
                ProtectionVisualState.Recovering -> MaterialSymbols.Filled.GppMaybe
                ProtectionVisualState.Paused -> MaterialSymbols.Filled.Pause
                ProtectionVisualState.Off -> MaterialSymbols.Filled.RemoveModerator
            },
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
        Surface(shape = RoundedCornerShape(SharedDimensions.groupCornerRadius), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(SharedDimensions.spacingXl), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg)) {
                Text(strings.connections, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg)) {
                    TodayMetric(formatGrouped(uiState.allowedToday), strings.allowed, RethinkStatusColors.allowed, MaterialSymbols.Filled.CheckCircle, Modifier.weight(1f))
                    TodayMetric(formatGrouped(uiState.blockedToday), strings.blocked, RethinkStatusColors.blocked, MaterialSymbols.Filled.DoNotDisturbOn, Modifier.weight(1f))
                }
                SplitBar(uiState.allowedToday, uiState.blockedToday)
                // DNS lookups are a separate count from the connections above: its own labelled
                // line, so "Blocked 122" over "259 stopped" doesn't read as a contradiction.
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                ) {
                    Icon(MaterialSymbols.Filled.Dns, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SharedDimensions.iconSizeSm))
                    Text(
                        strings.dnsQueries,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    RethinkPill(
                        text = strings.blockedCount(uiState.dnsBlockedToday),
                        colors = if (uiState.dnsBlockedToday > 0) RethinkPillDefaults.blocked() else RethinkPillDefaults.neutral(),
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
        RethinkListGroup {
            if (apps.isEmpty()) {
                RethinkListItem(headline = strings.nothingStopped, leadingIcon = MaterialSymbols.Filled.CheckCircle, position = CardPosition.Single)
            }
            apps.forEachIndexed { index, app ->
                RethinkListItem(
                    headline = app.label,
                    leadingContent = {
                        Box(Modifier.size(SharedDimensions.iconContainerMd).clip(RoundedCornerShape(SharedDimensions.iconContainerRadius)), contentAlignment = Alignment.Center) {
                            app.icon?.invoke() ?: Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
                        }
                    },
                    trailing = {
                        RethinkPill(
                            text = strings.blockedCount(app.count),
                            colors = RethinkPillDefaults.blocked(),
                            leadingIcon = MaterialSymbols.Filled.DoNotDisturbOn,
                        )
                    },
                    position = cardPositionFor(index, apps.lastIndex),
                    onClick = onClick?.let { { it(app.id) } },
                )
            }
        }
    }
}

private fun RethinkHomeUiState.visualState(): ProtectionVisualState = when {
    isVpnActive && isPaused -> ProtectionVisualState.Paused
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

/** "14:32" under an hour, "1:02:05" above; what the paused hero shows beside "left". */
fun formatPauseRemaining(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$minutes:$ss"
}
