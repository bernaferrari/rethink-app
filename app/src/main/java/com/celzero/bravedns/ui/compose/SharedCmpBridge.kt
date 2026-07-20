/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Re-exports / documents Compose Multiplatform entry points from :shared so app
 * screens can gradually delegate portable UI (empty states, welcome/pause shells,
 * home dashboard) without pulling Android resources into commonMain.
 *
 * Full non-functional web entry: [RethinkDemoApp] in :shared commonMain (wasmJs host in web-build).
 */
package com.celzero.bravedns.ui.compose

import androidx.compose.runtime.Composable
import com.celzero.bravedns.ui.compose.common.RethinkEmptyState
import com.celzero.bravedns.ui.compose.home.HomeDashboardShared
import com.celzero.bravedns.ui.compose.home.PauseScreenShared
import com.celzero.bravedns.ui.compose.home.StartStopButtonShared
import com.celzero.bravedns.ui.compose.home.WelcomeScreenShared
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
) = WelcomeScreenShared(title, subtitle, ctaLabel, onCta)

/** Portable pause shell. */
@Composable
fun SharedPauseShell(
    title: String,
    subtitle: String,
    resumeLabel: String,
    onResume: () -> Unit,
) = PauseScreenShared(title, subtitle, resumeLabel, onResume)

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
