/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.proxy

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSwitchListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.rethinkSection

data class RethinkTcpProxyStrings(
    val title: String,
    val active: String,
    val inactive: String,
    val rethinkProxyTitle: String,
    val rethinkProxyDescription: String,
    val udpRelayTitle: String,
    val udpRelayDescription: String,
    val appsTitle: String,
    val appsDescription: @Composable (Int) -> String,
    val warpTitle: String,
    val warpDescription: String,
)

/** Common TCP proxy settings UI. The host owns validation, services and app selection. */
@Composable
fun RethinkTcpProxyScreen(
    tcpProxyEnabled: Boolean,
    tcpProxyDescription: String,
    tcpError: String?,
    udpRelayEnabled: Boolean,
    warpEnabled: Boolean,
    appCount: Int,
    strings: RethinkTcpProxyStrings,
    onTcpProxyEnabledChange: (Boolean) -> Unit,
    onUdpRelayEnabledChange: (Boolean) -> Unit,
    onWarpEnabledChange: (Boolean) -> Unit,
    onAppsClick: () -> Unit,
    onBackClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        subtitle = if (tcpProxyEnabled) strings.active else strings.inactive,
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        rethinkSection(strings.rethinkProxyTitle, strings.rethinkProxyDescription) {
            RethinkListGroup {
                RethinkSwitchListItem(
                    headline = strings.rethinkProxyTitle,
                    checked = tcpProxyEnabled,
                    onCheckedChange = onTcpProxyEnabledChange,
                    supporting = tcpError ?: tcpProxyDescription,
                    leadingIcon = MaterialSymbols.Filled.VpnKey,
                    position = cardPositionFor(0, 2),
                )
                RethinkSwitchListItem(
                    headline = strings.udpRelayTitle,
                    checked = udpRelayEnabled,
                    onCheckedChange = onUdpRelayEnabledChange,
                    supporting = strings.udpRelayDescription,
                    leadingIcon = MaterialSymbols.Filled.Settings,
                    position = cardPositionFor(1, 2),
                )
                RethinkListItem(
                    headline = strings.appsTitle,
                    supporting = strings.appsDescription(appCount),
                    leadingIcon = MaterialSymbols.Filled.Apps,
                    position = cardPositionFor(2, 2),
                    onClick = onAppsClick,
                )
            }
        }
        rethinkSection(strings.warpTitle, strings.warpDescription) {
            RethinkListGroup {
                RethinkSwitchListItem(
                    headline = strings.warpTitle,
                    checked = warpEnabled,
                    onCheckedChange = onWarpEnabledChange,
                    supporting = strings.warpDescription,
                    leadingIcon = MaterialSymbols.Filled.VpnKey,
                    position = CardPosition.Single,
                )
            }
        }
    }
}
