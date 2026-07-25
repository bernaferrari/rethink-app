/* Copyright 2025 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.alerts

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.celzero.bravedns.R

@Composable
fun AlertsScreen(onBackClick: () -> Unit) {
    RethinkAlertsScreen(
        strings = RethinkAlertsStrings(
            title = stringResource(R.string.notif_channel_firewall_alerts),
            emptyMessage = stringResource(R.string.alerts_empty_state),
        ),
        onBackClick = onBackClick,
    )
}
