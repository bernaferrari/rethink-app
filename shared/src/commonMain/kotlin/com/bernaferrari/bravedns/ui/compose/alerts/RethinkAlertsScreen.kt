/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.bernaferrari.bravedns.ui.compose.alerts

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

data class RethinkAlertsStrings(val title: String, val emptyMessage: String)

/** Shared empty-alerts screen. Runtime notification storage stays platform-owned. */
@Composable
fun RethinkAlertsScreen(
    strings: RethinkAlertsStrings,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        // The shared empty state (not a bespoke bordered card), and without repeating the
        // screen title that the large top bar already shows.
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = SharedDimensions.screenPaddingHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            RethinkEmptyState(
                title = strings.emptyMessage,
                message = "",
                icon = MaterialSymbols.Filled.NotificationsOff,
            )
        }
    }
}
