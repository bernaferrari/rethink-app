/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.celzero.bravedns.ui.compose.wireguard

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.celzero.bravedns.ui.compose.components.RethinkSharedIconContainer
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import com.celzero.bravedns.ui.compose.theme.cardPositionFor

enum class RethinkWireguardSsidType(val connects: Boolean, val exact: Boolean) {
    EqualExact(true, true),
    EqualWildcard(true, false),
    NotEqualExact(false, true),
    NotEqualWildcard(false, false),
}

data class RethinkWireguardSsidRule(
    val name: String,
    val type: RethinkWireguardSsidType,
)

data class RethinkWireguardSsidEditorStrings(
    val title: String,
    val action: String,
    val criteria: String,
    val ssid: String,
    val connect: String,
    val pause: String,
    val exact: String,
    val wildcard: String,
    val add: String,
    val save: String,
    val cancel: String,
    val delete: String,
    val invalidName: String,
    val description: (action: String, criteria: String) -> String,
)

/** Portable editor for Wi-Fi rules; hosts convert its rules to and from their persistence format. */
@Composable
fun RethinkWireguardSsidEditor(
    initialRules: List<RethinkWireguardSsidRule>,
    strings: RethinkWireguardSsidEditorStrings,
    onSave: (List<RethinkWireguardSsidRule>) -> Unit,
    onDismiss: () -> Unit,
    onValidationError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rules = remember(initialRules) { mutableStateListOf<RethinkWireguardSsidRule>().apply { addAll(initialRules) } }
    var input by remember { mutableStateOf("") }
    var connects by remember { mutableStateOf(true) }
    var exact by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<RethinkWireguardSsidRule?>(null) }
    val enabled = input.isNotBlank()
    val selectedAction = if (connects) strings.connect else strings.pause
    val selectedCriteria = if (exact) strings.exact else strings.wildcard

    deleteTarget?.let { rule ->
        RethinkConfirmDialog(
            onDismissRequest = { deleteTarget = null },
            title = strings.delete,
            message = "${strings.delete} ${rule.name}",
            confirmText = strings.delete,
            dismissText = strings.cancel,
            onConfirm = { rules.remove(rule); deleteTarget = null },
            onDismiss = { deleteTarget = null },
            isConfirmDestructive = true,
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
            Text(strings.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                strings.description(selectedAction, selectedCriteria),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (rules.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = SharedDimensions.heroCornerRadius * 10),
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
            ) {
                items(rules, key = { "${it.name}_${it.type.name}" }) { rule ->
                    RethinkListItem(
                        headline = rule.name,
                        supporting = "${if (rule.type.connects) strings.connect else strings.pause} · ${if (rule.type.exact) strings.exact else strings.wildcard}",
                        position = cardPositionFor(rules.indexOf(rule), rules.lastIndex),
                        leadingContent = {
                            RethinkSharedIconContainer(MaterialTheme.colorScheme.primary) {
                                Icon(MaterialSymbols.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        trailing = {
                            IconButton(onClick = { deleteTarget = rule }) {
                                Icon(MaterialSymbols.Filled.Delete, strings.delete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
            RethinkSsidOptionGroup(
                label = strings.action,
                firstLabel = strings.connect,
                secondLabel = strings.pause,
                firstSelected = connects,
                onFirstSelected = { connects = true },
                onSecondSelected = { connects = false },
            )
            RethinkSsidOptionGroup(
                label = strings.criteria,
                firstLabel = strings.exact,
                secondLabel = strings.wildcard,
                firstSelected = exact,
                onFirstSelected = { exact = true },
                onSecondSelected = { exact = false },
            )
            TextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(strings.ssid) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadiusMdLg),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Button(
                onClick = {
                    val name = input.trim()
                    if (name.isBlank() || name.length > 32) {
                        onValidationError(strings.invalidName)
                        return@Button
                    }
                    val type = when {
                        connects && exact -> RethinkWireguardSsidType.EqualExact
                        connects -> RethinkWireguardSsidType.EqualWildcard
                        exact -> RethinkWireguardSsidType.NotEqualExact
                        else -> RethinkWireguardSsidType.NotEqualWildcard
                    }
                    val equalIndex = rules.indexOfFirst { it.name.equals(name, ignoreCase = true) && it.type == type }
                    if (equalIndex < 0) {
                        rules.removeAll { it.name.equals(name, ignoreCase = true) }
                        rules.add(RethinkWireguardSsidRule(name, type))
                    }
                    input = ""
                    connects = true
                    exact = false
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(strings.add) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
            Spacer(Modifier.width(SharedDimensions.spacingSm))
            Button(onClick = { onSave(rules.toList()) }) { Text(strings.save) }
        }
    }
}

@Composable
private fun RethinkSsidOptionGroup(
    label: String,
    firstLabel: String,
    secondLabel: String,
    firstSelected: Boolean,
    onFirstSelected: () -> Unit,
    onSecondSelected: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            RethinkSsidToggle(
                label = firstLabel,
                selected = firstSelected,
                onSelected = onFirstSelected,
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                modifier = Modifier.weight(1f),
            )
            RethinkSsidToggle(
                label = secondLabel,
                selected = !firstSelected,
                onSelected = onSecondSelected,
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RethinkSsidToggle(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
    shapes: androidx.compose.material3.ToggleButtonShapes,
    modifier: Modifier,
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { if (it && !selected) onSelected() },
        shapes = shapes,
        modifier = modifier,
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
