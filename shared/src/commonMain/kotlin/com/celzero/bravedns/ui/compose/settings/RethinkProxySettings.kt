/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.settings

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

data class RethinkProxySettingsState(
    val canEnableProxy: Boolean,
    val isRefreshing: Boolean,
    val wireguardAvailable: Boolean,
    val wireguardDescription: String,
    val socks5Enabled: Boolean,
    val socks5Description: String,
    val httpEnabled: Boolean,
    val httpDescription: String,
    val orbotEnabled: Boolean,
    val orbotConnecting: Boolean,
    val orbotDescription: String,
    val orbotAppCount: Int? = null,
)

data class RethinkProxySettingsStrings(
    val title: String,
    val refresh: String,
    val warning: String,
    val wireguard: String,
    val socks5: String,
    val http: String,
    val orbot: String,
    val active: String,
    val inactive: String,
    val waiting: String,
    val apps: String,
    val openApp: String,
    val info: String,
)

/** Shared proxy-settings shell. Hosts keep proxy services, Orbot availability, and form dialogs. */
@Composable
fun RethinkProxySettingsScreen(
    listState: LazyListState,
    state: RethinkProxySettingsState,
    strings: RethinkProxySettingsStrings,
    activeFocusKey: String? = null,
    onBackClick: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onWireguardClick: () -> Unit,
    onSocksRowClick: () -> Unit,
    onSocksChange: (Boolean) -> Unit,
    onHttpRowClick: () -> Unit,
    onHttpChange: (Boolean) -> Unit,
    onOrbotClick: () -> Unit,
    onOrbotChange: (Boolean) -> Unit,
    onOrbotAppsClick: (() -> Unit)? = null,
    onOpenOrbotApp: () -> Unit,
    onOrbotInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    androidx.compose.material3.Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
                titleStartPadding = SharedDimensions.spacingSm,
                actions = {
                    if (state.canEnableProxy) {
                        IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                            if (state.isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(MaterialSymbols.Filled.Refresh, strings.refresh)
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingMd,
                bottom = SharedDimensions.spacing3xl,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            if (!state.canEnableProxy) item { RethinkProxyWarning(strings.warning, activeFocusKey == "proxy_warning") }
            if (state.wireguardAvailable) item {
                SectionHeader(strings.wireguard)
                RethinkListGroup {
                    RethinkListItem(
                        headline = strings.wireguard,
                        supporting = state.wireguardDescription,
                        leadingIcon = MaterialSymbols.Filled.VpnKey,
                        position = CardPosition.Single,
                        highlighted = activeFocusKey == "proxy_wireguard",
                        onClick = onWireguardClick,
                    )
                }
            }
            item {
                SectionHeader(strings.socks5)
                RethinkProxyToggleRow(
                    title = strings.socks5,
                    description = state.socks5Description,
                    checked = state.socks5Enabled,
                    enabled = state.canEnableProxy || !state.socks5Enabled,
                    highlighted = activeFocusKey == "proxy_socks",
                    icon = MaterialSymbols.Filled.Security,
                    onRowClick = onSocksRowClick,
                    onCheckedChange = onSocksChange,
                )
            }
            item {
                SectionHeader(strings.http)
                RethinkProxyToggleRow(
                    title = strings.http,
                    description = state.httpDescription,
                    checked = state.httpEnabled,
                    enabled = state.canEnableProxy || !state.httpEnabled,
                    highlighted = activeFocusKey == "proxy_http",
                    icon = MaterialSymbols.Filled.Security,
                    onRowClick = onHttpRowClick,
                    onCheckedChange = onHttpChange,
                )
            }
            item {
                SectionHeader(strings.orbot)
                RethinkOrbotPanel(
                    state = state,
                    strings = strings,
                    highlightedKey = activeFocusKey,
                    onMainClick = onOrbotClick,
                    onCheckedChange = onOrbotChange,
                    onAppsClick = onOrbotAppsClick,
                    onOpenAppClick = onOpenOrbotApp,
                    onInfoClick = onOrbotInfo,
                )
            }
        }
    }
}

@Composable
private fun RethinkProxyWarning(message: String, highlighted: Boolean) {
    Surface(
        shape = RoundedCornerShape(SharedDimensions.cornerRadiusXl),
        color = if (highlighted) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingSmMd))
    }
}

@Composable
private fun RethinkProxyToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    highlighted: Boolean,
    icon: ImageVector,
    onRowClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    RethinkListGroup {
        RethinkListItem(
            headline = title,
            supporting = description,
            leadingIcon = icon,
            position = CardPosition.Single,
            highlighted = highlighted,
            enabled = enabled,
            trailing = { Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange) },
            onClick = onRowClick,
        )
    }
}

@Composable
private fun RethinkOrbotPanel(
    state: RethinkProxySettingsState,
    strings: RethinkProxySettingsStrings,
    highlightedKey: String?,
    onMainClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onAppsClick: (() -> Unit)?,
    onOpenAppClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    val highlighted = highlightedKey == "proxy_orbot"
    val enabled = state.canEnableProxy && !state.orbotConnecting
    val status = when {
        state.orbotConnecting -> strings.waiting to MaterialTheme.colorScheme.primary
        state.orbotEnabled -> strings.active to MaterialTheme.colorScheme.tertiary
        else -> strings.inactive to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusBackground = status.second.copy(alpha = 0.14f)
    val panelShape = RoundedCornerShape(SharedDimensions.cornerRadius3xl)
    Surface(
        onClick = onMainClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().clip(panelShape),
        shape = panelShape,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(SharedDimensions.dividerThicknessBold, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
    ) {
        Column(Modifier.padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingSmMd), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSmMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = statusBackground, modifier = Modifier.size(34.dp)) {
                    Icon(MaterialSymbols.Filled.Security, null, Modifier.padding(8.dp), tint = status.second)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(strings.orbot, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius), color = statusBackground) {
                        Text(status.first, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = status.second, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                Switch(checked = state.orbotEnabled, enabled = enabled, onCheckedChange = onCheckedChange)
            }
            Text(state.orbotDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (enabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    state.orbotAppCount?.let { count -> onAppsClick?.let { RethinkProxyChip("${strings.apps} $count", MaterialSymbols.Filled.Apps, highlightedKey == "proxy_orbot_apps", it) } }
                    RethinkProxyChip(strings.openApp, MaterialSymbols.AutoMirrored.Filled.ArrowForward, highlightedKey == "proxy_orbot_open_app" || highlightedKey == "proxy_orbot_notification", onOpenAppClick)
                    RethinkProxyChip(strings.info, MaterialSymbols.Filled.Info, highlightedKey == "proxy_orbot_info", onInfoClick)
                }
            }
        }
    }
}

@Composable
private fun RethinkProxyChip(label: String, icon: ImageVector, highlighted: Boolean, onClick: () -> Unit) {
    val container = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val content = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(16.dp)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = content,
            leadingIconContentColor = content,
        ),
    )
}
