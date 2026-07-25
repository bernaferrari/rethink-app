/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Re-exports / documents Compose Multiplatform entry points from :shared so app
 * screens can gradually delegate portable UI (empty states, welcome/pause shells,
 * home dashboard) without pulling Android resources into commonMain.
 *
 * Full non-functional web entry: [RethinkDemoApp] in :shared commonMain (wasmJs host in :shared).
 */
package com.celzero.bravedns.ui.compose

import androidx.compose.runtime.Composable
import com.celzero.bravedns.ui.compose.common.RethinkEmptyState
import com.celzero.bravedns.ui.compose.home.HomeDashboardShared
import com.celzero.bravedns.ui.compose.home.RethinkPauseScreen
import com.celzero.bravedns.ui.compose.home.RethinkPauseState
import com.celzero.bravedns.ui.compose.home.RethinkPauseStrings
import com.celzero.bravedns.ui.compose.home.StartStopButtonShared
import com.celzero.bravedns.ui.compose.home.RethinkWelcomeScreen
import com.celzero.bravedns.ui.compose.home.RethinkWelcomeContent
import com.celzero.bravedns.ui.icons.MaterialSymbols
import com.celzero.bravedns.ui.compose.theme.RethinkSharedTheme

/** Apply shared Material3 theme (light/dark) from KMP commonMain. */
@Composable
fun WithRethinkSharedTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    RethinkSharedTheme(darkTheme = darkTheme, content = content)
}

/** Portable welcome shell — no Android string resources / images. */
@Composable
fun SharedWelcomeShell(
    title: String,
    subtitle: String,
    ctaLabel: String,
    onCta: () -> Unit,
) = RethinkWelcomeScreen(
    content = RethinkWelcomeContent(
        title = title,
        description = subtitle,
        heroIcon = MaterialSymbols.Filled.Shield,
    ),
    ctaLabel = ctaLabel,
    onFinish = onCta,
)

/** Portable pause shell. */
@Composable
fun SharedPauseShell(
    title: String,
    subtitle: String,
    resumeLabel: String,
    onResume: () -> Unit,
) = RethinkPauseScreen(
    state = RethinkPauseState(timerText = subtitle),
    strings = RethinkPauseStrings(title = title, pauseLabel = title, resume = resumeLabel),
    onDecrease = {},
    onIncrease = {},
    onResume = onResume,
)

/** Portable start/stop CTA (no Icons/stringResource). */
@Composable
fun SharedStartStopButton(
    isActive: Boolean,
    startLabel: String,
    stopLabel: String,
    onClick: () -> Unit,
) = StartStopButtonShared(isActive, startLabel, stopLabel, onClick)

/** Portable home stats + toggle block. */
@Composable
fun SharedHomeDashboard(
    isVpnActive: Boolean,
    statusLine: String,
    startLabel: String,
    stopLabel: String,
    onToggleVpn: () -> Unit,
    statBlocked: String,
    statBlockedLabel: String,
    statQueries: String,
    statQueriesLabel: String,
    statApps: String,
    statAppsLabel: String,
    banner: String? = null,
) = HomeDashboardShared(
    isVpnActive = isVpnActive,
    statusLine = statusLine,
    startLabel = startLabel,
    stopLabel = stopLabel,
    onToggleVpn = onToggleVpn,
    statBlocked = statBlocked,
    statBlockedLabel = statBlockedLabel,
    statQueries = statQueries,
    statQueriesLabel = statQueriesLabel,
    statApps = statApps,
    statAppsLabel = statAppsLabel,
    banner = banner,
)

/** Portable empty/error list state. */
@Composable
fun SharedEmptyState(title: String, message: String) =
    RethinkEmptyState(title = title, message = message)
