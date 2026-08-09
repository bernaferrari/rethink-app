/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.wireguard

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.bernaferrari.bravedns.ui.compose.theme.PrimaryButton
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormSection
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormTextField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SecondaryButton
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

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
                RethinkFormSection(strings.configuration) {
                    RethinkFormTextField(
                        value = state.interfaceName,
                        onValueChange = { onStateChange(state.copy(interfaceName = it)) },
                        label = strings.name,
                        singleLine = true,
                    )
                    RethinkFormTextField(
                        value = state.addresses,
                        onValueChange = { onStateChange(state.copy(addresses = it)) },
                        label = strings.addresses,
                        minLines = 2,
                    )
                    RethinkFormTextField(
                        value = state.dnsServers,
                        onValueChange = { onStateChange(state.copy(dnsServers = it)) },
                        label = strings.dnsServers,
                        minLines = 2,
                    )
                }
            }
            item {
                RethinkFormSection(strings.setup) {
                    RethinkFormTextField(
                        value = state.privateKey,
                        onValueChange = { onStateChange(state.copy(privateKey = it)) },
                        label = strings.privateKey,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        trailingIcon = { IconButton(onClick = onGenerateKeys) { Icon(MaterialSymbols.Filled.Refresh, strings.generateKeys) } },
                    )
                    RethinkFormTextField(
                        value = state.publicKey,
                        onValueChange = {},
                        label = strings.publicKey,
                        modifier = Modifier
                            .clip(RoundedCornerShape(SharedDimensions.cornerRadiusLg))
                            .clickable(enabled = state.publicKey.isNotEmpty(), onClick = onCopyPublicKey),
                        readOnly = true,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        trailingIcon = {
                            IconButton(onClick = onCopyPublicKey, enabled = state.publicKey.isNotEmpty()) {
                                Icon(MaterialSymbols.Filled.ContentCopy, strings.copyPublicKey)
                            }
                        },
                    )
                }
            }
            item {
                RethinkFormSection(strings.network) {
                    if (state.showListenPort) {
                        RethinkFormTextField(
                            value = state.listenPort,
                            onValueChange = { onStateChange(state.copy(listenPort = it)) },
                            label = strings.listenPort,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    RethinkFormTextField(
                        value = state.mtu,
                        onValueChange = { onStateChange(state.copy(mtu = it)) },
                        label = strings.mtu,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            if (state.advancedProperties.isNotEmpty()) {
                item {
                    RethinkFormSection(strings.advanced) {
                        Text(state.advancedProperties, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
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
