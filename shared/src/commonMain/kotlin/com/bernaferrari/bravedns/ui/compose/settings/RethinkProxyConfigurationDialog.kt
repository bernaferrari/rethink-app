/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormActionRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormTextField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkRadioChoiceList
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor

enum class RethinkProxyConfigurationKind { Socks5, Http }

data class RethinkProxyAppOption(val id: String, val label: String)

data class RethinkProxyConfigurationState(
    val host: String,
    val port: String = "",
    val username: String = "",
    val password: String = "",
    val selectedAppId: String = "",
    val apps: List<RethinkProxyAppOption> = emptyList(),
    val udpBlocked: Boolean = false,
    val includeProxyApps: Boolean = true,
    val lockdown: Boolean = false,
    val error: String? = null,
)

data class RethinkProxyConfigurationStrings(
    val title: String,
    val description: String? = null,
    val host: String,
    val port: String,
    val username: String,
    val password: String,
    val appDescription: String,
    val udpBlocked: String,
    val includeProxyApps: String,
    val lockdownDescription: String,
    val save: String,
    val cancel: String,
)

data class RethinkProxyModeOption(val id: String, val label: String)

/** Shared SOCKS5/HTTP configuration form. Hosts own validation, installed app artwork, and persistence. */
@Composable
fun RethinkProxyConfigurationDialog(
    kind: RethinkProxyConfigurationKind,
    state: RethinkProxyConfigurationState,
    strings: RethinkProxyConfigurationStrings,
    onStateChange: (RethinkProxyConfigurationState) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onLockdownInfo: (() -> Unit)? = null,
    appIcon: @Composable ((RethinkProxyAppOption, Dp) -> Unit)? = null,
) {
    RethinkModalBottomSheet(
        onDismissRequest = onCancel,
        verticalSpacing = SharedDimensions.spacingLg,
        expandOnShow = true,
    ) { dismissSheet ->
        Text(strings.title, style = MaterialTheme.typography.headlineSmall)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            strings.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            RethinkProxyTextField(state.host, strings.host) { onStateChange(state.copy(host = it, error = null)) }
            if (kind == RethinkProxyConfigurationKind.Socks5) {
                RethinkProxyTextField(state.port, strings.port) { onStateChange(state.copy(port = it, error = null)) }
                RethinkProxyTextField(state.username, strings.username) { onStateChange(state.copy(username = it, error = null)) }
                RethinkProxyPasswordField(state.password, strings.password) { onStateChange(state.copy(password = it, error = null)) }
            }
            Text(strings.appDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RethinkProxyAppSelector(
                selectedId = state.selectedAppId,
                apps = state.apps,
                onSelected = { onStateChange(state.copy(selectedAppId = it, error = null)) },
                appIcon = appIcon,
            )
            val toggleCount = if (kind == RethinkProxyConfigurationKind.Socks5) 2 else 1
            RethinkListGroup {
                if (kind == RethinkProxyConfigurationKind.Socks5) {
                    RethinkProxyToggleRow(
                        label = strings.udpBlocked,
                        checked = state.udpBlocked,
                        position = cardPositionFor(0, toggleCount - 1),
                        onCheckedChange = { onStateChange(state.copy(udpBlocked = it, error = null)) },
                    )
                }
                RethinkProxyToggleRow(
                    label = strings.includeProxyApps,
                    checked = state.includeProxyApps,
                    enabled = !state.lockdown,
                    position = cardPositionFor(toggleCount - 1, toggleCount - 1),
                    onCheckedChange = { onStateChange(state.copy(includeProxyApps = it, error = null)) },
                )
            }
            if (state.lockdown) {
                Text(
                    strings.lockdownDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = if (onLockdownInfo == null) {
                        Modifier
                    } else {
                        Modifier
                            .heightIn(min = SharedDimensions.touchTargetMin)
                            .clickable(onClick = onLockdownInfo)
                    },
                )
            }
            state.error?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        RethinkFormActionRow(
            confirmLabel = strings.save,
            onConfirm = onConfirm,
            dismissLabel = strings.cancel,
            onDismiss = dismissSheet,
        )
    }
}

/** Shared radio choice dialog used for selecting the Orbot proxy mode. */
@Composable
fun RethinkProxyModeDialog(
    title: String,
    options: List<RethinkProxyModeOption>,
    selectedId: String,
    save: String,
    cancel: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = title,
        text = {
            RethinkRadioChoiceList(
                options = options,
                selected = { it.id == selectedId },
                label = RethinkProxyModeOption::label,
                onSelected = { onSelected(it.id) },
            )
        },
        confirmText = save,
        dismissText = cancel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun RethinkProxyTextField(value: String, label: String, onValueChange: (String) -> Unit) {
    RethinkFormTextField(value, onValueChange, label, singleLine = true)
}

@Composable
private fun RethinkProxyPasswordField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(SharedDimensions.cornerRadiusLg),
    )
}

@Composable
private fun RethinkProxyToggleRow(
    label: String,
    checked: Boolean,
    position: CardPosition,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    RethinkListItem(
        headline = label,
        position = position,
        enabled = enabled,
        trailing = { Switch(checked = checked, enabled = enabled, onCheckedChange = null) },
        onClick = { onCheckedChange(!checked) },
    )
}

@Composable
private fun RethinkProxyAppSelector(
    selectedId: String,
    apps: List<RethinkProxyAppOption>,
    onSelected: (String) -> Unit,
    appIcon: @Composable ((RethinkProxyAppOption, Dp) -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = apps.firstOrNull { it.id == selectedId } ?: apps.firstOrNull()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (apps.isNotEmpty()) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = apps.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = apps.isNotEmpty()),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            leadingIcon = selected?.let { option ->
                { appIcon?.invoke(option, SharedDimensions.iconSizeMd) }
            },
            shape = RoundedCornerShape(SharedDimensions.cornerRadiusLg),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            apps.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    leadingIcon = { appIcon?.invoke(option, SharedDimensions.iconSizeSm) },
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelected(option.id)
                    },
                )
            }
        }
    }
}
