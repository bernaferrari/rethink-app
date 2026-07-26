/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.wireguard

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.dns.RethinkEndpointFeed
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

enum class RethinkWgTab { One, General }

data class RethinkWireguardStrings(
    val title: String,
    val oneTab: String,
    val generalTab: String,
    val create: String,
    val import: String,
    val qrCode: String,
    val noConfigurations: String,
    val disableTitle: String,
    val disableMessage: String,
    val disableConfirm: String,
    val cancel: String,
)

/** Full target-neutral WireGuard landing screen; hosts retain config operations and row behavior. */
@Composable
fun <T> RethinkWireguardScreen(
    selectedTab: RethinkWgTab,
    overview: String,
    isEmpty: Boolean,
    configs: RethinkEndpointFeed<T>,
    strings: RethinkWireguardStrings,
    bottomInset: Dp,
    confirmDisable: Boolean,
    onBackClick: () -> Unit,
    onTabClick: (RethinkWgTab) -> Unit,
    onDismissDisable: () -> Unit,
    onConfirmDisable: () -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onQrCode: () -> Unit,
    modifier: Modifier = Modifier,
    configRow: @Composable (T, RethinkWgTab) -> Unit,
) {
    if (confirmDisable) {
        RethinkConfirmDialog(
            onDismissRequest = onDismissDisable,
            title = strings.disableTitle,
            message = strings.disableMessage,
            confirmText = strings.disableConfirm,
            dismissText = strings.cancel,
            onConfirm = onConfirmDisable,
            onDismiss = onDismissDisable,
        )
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { RethinkLargeTopBar(strings.title, onBackClick = onBackClick) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isEmpty) {
                RethinkWgEmptyState(strings.noConfigurations, bottomInset)
            } else {
                Column(Modifier.fillMaxSize()) {
                    RethinkWgOverview(overview)
                    RethinkWgTabs(selectedTab, strings, onTabClick)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = SharedDimensions.screenPaddingHorizontal,
                            end = SharedDimensions.screenPaddingHorizontal,
                            top = SharedDimensions.spacingSm,
                            bottom = 84.dp + bottomInset,
                        ),
                    ) {
                        items(configs.itemCount) { index ->
                            val config = configs[index] ?: return@items
                            configRow(config, selectedTab)
                        }
                    }
                }
            }
            RethinkWgActions(
                modifier = Modifier.align(Alignment.BottomCenter).padding(
                    start = SharedDimensions.screenPaddingHorizontal,
                    end = SharedDimensions.screenPaddingHorizontal,
                    bottom = bottomInset + SharedDimensions.spacingMd,
                ),
                strings = strings,
                onCreate = onCreate,
                onImport = onImport,
                onQrCode = onQrCode,
            )
        }
    }
}

@Composable
private fun RethinkWgTabs(selected: RethinkWgTab, strings: RethinkWireguardStrings, onClick: (RethinkWgTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(
            start = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingLg,
            end = SharedDimensions.screenPaddingHorizontal,
        ),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        RethinkWgTabToggle(
            label = strings.oneTab,
            checked = selected == RethinkWgTab.One,
            onCheckedChange = { onClick(RethinkWgTab.One) },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
        )
        RethinkWgTabToggle(
            label = strings.generalTab,
            checked = selected == RethinkWgTab.General,
            onCheckedChange = { onClick(RethinkWgTab.General) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
        )
    }
}

@Composable
private fun RowScope.RethinkWgTabToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    shapes: androidx.compose.material3.ToggleButtonShapes,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { isChecked -> if (isChecked && !checked) onCheckedChange() },
        shapes = shapes,
        colors = ToggleButtonDefaults.toggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = null,
        modifier = Modifier.weight(1f),
    ) {
        Text(label)
    }
}

@Composable
private fun RethinkWgOverview(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingMd,
        ),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius2xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.fillMaxWidth().padding(SharedDimensions.cardPadding), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(MaterialSymbols.Filled.VpnKey, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RethinkWgEmptyState(message: String, bottomInset: Dp) {
    Surface(
        modifier = Modifier.fillMaxSize().padding(
            start = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingXl,
            end = SharedDimensions.screenPaddingHorizontal,
            bottom = SharedDimensions.spacingXl + bottomInset,
        ),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius2xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
    ) {
        Column(Modifier.fillMaxSize().padding(SharedDimensions.spacingXl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadius5xl), color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(MaterialSymbols.Filled.VpnKey, null, Modifier.size(112.dp).padding(SharedDimensions.spacing2xl), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.height(SharedDimensions.spacingLg))
            Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RethinkWgActions(
    modifier: Modifier,
    strings: RethinkWireguardStrings,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onQrCode: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ExtendedFloatingActionButton(
            onClick = onCreate,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 1.dp,
                pressedElevation = 1.dp,
            ),
        ) {
            Icon(MaterialSymbols.Filled.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(strings.create)
        }
        Box {
            IconButton(
                onClick = { expanded = true },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(MaterialSymbols.Filled.KeyboardArrowDown, strings.import)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(strings.import) }, onClick = { expanded = false; onImport() })
                DropdownMenuItem(text = { Text(strings.qrCode) }, onClick = { expanded = false; onQrCode() })
            }
        }
    }
}
