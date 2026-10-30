/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.ui.compose.theme.RethinkOverflowMenu
import com.bernaferrari.bravedns.ui.compose.theme.RethinkOverflowItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceLabels
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

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
    additionalOverflowItems: List<RethinkOverflowItem> = emptyList(),
    networkContent: @Composable (onToolbarActionsChange: (RethinkLogToolbarActions) -> Unit) -> Unit,
    dnsContent: @Composable (onToolbarActionsChange: (RethinkLogToolbarActions) -> Unit) -> Unit,
) {
    // Saved as an ordinal so the selected tab survives rotation/process death on every target.
    var tabIndex by rememberSaveable { mutableIntStateOf(RethinkLogTab.Network.ordinal) }
    val tab = RethinkLogTab.entries[tabIndex]
    var toolbarActions by remember { mutableStateOf(RethinkLogToolbarActions()) }
    LaunchedEffect(tab) { toolbarActions = RethinkLogToolbarActions() }
    // A dense, list-first screen: small top bar, with the Network/DNS switch living in the bar
    // (beside the overflow) so the log list starts as high as possible.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkTopBar(
                title = strings.title,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
                actions = {
                    RethinkLogsTabSwitch(
                        selected = tab,
                        networkLabel = strings.network,
                        dnsLabel = strings.dns,
                        onTabSelected = { tabIndex = it.ordinal },
                    )
                    RethinkLogsOverflow(toolbarActions, strings, additionalOverflowItems)
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

/** Network/DNS switch in the top bar: the active source shows its label, the other its icon. */
@Composable
private fun RethinkLogsTabSwitch(
    selected: RethinkLogTab,
    networkLabel: String,
    dnsLabel: String,
    onTabSelected: (RethinkLogTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        RethinkChoiceOption(RethinkLogTab.Network, networkLabel, MaterialSymbols.Filled.SyncAlt),
        RethinkChoiceOption(RethinkLogTab.Dns, dnsLabel, MaterialSymbols.Filled.Dns),
    )
    RethinkConnectedChoiceGroup(
        options = options,
        selected = selected,
        onSelect = onTabSelected,
        modifier = modifier,
        fillWidth = false,
        labels = RethinkChoiceLabels.SelectedLabel,
    )
}

@Composable
private fun RethinkLogsOverflow(
    actions: RethinkLogToolbarActions,
    strings: RethinkLogsStrings,
    additionalItems: List<RethinkOverflowItem>,
) {
    RethinkOverflowMenu(
        moreDescription = strings.moreActions,
        items = listOfNotNull(
            actions.onRefresh?.let { RethinkOverflowItem(strings.refresh, MaterialSymbols.Filled.Refresh, onClick = it) },
            actions.onClear?.let { RethinkOverflowItem(strings.clear, MaterialSymbols.Filled.DeleteOutline, onClick = it) },
        ) + additionalItems,
    )
}
