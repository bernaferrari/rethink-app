/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.bernaferrari.bravedns.ui.compose.settings

import com.bernaferrari.bravedns.ui.compose.theme.RethinkToolbarIconToggle
import com.bernaferrari.bravedns.ui.compose.theme.RethinkToolbarRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkOverflowMenu
import com.bernaferrari.bravedns.ui.compose.theme.RethinkOverflowItem
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSheetHeader
import com.bernaferrari.bravedns.ui.compose.theme.RethinkRadioChoiceList
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import kotlinx.coroutines.delay

/** Copy and platform callbacks used by the portable console-log UI. */
data class RethinkConsoleLogStrings(
    val title: String,
    val searchHint: String,
    val filterDescription: String,
    /** Content description of the "delete all logs" action. */
    val clearDescription: String,
    /** Content description of the top-bar overflow ("More options"). */
    val moreDescription: String = "More options",
    /** Content description of the search field's clear-query button. */
    val clearSearchDescription: String,
    val deleteDialogTitle: String,
    val deleteDialogMessage: String,
    val deleteLabel: String,
    val reportLabel: String,
    val cancelLabel: String,
    val filterOptions: List<String>,
    val pauseLabel: String = "Pause live updates",
    val resumeLabel: String = "Resume live updates",
    val pausedDescription: String = "Paused: filtering a frozen snapshot of up to 5,000 entries.",
    val pausingDescription: String = "Freezing log snapshot…",
    val clearingDescription: String = "Deleting console logs…",

)

/**
 * Shared console-log chrome: header, debounced search, level picker, actions and report FAB.
 * Hosts own storage and paging; no Android database or VPN type reaches this renderer.
 */
@Composable
fun RethinkConsoleLogsScreen(
    strings: RethinkConsoleLogStrings,
    initialLogLevel: Int,
    onFilterChange: (String) -> Unit,
    onLogLevelSelected: (Int) -> Unit,
    onShareClick: () -> Unit,
    onClearClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    pauseBusy: Boolean = false,
    pauseError: String? = null,
    onPauseToggle: (() -> Unit)? = null,
    clearBusy: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filterVisible by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedLogLevel by remember(initialLogLevel) { mutableIntStateOf(initialLogLevel) }

    LaunchedEffect(query) {
        delay(1_000)
        onFilterChange(query)
    }

    if (filterVisible) {
        RethinkModalBottomSheet(
            onDismissRequest = { filterVisible = false },
        ) {
            RethinkSheetHeader(title = strings.filterDescription)
            RethinkRadioChoiceList(
                options = strings.filterOptions.indices.toList(),
                selected = { selectedLogLevel == it },
                label = { strings.filterOptions[it] },
                onSelected = {
                    selectedLogLevel = it
                    onLogLevelSelected(it)
                    filterVisible = false
                },
            )
        }
    }

    if (showDeleteDialog) {
        RethinkConfirmDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = strings.deleteDialogTitle,
            message = strings.deleteDialogMessage,
            confirmText = strings.deleteLabel,
            dismissText = strings.cancelLabel,
            isConfirmDestructive = true,
            confirmEnabled = !clearBusy,
            onConfirm = {
                showDeleteDialog = false
                onClearClick()
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Same chrome as Logs and Events: small bar, destructive action tucked in the overflow.
        topBar = {
            RethinkTopBar(
                title = strings.title,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
                actions = {
                    RethinkOverflowMenu(
                        moreDescription = strings.moreDescription,
                        items = listOf(
                            RethinkOverflowItem(strings.clearDescription, MaterialSymbols.Filled.DeleteOutline, enabled = !clearBusy) {
                                showDeleteDialog = true
                            },
                        ),
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(strings.reportLabel) },
                icon = { Icon(MaterialSymbols.Filled.Share, null) },
                onClick = onShareClick,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            RethinkConsoleLogSearchRow(
                query = query,
                strings = strings,
                onQueryChange = { query = it },
                filterActive = selectedLogLevel > 0,
                onFilterClick = { filterVisible = true },
                pauseAction = onPauseToggle?.let { action -> {
                    RethinkToolbarIconToggle(
                        icon = if (paused) MaterialSymbols.Filled.PlayArrow else MaterialSymbols.Filled.PauseCircle,
                        contentDescription = if (paused) strings.resumeLabel else strings.pauseLabel,
                        selected = paused,
                        enabled = !pauseBusy,
                        onClick = action,
                    )
                } },
            )
            ConsoleUpdatesStatus(strings, paused, pauseBusy, pauseError, clearBusy)
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun RethinkConsoleLogSearchRow(
    query: String,
    strings: RethinkConsoleLogStrings,
    onQueryChange: (String) -> Unit,
    filterActive: Boolean,
    onFilterClick: () -> Unit,
    pauseAction: (@Composable () -> Unit)? = null,
) {
    RethinkToolbarRow(
        modifier = Modifier.padding(
            horizontal = SharedDimensions.screenPaddingHorizontal,
            vertical = SharedDimensions.spacingSm,
        ),
    ) {
        RethinkSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = strings.searchHint,
            clearQueryContentDescription = strings.clearSearchDescription,
            modifier = Modifier.weight(1f),
        )
        pauseAction?.invoke()
        RethinkToolbarIconToggle(
            icon = MaterialSymbols.Filled.FilterList,
            contentDescription = strings.filterDescription,
            selected = filterActive,
            onClick = onFilterClick,
        )
    }
}

@Composable
private fun ConsoleUpdatesStatus(strings: RethinkConsoleLogStrings, paused: Boolean, busy: Boolean, error: String?, clearing: Boolean) {
    val status = error ?: when {
        clearing -> strings.clearingDescription
        busy -> strings.pausingDescription
        paused -> strings.pausedDescription
        else -> null
    }
    status?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingXs)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
