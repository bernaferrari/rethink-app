/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.wireguard

import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWell
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWellDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSwitchListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
    val deleteConfirmTitle: String,
    val deleteConfirmMessage: String,
    val cancel: String,
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
    var confirmDelete by remember { mutableStateOf(false) }
    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        onBackClick = onBackClick,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
    ) {
            item { RethinkWireguardDetailOverview(state) }
            if (state.isOneWireguard) item { RethinkOneWireguardNotice(strings.oneWireguardNotice) }
            item {
                SectionHeader(strings.configure)
                RethinkListGroup {
                    RethinkWireguardDetailAction(strings.addPeer, strings.peer, MaterialSymbols.Filled.Add, CardPosition.First, onAddPeer)
                    // Only Edit leaves the screen; the other rows open dialogs, so they get no chevron.
                    RethinkWireguardDetailAction(strings.edit, strings.title, MaterialSymbols.Filled.Edit, CardPosition.Middle, onEdit, navigates = true)
                    RethinkWireguardDetailAction(strings.delete, strings.deleteDescription, MaterialSymbols.Filled.Delete, CardPosition.Last, { confirmDelete = true }, destructive = true)
                }
            }
            if (!state.isOneWireguard) {
                item {
                    SectionHeader(strings.apps)
                    RethinkListGroup {
                        RethinkWireguardDetailAction(strings.manageApps(state.appsCount), null, MaterialSymbols.Filled.Apps, CardPosition.First, onManageApps, enabled = !state.catchAllEnabled)
                        RethinkWireguardDetailAction(strings.manageHops, null, MaterialSymbols.Filled.MobiledataArrows, CardPosition.Last, onManageHops)
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
    if (confirmDelete) {
        RethinkConfirmDialog(
            onDismissRequest = { confirmDelete = false },
            title = strings.deleteConfirmTitle,
            message = strings.deleteConfirmMessage,
            confirmText = strings.deleteConfirmTitle,
            dismissText = strings.cancel,
            isConfirmDestructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

@Composable
private fun RethinkWireguardDetailOverview(state: RethinkWireguardDetailState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadius2xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SharedDimensions.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            RethinkIconWell(
                icon = MaterialSymbols.Filled.VpnKey,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconSize = SharedDimensions.iconSizeMd,
            )
            Column(Modifier.weight(1f)) {
                Text(state.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                // With a status color the pill carries the status; never say it twice.
                if (state.statusColor == null) {
                    Text(state.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.statusColor?.let { color ->
                RethinkPill(state.status, colors = RethinkPillDefaults.status(color))
            }
        }
    }
}

@Composable
private fun RethinkOneWireguardNotice(message: String) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadius2xl), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = SharedDimensions.cardPadding, vertical = SharedDimensions.spacingMd))
    }
}

@Composable
private fun RethinkWireguardDetailAction(
    title: String,
    description: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    position: CardPosition,
    onClick: () -> Unit,
    enabled: Boolean = true,
    navigates: Boolean = false,
    destructive: Boolean = false,
) = RethinkListItem(
    headline = title,
    supporting = description,
    leadingIcon = icon,
    destructive = destructive,
    leadingIconTint = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
    leadingIconContainerColor = if (destructive) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        RethinkIconWellDefaults.tinted(MaterialTheme.colorScheme.primary)
    },
    position = position,
    enabled = enabled,
    showTrailingChevron = navigates,
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
) = RethinkSwitchListItem(
    headline = title,
    checked = checked,
    onCheckedChange = onCheckedChange,
    supporting = description,
    leadingIcon = icon,
    position = position,
)
