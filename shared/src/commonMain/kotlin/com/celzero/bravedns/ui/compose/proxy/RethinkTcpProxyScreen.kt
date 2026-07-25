/* Copyright 2026 RethinkDNS and its authors */

package com.celzero.bravedns.ui.compose.proxy

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.celzero.bravedns.ui.compose.theme.SectionHeaderWithSubtitle
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import com.celzero.bravedns.ui.compose.theme.cardPositionFor

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
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = PaddingValues(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingMd,
            bottom = SharedDimensions.spacing3xl,
        ),
    ) {
        item {
            SectionHeaderWithSubtitle(title = strings.rethinkProxyTitle, subtitle = strings.rethinkProxyDescription)
            Column {
                RethinkListItem(
                    headline = strings.rethinkProxyTitle,
                    supporting = tcpError ?: tcpProxyDescription,
                    leadingIcon = MaterialSymbols.Filled.VpnKey,
                    position = cardPositionFor(0, 2),
                    onClick = { onTcpProxyEnabledChange(!tcpProxyEnabled) },
                    trailing = { Switch(tcpProxyEnabled, onTcpProxyEnabledChange) },
                )
                RethinkListItem(
                    headline = strings.udpRelayTitle,
                    supporting = strings.udpRelayDescription,
                    leadingIcon = MaterialSymbols.Filled.Settings,
                    position = cardPositionFor(1, 2),
                    onClick = { onUdpRelayEnabledChange(!udpRelayEnabled) },
                    trailing = { Switch(udpRelayEnabled, onUdpRelayEnabledChange) },
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
        item {
            SectionHeaderWithSubtitle(title = strings.warpTitle, subtitle = strings.warpDescription)
            RethinkListItem(
                headline = strings.warpTitle,
                supporting = strings.warpDescription,
                leadingIcon = MaterialSymbols.Filled.VpnKey,
                position = cardPositionFor(0, 0),
                onClick = { onWarpEnabledChange(!warpEnabled) },
                trailing = { Switch(warpEnabled, onWarpEnabledChange) },
            )
        }
        item { Spacer(Modifier.height(SharedDimensions.spacingSm)) }
    }
}
