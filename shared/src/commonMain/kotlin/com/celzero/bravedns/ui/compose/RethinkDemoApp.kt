/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Full UI entry point in commonMain for wasmJs/web demo (and future non-Android hosts).
 * No VPN/DNS/Room at runtime — screens show realistic shells with demo/static data.
 */
package com.celzero.bravedns.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.celzero.bravedns.ui.compose.common.RethinkEmptyState
import com.celzero.bravedns.ui.compose.home.HomeDashboardShared
import com.celzero.bravedns.ui.compose.home.PauseScreenShared
import com.celzero.bravedns.ui.compose.home.WelcomeScreenShared
import com.celzero.bravedns.ui.compose.theme.RethinkSharedTheme
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

enum class DemoTab {
    Home,
    Configure,
    Logs,
}

enum class DemoOverlay {
    None,
    Welcome,
    Pause,
}

/**
 * Portable RethinkDNS UI shell. Hosts (Android app, wasmJs) pass nothing — all copy is
 * inline demo strings so this compiles without resources on web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkDemoApp(
    darkTheme: Boolean = false,
    demoBanner: String = "Web preview — UI only; VPN/DNS/Room not active on this target.",
) {
    RethinkSharedTheme(darkTheme = darkTheme) {
        var tab by remember { mutableStateOf(DemoTab.Home) }
        var overlay by remember { mutableStateOf(DemoOverlay.Welcome) }
        var vpnOn by remember { mutableStateOf(false) }

        when (overlay) {
            DemoOverlay.Welcome -> WelcomeScreenShared(
                title = "RethinkDNS",
                subtitle = "Open-source firewall + DNS. This is a Compose Multiplatform preview.",
                ctaLabel = "Continue",
                onCta = { overlay = DemoOverlay.None },
            )
            DemoOverlay.Pause -> PauseScreenShared(
                title = "Paused",
                subtitle = "Protection is paused in this demo. Resume to return to the home shell.",
                resumeLabel = "Resume",
                onResume = { overlay = DemoOverlay.None },
            )
            DemoOverlay.None -> Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = { Text("RethinkDNS") },
                        actions = {
                            TextButton(onClick = { overlay = DemoOverlay.Pause }) {
                                Text("Pause")
                            }
                        },
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == DemoTab.Home,
                            onClick = { tab = DemoTab.Home },
                            icon = { Text("H") },
                            label = { Text("Home") },
                        )
                        NavigationBarItem(
                            selected = tab == DemoTab.Configure,
                            onClick = { tab = DemoTab.Configure },
                            icon = { Text("C") },
                            label = { Text("Configure") },
                        )
                        NavigationBarItem(
                            selected = tab == DemoTab.Logs,
                            onClick = { tab = DemoTab.Logs },
                            icon = { Text("L") },
                            label = { Text("Logs") },
                        )
                    }
                },
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (tab) {
                        DemoTab.Home -> HomeDashboardShared(
                            isVpnActive = vpnOn,
                            statusLine = if (vpnOn) "Protected (demo)" else "Not protected (demo)",
                            startLabel = "Start",
                            stopLabel = "Stop",
                            onToggleVpn = { vpnOn = !vpnOn },
                            statBlocked = "128",
                            statBlockedLabel = "Blocked",
                            statQueries = "1.2k",
                            statQueriesLabel = "Queries",
                            statApps = "42",
                            statAppsLabel = "Apps",
                            banner = demoBanner,
                        )
                        DemoTab.Configure -> RethinkEmptyState(
                            title = "Configure",
                            message = "DNS, firewall, and proxy settings live in the Android app. " +
                                "Portable settings shells can move here incrementally.",
                            modifier = Modifier.padding(SharedDimensions.spacingXl),
                        )
                        DemoTab.Logs -> RethinkEmptyState(
                            title = "No logs yet",
                            message = "Network and DNS logs require the VPN service. " +
                                "This tab shows the shared empty-state component.",
                            modifier = Modifier.padding(SharedDimensions.spacingXl),
                        )
                    }
                    Text(
                        text = "commonMain · Compose Multiplatform",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = SharedDimensions.spacingXl,
                            vertical = SharedDimensions.spacingSm,
                        ),
                    )
                }
            }
        }
    }
}
