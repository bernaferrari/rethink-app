/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar

enum class RethinkLogTab { Network, Dns }

data class RethinkLogToolbarActions(
    val onRefresh: (() -> Unit)? = null,
    val onClear: (() -> Unit)? = null,
)

data class RethinkLogsStrings(
    val title: String,
    val network: String,
    val dns: String,
    val moreActions: String,
    val refresh: String,
    val clear: String,
)

/** Shared top-level Logs navigation, tabs, and overflow behaviour. */
@Composable
fun RethinkLogsScreenShell(
    strings: RethinkLogsStrings,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    networkContent: @Composable (onToolbarActionsChange: (RethinkLogToolbarActions) -> Unit) -> Unit,
    dnsContent: @Composable (onToolbarActionsChange: (RethinkLogToolbarActions) -> Unit) -> Unit,
) {
    var tab by remember { mutableStateOf(RethinkLogTab.Network) }
    var toolbarActions by remember { mutableStateOf(RethinkLogToolbarActions()) }
    LaunchedEffect(tab) { toolbarActions = RethinkLogToolbarActions() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
                actions = {
                    RethinkLogsTabSwitch(tab, strings.network, strings.dns, onTabSelected = { tab = it })
                    RethinkLogsOverflow(toolbarActions, strings)
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                RethinkLogTab.Network -> networkContent { toolbarActions = it }
                RethinkLogTab.Dns -> dnsContent { toolbarActions = it }
            }
        }
    }
}

@Composable
private fun RethinkLogsTabSwitch(
    selected: RethinkLogTab,
    networkLabel: String,
    dnsLabel: String,
    onTabSelected: (RethinkLogTab) -> Unit,
) {
    val options = listOf(
        RethinkChoiceOption(RethinkLogTab.Network, networkLabel, MaterialSymbols.Filled.NetworkPing),
        RethinkChoiceOption(RethinkLogTab.Dns, dnsLabel, MaterialSymbols.Filled.Shield),
    )
    RethinkConnectedChoiceGroup(
        options = options,
        selected = selected,
        onSelect = onTabSelected,
        fillWidth = false,
    )
}

@Composable
private fun RethinkLogsOverflow(actions: RethinkLogToolbarActions, strings: RethinkLogsStrings) {
    if (actions.onRefresh == null && actions.onClear == null) return

    var expanded by remember { mutableStateOf(false) }
    // DropdownMenu is positioned relative to its layout parent. Keeping the trigger and popup in
    // one Box prevents Compose Web from anchoring the menu to the full app-bar action row.
    Box {
        IconButton(onClick = { expanded = true }) { Icon(MaterialSymbols.Filled.MoreVert, strings.moreActions) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.onRefresh?.let { refresh ->
                DropdownMenuItem(
                    text = { Text(strings.refresh) },
                    leadingIcon = { Icon(MaterialSymbols.Filled.Refresh, null) },
                    onClick = { expanded = false; refresh() },
                )
            }
            actions.onClear?.let { clear ->
                DropdownMenuItem(
                    text = { Text(strings.clear) },
                    leadingIcon = { Icon(MaterialSymbols.Filled.Clear, null) },
                    onClick = { expanded = false; clear() },
                )
            }
        }
    }
}
