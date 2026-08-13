/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.bernaferrari.bravedns.di.RethinkWebDemoDependencies
import com.bernaferrari.bravedns.ui.compose.about.RethinkWebDemoMetadata
import com.bernaferrari.bravedns.ui.compose.home.RethinkHomeAtmosphere
import com.bernaferrari.bravedns.ui.compose.home.RethinkHomeUiState
import com.bernaferrari.bravedns.ui.compose.home.RethinkWelcomeContent
import com.bernaferrari.bravedns.ui.compose.home.RethinkWelcomeFeature
import com.bernaferrari.bravedns.ui.compose.home.RethinkWelcomeScreen
import com.bernaferrari.bravedns.ui.compose.navigation.RethinkAppNavigation
import com.bernaferrari.bravedns.ui.compose.navigation.RethinkRoute
import com.bernaferrari.bravedns.ui.compose.navigation.rethinkPreviewEntries
import com.bernaferrari.bravedns.ui.compose.settings.RethinkAppearanceMode
import com.bernaferrari.bravedns.ui.compose.settings.RethinkAppearanceSettingsCard
import com.bernaferrari.bravedns.ui.compose.settings.rethinkAppearancePresets
import com.bernaferrari.bravedns.ui.compose.settings.rethinkAppearanceStrings
import com.bernaferrari.bravedns.ui.compose.statistics.RethinkStatisticsWindow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSharedTheme
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/**
 * Shared app shell: theme + optional welcome + one [RethinkAppNavigation].
 *
 * Wasm uses the default preview [entryBuilder]. Android mounts the same navigation host with a
 * product [entryBuilder] (see `HomeScreenRoot`) — same routes, stack policy, and chrome.
 */
@Composable
fun RethinkAppContent(
    darkTheme: Boolean = false,
    reducedMotion: Boolean = false,
    demoDependencies: RethinkWebDemoDependencies =
        RethinkWebDemoDependencies(appMetadata = RethinkWebDemoMetadata),
    showWelcomeInitially: Boolean = false,
    onWelcomeFinished: () -> Unit = {},
    startRoute: RethinkRoute = RethinkRoute.Home,
    pendingRoute: RethinkRoute? = null,
    onRouteNavigated: () -> Unit = {},
) {
    var showWelcome by remember(showWelcomeInitially) { mutableStateOf(showWelcomeInitially) }
    val vpnOn = remember { mutableStateOf(false) }
    var appearanceMode by remember { mutableStateOf(RethinkAppearanceMode.System) }
    var appearancePresetId by remember { mutableIntStateOf(2) }
    var statisticsWindow by remember { mutableStateOf(RethinkStatisticsWindow.TwentyFourHours) }
    var configureSearchOpen by remember { mutableStateOf(false) }
    var configureQuery by remember { mutableStateOf("") }
    val seedColor = rethinkAppearancePresets
        .firstOrNull { it.id == appearancePresetId && it.color != null }
        ?.color
        ?: Color(0xFF804136)

    RethinkSharedTheme(
        darkTheme = when (appearanceMode) {
            RethinkAppearanceMode.System -> darkTheme
            RethinkAppearanceMode.Light -> false
            RethinkAppearanceMode.Dark -> true
        },
        seedColor = seedColor,
        reducedMotion = reducedMotion,
    ) {
        if (showWelcome) {
            RethinkWelcomeScreen(
                content = RethinkWelcomeContent(
                    title = "Welcome to RethinkDNS",
                    description = "Open-source privacy tools, designed to keep your connection in your control.",
                    heroIcon = MaterialSymbols.Filled.Shield,
                    features = listOf(
                        RethinkWelcomeFeature("App firewall", MaterialSymbols.Filled.Security),
                        RethinkWelcomeFeature("Private DNS", MaterialSymbols.Filled.Dns),
                        RethinkWelcomeFeature("Secure routing", MaterialSymbols.Filled.VpnKey),
                    ),
                ),
                ctaLabel = "Get started",
                onFinish = {
                    showWelcome = false
                    onWelcomeFinished()
                },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                RethinkAppNavigation(
                    destinations = demoNavigationItems,
                    startRoute = startRoute,
                    pendingRoute = pendingRoute,
                    onRouteNavigated = onRouteNavigated,
                    modifier = Modifier.fillMaxSize(),
                    background = { current ->
                        if (current is RethinkRoute.Home) {
                            RethinkHomeAtmosphere(
                                uiState = RethinkHomeUiState(
                                    isVpnActive = vpnOn.value,
                                    networkLogsCount = 4_320,
                                    dnsLogsCount = 1_284,
                                    protectionStatus = if (vpnOn.value) "Protected" else "Not active",
                                ),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
                    entryBuilder = { nav ->
                        rethinkPreviewEntries(
                            nav = nav,
                            demoDependencies = demoDependencies,
                            vpnOn = vpnOn,
                            statisticsWindow = statisticsWindow,
                            onStatisticsWindowChange = { statisticsWindow = it },
                            configureSearchOpen = configureSearchOpen,
                            onConfigureSearchOpenChange = { configureSearchOpen = it },
                            configureQuery = configureQuery,
                            onConfigureQueryChange = { configureQuery = it },
                            appearanceContent = {
                                RethinkAppearanceSettingsCard(
                                    selectedMode = appearanceMode,
                                    selectedPresetId = appearancePresetId,
                                    presets = rethinkAppearancePresets,
                                    strings = rethinkAppearanceStrings,
                                    dynamicColor = Color(0xFF7C8BFF),
                                    dynamicSupported = false,
                                    onModeSelected = { appearanceMode = it },
                                    onPresetSelected = { appearancePresetId = it.id },
                                )
                            },
                        )
                    },
                )
            }
        }
    }
}
