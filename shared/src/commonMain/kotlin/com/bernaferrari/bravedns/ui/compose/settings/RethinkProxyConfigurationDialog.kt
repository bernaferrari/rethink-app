/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormActionRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormTextField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkRadioChoiceList
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

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
    ) {
        Text(strings.title, style = MaterialTheme.typography.headlineSmall)
        Column(
            modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            strings.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            RethinkProxyTextField(state.host, strings.host) { onStateChange(state.copy(host = it, error = null)) }
            if (kind == RethinkProxyConfigurationKind.Socks5) {
                RethinkProxyTextField(state.port, strings.port) { onStateChange(state.copy(port = it, error = null)) }
                RethinkProxyTextField(state.username, strings.username) { onStateChange(state.copy(username = it, error = null)) }
                RethinkProxyTextField(state.password, strings.password) { onStateChange(state.copy(password = it, error = null)) }
            }
            Text(strings.appDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RethinkProxyAppSelector(
                selectedId = state.selectedAppId,
                apps = state.apps,
                onSelected = { onStateChange(state.copy(selectedAppId = it, error = null)) },
                appIcon = appIcon,
            )
            if (kind == RethinkProxyConfigurationKind.Socks5) {
                RethinkProxyToggleRow(
                    label = strings.udpBlocked,
                    checked = state.udpBlocked,
                    onCheckedChange = { onStateChange(state.copy(udpBlocked = it, error = null)) },
                )
            }
            RethinkProxyToggleRow(
                label = strings.includeProxyApps,
                checked = state.includeProxyApps,
                enabled = !state.lockdown,
                onCheckedChange = { onStateChange(state.copy(includeProxyApps = it, error = null)) },
            )
            if (state.lockdown) {
                Text(
                    strings.lockdownDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = if (onLockdownInfo == null) Modifier else Modifier.clickable(onClick = onLockdownInfo),
                )
            }
            state.error?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        RethinkFormActionRow(
            confirmLabel = strings.save,
            onConfirm = onConfirm,
            dismissLabel = strings.cancel,
            onDismiss = onCancel,
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
private fun RethinkProxyToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, modifier = Modifier.weight(1f))
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    }
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
    val selectorShape = MaterialTheme.shapes.medium
    Box(Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            enabled = apps.isNotEmpty(),
            shape = selectorShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(SharedDimensions.dividerThicknessBold, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
            // Compose Web's hover indication can otherwise paint outside the rounded selector.
            modifier = Modifier.fillMaxWidth().clip(selectorShape),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            ) {
                selected?.let { option -> appIcon?.invoke(option, SharedDimensions.iconSizeMd) }
                Text(
                    selected?.label.orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(MaterialSymbols.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            apps.forEach { option ->
                DropdownMenuItem(
                    leadingIcon = { appIcon?.invoke(option, SharedDimensions.iconSizeSm) },
                    text = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { expanded = false; onSelected(option.id) },
                )
            }
        }
    }
}
