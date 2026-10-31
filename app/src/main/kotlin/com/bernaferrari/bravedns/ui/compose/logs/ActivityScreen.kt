/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.compose.navigation.RethinkActivityView

/**
 * The Activity tab: the log and Insights (former Stats) behind one Log · Insights switch in the
 * top bar. Each view keeps its saveable state (filters, scroll, selected window) while the other
 * shows; [view] itself is host state, so Stats links and notifications can pick Insights.
 */
@Composable
fun ActivityScreen(
    view: RethinkActivityView,
    onViewChange: (RethinkActivityView) -> Unit,
    log: @Composable (viewSwitch: @Composable () -> Unit) -> Unit,
    insights: @Composable (viewSwitch: @Composable () -> Unit) -> Unit,
) {
    val logLabel = stringResource(R.string.activity_view_log)
    val insightsLabel = stringResource(R.string.activity_view_insights)
    val viewSwitch: @Composable () -> Unit = {
        RethinkActivityViewSwitch(
            selected = view,
            onSelect = onViewChange,
            logLabel = logLabel,
            insightsLabel = insightsLabel,
        )
    }
    val holder = rememberSaveableStateHolder()
    holder.SaveableStateProvider(view.name) {
        when (view) {
            RethinkActivityView.Log -> log(viewSwitch)
            RethinkActivityView.Insights -> insights(viewSwitch)
        }
    }
}
