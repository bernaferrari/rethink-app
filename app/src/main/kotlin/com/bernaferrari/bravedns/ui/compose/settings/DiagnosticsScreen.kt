/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/**
 * Settings › Advanced › Diagnostics: the troubleshooting tools that used to be scattered (event
 * logs, app logs, the connectivity check; in debug builds also engine stats, the database
 * inspector and the flight recorder, which About used to hold).
 */
@Composable
fun DiagnosticsScreen(
    isDebug: Boolean,
    onEventLogsClick: () -> Unit,
    onAppLogsClick: () -> Unit,
    onConnectivityChecksClick: () -> Unit,
    onNetworkStatsClick: () -> Unit,
    onDatabaseClick: () -> Unit,
    onFlightRecorderClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
) {
    RethinkSettingsLinksScreen(
        title = stringResource(R.string.about_diagnostics),
        sections = listOf(
            RethinkSettingsLinkSection(
                stringResource(R.string.diagnostics_section_troubleshoot),
                listOf(
                    RethinkSettingsLink("events", stringResource(R.string.event_logs_title), MaterialSymbols.Filled.History, onEventLogsClick, stringResource(R.string.diagnostics_event_logs_desc)),
                    RethinkSettingsLink("console", stringResource(R.string.console_log_title), MaterialSymbols.Filled.Terminal, onAppLogsClick, stringResource(R.string.diagnostics_app_logs_desc)),
                    RethinkSettingsLink("ping", stringResource(R.string.settings_connectivity_checks), MaterialSymbols.Filled.NetworkCheck, onConnectivityChecksClick, stringResource(R.string.settings_connectivity_checks_desc)),
                ),
            ),
            RethinkSettingsLinkSection(
                stringResource(R.string.settings_developer_title),
                if (!isDebug) emptyList() else listOf(
                    // Stats opens a dialog and the recorder starts at once: actions in place, no chevron.
                    RethinkSettingsLink("stats", stringResource(R.string.title_statistics), MaterialSymbols.Filled.InsertChart, onNetworkStatsClick, opensScreen = false),
                    RethinkSettingsLink("database", stringResource(R.string.title_database_dump), MaterialSymbols.Filled.Database, onDatabaseClick),
                    RethinkSettingsLink("flight", stringResource(R.string.about_flight_recorder), MaterialSymbols.Filled.Report, onFlightRecorderClick, opensScreen = false),
                ),
            ),
        ),
        onBackClick = onBackClick,
    )
}
