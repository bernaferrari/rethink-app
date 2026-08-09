/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.wireguard

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

data class RethinkWireguardDetailState(
    val name: String,
    val status: String,
    val statusColor: Color? = null,
    val isOneWireguard: Boolean = false,
    val appsCount: Int = 0,
    val catchAllEnabled: Boolean = false,
    val useMobileEnabled: Boolean = false,
    val ssidEnabled: Boolean = false,
    val ssidSupported: Boolean = false,
    val ssidSummary: String = "",
)

data class RethinkWireguardDetailStrings(
    val title: String,
    val configure: String,
    val addPeer: String,
    val peer: String,
    val edit: String,
    val delete: String,
    val deleteDescription: String,
    val apps: String,
    val manageApps: (Int) -> String,
    val manageHops: String,
    val advanced: String,
    val catchAll: String,
    val catchAllDescription: String,
    val useMobile: String,
    val useMobileDescription: String,
    val ssid: String,
    val editSsids: String,
    val peers: String,
    val oneWireguardNotice: String,
)

/** Shared configuration-detail chrome. Hosts own tunnel operations, permissions, dialogs, and peer content. */
@Composable
fun RethinkWireguardDetail(
    state: RethinkWireguardDetailState,
    strings: RethinkWireguardDetailStrings,
    onBackClick: () -> Unit,
    onAddPeer: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageApps: () -> Unit,
    onManageHops: () -> Unit,
    onCatchAllChange: (Boolean) -> Unit,
    onUseMobileChange: (Boolean) -> Unit,
    onSsidChange: (Boolean) -> Unit,
    onEditSsids: () -> Unit,
    modifier: Modifier = Modifier,
    peerContent: @Composable () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    androidx.compose.material3.Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { RethinkLargeTopBar(strings.title, onBackClick = onBackClick, scrollBehavior = scrollBehavior) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                bottom = SharedDimensions.spacing3xl,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            item { RethinkWireguardDetailOverview(state) }
            if (state.isOneWireguard) item { RethinkOneWireguardNotice(strings.oneWireguardNotice) }
            item {
                SectionHeader(strings.configure)
                RethinkListGroup {
                    RethinkWireguardDetailAction(strings.addPeer, strings.peer, MaterialSymbols.Filled.Add, CardPosition.First, onAddPeer)
                    RethinkWireguardDetailAction(strings.edit, strings.title, MaterialSymbols.Filled.Edit, CardPosition.Middle, onEdit)
                    RethinkWireguardDetailAction(strings.delete, strings.deleteDescription, MaterialSymbols.Filled.Delete, CardPosition.Last, onDelete, MaterialTheme.colorScheme.error)
                }
            }
            if (!state.isOneWireguard) {
                item {
                    SectionHeader(strings.apps)
                    RethinkListGroup {
                        RethinkWireguardDetailAction(strings.manageApps(state.appsCount), null, MaterialSymbols.Filled.Apps, CardPosition.First, onManageApps, enabled = !state.catchAllEnabled)
                        RethinkWireguardDetailAction(strings.manageHops, null, MaterialSymbols.AutoMirrored.Filled.ArrowForward, CardPosition.Last, onManageHops)
                    }
                }
            }
            item {
                SectionHeader(strings.advanced)
                RethinkListGroup {
                    RethinkWireguardDetailToggle(strings.catchAll, strings.catchAllDescription, MaterialSymbols.Filled.Security, state.catchAllEnabled, onCatchAllChange, CardPosition.First)
                    RethinkWireguardDetailToggle(
                        strings.useMobile,
                        strings.useMobileDescription,
                        MaterialSymbols.Filled.Settings,
                        state.useMobileEnabled,
                        onUseMobileChange,
                        if (state.ssidSupported) CardPosition.Middle else CardPosition.Last,
                    )
                    if (state.ssidSupported) {
                        RethinkWireguardDetailToggle(strings.ssid, state.ssidSummary, MaterialSymbols.Filled.Wifi, state.ssidEnabled, onSsidChange, CardPosition.Middle)
                        RethinkWireguardDetailAction(strings.edit, strings.editSsids, MaterialSymbols.Filled.Edit, CardPosition.Last, onEditSsids)
                    }
                }
            }
            item { SectionHeader(strings.peers) }
            item { peerContent() }
        }
    }
}

@Composable
private fun RethinkWireguardDetailOverview(state: RethinkWireguardDetailState) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = SharedDimensions.spacingSm),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadius2xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SharedDimensions.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.iconContainerRadius), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(MaterialSymbols.Filled.VpnKey, null, Modifier.size(SharedDimensions.iconContainerMd).padding(SharedDimensions.spacingSm), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(state.name, style = MaterialTheme.typography.titleMedium)
                Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.statusColor?.let { color ->
                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadiusFull), color = color.copy(alpha = 0.14f)) {
                    Text(state.status, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun RethinkOneWireguardNotice(message: String) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadius2xl), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
    }
}

@Composable
private fun RethinkWireguardDetailAction(
    title: String,
    description: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    position: CardPosition,
    onClick: () -> Unit,
    accent: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) = RethinkListItem(
    headline = title,
    supporting = description,
    leadingIcon = icon,
    leadingIconTint = accent,
    leadingIconContainerColor = accent.copy(alpha = 0.14f),
    position = position,
    enabled = enabled,
    onClick = onClick,
)

@Composable
private fun RethinkWireguardDetailToggle(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: CardPosition,
) = RethinkListItem(
    headline = title,
    supporting = description,
    leadingIcon = icon,
    position = position,
    trailing = { Switch(checked = checked, onCheckedChange = null) },
    onClick = { onCheckedChange(!checked) },
)
