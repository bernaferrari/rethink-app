/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.navigation.RethinkActivityView
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/**
 * The Activity tab's **Log · Insights** switch. It sits in the top bar of both views, at the same
 * place, so switching never moves it. Two short words, both always labelled.
 */
@Composable
fun RethinkActivityViewSwitch(
    selected: RethinkActivityView,
    onSelect: (RethinkActivityView) -> Unit,
    logLabel: String,
    insightsLabel: String,
    modifier: Modifier = Modifier,
) {
    RethinkConnectedChoiceGroup(
        options = listOf(
            RethinkChoiceOption(RethinkActivityView.Log, logLabel),
            RethinkChoiceOption(RethinkActivityView.Insights, insightsLabel),
        ),
        selected = selected,
        onSelect = onSelect,
        modifier = modifier.padding(end = SharedDimensions.spacingXs),
        fillWidth = false,
        compact = true,
        unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

/**
 * Which log the Activity tab lists: connections or DNS lookups. Labelled on both halves (the old
 * top-bar switch showed the inactive half as a bare icon).
 */
@Composable
fun RethinkLogTypeSwitch(
    selected: RethinkLogTab,
    onSelect: (RethinkLogTab) -> Unit,
    connectionsLabel: String,
    lookupsLabel: String,
    modifier: Modifier = Modifier,
) {
    RethinkConnectedChoiceGroup(
        options = listOf(
            RethinkChoiceOption(RethinkLogTab.Network, connectionsLabel, MaterialSymbols.Filled.SyncAlt),
            RethinkChoiceOption(RethinkLogTab.Dns, lookupsLabel, MaterialSymbols.Filled.Dns),
        ),
        selected = selected,
        onSelect = onSelect,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SharedDimensions.screenPaddingHorizontal)
            .padding(top = SharedDimensions.spacingXs),
    )
}
