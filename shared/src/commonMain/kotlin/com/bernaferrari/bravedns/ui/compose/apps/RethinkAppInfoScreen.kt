/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.apps

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.common.RethinkAdaptiveWidth
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import com.bernaferrari.bravedns.ui.compose.common.isExpanded
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.CompactEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormActionRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormSection
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkRadioListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSheetHeader
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSwitchListItem
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListPairShape
import com.bernaferrari.bravedns.ui.compose.theme.rethinkListItemColors
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.icons.keepout.KeepoutIcons

/** Two-line action row height (Material 72dp). */
private val AppInfoActionMinHeight = 72.dp

/**
 * How Keepout treats an app beyond its Wi‑Fi / mobile data flags. Exactly one applies; [Normal]
 * follows every rule. Hosts map their stored firewall status onto these.
 */
enum class RethinkAppMode { Normal, TrustedOnly, SkipAllFiltering, SkipUniversalRules, Excluded }

/** The tone of App detail's one status line. */
enum class RethinkAppStatusTone { Allowed, Partial, Blocked, Temporary }

/** One destination in Recent activity: the newest connection to it, blocked rows first. */
@Immutable
data class RethinkAppInfoActivityItem(
    val id: String,
    /** The domain, else the IP. */
    val title: String,
    /** "Blocked · 09:41 · 3 times", formatted by the host. */
    val subtitle: String?,
    val blocked: Boolean,
)

@Immutable
data class RethinkAppInfoState(
    /** True until the app's first lookup completes: neither the controls nor "missing" show yet. */
    val loading: Boolean,
    /** False only after loading finished and the app wasn't found. */
    val appAvailable: Boolean,
    val title: String,
    val subtitle: String? = null,
    /** The single status sentence: "Can't use the internet", "Allowed for 12 more minutes"… */
    val status: String,
    val statusTone: RethinkAppStatusTone,
    /** A quiet line under the status (proxy routing), when there is one. */
    val statusDetail: String? = null,
    val wifiBlocked: Boolean,
    val mobileBlocked: Boolean,
    val mode: RethinkAppMode,
    /** Minutes of temporary access left, or null when the app isn't temporarily allowed. */
    val tempAllowMinutesLeft: Int? = null,
    val proxyExcluded: Boolean,
    /** "Bypass all proxies" only matters when a proxy is set up (or the app already bypasses). */
    val showProxyExclude: Boolean = true,
    /** Null while loading. Blocked destinations first. */
    val activity: List<RethinkAppInfoActivityItem>? = null,
    val domainRuleCount: Int? = null,
    val ipRuleCount: Int? = null,
    /** False while a write is in flight, so rapid taps can't interleave. */
    val enabled: Boolean = true,
)

@Immutable
data class RethinkAppInfoStrings(
    val unavailable: String,
    val loading: String,
    val internetAccess: String,
    val wifi: String,
    val mobile: String,
    val allowed: String,
    val blocked: String,
    /** "Metered Wi‑Fi counts as mobile data." The firewall decides by metered, not by radio. */
    val meteredFootnote: String,
    val temporaryAllow: String,
    /** "Stop temporary access · 12 min left" */
    val stopTemporaryAccess: (minutesLeft: Int) -> String,
    val recentActivity: String,
    val seeAllActivity: String,
    val noRecentActivity: String,
    val rules: String,
    val ipRules: String,
    val domainRules: String,
    val specialHandling: String,
    /** The sheet's title: "How Keepout treats this app". */
    val modeTitle: String,
    val normal: String,
    val normalDescription: String,
    val trustedOnly: String,
    val trustedOnlyDescription: String,
    val skipAllFiltering: String,
    val skipAllFilteringDescription: String,
    val skipUniversalRules: String,
    val skipUniversalRulesDescription: String,
    val exclude: String,
    val excludeDescription: String,
    /** Shown in the sheet while Exclude or Skip all filtering is the pending choice. */
    val excludeWarning: String,
    val skipAllFilteringWarning: String,
    val apply: String,
    val cancel: String,
    val more: String,
    val proxyExclude: String,
    val proxyExcludeDescription: String,
    val systemAppInfo: String,
    val wifiState: (blocked: Boolean) -> String = { if (it) "$wifi: $blocked" else "$wifi: $allowed" },
    val mobileState: (blocked: Boolean) -> String = { if (it) "$mobile: $blocked" else "$mobile: $allowed" },
)

/** Everything App detail can do. Hosts own the writes, undo and navigation. */
@Immutable
data class RethinkAppInfoActions(
    val onBackClick: () -> Unit,
    val onWifiClick: () -> Unit,
    val onMobileClick: () -> Unit,
    /** Choose a handling mode; called only after the user confirms it in the sheet. */
    val onModeChange: (RethinkAppMode) -> Unit,
    val onTempAllowChange: (Boolean) -> Unit,
    val onProxyExcludedChange: (Boolean) -> Unit,
    val onSystemAppInfo: () -> Unit,
    val onIpRules: () -> Unit,
    val onDomainRules: () -> Unit,
    val onActivityClick: ((RethinkAppInfoActivityItem) -> Unit)? = null,
    val onSeeAllActivity: (() -> Unit)? = null,
)

/**
 * Shared App detail, built around "can it use the internet?": one status line, the Wi‑Fi and
 * mobile data tiles (with temporary access when anything is blocked), recent activity, the app's
 * rules, its special handling and the rest. Android keeps lookup, icons, writes and log queries.
 */
@Composable
fun RethinkAppInfoScreen(
    state: RethinkAppInfoState,
    strings: RethinkAppInfoStrings,
    actions: RethinkAppInfoActions,
    modifier: Modifier = Modifier,
    titleLeading: (@Composable () -> Unit)? = null,
    appNotes: (@Composable () -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showModes by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkLargeTopBar(
                title = state.title,
                subtitle = state.subtitle,
                onBackClick = actions.onBackClick,
                scrollBehavior = scrollBehavior,
                titleLeading = titleLeading,
            )
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
    ) { padding ->
        val access: LazyListScope.() -> Unit = {
            item(key = "status") { RethinkAppStatusLine(state) }
            item(key = "access") { RethinkAppAccessSection(state, strings, actions) }
        }
        val activity: LazyListScope.() -> Unit = {
            item(key = "activity") { RethinkAppActivitySection(state, strings, actions) }
        }
        val rules: LazyListScope.() -> Unit = {
            item(key = "rules") {
                RethinkFormSection(strings.rules) {
                    RethinkListGroup {
                        RethinkAppNavigationRow(strings.domainRules, state.domainRuleCount, MaterialSymbols.Filled.Link, CardPosition.First, actions.onDomainRules)
                        RethinkAppNavigationRow(strings.ipRules, state.ipRuleCount, MaterialSymbols.Filled.Pin, CardPosition.Last, actions.onIpRules)
                    }
                }
            }
        }
        val handling: LazyListScope.() -> Unit = {
            item(key = "handling") { RethinkAppSpecialHandling(state, strings, onOpen = { showModes = true }) }
        }
        val more: LazyListScope.() -> Unit = {
            item(key = "more") {
                RethinkFormSection(strings.more) {
                    RethinkListGroup {
                        if (state.showProxyExclude) {
                            RethinkSwitchListItem(
                                headline = strings.proxyExclude,
                                checked = state.proxyExcluded,
                                onCheckedChange = actions.onProxyExcludedChange,
                                supporting = strings.proxyExcludeDescription,
                                leadingIcon = MaterialSymbols.Filled.ConversionPath,
                                position = CardPosition.First,
                            )
                        }
                        RethinkListItem(
                            headline = strings.systemAppInfo,
                            leadingIcon = MaterialSymbols.Filled.PhoneAndroid,
                            position = if (state.showProxyExclude) CardPosition.Last else CardPosition.Single,
                            showTrailingChevron = true,
                            onClick = actions.onSystemAppInfo,
                        )
                    }
                }
            }
            if (appNotes != null) item(key = "notes") { appNotes() }
            item(key = "end") { Spacer(Modifier.height(SharedDimensions.spacingSm)) }
        }
        RethinkAdaptiveWidth(Modifier.fillMaxSize().padding(padding)) { widthClass ->
            when {
                // The lookup is a quick local read: show nothing rather than flash a spinner or "missing".
                state.loading -> Spacer(Modifier.fillMaxSize())
                !state.appAvailable -> LazyColumn(Modifier.fillMaxSize(), contentPadding = AppInfoPadding) {
                    // Only after the lookup finished: the top bar already offers back.
                    item { RethinkEmptyState(icon = MaterialSymbols.Filled.Apps, title = strings.unavailable) }
                }
                widthClass.isExpanded -> Row(
                    // Wide windows: access and handling in one column, traffic and rules in the other.
                    Modifier.fillMaxSize().padding(horizontal = SharedDimensions.spacingSm),
                    horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                ) {
                    LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = AppInfoPadding, verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
                        access()
                        handling()
                        more()
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = AppInfoPadding, verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
                        activity()
                        rules()
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = AppInfoPadding,
                    verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
                ) {
                    access()
                    activity()
                    rules()
                    handling()
                    more()
                }
            }
        }
    }
    if (showModes && state.appAvailable) {
        RethinkAppModeSheet(
            current = state.mode,
            strings = strings,
            onDismiss = { showModes = false },
            onApply = actions.onModeChange,
        )
    }
}

private val AppInfoPadding = PaddingValues(
    start = SharedDimensions.screenPaddingHorizontal,
    end = SharedDimensions.screenPaddingHorizontal,
    top = SharedDimensions.spacingSm,
    bottom = SharedDimensions.spacing3xl,
)

@Composable
private fun RethinkAppStatusTone.color(): Color = when (this) {
    RethinkAppStatusTone.Allowed, RethinkAppStatusTone.Temporary -> RethinkStatusColors.allowed
    RethinkAppStatusTone.Partial -> RethinkStatusColors.mixed
    RethinkAppStatusTone.Blocked -> RethinkStatusColors.blocked
}

private fun RethinkAppInfoState.statusIcon(): ImageVector = when (statusTone) {
    RethinkAppStatusTone.Allowed -> MaterialSymbols.Filled.CheckCircle
    RethinkAppStatusTone.Temporary -> MaterialSymbols.Filled.Timer
    RethinkAppStatusTone.Blocked -> MaterialSymbols.Filled.DoNotDisturbOn
    RethinkAppStatusTone.Partial -> when {
        // Show what is actually limited, most specific first.
        mode == RethinkAppMode.Excluded -> MaterialSymbols.Filled.RemoveModerator
        mode == RethinkAppMode.SkipAllFiltering -> KeepoutIcons.FirewallOff
        mode == RethinkAppMode.SkipUniversalRules -> MaterialSymbols.Filled.PublicOff
        wifiBlocked -> MaterialSymbols.Filled.WifiOff
        mobileBlocked -> MaterialSymbols.Filled.MobiledataOff
        else -> MaterialSymbols.Filled.PublicOff
    }
}

/** The one answer to "can it use the internet?", replacing the old heading + chip. */
@Composable
private fun RethinkAppStatusLine(state: RethinkAppInfoState) {
    val color by animateColorAsState(state.statusTone.color(), label = "app_status_color")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SharedDimensions.spacingXs, vertical = SharedDimensions.spacingXs)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
            Icon(state.statusIcon(), null, tint = color, modifier = Modifier.size(SharedDimensions.iconSizeMd))
            Text(state.status, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
        }
        state.statusDetail?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SharedDimensions.iconSizeMd + SharedDimensions.spacingSm),
            )
        }
    }
}

/** Wi‑Fi and mobile data, temporary access when something is blocked, and the metered footnote. */
@Composable
private fun RethinkAppAccessSection(state: RethinkAppInfoState, strings: RethinkAppInfoStrings, actions: RethinkAppInfoActions) {
    RethinkFormSection(strings.internetAccess) {
        RethinkAppNetworkPair(state, strings, actions.onWifiClick, actions.onMobileClick)
        val minutesLeft = state.tempAllowMinutesLeft
        val somethingBlocked = state.wifiBlocked || state.mobileBlocked || state.mode == RethinkAppMode.TrustedOnly
        AnimatedVisibility(
            visible = minutesLeft != null || somethingBlocked,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            FilledTonalButton(
                onClick = { actions.onTempAllowChange(minutesLeft == null) },
                enabled = state.enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = SharedDimensions.buttonHeight),
            ) {
                Icon(
                    if (minutesLeft == null) MaterialSymbols.Filled.Timer else MaterialSymbols.Filled.TimerOff,
                    null,
                    Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(if (minutesLeft == null) strings.temporaryAllow else strings.stopTemporaryAccess(minutesLeft))
            }
        }
        Text(
            strings.meteredFootnote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SharedDimensions.spacingXs),
        )
    }
}

@Composable
private fun RethinkAppNetworkPair(
    state: RethinkAppInfoState,
    strings: RethinkAppInfoStrings,
    onWifiClick: () -> Unit,
    onMobileClick: () -> Unit,
) {
    // Compact horizontal network cards with the grouped-list gap. IntrinsicSize.Min keeps both
    // tiles the same height when one label wraps to two lines.
    Row(
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
    ) {
        RethinkAppNetworkTile(
            label = strings.wifi,
            blocked = state.wifiBlocked,
            allowedLabel = strings.allowed,
            blockedLabel = strings.blocked,
            stateDescription = strings.wifiState(state.wifiBlocked),
            allowedIcon = MaterialSymbols.Filled.Wifi,
            blockedIcon = MaterialSymbols.Filled.WifiOff,
            shape = rethinkGroupedListPairShape(isLeadingTile = true, position = CardPosition.Single),
            enabled = state.enabled,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = onWifiClick,
        )
        RethinkAppNetworkTile(
            label = strings.mobile,
            blocked = state.mobileBlocked,
            allowedLabel = strings.allowed,
            blockedLabel = strings.blocked,
            stateDescription = strings.mobileState(state.mobileBlocked),
            allowedIcon = MaterialSymbols.Filled.MobiledataArrows,
            blockedIcon = MaterialSymbols.Filled.MobiledataOff,
            shape = rethinkGroupedListPairShape(isLeadingTile = false, position = CardPosition.Single),
            enabled = state.enabled,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = onMobileClick,
        )
    }
}

/**
 * Compact allow/block network card: at least list-item-large tall, the icon on the segment itself, and
 * Allowed/Blocked status text. The title may take two lines so "Mobile data" never truncates.
 */
@Composable
private fun RethinkAppNetworkTile(
    label: String,
    blocked: Boolean,
    allowedLabel: String,
    blockedLabel: String,
    stateDescription: String,
    allowedIcon: ImageVector,
    blockedIcon: ImageVector,
    shape: Shape,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val motion = LocalRethinkMotion.current
    val haptics = LocalHapticFeedback.current
    val colorSpec: AnimationSpec<Color> = tween(motion.durationMedium, easing = FastOutSlowInEasing)
    val baseRounded = shape as? RoundedCornerShape ?: RoundedCornerShape(SharedDimensions.cornerRadiusXl)
    val animatedShape = RoundedCornerShape(
        topStart = animateCorner(baseRounded.topStart, SharedDimensions.cornerRadius2xl, blocked, density),
        topEnd = animateCorner(baseRounded.topEnd, SharedDimensions.cornerRadius2xl, blocked, density),
        bottomEnd = animateCorner(baseRounded.bottomEnd, SharedDimensions.cornerRadius2xl, blocked, density),
        bottomStart = animateCorner(baseRounded.bottomStart, SharedDimensions.cornerRadius2xl, blocked, density),
    )
    val containerColor by animateColorAsState(
        targetValue = if (blocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = colorSpec,
        label = "app_network_tile_bg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (blocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = colorSpec,
        label = "app_network_tile_title",
    )
    val secondaryColor by animateColorAsState(
        targetValue = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = colorSpec,
        label = "app_network_tile_status",
    )
    // Same rule as the list's network segments: the icon sits directly on its segment, no
    // inner circle; allowed is tonal, blocked takes the error container's on-colour.
    val iconBlockedTint = if (blocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val iconAllowedTint = if (blocked) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.44f) else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = {
            haptics.performHapticFeedback(if (blocked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onClick()
        },
        enabled = enabled,
        // One stop for TalkBack: "Wi‑Fi: blocked", a switch whose double-tap allows it.
        modifier = modifier.heightIn(min = AppInfoActionMinHeight).semantics(mergeDescendants = true) {
            role = Role.Switch
            this.stateDescription = stateDescription
        },
        shape = animatedShape,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                // 16dp in from the start, like the list rows' leading icons below it.
                .padding(start = SharedDimensions.spacingLg, end = SharedDimensions.spacingMd, top = SharedDimensions.spacingMd, bottom = SharedDimensions.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            DiagonalWipeIcon(
                blocked = blocked,
                allowedIcon = allowedIcon,
                blockedIcon = blockedIcon,
                allowedTint = iconAllowedTint,
                blockedTint = iconBlockedTint,
                contentDescription = null,
                modifier = Modifier.size(SharedDimensions.iconSizeMd),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimatedContent(
                    targetState = blocked,
                    transitionSpec = {
                        if (motion.reducedMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (slideInVertically { height -> if (targetState) height else -height } + fadeIn()) togetherWith
                                (slideOutVertically { height -> if (targetState) -height else height } + fadeOut())
                        }
                    },
                    label = "app_network_tile_status_text",
                ) { isBlocked ->
                    Text(
                        text = if (isBlocked) blockedLabel else allowedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = secondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun animateCorner(
    base: CornerSize,
    targetDp: Dp,
    blocked: Boolean,
    density: Density,
): CornerSize {
    val basePx = base.toPx(Size(100f, 100f), density)
    val baseDp = with(density) { basePx.toDp() }
    val currentDp by animateDpAsState(
        targetValue = if (blocked) targetDp else baseDp,
        animationSpec = tween(LocalRethinkMotion.current.durationMedium, easing = FastOutSlowInEasing),
        label = "app_network_tile_corner",
    )
    return CornerSize(currentDp)
}

/** Blocked destinations first; each opens the Connection sheet, then "See all activity". */
@Composable
private fun RethinkAppActivitySection(state: RethinkAppInfoState, strings: RethinkAppInfoStrings, actions: RethinkAppInfoActions) {
    val items = state.activity
    RethinkFormSection(strings.recentActivity) {
        when {
            items == null -> RethinkLoadingState(label = strings.loading)
            items.isEmpty() -> Surface(
                shape = RoundedCornerShape(SharedDimensions.groupCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CompactEmptyState(message = strings.noRecentActivity, icon = MaterialSymbols.Filled.Info)
            }
            else -> RethinkListGroup {
                val seeAll = actions.onSeeAllActivity
                val last = if (seeAll != null) items.size else items.lastIndex
                items.forEachIndexed { index, item ->
                    RethinkListItem(
                        headline = item.title,
                        supporting = item.subtitle,
                        singleLineHeadline = true,
                        leadingIcon = if (item.blocked) MaterialSymbols.Filled.DoNotDisturbOn else MaterialSymbols.Filled.CheckCircle,
                        colors = rethinkListItemColors(
                            leadingIconTint = if (item.blocked) RethinkStatusColors.blocked else RethinkStatusColors.allowed,
                        ),
                        position = cardPositionFor(index, last),
                        showTrailingChevron = actions.onActivityClick != null,
                        onClick = actions.onActivityClick?.let { open -> { open(item) } },
                    )
                }
                if (seeAll != null) {
                    RethinkListItem(
                        headline = strings.seeAllActivity,
                        leadingIcon = MaterialSymbols.Filled.History,
                        position = cardPositionFor(last, last),
                        showTrailingChevron = true,
                        onClick = seeAll,
                    )
                }
            }
        }
        if (items != null && items.isEmpty()) {
            actions.onSeeAllActivity?.let { seeAll ->
                RethinkListGroup {
                    RethinkListItem(
                        headline = strings.seeAllActivity,
                        leadingIcon = MaterialSymbols.Filled.History,
                        position = CardPosition.Single,
                        showTrailingChevron = true,
                        onClick = seeAll,
                    )
                }
            }
        }
    }
}

@Composable
private fun RethinkAppNavigationRow(title: String, count: Int?, icon: ImageVector, position: CardPosition, onClick: () -> Unit) {
    RethinkListItem(
        headline = if (count != null) "$title · $count" else title,
        leadingIcon = icon,
        position = position,
        showTrailingChevron = true,
        onClick = onClick,
    )
}

private fun RethinkAppMode.icon(): ImageVector = when (this) {
    RethinkAppMode.Normal -> MaterialSymbols.Filled.GppGood
    RethinkAppMode.TrustedOnly -> MaterialSymbols.Filled.Verified
    RethinkAppMode.SkipAllFiltering -> KeepoutIcons.FirewallOff
    RethinkAppMode.SkipUniversalRules -> MaterialSymbols.Filled.PublicOff
    RethinkAppMode.Excluded -> MaterialSymbols.Filled.RemoveModerator
}

private fun RethinkAppInfoStrings.label(mode: RethinkAppMode): String = when (mode) {
    RethinkAppMode.Normal -> normal
    RethinkAppMode.TrustedOnly -> trustedOnly
    RethinkAppMode.SkipAllFiltering -> skipAllFiltering
    RethinkAppMode.SkipUniversalRules -> skipUniversalRules
    RethinkAppMode.Excluded -> exclude
}

private fun RethinkAppInfoStrings.description(mode: RethinkAppMode): String = when (mode) {
    RethinkAppMode.Normal -> normalDescription
    RethinkAppMode.TrustedOnly -> trustedOnlyDescription
    RethinkAppMode.SkipAllFiltering -> skipAllFilteringDescription
    RethinkAppMode.SkipUniversalRules -> skipUniversalRulesDescription
    RethinkAppMode.Excluded -> excludeDescription
}

/**
 * The current mode, always visible as its own row (highlighted when it isn't Normal). Tapping it
 * opens the five modes as the familiar radio group; nothing changes until Apply.
 */
@Composable
private fun RethinkAppSpecialHandling(state: RethinkAppInfoState, strings: RethinkAppInfoStrings, onOpen: () -> Unit) {
    RethinkFormSection(strings.specialHandling) {
        RethinkListGroup {
            RethinkListItem(
                headline = strings.label(state.mode),
                supporting = strings.description(state.mode),
                leadingIcon = state.mode.icon(),
                position = CardPosition.Single,
                highlighted = state.mode != RethinkAppMode.Normal,
                colors = rethinkListItemColors(highlightContainerColor = MaterialTheme.colorScheme.secondaryContainer),
                showTrailingChevron = true,
                enabled = state.enabled,
                onClick = onOpen,
            )
        }
    }
}

/**
 * The modes are mutually exclusive, so they read as one radio group with "Normal" first. The
 * choice is pending until Apply; Exclude and Skip all filtering say what they give up.
 */
@Composable
private fun RethinkAppModeSheet(
    current: RethinkAppMode,
    strings: RethinkAppInfoStrings,
    onDismiss: () -> Unit,
    onApply: (RethinkAppMode) -> Unit,
) {
    var pending by rememberSaveable(current) { mutableStateOf(current) }
    RethinkModalBottomSheet(
        onDismissRequest = onDismiss,
        contentPadding = PaddingValues(SharedDimensions.spacingLg),
        verticalSpacing = SharedDimensions.spacingMd,
        includeBottomSpacer = false,
        expandOnShow = true,
    ) { dismiss ->
        Column(
            Modifier.fillMaxWidth().heightIn(max = SharedDimensions.sheetContentMaxHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            RethinkSheetHeader(title = strings.modeTitle)
            RethinkListGroup(Modifier.selectableGroup()) {
                RethinkAppMode.entries.forEachIndexed { index, mode ->
                    RethinkRadioListItem(
                        headline = strings.label(mode),
                        selected = pending == mode,
                        onSelect = { pending = mode },
                        supporting = strings.description(mode),
                        leadingIcon = mode.icon(),
                        position = cardPositionFor(index, RethinkAppMode.entries.lastIndex),
                    )
                }
            }
            val warning = when (pending) {
                RethinkAppMode.Excluded -> strings.excludeWarning
                RethinkAppMode.SkipAllFiltering -> strings.skipAllFilteringWarning
                else -> null
            }
            AnimatedVisibility(visible = warning != null && pending != current) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingXs),
                    horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                ) {
                    Icon(MaterialSymbols.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(SharedDimensions.iconSizeSm))
                    Text(warning.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        RethinkFormActionRow(
            confirmLabel = strings.apply,
            confirmEnabled = pending != current,
            onConfirm = {
                val chosen = pending
                dismiss()
                onApply(chosen)
            },
            dismissLabel = strings.cancel,
            onDismiss = dismiss,
        )
    }
}
