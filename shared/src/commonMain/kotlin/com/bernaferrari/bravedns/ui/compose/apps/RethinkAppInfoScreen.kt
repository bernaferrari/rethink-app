/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.apps

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillSize
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWell
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import com.bernaferrari.bravedns.ui.compose.common.isExpanded
import com.bernaferrari.bravedns.ui.compose.common.RethinkAdaptiveWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.CompactEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormSection
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSwitchListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListPairShape
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/** Two-line action row height (Material 72dp). */
private val AppInfoActionMinHeight = 72.dp

data class RethinkAppInfoLogItem(val id: String, val title: String, val subtitle: String? = null)

data class RethinkAppInfoLogSection(
    val title: String,
    val count: Int,
    val loading: Boolean,
    val empty: Boolean,
    val entries: List<RethinkAppInfoLogItem>,
)

data class RethinkAppInfoState(
    val appAvailable: Boolean,
    val title: String,
    val subtitle: String? = null,
    val status: String,
    val temporaryAllowed: Boolean,
    val proxyDetails: String,
    val wifiBlocked: Boolean,
    val mobileBlocked: Boolean,
    val isolated: Boolean,
    val bypassDnsFirewall: Boolean,
    val bypassUniversalFirewall: Boolean,
    val excluded: Boolean,
    val proxyExcluded: Boolean,
    val tempAllowed: Boolean,
    val activeConnections: RethinkAppInfoLogSection,
    val domainLogs: RethinkAppInfoLogSection,
    val ipLogs: RethinkAppInfoLogSection,
)

data class RethinkAppInfoStrings(
    val unavailable: String,
    val status: String,
    val temporaryAllow: String,
    val firewall: String,
    val wifi: String,
    val mobile: String,
    val allowed: String,
    val blocked: String,
    val isolate: String,
    val isolateDescription: String,
    val bypassDns: String,
    val bypassDnsDescription: String,
    val bypassUniversal: String,
    val bypassUniversalDescription: String,
    val exclude: String,
    val excludeDescription: String,
    val advanced: String,
    val proxyExclude: String,
    val proxyExcludeDescription: String,
    val temporaryAllowDescription: String,
    val rules: String,
    val systemAppInfo: String,
    val ipRules: String,
    val domainRules: String,
    val loading: String,
    val empty: String,
)

/** Shared app-detail screen. Android retains app lookup, icon loading, rule writes and log paging. */
@Composable
fun RethinkAppInfoScreen(
    state: RethinkAppInfoState,
    strings: RethinkAppInfoStrings,
    onBackClick: () -> Unit,
    onWifiClick: () -> Unit,
    onMobileClick: () -> Unit,
    onIsolateClick: () -> Unit,
    onBypassDnsClick: () -> Unit,
    onBypassUniversalClick: () -> Unit,
    onExcludeClick: () -> Unit,
    onProxyExcludedChange: (Boolean) -> Unit,
    onTempAllowChange: (Boolean) -> Unit,
    onSystemAppInfo: () -> Unit,
    onIpRules: () -> Unit,
    onDomainRules: () -> Unit,
    onActiveConnections: (() -> Unit)?,
    onDomains: (() -> Unit)?,
    onIps: (() -> Unit)?,
    onActiveEntry: ((RethinkAppInfoLogItem) -> Unit)?,
    onDomainEntry: ((RethinkAppInfoLogItem) -> Unit)?,
    onIpEntry: ((RethinkAppInfoLogItem) -> Unit)?,
    titleLeading: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkLargeTopBar(
                title = state.title,
                subtitle = state.subtitle,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
                titleLeading = titleLeading,
            )
        },
    ) { padding ->
        val controls: LazyListScope.() -> Unit = {
            item {
                RethinkFormSection(strings.firewall) {
                    RethinkAppStatusChips(state, strings)
                    RethinkAppNetworkPair(state, strings, onWifiClick, onMobileClick)
                    RethinkListGroup {
                        RethinkAppExclusiveRow(strings.isolate, strings.isolateDescription, state.isolated, MaterialSymbols.Filled.Security, CardPosition.First, onIsolateClick)
                        RethinkAppExclusiveRow(strings.bypassDns, strings.bypassDnsDescription, state.bypassDnsFirewall, MaterialSymbols.Filled.Dns, CardPosition.Middle, onBypassDnsClick)
                        RethinkAppExclusiveRow(strings.bypassUniversal, strings.bypassUniversalDescription, state.bypassUniversalFirewall, MaterialSymbols.Filled.Public, CardPosition.Middle, onBypassUniversalClick)
                        RethinkAppExclusiveRow(strings.exclude, strings.excludeDescription, state.excluded, MaterialSymbols.Filled.Apps, CardPosition.Last, onExcludeClick)
                    }
                }
            }
        }
        val rules: LazyListScope.() -> Unit = {
            item {
                RethinkFormSection(strings.rules) {
                    RethinkListGroup {
                        RethinkAppNavigationRow(strings.ipRules, MaterialSymbols.Filled.Public, CardPosition.First, onIpRules)
                        RethinkAppNavigationRow(strings.domainRules, MaterialSymbols.Filled.Dns, CardPosition.Last, onDomainRules)
                    }
                }
            }
        }
        val advanced: LazyListScope.() -> Unit = {
            item {
                // System app info is an app-level escape hatch, not a rule: it closes the
                // Advanced group instead of sitting among IP/domain rules.
                RethinkFormSection(strings.advanced) {
                    RethinkListGroup {
                        RethinkAppToggleRow(strings.proxyExclude, strings.proxyExcludeDescription, state.proxyExcluded, MaterialSymbols.Filled.VpnKey, CardPosition.First, onProxyExcludedChange)
                        RethinkAppToggleRow(strings.temporaryAllow, strings.temporaryAllowDescription, state.tempAllowed, MaterialSymbols.Filled.Timer, CardPosition.Middle, onTempAllowChange)
                        RethinkAppNavigationRow(strings.systemAppInfo, MaterialSymbols.Filled.PhoneAndroid, CardPosition.Last, onSystemAppInfo)
                    }
                }
            }
        }
        val activity: LazyListScope.() -> Unit = {
            val logSections = listOf(
                Triple(state.activeConnections, onActiveConnections, onActiveEntry),
                Triple(state.domainLogs, onDomains, onDomainEntry),
                Triple(state.ipLogs, onIps, onIpEntry),
            )
            if (logSections.all { (section) -> section.empty && !section.loading }) {
                // One compact note instead of three stacked full empty states.
                item { RethinkAppNoActivity(strings.empty) }
            } else {
                logSections.forEach { (section, onOpen, onEntry) ->
                    item { RethinkAppLogCard(section, strings, onOpen, onEntry) }
                }
            }
            item { Spacer(Modifier.height(SharedDimensions.spacingSm)) }
        }
        RethinkAdaptiveWidth(Modifier.fillMaxSize().padding(padding)) { widthClass ->
            if (widthClass.isExpanded && state.appAvailable) {
                // Wide windows: the app's controls in one column, its rules and traffic in the other.
                Row(
                    Modifier.fillMaxSize().padding(horizontal = SharedDimensions.spacingSm),
                    horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                ) {
                    LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = AppInfoPadding, verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
                        controls()
                        advanced()
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = AppInfoPadding, verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
                        rules()
                        activity()
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = AppInfoPadding,
                    verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
                ) {
                    if (!state.appAvailable) {
                        item {
                            // The top bar already provides back navigation; no duplicate in-content row.
                            RethinkEmptyState(
                                icon = MaterialSymbols.Filled.Apps,
                                title = strings.unavailable,
                                message = "",
                            )
                        }
                    } else {
                        controls()
                        rules()
                        advanced()
                        activity()
                    }
                }
            }
        }
    }
}

private val AppInfoPadding = PaddingValues(
    start = SharedDimensions.screenPaddingHorizontal,
    end = SharedDimensions.screenPaddingHorizontal,
    top = SharedDimensions.spacingSm,
    bottom = SharedDimensions.spacing3xl,
)

/** The app's effective firewall verdict, derived from the same flags the controls below edit. */
private enum class RethinkAppStatusTone { Allowed, Partial, Blocked }

private fun RethinkAppInfoState.statusTone(): RethinkAppStatusTone = when {
    isolated || (wifiBlocked && mobileBlocked) -> RethinkAppStatusTone.Blocked
    excluded || bypassDnsFirewall || bypassUniversalFirewall || wifiBlocked || mobileBlocked -> RethinkAppStatusTone.Partial
    else -> RethinkAppStatusTone.Allowed
}

/**
 * Status summary as a wrapping row of tonal pills (verdict, temporary allow, proxy routing)
 * instead of two cramped lines of caption text. Pills wrap rather than truncate.
 */
@Composable
private fun RethinkAppStatusChips(state: RethinkAppInfoState, strings: RethinkAppInfoStrings) {
    val tone = state.statusTone()
    val (toneColor, toneIcon) = when (tone) {
        RethinkAppStatusTone.Allowed -> RethinkStatusColors.allowed to MaterialSymbols.Filled.CheckCircle
        RethinkAppStatusTone.Partial -> RethinkStatusColors.mixed to MaterialSymbols.Filled.Tune
        RethinkAppStatusTone.Blocked -> RethinkStatusColors.blocked to MaterialSymbols.Filled.Block
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        RethinkPill(state.status, colors = RethinkPillDefaults.status(toneColor), size = RethinkPillSize.Large, leadingIcon = toneIcon, maxLines = Int.MAX_VALUE)
        if (state.temporaryAllowed) {
            RethinkPill(strings.temporaryAllow, colors = RethinkPillDefaults.status(RethinkStatusColors.allowed), size = RethinkPillSize.Large, leadingIcon = MaterialSymbols.Filled.Timer, maxLines = Int.MAX_VALUE)
        }
        if (state.proxyDetails.isNotBlank()) {
            RethinkPill(state.proxyDetails, colors = RethinkPillDefaults.status(RethinkStatusColors.idle), size = RethinkPillSize.Large, leadingIcon = MaterialSymbols.Filled.VpnKey, maxLines = Int.MAX_VALUE)
        }
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
            allowedIcon = MaterialSymbols.Filled.Wifi,
            blockedIcon = MaterialSymbols.Filled.WifiOff,
            shape = rethinkGroupedListPairShape(isLeadingTile = true, position = CardPosition.Single),
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = onWifiClick,
        )
        RethinkAppNetworkTile(
            label = strings.mobile,
            blocked = state.mobileBlocked,
            allowedLabel = strings.allowed,
            blockedLabel = strings.blocked,
            allowedIcon = MaterialSymbols.Outlined.MobiledataArrows,
            blockedIcon = MaterialSymbols.Outlined.MobiledataOff,
            shape = rethinkGroupedListPairShape(isLeadingTile = false, position = CardPosition.Single),
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
    allowedIcon: ImageVector,
    blockedIcon: ImageVector,
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val motion = LocalRethinkMotion.current
    val colorSpec: AnimationSpec<Color> = tween(motion.durationMedium, easing = FastOutSlowInEasing)
    val baseRounded = shape as? RoundedCornerShape ?: RoundedCornerShape(SharedDimensions.cornerRadiusXl)
    val animatedShape = RoundedCornerShape(
        topStart = animateCorner(baseRounded.topStart, SharedDimensions.cornerRadius2xl, blocked, density),
        topEnd = animateCorner(baseRounded.topEnd, SharedDimensions.cornerRadius2xl, blocked, density),
        bottomEnd = animateCorner(baseRounded.bottomEnd, SharedDimensions.cornerRadius2xl, blocked, density),
        bottomStart = animateCorner(baseRounded.bottomStart, SharedDimensions.cornerRadius2xl, blocked, density),
    )
    val containerColor by animateColorAsState(
        targetValue = if (blocked) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = colorSpec,
        label = "app_network_tile_bg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (blocked) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = colorSpec,
        label = "app_network_tile_title",
    )
    val secondaryColor by animateColorAsState(
        targetValue = if (blocked) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = colorSpec,
        label = "app_network_tile_status",
    )
    // Same rule as the list's network segments: the icon sits directly on its segment, no
    // inner circle; allowed is tonal, blocked takes the error container's on-colour.
    val iconBlockedTint = if (blocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val iconAllowedTint = if (blocked) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.44f) else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = AppInfoActionMinHeight),
        shape = animatedShape,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingMd),
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

@Composable
private fun RethinkAppExclusiveRow(title: String, description: String, enabled: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, position: CardPosition, onClick: () -> Unit) {
    // Mutually exclusive modes, but each can be switched back off (to "none"), so these are
    // switches rather than radio buttons: turning one on turns the others off.
    RethinkSwitchListItem(
        headline = title,
        checked = enabled,
        onCheckedChange = { onClick() },
        supporting = description,
        leadingIcon = icon,
        position = position,
    )
}

@Composable
private fun RethinkAppToggleRow(title: String, description: String, checked: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, position: CardPosition, onChange: (Boolean) -> Unit) =
    RethinkSwitchListItem(headline = title, checked = checked, onCheckedChange = onChange, supporting = description, leadingIcon = icon, position = position)

@Composable
private fun RethinkAppNavigationRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, position: CardPosition, onClick: () -> Unit) {
    RethinkListItem(headline = title, leadingIcon = icon, position = position, showTrailingChevron = true, onClick = onClick)
}

@Composable
private fun RethinkAppNoActivity(message: String) {
    Surface(
        shape = RoundedCornerShape(SharedDimensions.groupCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        CompactEmptyState(message = message, icon = MaterialSymbols.Filled.Info)
    }
}

@Composable
private fun RethinkAppLogCard(section: RethinkAppInfoLogSection, strings: RethinkAppInfoStrings, onOpen: (() -> Unit)?, onEntry: ((RethinkAppInfoLogItem) -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        val header: @Composable () -> Unit = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(title = section.title, horizontalPadding = SharedDimensions.spacingXs, modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.padding(end = SharedDimensions.spacingXs),
                    horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RethinkPill(
                        section.count.toString(),
                        colors = if (section.count > 0) RethinkPillDefaults.primary() else RethinkPillDefaults.neutral(),
                        size = RethinkPillSize.Small,
                    )
                    if (onOpen != null) Icon(MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (onOpen != null) {
            Surface(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent,
                content = header,
            )
        } else {
            header()
        }
        when {
            section.loading -> RethinkLoadingState(label = strings.loading)
            section.empty -> CompactEmptyState(message = strings.empty, icon = MaterialSymbols.Filled.Info)
            else -> RethinkListGroup {
                section.entries.forEachIndexed { index, item ->
                    RethinkListItem(
                        headline = item.title,
                        supporting = item.subtitle,
                        leadingIcon = MaterialSymbols.Filled.Public,
                        position = when {
                            section.entries.lastIndex <= 0 -> CardPosition.Single
                            index == 0 -> CardPosition.First
                            index == section.entries.lastIndex -> CardPosition.Last
                            else -> CardPosition.Middle
                        },
                        defaultContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        showTrailingChevron = onEntry != null,
                        onClick = onEntry?.let { callback -> { callback(item) } },
                    )
                }
            }
        }
    }
}
