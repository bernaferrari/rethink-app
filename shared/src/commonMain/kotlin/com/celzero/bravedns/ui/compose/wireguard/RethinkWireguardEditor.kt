/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.wireguard

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.PrimaryButton
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.SecondaryButton
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

data class RethinkWireguardEditorState(
    val interfaceName: String = "",
    val privateKey: String = "",
    val publicKey: String = "",
    val addresses: String = "",
    val listenPort: String = "",
    val dnsServers: String = "",
    val mtu: String = "",
    val advancedProperties: String = "",
    val showListenPort: Boolean = false,
)

data class RethinkWireguardEditorStrings(
    val title: String,
    val configuration: String,
    val setup: String,
    val network: String,
    val advanced: String,
    val name: String,
    val addresses: String,
    val dnsServers: String,
    val privateKey: String,
    val publicKey: String,
    val listenPort: String,
    val mtu: String,
    val generateKeys: String,
    val copyPublicKey: String,
    val cancel: String,
    val save: String,
)

/** Target-neutral WireGuard interface editor. Hosts own key creation, clipboard access, and saving. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkWireguardEditor(
    state: RethinkWireguardEditorState,
    strings: RethinkWireguardEditorStrings,
    actionBottomInset: Dp,
    onStateChange: (RethinkWireguardEditorState) -> Unit,
    onBackClick: () -> Unit,
    onGenerateKeys: () -> Unit,
    onCopyPublicKey: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val fieldShape = RoundedCornerShape(SharedDimensions.cornerRadiusLg)
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { RethinkLargeTopBar(strings.title, onBackClick = onBackClick, scrollBehavior = scrollBehavior) },
        bottomBar = {
            RethinkWireguardEditorActions(
                bottomInset = actionBottomInset,
                cancelLabel = strings.cancel,
                saveLabel = strings.save,
                onCancelClick = onBackClick,
                onSaveClick = onSaveClick,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingSm,
                bottom = SharedDimensions.spacingXl,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            item {
                RethinkWireguardEditorSection(strings.configuration) {
                    OutlinedTextField(
                        value = state.interfaceName,
                        onValueChange = { onStateChange(state.copy(interfaceName = it)) },
                        label = { Text(strings.name) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.addresses,
                        onValueChange = { onStateChange(state.copy(addresses = it)) },
                        label = { Text(strings.addresses) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        minLines = 2,
                    )
                    OutlinedTextField(
                        value = state.dnsServers,
                        onValueChange = { onStateChange(state.copy(dnsServers = it)) },
                        label = { Text(strings.dnsServers) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        minLines = 2,
                    )
                }
            }
            item { RethinkWireguardEditorDivider() }
            item {
                RethinkWireguardEditorSection(strings.setup) {
                    OutlinedTextField(
                        value = state.privateKey,
                        onValueChange = { onStateChange(state.copy(privateKey = it)) },
                        label = { Text(strings.privateKey) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        trailingIcon = { IconButton(onClick = onGenerateKeys) { Icon(MaterialSymbols.Filled.Refresh, strings.generateKeys) } },
                    )
                    OutlinedTextField(
                        value = state.publicKey,
                        onValueChange = {},
                        label = { Text(strings.publicKey) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(fieldShape)
                            .clickable(enabled = state.publicKey.isNotEmpty(), onClick = onCopyPublicKey),
                        shape = fieldShape,
                        readOnly = true,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        trailingIcon = {
                            IconButton(onClick = onCopyPublicKey, enabled = state.publicKey.isNotEmpty()) {
                                Icon(MaterialSymbols.Filled.ContentCopy, strings.copyPublicKey)
                            }
                        },
                    )
                }
            }
            item { RethinkWireguardEditorDivider() }
            item {
                RethinkWireguardEditorSection(strings.network) {
                    if (state.showListenPort) {
                        OutlinedTextField(
                            value = state.listenPort,
                            onValueChange = { onStateChange(state.copy(listenPort = it)) },
                            label = { Text(strings.listenPort) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = fieldShape,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    OutlinedTextField(
                        value = state.mtu,
                        onValueChange = { onStateChange(state.copy(mtu = it)) },
                        label = { Text(strings.mtu) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            if (state.advancedProperties.isNotEmpty()) {
                item { RethinkWireguardEditorDivider() }
                item {
                    RethinkWireguardEditorSection(strings.advanced) {
                        Text(state.advancedProperties, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RethinkWireguardEditorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun RethinkWireguardEditorDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun RethinkWireguardEditorActions(
    bottomInset: Dp,
    cancelLabel: String,
    saveLabel: String,
    onCancelClick: () -> Unit,
    onSaveClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    start = SharedDimensions.screenPaddingHorizontal,
                    end = SharedDimensions.screenPaddingHorizontal,
                    top = SharedDimensions.spacingSm,
                    bottom = SharedDimensions.spacingSm + bottomInset,
                ),
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
            ) {
                SecondaryButton(cancelLabel, onCancelClick, Modifier.weight(1f))
                PrimaryButton(saveLabel, onSaveClick, Modifier.weight(1f))
            }
        }
    }
}
