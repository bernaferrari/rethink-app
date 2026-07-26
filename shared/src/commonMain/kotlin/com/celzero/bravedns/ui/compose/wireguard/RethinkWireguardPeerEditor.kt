/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.wireguard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.celzero.bravedns.ui.compose.theme.RethinkFormActionRow
import com.celzero.bravedns.ui.compose.theme.RethinkFormTextField
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

data class RethinkWireguardPeerState(
    val publicKey: String = "",
    val presharedKey: String = "",
    val allowedIps: String = "",
    val endpoint: String = "",
    val persistentKeepalive: String = "",
)

data class RethinkWireguardPeerEditorStrings(
    val title: String,
    val publicKey: String,
    val presharedKey: String,
    val persistentKeepalive: String,
    val endpoint: String,
    val allowedIps: String,
    val save: String,
    val dismiss: String,
)

/** Full portable peer editor; hosts own WireGuard parsing, persistence, and error reporting. */
@Composable
fun RethinkWireguardPeerEditor(
    initialState: RethinkWireguardPeerState,
    strings: RethinkWireguardPeerEditorStrings,
    onSave: (RethinkWireguardPeerState) -> Unit,
    onDismiss: () -> Unit,
    keepaliveHint: (String) -> String? = { null },
    modifier: Modifier = Modifier,
) {
    var state by remember(initialState) { mutableStateOf(initialState) }
    val keepaliveSupportingText = remember(state.persistentKeepalive) { keepaliveHint(state.persistentKeepalive) }
    Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SharedDimensions.spacingLg)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            Text(strings.title, style = MaterialTheme.typography.titleLarge)
            RethinkPeerTextField(state.publicKey, strings.publicKey, KeyboardType.Password) { state = state.copy(publicKey = it) }
            RethinkPeerTextField(state.presharedKey, strings.presharedKey, KeyboardType.Password) { state = state.copy(presharedKey = it) }
            RethinkPeerTextField(state.persistentKeepalive, strings.persistentKeepalive, KeyboardType.Number) { state = state.copy(persistentKeepalive = it) }
            if (!keepaliveSupportingText.isNullOrBlank()) {
                Text(keepaliveSupportingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RethinkPeerTextField(state.endpoint, strings.endpoint, KeyboardType.Password) { state = state.copy(endpoint = it) }
            RethinkPeerTextField(state.allowedIps, strings.allowedIps, KeyboardType.Text) { state = state.copy(allowedIps = it) }
        }
        RethinkFormActionRow(
            confirmLabel = strings.save,
            onConfirm = { onSave(state) },
            dismissLabel = strings.dismiss,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun RethinkPeerTextField(
    value: String,
    label: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
) {
    RethinkFormTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
    )
}
