/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.wireguard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SharedDimensions.spacingMd)) {
        Surface(
            shape = RoundedCornerShape(SharedDimensions.cornerRadius3xl),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
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
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text(strings.dismiss) }
            Spacer(Modifier.width(SharedDimensions.spacingSm))
            Button(onClick = { onSave(state) }) { Text(strings.save) }
        }
    }
}

@Composable
private fun RethinkPeerTextField(
    value: String,
    label: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}
