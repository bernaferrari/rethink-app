/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.apps

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val isolate: String,
    val isolateDescription: String,
    val bypassDns: String,
    val bypassDnsDescription: String,
    val bypassUniversal: String,
    val bypassUniversalDescription: String,
    val exclude: String,
    val excludeDescription: String,
    val enabled: String,
    val disabled: String,
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
    onActiveConnections: () -> Unit,
    onDomains: () -> Unit,
    onIps: () -> Unit,
    onActiveEntry: (RethinkAppInfoLogItem) -> Unit,
    onDomainEntry: (RethinkAppInfoLogItem) -> Unit,
    onIpEntry: (RethinkAppInfoLogItem) -> Unit,
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
                    RethinkFormSection(strings.firewall) {
                        RethinkAppNetworkPair(state, strings, onWifiClick, onMobileClick)
                        RethinkListGroup {
                            RethinkAppExclusiveRow(strings.isolate, strings.isolateDescription, state.isolated, MaterialSymbols.Filled.Security, MaterialTheme.colorScheme.error, strings.enabled, strings.disabled, CardPosition.First, onIsolateClick)
                            RethinkAppExclusiveRow(strings.bypassDns, strings.bypassDnsDescription, state.bypassDnsFirewall, MaterialSymbols.Filled.Dns, MaterialTheme.colorScheme.tertiary, strings.enabled, strings.disabled, CardPosition.Middle, onBypassDnsClick)
                            RethinkAppExclusiveRow(strings.bypassUniversal, strings.bypassUniversalDescription, state.bypassUniversalFirewall, MaterialSymbols.Filled.Public, MaterialTheme.colorScheme.tertiary, strings.enabled, strings.disabled, CardPosition.Middle, onBypassUniversalClick)
                            RethinkAppExclusiveRow(strings.exclude, strings.excludeDescription, state.excluded, MaterialSymbols.Filled.Apps, MaterialTheme.colorScheme.secondary, strings.enabled, strings.disabled, CardPosition.Last, onExcludeClick)
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
                    RethinkFormSection(strings.rules) {
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
private fun RethinkAppNetworkPair(state: RethinkAppInfoState, strings: RethinkAppInfoStrings, onWifiClick: () -> Unit, onMobileClick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile), modifier = Modifier.fillMaxWidth()) {
        RethinkAppNetworkTile(strings.wifi, strings.wifiDescription, state.wifiBlocked, MaterialSymbols.Filled.Wifi, MaterialSymbols.Filled.WifiOff, rethinkGroupedListPairShape(isLeadingTile = true, position = CardPosition.Single), Modifier.weight(1f), onWifiClick)
        RethinkAppNetworkTile(strings.mobile, strings.mobileDescription, state.mobileBlocked, MaterialSymbols.Filled.SignalCellularAlt, MaterialSymbols.Filled.MobileOff, rethinkGroupedListPairShape(isLeadingTile = false, position = CardPosition.Single), Modifier.weight(1f), onMobileClick)
    }
}

@Composable
private fun RethinkAppNetworkTile(title: String, description: String, blocked: Boolean, allowedIcon: androidx.compose.ui.graphics.vector.ImageVector, blockedIcon: androidx.compose.ui.graphics.vector.ImageVector, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.clip(shape), shape = shape, color = if (blocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.48f) else MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(SharedDimensions.cardPadding), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
            Icon(if (blocked) blockedIcon else allowedIcon, null, tint = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RethinkAppExclusiveRow(title: String, description: String, enabled: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, onLabel: String, offLabel: String, position: CardPosition, onClick: () -> Unit) {
    RethinkListItem(headline = title, supporting = description, leadingIcon = icon, leadingIconTint = tint, position = position, trailing = { RethinkAppStatusPill(if (enabled) onLabel else offLabel, enabled) }, onClick = onClick)
}

@Composable
private fun RethinkAppToggleRow(title: String, description: String, checked: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, position: CardPosition, onChange: (Boolean) -> Unit) {
    RethinkListItem(headline = title, supporting = description, leadingIcon = icon, position = position, onClick = { onChange(!checked) }, trailing = { Switch(checked = checked, onCheckedChange = null) })
}

@Composable
private fun RethinkAppNavigationRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, position: CardPosition, onClick: () -> Unit) {
    RethinkListItem(headline = title, leadingIcon = icon, position = position, showTrailingChevron = true, onClick = onClick)
}

@Composable
private fun RethinkAppLogCard(section: RethinkAppInfoLogSection, strings: RethinkAppInfoStrings, onOpen: () -> Unit, onEntry: (RethinkAppInfoLogItem) -> Unit) {
    val headerShape = RoundedCornerShape(SharedDimensions.cornerRadiusMd)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        Surface(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().clip(headerShape),
            shape = headerShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                Modifier.padding(horizontal = SharedDimensions.cardPadding, vertical = SharedDimensions.spacingSm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(section.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
                    RethinkAppStatusPill(section.count.toString(), section.count > 0)
                    Icon(MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
                        showTrailingChevron = true,
                        onClick = { onEntry(item) },
                    )
                }
            }
        }
    }
}
