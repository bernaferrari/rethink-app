/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.wireguard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.dns.RethinkEndpointFeed
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWell
import com.bernaferrari.bravedns.ui.compose.theme.RethinkContainedCard

enum class RethinkWgTab { One, General }

data class RethinkWireguardStrings(
    val title: String,
    val oneTab: String,
    val generalTab: String,
    val create: String,
    val import: String,
    val qrCode: String,
    /** Content description of the FAB-menu toggle, e.g. "Add configuration". */
    val addConfiguration: String,
    val expanded: String,
    val collapsed: String,
    val noConfigurations: String,
    val disableTitle: String,
    val disableMessage: String,
    val disableConfirm: String,
    val cancel: String,
)

/** Full target-neutral WireGuard landing screen; hosts retain config operations and row behavior. */
@OptIn(ExperimentalMaterial3Api::class)
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
    onQrCode: (() -> Unit)?,
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { RethinkLargeTopBar(strings.title, onBackClick = onBackClick, scrollBehavior = scrollBehavior) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isEmpty) {
                // No in-place Create button: the FAB menu below is the one way to add (create,
                // import or scan), so the empty screen doesn't offer two entry points.
                RethinkEmptyState(
                    title = strings.noConfigurations,
                    icon = MaterialSymbols.Filled.VpnKey,
                    modifier = Modifier.align(Alignment.Center).padding(bottom = bottomInset),
                )
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
                            // Clear the docked FAB so the last card is never hidden under it.
                            bottom = SharedDimensions.fabClearance + bottomInset,
                        ),
                    ) {
                        items(configs.itemCount) { index ->
                            val config = configs[index] ?: return@items
                            configRow(config, selectedTab)
                        }
                    }
                }
            }
            RethinkWgFabMenu(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = bottomInset),
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
    RethinkConnectedChoiceGroup(
        options = listOf(
            RethinkChoiceOption(RethinkWgTab.One, strings.oneTab),
            RethinkChoiceOption(RethinkWgTab.General, strings.generalTab),
        ),
        selected = selected,
        onSelect = onClick,
        modifier = Modifier.padding(
            start = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingLg,
            end = SharedDimensions.screenPaddingHorizontal,
        ),
    )
}

@Composable
private fun RethinkWgOverview(text: String) {
    RethinkContainedCard(
        modifier = Modifier.padding(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingMd,
        ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd), verticalAlignment = Alignment.CenterVertically) {
            // Same well as the detail screen's overview card.
            RethinkIconWell(
                icon = MaterialSymbols.Filled.VpnKey,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconSize = SharedDimensions.iconSizeMd,
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** M3 Expressive FAB menu: one toggle FAB that fans out the ways to add a configuration. */
@Composable
private fun RethinkWgFabMenu(
    modifier: Modifier,
    strings: RethinkWireguardStrings,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onQrCode: (() -> Unit)?,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { expanded = it },
                modifier = Modifier.semantics {
                    traversalIndex = -1f
                    contentDescription = strings.addConfiguration
                    stateDescription = if (expanded) strings.expanded else strings.collapsed
                },
            ) {
                Icon(
                    if (checkedProgress > 0.5f) MaterialSymbols.Filled.Close else MaterialSymbols.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; onCreate() },
            text = { Text(strings.create) },
            icon = { Icon(MaterialSymbols.Filled.Edit, null) },
        )
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; onImport() },
            text = { Text(strings.import) },
            icon = { Icon(MaterialSymbols.Filled.Download, null) },
        )
        if (onQrCode != null) {
            FloatingActionButtonMenuItem(
                onClick = { expanded = false; onQrCode() },
                text = { Text(strings.qrCode) },
                icon = { Icon(MaterialSymbols.Filled.CameraAlt, null) },
            )
        }
    }
}
