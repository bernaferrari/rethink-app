/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormActionRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormTextField
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

data class RethinkEndpointEditorStrings(
    val cancel: String,
    val add: String,
)

data class RethinkDnsProxyEditorStrings(
    val title: String,
    val lockdownMessage: String,
    val app: String,
    val name: String,
    val ipAddress: String,
    val port: String,
    val excludeApps: String,
    val cancel: String,
    val add: String,
)

data class RethinkDnsProxyEditorInput(
    val name: String,
    val appName: String,
    val ipAddress: String,
    val port: String,
    val excludeApps: Boolean,
)

/**
 * Shared editor sheet for endpoints. These multi-field, reversible edits are deliberately a
 * bottom sheet: it preserves the list context and avoids a floating, elevated form competing
 * with the underlying destination.
 */
@Composable
fun RethinkEndpointEditorDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    RethinkModalBottomSheet(
        onDismissRequest = onDismiss,
        contentPadding = PaddingValues(0.dp),
        verticalSpacing = 0.dp,
        includeBottomSpacer = false,
        expandOnShow = true,
    ) {
        content()
    }
}

/**
 * Keeps endpoint-editor actions fixed at the bottom of the sheet.  The fields are the only
 * scrollable region, so a longer DNS form never hides Cancel/Add behind the gesture area.
 */
@Composable
private fun RethinkEndpointEditorForm(
    title: String,
    error: String,
    strings: RethinkEndpointEditorStrings,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(SharedDimensions.screenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSmMd),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSmMd),
            content = content,
        )
        if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        RethinkEditorActions(strings, onDismiss, onConfirm)
    }
}

/** Reusable target-neutral editor for DoH and DoT endpoints. */
@Composable
fun RethinkUrlEndpointEditor(
    title: String,
    nameLabel: String,
    endpointLabel: String,
    defaultName: String,
    initialEndpoint: String,
    insecureLabel: String,
    strings: RethinkEndpointEditorStrings,
    onSubmit: (name: String, endpoint: String, isSecure: Boolean) -> String?,
    onDismiss: () -> Unit,
) {
    var name by remember(defaultName) { mutableStateOf(defaultName) }
    var endpoint by remember(initialEndpoint) { mutableStateOf(initialEndpoint) }
    var insecure by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    RethinkEndpointEditorForm(
        title = title,
        error = error,
        strings = strings,
        onDismiss = onDismiss,
        onConfirm = { error = onSubmit(name, endpoint, !insecure).orEmpty() },
    ) {
        RethinkFormTextField(name, onValueChange = { name = it }, label = nameLabel)
        RethinkFormTextField(endpoint, onValueChange = { endpoint = it }, label = endpointLabel, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(insecure, onCheckedChange = { insecure = it })
            Spacer(Modifier.width(6.dp))
            Text(insecureLabel)
        }
    }
}

/** Target-neutral ODoH editor. URL validity and storage remain host responsibilities. */
@Composable
fun RethinkODoHEndpointEditor(
    title: String,
    nameLabel: String,
    proxyLabel: String,
    resolverLabel: String,
    defaultName: String,
    initialResolver: String,
    strings: RethinkEndpointEditorStrings,
    onSubmit: (name: String, proxy: String, resolver: String) -> String?,
    onDismiss: () -> Unit,
) {
    var name by remember(defaultName) { mutableStateOf(defaultName) }
    var proxy by remember { mutableStateOf("") }
    var resolver by remember(initialResolver) { mutableStateOf(initialResolver) }
    var error by remember { mutableStateOf("") }
    RethinkEndpointEditorForm(
        title = title,
        error = error,
        strings = strings,
        onDismiss = onDismiss,
        onConfirm = { error = onSubmit(name, proxy, resolver).orEmpty() },
    ) {
        RethinkFormTextField(name, onValueChange = { name = it }, label = nameLabel)
        RethinkFormTextField(proxy, onValueChange = { proxy = it }, label = proxyLabel, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        RethinkFormTextField(resolver, onValueChange = { resolver = it }, label = resolverLabel, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    }
}

/** Shared DNS-proxy form; Android retains its app list, VPN settings intent, and database update. */
@Composable
fun RethinkDnsProxyEndpointEditor(
    appNames: List<String>,
    defaultName: String,
    defaultIpAddress: String,
    initialExcludeApps: Boolean,
    isLockdown: Boolean,
    strings: RethinkDnsProxyEditorStrings,
    onOpenVpnProfile: () -> Unit,
    onSubmit: (RethinkDnsProxyEditorInput) -> String?,
    onDismiss: () -> Unit,
) {
    var selectedAppIndex by remember(appNames) { mutableStateOf(0) }
    var appMenuExpanded by remember { mutableStateOf(false) }
    var name by remember(defaultName) { mutableStateOf(defaultName) }
    var ipAddress by remember(defaultIpAddress) { mutableStateOf(defaultIpAddress) }
    var port by remember { mutableStateOf("") }
    var excludeApps by remember(initialExcludeApps) { mutableStateOf(initialExcludeApps) }
    var error by remember { mutableStateOf("") }
    RethinkEndpointEditorForm(
        title = strings.title,
        error = error,
        strings = RethinkEndpointEditorStrings(strings.cancel, strings.add),
        onDismiss = onDismiss,
        onConfirm = {
            error = onSubmit(
                RethinkDnsProxyEditorInput(
                    name,
                    appNames.getOrNull(selectedAppIndex).orEmpty(),
                    ipAddress,
                    port,
                    excludeApps,
                ),
            ).orEmpty()
        },
    ) {
        if (isLockdown) TextButton(onClick = onOpenVpnProfile) { Text(strings.lockdownMessage) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.app, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.3f))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(0.7f)) {
                TextButton(onClick = { appMenuExpanded = true }) { Text(appNames.getOrNull(selectedAppIndex).orEmpty()) }
                DropdownMenu(expanded = appMenuExpanded, onDismissRequest = { appMenuExpanded = false }) {
                    appNames.forEachIndexed { index, appName ->
                        DropdownMenuItem(text = { Text(appName) }, onClick = { selectedAppIndex = index; appMenuExpanded = false })
                    }
                }
            }
        }
        RethinkFormTextField(name, onValueChange = { name = it }, label = strings.name)
        RethinkFormTextField(ipAddress, onValueChange = { ipAddress = it }, label = strings.ipAddress)
        RethinkFormTextField(port, onValueChange = { port = it }, label = strings.port, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(strings.excludeApps)
            Checkbox(excludeApps, onCheckedChange = { if (!isLockdown) excludeApps = it }, enabled = !isLockdown)
        }
    }
}

enum class RethinkDnsCryptEndpointKind { Resolver, Relay }

/** Target-neutral DNSCrypt resolver/relay form. */
@Composable
fun RethinkDnsCryptEndpointEditor(
    title: String,
    resolverLabel: String,
    relayLabel: String,
    nameLabel: String,
    stampLabel: String,
    descriptionLabel: String,
    resolverDefaultName: String,
    relayDefaultName: String,
    invalidInputMessage: String,
    strings: RethinkEndpointEditorStrings,
    onSubmit: (RethinkDnsCryptEndpointKind, name: String, stamp: String, description: String) -> String?,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(RethinkDnsCryptEndpointKind.Resolver) }
    var name by remember(resolverDefaultName) { mutableStateOf(resolverDefaultName) }
    var stamp by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val defaultName = if (kind == RethinkDnsCryptEndpointKind.Resolver) resolverDefaultName else relayDefaultName
    RethinkEndpointEditorForm(
        title = title,
        error = error,
        strings = strings,
        onDismiss = onDismiss,
        onConfirm = {
            error = if (name.isBlank() || stamp.isBlank()) {
                invalidInputMessage
            } else {
                onSubmit(kind, name.ifBlank { defaultName }, stamp, description).orEmpty()
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            val resolverSelected = kind == RethinkDnsCryptEndpointKind.Resolver
            ToggleButton(
                checked = resolverSelected,
                onCheckedChange = { checked ->
                    if (checked && !resolverSelected) {
                        kind = RethinkDnsCryptEndpointKind.Resolver
                        name = resolverDefaultName
                    }
                },
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                colors = ToggleButtonDefaults.toggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = null,
                modifier = Modifier.weight(1f),
            ) { Text(resolverLabel) }
            val relaySelected = kind == RethinkDnsCryptEndpointKind.Relay
            ToggleButton(
                checked = relaySelected,
                onCheckedChange = { checked ->
                    if (checked && !relaySelected) {
                        kind = RethinkDnsCryptEndpointKind.Relay
                        name = relayDefaultName
                    }
                },
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                colors = ToggleButtonDefaults.toggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = null,
                modifier = Modifier.weight(1f),
            ) { Text(relayLabel) }
        }
        RethinkFormTextField(name, onValueChange = { name = it }, label = nameLabel)
        RethinkFormTextField(stamp, onValueChange = { stamp = it }, label = stampLabel)
        RethinkFormTextField(description, onValueChange = { description = it }, label = descriptionLabel)
    }
}

@Composable
private fun RethinkEditorActions(strings: RethinkEndpointEditorStrings, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    RethinkFormActionRow(
        confirmLabel = strings.add,
        onConfirm = onConfirm,
        dismissLabel = strings.cancel,
        onDismiss = onDismiss,
    )
}
