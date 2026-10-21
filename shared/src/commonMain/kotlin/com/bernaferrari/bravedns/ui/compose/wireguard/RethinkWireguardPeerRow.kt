/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.wireguard

import com.bernaferrari.bravedns.ui.compose.theme.RethinkKeyValueRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkKeyValueLayout
import com.bernaferrari.bravedns.ui.compose.theme.RethinkOverflowMenu
import com.bernaferrari.bravedns.ui.compose.theme.RethinkOverflowItem
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

data class RethinkWireguardPeerItem(
    val publicKey: String,
    val allowedIps: String? = null,
    val endpoint: String? = null,
    val persistentKeepalive: String? = null,
)

data class RethinkWireguardPeerRowStrings(
    val peer: String,
    val publicKey: String,
    val allowedIps: String,
    val endpoint: String,
    val persistentKeepalive: String,
    val editDescription: String,
    val deleteDescription: String,
    val deleteTitle: String,
    val deleteMessage: String,
    val deleteConfirm: String,
    val cancel: String,
    val moreActions: String = "More options",
)

/** Portable card for an existing WireGuard peer. Hosts provide all peer mutations. */
@Composable
fun RethinkWireguardPeerRow(
    item: RethinkWireguardPeerItem,
    strings: RethinkWireguardPeerRowStrings,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember(item.publicKey) { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.groupCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(SharedDimensions.spacingLg),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.peer, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                // Edit and the destructive Delete live in the card's overflow, not as bare icons.
                RethinkOverflowMenu(
                    moreDescription = strings.moreActions,
                    items = listOf(
                        RethinkOverflowItem(strings.editDescription, MaterialSymbols.Filled.Edit, onEdit),
                        RethinkOverflowItem(strings.deleteDescription, MaterialSymbols.Filled.DeleteOutline) { showDeleteDialog = true },
                    ),
                )
            }
            // Keys, addresses and endpoints are copied into other clients: selectable, monospace.
            val field: @Composable (String, String) -> Unit = { label, value ->
                RethinkKeyValueRow(label, value, layout = RethinkKeyValueLayout.Stacked, monospace = true, selectable = true)
            }
            field(strings.publicKey, item.publicKey)
            item.allowedIps?.takeIf { it.isNotBlank() }?.let { field(strings.allowedIps, it) }
            item.endpoint?.takeIf { it.isNotBlank() }?.let { field(strings.endpoint, it) }
            item.persistentKeepalive?.takeIf { it.isNotBlank() }?.let { field(strings.persistentKeepalive, it) }
        }
    }
    if (showDeleteDialog) {
        RethinkConfirmDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = strings.deleteTitle,
            message = strings.deleteMessage,
            confirmText = strings.deleteConfirm,
            dismissText = strings.cancel,
            isConfirmDestructive = true,
            onConfirm = { showDeleteDialog = false; onDelete() },
            onDismiss = { showDeleteDialog = false },
        )
    }
}
