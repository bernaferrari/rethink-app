/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.apps

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormSection
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListPairShape

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
    val back: String,
    val status: String,
    val temporaryAllow: String,
    val firewall: String,
    val wifi: String,
    val wifiDescription: String,
    val mobile: String,
    val mobileDescription: String,
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingSm,
                bottom = SharedDimensions.spacing3xl,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            if (!state.appAvailable) {
                item {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
                    ) {
                        RethinkEmptyState(title = strings.unavailable, message = "")
                        RethinkListGroup {
                            RethinkListItem(
                                headline = strings.back,
                                leadingIcon = MaterialSymbols.AutoMirrored.Filled.ArrowForward,
                                position = CardPosition.Single,
                                onClick = onBackClick,
                            )
                        }
                    }
                }
            } else {
                item { RethinkAppStatusCard(state, strings) }
                item {
                    // Compact network cards sit above the firewall settings rows.
                    RethinkFormSection(strings.firewall) {
                        RethinkAppNetworkPair(state, strings, onWifiClick, onMobileClick)
                        RethinkListGroup {
                            RethinkAppExclusiveRow(strings.isolate, strings.isolateDescription, state.isolated, MaterialSymbols.Filled.Security, MaterialTheme.colorScheme.error, CardPosition.First, onIsolateClick)
                            RethinkAppExclusiveRow(strings.bypassDns, strings.bypassDnsDescription, state.bypassDnsFirewall, MaterialSymbols.Filled.Dns, MaterialTheme.colorScheme.tertiary, CardPosition.Middle, onBypassDnsClick)
                            RethinkAppExclusiveRow(strings.bypassUniversal, strings.bypassUniversalDescription, state.bypassUniversalFirewall, MaterialSymbols.Filled.Public, MaterialTheme.colorScheme.tertiary, CardPosition.Middle, onBypassUniversalClick)
                            RethinkAppExclusiveRow(strings.exclude, strings.excludeDescription, state.excluded, MaterialSymbols.Filled.Apps, MaterialTheme.colorScheme.secondary, CardPosition.Last, onExcludeClick)
                        }
                    }
                }
                item {
                    RethinkFormSection(strings.advanced) {
                        RethinkListGroup {
                            RethinkAppToggleRow(strings.proxyExclude, strings.proxyExcludeDescription, state.proxyExcluded, MaterialSymbols.Filled.Settings, CardPosition.First, onProxyExcludedChange)
                            RethinkAppToggleRow(strings.temporaryAllow, strings.temporaryAllowDescription, state.tempAllowed, MaterialSymbols.Filled.Timer, CardPosition.Last, onTempAllowChange)
                        }
                    }
                }
                item {
                    RethinkFormSection(
                        title = strings.rules,
                        titleModifier = Modifier.padding(horizontal = SharedDimensions.spacingSm),
                    ) {
                        RethinkListGroup {
                            RethinkAppNavigationRow(strings.systemAppInfo, MaterialSymbols.Filled.Settings, CardPosition.First, onSystemAppInfo)
                            RethinkAppNavigationRow(strings.ipRules, MaterialSymbols.Filled.Public, CardPosition.Middle, onIpRules)
                            RethinkAppNavigationRow(strings.domainRules, MaterialSymbols.Filled.Dns, CardPosition.Last, onDomainRules)
                        }
                    }
                }
                item { RethinkAppLogCard(state.activeConnections, strings, onActiveConnections, onActiveEntry) }
                item { RethinkAppLogCard(state.domainLogs, strings, onDomains, onDomainEntry) }
                item { RethinkAppLogCard(state.ipLogs, strings, onIps, onIpEntry) }
                item { Spacer(Modifier.height(SharedDimensions.spacingSm)) }
            }
        }
    }
}

@Composable
private fun RethinkAppStatusCard(state: RethinkAppInfoState, strings: RethinkAppInfoStrings) {
    Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadius3xl), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SharedDimensions.cardPadding), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
            Text(strings.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs), verticalAlignment = Alignment.CenterVertically) {
                RethinkAppStatusPill(state.status, true)
                if (state.temporaryAllowed) RethinkAppStatusPill(strings.temporaryAllow, true)
            }
            if (state.proxyDetails.isNotBlank()) Text(state.proxyDetails, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RethinkAppStatusPill(label: String, active: Boolean) {
    Surface(shape = RoundedCornerShape(SharedDimensions.chipCornerRadius), color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = SharedDimensions.spacingSm, vertical = 3.dp))
    }
}

@Composable
private fun RethinkAppNetworkPair(
    state: RethinkAppInfoState,
    strings: RethinkAppInfoStrings,
    onWifiClick: () -> Unit,
    onMobileClick: () -> Unit,
) {
    // Compact horizontal network cards with a 2.dp gap like list rows.
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        RethinkAppNetworkTile(
            label = strings.wifi,
            blocked = state.wifiBlocked,
            allowedLabel = strings.allowed,
            blockedLabel = strings.blocked,
            allowedIcon = MaterialSymbols.Filled.Wifi,
            blockedIcon = MaterialSymbols.Filled.WifiOff,
            shape = rethinkGroupedListPairShape(isLeadingTile = true, position = CardPosition.Single),
            modifier = Modifier.weight(1f),
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
            modifier = Modifier.weight(1f),
            onClick = onMobileClick,
        )
    }
}

/**
 * Compact allow/block network card:
 * fixed 72.dp height, circular icon pill, and Allowed/Blocked status text.
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
    val baseRounded = shape as? RoundedCornerShape ?: RoundedCornerShape(20.dp)
    val animatedShape = RoundedCornerShape(
        topStart = animateCorner(baseRounded.topStart, 28.dp, blocked, density),
        topEnd = animateCorner(baseRounded.topEnd, 28.dp, blocked, density),
        bottomEnd = animateCorner(baseRounded.bottomEnd, 28.dp, blocked, density),
        bottomStart = animateCorner(baseRounded.bottomStart, 28.dp, blocked, density),
    )
    val containerColor by animateColorAsState(
        targetValue = if (blocked) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "app_network_tile_bg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (blocked) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(400),
        label = "app_network_tile_title",
    )
    val secondaryColor by animateColorAsState(
        targetValue = if (blocked) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(400),
        label = "app_network_tile_status",
    )
    val iconContainerColor by animateColorAsState(
        targetValue = if (blocked) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(400),
        label = "app_network_tile_icon_bg",
    )
    val iconBlockedTint = if (blocked) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconAllowedTint = if (blocked) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.44f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = animatedShape,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center,
            ) {
                DiagonalWipeIcon(
                    blocked = blocked,
                    allowedIcon = allowedIcon,
                    blockedIcon = blockedIcon,
                    allowedTint = iconAllowedTint,
                    blockedTint = iconBlockedTint,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimatedContent(
                    targetState = blocked,
                    transitionSpec = {
                        (slideInVertically { height -> if (targetState) height else -height } + fadeIn()) togetherWith
                            (slideOutVertically { height -> if (targetState) -height else height } + fadeOut())
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
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "app_network_tile_corner",
    )
    return CornerSize(currentDp)
}

@Composable
private fun RethinkAppExclusiveRow(title: String, description: String, enabled: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, position: CardPosition, onClick: () -> Unit) {
    RethinkListItem(
        headline = title,
        supporting = description,
        leadingIcon = icon,
        leadingIconTint = tint,
        position = position,
        defaultContainerColor = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        trailing = { Switch(checked = enabled, onCheckedChange = null) },
        onClick = onClick,
    )
}

@Composable
private fun RethinkAppToggleRow(title: String, description: String, checked: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, position: CardPosition, onChange: (Boolean) -> Unit) {
    RethinkListItem(
        headline = title,
        supporting = description,
        leadingIcon = icon,
        position = position,
        defaultContainerColor = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        onClick = { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun RethinkAppNavigationRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, position: CardPosition, onClick: () -> Unit) {
    RethinkListItem(headline = title, leadingIcon = icon, position = position, showTrailingChevron = true, onClick = onClick)
}

@Composable
private fun RethinkAppLogCard(section: RethinkAppInfoLogSection, strings: RethinkAppInfoStrings, onOpen: (() -> Unit)?, onEntry: ((RethinkAppInfoLogItem) -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        val header: @Composable () -> Unit = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingXs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
                    RethinkAppStatusPill(section.count.toString(), section.count > 0)
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
            section.loading -> RethinkEmptyState(title = strings.loading, message = "")
            section.empty -> RethinkEmptyState(title = strings.empty, message = "")
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
