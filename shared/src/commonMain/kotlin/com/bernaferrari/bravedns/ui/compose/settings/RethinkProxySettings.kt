@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import com.bernaferrari.bravedns.ui.compose.theme.rethinkListItemColors

import com.bernaferrari.bravedns.ui.compose.theme.RethinkSplitSwitch
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWellDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkSection
import com.bernaferrari.bravedns.ui.compose.theme.RethinkContainedCard

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
    /** Plural-aware supporting line for the Orbot apps row, e.g. "4 apps use Orbot". */
    val appsCount: @Composable (Int) -> String,
    val openApp: String,
    val info: String,
    /** Header above WireGuard, SOCKS5 and HTTP; must not repeat any row title. */
    val serversSection: String,
    /** Header above the Orbot group; must not repeat the "Orbot" row title. */
    val torSection: String,
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
    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        onBackClick = onBackClick,
        modifier = modifier,
        listState = listState,
        topBarActions = {
            if (state.canEnableProxy) {
                IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                    if (state.isRefreshing) LoadingIndicator(Modifier.size(SharedDimensions.iconSizeMd))
                    else Icon(MaterialSymbols.Filled.Refresh, strings.refresh)
                }
            }
        },
    ) {
        if (!state.canEnableProxy) item { RethinkProxyWarning(strings.warning, activeFocusKey == "proxy_warning") }
        rethinkSection(strings.serversSection) {
            RethinkListGroup {
                val rowCount = if (state.wireguardAvailable) 3 else 2
                var row = 0
                if (state.wireguardAvailable) {
                    RethinkListItem(
                        headline = strings.wireguard,
                        supporting = state.wireguardDescription,
                        leadingIcon = MaterialSymbols.Filled.VpnKey,
                        position = cardPositionFor(row++, rowCount - 1),
                        highlighted = activeFocusKey == "proxy_wireguard",
                        showTrailingChevron = true,
                        onClick = onWireguardClick,
                    )
                }
                RethinkProxyToggleRow(
                    title = strings.socks5,
                    description = state.socks5Description,
                    checked = state.socks5Enabled,
                    enabled = state.canEnableProxy || !state.socks5Enabled,
                    highlighted = activeFocusKey == "proxy_socks",
                    icon = MaterialSymbols.Filled.Security,
                    position = cardPositionFor(row++, rowCount - 1),
                    onRowClick = onSocksRowClick,
                    onCheckedChange = onSocksChange,
                )
                RethinkProxyToggleRow(
                    title = strings.http,
                    description = state.httpDescription,
                    checked = state.httpEnabled,
                    enabled = state.canEnableProxy || !state.httpEnabled,
                    highlighted = activeFocusKey == "proxy_http",
                    icon = MaterialSymbols.Filled.Http,
                    position = cardPositionFor(row, rowCount - 1),
                    onRowClick = onHttpRowClick,
                    onCheckedChange = onHttpChange,
                )
            }
        }
        rethinkSection(strings.torSection) {
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

@Composable
private fun RethinkProxyWarning(message: String, highlighted: Boolean) {
    RethinkContainedCard(
        color = if (highlighted) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
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
    position: CardPosition,
    onRowClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    RethinkListItem(
        headline = title,
        supporting = description,
        leadingIcon = icon,
        position = position,
        highlighted = highlighted,
        enabled = enabled,
        trailing = {
            // The row opens configuration; the switch is a separate action. The divider makes the
            // two targets visually distinct.
            RethinkSplitSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                contentDescription = title,
            )
        },
        onClick = onRowClick,
    )
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
        state.orbotConnecting -> strings.waiting to RethinkStatusColors.mixed
        state.orbotEnabled -> strings.active to RethinkStatusColors.allowed
        else -> strings.inactive to RethinkStatusColors.idle
    }
    val actionCount = if (enabled) 1 + (if (state.orbotAppCount != null && onAppsClick != null) 1 else 0) + 2 else 1
    RethinkListGroup {
        RethinkListItem(
            headline = strings.orbot,
            supporting = "${status.first} · ${state.orbotDescription}",
            leadingIcon = MaterialSymbols.Filled.Security,
            // Inactive Orbot keeps the shared well like every other row; only a live state tints it.
            leadingIconTint = if (state.orbotEnabled || state.orbotConnecting) status.second else MaterialTheme.colorScheme.primary,
            colors = rethinkListItemColors(leadingIconContainerColor = if (state.orbotEnabled || state.orbotConnecting) RethinkIconWellDefaults.tinted(status.second) else null),
            position = cardPositionFor(0, actionCount - 1),
            highlighted = highlighted,
            enabled = enabled,
            trailing = {
                RethinkSplitSwitch(
                    checked = state.orbotEnabled,
                    enabled = enabled,
                    onCheckedChange = onCheckedChange,
                    contentDescription = strings.orbot,
                )
            },
            onClick = onMainClick,
        )
        if (enabled) {
            var row = 1
            state.orbotAppCount?.let { count ->
                onAppsClick?.let { openApps ->
                    RethinkListItem(
                        headline = strings.apps,
                        supporting = strings.appsCount(count),
                        leadingIcon = MaterialSymbols.Filled.Apps,
                        position = cardPositionFor(row++, actionCount - 1),
                        highlighted = highlightedKey == "proxy_orbot_apps",
                        showTrailingChevron = true,
                        onClick = openApps,
                    )
                }
            }
            RethinkListItem(
                headline = strings.openApp,
                leadingIcon = MaterialSymbols.Filled.PhoneAndroid,
                position = cardPositionFor(row++, actionCount - 1),
                highlighted = highlightedKey == "proxy_orbot_open_app" || highlightedKey == "proxy_orbot_notification",
                showTrailingChevron = true,
                onClick = onOpenAppClick,
            )
            RethinkListItem(
                headline = strings.info,
                leadingIcon = MaterialSymbols.Filled.Info,
                position = cardPositionFor(row, actionCount - 1),
                highlighted = highlightedKey == "proxy_orbot_info",
                // Opens an explanation dialog in place; chevrons are reserved for navigation.
                onClick = onInfoClick,
            )
        }
    }
}
